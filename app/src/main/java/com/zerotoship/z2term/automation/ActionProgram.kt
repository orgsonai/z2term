package com.zerotoship.z2term.automation

/** Validate and freeze the complete call graph before any Android action is dispatched. */
internal data class ActionProgram(val name: String, val definitions: Map<String, ActionDefinition>, val key: String = name) {
    val root get() = definitions.getValue(key)
    val hasControlFlow = ActionDefinition.allSteps(root.steps).any {
        it is ActionDefinition.Step.Repeat || it is ActionDefinition.Step.Branch || it is ActionDefinition.Step.Call ||
            it is ActionDefinition.Step.Try ||
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
                is ActionDefinition.Step.ReadColors -> step.target == ActionDefinition.CURRENT_TARGET
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
        fun load(name: String, args: List<String> = emptyList(), read: (String) -> ActionDefinition): ActionProgram {
            val sources = mutableMapOf<String, ActionDefinition>()
            val definitions = linkedMapOf<String, ActionDefinition>()
            val visiting = mutableSetOf<String>()
            val depths = mutableMapOf<String, Int>()
            val heights = mutableMapOf<String, Int>()
            fun visit(current: String, values: List<String>, callDepth: Int): Int {
                ActionDefinition.macroName(current)
                val key = ActionDefinition.callKey(current, values)
                require(current !in visiting) { "Recursive macro call: $current" }
                require(callDepth <= MAX_CALL_DEPTH) { "At most $MAX_CALL_DEPTH macro levels including the root" }
                depths[key]?.let { depth ->
                    require(callDepth + heights.getValue(key) - 1 <= MAX_CALL_DEPTH) { "Macro call depth exceeded" }
                    return depth
                }
                // Depth is checked on every path, including repeated references in a DAG.
                val definition = definitions[key] ?: run {
                    val source = sources[current] ?: read(current).also { sources[current] = it }
                    val bound = try { source.bind(values) } catch (e: IllegalArgumentException) {
                        throw IllegalArgumentException("$current: " + e.message, e)
                    }
                    require(definitions.size < 64) { "At most 64 referenced definitions" }
                    bound.also { definitions[key] = it }
                }
                visiting += current
                fun bodyDepth(steps: List<ActionDefinition.Step>): Int = steps.maxOfOrNull { step ->
                    when (step) {
                        is ActionDefinition.Step.Repeat -> 1 + bodyDepth(step.body)
                        is ActionDefinition.Step.Branch -> 1 + maxOf(bodyDepth(step.yes), bodyDepth(step.no))
                        is ActionDefinition.Step.Try -> 1 + maxOf(bodyDepth(step.body), bodyDepth(step.recover))
                        is ActionDefinition.Step.Call -> visit(step.name, step.args, callDepth + 1)
                        // 割り込みで呼ぶ先も、呼び出しと同じく実行前に検査・固定する。
                        is ActionDefinition.Step.Watch -> visit(step.name, emptyList(), callDepth + 1)
                        else -> 0
                    }
                } ?: 0
                val depth = 1 + bodyDepth(definition.steps)
                require(depth <= MAX_FRAME_DEPTH) { "Combined call/block nesting exceeds $MAX_FRAME_DEPTH" }
                visiting -= current
                heights[key] = 1 + (ActionDefinition.allSteps(definition.steps)
                    .mapNotNull { (it as? ActionDefinition.Step.Call)?.key ?: (it as? ActionDefinition.Step.Watch)?.name }
                    .maxOfOrNull { heights.getValue(it) } ?: 0)
                depths[key] = depth
                return depth
            }
            visit(name, args, 1)
            return ActionProgram(name, definitions.toMap(), ActionDefinition.callKey(name, args))
        }
    }
}
