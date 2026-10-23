package me.nomi.urdutyper.presentation.utils.common

import android.content.Context
import android.graphics.Typeface
import android.net.Uri
import android.provider.OpenableColumns
import android.webkit.MimeTypeMap
import me.nomi.urdutyper.presentation.utils.extensions.common.dialog
import java.io.File
import java.io.FileOutputStream
import java.io.IOException


object Fonts {
    fun copyFontToCache(context: Context, uri: Uri) {
        try {
            val cursor = context.contentResolver.query(uri, null, null, null, null, null)
            val nameIndex = cursor?.getColumnIndex(OpenableColumns.DISPLAY_NAME)
            val fileName = if (cursor != null && cursor.moveToFirst() && nameIndex != null) {
                cursor.getString(nameIndex)
            } else {
                System.currentTimeMillis().toString()
            } ?: System.currentTimeMillis().toString()
            cursor?.close()

            val fileExtension = MimeTypeMap.getSingleton()
                .getExtensionFromMimeType(context.contentResolver.getType(uri))
            val completeFileName =
                if (fileExtension != null) "$fileName.$fileExtension" else fileName
            if (completeFileName.endsWith("ttf")) {

                val inputStream = context.contentResolver.openInputStream(uri)
                val outputFile = File(context.cacheDir, completeFileName)
                val outputStream = FileOutputStream(outputFile)

                inputStream?.copyTo(outputStream)

                inputStream?.close()
                outputStream.close()
            } else throw Exception("The selected file does not appear to be a valid TrueType font (.ttf). Please select a valid .ttf font file.")
        } catch (e: Exception) {
            context.dialog(e.localizedMessage?: "Something went wrong").show()
        }
    }


    fun getAllFontsFromCache(context: Context): List<Typeface> {
        val fonts: MutableList<Typeface> = ArrayList()
        val cacheDir: File = context.cacheDir
        val fontFiles = cacheDir.listFiles()

        if (fontFiles != null) {
            for (fontFile in fontFiles) {
                if (fontFile != null && fontFile.extension == "ttf") {
                    try {
                        val typeface: Typeface = Typeface.createFromFile(fontFile)
                        fonts.add(typeface)
                    } catch (e: Exception) {
                        e.printStackTrace()
                    }
                }
            }
        }

        return fonts.toSet().toList()
    }
}
