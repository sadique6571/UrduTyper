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
import android.graphics.BitmapFactory

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [28], application = Application::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class AnimationRenderTest {
    private val context: Application get() = RuntimeEnvironment.getApplication()
    @Test fun gifFirstFrameDecodesAndHasLoopingMetadata() {
        val document = StudioDocument(width = 320,height = 320,video = VideoSettings(1000),
            layers = listOf(StudioLayer(text = "امید",x = 160f,y = 160f,width = 280f,size = 45f,animation = LayerAnimation("typing",0,1000,500))))
        val file = File(context.cacheDir,"animation-test.gif")
        try {
            StudioGifExporter.export(context,document,file)
            val first = checkNotNull(BitmapFactory.decodeFile(file.path)) { "GIF first frame must decode" }
            try { assertEquals(320,first.width); assertEquals(320,first.height) }
            finally { first.recycle() }
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
    @Test fun storyboardRendersCorrectScenesAndBlendsTransitionWithoutMutatingDraft() {
        val red = StudioScene(id = "first", backgroundColor = android.graphics.Color.RED, gradientColor = android.graphics.Color.RED,
            layers = emptyList(), video = VideoSettings(1000))
        val blue = red.copy(id = "second", backgroundColor = android.graphics.Color.BLUE, gradientColor = android.graphics.Color.BLUE, transition = "fade", transitionMs = 400)
        val doc = red.applyTo(StudioDocument(width = 320, height = 320)).copy(scenes = listOf(red, blue))
        val renderer = StudioAnimationRenderer(StudioRenderer(context, StudioFonts(context)))
        val first = renderer.bitmap(doc, 320, 500)
        val middle = renderer.bitmap(doc, 320, 1200)
        val last = renderer.bitmap(doc, 320, 1700)
        try {
            assertEquals(android.graphics.Color.RED, first.getPixel(160,160))
            assertEquals(android.graphics.Color.BLUE, last.getPixel(160,160))
            val blended = middle.getPixel(160,160)
            assertTrue(android.graphics.Color.red(blended) in 100..155)
            assertTrue(android.graphics.Color.blue(blended) in 100..155)
            assertEquals("first", doc.sceneId)
        } finally { first.recycle(); middle.recycle(); last.recycle() }
    }
    @Test fun cancelledGifRemovesIncompleteOutput() {
        val file = File(context.cacheDir,"cancelled-animation.gif")
        try {
            StudioGifExporter.export(context,StudioDocument(video = VideoSettings(1000)),file,cancelled = { true })
            fail("Cancellation must stop export")
        } catch (_: java.util.concurrent.CancellationException) { assertFalse(file.exists()) }
    }
}

