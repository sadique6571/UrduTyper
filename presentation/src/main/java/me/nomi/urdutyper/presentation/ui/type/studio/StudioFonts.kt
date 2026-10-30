package me.nomi.urdutyper.presentation.ui.type.studio

import android.content.Context
import android.graphics.Typeface
import android.net.Uri
import androidx.core.content.res.ResourcesCompat
import me.nomi.urdutyper.R
import java.io.File
import java.util.UUID

data class StudioFont(val id: String, val name: String, val resource: Int = 0, val path: String = "", val urdu: Boolean = true)

/** Use stable IDs, never a transient Typeface reference, in projects and undo states. */
class StudioFonts(private val context: Context) {
    private val cache = java.util.concurrent.ConcurrentHashMap<String, Typeface>()
    val builtIn = listOf(
        StudioFont("nastaliq", "Noto Nastaliq", R.font.noto_nastaliq_urdu),
        StudioFont("alvi", "Alvi Nastaleeq", R.font.alqalam_alvi_nastaleeq),
        StudioFont("almas", "Sameer Almas", R.font.aa_sameer_almas_regular),
        StudioFont("divangiry", "Sameer Divangiry", R.font.aa_sameer_divangiry_regular),
        StudioFont("qamri", "Sameer Qamri", R.font.aa_sameer_qamri_regular),
        StudioFont("rafiya", "Sameer Rafiya", R.font.aa_sameer_rafiya_unicode_regular),
        StudioFont("zikran", "Sameer Zikran", R.font.aa_sameer_zikran_regular),
        StudioFont("aadil", "Aadil", R.font.aadil_aadil),
        StudioFont("gandhara", "Gandhara Suls", R.font.gandhara_suls_regular),
        StudioFont("sumbali", "AlQalam Sumbali", R.font.alqalam_khat_e_sumbali_regular),
        StudioFont("makki", "AlQalam Makki", R.font.alqalam_makki_regular),
        StudioFont("shekastah", "AlQalam Shekastah", R.font.alqalam_shekastah),
        StudioFont("pencil", "PDMS Pencil Nafees", R.font.pdms_pencil_nafees),
        StudioFont("a300", "A 300", R.font.a_300_regular),
        StudioFont("lobster", "Lobster · Latin only", R.font.lobster_regular, urdu = false)
    )
    private val directory get() = File(context.filesDir, "studio-fonts").apply { mkdirs() }
    init {
        // Preserve fonts imported by the previous editor, without depending on cache retention.
        context.cacheDir.listFiles().orEmpty().filter {
            it.isFile && it.extension.lowercase() in listOf("ttf", "otf")
        }.forEach { legacy ->
            val target = File(directory, "legacy_${legacy.name}")
            if (!target.exists()) runCatching {
                Typeface.createFromFile(legacy)
                legacy.copyTo(target, overwrite = false)
            }
        }
    }
    fun all(): List<StudioFont> = builtIn + directory.listFiles().orEmpty()
        .filter { it.extension.lowercase() in listOf("ttf", "otf") }
        .sortedBy { it.name }.map { StudioFont("import:${it.name}", it.nameWithoutExtension.substringAfter('_'), path = it.path) }
    fun resolve(id: String): Typeface = cache.getOrPut(id) {
        val font = all().firstOrNull { it.id == id } ?: builtIn.first()
        runCatching {
            if (font.resource != 0) ResourcesCompat.getFont(context, font.resource)!!
            else Typeface.createFromFile(font.path)
        }.getOrElse { ResourcesCompat.getFont(context, R.font.noto_nastaliq_urdu)!! }
    }
    fun import(uri: Uri): StudioFont {
        var name = "Imported font.ttf"
        context.contentResolver.query(uri, arrayOf(android.provider.OpenableColumns.DISPLAY_NAME), null, null, null)?.use {
            if (it.moveToFirst()) name = it.getString(0) ?: name
        }
        val extension = name.substringAfterLast('.', "").lowercase()
        require(extension in listOf("ttf", "otf")) { "Choose a .ttf or .otf font" }
        val safeName = name.substringBeforeLast('.').replace(Regex("[^\\p{L}\\p{N} ._-]"), "_").take(80) + ".$extension"
        val target = File(directory, "${UUID.randomUUID()}_$safeName")
        try {
            context.contentResolver.openInputStream(uri)!!.use { input ->
                target.outputStream().use { output ->
                    val buffer = ByteArray(8192)
                    var total = 0
                    while (true) {
                        val read = input.read(buffer)
                        if (read < 0) break
                        total += read
                        require(total <= 20 * 1024 * 1024) { "Font must be smaller than 20 MB" }
                        output.write(buffer, 0, read)
                    }
                }
            }
            val typeface = Typeface.createFromFile(target)
            val font = StudioFont("import:${target.name}", name.substringBeforeLast('.'), path = target.path)
            cache[font.id] = typeface
            return font
        } catch (error: Exception) { target.delete(); throw error }
    }
}
