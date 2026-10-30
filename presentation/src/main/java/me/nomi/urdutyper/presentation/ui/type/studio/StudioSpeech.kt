package me.nomi.urdutyper.presentation.ui.type.studio

import android.content.Context
import android.os.Handler
import android.os.Looper
import android.speech.tts.TextToSpeech
import android.speech.tts.UtteranceProgressListener
import kotlinx.coroutines.suspendCancellableCoroutine
import java.io.File
import java.util.Locale
import java.util.UUID
import java.util.concurrent.atomic.AtomicReference
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException

/** Optional OS-provided Urdu synthesis. Never substitute an English voice for Urdu. */
object StudioSpeech {
    suspend fun synthesize(context: Context, text: String, target: File): File = suspendCancellableCoroutine { continuation ->
        val main = Handler(Looper.getMainLooper())
        val engine = AtomicReference<TextToSpeech?>()
        val id = UUID.randomUUID().toString()
        var finished = false
        val timeout = Runnable {
            if (!finished) {
                finished = true; engine.get()?.shutdown(); target.delete()
                if (continuation.isActive) continuation.resumeWithException(IllegalStateException("Voice generation timed out. Try a shorter text or record your voice."))
            }
        }
        fun finish(error: String?) {
            main.post {
                if (!finished) {
                    finished = true; main.removeCallbacks(timeout); engine.get()?.shutdown()
                    if (error != null) {
                        target.delete()
                        if (continuation.isActive) continuation.resumeWithException(IllegalStateException(error))
                    } else if (continuation.isActive) continuation.resume(target)
                    else target.delete()
                }
            }
        }
        val tts = TextToSpeech(context.applicationContext) { status ->
            main.post {
                if (!finished && continuation.isActive) {
                    val current = engine.get()
                    if (current == null || status != TextToSpeech.SUCCESS) finish("No text-to-speech engine is available. Install an Urdu voice or use recording/audio import.")
                    else if (text.isBlank() || text.length > TextToSpeech.getMaxSpeechInputLength()) finish("Choose a shorter, non-empty text for voice generation.")
                    else {
                        val available = current.isLanguageAvailable(Locale("ur","PK"))
                        if (available < TextToSpeech.LANG_AVAILABLE || current.setLanguage(Locale("ur","PK")) < TextToSpeech.LANG_AVAILABLE) {
                            finish("Your phone's voice engine has no installed Urdu voice. Install Urdu in Android Text-to-speech settings, or record/import audio.")
                        } else {
                            current.voices?.filter { it.locale.language == "ur" }?.sortedBy { it.isNetworkConnectionRequired }?.firstOrNull()?.let { current.setVoice(it) }
                            current.setSpeechRate(.9f)
                            current.setOnUtteranceProgressListener(object : UtteranceProgressListener() {
                                override fun onStart(utteranceId: String?) = Unit
                                override fun onDone(utteranceId: String?) { if (utteranceId == id) finish(if (target.isFile && target.length() > 44) null else "The voice engine produced no audio.") }
                                @Deprecated("Android callback") override fun onError(utteranceId: String?) { if (utteranceId == id) finish("Urdu voice generation failed. Check the installed voice data or use recording/audio import.") }
                                override fun onError(utteranceId: String?, errorCode: Int) { onError(utteranceId) }
                            })
                            if (current.synthesizeToFile(text, android.os.Bundle(), target, id) != TextToSpeech.SUCCESS) finish("The voice engine could not create audio.")
                        }
                    }
                }
            }
        }
        engine.set(tts)
        main.postDelayed(timeout,120000)
        continuation.invokeOnCancellation {
            main.post { finished = true; main.removeCallbacks(timeout); engine.get()?.stop(); engine.get()?.shutdown(); target.delete() }
        }
    }
}
