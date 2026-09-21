package com.zerotoship.z2term.proot

import java.io.File
import java.nio.file.Files
import org.junit.Assert.*
import org.junit.Test

class DocumentCliTest {
    @Test fun binaryStagingSafeNamesExitStatusAndCleanup() {
        val dir = Files.createTempDirectory("document-cli").toFile()
        try {
            val stage = File(dir, "stage").apply { mkdirs() }
            val api = File(dir, "api").apply {
                writeText("""#!/bin/sh
if [ "${'$'}3" = pick ]; then
  [ "${'$'}FAIL" = 1 ] && exit 9
  printf '%s\n' 'z2term-inbox/id/a file.txt'
else
  cp '${stage.path}/'"${'$'}4"/data '${dir.path}/received'
  printf '%s' "${'$'}5" > '${dir.path}/name'
  [ "${'$'}FAIL" = 1 ] && exit 9
fi
exit 0
""")
                setExecutable(true)
            }
            val script = File(dir, "file.sh").apply { writeText(z2ApiScripts().getValue("z2-file")
                .replace("/storage/app/z2api", stage.path).replace("/usr/local/bin/z2api", api.path)) }
            fun run(fail: Boolean, vararg args: String): Pair<Int, String> {
                val p = ProcessBuilder(listOf("sh", script.path) + args).apply {
                    environment()["HOME"] = dir.path; environment()["FAIL"] = if (fail) "1" else "0"
                }.redirectErrorStream(true).start()
                val text = p.inputStream.bufferedReader().readText()
                return p.waitFor() to text
            }
            assertEquals(0 to "${dir.path}/z2term-inbox/id/a file.txt\n", run(false, "pick"))
            assertEquals(9 to "", run(true, "pick"))
            val source = File(dir, "a '\" \$(touch injected).bin").apply { writeBytes(ByteArray(70001) { it.toByte() }) }
            for (fail in listOf(false, true)) {
                assertEquals(if (fail) 9 else 0, run(fail, "save", source.path, "application/octet-stream").first)
                assertArrayEquals(source.readBytes(), File(dir, "received").readBytes())
                assertEquals(source.name, File(dir, "name").readText())
                assertTrue(stage.listFiles()!!.isEmpty())
                assertFalse(File(dir, "injected").exists())
            }
            assertEquals(2, run(false, "save", File(dir, "missing").path).first)
        } finally { dir.deleteRecursively() }
    }
}
