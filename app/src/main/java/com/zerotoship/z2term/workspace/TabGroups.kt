package com.zerotoship.z2term.workspace

/**
 * A named set of tabs shown side by side. A group with two or more members replaces the tab bar
 * entries of its members with one group entry; selecting it stacks the members as panes.
 * A group with a single member behaves exactly like a plain tab (no separate "group mode").
 */
internal data class TabGroup(
    val id: String,
    val name: String,
    val members: List<String> = emptyList(),
    val horizontal: Boolean = false,
    /** Share of each member, in [members] order, summing to 1. Reset to equal shares when membership changes. */
    val weights: List<Float> = equalWeights(members.size),
    /** The member shown alone (maximized) until restored, or null. */
    val maximized: String? = null,
    /** The member focused last; selecting the group from the tab bar returns to it. */
    val focus: String? = null,
    /** Members ticked to receive broadcast input. */
    val checked: Set<String> = emptySet(),
) {
    /** True when the members are shown as panes (two or more). */
    val stacked: Boolean get() = members.size >= 2

    /** Panes to show while [active] (a member) is focused. Maximizing only applies to the maximized member itself. */
    fun panes(active: String): List<String> = if (maximized == active && active in members) listOf(active) else members

    fun weightOf(id: String): Float = members.indexOf(id).let { i -> if (i < 0) 0f else weights.getOrElse(i) { 1f / members.size } }

    /** The member to focus when the group entry is selected. */
    fun entry(): String? = focus?.takeIf { it in members } ?: members.firstOrNull()
}

/** One entry of the tab bar: a plain tab or a group of two or more tabs. */
internal sealed interface TabUnit {
    val key: String
    data class Solo(val id: String) : TabUnit { override val key: String get() = id }
    data class Group(val group: TabGroup) : TabUnit { override val key: String get() = GROUP_KEY_PREFIX + group.id }
}

internal const val GROUP_KEY_PREFIX = "group:"

/** Smallest share of one pane, relative to an equal share, so dragging a divider never hides a pane. */
private const val MIN_SHARE_OF_EQUAL = 0.4f

internal fun equalWeights(n: Int): List<Float> = if (n <= 0) emptyList() else List(n) { 1f / n }

/**
 * All groups. Pure value: every change returns a new instance, so the rules are unit-tested
 * without Compose. A tab belongs to at most one group.
 */
internal data class TabGroups(val groups: List<TabGroup> = emptyList(), val nextNumber: Int = 1) {

    fun groupOf(sessionId: String?): TabGroup? = sessionId?.let { id -> groups.firstOrNull { id in it.members } }
    fun group(id: String?): TabGroup? = groups.firstOrNull { it.id == id }

    /** The group whose panes are shown while [active] is focused, or null for a single pane. */
    fun stackedFor(active: String?): TabGroup? = groupOf(active)?.takeIf { it.stacked }

    /**
     * Tabs on screen while [active] is focused: its group's panes (only the maximized one when
     * maximized), or [active] alone. A tab on screen is "being looked at" even when not focused.
     */
    fun onScreen(active: String?): List<String> =
        active?.let { a -> stackedFor(a)?.panes(a) ?: listOf(a) }.orEmpty()

    /** A new empty group named by [nameFor] (called with the group's number); returns the new id. */
    fun create(nameFor: (Int) -> String): Pair<TabGroups, String> {
        val id = "g$nextNumber"
        return copy(groups = groups + TabGroup(id, nameFor(nextNumber)), nextNumber = nextNumber + 1) to id
    }

    /**
     * Appends [sessionId] to [groupId], taking it out of any other group first.
     * Returns null when the group does not exist or already holds [max] tabs.
     */
    fun add(groupId: String, sessionId: String, max: Int): TabGroups? {
        val target = group(groupId) ?: return null
        if (sessionId in target.members) return this
        if (target.members.size >= max) return null
        return removeMember(sessionId).mapGroup(groupId) { g ->
            val members = g.members + sessionId
            g.copy(members = members, weights = equalWeights(members.size))
        }
    }

