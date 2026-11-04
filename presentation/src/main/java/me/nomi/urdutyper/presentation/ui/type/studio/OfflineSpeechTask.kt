package me.nomi.urdutyper.presentation.ui.type.studio

import com.google.gson.Gson
import java.util.concurrent.CancellationException
import java.util.concurrent.locks.ReentrantLock

internal object WhisperNative {
    init { System.loadLibrary("studio_whisper") }
    external fun create(): Long
    external fun cancel(handle: Long)
    external fun progress(handle: Long): Int
    external fun destroy(handle: Long)
    external fun run(handle: Long, path: String, pcm: FloatArray, language: String, threads: Int): ByteArray
}
data class SpeechWord(val text: String = "", val startMs: Long = 0, val endMs: Long = 0, val confidence: Float = 0f, val estimated: Boolean = false)
class OfflineSpeechTask {
    companion object { private val engine = ReentrantLock() }
    @Volatile private var cancelled = false
    @Volatile var stage = "Preparing audio"; private set
    private val gate = Any(); private var handle = 0L
    fun cancel() { cancelled = true; synchronized(gate) { if (handle != 0L) WhisperNative.cancel(handle) } }
    fun progress() = synchronized(gate) { if (handle == 0L) 0 else WhisperNative.progress(handle) }
    fun run(models: SpeechModels, model: SpeechModels.Model, track: StudioAudio, language: String = "ur"): List<SpeechWord> {
        check(engine.tryLock()) { "Another speech analysis is finishing; try again" }
        try {
            require(java.io.File(track.path).isFile) { "Voice file is missing. Import or record it again." }
            require(language in listOf("ur","en","auto")); require(track.durationMs() in 200..60000) { "Trim voice to 0.2–60 seconds" }
            models.checkMemory(model)
            require(track.startMs >= 0 && track.startMs+track.durationMs() <= 60000) { "Move or trim voice to fit the 60-second timeline before analysis" }
            stage = "Verifying model"; val file = models.verify(model) { cancelled }
            stage = "Preparing voice"; val pcm = StudioAudioEngine.speechPcm(track) { cancelled }
            if (cancelled) throw CancellationException("Speech analysis cancelled")
            check(pcm.any { kotlin.math.abs(it) > .001f }) { "No audible signal found. Check the voice recording and trim." }
            stage = "Loading / analysing voice"
            val ptr = synchronized(gate) { WhisperNative.create().also { handle = it; if (cancelled) WhisperNative.cancel(it) } }
            val bytes = WhisperNative.run(ptr,file.path,pcm,language,Runtime.getRuntime().availableProcessors().coerceIn(1,4))
            if (cancelled) throw CancellationException("Speech analysis cancelled")
            val raw = Gson().fromJson(String(bytes,Charsets.UTF_8),Array<SpeechWord>::class.java).toList()
            val words = raw.flatMap { item ->
                val parts = Regex("\\S+").findAll(item.text).map { it.value }.toList()
                parts.mapIndexed { index, word -> item.copy(text = word,
                    startMs = item.startMs+(item.endMs-item.startMs)*index/parts.size,
                    endMs = item.startMs+(item.endMs-item.startMs)*(index+1)/parts.size,estimated = parts.size > 1) }
            }.filter { it.text.isNotBlank() && it.startMs >= 0 && it.startMs < track.durationMs() && it.endMs > it.startMs }
                .map { it.copy(endMs = it.endMs.coerceAtMost(track.durationMs()),confidence = if (it.confidence.isFinite()) it.confidence.coerceIn(0f,1f) else 0f) }
                .sortedBy { it.startMs }.take(1000)
            check(words.isNotEmpty()) { "No words found. Try clearer voice, the correct language or a larger model." }
            return words
        } catch (error: LinkageError) { throw IllegalStateException("Offline speech engine is unavailable for this device. Install the full APK.",error) } finally {
            synchronized(gate) { if (handle != 0L) { WhisperNative.destroy(handle); handle = 0 } }
            engine.unlock()
        }
    }
}
