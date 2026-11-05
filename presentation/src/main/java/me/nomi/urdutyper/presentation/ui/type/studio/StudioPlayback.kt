package me.nomi.urdutyper.presentation.ui.type.studio

import android.media.MediaPlayer
import android.os.Handler
import android.os.Looper
import android.os.SystemClock

/** Owns preview audio and the clock. Always release before leaving the editor. */
class StudioPlayback(private val frame: (Long, Boolean) -> Unit, private val error: (String) -> Unit) {
    private val handler = Handler(Looper.getMainLooper())
    private val players = mutableListOf<Pair<StudioAudio, MediaPlayer>>()
    private val ready = mutableSetOf<MediaPlayer>()
    private var document: StudioDocument? = null
    var position = 0L; private set
    var playing = false; private set
    private var start = 0L
    var loopWindow: LongRange? = null
    var frameIntervalMs = 40L
    private val ticker = object : Runnable {
        override fun run() {
            val doc = document ?: return
            if (!playing) return
            position = SystemClock.elapsedRealtime() - start
            val window = loopWindow
            if (window != null && position >= window.last) { position = window.first; start = SystemClock.elapsedRealtime()-position; sync(true) }
            if (position >= doc.movieDurationMs()) {
                if (doc.video.loopPreview) { position = 0; start = SystemClock.elapsedRealtime(); sync(true) }
                else { position = doc.movieDurationMs(); playing = false }
            }
            sync(false); frame(position, playing)
            if (playing) handler.postDelayed(this, frameIntervalMs.coerceIn(33,100))
        }
    }
    fun toggle(doc: StudioDocument) {
        if (playing) { pause(); return }
        releasePlayers(); document = doc
        doc.audio.take(2).forEach { track ->
            val player = MediaPlayer()
            try {
                player.setDataSource(track.path); players.add(track to player)
                player.setOnPreparedListener { prepared ->
                    if (players.any { it.second === prepared }) { ready.add(prepared); sync(true) }
                }
                player.setOnErrorListener { broken, _, _ ->
                    ready.remove(broken); players.removeAll { it.second === broken }; broken.release()
                    error("Cannot preview ${track.name}"); true
                }
                player.prepareAsync()
            } catch (exception: Exception) { players.removeAll { it.second === player }; player.release(); error("Cannot preview ${track.name}") }
        }
        if (position >= doc.movieDurationMs()) position = 0
        loopWindow?.let { if(position !in it) position=it.first }
        playing = true; start = SystemClock.elapsedRealtime() - position
        sync(true); handler.post(ticker)
    }
    fun seek(doc: StudioDocument, ms: Long) {
        pause(); document = doc; position = ms.coerceIn(0, doc.movieDurationMs() - 1); frame(position, false)
    }
    fun pause() { playing = false; handler.removeCallbacks(ticker); players.filter { it.second in ready }.forEach { runCatching { it.second.pause() } }; frame(position, false) }
    private fun sync(seek: Boolean) {
        val doc = document ?: return
        players.filter { it.second in ready }.forEach { (track, player) -> runCatching {
            val local = position - track.startMs
            val active = playing && local in 0 until track.durationMs() && position < doc.movieDurationMs()
            val gain = doc.musicGain(track, position); player.setVolume(gain, gain)
            if (active) {
                if (seek || !player.isPlaying) player.seekTo((track.trimStartMs + local).toInt())
                if (!player.isPlaying) player.start()
            } else if (player.isPlaying) player.pause()
        } }
    }
    private fun releasePlayers() { players.forEach { runCatching { it.second.release() } }; players.clear(); ready.clear() }
    fun release() { playing = false; handler.removeCallbacks(ticker); releasePlayers(); document = null }
}
