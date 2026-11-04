package me.nomi.urdutyper.presentation.ui.type.studio

import org.junit.Assert.*
import org.junit.Test

class VoiceTimingTest {
    private fun voice(duration: Long = 4000, start: Long = 500) = StudioAudio(role = "voice",sourceDurationMs = duration+1000,trimStartMs = 300,trimEndMs = duration+300,startMs = start)
    @Test fun selectedUsesTrimmedAudioAndOffsetAndHoldsCompleteUrduAtEnd() {
        val layer = StudioLayer(text = "خوابوں کو حقیقت بناؤ",animation = LayerAnimation("typing",0,8000,2000,true,true))
        val doc = StudioDocument(layers = listOf(layer),audio = listOf(voice()))
        val next = VoiceTiming.selected(doc,layer.id)
        assertEquals(4800L,next.video.durationMs)
        val a = next.layers.single().animation
        assertEquals(500L,a.startMs); assertEquals(4800L,a.endMs); assertEquals(3900L,a.revealMs)
        assertFalse(a.repeat); assertFalse(a.cursor)
        assertFalse(AnimationTimeline.layer(next.layers.single(),499).visible)
        assertEquals(layer.text,AnimationTimeline.layer(next.layers.single(),4799).text)
        assertEquals(8000L,doc.video.durationMs)
        assertEquals(doc.audio,next.audio)
    }
    @Test fun storyboardUsesTextWeightsKeepsSceneOrderAndPreservesMusicAndWeather() {
        val first = StudioScene(id = "a",layers = listOf(StudioLayer(text = "ایک لفظ")),video = VideoSettings(atmosphere = "snow"))
        val second = StudioScene(id = "b",layers = listOf(StudioLayer(text = "یہ چار لفظ ہیں")))
        val doc = first.applyTo(StudioDocument(audio = listOf(voice(10000,1000),StudioAudio(role = "music")))).copy(scenes = listOf(first,second))
        val fitted = VoiceTiming.storyboard(doc)
        assertEquals(listOf("a","b"),fitted.orderedScenes().map { it.id })
        assertEquals(11300L,fitted.movieDurationMs())
        assertTrue(fitted.orderedScenes()[1].video.durationMs > fitted.orderedScenes()[0].video.durationMs)
        assertEquals("snow",fitted.orderedScenes()[0].video.atmosphere)
        assertEquals(1000L,fitted.layers.single().animation.startMs)
        assertEquals(doc.audio,fitted.audio)
    }
    @Test fun rejectsMissingVoiceAndLockedTextAndOversizedVoice() {
        val layer = StudioLayer()
        assertThrows(IllegalStateException::class.java) { VoiceTiming.selected(StudioDocument(),layer.id) }
        assertThrows(IllegalStateException::class.java) { VoiceTiming.selected(StudioDocument(layers = listOf(layer.copy(locked = true)),audio = listOf(voice())),layer.id) }
        assertThrows(IllegalArgumentException::class.java) { VoiceTiming.storyboard(StudioDocument(audio = listOf(voice(60000,1000)))) }
    }
    @Test fun tooManyScenesForShortVoiceRequiresExplicitCorrection() {
        val scenes = listOf(StudioScene(id = "a",layers = listOf(StudioLayer())),StudioScene(id = "b",layers = listOf(StudioLayer())))
        val doc = scenes[0].applyTo(StudioDocument(audio = listOf(voice(1000,0)))).copy(scenes = scenes)
        assertThrows(IllegalArgumentException::class.java) { VoiceTiming.storyboard(doc) }
        assertEquals(16000L,doc.movieDurationMs())
    }
    @Test fun preservesLockedTextAndPadsVeryShortSingleSceneSafely() {
        val locked = StudioLayer(locked = true,text = "عنوان")
        val layer = StudioLayer()
        val doc = StudioDocument(layers = listOf(locked,layer),audio = listOf(voice(200,0)))
        val next = VoiceTiming.storyboard(doc)
        assertEquals(1000L,next.video.durationMs)
        assertEquals("none",next.layers[0].animation.effect)
        assertEquals("words",next.layers[1].animation.effect)
        assertEquals(layer.text,AnimationTimeline.layer(next.layers[1],999).text)
    }
}
