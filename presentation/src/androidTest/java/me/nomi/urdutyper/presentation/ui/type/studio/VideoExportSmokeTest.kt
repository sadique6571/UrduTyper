package me.nomi.urdutyper.presentation.ui.type.studio

import android.media.*
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import java.io.File
import java.nio.ByteBuffer
import java.nio.ByteOrder
import kotlin.math.*

@RunWith(AndroidJUnit4::class)
class VideoExportSmokeTest {
    @org.junit.Rule @JvmField val timeout = org.junit.rules.Timeout.seconds(120)
    @org.junit.Rule @JvmField val diagnostics = object : org.junit.rules.TestWatcher() {
        override fun starting(description: org.junit.runner.Description) { android.util.Log.i("StudioTests","START ${description.className}.${description.methodName}") }
        override fun finished(description: org.junit.runner.Description) { android.util.Log.i("StudioTests","END ${description.className}.${description.methodName}") }
    }

    @Test fun exportsPlayableTypingVideoWithMixedAacAudioAndNoTemporaryFiles() {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val directory = File(context.cacheDir, "video-smoke").apply { mkdirs() }
        val voice = File(directory,"voice.wav"); val music = File(directory,"music.wav")
        tone(voice,440); tone(music,660)
        val text = StudioLayer(text = "خوابوں کو حقیقت بناؤ", x = 160f, y = 160f, width = 280f, size = 28f,
            animation = LayerAnimation("typing",0,1200,600))
        val document = StudioDocument(width = 320,height = 320,layers = listOf(text), video = VideoSettings(1200,"zoom","snow"),
            audio = listOf(StudioAudio(path = voice.path, sourceDurationMs = 1000,trimEndMs = 1000),
                StudioAudio(path = music.path,role = "music",sourceDurationMs = 1000,trimEndMs = 1000,volume = .2f)))
        val ranges = AnimationTimeline.words(text.text)
        val timedText = text.copy(animation = text.animation.copy(wordCues = ranges.mapIndexed { index,r -> WordCue(r.first,r.last+1,index*200L,(index+1)*200L) },voiceSignature = WordSync.signature(document.audio.first { it.role == "voice" })))
        val timedDocument = document.copy(layers = listOf(timedText))
        val file = File(directory,"result.mp4")
        try {
            StudioVideoExporter.export(context,timedDocument,file,320)
            assertTrue(file.length() > 1000)
            val extractor = MediaExtractor()
            try {
                extractor.setDataSource(file.path)
                val formats = (0 until extractor.trackCount).map { extractor.getTrackFormat(it) }
                assertEquals(2,formats.size)
                assertTrue(formats.any { it.getString(MediaFormat.KEY_MIME) == "video/avc" })
                assertTrue(formats.any { it.getString(MediaFormat.KEY_MIME) == "audio/mp4a-latm" })
            } finally { extractor.release() }
            val retriever = MediaMetadataRetriever()
            try {
                retriever.setDataSource(file.path)
                assertTrue(retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_DURATION)!!.toLong() in 1100..1600)
                val early = retriever.getFrameAtTime(0,MediaMetadataRetriever.OPTION_CLOSEST)!!
                val late = retriever.getFrameAtTime(900000,MediaMetadataRetriever.OPTION_CLOSEST)!!
                try { assertEquals(320,late.width); assertEquals(320,late.height); assertFalse("Typing must change video frames",early.sameAs(late)) }
                finally { early.recycle(); late.recycle() }
            } finally { retriever.release() }
            assertTrue("Audio must contain the imported tones", decodedPeak(file) > .05f)
        } finally { directory.deleteRecursively() }
    }
    @Suppress("DEPRECATION")
    @Test fun gifAnimationDecodesOnAndroidWithChangingFramesAndCorrectDuration() {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val document = StudioDocument(width = 320,height = 320,video = VideoSettings(1000),
            layers = listOf(StudioLayer(text = "امید",x = 160f,y = 160f,width = 280f,size = 45f,animation = LayerAnimation("typing",0,1000,500))))
        val file = File(context.cacheDir,"animated-gif-smoke.gif")
        try {
            StudioGifExporter.export(context,document,file)
            val bytes = file.readBytes()
            val movie = checkNotNull(android.graphics.Movie.decodeByteArray(bytes,0,bytes.size))
            assertEquals(320,movie.width()); assertEquals(320,movie.height()); assertEquals(1000,movie.duration())
            val first = android.graphics.Bitmap.createBitmap(320,320,android.graphics.Bitmap.Config.ARGB_8888)
            val last = android.graphics.Bitmap.createBitmap(320,320,android.graphics.Bitmap.Config.ARGB_8888)
            try {
                movie.setTime(0); movie.draw(android.graphics.Canvas(first),0f,0f)
                movie.setTime(875); movie.draw(android.graphics.Canvas(last),0f,0f)
                assertFalse("GIF must animate on Android",first.sameAs(last))
            } finally { first.recycle(); last.recycle() }
        } finally { file.delete() }
    }
    @Test fun multiSceneExportHasFullDurationAndBothScenes() {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val first = StudioScene(id = "first", backgroundColor = android.graphics.Color.RED, gradientColor = android.graphics.Color.RED,
            layers = emptyList(), video = VideoSettings(1000))
        val next = first.copy(id = "next", backgroundColor = android.graphics.Color.BLUE, gradientColor = android.graphics.Color.BLUE, transition = "slide")
        val doc = first.applyTo(StudioDocument(width = 320,height = 320)).copy(scenes = listOf(first,next))
        val file = File(context.cacheDir,"storyboard-smoke.mp4")
        try {
            StudioVideoExporter.export(context,doc,file,320)
            val retriever = MediaMetadataRetriever()
            try {
                retriever.setDataSource(file.path)
                assertTrue(retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_DURATION)!!.toLong() in 1900..2200)
                val early = retriever.getFrameAtTime(200000,MediaMetadataRetriever.OPTION_CLOSEST)!!
                val late = retriever.getFrameAtTime(1700000,MediaMetadataRetriever.OPTION_CLOSEST)!!
                try {
                    val a = early.getPixel(160,160); val b = late.getPixel(160,160)
                    assertTrue(android.graphics.Color.red(a) > 180 && android.graphics.Color.blue(a) < 50)
                    assertTrue(android.graphics.Color.blue(b) > 180 && android.graphics.Color.red(b) < 50)
                } finally { early.recycle(); late.recycle() }
            } finally { retriever.release() }
        } finally { file.delete() }
    }
    private fun tone(file: File, frequency: Int) {
        val frames = 44100
        val data = ByteBuffer.allocate(44 + frames * 2).order(ByteOrder.LITTLE_ENDIAN)
        data.put("RIFF".toByteArray()); data.putInt(36 + frames * 2); data.put("WAVEfmt ".toByteArray())
        data.putInt(16); data.putShort(1); data.putShort(1); data.putInt(44100); data.putInt(88200); data.putShort(2); data.putShort(16)
        data.put("data".toByteArray()); data.putInt(frames * 2)
        for (i in 0 until frames) data.putShort((sin(2 * PI * frequency * i / 44100) * 12000).roundToInt().toShort())
        file.writeBytes(data.array())
    }
    private fun decodedPeak(file: File): Float {
        val extractor = MediaExtractor()
        var codec: MediaCodec? = null
        try {
            extractor.setDataSource(file.path)
            val index = (0 until extractor.trackCount).first { extractor.getTrackFormat(it).getString(MediaFormat.KEY_MIME)!!.startsWith("audio/") }
            extractor.selectTrack(index)
            val format = extractor.getTrackFormat(index)
            val decoder = MediaCodec.createDecoderByType(format.getString(MediaFormat.KEY_MIME)!!); codec = decoder
            decoder.configure(format,null,null,0); decoder.start()
            val info = MediaCodec.BufferInfo(); var done = false; var inputDone = false; var peak = 0f; var encoding = AudioFormat.ENCODING_PCM_16BIT
            var cycles = 0
            while (!done && cycles++ < 10000) {
                if (!inputDone) {
                    val input = decoder.dequeueInputBuffer(10000)
                    if (input >= 0) {
                        val buffer = decoder.getInputBuffer(input)!!; buffer.clear()
                        val count = extractor.readSampleData(buffer,0)
                        if (count < 0) { decoder.queueInputBuffer(input,0,0,0,MediaCodec.BUFFER_FLAG_END_OF_STREAM); inputDone = true }
                        else { decoder.queueInputBuffer(input,0,count,extractor.sampleTime,0); extractor.advance() }
                    }
                }
                val output = decoder.dequeueOutputBuffer(info,10000)
                if (output == MediaCodec.INFO_OUTPUT_FORMAT_CHANGED) {
                    if (decoder.outputFormat.containsKey(MediaFormat.KEY_PCM_ENCODING)) encoding = decoder.outputFormat.getInteger(MediaFormat.KEY_PCM_ENCODING)
                } else if (output >= 0) {
                    val buffer = decoder.getOutputBuffer(output)!!.order(ByteOrder.LITTLE_ENDIAN)
                    buffer.position(info.offset); buffer.limit(info.offset + info.size)
                    while (buffer.remaining() >= if (encoding == AudioFormat.ENCODING_PCM_FLOAT) 4 else 2) {
                        val value = if (encoding == AudioFormat.ENCODING_PCM_FLOAT) buffer.float else buffer.short / 32768f
                        peak = maxOf(peak,abs(value))
                    }
                    done = info.flags and MediaCodec.BUFFER_FLAG_END_OF_STREAM != 0
                    decoder.releaseOutputBuffer(output,false)
                }
            }
            assertTrue("AAC decoder must reach end of stream",done)
            return peak
        } finally { codec?.let { runCatching { it.stop() }; it.release() }; extractor.release() }
    }
}