    /** Takes [sessionId] out of its group. The group stays (possibly empty) until [prune]. */
    fun removeMember(sessionId: String): TabGroups {
        val g = groupOf(sessionId) ?: return this
        return mapGroup(g.id) {
            val members = it.members - sessionId
            it.copy(
                members = members,
                weights = equalWeights(members.size),
                maximized = it.maximized?.takeIf { m -> m != sessionId },
                focus = it.focus?.takeIf { f -> f != sessionId },
                checked = it.checked - sessionId,
            )
        }
    }

    /** Moves a member within its group; each pane keeps its share. */
    fun move(groupId: String, from: Int, to: Int): TabGroups = mapGroup(groupId) { g ->
        if (from !in g.members.indices) return@mapGroup g
        val target = to.coerceIn(0, g.members.size - 1)
        if (target == from) return@mapGroup g
        val members = g.members.toMutableList().apply { add(target, removeAt(from)) }
        val weights = g.weights.toMutableList().apply { if (size == members.size) add(target, removeAt(from)) }
        g.copy(members = members, weights = if (weights.size == members.size) weights else equalWeights(members.size))
    }

    fun rename(groupId: String, name: String): TabGroups {
        val trimmed = name.trim()
        return if (trimmed.isEmpty()) this else mapGroup(groupId) { it.copy(name = trimmed) }
    }

    /** Removes the group; its tabs become plain tabs again. */
    fun delete(groupId: String): TabGroups = copy(groups = groups.filterNot { it.id == groupId })

    fun setHorizontal(groupId: String, horizontal: Boolean): TabGroups = mapGroup(groupId) { it.copy(horizontal = horizontal) }

    /** Shows [sessionId] alone, or restores all panes when null. */
    fun maximize(groupId: String, sessionId: String?): TabGroups =
        mapGroup(groupId) { it.copy(maximized = sessionId?.takeIf { id -> id in it.members }) }

    /** Ticks or unticks [sessionId] for broadcast input. */
    fun setChecked(sessionId: String, checked: Boolean): TabGroups {
        val g = groupOf(sessionId) ?: return this
        return mapGroup(g.id) { it.copy(checked = if (checked) it.checked + sessionId else it.checked - sessionId) }
    }

    /** Ticks exactly [ids] (those in the group) for broadcast input. */
    fun checkOnly(groupId: String, ids: Collection<String>): TabGroups =
        mapGroup(groupId) { g -> g.copy(checked = ids.filterTo(HashSet()) { it in g.members }) }

    /** Remembers [sessionId] as the focused member of its group. */
    fun focus(sessionId: String): TabGroups {
        val g = groupOf(sessionId) ?: return this
        return if (g.focus == sessionId) this else mapGroup(g.id) { it.copy(focus = sessionId) }
    }

    /**
     * Drags the divider after pane [divider] by [delta] (a fraction of the whole extent). Only the two
     * neighbouring panes trade share, and neither goes below [MIN_SHARE_OF_EQUAL] of an equal share.
     */
    fun resize(groupId: String, divider: Int, delta: Float): TabGroups = mapGroup(groupId) { g ->
        val n = g.members.size
        if (divider !in 0 until n - 1 || !delta.isFinite() || g.weights.size != n) return@mapGroup g
        val min = MIN_SHARE_OF_EQUAL / n
        val a = g.weights[divider]
        val b = g.weights[divider + 1]
        val newA = (a + delta).coerceIn(min, a + b - min)
        g.copy(weights = g.weights.toMutableList().apply { this[divider] = newA; this[divider + 1] = a + b - newA })
    }

    /**
     * Drops closed tabs. Empty groups are removed unless [keepEmpty] (while the editor is open,
     * a just-created group has no members yet).
     */
    fun prune(available: Set<String>, keepEmpty: Boolean = false): TabGroups {
        var changed = false
        val next = groups.mapNotNull { g ->
            val members = g.members.filter { it in available }
            if (members.size != g.members.size) changed = true
            when {
                members.isEmpty() && !keepEmpty -> { changed = true; null }
                members.size == g.members.size -> g
                else -> g.copy(
                    members = members,
                    weights = equalWeights(members.size),
                    maximized = g.maximized?.takeIf { it in members },
                    focus = g.focus?.takeIf { it in members },
                    checked = g.checked.filterTo(HashSet()) { it in members },
                )
            }
        }
        return if (changed) copy(groups = next) else this
    }

