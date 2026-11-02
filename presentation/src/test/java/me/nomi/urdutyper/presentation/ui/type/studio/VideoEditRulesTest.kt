package me.nomi.urdutyper.presentation.ui.type.studio

import org.junit.Assert.*
import org.junit.Test

class VideoEditRulesTest {
    @Test fun shorteningSceneClampsEveryLayerAndFinishesTypingBeforeDisappearing() {
        val layer = StudioLayer(text = "خوابوں کو حقیقت بناؤ",animation = LayerAnimation("typing",0,8000,7000))
        val late = layer.copy(id = "late",animation = LayerAnimation("fade",7800,8000,2000))
        val doc = StudioDocument(layers = listOf(layer,late),video = VideoSettings(8000))
        val edited = VideoEditRules.duration(doc,5000)
        assertEquals(5000L,edited.video.durationMs)
        edited.layers.forEach { assertTrue(it.animation.startMs < it.animation.endMs); assertTrue(it.animation.endMs <= 5000) }
        val text = edited.layers.first()
        assertTrue(text.animation.revealMs < text.animation.endMs-text.animation.startMs)
        assertEquals(layer.text,AnimationTimeline.layer(text,text.animation.endMs-1).text)
        assertEquals(8000L,doc.video.durationMs)
    }
    @Test fun lengtheningSceneKeepsFullSceneLayersPinnedAndOtherTimingsIndependent() {
        val full = StudioLayer(animation = LayerAnimation("none",0,8000,3000))
        val short = StudioLayer(animation = LayerAnimation("fade",1000,3000,1000))
        val doc = StudioDocument(video = VideoSettings(8000),layers = listOf(full,short))
        val edited = VideoEditRules.duration(doc,10000)
        assertEquals(10000L,edited.layers[0].animation.endMs)
        assertEquals(3000L,edited.layers[1].animation.endMs)
    }
    @Test fun sceneBudgetCannotExceedTotalMovieLimit() {
        val doc = StudioDocument(video = VideoSettings(8000))
        val other = doc.captureScene().copy(id = "other",video = VideoSettings(50000))
        val movie = doc.copy(scenes = listOf(doc.captureScene(),other))
        assertEquals(10000L,VideoEditRules.maximumSceneDuration(movie))
        assertEquals(60000L,VideoEditRules.duration(movie,40000).movieDurationMs())
    }
    @Test fun veryShortAnimationStillGetsACompleteTextFrame() {
        val value = VideoEditRules.animation(LayerAnimation("words",990,1000,9000),1000)
        assertTrue(value.endMs-value.startMs >= 200)
        val layer = StudioLayer(text = "ایک نئی امید",animation = value)
        assertEquals(layer.text,AnimationTimeline.layer(layer,value.endMs-1).text)
    }
    @Test fun gifPreviewIsSilentWithoutRemovingDraftAudio() {
        val doc = StudioDocument(audio = listOf(StudioAudio(path = "voice.wav")))
        assertTrue(VideoEditRules.exportPreview(doc,2).audio.isEmpty())
        assertEquals(1,doc.audio.size)
        assertEquals(doc,VideoEditRules.exportPreview(doc,0))
    }
    @Test fun imageAndNonTypingAnimationsDoNotKeepIrrelevantCursor() {
        val value = VideoEditRules.animation(LayerAnimation("fade",cursor = true),8000)
        assertFalse(value.cursor)
        assertFalse(VideoEditRules.animation(value.copy(effect = "none",repeat = true),8000).repeat)
    }
}
