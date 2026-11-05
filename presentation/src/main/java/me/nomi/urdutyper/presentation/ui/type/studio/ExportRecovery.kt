package me.nomi.urdutyper.presentation.ui.type.studio

import android.content.Context
import com.google.gson.Gson
import java.io.File

/** Snapshot journal survives a process interruption; retry restarts encoding from this snapshot. */
class ExportRecovery(context: Context) {
    data class Request(val document: StudioDocument, val format: Int, val share: Boolean)
    private val root = File(context.filesDir,"studio-export-recovery").apply { mkdirs() }
    private val file = File(root,"request.json")
    @Synchronized fun save(request: Request) {
        val temp = File(root,"request.tmp")
        try { temp.outputStream().use { it.write(Gson().toJson(request).toByteArray()); it.fd.sync() }
            java.nio.file.Files.move(temp.toPath(),file.toPath(),java.nio.file.StandardCopyOption.REPLACE_EXISTING,java.nio.file.StandardCopyOption.ATOMIC_MOVE)
        } finally { temp.delete() }
    }
    @Synchronized fun pending(): Request? = runCatching { if (file.isFile) Gson().fromJson(file.readText(),Request::class.java) else null }.getOrNull()
    @Synchronized fun clear() { file.delete() }
    fun cleanInterruptedFiles(cache: File, output: File) {
        cache.listFiles().orEmpty().filter { it.name.startsWith("studio-video-") || it.name.startsWith("studio-audio-") }.forEach { it.delete() }
        output.listFiles().orEmpty().filter { it.name.startsWith("UT") && it.name.endsWith(".pending") }.forEach { it.delete() }
    }
}
