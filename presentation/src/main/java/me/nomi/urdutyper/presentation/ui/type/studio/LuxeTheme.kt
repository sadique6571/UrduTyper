package me.nomi.urdutyper.presentation.ui.type.studio

import android.graphics.*
import android.graphics.drawable.Drawable
import androidx.core.graphics.PathParser

/** Shared chrome palette; document colors and exported artwork are independent. */
object LuxeTheme {
    val background = 0xff08121e.toInt()
    val surface = 0xff111f2f.toInt()
    val raised = 0xff192b3e.toInt()
    val border = 0xff28394c.toInt()
    val gold = 0xfff6c66d.toInt()
    val text = 0xfff4f0e8.toInt()
    val muted = 0xffa7b4c5.toInt()
}

class LuxeIcon(private val name: String, color: Int = LuxeTheme.text) : Drawable() {
    private val paint = Paint(Paint.ANTI_ALIAS_FLAG).apply { this.color = color; style = Paint.Style.STROKE; strokeWidth = 1.65f; strokeCap = Paint.Cap.ROUND; strokeJoin = Paint.Join.ROUND }
    private val path = PathParser.createPathFromPathData(when (name) {
        "Text", "+ Text", "Edit words", "Edit" -> "M5,5 H19 M12,5 V20 M8,20 H16"
        "Fonts" -> "M3,19 L9,5 L15,19 M5,15 H13 M17,19 V12 C21,10 23,13 23,16 V19 M17,16 H23"
        "Color" -> "M12,3 C6,3 2,7 2,12 C2,18 7,22 12,21 C16,20 12,17 15,15 C17,13 22,17 22,11 C22,6 18,3 12,3 Z M7,9 h0.1 M11,6 h0.1 M16,7 h0.1 M6,14 h0.1"
        "Effects", "Animate", "Animate / Video" -> "M12,2 L15,9 L22,12 L15,15 L12,22 L9,15 L2,12 L9,9 Z"
        "Layers", "Arrange" -> "M12,3 L22,9 L12,15 L2,9 Z M2,13 L12,19 L22,13 M2,17 L12,23 L22,17"
        "Media", "+ Image", "Background" -> "M3,4 H21 V20 H3 Z M3,17 L9,10 L14,15 L17,11 L21,17 M16,8 h0.1"
        "Voice", "Voice / music" -> "M9,4 C9,0 15,0 15,4 V12 C15,16 9,16 9,12 Z M5,10 V12 C5,21 19,21 19,12 V10 M12,19 V23 M8,23 H16"
        "Music" -> "M9,18 V5 L21,3 V16 M9,9 L21,7 M9,18 C3,15 2,23 7,22 C9,22 9,20 9,18 M21,16 C15,13 14,21 19,20 C21,20 21,18 21,16"
        "Video", "Play" -> "M7,3 L21,12 L7,21 Z"
        "Undo" -> "M9,4 L3,10 L9,16 M3,10 H15 C23,10 23,21 15,21"
        "Redo" -> "M15,4 L21,10 L15,16 M21,10 H9 C1,10 1,21 9,21"
        "Back" -> "M15,4 L7,12 L15,20 M7,12 H22"
        "Scenes", "Templates" -> "M3,3 H10 V10 H3 Z M14,3 H21 V10 H14 Z M3,14 H10 V21 H3 Z M14,14 H21 V21 H14 Z"
        "Style", "Transform", "Crop / opacity" -> "M7,2 V17 H22 M2,7 H17 V22"
        "Project", "More" -> "M5,12 h0.1 M12,12 h0.1 M19,12 h0.1"
        "Size", "Canvas size" -> "M3,8 V3 H8 M16,3 H21 V8 M21,16 V21 H16 M8,21 H3 V16"
        "Delete" -> "M3,6 H21 M9,6 V3 H15 V6 M6,6 L7,22 H17 L18,6 M10,10 V18 M14,10 V18"
        else -> "M12,4 V20 M4,12 H20"
    })!!
    override fun draw(canvas: Canvas) {
        canvas.save(); canvas.translate(bounds.left.toFloat(), bounds.top.toFloat())
        canvas.scale(bounds.width() / 24f, bounds.height() / 24f); canvas.drawPath(path, paint); canvas.restore()
    }
    override fun setTint(color: Int) { paint.color = color; invalidateSelf() }
    override fun setTintList(tint: android.content.res.ColorStateList?) {
        if (tint != null) { paint.color = tint.getColorForState(state,tint.defaultColor); invalidateSelf() }
    }
    override fun setAlpha(alpha: Int) { paint.alpha = alpha; invalidateSelf() }
    override fun setColorFilter(filter: ColorFilter?) { paint.colorFilter = filter; invalidateSelf() }
    @Deprecated("Deprecated in Java") override fun getOpacity() = PixelFormat.TRANSLUCENT
    override fun getIntrinsicWidth() = 24
    override fun getIntrinsicHeight() = 24
}

class CanvasRatioIcon(private val ratio: Float) : Drawable() {
    private val paint = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = LuxeTheme.gold; style = Paint.Style.STROKE; strokeWidth = 1.5f }
    override fun draw(canvas: Canvas) {
        val w = if (ratio > 1f) 20f else 20f * ratio
        val h = if (ratio > 1f) 20f / ratio else 20f
        canvas.save(); canvas.translate(bounds.left.toFloat(),bounds.top.toFloat()); canvas.scale(bounds.width()/24f,bounds.height()/24f)
        canvas.drawRoundRect(RectF((24-w)/2,(24-h)/2,(24+w)/2,(24+h)/2),1f,1f,paint); canvas.restore()
    }
    override fun setAlpha(value: Int) { paint.alpha = value }
    override fun setColorFilter(value: ColorFilter?) { paint.colorFilter = value }
    @Deprecated("Deprecated in Java") override fun getOpacity() = PixelFormat.TRANSLUCENT
    override fun getIntrinsicWidth() = 24
    override fun getIntrinsicHeight() = 24
}
