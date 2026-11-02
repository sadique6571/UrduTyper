package me.nomi.urdutyper.presentation.ui.type.studio

/** Preview and image export must resolve the background the same way. */
object StudioImageExportRules {
    fun snapshot(document: StudioDocument, png: Boolean, transparent: Boolean): StudioDocument = when {
        png -> document.copy(backgroundVisible = !transparent)
        !document.backgroundVisible -> document.copy(backgroundVisible = true,backgroundStyle = "",backgroundImage = "",backgroundColor = 0xffffffff.toInt(),gradientColor = 0xffffffff.toInt())
        else -> document
    }
}
