package me.nomi.urdutyper.presentation.ui.type.studio

import kotlin.math.*

data class LayerAnimation(
    val effect: String = "none",
    val startMs: Long = 0,
    val endMs: Long = 60000,
    val revealMs: Long = 3000,
    val cursor: Boolean = false,
    val repeat: Boolean = false
)

data class VideoSettings(
    val durationMs: Long = 8000,
    val backgroundMotion: String = "none",
    val atmosphere: String = "none",
    val loopPreview: Boolean = true
)

data class StudioAudio(
    val id: String = java.util.UUID.randomUUID().toString(),
    val path: String = "",
    val name: String = "Audio",
    val role: String = "voice",
    val sourceDurationMs: Long = 60000,
    val startMs: Long = 0,
    val trimStartMs: Long = 0,
    val trimEndMs: Long = 60000,
    val volume: Float = 1f,
    val fadeInMs: Long = 0,
    val fadeOutMs: Long = 0
) {
    fun durationMs() = (trimEndMs - trimStartMs).coerceAtLeast(0)
    fun gainAt(timeMs: Long, sceneEndMs: Long = Long.MAX_VALUE): Float {
        val local = timeMs - startMs
        val visibleDuration = minOf(durationMs(), (sceneEndMs - startMs).coerceAtLeast(0))
        if (local < 0 || local >= visibleDuration) return 0f
        val fadeIn = if (fadeInMs > 0) (local.toFloat() / fadeInMs).coerceIn(0f, 1f) else 1f
        val fadeOut = if (fadeOutMs > 0) ((visibleDuration - local).toFloat() / fadeOutMs).coerceIn(0f, 1f) else 1f
        return volume.coerceIn(0f, 1f) * minOf(fadeIn, fadeOut)
    }
}

object AnimationTimeline {
    val effects = listOf("none", "typing", "words", "fade", "slide", "zoom", "highlight")
    /** UTF-16 offsets after complete clusters: no split surrogate, vowel mark or ZWJ sequence. */
    fun clusters(text: String): List<Int> {
        val result = mutableListOf<Int>()
        var index = 0
        var previous = -1
        var regionalCount = 0
        while (index < text.length) {
            val cp = Character.codePointAt(text, index)
            val type = Character.getType(cp)
            val regional = cp in 0x1f1e6..0x1f1ff
            val attach = type == Character.NON_SPACING_MARK.toInt() || type == Character.COMBINING_SPACING_MARK.toInt() ||
                type == Character.ENCLOSING_MARK.toInt() || cp == 0x200c || cp == 0x200d || previous == 0x200d ||
                cp in 0xfe00..0xfe0f || cp in 0x1f3fb..0x1f3ff || (regional && regionalCount % 2 == 1)
            index += Character.charCount(cp)
            if (attach && result.isNotEmpty()) result[result.lastIndex] = index else result.add(index)
            regionalCount = if (regional) regionalCount + 1 else 0
            previous = cp
        }
        return result
    }
    fun words(text: String): List<IntRange> = Regex("\\S+").findAll(text).map { it.range }.toList()
    fun progress(animation: LayerAnimation, timeMs: Long): Float {
        var elapsed = (timeMs - animation.startMs).coerceAtLeast(0)
        val duration = animation.revealMs.coerceAtLeast(100)
        if (animation.repeat) elapsed %= duration + 1000
        return (elapsed.toFloat() / duration).coerceIn(0f, 1f)
    }
    fun layer(layer: StudioLayer, timeMs: Long): StudioLayer {
        val a = layer.animation
        if (!layer.visible || timeMs < a.startMs || timeMs >= a.endMs) return layer.copy(visible = false)
        val p = progress(a, timeMs)
        return when (a.effect) {
            "typing" -> {
                val boundaries = clusters(layer.text)
                val count = floor(boundaries.size * p).toInt().coerceIn(0, boundaries.size)
                val prefix = if (count == 0) "" else layer.text.substring(0, boundaries[count - 1])
                layer.copy(text = prefix + if (a.cursor && p < 1 && timeMs / 450 % 2 == 0L) "\u200f│" else "")
            }
            "words" -> {
                val words = words(layer.text)
                val count = floor(words.size * p).toInt().coerceIn(0, words.size)
                layer.copy(text = if (count == 0) "" else layer.text.substring(0, words[count - 1].last + 1))
            }
            "fade" -> layer.copy(opacity = layer.opacity * p)
            "slide" -> layer.copy(y = layer.y + layer.size * 2 * (1 - p), opacity = layer.opacity * p)
            "zoom" -> layer.copy(size = layer.size * (.65f + .35f * p), width = layer.width * (.65f + .35f * p), height = layer.height * (.65f + .35f * p), opacity = layer.opacity * p)
            "highlight" -> layer.copy(activeWord = if (p >= 1) -1 else floor(words(layer.text).size * p).toInt())
            else -> layer
        }
    }
}
