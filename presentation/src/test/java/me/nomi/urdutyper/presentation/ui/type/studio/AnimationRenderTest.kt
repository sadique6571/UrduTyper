package me.nomi.urdutyper.presentation.ui.type.studio

import android.app.Application
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import java.io.File
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Movie

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [28], application = Application::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class AnimationRenderTest {
    private val context: Application get() = RuntimeEnvironment.getApplication()
    @Test fun gifIsDecodableHasMultipleChangingFramesAndLoops() {
        val document = StudioDocument(width = 320,height = 320,video = VideoSettings(1000),
            layers = listOf(StudioLayer(text = "امید",x = 160f,y = 160f,width = 280f,size = 45f,animation = LayerAnimation("typing",0,1000,500))))
        val file = File(context.cacheDir,"animation-test.gif")
        try {
            StudioGifExporter.export(context,document,file)
            val bytes = file.readBytes()
            val movie = checkNotNull(Movie.decodeByteArray(bytes,0,bytes.size)) { "GIF must decode on Android" }
            assertEquals(320,movie.width()); assertEquals(320,movie.height())
            assertEquals(1000,movie.duration())
            val first = Bitmap.createBitmap(320,320,Bitmap.Config.ARGB_8888)
            val last = Bitmap.createBitmap(320,320,Bitmap.Config.ARGB_8888)
            try {
                movie.setTime(0); movie.draw(Canvas(first),0f,0f)
                movie.setTime(875); movie.draw(Canvas(last),0f,0f)
                assertFalse("GIF must contain changing frames",first.sameAs(last))
            } finally { first.recycle(); last.recycle() }
            assertTrue(file.readBytes().toString(Charsets.ISO_8859_1).contains("NETSCAPE2.0"))
        } finally { file.delete() }
    }
    @Test fun previewAndExportFramesAreDeterministicAndDoNotMutateDraft() {
        val document = StudioDocument(video = VideoSettings(2000,"pan","rain"),layers = listOf(StudioLayer(animation = LayerAnimation("words",0,2000,1000))))
        val renderer = StudioAnimationRenderer(StudioRenderer(context,StudioFonts(context)))
        val first = renderer.bitmap(document,240,800)
        val second = StudioAnimationRenderer(StudioRenderer(context,StudioFonts(context))).bitmap(document,240,800)
        try { assertTrue(first.sameAs(second)); assertEquals("اپنا متن لکھیں",document.layers.single().text) }
        finally { first.recycle(); second.recycle() }
    }
    @Test fun videoDimensionsAreEvenAndFitEncoderBounds() {
        listOf(1080 to 1920,1920 to 1080,1080 to 1080,320 to 4096).forEach { (w,h) ->
            val (width,height) = StudioVideoExporter.dimensions(StudioDocument(width = w,height = h),1080)
            assertEquals(0,width % 2); assertEquals(0,height % 2)
            assertTrue(width in 64..1920 && height in 64..1920)
        }
    }
    @Test fun cancelledGifRemovesIncompleteOutput() {
        val file = File(context.cacheDir,"cancelled-animation.gif")
        try {
            StudioGifExporter.export(context,StudioDocument(video = VideoSettings(1000)),file,cancelled = { true })
            fail("Cancellation must stop export")
        } catch (_: java.util.concurrent.CancellationException) { assertFalse(file.exists()) }
    }
}
