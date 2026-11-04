package me.nomi.urdutyper.presentation.ui.type.studio

import android.app.Application
import android.graphics.Bitmap
import android.graphics.Canvas
import com.google.gson.Gson
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [28], application = Application::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class AtmosphereTest {
    @get:Rule val folder = TemporaryFolder()
    private val context: Application get() = RuntimeEnvironment.getApplication()
    private fun pixels(bitmap: Bitmap) = IntArray(bitmap.width*bitmap.height).also { bitmap.getPixels(it,0,bitmap.width,0,0,bitmap.width,bitmap.height) }
    @Test fun explicitWeatherNamesWinAndAmbiguousImagesStayUnchanged() {
        assertEquals("leaves",AtmosphereHints.suggest("میری خزاں تصویر.jpg",intArrayOf()).effect)
        assertEquals("rain",AtmosphereHints.suggest("barish_evening.png",intArrayOf()).effect)
        assertEquals("snow",AtmosphereHints.suggest("WINTER.png",intArrayOf()).effect)
        assertEquals("none",AtmosphereHints.suggest("photo.jpg",IntArray(100) { 0xffeeeeee.toInt() }).effect)
        val snow = IntArray(100) { if (it < 65) 0xffeeeeef.toInt() else 0xff557799.toInt() }
        assertEquals("snow",AtmosphereHints.suggest("photo.png",snow).effect)
    }
    @Test fun effectsAreDeterministicSeekableAndDistinctAndIntensityZeroIsClear() {
        fun frame(effect: String, time: Long, intensity: Float = .8f): IntArray {
            val bitmap = Bitmap.createBitmap(320,320,Bitmap.Config.ARGB_8888)
            return try { StudioAtmosphere.draw(Canvas(bitmap),320f,320f,time,effect,intensity,1f); pixels(bitmap) } finally { bitmap.recycle() }
        }
        val hashes = listOf("leaves","rain","snow","particles").map { effect ->
            val a = frame(effect,1800)
            assertArrayEquals(a,frame(effect,1800))
            assertFalse(a.contentEquals(frame(effect,2100)))
            assertTrue(a.any { it != 0 }); a.contentHashCode()
        }
        assertEquals(4,hashes.distinct().size)
        assertTrue(frame("snow",1200,0f).all { it == 0 })
    }
    @Test fun weatherAndBackgroundNamesRoundTripAcrossScenesAndOldDrafts() {
        val doc = StudioDocument(backgroundName = "autumn.jpg",video = VideoSettings(atmosphere = "auto",atmosphereIntensity = .8f,atmosphereSpeed = 1.3f))
        val store = StudioStore(folder.newFolder())
        store.save(doc.copy(scenes = listOf(doc.captureScene())))
        val loaded = store.load(doc.id)!!
        assertEquals("autumn.jpg",loaded.orderedScenes().single().backgroundName)
        assertEquals(.8f,loaded.video.atmosphereIntensity,0f)
        assertEquals(1.3f,loaded.video.atmosphereSpeed,0f)
        val old = Gson().fromJson("{}",VideoSettings::class.java)
        assertEquals(.55f,old.atmosphereIntensity,0f); assertEquals(1f,old.atmosphereSpeed,0f)
    }
    @Test fun autoUsesTheSameResolvedWeatherAsManualRender() {
        val renderer = StudioAnimationRenderer(StudioRenderer(context,StudioFonts(context)))
        val doc = StudioDocument(width = 320,height = 320,backgroundStyle = "winter",layers = emptyList(),video = VideoSettings(atmosphere = "auto"))
        val auto = renderer.bitmap(doc,320,1200)
        val snow = renderer.bitmap(doc.copy(video = doc.video.copy(atmosphere = "snow")),320,1200)
        try { assertArrayEquals(pixels(auto),pixels(snow)) } finally { auto.recycle(); snow.recycle() }
    }
}
