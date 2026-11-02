package me.nomi.urdutyper.presentation.ui.type.studio

import android.app.Application
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
class InspectorPersistenceTest {
    @get:Rule val folder = TemporaryFolder()
    private val context: Application get() = RuntimeEnvironment.getApplication()
    @Test fun newEffectSettingsPersistAndOldLayersGetSafeDefaults() {
        val layer = StudioLayer(effects = listOf("Gradient","Drop shadow"),effectIntensity = .8f,shadowStrength = .3f,gradientStart = 0xff00ff00.toInt(),gradientEnd = 0xff0000ff.toInt())
        val doc = StudioDocument(layers = listOf(layer))
        val store = StudioStore(folder.newFolder()); store.save(doc)
        assertEquals(doc,store.load(doc.id))
        val old = Gson().fromJson("""{"text":"امید","effects":[]}""",StudioLayer::class.java)
        assertEquals(1f,old.effectIntensity,0f); assertEquals(1f,old.shadowStrength,0f)
        assertNull(old.gradientStart)
    }
    @Test fun effectStrengthAndCustomGradientChangeExportPixels() {
        val renderer = StudioRenderer(context,StudioFonts(context))
        val layer = StudioLayer(text = "امید",x = 160f,y = 160f,width = 280f,size = 50f,color = 0xffa86de8.toInt(),effects = listOf("Neon"))
        val doc = StudioDocument(width = 320,height = 320,layers = listOf(layer))
        val low = renderer.bitmap(doc.copy(layers = listOf(layer.copy(effectIntensity = .1f))),320,false)
        val high = renderer.bitmap(doc.copy(layers = listOf(layer.copy(effectIntensity = 1.8f))),320,false)
        val gradient = renderer.bitmap(doc.copy(layers = listOf(layer.copy(effects = listOf("Gradient"),gradientStart = 0xff00ff00.toInt(),gradientEnd = 0xff0000ff.toInt()))),320,false)
        try { assertFalse(low.sameAs(high)); assertFalse(high.sameAs(gradient)) }
        finally { low.recycle(); high.recycle(); gradient.recycle() }
    }
    @Test fun fontPreferencesPersistOrderedRecentsAndCombineSearchWithFilters() {
        context.getSharedPreferences("studio-design-preferences",0).edit().clear().commit()
        val prefs = StudioDesignPreferences(context)
        prefs.recordFont("alvi"); prefs.recordFont("makki"); prefs.recordFont("alvi")
        prefs.toggleFont("makki")
        val reload = StudioDesignPreferences(context)
        assertEquals(listOf("alvi","makki"),reload.recentFonts())
        val fonts = listOf(StudioFont("alvi","Alvi"),StudioFont("makki","AlQalam Makki"),StudioFont("custom","Imported",path = "/font.ttf"))
        assertEquals(listOf("makki"),reload.filterFonts(fonts,"Favorites","mak").map { it.id })
        assertEquals(listOf("custom"),reload.filterFonts(fonts,"Imported","").map { it.id })
        prefs.toggleFont("makki"); assertTrue(reload.favoriteFonts().isEmpty())
    }
}
