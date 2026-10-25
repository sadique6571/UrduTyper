package me.nomi.urdutyper.presentation.utils.common

import java.io.File
import java.io.IOException
import java.nio.file.Files
import java.nio.file.StandardCopyOption

/**
 * Local JSON persistence for editable projects.
 *
 * This storage layer is independent of Android so it can be unit tested.
 * Callers should keep large rendering/export work off the main thread.
 */
class EditableProjectStore(private val root: File) {
    init {
        if (!root.exists() && !root.mkdirs()) {
            throw IOException("Could not create project directory")
        }
        if (!root.isDirectory) throw IOException("Project path is not a directory")
    }

    @Synchronized
    @Throws(IOException::class)
    fun save(project: EditableProject) {
        val target = projectFile(project.id)
        val temp = File(root, ".${project.id}.tmp")
        try {
            temp.writeText(project.toJson(), Charsets.UTF_8)
            try {
                Files.move(
                    temp.toPath(),
                    target.toPath(),
                    StandardCopyOption.REPLACE_EXISTING,
                    StandardCopyOption.ATOMIC_MOVE
                )
            } catch (_: Exception) {
                // Some filesystems do not support atomic moves. The temp file
                // is in the same directory, so a normal replacement is next.
                Files.move(temp.toPath(), target.toPath(), StandardCopyOption.REPLACE_EXISTING)
            }
        } finally {
            if (temp.exists()) temp.delete()
        }
    }

    @Synchronized
    fun load(id: String): EditableProject? {
        val file = projectFile(id)
        if (!file.isFile) return null
        return try {
            EditableProject.fromJson(file.readText(Charsets.UTF_8))
        } catch (_: Exception) {
            null
        }
    }

    @Synchronized
    fun listIds(): List<String> =
        root.listFiles()
            .orEmpty()
            .filter { it.isFile && it.extension == "json" }
            .map { it.nameWithoutExtension }
            .filter { ID_PATTERN.matches(it) }
            .sorted()

    @Synchronized
    fun delete(id: String): Boolean {
        val file = projectFile(id)
        return !file.exists() || file.delete()
    }

    private fun projectFile(id: String): File {
        require(ID_PATTERN.matches(id)) { "Invalid project id" }
        return File(root, id + ".json")
    }

    companion object {
        private val ID_PATTERN = Regex("[a-zA-Z0-9_-]{1,80}")
    }
}
