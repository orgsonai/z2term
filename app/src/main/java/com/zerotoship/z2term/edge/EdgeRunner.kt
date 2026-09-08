package com.zerotoship.z2term.edge

import android.content.Context
import android.os.Handler
import android.os.Looper
import com.zerotoship.z2term.service.HeadlessRun
import java.io.File
import java.util.UUID
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.Executors
import java.util.concurrent.atomic.AtomicBoolean

/** Uses the existing distro execution path; stdout, stdin and selection values are separate from code. */
class EdgeRunner(private val context: Context) {
    data class Result(val output: String, val error: String? = null)
    private class Job(val name: String) {
        val cancelled = AtomicBoolean(false)
        val delivered = AtomicBoolean(false)
        @Volatile var failure: String? = null
        @Volatile var timer: Runnable? = null
    }
    private val main = Handler(Looper.getMainLooper())
    private val jobs = ConcurrentHashMap<String, Job>()

    fun isRunning(key: String): Boolean = jobs.containsKey(key)

    fun run(key: String, command: String, timeout: Long, input: String? = null, value: String? = null,
            done: (Result) -> Unit): Boolean {
        if (command.isBlank() || jobs.size >= 4) return false
        val token = UUID.randomUUID().toString()
        val job = Job("edge-$token")
        if (jobs.putIfAbsent(key, job) != null) return false
        workers.execute {
            val dir = File(context.filesDir, "shared_home/.z2term/edge/.runtime").apply { mkdirs() }
            val output = File(dir, "$token.out")
            val errors = File(dir, "$token.err")
            val status = File(dir, "$token.status")
            val linux = "/root/.z2term/edge/.runtime/$token"
            val quote = HeadlessRun::shSingleQuote
            // Drain both streams through bounded collectors. Do not limit files the user's command writes.
            val invoke = "sh -c ${quote(command)} z2-edge" + (value?.let { " ${quote(it)}" } ?: "")
            val pipe = input?.let { "printf %s ${quote(it)} | " }.orEmpty()
            val script = "{ { $pipe$invoke; printf '%s' \"\$?\" > ${quote("$linux.status")}; " +
                "} 2>&1 1>&3 | { head -c 65537 > ${quote("$linux.err")}; cat > /dev/null; }; " +
                "} 3>&1 | { head -c 65537 > ${quote("$linux.out")}; cat > /dev/null; }"
            fun read(file: File): String = if (file.isFile) file.inputStream().use { stream ->
                val bytes = ByteArray(65536)
                var total = 0
                while (total < bytes.size) {
                    val n = stream.read(bytes, total, bytes.size - total)
                    if (n <= 0) break
                    total += n
                }
                String(bytes, 0, total, Charsets.UTF_8)
            } else ""
            fun complete(failure: String? = null) {
                if (!job.delivered.compareAndSet(false, true)) return
                job.timer?.let { main.removeCallbacks(it) }
                val result = runCatching {
                    val code = read(status).trim()
                    val error = failure ?: job.failure ?: if (code != "0") read(errors).trim().take(2000).ifBlank { "Command exited: $code" }
                        else if (output.length() > 65536) "Output exceeds 64 KiB" else null
                    Result(read(output), error)
                }.getOrElse { Result("", it.message ?: "Cannot read output") }
                output.delete(); errors.delete(); status.delete()
                jobs.remove(key, job)
                main.post { if (!job.cancelled.get()) done(result) }
            }
            if (job.cancelled.get()) { complete(); return@execute }
            val launched = runCatching {
                HeadlessRun.launch(context, script, null, job.name, onExit = { complete() })
            }.getOrDefault(false)
            if (!launched) complete("Cannot start command in the selected Linux environment")
            else if (job.cancelled.get()) HeadlessRun.stop(job.name)
            else {
                val timer = Runnable {
                if (jobs[key] === job && !job.delivered.get()) {
                    workers.execute {
                        job.failure = "Command timed out (${timeout}s)"
                        HeadlessRun.stop(job.name)
                        complete()
                    }
                }
                }
                job.timer = timer
                if (!job.delivered.get()) main.postDelayed(timer, timeout * 1000)
            }
        }
        return true
    }

    fun cancelReads() = cancel { it.startsWith("read:") }
    fun cancelRead(target: String) = cancel { it == "read:$target" }
    fun cancelAll() = cancel { true }

    private fun cancel(matches: (String) -> Boolean) {
        jobs.entries.filter { matches(it.key) }.forEach { (key, job) ->
            job.cancelled.set(true)
            job.timer?.let { main.removeCallbacks(it) }
            workers.execute { HeadlessRun.stop(job.name) }
            jobs.remove(key, job)
        }
    }

    companion object {
        private val workers = Executors.newFixedThreadPool(2) { task ->
            Thread(task, "edge-command").apply { isDaemon = true }
        }
    }
}
