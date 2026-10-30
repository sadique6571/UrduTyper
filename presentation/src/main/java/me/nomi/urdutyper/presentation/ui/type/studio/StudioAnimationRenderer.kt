package me.nomi.urdutyper.presentation.ui.type.studio

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import kotlin.math.roundToInt

class StudioAnimationRenderer(private val renderer: StudioRenderer) {
    fun draw(canvas: Canvas, document: StudioDocument, timeMs: Long) {
        canvas.save()
        val w = document.width.toFloat(); val h = document.height.toFloat()
        canvas.clipRect(0f, 0f, w, h)
        canvas.drawColor(Color.WHITE)
        val p = (timeMs.toFloat() / document.video.durationMs.coerceAtLeast(1)).coerceIn(0f, 1f)
        canvas.save()
        when (document.video.backgroundMotion) {
            "zoom" -> canvas.scale(1f + .12f * p, 1f + .12f * p, w / 2, h / 2)
            "pan" -> { canvas.translate(w * (.04f - .08f * p), 0f); canvas.scale(1.12f, 1.12f, w / 2, h / 2) }
        }
        renderer.draw(canvas, document.copy(layers = emptyList()))
        canvas.restore()
        val atmosphere = document.video.atmosphere
        if (atmosphere != "none") {
            val paint = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = 0x66ffffff; strokeWidth = w / 700 }
            for (i in 0 until 45) {
                val x = ((i * 71 % 101) / 101f) * w
                val y = ((i * 137 % 997) / 997f * h + timeMs * h / (if (atmosphere == "rain") 1500f else 9000f)) % h
                if (atmosphere == "rain") canvas.drawLine(x, y, x - w / 90, y + h / 30, paint)
                else canvas.drawCircle(x, y, w / (180 + i % 5 * 60), paint)
            }
        }
        val layers = document.layers.map { original ->
            var animated = AnimationTimeline.layer(original, timeMs)
            if (original.kind == "text" && original.animation.effect in listOf("typing", "words") && animated.visible) {
                animated = animated.copy(y = original.y - (renderer.height(original) - renderer.height(animated)) / 2)
            }
            animated
        }
        renderer.draw(canvas, document.copy(backgroundVisible = false, layers = layers))
        canvas.restore()
    }
    fun bitmap(document: StudioDocument, width: Int, timeMs: Long): Bitmap {
        val height = (width.toFloat() / document.width * document.height).roundToInt().coerceAtLeast(1)
        return Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888).also {
            val canvas = Canvas(it); canvas.scale(width.toFloat() / document.width, height.toFloat() / document.height)
            draw(canvas, document, timeMs)
        }
    }
}
