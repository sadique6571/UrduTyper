package me.nomi.urdutyper.presentation.ui.type.studio

import org.junit.Assert.*
import org.junit.Test

class WordSyncTest {
    private val track = StudioAudio(id = "voice",path = "voice.wav",role = "voice",sourceDurationMs = 4000,trimStartMs = 500,trimEndMs = 3500,startMs = 1000)
    @Test fun acceptsUrduDigitsAndRejectsInvalidManualTimes() {
        assertEquals(1250L,WordSync.parseSeconds("۱٫۲۵")); assertEquals(500L,WordSync.parseSeconds("٠,٥"))
        assertNull(WordSync.parseSeconds("NaN")); assertNull(WordSync.parseSeconds("60.1")); assertNull(WordSync.parseSeconds("-1"))
    }
    @Test fun matchesUrduDiacriticsAndArabicLetterVariantsWithoutInventingMissingWords() {
        val speech = listOf(SpeechWord("میں",1000,1400,.9f),SpeechWord("تم",1500,1800,.8f),SpeechWord("سے",1900,2100,.95f),SpeechWord("ملا",2200,2700,.9f))
        val words = WordSync.match("مَیں تم سے کبھی مِلا",speech)
        assertEquals(1000L,words[0].startMs); assertNull(words[3].startMs); assertEquals(2200L,words[4].startMs)
        assertEquals(WordSync.normalized("کیا"),WordSync.normalized("كيا"))
        assertNotNull(WordSync.validationError("مَیں تم سے کبھی مِلا",words,track))
    }
    @Test fun repeatedWordsStayOrderedAndMissingTimingMustBeReviewed() {
        val words = WordSync.match("تم تم ہو",listOf(SpeechWord("تم",1100,1400,.9f),SpeechWord("تم",1500,1800,.9f),SpeechWord("ہو",2000,2300,.9f)))
        assertEquals(listOf(1100L,1500L,2000L),words.map { it.startMs })
        assertNull(WordSync.validationError("تم تم ہو",words,track))
        assertNotNull(WordSync.validationError("تم تم ہو",words.mapIndexed { i,w -> if (i == 1) w.copy(startMs = 5000) else w },track))
    }
    @Test fun sourceChangesPauseCuesButVolumeAndFadeDoNotInvalidateThem() {
        val layer = StudioLayer(text = "ایک دو",animation = LayerAnimation(wordCues = listOf(WordCue(0,3,1000,1500),WordCue(4,6,2000,2500)),voiceSignature = WordSync.signature(track)))
        val doc = StudioDocument(layers = listOf(layer),audio = listOf(track))
        assertTrue(WordSync.validSource(doc,layer))
        assertTrue(WordSync.validSource(doc.copy(audio = listOf(track.copy(volume = .4f,fadeInMs = 100))),layer))
        assertFalse(WordSync.validSource(doc.copy(audio = listOf(track.copy(startMs = 500))),layer))
        assertFalse(WordSync.validSource(doc.copy(audio = listOf(track.copy(trimStartMs = 600))),layer))
    }
    @Test fun wordRevealAndHighlightFollowTimestampsAndHoldAcrossPauses() {
        val text = "خواب حقیقت"
        val ranges = AnimationTimeline.words(text)
        val cues = ranges.mapIndexed { i,r -> WordCue(r.first,r.last+1,1000L+i*1500,1800L+i*1500) }
        val layer = StudioLayer(text = text,animation = LayerAnimation(effect = "words",startMs = 0,endMs = 5000,wordCues = cues))
        assertEquals("",AnimationTimeline.layer(layer,900).text)
        assertEquals("خواب",AnimationTimeline.layer(layer,1000).text)
        assertEquals("خواب",AnimationTimeline.layer(layer,2200).text)
        assertEquals(text,AnimationTimeline.layer(layer,2500).text)
        val highlight = layer.copy(animation = layer.animation.copy(effect = "highlight"))
        assertEquals(0,AnimationTimeline.layer(highlight,1400).activeWord)
        assertEquals(-1,AnimationTimeline.layer(highlight,2200).activeWord)
        assertEquals(1,AnimationTimeline.layer(highlight,2600).activeWord)
    }
    @Test fun globalTimesWorkInLaterScenesAndCueValidationRejectsBrokenOffsets() {
        val layer = StudioLayer(text = "ہم تم")
        val first = StudioScene(id = "a",video = VideoSettings(1000),layers = emptyList())
        val second = StudioScene(id = "b",video = VideoSettings(3000),layers = listOf(layer))
        val doc = second.applyTo(StudioDocument(audio = listOf(track))).copy(scenes = listOf(first,second))
        val words = listOf(ReviewWord("ہم",1200,1700,.8f),ReviewWord("تم",2300,2900,.9f,manual = true))
        val aligned = WordSync.apply(doc,layer.id,layer.text,words,"words")
        assertEquals(200L,aligned.layers.single().animation.startMs)
        assertEquals("ہم",AnimationTimeline.layer(aligned.layers.single(),300,1300).text)
        assertTrue(WordSync.validCues(aligned.layers.single()))
        assertFalse(WordSync.validCues(aligned.layers.single().copy(text = "bad")))
        assertTrue(aligned.layers.single().animation.wordCues[1].manual)
    }
    @Test fun selectedDurationFitPreservesExistingWordSyncAndDraftImmutability() {
        val layer = StudioLayer(text = "ہم",animation = LayerAnimation(effect = "words",wordCues = listOf(WordCue(0,2,1000,2000)),voiceSignature = WordSync.signature(track)))
        val doc = StudioDocument(layers = listOf(layer),audio = listOf(track))
        assertEquals(layer.animation.wordCues,VoiceTiming.selected(doc,layer.id).layers.single().animation.wordCues)
        assertEquals(1,doc.layers.single().animation.wordCues.size)
    }
}
