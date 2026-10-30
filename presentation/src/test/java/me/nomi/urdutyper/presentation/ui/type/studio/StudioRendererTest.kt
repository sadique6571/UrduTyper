package me.nomi.urdutyper.presentation.ui.type.studio

import android.app.Application
import android.graphics.Color
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
class StudioRendererTest {
    private val context: Application get() = RuntimeEnvironment.getApplication()

    @Test fun selectingAnUrduFontChangesRenderedGlyphsAndSurvivesReload() {
        val renderer = StudioRenderer(context, StudioFonts(context))
        val original = StudioDocument()
        val changed = original.copy(layers = original.layers.map { it.copy(fontId = "alvi") })
        val store = StudioStore(java.io.File(context.cacheDir, "font-regression"))
        store.save(changed)
        val before = renderer.bitmap(original, 360, false)
        val after = renderer.bitmap(changed, 360, false)
        val reopened = StudioRenderer(context, StudioFonts(context)).bitmap(store.load(changed.id)!!, 360, false)
        try {
            assertFalse("Urdu font selection must change glyph rendering", before.sameAs(after))
            assertTrue("Reopening must preserve the chosen font and its export", after.sameAs(reopened))
        } finally { before.recycle(); after.recycle(); reopened.recycle() }
    }

    @Test fun transparentPngRemovesOnlyBackgroundAndKeepsText() {
        val renderer = StudioRenderer(context, StudioFonts(context))
        val document = StudioDocument()
        val solid = renderer.bitmap(document, 240, false)
        val alpha = renderer.bitmap(document, 240, true)
        try {
            assertEquals(255, Color.alpha(solid.getPixel(0, 0)))
            assertEquals(0, Color.alpha(alpha.getPixel(0, 0)))
            val pixels = IntArray(alpha.width * alpha.height)
            alpha.getPixels(pixels, 0, alpha.width, 0, 0, alpha.width, alpha.height)
            assertTrue("Text must remain in a transparent export", pixels.any { Color.alpha(it) > 0 })
        } finally { solid.recycle(); alpha.recycle() }
    }

    @Test fun hiddenLayersDoNotAppearInExportAndRotationChangesOutput() {
        val renderer = StudioRenderer(context, StudioFonts(context))
        val document = StudioDocument()
        val hidden = document.copy(layers = document.layers.map { it.copy(visible = false) })
        val rotated = document.copy(layers = document.layers.map { it.copy(rotation = 45f) })
        val regular = renderer.bitmap(document, 240, false)
        val removed = renderer.bitmap(hidden, 240, false)
        val tilted = renderer.bitmap(rotated, 240, false)
        try {
            assertFalse(regular.sameAs(removed))
            assertFalse(regular.sameAs(tilted))
        } finally { regular.recycle(); removed.recycle(); tilted.recycle() }
    }

    @Test fun exportUsesDocumentAspectRatioRegardlessOfDisplayZoom() {
        val document = StudioDocument(width = 1080, height = 1920)
        val bitmap = StudioRenderer(context, StudioFonts(context)).bitmap(document, 540, false)
        try { assertEquals(540, bitmap.width); assertEquals(960, bitmap.height) }
        finally { bitmap.recycle() }
    }
}
