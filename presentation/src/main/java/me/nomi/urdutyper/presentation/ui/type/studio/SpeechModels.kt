package me.nomi.urdutyper.presentation.ui.type.studio

import android.content.Context
import java.io.File
import java.io.InputStream
import java.net.HttpURLConnection
import java.net.URL
import java.security.MessageDigest
import java.util.concurrent.CancellationException
import java.util.concurrent.locks.ReentrantLock

class SpeechModels(context: Context) {
    private val appContext = context.applicationContext
    data class Model(val id: String, val title: String, val size: String, val sha1: String) {
        val filename get() = "ggml-$id.bin"
        val url get() = "https://huggingface.co/ggerganov/whisper.cpp/resolve/main/$filename"
    }
    companion object {
        val all = listOf(
            Model("tiny","Tiny","75 MiB · lighter / less accurate","bd577a113a864445d4c299885e0cb97d4ba92b5f"),
            Model("base","Base","142 MiB · balanced","465707469ff3a37a2b9b8d8f89f2f99de7299dac"),
            Model("small","Small","466 MiB · more memory / slower","55356645c2b361a969dfd0ef2c5a50d530afd8d5")
        )
        private val installs = ReentrantLock()
        private const val MAX = 600L*1024*1024
    }
    private val root = File(context.filesDir,"speech-models").apply { check(isDirectory || mkdirs()) }
    private val preferences = context.getSharedPreferences("offline-speech",0)
    fun selected() = all.firstOrNull { it.id == preferences.getString("model","base") } ?: all[1]
    fun select(model: Model) { preferences.edit().putString("model",model.id).apply() }
    fun file(model: Model) = File(root,model.filename)
    fun installed(model: Model) = file(model).isFile && file(model).length() > 1024
    fun delete(model: Model) { check(file(model).delete() || !file(model).exists()) { "Could not remove model" } }
    private fun checkCancellation(cancelled: () -> Boolean) { if (cancelled()) throw CancellationException("Model operation cancelled") }
    fun checkMemory(model: Model) {
        val manager = appContext.getSystemService(Context.ACTIVITY_SERVICE) as android.app.ActivityManager
        val info = android.app.ActivityManager.MemoryInfo(); manager.getMemoryInfo(info)
        val needed = when(model.id) { "small" -> 1050L; "base" -> 500L; else -> 350L }*1024*1024
        check(!info.lowMemory && info.availMem >= needed) { "Not enough free memory. Choose Tiny or close other apps first." }
    }
    fun verify(model: Model, cancelled: () -> Boolean = { false }): File {
        val target = file(model)
        check(target.isFile) { "Download or import ${model.title} first" }
        val digest = MessageDigest.getInstance("SHA-1")
        target.inputStream().use { input -> val bytes = ByteArray(65536); while (true) { checkCancellation(cancelled); val n = input.read(bytes); if (n < 0) break; digest.update(bytes,0,n) } }
        check(digest.digest().joinToString("") { "%02x".format(it.toInt() and 255) } == model.sha1) { "Model verification failed. Remove it and download again." }
        return target
    }
    private fun receive(input: InputStream, expected: Model?, total: Long, cancelled: () -> Boolean, progress: (Long,Long) -> Unit): Model {
        check(installs.tryLock()) { "Another model installation is finishing; try again" }
        val pending = try { File.createTempFile("speech-", ".part",root) } catch (error: Throwable) { installs.unlock(); throw error }
        try {
            check(root.usableSpace > (if (total > 0) total else 16L*1024*1024)+16L*1024*1024) { "Not enough free storage for the model" }
            val digest = MessageDigest.getInstance("SHA-1"); var copied = 0L
            pending.outputStream().use { output ->
                val bytes = ByteArray(65536)
                while (true) { checkCancellation(cancelled); val n = input.read(bytes); if (n < 0) break; copied += n; require(copied <= MAX) { "Model exceeds 600 MiB" }; output.write(bytes,0,n); digest.update(bytes,0,n); progress(copied,total) }
                output.fd.sync()
            }
            checkCancellation(cancelled)
            val sha = digest.digest().joinToString("") { "%02x".format(it.toInt() and 255) }
            val model = all.firstOrNull { it.sha1 == sha } ?: error("Unsupported model. Import an original multilingual ggml Tiny, Base or Small model.")
            require(expected == null || expected.id == model.id) { "Downloaded model did not match its checksum" }
            java.nio.file.Files.move(pending.toPath(),file(model).toPath(),java.nio.file.StandardCopyOption.REPLACE_EXISTING,java.nio.file.StandardCopyOption.ATOMIC_MOVE)
            select(model); return model
        } finally { pending.delete(); installs.unlock() }
    }
    fun importModel(input: InputStream, cancelled: () -> Boolean, progress: (Long,Long) -> Unit = { _,_ -> }) = input.use { receive(it,null,-1,cancelled,progress) }
    fun download(model: Model, cancelled: () -> Boolean, progress: (Long,Long) -> Unit): Model {
        var url = URL(model.url)
        repeat(8) {
            checkCancellation(cancelled); require(url.protocol == "https") { "Model download needs HTTPS" }
            val connection = url.openConnection() as HttpURLConnection
            connection.connectTimeout = 20000; connection.readTimeout = 20000; connection.instanceFollowRedirects = false
            try {
                val code = connection.responseCode
                if (code in listOf(301,302,303,307,308)) { url = URL(url,connection.getHeaderField("Location") ?: error("Missing download redirect")) }
                else { check(code == 200) { "Download failed ($code). Check connection or import a model file." }; return connection.inputStream.use { receive(it,model,connection.contentLengthLong,cancelled,progress) } }
            } finally { connection.disconnect() }
        }
        error("Too many model download redirects")
    }
}
