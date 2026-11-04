package me.nomi.urdutyper.presentation.ui.type.studio

/** Pure editing rules shared by numeric controls, range handles and tests. */
object VideoEditRules {
    fun maximumSceneDuration(document: StudioDocument) = 60000 - (document.movieDurationMs() - document.video.durationMs)
    fun animation(value: LayerAnimation, sceneDuration: Long): LayerAnimation {
        val minimum = if (value.effect == "none") 100L else 200L
        val start = value.startMs.coerceIn(0,sceneDuration-minimum)
        val end = value.endMs.coerceIn(start+minimum,sceneDuration)
        val maximumReveal = (end-start-100).coerceAtLeast(100)
        return value.copy(startMs = start,endMs = end,revealMs = value.revealMs.coerceIn(100,maximumReveal),
            cursor = value.cursor && value.effect == "typing",repeat = value.repeat && value.effect != "none")
    }
    fun duration(document: StudioDocument, requested: Long): StudioDocument {
        val next = requested.coerceIn(1000,maximumSceneDuration(document))
        return document.copy(video = document.video.copy(durationMs = next),layers = document.layers.map { layer ->
            val value = layer.animation.copy(endMs = if (layer.animation.endMs >= document.video.durationMs) next else layer.animation.endMs)
            layer.copy(animation = animation(value,next))
        })
    }
    fun timing(document: StudioDocument, id: String, value: LayerAnimation): StudioDocument = document.copy(layers = document.layers.map {
        if (it.id == id && !it.locked) it.copy(animation = animation(value,document.video.durationMs)) else it
    })
    fun exportPreview(document: StudioDocument, format: Int): StudioDocument = if (format == 2) document.copy(audio = emptyList(),silentPreviewVoices = document.audio.filter { it.role == "voice" }) else document
}
