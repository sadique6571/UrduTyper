package me.nomi.urdutyper.presentation.ui.type.studio

import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder

class StudioSceneTest {
    @get:Rule val folder = TemporaryFolder()
    @Test fun switchingScenesPersistsUnsavedLayerEditsAndTransitions() {
        val first = StudioScene(id = "a", layers = listOf(StudioLayer(text = "پہلا")), video = VideoSettings(2000))
        val second = first.copy(id = "b", layers = listOf(StudioLayer(text = "دوسرا")), video = VideoSettings(3000), transition = "slide")
        val document = first.applyTo(StudioDocument()).copy(scenes = listOf(first, second), layers = listOf(StudioLayer(text = "بدلا ہوا")))
        val switched = document.selectScene("b")
        assertEquals("دوسرا", switched.layers.single().text)
        assertEquals(2000L, switched.sceneOffsetMs())
        assertEquals(5000L, switched.movieDurationMs())
        assertEquals("بدلا ہوا", switched.selectScene("a").layers.single().text)
        assertEquals("slide", switched.orderedScenes()[1].transition)
        val store = StudioStore(folder.newFolder()); store.save(switched)
        assertEquals(switched, store.load(switched.id))
    }
    @Test fun oldSchemaTwoDraftLoadsWithSingleSceneAndKeepsAudio() {
        val root = folder.newFolder()
        java.io.File(root, "old.json").writeText("""{"id":"old","schemaVersion":2,"width":1080,"height":1080,"layers":[],"video":{"durationMs":2000},"audio":[]}""")
        val loaded = checkNotNull(StudioStore(root).load("old"))
        assertEquals(2000L, loaded.movieDurationMs()); assertEquals(1, loaded.orderedScenes().size)
    }
    @Test fun resizingAndUndoRestoreEveryScene() {
        val initial = StudioDocument()
        val first = initial.captureScene()
        val second = first.copy(id = "next", layers = listOf(StudioLayer(x = 270f, y = 300f)))
        val document = initial.copy(scenes = listOf(first, second))
        val resized = document.resized(2160, 2160)
        assertEquals(540f, resized.selectScene("next").layers.single().x, .01f)
        val history = StudioHistory(); history.reset(document); history.record(resized)
        assertEquals(document, history.undo())
    }
    @Test fun musicDucksOnlyDuringAudibleVoiceAndCanBeDisabled() {
        val voice = StudioAudio(role = "voice", startMs = 1000, trimEndMs = 2000)
        val music = StudioAudio(role = "music", volume = .8f)
        val doc = StudioDocument(audio = listOf(voice, music))
        assertEquals(.8f, doc.musicGain(music, 500), .001f)
        assertEquals(.2f, doc.musicGain(music, 1500), .001f)
        assertEquals(.8f, doc.musicGain(music, 3500), .001f)
        assertEquals(.8f, doc.copy(duckMusic = false).musicGain(music, 1500), .001f)
        assertEquals(.8f, doc.copy(audio = listOf(voice.copy(volume = 0f), music)).musicGain(music, 1500), .001f)
    }
    @Test fun srtCaptionsCrossSceneBoundaryWithoutSplittingUrduText() {
        val parsed = StudioCaptions.parse("1\r\n00:00:01,500 --> 00:00:02,500\r\n<b>نئی امید</b>\r\n")
        val first = StudioScene(id = "first", video = VideoSettings(2000))
        val second = first.copy(id = "second")
        val doc = first.applyTo(StudioDocument()).copy(scenes = listOf(first, second))
        val applied = StudioCaptions.apply(doc, parsed)
        assertEquals("نئی امید", applied.layers.single().text)
        assertEquals(1500L, applied.layers.single().animation.startMs)
        assertEquals(2000L, applied.layers.single().animation.endMs)
        val next = applied.selectScene("second").layers.single()
        assertEquals(0L, next.animation.startMs); assertEquals(500L, next.animation.endMs)
    }
    @Test fun invalidCaptionTimingIsRejected() {
        listOf("1\n00:00:02,000 --> 00:00:01,000\ntext", "1\n00:00:00,000 --> 00:01:01,000\ntext").forEach { source ->
            try { StudioCaptions.parse(source); fail("Invalid timing accepted") } catch (_: IllegalArgumentException) { }
        }
    }
    @Test fun oversizedMovieIsNotLoaded() {
        val store = StudioStore(folder.newFolder())
        val doc = StudioDocument(video = VideoSettings(40000))
        store.save(doc.copy(scenes = listOf(doc.captureScene(), doc.captureScene().copy(id = "extra"))))
        assertNull(store.load(doc.id))
    }
}
