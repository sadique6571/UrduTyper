package me.nomi.urdutyper.presentation.ui.type.studio

import android.content.Context
import org.json.JSONArray

class StudioDesignPreferences(context: Context) {
    private val prefs = context.getSharedPreferences("studio-design-preferences",0)
    fun favoriteFonts(): Set<String> = prefs.getStringSet("favorite-fonts",emptySet()).orEmpty().toSet()
    fun toggleFont(id: String) { val values = favoriteFonts().toMutableSet(); if (!values.add(id)) values.remove(id); prefs.edit().putStringSet("favorite-fonts",values).apply() }
    private fun strings(key: String): List<String> = runCatching { val data = JSONArray(prefs.getString(key,"[]")); (0 until data.length()).map { data.getString(it) } }.getOrDefault(emptyList())
    fun recentFonts() = strings("recent-fonts")
    fun recordFont(id: String) { prefs.edit().putString("recent-fonts",JSONArray((listOf(id)+recentFonts()).distinct().take(10)).toString()).apply() }
    fun colors() = strings("recent-colors").mapNotNull { it.toIntOrNull() }
    fun recordColor(color: Int) { prefs.edit().putString("recent-colors",JSONArray((listOf(color.toString())+strings("recent-colors")).distinct().take(10)).toString()).apply() }
    fun filterFonts(fonts: List<StudioFont>, tab: String, query: String): List<StudioFont> {
        val recent = recentFonts(); val favorite = favoriteFonts()
        return fonts.filter { font ->
            (when(tab) { "Recent" -> font.id in recent; "Favorites" -> font.id in favorite; "Imported" -> font.path.isNotBlank(); else -> true }) &&
                (query.isBlank() || font.name.contains(query,true) || font.id.contains(query,true))
        }.let { if (tab == "Recent") it.sortedBy { font -> recent.indexOf(font.id) } else it }
    }
}
