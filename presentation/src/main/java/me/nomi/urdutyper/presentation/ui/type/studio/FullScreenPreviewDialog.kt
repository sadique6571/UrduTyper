package me.nomi.urdutyper.presentation.ui.type.studio

import android.app.Dialog
import android.content.Context
import android.os.Bundle
import android.view.View
import android.view.ViewGroup
import android.view.WindowManager
import android.widget.Button
import android.widget.LinearLayout
import android.widget.SeekBar
import me.nomi.urdutyper.R

/** Uses the export renderer; selection, grids, gestures and safe guides are excluded. */
class FullScreenPreviewDialog(context: Context, private val document: StudioDocument, private val videoMode: Boolean, private val startAtMs: Long = 0, private val autoPlay: Boolean = false) : Dialog(context,R.style.Theme_UrduTyper) {
    private lateinit var playback: StudioPlayback
    private lateinit var canvas: StudioCanvas
    private var positioned = false
    override fun onCreate(state: Bundle?) {
        super.onCreate(state)
        val root = LinearLayout(context).apply { orientation = LinearLayout.VERTICAL; setBackgroundColor(LuxeTheme.background) }
        canvas = StudioCanvas(context,StudioRenderer(context,StudioFonts(context))).apply {
            this.document = this@FullScreenPreviewDialog.document.copy(safeMargins = false)
            selectedId = null; showGrid = false; previewTimeMs = if (videoMode) 0 else null
            contentDescription = "Full-screen artwork preview"
            setOnTouchListener { _,_ -> true }
        }
        root.addView(canvas,LinearLayout.LayoutParams(-1,0,1f))
        val row = LinearLayout(context)
        val play = Button(context).apply { text = "Play"; setTextColor(LuxeTheme.gold) }
        val seek = SeekBar(context).apply { max = document.movieDurationMs().toInt(); contentDescription = "Preview position" }
        playback = StudioPlayback({ position, playing ->
            canvas.previewTimeMs = position.coerceAtMost(document.movieDurationMs() - 1); canvas.invalidate()
            seek.progress = position.toInt(); play.text = if (playing) "Pause" else "Play"
        }, { android.widget.Toast.makeText(context,it,android.widget.Toast.LENGTH_SHORT).show() })
        if (videoMode) {
            play.setOnClickListener { playback.toggle(document) }; row.addView(play)
            seek.setOnSeekBarChangeListener(object : SeekBar.OnSeekBarChangeListener {
                override fun onStartTrackingTouch(bar: SeekBar?) { playback.pause() }
                override fun onProgressChanged(bar: SeekBar?, value: Int, user: Boolean) { if (user) playback.seek(document,value.toLong()) }
                override fun onStopTrackingTouch(bar: SeekBar?) = Unit
            })
            row.addView(seek,LinearLayout.LayoutParams(0,-2,1f))
        } else row.addView(View(context),LinearLayout.LayoutParams(0,1,1f))
        row.addView(Button(context).apply { text = "Close"; contentDescription = "Close full-screen preview"; setTextColor(LuxeTheme.gold); setOnClickListener { dismiss() } })
        root.addView(row)
        setContentView(root)
    }
    override fun onStart() {
        super.onStart(); window?.setLayout(ViewGroup.LayoutParams.MATCH_PARENT,ViewGroup.LayoutParams.MATCH_PARENT)
        window?.addFlags(WindowManager.LayoutParams.FLAG_FULLSCREEN)
        if (videoMode && !positioned) { positioned = true; playback.seek(document,startAtMs); if (autoPlay) playback.toggle(document) }
    }
    override fun onStop() { if (::playback.isInitialized) playback.release(); super.onStop() }
}
