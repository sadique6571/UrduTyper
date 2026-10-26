package me.nomi.urdutyper.presentation.ui.dashboard.ui.templates

import android.content.Context
import org.json.JSONArray

/**
 * Recently used templates (Templates tab strip above the grid). Only
 * template ids are stored, most-recent-first, capped at 10; ids that no
 * longer resolve to a template are dropped when the strip is built.
 */
object RecentTemplates {
    private const val PREFS = "recent_templates"
    private const val KEY = "ids"
    private const val MAX = 10

    fun record(context: Context, id: String) {
        if (id.isEmpty()) return
        val ids = (listOf(id) + loadIds(context)).distinct().take(MAX)
        val arr = JSONArray()
        for (entry in ids) arr.put(entry)
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
            .edit().putString(KEY, arr.toString()).apply()
    }

    fun loadIds(context: Context): List<String> {
        val raw = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
            .getString(KEY, null) ?: return emptyList()
        return try {
            val arr = JSONArray(raw)
            List(arr.length()) { arr.getString(it) }
        } catch (_: Exception) {
            emptyList()
        }
    }
}
