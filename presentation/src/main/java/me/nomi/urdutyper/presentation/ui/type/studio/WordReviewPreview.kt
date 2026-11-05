package me.nomi.urdutyper.presentation.ui.type.studio

/** Missing cues must not stop listening to a known word beyond the draft's current duration. */
internal object WordReviewPreview {
    fun document(original: StudioDocument, ready: StudioDocument?, selectedId: String?, text: String, word: Int): StudioDocument {
        if(ready!=null) return ready
        val voiceEnd=original.audio.filter { it.role=="voice" }.maxOfOrNull { it.startMs+it.durationMs() } ?: original.movieDurationMs()
        return original.copy(scenes=emptyList(),sceneId="",video=original.video.copy(durationMs=maxOf(original.movieDurationMs(),voiceEnd).coerceIn(1000,60000)),
            layers=original.layers.map { it.copy(text=if(it.id==selectedId) text else it.text,activeWord=if(it.id==selectedId) word else -1,animation=LayerAnimation()) })
    }
}
