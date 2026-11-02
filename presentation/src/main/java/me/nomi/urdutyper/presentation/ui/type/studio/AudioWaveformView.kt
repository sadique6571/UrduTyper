package me.nomi.urdutyper.presentation.ui.type.studio

import android.content.Context
import android.graphics.Canvas
import android.graphics.Paint
import android.view.View

class AudioWaveformView(context: Context) : View(context) {
    var peaks = FloatArray(0)
        set(value) { field = value; invalidate() }
    private val paint = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = 0xff62c7ba.toInt(); strokeWidth = context.resources.displayMetrics.density * 2 }
    init { contentDescription = "Selected audio waveform"; setBackgroundColor(LuxeTheme.surface) }
    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)
        if (peaks.isEmpty()) { paint.alpha = 80; canvas.drawLine(8f,height/2f,width-8f,height/2f,paint); return }
        paint.alpha = 255
        peaks.forEachIndexed { index, peak ->
            val x = width * (index + .5f)/peaks.size
            val y = peak.coerceIn(.025f,1f) * height * .44f
            canvas.drawLine(x,height/2f-y,x,height/2f+y,paint)
        }
    }
}
