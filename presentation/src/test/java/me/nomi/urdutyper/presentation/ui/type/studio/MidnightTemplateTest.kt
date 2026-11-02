package me.nomi.urdutyper.presentation.ui.type.studio

import android.app.Application
import me.nomi.urdutyper.domain.TemplateCatalog
import me.nomi.urdutyper.presentation.ui.type.ui.BACKGROUND_ENTRIES
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [28], application = Application::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class MidnightTemplateTest {
    @Test fun bundledPhotoTemplatesRenderAndKeepTextIndependent() {
        val context: Application = RuntimeEnvironment.getApplication()
        val renderer = StudioRenderer(context,StudioFonts(context))
        val templates = TemplateCatalog.templates.filter { it.backgroundKey?.startsWith("LUXE_") == true }
        assertEquals(6,templates.size)
        templates.forEach { template ->
            assertNotNull(BACKGROUND_ENTRIES.firstOrNull { it.styleName == template.backgroundKey })
            val doc = StudioDocument(width = 1080,height = 1620,backgroundStyle = template.backgroundKey!!,
                layers = listOf(StudioLayer(text = template.couplet,y = 810f,color = if (template.style.darkText) 0xff1d352e.toInt() else android.graphics.Color.WHITE)))
            val original = renderer.bitmap(doc,240,false)
            val edited = renderer.bitmap(doc.copy(layers = doc.layers.map { it.copy(text = "نئی امید") }),240,false)
            try { assertEquals(360,original.height); assertFalse("Text must remain editable for ${template.id}",original.sameAs(edited)) }
            finally { original.recycle(); edited.recycle() }
        }
    }
}
