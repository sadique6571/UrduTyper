package me.nomi.urdutyper.presentation.ui.type.studio

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import kotlin.math.roundToInt

class StudioAnimationRenderer(private val renderer: StudioRenderer) {
    private val suggestions = object : LinkedHashMap<String,AtmosphereSuggestion>(16,.75f,true) {
        override fun removeEldestEntry(eldest: MutableMap.MutableEntry<String,AtmosphereSuggestion>?) = size > 16
    }
    fun suggestion(document: StudioDocument): AtmosphereSuggestion {
        val name = if (document.backgroundImage.isNotBlank()) document.backgroundName else document.backgroundStyle
        val key = "${document.width}x${document.height}|$name|${document.backgroundImage}|${document.backgroundColor}|${document.gradientColor}|${document.backgroundVisible}"
        suggestions[key]?.let { return it }
        if (!document.backgroundVisible) return AtmosphereSuggestion("none","Background is transparent")
        val sample = renderer.bitmap(document.copy(layers = emptyList()),32,false)
        val pixels = IntArray(sample.width*sample.height)
        sample.getPixels(pixels,0,sample.width,0,0,sample.width,sample.height); sample.recycle()
        return AtmosphereHints.suggest(name,pixels).also { suggestions[key] = it }
    }

    fun draw(canvas: Canvas, document: StudioDocument, timeMs: Long) {
        val scenes = document.orderedScenes()
        var offset = 0L
        val time = timeMs.coerceIn(0, document.movieDurationMs() - 1)
        val index = scenes.indexOfFirst { scene ->
            if (time < offset + scene.video.durationMs) true else { offset += scene.video.durationMs; false }
        }.coerceAtLeast(0)
        val scene = scenes[index]
        val local = time - offset
        val transition = minOf(scene.transitionMs, scene.video.durationMs / 2)
        if (index > 0 && scene.transition != "none" && local < transition) {
            val previous = scenes[index - 1]
            drawScene(canvas, previous.applyTo(document), previous.video.durationMs - 1)
            val p = (local.toFloat() / transition).coerceIn(0f, 1f)
            canvas.save()
            when (scene.transition) {
                "slide" -> canvas.translate(document.width * (1f - p), 0f)
                "dissolve" -> {
                    val clip = android.graphics.Path()
                    for (y in 0 until 12) for (x in 0 until 12) {
                        if (((x * 37 + y * 17) % 144) / 144f <= p)
                            clip.addRect(x * document.width / 12f, y * document.height / 12f,
                                (x + 1) * document.width / 12f, (y + 1) * document.height / 12f, android.graphics.Path.Direction.CW)
                    }
                    canvas.clipPath(clip)
                }
            }
            if (scene.transition == "fade") canvas.saveLayerAlpha(0f, 0f, document.width.toFloat(), document.height.toFloat(), (255 * p).toInt())
            drawScene(canvas, scene.applyTo(document), local)
            if (scene.transition == "fade") canvas.restore()
            canvas.restore()
        } else drawScene(canvas, scene.applyTo(document), local)
    }
    private fun drawScene(canvas: Canvas, document: StudioDocument, timeMs: Long) {
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
        val atmosphere = if (document.video.atmosphere == "auto") suggestion(document).effect else document.video.atmosphere
        StudioAtmosphere.draw(canvas,w,h,timeMs,atmosphere,document.video.atmosphereIntensity,document.video.atmosphereSpeed)
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

