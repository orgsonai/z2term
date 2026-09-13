package com.zerotoship.z2term.distro

import java.io.File
import java.io.IOException
import java.nio.file.FileVisitResult
import java.nio.file.Files
import java.nio.file.LinkOption.NOFOLLOW_LINKS
import java.nio.file.Path
import java.nio.file.SimpleFileVisitor
import java.nio.file.attribute.BasicFileAttributes

/** Deletes only distro-owned data. Shared home and symlink targets are never traversed. */
class DistroDataDeletion(private val filesDir: File, private val cacheDir: File, val id: String) {
    class Mounted : IOException("Unmount the distro before deleting it")

    init { require(id.matches(Regex("[a-zA-Z0-9][a-zA-Z0-9_-]*"))) { "Invalid distro id" } }

    private val targets: List<File>
        get() {
            val archives = File(cacheDir, "distros")
            checkParent(archives)
            return listOf(File(filesDir, "distros/$id"), File(filesDir, "home_overlay/$id")) +
                archives.listFiles().orEmpty().filter {
                    it.name.matches(Regex("${Regex.escape(id)}-(arm64-v8a|armeabi-v7a|x86_64|x86)\\.(tgz|tar\\.gz)"))
                }
        }

    private fun checkParent(parent: File) {
        // Android's filesDir itself can contain /data/data aliases; only reject redirected children.
        val expected = File(parent.parentFile.canonicalFile, parent.name)
        check(parent.canonicalFile == expected) { "Redirected distro data directory" }
    }

    fun checkUnmounted(mountInfo: String) {
        val roots = targets.map { target ->
            checkParent(target.parentFile!!)
            File(target.parentFile!!.canonicalFile, target.name).toPath()
        }
        for (line in mountInfo.lineSequence()) {
            val encoded = line.split(' ').getOrNull(4) ?: continue
            val mount = Regex("\\\\([0-7]{3})").replace(encoded) {
                it.groupValues[1].toInt(8).toChar().toString()
            }
            val path = File(mount).canonicalFile.toPath()
            if (roots.any { path.startsWith(it) }) throw Mounted()
        }
    }

    fun delete() {
        // Fail closed if mount information cannot be read. Recheck just before touching files.
        checkUnmounted(File("/proc/self/mountinfo").readText())
        for (target in targets) {
            checkParent(target.parentFile!!)
            val path = target.toPath()
            if (!Files.exists(path, NOFOLLOW_LINKS)) continue
            Files.walkFileTree(path, object : SimpleFileVisitor<Path>() {
                override fun preVisitDirectory(dir: Path, attrs: BasicFileAttributes): FileVisitResult {
                    dir.toFile().setWritable(true, true)
                    dir.toFile().setReadable(true, true)
                    dir.toFile().setExecutable(true, true)
                    return FileVisitResult.CONTINUE
                }
                override fun visitFile(file: Path, attrs: BasicFileAttributes): FileVisitResult {
                    Files.delete(file)
                    return FileVisitResult.CONTINUE
                }
                override fun postVisitDirectory(dir: Path, exc: IOException?): FileVisitResult {
                    if (exc != null) throw exc
                    Files.delete(dir)
                    return FileVisitResult.CONTINUE
                }
            })
        }
    }
}
