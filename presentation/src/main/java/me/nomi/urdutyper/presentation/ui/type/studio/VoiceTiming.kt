package me.nomi.urdutyper.presentation.ui.type.studio

/** Duration alignment, not speech recognition: reveal pace is estimated from text length. */
object VoiceTiming {
    private fun voice(document: StudioDocument): StudioAudio {
        val track = document.audio.firstOrNull { it.role == "voice" } ?: error("Import or record a voice track first")
        require(track.durationMs() >= 200 && track.startMs >= 0) { "Voice must contain at least 0.2 seconds" }
        require(track.startMs+track.durationMs() <= 60000) { "Trim voice to fit the 60-second video limit" }
        return track
    }
    private fun aligned(layer: StudioLayer, start: Long, end: Long, duration: Long): StudioLayer {
        if (layer.kind != "text" || layer.locked || !layer.visible) return layer
        val effect = layer.animation.effect.takeIf { it in listOf("typing","words","highlight") } ?: "words"
        return layer.copy(animation = VideoEditRules.animation(layer.animation.copy(effect = effect,startMs = start,endMs = duration,
            revealMs = (end-start-100).coerceAtLeast(100),repeat = false,cursor = false),duration))
    }
    fun selected(document: StudioDocument, selectedId: String?): StudioDocument {
        val layer = document.layers.firstOrNull { it.id == selectedId && it.kind == "text" && !it.locked && it.visible && it.text.isNotBlank() }
            ?: error("Select an unlocked, visible text layer first")
        val voice = voice(document)
        val offset = document.sceneOffsetMs()
        val from = (voice.startMs-offset).coerceAtLeast(0)
        val until = voice.startMs+voice.durationMs()-offset
        require(until-from >= 200) { "Voice does not overlap this scene; select its scene or fit the whole video" }
        val hold = minOf(300L,60000-document.movieDurationMs()+document.video.durationMs-until).coerceAtLeast(0)
        val length = maxOf(1000,until+hold)
        require(length <= VideoEditRules.maximumSceneDuration(document)) { "Voice exceeds this scene's remaining budget; use Fit whole video" }
        val resized = VideoEditRules.duration(document,length)
        return resized.copy(layers = resized.layers.map { if (it.id == layer.id) aligned(it,from,until,length) else it })
    }
    fun storyboard(document: StudioDocument): StudioDocument {
        val voice = voice(document)
        val scenes = document.orderedScenes()
        require(scenes.any { scene -> scene.layers.any { it.kind == "text" && !it.locked && it.visible && it.text.isNotBlank() } }) { "Add unlocked text before fitting the video" }
        val minimum = if (scenes.size == 1) 200L else 1000L
        require(voice.durationMs() >= minimum*scenes.size) { "Voice is too short for ${scenes.size} scenes; remove scenes or use a longer recording" }
        val weights = scenes.map { scene -> scene.layers.filter { it.kind == "text" && it.visible && !it.locked }.sumOf { AnimationTimeline.words(it.text).size }.coerceAtLeast(1) }
        val spare = voice.durationMs()-minimum*scenes.size
        var used = 0L
        val fitted = scenes.mapIndexed { index, scene ->
            val segment = if (index == scenes.lastIndex) voice.durationMs()-used else minimum+spare*weights[index]/weights.sum()
            used += segment
            val lead = if (index == 0) voice.startMs else 0L
            val tail = if (index == scenes.lastIndex) minOf(300,60000-voice.startMs-voice.durationMs()) else 0L
            val duration = maxOf(1000,lead+segment+tail)
            scene.copy(video = scene.video.copy(durationMs = duration),layers = scene.layers.map { layer ->
                val normalized = layer.copy(animation = VideoEditRules.animation(layer.animation,duration))
                aligned(normalized,lead,lead+segment,duration)
            })
        }
        require(fitted.sumOf { it.video.durationMs } <= 60000) { "Voice plus scene padding exceeds 60 seconds; trim voice slightly" }
        return fitted.first { it.id == document.sceneId }.applyTo(document).copy(scenes = fitted)
    }
}
