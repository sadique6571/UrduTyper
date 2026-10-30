package me.nomi.urdutyper.presentation.ui.type.studio

import org.junit.Assert.*
import org.junit.Test

class AnimationTimelineTest {
    @Test fun typingKeepsUrduDiacriticsAndEmojiClustersWhole() {
        val text = "اَ👨‍👩‍👧ب"
        val boundaries = AnimationTimeline.clusters(text)
        assertEquals(3,boundaries.size)
        assertEquals("اَ",text.substring(0,boundaries[0]))
        assertEquals("اَ👨‍👩‍👧",text.substring(0,boundaries[1]))
        val layer = StudioLayer(text = text,animation = LayerAnimation("typing",0,5000,3000))
        assertEquals("",AnimationTimeline.layer(layer,0).text)
        assertEquals("اَ",AnimationTimeline.layer(layer,1000).text)
        assertEquals(text,AnimationTimeline.layer(layer,3000).text)
    }
    @Test fun wordsPreserveOriginalSpacingAndLayerTiming() {
        val layer = StudioLayer(text = "ایک  نئی\nامید",animation = LayerAnimation("words",500,5000,3000))
        assertFalse(AnimationTimeline.layer(layer,499).visible)
        assertEquals("ایک  نئی",AnimationTimeline.layer(layer,2500).text)
        assertEquals(layer.text,AnimationTimeline.layer(layer,4000).text)
        assertFalse(AnimationTimeline.layer(layer,5000).visible)
    }
    @Test fun fadeZoomSlideAndHighlightUseIndependentLayerTimings() {
        val layer = StudioLayer(text = "ایک نئی امید",opacity = .8f,animation = LayerAnimation("fade",1000,8000,2000))
        assertEquals(.4f,AnimationTimeline.layer(layer,2000).opacity,.001f)
        val highlight = layer.copy(animation = layer.animation.copy(effect = "highlight"))
        assertEquals(1,AnimationTimeline.layer(highlight,2000).activeWord)
        assertEquals(-1,AnimationTimeline.layer(highlight,3000).activeWord)
        assertTrue(AnimationTimeline.layer(layer.copy(animation = layer.animation.copy(effect = "slide")),1500).y > layer.y)
        assertTrue(AnimationTimeline.layer(layer.copy(animation = layer.animation.copy(effect = "zoom")),1500).size < layer.size)
    }
    @Test fun audioGainIncludesOffsetTrimAndBothFades() {
        val audio = StudioAudio(startMs = 1000,trimStartMs = 2000,trimEndMs = 6000,volume = .8f,fadeInMs = 1000,fadeOutMs = 1000)
        assertEquals(0f,audio.gainAt(999),0f)
        assertEquals(.4f,audio.gainAt(1500),.001f)
        assertEquals(.8f,audio.gainAt(3000),.001f)
        assertEquals(.4f,audio.gainAt(4500),.001f)
        assertEquals(0f,audio.gainAt(5000),0f)
    }
    @Test fun repeatedTypingRestartsAfterOneSecondHold() {
        val layer = StudioLayer(text = "اب",animation = LayerAnimation("typing",0,10000,1000,repeat = true))
        assertEquals("اب",AnimationTimeline.layer(layer,1500).text)
        assertEquals("",AnimationTimeline.layer(layer,2000).text)
    }
    @Test fun longMusicFadesAtSceneEndInsteadOfAtAnUnexportedSourceEnd() {
        val audio = StudioAudio(trimEndMs = 60000,fadeOutMs = 2000)
        assertEquals(.5f,audio.gainAt(7000,8000),.001f)
        assertEquals(0f,audio.gainAt(8000,8000),0f)
    }
    @Test fun oldDraftsKeepAnimationDefaultsAndNewSettingsSurviveReload() {
        val gson = com.google.gson.Gson()
        val original = StudioDocument()
        val json = com.google.gson.JsonParser.parseString(gson.toJson(original)).asJsonObject
        json.remove("video"); json.remove("audio")
        json.getAsJsonArray("layers").forEach { it.asJsonObject.remove("animation") }
        val loaded = gson.fromJson(json,StudioDocument::class.java)
        assertEquals(VideoSettings(),loaded.video); assertTrue(loaded.audio.isEmpty())
        assertEquals("none",loaded.layers.single().animation.effect)
        val animated = original.copy(video = VideoSettings(9000,"pan","rain"),audio = listOf(StudioAudio(path="voice.m4a")))
        assertEquals(animated,gson.fromJson(gson.toJson(animated),StudioDocument::class.java))
    }
}
