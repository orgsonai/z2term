package com.zerotoship.z2term.automation

/** Record complete touch episodes, preserving pauses between them and trimming only outer silence. */
internal class ActionRecording {
    data class Contact(val id: Int, val x: Float, val y: Float)
    data class Episode(val startMs: Long, val endMs: Long, val tracks: List<List<ActionGesture.Point>>)
    private data class Finger(val start: Long, var last: Contact, val samples: ActionGesture.Recording)
    private val active = linkedMapOf<Int, Finger>()
    private val finishedTracks = mutableListOf<List<ActionGesture.Point>>()
    private val episodes = mutableListOf<Episode>()
    private var episodeStart = 0L
    private var lastTime = 0L
    val count get() = episodes.size
    val touching get() = active.isNotEmpty()
    val durationMs get() = episodes.lastOrNull()?.let { it.endMs - episodes.first().startMs } ?: 0L

    fun frame(timeMs: Long, contacts: List<Contact>, released: List<Contact> = emptyList()) {
        require(contacts.size <= 2 && contacts.map { it.id }.distinct().size == contacts.size) { "Record at most two fingers" }
        require(timeMs >= lastTime) { "Touch timestamps moved backwards" }
        lastTime = timeMs
        val ids = contacts.map { it.id }.toSet()
        // Linux can report the last position and UP within the same input frame.
        released.forEach { contact -> active[contact.id]?.last = contact }
        // End old contacts first, so an UP followed by a new DOWN at the same time starts a new episode.
        active.keys.filter { it !in ids }.forEach { id ->
            val finger = active.remove(id)!!
            finger.samples.add(finger.last.x, finger.last.y, timeMs - finger.start, up = true)
            finishedTracks += finger.samples.points.map { it.copy(ms = it.ms + finger.start - episodeStart) }
        }
        if (active.isEmpty() && finishedTracks.isNotEmpty()) {
            require(episodes.size < 32) { "Recording is limited to 32 touch episodes" }
            val tracks = finishedTracks.sortedBy { it.first().ms }.toList()
            episodes += Episode(episodeStart, episodeStart + tracks.maxOf { it.last().ms }, tracks)
            finishedTracks.clear()
        }
        contacts.forEach { contact ->
            val finger = active[contact.id]
            if (finger == null) {
                if (active.isEmpty()) episodeStart = timeMs
                require(active.size + finishedTracks.size < 2) { "Lift both fingers before starting another two-finger gesture" }
                val samples = ActionGesture.Recording().apply { add(contact.x, contact.y, 0) }
                active[contact.id] = Finger(timeMs, contact, samples)
            } else {
                finger.last = contact
                finger.samples.add(contact.x, contact.y, timeMs - finger.start)
            }
        }
    }

    fun preview(): List<List<ActionGesture.Point>> = if (touching)
        finishedTracks + active.values.map { it.samples.points.toList() }
        else episodes.lastOrNull()?.tracks.orEmpty()

    fun cancelTouch() { active.clear(); finishedTracks.clear() }
    fun finish(timeMs: Long) { if (touching) frame(maxOf(lastTime, timeMs), emptyList()) }

    fun source(unit: String, screen: ActionDefinition.Screen): String {
        require(episodes.isNotEmpty() && !touching) { "Record at least one complete touch" }
        val lines = mutableListOf("repeat 1", "  # Recorded touch sequence", "  target current")
        var end = episodes.first().startMs
        episodes.forEach { episode ->
            var pause = (episode.startMs - end).coerceAtLeast(0)
            while (pause > 0) {
                val ms = minOf(pause, 30_000)
                lines += "  wait $ms"; pause -= ms
            }
            lines += "  touch $unit " + episode.tracks.joinToString(" | ") { track ->
                ActionGesture.encode(ActionGesture.convert(track, "px", unit, screen))
            }
            end = episode.endMs
        }
        lines += "end"
        val source = lines.joinToString("\n")
        // Bound total bytes and steps before returning the recording to an editor.
        ActionDefinition.parse("version=2\ntimeout=300\nscreen=$screen\n$source")
        return source
    }
}
