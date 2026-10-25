package me.nomi.urdutyper.presentation.ui.dashboard.ui.templates

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Color
import android.net.Uri
import me.nomi.urdutyper.domain.entity.Template
import me.nomi.urdutyper.domain.entity.TemplateCategory
import me.nomi.urdutyper.domain.entity.TemplateStyle
import org.json.JSONArray
import org.json.JSONObject
import java.io.File
import java.io.FileOutputStream

/**
 * User-added templates (Templates tab "Add Template"). Gallery images are
 * downscaled to 1120px wide, stored under filesDir/user_templates, and listed
 * in SharedPreferences. Bright-centred images get dark text automatically so
 * the couplet stays readable, the same rule the paper templates use.
 */
object UserTemplateStore {
    private const val PREFS = "user_templates"
    private const val KEY = "templates"
    private const val MAX_WIDTH = 1120

    fun load(context: Context): List<Template> {
        val raw = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
            .getString(KEY, null) ?: return emptyList()
        val list = mutableListOf<Template>()
        var pruned = false
        try {
            val arr = JSONArray(raw)
            for (i in 0 until arr.length()) {
                val o = arr.getJSONObject(i)
                val path = o.optString("path")
                if (path.isEmpty() || !File(path).exists()) {
                    pruned = true
                    continue
                }
                list.add(
                    Template(
                        id = o.optString("id"),
                        category = TemplateCategory.MY,
                        title = "میری تصویر",
                        couplet = "",
                        fontKey = "nastaliq",
                        style = TemplateStyle.GRADIENT,
                        imagePath = path,
                        darkTextOverride = o.optBoolean("darkText")
                    )
                )
            }
        } catch (_: Exception) {
            return emptyList()
        }
        if (pruned) saveAll(context, list)
        return list
    }

    fun addFromUri(context: Context, uri: Uri): Template? {
        try {
            val resolver = context.contentResolver
            val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
            resolver.openInputStream(uri)?.use { BitmapFactory.decodeStream(it, null, bounds) }
            if (bounds.outWidth <= 0) return null
            var sample = 1
            while (bounds.outWidth / sample > MAX_WIDTH) sample *= 2
            val opts = BitmapFactory.Options().apply { inSampleSize = sample }
            var bitmap = resolver.openInputStream(uri)?.use {
                BitmapFactory.decodeStream(it, null, opts)
            } ?: return null
            if (bitmap.width > MAX_WIDTH) {
                val h = (bitmap.height * MAX_WIDTH.toFloat() / bitmap.width).toInt()
                val scaled = Bitmap.createScaledBitmap(bitmap, MAX_WIDTH, h, true)
                if (scaled != bitmap) bitmap.recycle()
                bitmap = scaled
            }
            val dir = File(context.filesDir, "user_templates").apply { mkdirs() }
            val id = "user_" + System.currentTimeMillis()
            val file = File(dir, "$id.jpg")
            FileOutputStream(file).use {
                bitmap.compress(Bitmap.CompressFormat.JPEG, 90, it)
            }
            val darkText = isBrightCentre(bitmap)
            bitmap.recycle()
            val template = Template(
                id = id,
                category = TemplateCategory.MY,
                title = "میری تصویر",
                couplet = "",
                fontKey = "nastaliq",
                style = TemplateStyle.GRADIENT,
                imagePath = file.absolutePath,
                darkTextOverride = darkText
            )
            saveAll(context, load(context) + template)
            return template
        } catch (_: Exception) {
            return null
        }
    }

    fun remove(context: Context, template: Template) {
        template.imagePath?.let { File(it).delete() }
        saveAll(context, load(context).filter { it.id != template.id })
    }

    private fun saveAll(context: Context, list: List<Template>) {
        val arr = JSONArray()
        for (t in list) {
            if (t.imagePath == null) continue
            arr.put(
                JSONObject().apply {
                    put("id", t.id)
                    put("path", t.imagePath)
                    put("darkText", t.darkTextOverride ?: false)
                }
            )
        }
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
            .edit().putString(KEY, arr.toString()).apply()
    }

    /** Average luminance of the centre half; bright centres need dark text. */
    private fun isBrightCentre(bitmap: Bitmap): Boolean {
        val w = bitmap.width
        val h = bitmap.height
        val left = w / 4
        val top = h / 4
        val cw = w / 2
        val ch = h / 2
        var sum = 0.0
        var count = 0
        var y = top
        while (y < top + ch) {
            var x = left
            while (x < left + cw) {
                val c = bitmap.getPixel(x, y)
                sum += 0.299 * Color.red(c) + 0.587 * Color.green(c) + 0.114 * Color.blue(c)
                count++
                x += 4
            }
            y += 4
        }
        return count > 0 && sum / count / 255.0 > 0.6
    }
}