    /**
     * Tab bar entries for tabs in [order]. A stacked group appears once, where its first member
     * would be; a single-member group is a plain tab.
     */
    fun units(order: List<String>): List<TabUnit> {
        val emitted = HashSet<String>()
        val out = ArrayList<TabUnit>()
        for (id in order) {
            val g = groupOf(id)
            when {
                g == null || !g.stacked -> out += TabUnit.Solo(id)
                emitted.add(g.id) -> out += TabUnit.Group(g)
            }
        }
        return out
    }

    /** Tab order with each group's members next to each other, in the group's order. */
    fun ordered(order: List<String>): List<String> = flatten(units(order), order.toSet())

    fun flatten(units: List<TabUnit>, available: Set<String>): List<String> = units.flatMap { u ->
        when (u) {
            is TabUnit.Solo -> listOf(u.id)
            is TabUnit.Group -> u.group.members.filter { it in available }
        }
    }

    private fun mapGroup(groupId: String, f: (TabGroup) -> TabGroup): TabGroups =
        copy(groups = groups.map { if (it.id == groupId) f(it) else it })
}

/** Rows each pane keeps at least, with the keyboard open, when deciding how many tabs a group may hold. */
internal const val GROUP_MIN_ROWS = 4
/** A pane's title row plus the divider above it (dp). */
private const val PANE_CHROME_DP = 42f
/** Tab bar, toolbar and system bars that the stack never gets (dp). An estimate, not a measurement. */
private const val SCREEN_CHROME_DP = 150f
/** Line height relative to the font size; the terminal draws a little taller than the glyphs. */
private const val LINE_HEIGHT_RATIO = 1.2f

/**
 * How many tabs one group may hold on this screen: as many panes as fit with the keyboard open
 * while each still shows [GROUP_MIN_ROWS] rows, between [min] and [max].
 * [longSideDp] is the screen's longer side (the stack runs top to bottom in portrait).
 */
internal fun groupMaxTabsFor(longSideDp: Float, keyboardDp: Float, fontSp: Float, fontScale: Float, min: Int, max: Int): Int {
    val pane = PANE_CHROME_DP + GROUP_MIN_ROWS * fontSp * fontScale * LINE_HEIGHT_RATIO
    val available = longSideDp - keyboardDp - SCREEN_CHROME_DP
    if (pane <= 0f || !available.isFinite()) return min
    return (available / pane).toInt().coerceIn(min, max)
}

/**
 * Who receives input typed into [from] (rule fixed with the user):
 * broadcast ON and [from] ticked → every ticked tab of **every** group (groups only exist because
 * one screen cannot hold all the tabs; 1.0.156), in group order then member order;
 * [from] not ticked, or broadcast OFF → [from] alone. Only groups shown as panes count (a one-tab
 * group has no tick box on screen, so a tick left there must not receive unseen). [eligible] filters
 * the others (terminal tabs that are running); [from] itself is always included.
 */
internal fun broadcastTargets(groups: TabGroups, on: Boolean, from: String, eligible: (String) -> Boolean): List<String> {
    if (!on) return listOf(from)
    val g = groups.stackedFor(from) ?: return listOf(from)
    if (from !in g.checked) return listOf(from)
    return groups.groups.filter { it.stacked }.flatMap { grp ->
        grp.members.filter { it in grp.checked && (it == from || eligible(it)) }
    }
}

/** Width of one character of a tab name (11sp monospace), so the default 84dp holds 12 characters as before. */
private const val TAB_CHAR_DP = 7f

/** How many characters of a name fit in a tab bar entry [widthDp] wide (at least 3). */
internal fun tabNameChars(widthDp: Float): Int = if (widthDp.isFinite()) (widthDp / TAB_CHAR_DP).toInt().coerceAtLeast(3) else 3
