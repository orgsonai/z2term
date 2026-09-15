package com.zerotoship.z2term.automation

/** Decode labelled getevent Type-B multitouch frames without consuming the input device. */
internal class ActionInputEvents(private val device: Device, private val screen: ActionDefinition.Screen) {
    data class Axis(val min: Int, val max: Int)
    data class Device(val path: String, val name: String, val x: Axis, val y: Axis)
    data class Frame(val ms: Long, val contacts: List<ActionRecording.Contact>, val released: List<ActionRecording.Contact> = emptyList())
    private data class Slot(var id: Int = -1, var x: Int? = null, var y: Int? = null)
    private val slots = linkedMapOf<Int, Slot>()
    private var slot = 0
    private val released = mutableListOf<ActionRecording.Contact>()
    fun line(text: String): Frame? {
        val match = Regex("\\[\\s*([0-9]+)\\.([0-9]+)\\]\\s+(?:/dev/input/event[0-9]+:\\s+)?(EV_\\w+)\\s+(\\w+)\\s+([0-9a-fA-F]+)").find(text) ?: return null
        val ms = match.groupValues[1].toLong() * 1000 + match.groupValues[2].padEnd(3, '0').take(3).toLong()
        val code = match.groupValues[4]
        val value = match.groupValues[5].toLong(16).toInt()
        require(code != "SYN_DROPPED") { "Input events were dropped; record again" }
        if (code == "ABS_MT_SLOT") { require(value in 0..31); slot = value }
        else {
            val state = slots.getOrPut(slot) { Slot() }
            when (code) {
                "ABS_MT_TRACKING_ID" -> {
                    if (value < 0 && state.id >= 0 && state.x != null && state.y != null)
                        released += map(state.id, state.x!!, state.y!!)
                    state.id = value
                }
                "ABS_MT_POSITION_X" -> state.x = value
                "ABS_MT_POSITION_Y" -> state.y = value
                "SYN_REPORT" -> return Frame(ms, slots.values.filter { it.id >= 0 }.map { s ->
                    val x = requireNotNull(s.x) { "Touch X is unavailable" }
                    val y = requireNotNull(s.y) { "Touch Y is unavailable" }
                    map(s.id, x, y)
                }, released.toList()).also { released.clear() }
            }
        }
        return null
    }

    private fun map(id: Int, rawX: Int, rawY: Int): ActionRecording.Contact {
        val x = ((rawX - device.x.min).toFloat() / (device.x.max - device.x.min)).coerceIn(0f, 1f)
        val y = ((rawY - device.y.min).toFloat() / (device.y.max - device.y.min)).coerceIn(0f, 1f)
        val (u, v) = when (screen.rotation) {
            1 -> y to 1 - x
            2 -> 1 - x to 1 - y
            3 -> 1 - y to x
            else -> x to y
        }
        return ActionRecording.Contact(id, u * (screen.width - 1), v * (screen.height - 1))
    }

    companion object {
        fun devices(text: String): List<Device> =
            text.split(Regex("(?=add device [0-9]+:)")).mapNotNull { block ->
                val path = Regex("add device [0-9]+: (/dev/input/event[0-9]+)").find(block)?.groupValues?.get(1) ?: return@mapNotNull null
                if ("ABS_MT_SLOT" !in block || "ABS_MT_TRACKING_ID" !in block || "INPUT_PROP_DIRECT" !in block) return@mapNotNull null
                fun axis(name: String): Axis? {
                    val match = Regex(name + "[^\\n]*min (-?[0-9]+), max (-?[0-9]+)").find(block) ?: return null
                    val min = match.groupValues[1].toIntOrNull() ?: return null
                    val max = match.groupValues[2].toIntOrNull() ?: return null
                    return if (max > min) Axis(min, max) else null
                }
                val x = axis("ABS_MT_POSITION_X") ?: return@mapNotNull null
                val y = axis("ABS_MT_POSITION_Y") ?: return@mapNotNull null
                val name = Regex("name:\\s*\"([^\"]+)\"").find(block)?.groupValues?.get(1) ?: path
                Device(path, name, x, y)
            }
    }
}
