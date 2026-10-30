package me.nomi.urdutyper.presentation.ui.type.studio

import android.app.Dialog
import android.content.Context
import android.media.MediaPlayer
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.os.SystemClock
import android.view.ViewGroup
import android.widget.*
import kotlin.math.roundToInt

class AnimationPreviewDialog(context: Context, private val document: StudioDocument) : Dialog(context) {
    private val handler = Handler(Looper.getMainLooper())
    private val players = mutableListOf<Pair<StudioAudio, MediaPlayer>>()
    private var position = 0L
    private var start = 0L
    private var playing = false
    private lateinit var canvas: StudioCanvas
    private lateinit var seek: SeekBar
    private lateinit var play: Button
    private lateinit var time: TextView
    private val ticker = object : Runnable {
        override fun run() {
            if (!playing) return
            position = SystemClock.elapsedRealtime() - start
            if (position >= document.video.durationMs) {
                if (document.video.loopPreview) { position = 0; start = SystemClock.elapsedRealtime(); syncAudio(true) }
                else { position = document.video.durationMs; playing = false }
            }
            draw(); syncAudio(false)
            if (playing) handler.postDelayed(this, 33) else play.text = "Play"
        }
    }
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setTitle("Animation preview")
        val root = LinearLayout(context).apply { orientation = LinearLayout.VERTICAL; setPadding(12,12,12,12) }
        canvas = StudioCanvas(context, StudioRenderer(context, StudioFonts(context))).apply { this.document = this@AnimationPreviewDialog.document; previewTimeMs = 0 }
        root.addView(canvas, LinearLayout.LayoutParams(-1,0,1f))
        time = TextView(context); root.addView(time)
        seek = SeekBar(context).apply {
            max = document.video.durationMs.toInt()
            setOnSeekBarChangeListener(object : SeekBar.OnSeekBarChangeListener {
                override fun onStartTrackingTouch(bar: SeekBar?) { playing = false; handler.removeCallbacks(ticker); players.forEach { it.second.pause() }; play.text = "Play" }
                override fun onProgressChanged(bar: SeekBar?, value: Int, fromUser: Boolean) { if (fromUser) { position = value.toLong(); draw(); syncAudio(true) } }
                override fun onStopTrackingTouch(bar: SeekBar?) = Unit
            })
        }; root.addView(seek)
        val row = LinearLayout(context)
        play = Button(context).apply { text = "Play"; setOnClickListener {
            playing = !playing
            if (playing) { if (position >= document.video.durationMs) position = 0; start = SystemClock.elapsedRealtime() - position; syncAudio(true); handler.post(ticker) }
            else { handler.removeCallbacks(ticker); players.forEach { it.second.pause() } }
            text = if (playing) "Pause" else "Play"
        } }; row.addView(play)
        row.addView(Button(context).apply { text = "Close"; setOnClickListener { dismiss() } }); root.addView(row)
        setContentView(root)
        window?.setLayout(ViewGroup.LayoutParams.MATCH_PARENT, (context.resources.displayMetrics.heightPixels * .85f).roundToInt())
        document.audio.take(2).forEach { audio ->
            runCatching {
                val player = MediaPlayer()
                try { player.setDataSource(audio.path); player.prepare(); players.add(audio to player) }
                catch (error: Exception) { player.release(); throw error }
            }.onFailure { Toast.makeText(context, "Audio unavailable: ${audio.name}", Toast.LENGTH_SHORT).show() }
        }
        draw()
    }
    private fun draw() {
        canvas.previewTimeMs = position.coerceAtMost(document.video.durationMs - 1); canvas.invalidate(); seek.progress = position.toInt()
        time.text = String.format(java.util.Locale.ROOT,"%.1f / %.1f seconds",position / 1000f,document.video.durationMs / 1000f)
    }
    private fun syncAudio(seekNow: Boolean) {
        players.forEach { (track, player) ->
            val local = position - track.startMs
            val active = local >= 0 && local < track.durationMs() && position < document.video.durationMs
            runCatching {
                val gain = track.gainAt(position,document.video.durationMs); player.setVolume(gain,gain)
                if (active && playing) {
                    if (seekNow || !player.isPlaying) player.seekTo((track.trimStartMs + local).toInt())
                    if (!player.isPlaying) player.start()
                } else if (player.isPlaying) player.pause()
            }
        }
    }
    override fun onStop() {
        playing = false; handler.removeCallbacks(ticker)
        players.forEach { runCatching { it.second.release() } }; players.clear()
        super.onStop()
    }
}
