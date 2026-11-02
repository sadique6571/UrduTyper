package me.nomi.urdutyper.presentation.ui.type.studio

/** Import human-reviewed SRT timings; no speech recognition or external service is implied. */
object StudioCaptions {
    data class Caption(val startMs: Long, val endMs: Long, val text: String)
    fun parse(srt: String): List<Caption> {
        require(srt.length <= 100000) { "Caption file must be under 100 KB" }
        val pattern = Regex("(\\d{2}):(\\d{2}):(\\d{2})[,.](\\d{3})\\s*-->\\s*(\\d{2}):(\\d{2}):(\\d{2})[,.](\\d{3})")
        val result = srt.replace("\r\n", "\n").trim().split(Regex("\n\\s*\n")).mapNotNull { block ->
            val lines = block.lines()
            val index = lines.indexOfFirst { pattern.containsMatchIn(it) }
            if (index < 0) return@mapNotNull null
            val match = pattern.find(lines[index])!!
            fun time(offset: Int): Long {
                val values = (1..4).map { match.groupValues[offset + it].toLong() }
                require(values[1] < 60 && values[2] < 60)
                return values[0] * 3600000 + values[1] * 60000 + values[2] * 1000 + values[3]
            }
            val start = time(0); val end = time(4)
            val text = lines.drop(index + 1).joinToString("\n").replace(Regex("<[^>]*>"), "").trim()
            require(start in 0..59999 && end in 1..60000 && end > start && text.length <= 2000)
            if (text.isBlank()) null else Caption(start, end, text)
        }
        require(result.isNotEmpty() && result.size <= 100) { "Use 1–100 captions within 60 seconds" }
        return result.sortedBy { it.startMs }
    }
    fun apply(document: StudioDocument, captions: List<Caption>): StudioDocument {
        var offset = 0L
        val scenes = document.orderedScenes().map { scene ->
            val start = offset; val end = start + scene.video.durationMs
            offset = end
            val layers = captions.filter { it.endMs > start && it.startMs < end }.map { caption ->
                val from = maxOf(caption.startMs, start) - start
                val until = minOf(caption.endMs, end) - start
                StudioLayer(text = caption.text, x = document.width / 2f, y = document.height * .82f,
                    width = document.width * .84f, size = document.width * .048f, color = android.graphics.Color.WHITE,
                    effects = listOf("Outline"), animation = LayerAnimation("highlight", from, until, until - from))
            }
            require(scene.layers.size + layers.size <= 100) { "Too many caption layers in one scene" }
            scene.copy(layers = scene.layers + layers)
        }
        return scenes.first { it.id == document.sceneId }.applyTo(document).copy(scenes = scenes)
    }
}
