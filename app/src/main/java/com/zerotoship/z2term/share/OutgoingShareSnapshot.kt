package com.zerotoship.z2term.share

import java.io.File
import java.util.UUID

/** Only explicitly staged files are copied into the directory exposed by FileProvider. */
internal object OutgoingShareSnapshot {
    const val MAX_FILES = 32
    const val MAX_BYTES = 512L * 1024 * 1024
    const val KEEP_MS = 24L * 60 * 60 * 1000

    fun prepare(staging: File, cache: File, token: String, names: List<String>,
                limit: Long = MAX_BYTES): List<File> {
        require(Regex("share-[a-zA-Z0-9]+").matches(token)) { "Invalid share request" }
        require(names.size in 1..MAX_FILES) { "Select 1–$MAX_FILES files" }
        val source = File(staging, token)
        require(source.isDirectory && source.canonicalFile == File(staging.canonicalFile, token))
        val destination = File(cache, UUID.randomUUID().toString())
        check(destination.mkdirs()) { "Cannot prepare shared files" }
        var total = 0L
        try {
            return names.mapIndexed { index, name ->
                val input = File(source, index.toString())
                require(input.isFile && input.canonicalFile == File(source.canonicalFile, index.toString())) {
                    "Unreadable shared file"
                }
                val safeName = SharedPayload.limitFileName(name.substringAfterLast('/').replace(Regex("[\\p{Cntrl}\\\\]"), "_")
                    .trim('.').ifBlank { "file" })
                // Separate directories preserve equal filenames without overwriting another selection.
                val output = File(File(destination, index.toString()).apply { check(mkdir()) }, safeName)
                input.inputStream().use { from ->
                    output.outputStream().use { to ->
                        val buffer = ByteArray(64 * 1024)
                        while (true) {
                            val n = from.read(buffer)
                            if (n < 0) break
                            total += n
                            require(total <= limit) { "Shared files exceed 512 MiB" }
                            to.write(buffer, 0, n)
                        }
                    }
                }
                output
            }
        } catch (e: Exception) {
            destination.deleteRecursively()
            throw e
        }
    }

    fun cleanup(cache: File, now: Long = System.currentTimeMillis()) {
        cache.listFiles().orEmpty().filter { now - it.lastModified() > KEEP_MS }.forEach { it.deleteRecursively() }
    }
}
