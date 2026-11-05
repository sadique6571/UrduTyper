package me.nomi.urdutyper.presentation.ui.type.studio

import android.app.ActivityManager
import android.content.Context

/** Local editing preference, deliberately excluded from documents and export settings. */
object PreviewQuality {
    val labels = listOf("Auto","Smooth","High")
    fun selected(context: Context) = context.getSharedPreferences("studio-preview",0).getString("quality","Auto").orEmpty().takeIf { it in labels } ?: "Auto"
    fun select(context: Context, value: String) { require(value in labels); context.getSharedPreferences("studio-preview",0).edit().putString("quality",value).apply() }
    fun width(context: Context, mode: String, slow: Boolean = false): Int = when(mode) {
        "Smooth" -> 360
        "High" -> 720
        else -> if (slow || (context.getSystemService(Context.ACTIVITY_SERVICE) as ActivityManager).memoryClass < 192) 360 else 540
    }
}
