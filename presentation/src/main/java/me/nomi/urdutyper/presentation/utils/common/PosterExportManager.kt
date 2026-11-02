package me.nomi.urdutyper.presentation.utils.common

import android.content.Context
import android.net.Uri
import android.view.View
import android.widget.EditText
import androidx.core.content.FileProvider
import java.io.File

enum class PosterExportPreset {
    NORMAL,
    HD,
    WHATSAPP
}

object PosterExportManager {
    fun savePoster(
        context: Context,
        posterView: View,
        textView: EditText,
        effects: Set<Int>,
        preset: PosterExportPreset = PosterExportPreset.NORMAL
    ): File? = when (preset) {
        PosterExportPreset.NORMAL -> ImageMaker.savePosterBitmap(
            context = context,
            posterView = posterView,
            textView = textView,
            effects = effects,
            targetWidth = 1080,
            targetHeight = 1920,
            prefix = "UT",
            quality = 95,
            dialogPrefix = "Saved: "
        )
        PosterExportPreset.HD -> ImageMaker.savePosterBitmap(
            context = context,
            posterView = posterView,
            textView = textView,
            effects = effects,
            targetWidth = 1440,
            targetHeight = 2560,
            prefix = "UT",
            quality = 95,
            dialogPrefix = "Saved (HD): "
        )
        PosterExportPreset.WHATSAPP -> ImageMaker.savePosterBitmap(
            context = context,
            posterView = posterView,
            textView = textView,
            effects = effects,
            targetWidth = 1080,
            targetHeight = 1920,
            prefix = "UTWA",
            quality = 85,
            dialogPrefix = "Saved (WhatsApp size): "
        )
    }

    fun createUri(context: Context, file: File): Uri = FileProvider.getUriForFile(
        context,
        context.applicationContext.packageName + ".provider",
        file
    )
}
