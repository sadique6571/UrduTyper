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
class FullScreenPreviewDialog(context: Context, private val document: StudioDocument, private val videoMode: Boolean, private val startAtMs: Long = 0, private val autoPlay: Boolean = false, private val loopRange: LongRange? = null) : Dialog(context,R.style.Theme_UrduTyper) {
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
        val row = LinearLayout(context).apply { gravity=android.view.Gravity.CENTER_VERTICAL; setPadding(dp(8),dp(4),dp(8),dp(8)) }
        val play = previewButton(true).apply { text = "Play" }
        val seek = SeekBar(context).apply { max = document.movieDurationMs().toInt(); contentDescription = "Preview position" }
        playback = StudioPlayback({ position, playing ->
            canvas.previewTimeMs = position.coerceAtMost(document.movieDurationMs() - 1); canvas.invalidate()
            seek.progress = position.toInt(); play.text = if (playing) "Pause" else "Play"
        }, { android.widget.Toast.makeText(context,it,android.widget.Toast.LENGTH_SHORT).show() })
        playback.loopWindow = loopRange
        if (videoMode) {
            val quality = previewButton().apply {
                text = "Quality · ${PreviewQuality.selected(context)}"
                contentDescription = "Preview quality"
                setTextColor(LuxeTheme.gold)
                setOnClickListener {
                    val labels=PreviewQuality.labels.toTypedArray()
                    android.app.AlertDialog.Builder(context).setTitle("Preview quality · export unchanged").setSingleChoiceItems(labels,labels.indexOf(PreviewQuality.selected(context))) { dialog,index ->
                        PreviewQuality.select(context,labels[index]); text="Quality · ${labels[index]}"; canvas.invalidate(); dialog.dismiss()
                    }.show()
                }
            }
            val tools=LinearLayout(context).apply { setPadding(dp(8),dp(4),dp(8),dp(4)) }
            tools.addView(quality,LinearLayout.LayoutParams(0,dp(48),1f).apply { marginEnd=dp(6) })
            if(loopRange!=null) tools.addView(previewButton().apply { text="Loop word: on"; setTextColor(LuxeTheme.gold); setOnClickListener { playback.loopWindow=if(playback.loopWindow==null) loopRange else null; text=if(playback.loopWindow==null) "Loop word: off" else "Loop word: on" } },LinearLayout.LayoutParams(0,dp(48),1f))
            root.addView(tools)
            play.setOnClickListener { playback.toggle(document) }; row.addView(play)
            seek.setOnSeekBarChangeListener(object : SeekBar.OnSeekBarChangeListener {
                override fun onStartTrackingTouch(bar: SeekBar?) { playback.pause() }
                override fun onProgressChanged(bar: SeekBar?, value: Int, user: Boolean) { if (user) playback.seek(document,value.toLong()) }
                override fun onStopTrackingTouch(bar: SeekBar?) = Unit
            })
            row.addView(seek,LinearLayout.LayoutParams(0,-2,1f))
        } else row.addView(View(context),LinearLayout.LayoutParams(0,1,1f))
        row.addView(previewButton().apply { text = "Close"; contentDescription = "Close full-screen preview"; setTextColor(LuxeTheme.gold); setOnClickListener { dismiss() } })
        root.addView(row)
        setContentView(root)
    }
    private fun dp(value: Int)=(value*context.resources.displayMetrics.density).toInt()
    private fun previewButton(primary: Boolean=false)=com.google.android.material.button.MaterialButton(context,null,com.google.android.material.R.attr.materialButtonOutlinedStyle).apply {
        isAllCaps=false; textSize=12f; letterSpacing=0f; minWidth=0; minimumWidth=0; minHeight=dp(48)
        insetTop=0; insetBottom=0; cornerRadius=dp(12); setPadding(dp(12),0,dp(12),0)
        backgroundTintList=android.content.res.ColorStateList.valueOf(if(primary) LuxeTheme.gold else LuxeTheme.surface)
        strokeColor=android.content.res.ColorStateList.valueOf(if(primary) LuxeTheme.gold else LuxeTheme.border)
        setTextColor(if(primary) LuxeTheme.background else LuxeTheme.gold)
    }
    override fun onStart() {
        super.onStart(); window?.setLayout(ViewGroup.LayoutParams.MATCH_PARENT,ViewGroup.LayoutParams.MATCH_PARENT)
        window?.addFlags(WindowManager.LayoutParams.FLAG_FULLSCREEN)
        if (videoMode && !positioned) { positioned = true; playback.seek(document,startAtMs); if (autoPlay) playback.toggle(document) }
    }
    override fun onStop() { if (::playback.isInitialized) playback.release(); super.onStop() }
}
