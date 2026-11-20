package com.zerotoship.z2term.automation

/** Validate and freeze the complete call graph before any Android action is dispatched. */
internal data class ActionProgram(val name: String, val definitions: Map<String, ActionDefinition>) {
    val root get() = definitions.getValue(name)
    val hasControlFlow = ActionDefinition.allSteps(root.steps).any {
        it is ActionDefinition.Step.Repeat || it is ActionDefinition.Step.Branch || it is ActionDefinition.Step.Call ||
            it is ActionDefinition.Step.Watch || it is ActionDefinition.Step.RunOutput
    }
    val usesCurrentTarget get() = definitions.values.any { definition ->
        ActionDefinition.allSteps(definition.steps).any { step ->
            when (step) {
                is ActionDefinition.Step.Stroke -> step.target == ActionDefinition.CURRENT_TARGET
                is ActionDefinition.Step.Scroll -> step.target == ActionDefinition.CURRENT_TARGET
                is ActionDefinition.Step.Ui -> step.target == ActionDefinition.CURRENT_TARGET
                is ActionDefinition.Step.UiTap -> step.target == ActionDefinition.CURRENT_TARGET
                is ActionDefinition.Step.Input -> step.target == ActionDefinition.CURRENT_TARGET
                // 画面を読む条件 (ui / color) も前面の画面が相手。状態だけの条件は画面を見ない。
                is ActionDefinition.Step.Branch -> step.target == ActionDefinition.CURRENT_TARGET &&
                    ActionCondition.parse(step.condition).readsScreen
                is ActionDefinition.Step.WaitFor -> step.target == ActionDefinition.CURRENT_TARGET &&
                    ActionCondition.parse(step.condition).readsScreen
                is ActionDefinition.Step.Watch -> step.target == ActionDefinition.CURRENT_TARGET &&
                    ActionCondition.parse(step.condition).readsScreen
                is ActionDefinition.Step.Repeat -> step.target == ActionDefinition.CURRENT_TARGET &&
                    step.until?.let { ActionCondition.parse(it).readsScreen } == true
                // 出力される手順は前面の画面を相手にするかもしれない。
                is ActionDefinition.Step.RunOutput -> step.target == ActionDefinition.CURRENT_TARGET
                else -> false
            }
        }
    }
    companion object {
        const val MAX_CALL_DEPTH = 8
        const val MAX_FRAME_DEPTH = 16
        fun load(name: String, read: (String) -> ActionDefinition): ActionProgram {
            val definitions = linkedMapOf<String, ActionDefinition>()
            val visiting = mutableSetOf<String>()
            val depths = mutableMapOf<String, Int>()
            val heights = mutableMapOf<String, Int>()
            fun visit(current: String, callDepth: Int): Int {
                ActionDefinition.macroName(current)
                require(current !in visiting) { "Recursive macro call: $current" }
                require(callDepth <= MAX_CALL_DEPTH) { "At most $MAX_CALL_DEPTH macro levels including the root" }
                depths[current]?.let { depth ->
                    require(callDepth + heights.getValue(current) - 1 <= MAX_CALL_DEPTH) { "Macro call depth exceeded" }
                    return depth
                }
                // Depth is checked on every path, including repeated references in a DAG.
                val definition = definitions[current] ?: read(current).also {
                    require(definitions.size < 64) { "At most 64 referenced definitions" }
                    definitions[current] = it
                }
                visiting += current
                fun bodyDepth(steps: List<ActionDefinition.Step>): Int = steps.maxOfOrNull { step ->
                    when (step) {
                        is ActionDefinition.Step.Repeat -> 1 + bodyDepth(step.body)
                        is ActionDefinition.Step.Branch -> 1 + maxOf(bodyDepth(step.yes), bodyDepth(step.no))
                        is ActionDefinition.Step.Call -> visit(step.name, callDepth + 1)
                        // 割り込みで呼ぶ先も、呼び出しと同じく実行前に検査・固定する。
                        is ActionDefinition.Step.Watch -> visit(step.name, callDepth + 1)
                        else -> 0
                    }
                } ?: 0
                val depth = 1 + bodyDepth(definition.steps)
                require(depth <= MAX_FRAME_DEPTH) { "Combined call/block nesting exceeds $MAX_FRAME_DEPTH" }
                visiting -= current
                heights[current] = 1 + (ActionDefinition.allSteps(definition.steps)
                    .mapNotNull { (it as? ActionDefinition.Step.Call)?.name ?: (it as? ActionDefinition.Step.Watch)?.name }
                    .maxOfOrNull { heights.getValue(it) } ?: 0)
                depths[current] = depth
                return depth
            }
            visit(name, 1)
            return ActionProgram(name, definitions.toMap())
        }
    }
}
