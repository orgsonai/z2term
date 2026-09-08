package com.zerotoship.z2term.edge

import com.zerotoship.z2term.proot.z2EdgeScripts
import org.junit.Assert.*
import org.junit.Test
import java.nio.file.Files

class EdgeCliTest {
    @Test fun pickerWaitAndHandleArgumentsSurviveShellWrapper() {
        val dir = Files.createTempDirectory("edge-cli-test").toFile()
        try {
            val api = dir.resolve("z2api")
            api.writeText("#!/bin/sh\nprintf '%s\\n' \"\${Z2API_WAIT:-default}\" \"\$@\"\n")
            check(api.setExecutable(true))
            val scripts = z2EdgeScripts("ja")
            fun run(name: String, vararg args: String): Pair<Int, String> {
                val script = dir.resolve(name).apply { writeText(scripts.getValue(name)) }
                val process = ProcessBuilder(listOf("sh", script.path) + args)
                    .redirectErrorStream(true).apply {
                        environment()["PATH"] = dir.path + ":" + System.getenv("PATH")
                    }.start()
                val output = process.inputStream.bufferedReader().readText()
                return process.waitFor() to output
            }
            assertEquals(0 to "1250\n1\napp\npick\n", run("z2-app", "pick"))
            assertNotEquals(0, run("z2-app", "pick", "unexpected").first)
            assertEquals(0 to "default\n1\nedge\nhandle\nmain\nbar\n--label\na b\n--open\nswipe\n",
                run("z2-edge", "handle", "main", "bar", "--label", "a b", "--open", "swipe"))
            assertEquals(0 to "default\n1\nedge\ntoggle\n", run("z2-edge", "toggle"))
        } finally { dir.deleteRecursively() }
    }
}
