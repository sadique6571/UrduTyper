package me.nomi.urdutyper.presentation.ui.type.studio

import android.content.Context
import org.robolectric.RuntimeEnvironment
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.io.ByteArrayInputStream
import java.io.File
import java.net.HttpURLConnection
import java.net.URL
import java.security.MessageDigest
import java.util.concurrent.CancellationException

@RunWith(RobolectricTestRunner::class)
@Config(sdk=[28], application=android.app.Application::class)
class StudioPolishTest {
    private val context: Context get()=RuntimeEnvironment.getApplication()
    private class Response(url: URL, private val code: Int, private val bytes: ByteArray, private val headers: Map<String,String> = emptyMap()): HttpURLConnection(url) {
        override fun connect()=Unit
        override fun disconnect()=Unit
        override fun usingProxy()=false
        override fun getResponseCode()=code
        override fun getContentLengthLong()=bytes.size.toLong()
        override fun getHeaderField(name: String)=headers[name]
        override fun getInputStream()=ByteArrayInputStream(bytes)
    }
    private fun hash(bytes: ByteArray)=MessageDigest.getInstance("SHA-1").digest(bytes).joinToString("") { "%02x".format(it.toInt() and 255) }
    @Test fun downloadRetainsCancelledBytesAndResumesWithRangeAndValidator() {
        val root=File(context.cacheDir,"resume-test").apply { deleteRecursively(); mkdirs() }
        val part=File(root,"tiny.download"); val bytes=ByteArray(180000) { (it%123).toByte() }; val url=URL("https://example.test/model")
        var cancel=false
        try { ResumableModelDownload.transfer(url,part,hash(bytes),{ cancel },{ copied,_ -> if(copied>=65536) cancel=true }) { Response(it,200,bytes,mapOf("ETag" to "v1")) }; fail() } catch(_: CancellationException) {}
        val size=part.length(); assertTrue(size in 1 until bytes.size.toLong())
        val done=ResumableModelDownload.transfer(url,part,hash(bytes),{ false },{ _,_ -> }) {
            Response(it,206,bytes.copyOfRange(size.toInt(),bytes.size),mapOf("Content-Range" to "bytes $size-${bytes.size-1}/${bytes.size}","ETag" to "v1")).also { connection ->
                // Header assertions are made after transfer below through this retained response.
                captured=connection
            }
        }
        assertEquals("bytes=$size-",captured!!.getRequestProperty("Range")); assertEquals("v1",captured!!.getRequestProperty("If-Range"))
        assertArrayEquals(bytes,done.readBytes())
        assertFalse(File(part.path+".validator").exists())
    }
    private var captured: HttpURLConnection?=null
    @Test fun ignoredRangeRestartsAndCorruptPayloadNeverPassesChecksum() {
        val root=File(context.cacheDir,"resume-restart").apply { deleteRecursively(); mkdirs() }; val part=File(root,"base.download")
        val bytes="complete correct model".toByteArray(); part.writeText("old partial")
        ResumableModelDownload.transfer(URL("https://example.test/model"),part,hash(bytes),{ false },{ _,_ -> }) { Response(it,200,bytes) }
        assertArrayEquals(bytes,part.readBytes())
        part.writeText("partial")
        try { ResumableModelDownload.transfer(URL("https://example.test/model"),part,hash(bytes),{ false },{ _,_ -> }) { Response(it,200,"corrupt".toByteArray()) }; fail() } catch(_: IllegalStateException) {}
        assertFalse(part.exists())
    }
    @Test fun invalidResumeRangeDoesNotAppendUnrelatedBytes() {
        val part=File(context.cacheDir,"bad-range.download").apply { writeText("first") }
        try { ResumableModelDownload.transfer(URL("https://example.test/model"),part,"unused",{ false },{ _,_ -> }) { Response(it,206,"x".toByteArray(),mapOf("Content-Range" to "bytes 0-0/8")) }; fail() } catch(_: IllegalArgumentException) {}
        assertEquals("first",part.readText())
    }
    @Test fun dragHandlesClampGlobalTimesAndKeepWordStartOrdering() {
        val words=listOf(ReviewWord("a",1000,1500),ReviewWord("b",2000,2500),ReviewWord("c",3000,3500))
        assertEquals(1000L to 2500L,WordTimingView.adjusted(1,1,-5000,2000,2500,words,800,4000))
        assertEquals(2000L to 4000L,WordTimingView.adjusted(1,2,5000,2000,2500,words,800,4000))
        assertEquals(3000L to 3500L,WordTimingView.adjusted(1,3,5000,2000,2500,words,800,4000))
    }
    @Test fun recoveryKeepsExactSnapshotAndOnlyCleansIncompleteExports() {
        val recovery=ExportRecovery(context); recovery.clear()
        val doc=StudioDocument(layers=listOf(StudioLayer(text="محفوظ متن")))
        recovery.save(ExportRecovery.Request(doc,1,true))
        val restored=ExportRecovery(context).pending()!!; assertEquals(doc,restored.document); assertEquals(1,restored.format); assertTrue(restored.share)
        val out=File(context.cacheDir,"recovery-output").apply { mkdirs() }
        val good=File(out,"UT1.mp4").apply { writeText("finished") }; val bad=File(out,"UT2.mp4.pending").apply { writeText("partial") }
        recovery.cleanInterruptedFiles(File(context.cacheDir,"empty"),out)
        assertTrue(good.exists()); assertFalse(bad.exists()); recovery.clear(); assertNull(recovery.pending())
    }
    @Test fun missingCuesStillAllowListeningBeyondCurrentSceneWithoutChangingDraft() {
        val track=StudioAudio(path="voice.wav",sourceDurationMs=18000,trimEndMs=18000,startMs=2000)
        val layer=StudioLayer(text="we hope")
        val original=StudioDocument(layers=listOf(layer),audio=listOf(track),video=VideoSettings(3000))
        val preview=WordReviewPreview.document(original,null,layer.id,"we really hope",2)
        assertEquals(20000L,preview.movieDurationMs()); assertEquals("we really hope",preview.layers.single().text); assertEquals(2,preview.layers.single().activeWord)
        assertEquals(3000L,original.movieDurationMs()); assertEquals("we hope",original.layers.single().text)
        assertSame(original,WordReviewPreview.document(original,original,layer.id,"unused",0))
    }
    @Test fun selectedWordLoopWrapsItsClockAndReleasesPlayback() {
        val playback=StudioPlayback({ _,_ -> },{ fail(it) })
        playback.loopWindow=100L..500L
        playback.toggle(StudioDocument(video=VideoSettings(3000)))
        org.robolectric.Shadows.shadowOf(android.os.Looper.getMainLooper()).idleFor(1600,java.util.concurrent.TimeUnit.MILLISECONDS)
        assertTrue(playback.playing); assertTrue(playback.position in 100L..500L)
        playback.release(); assertFalse(playback.playing)
    }
    @Test fun previewPreferenceNeverChangesExportDimensions() {
        val doc=StudioDocument(); val dimensions=StudioVideoExporter.dimensions(doc,1080)
        PreviewQuality.select(context,"Smooth"); assertEquals(360,PreviewQuality.width(context,"Smooth")); assertEquals(dimensions,StudioVideoExporter.dimensions(doc,1080))
        assertEquals(360,PreviewQuality.width(context,"Auto",true)); assertEquals(720,PreviewQuality.width(context,"High")); PreviewQuality.select(context,"Auto")
    }
}
