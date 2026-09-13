package com.zerotoship.z2term.distro

import java.io.Closeable

/** Coordinates launches/installations with deletion without holding a thread lock during I/O. */
object DistroOperations {
    class Busy : IllegalStateException("Distro is starting, installing, or being deleted")
    private val users = mutableMapOf<String, Int>()
    private val deleting = mutableSetOf<String>()

    @Synchronized
    fun use(id: String): Closeable {
        if (id in deleting) throw Busy()
        users[id] = (users[id] ?: 0) + 1
        return once {
            val count = users.getValue(id) - 1
            if (count == 0) users.remove(id) else users[id] = count
        }
    }

    @Synchronized
    fun delete(id: String): Closeable {
        if (id in deleting || (users[id] ?: 0) > 0) throw Busy()
        deleting.add(id)
        return once { deleting.remove(id) }
    }

    private fun once(action: () -> Unit): Closeable {
        var closed = false
        return Closeable {
            synchronized(this) {
                if (!closed) { closed = true; action() }
            }
        }
    }
}
