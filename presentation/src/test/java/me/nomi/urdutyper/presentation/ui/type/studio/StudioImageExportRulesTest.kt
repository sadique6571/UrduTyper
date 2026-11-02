package me.nomi.urdutyper.presentation.ui.type.studio

import org.junit.Assert.*
import org.junit.Test

class StudioImageExportRulesTest {
    @Test fun pngBackgroundFollowsExplicitTransparencyChoiceWithoutChangingDraft() {
        val document = StudioDocument(backgroundVisible = false,backgroundStyle = "LUXE_MOONLIT_LAKE")
        val opaque = StudioImageExportRules.snapshot(document,true,false)
        assertTrue(opaque.backgroundVisible)
        assertEquals(document.backgroundStyle,opaque.backgroundStyle)
        assertFalse(StudioImageExportRules.snapshot(opaque,true,true).backgroundVisible)
        assertFalse(document.backgroundVisible)
    }
    @Test fun jpgUsesWhiteForHiddenBackgroundAndKeepsVisibleArtwork() {
        val document = StudioDocument(backgroundVisible = false,backgroundImage = "/photo.png",backgroundStyle = "LUXE_MOONLIT_LAKE")
        val white = StudioImageExportRules.snapshot(document,false,false)
        assertTrue(white.backgroundVisible)
        assertEquals("",white.backgroundStyle)
        assertEquals("",white.backgroundImage)
        assertEquals(0xffffffff.toInt(),white.backgroundColor)
        val visible = document.copy(backgroundVisible = true)
        assertEquals(visible,StudioImageExportRules.snapshot(visible,false,false))
    }
}
