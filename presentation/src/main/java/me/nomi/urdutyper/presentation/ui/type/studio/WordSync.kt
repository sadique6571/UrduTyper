package me.nomi.urdutyper.presentation.ui.type.studio

import java.security.MessageDigest
import java.text.Normalizer
import kotlin.math.*

/** Cue character offsets are UTF-16, timestamps are on the complete movie timeline. */
data class WordCue(val first: Int = 0, val end: Int = 0, val startMs: Long = 0, val endMs: Long = 0, val confidence: Float = 1f, val estimated: Boolean = false, val manual: Boolean = false)
data class ReviewWord(val text: String, val startMs: Long?, val endMs: Long?, val confidence: Float = 0f, val estimated: Boolean = false, val manual: Boolean = false)
object WordSync {
    fun parseSeconds(value: String): Long? {
        val normalized = value.trim().map { char ->
            val digit = Character.digit(char,10)
            if (digit >= 0) ('0'.code+digit).toChar() else if (char == '٫' || char == ',') '.' else char
        }.joinToString("")
        return normalized.toDoubleOrNull()?.takeIf { it.isFinite() && it in 0.0..60.0 }?.let { (it*1000).toLong() }
    }
    fun signature(track: StudioAudio): String = MessageDigest.getInstance("SHA-256").digest("${track.id}|${track.path}|${track.sourceDurationMs}|${track.startMs}|${track.trimStartMs}|${track.trimEndMs}".toByteArray()).joinToString("") { "%02x".format(it.toInt() and 255) }
    fun validSource(document: StudioDocument, layer: StudioLayer): Boolean = document.audio.any { it.role == "voice" && signature(it) == layer.animation.voiceSignature }
    fun normalized(word: String): String = Normalizer.normalize(word,Normalizer.Form.NFKC).lowercase(java.util.Locale.ROOT)
        .replace('ي','ی').replace('ى','ی').replace('ك','ک').replace('ة','ہ').replace('ۀ','ہ')
        .filter { Character.isLetterOrDigit(it) }
    private fun score(a: String, b: String): Int {
        if (a.isEmpty() || b.isEmpty()) return 0
        if (a == b) return 10
        if (minOf(a.length,b.length) < 4 || abs(a.length-b.length) > 2) return 0
        var prev = IntArray(b.length+1) { it }
        for (i in a.indices) {
            val next = IntArray(b.length+1); next[0] = i+1
            for (j in b.indices) next[j+1] = minOf(next[j]+1,prev[j+1]+1,prev[j]+if (a[i] == b[j]) 0 else 1)
            prev = next
        }
        return if (prev.last().toFloat()/maxOf(a.length,b.length) <= .25f) 6 else 0
    }
    fun match(text: String, spoken: List<SpeechWord>): List<ReviewWord> {
        val target = AnimationTimeline.words(text).map { text.substring(it) }
        require(target.size <= 600 && spoken.size <= 1000) { "Use at most 600 written words and 60 seconds of voice" }
        val a = target.map(::normalized); val b = spoken.map { normalized(it.text) }; val width = b.size+1
        val table = IntArray((a.size+1)*width)
        for (i in 1..a.size) for (j in 1..b.size) table[i*width+j] = maxOf(table[(i-1)*width+j],table[i*width+j-1],table[(i-1)*width+j-1]+score(a[i-1],b[j-1]))
        val matches = IntArray(a.size) { -1 }; var i = a.size; var j = b.size
        while (i > 0 && j > 0) {
            val similarity = score(a[i-1],b[j-1])
            if (similarity > 0 && table[i*width+j] == table[(i-1)*width+j-1]+similarity) { matches[i-1] = j-1; i--; j-- }
            else if (table[(i-1)*width+j] > table[i*width+j-1]) i-- else j--
        }
        return target.mapIndexed { index, word ->
            val match = matches[index]
            if (match < 0) ReviewWord(word,null,null,0f,true)
            else { val source = spoken[match]; val fuzzy = a[index] != b[match]; ReviewWord(word,source.startMs,source.endMs,if (fuzzy) minOf(.6f,source.confidence) else source.confidence,source.estimated || fuzzy) }
        }
    }
    fun transcript(spoken: List<SpeechWord>) = spoken.map { ReviewWord(it.text,it.startMs,it.endMs,it.confidence,it.estimated) }
    fun review(text: String, cues: List<WordCue>): List<ReviewWord> = AnimationTimeline.words(text).map { range ->
        val cue = cues.firstOrNull { it.first == range.first && it.end == range.last+1 }
        ReviewWord(text.substring(range),cue?.startMs,cue?.endMs,cue?.confidence ?: 0f,cue?.estimated ?: true,cue?.manual ?: false)
    }
    fun validationError(text: String, words: List<ReviewWord>, track: StudioAudio): String? {
        val tokens = AnimationTimeline.words(text)
        if (tokens.isEmpty() || tokens.size != words.size || words.size > 600 || text.length > 2000) return "Update the word list after editing text (maximum 2,000 characters / 600 words)"
        var previous = -1L
        words.forEachIndexed { index, word ->
            val start = word.startMs; val end = word.endMs
            if (start == null || end == null) return "Set timing for word ${index+1}: ${word.text}"
            if (word.text != text.substring(tokens[index])) return "Update the word list after editing text"
            if (start < track.startMs || end > track.startMs+track.durationMs() || end > 60000 || end <= start || start < previous) return "Word ${index+1}: keep times ordered and inside the selected voice range"
            previous = start
        }
        return null
    }
    fun apply(document: StudioDocument, layerId: String, text: String, words: List<ReviewWord>, mode: String): StudioDocument {
        val track = document.audio.firstOrNull { it.role == "voice" } ?: error("Add a voice track first")
        require(validationError(text,words,track) == null) { validationError(text,words,track).orEmpty() }
        val old = document.layers.firstOrNull { it.id == layerId && it.kind == "text" && it.visible && !it.locked } ?: error("Select an unlocked visible text layer")
        require(mode in listOf("words","highlight","typing"))
        val tokens = AnimationTimeline.words(text)
        val cues = words.mapIndexed { index, word -> WordCue(tokens[index].first,tokens[index].last+1,word.startMs!!,word.endMs!!,word.confidence.coerceIn(0f,1f),word.estimated,word.manual) }
        val offset = document.sceneOffsetMs()
        require(cues.first().startMs >= offset) { "These words start in an earlier scene. Select that scene or align its excerpt." }
        val end = cues.maxOf { it.endMs }-offset
        val length = maxOf(document.video.durationMs,end+minOf(300,VideoEditRules.maximumSceneDuration(document)-end).coerceAtLeast(0))
        require(length <= VideoEditRules.maximumSceneDuration(document)) { "Word timings exceed this scene's remaining video budget" }
        val resized = VideoEditRules.duration(document,length)
        val layer = old.copy(text = text,animation = old.animation.copy(effect = mode,startMs = cues.first().startMs-offset,endMs = length,
            revealMs = (end-(cues.first().startMs-offset)).coerceAtLeast(100),repeat = false,cursor = false,wordCues = cues,voiceSignature = signature(track)))
        return resized.copy(layers = resized.layers.map { if (it.id == layerId) layer else it })
    }
    fun validCues(layer: StudioLayer): Boolean {
        val cues = layer.animation.wordCues
        val ranges = AnimationTimeline.words(layer.text)
        return cues.size <= 600 && (cues.isEmpty() || (cues.size == ranges.size && cues.map { it.first } == ranges.map { it.first } && cues.map { it.end } == ranges.map { it.last+1 })) &&
            cues.all { it.first >= 0 && it.end <= layer.text.length && it.end > it.first && it.startMs in 0..59999 && it.endMs in (it.startMs+1)..60000 && it.confidence.isFinite() && it.confidence in 0f..1f } &&
            cues.zipWithNext().all { (a,b) -> b.startMs >= a.startMs }
    }
    fun animated(layer: StudioLayer, timeMs: Long): StudioLayer {
        val cues = layer.animation.wordCues
        val index = cues.indexOfLast { it.startMs <= timeMs }
        if (layer.animation.effect == "highlight") return layer.copy(activeWord = index.takeIf { it >= 0 && timeMs < cues[it].endMs } ?: -1)
        if (index < 0) return layer.copy(text = "",activeWord = -1)
        val cue = cues[index]
        if (layer.animation.effect == "typing" && timeMs < cue.endMs) {
            val word = layer.text.substring(cue.first,cue.end); val clusters = AnimationTimeline.clusters(word)
            val count = (clusters.size*(timeMs-cue.startMs).toFloat()/(cue.endMs-cue.startMs)).toInt().coerceIn(0,clusters.size)
            return layer.copy(text = layer.text.substring(0,cue.first)+(if (count == 0) "" else word.substring(0,clusters[count-1])),activeWord = -1)
        }
        return layer.copy(text = layer.text.substring(0,cue.end),activeWord = -1)
    }
}
