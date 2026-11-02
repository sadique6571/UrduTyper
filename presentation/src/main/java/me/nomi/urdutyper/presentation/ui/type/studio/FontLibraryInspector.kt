package me.nomi.urdutyper.presentation.ui.type.studio

import android.content.Context
import android.graphics.Typeface
import android.text.Editable
import android.text.TextWatcher
import android.view.Gravity
import android.view.inputmethod.EditorInfo
import android.widget.EditText
import android.widget.LinearLayout
import android.widget.Toast

class FontLibraryInspector(
    private val context: Context, private val fonts: StudioFonts, private val original: StudioDocument,
    private val layer: StudioLayer, private val preview: (StudioDocument) -> Unit,
    private val apply: (StudioLayer) -> Unit, private val importFont: () -> Unit,
    private val closed: () -> Unit
) {
    val panel = StudioInspector(context,"Fonts",fonts)
    private val preferences = StudioDesignPreferences(context)
    private var candidate = layer
    private var tab = "All"
    private var query = ""
    private val grid = panel.column()
    init {
        val search = EditText(context).apply {
            hint = "Search fonts"; isSingleLine = true; imeOptions = EditorInfo.IME_ACTION_DONE
            setTextColor(LuxeTheme.text); setHintTextColor(LuxeTheme.muted); textSize = 14f
            background = panel.shape(); setPadding(panel.dp(14),0,panel.dp(14),0)
            contentDescription = "Search font library"
            addTextChangedListener(object : TextWatcher {
                override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) = Unit
                override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) { query = s.toString(); drawCards() }
                override fun afterTextChanged(s: Editable?) = Unit
            })
        }
        panel.body.addView(search,LinearLayout.LayoutParams(-1,panel.dp(48)).apply { bottomMargin = panel.dp(10) })
        panel.body.addView(grid)
        panel.body.addView(panel.button("Import TTF / OTF font") { panel.dismiss(); importFont() })
        panel.setOnDismissListener { closed() }
        drawTabs(); drawCards(); render()
        panel.actions("Apply font", {
            preferences.recordFont(candidate.fontId); apply(candidate); panel.dismiss()
        })
    }
    private fun render() {
        val doc = original.copy(layers = original.layers.map { if (it.id == layer.id) candidate else it })
        preview(doc); panel.render(doc)
    }
    private fun drawTabs() {
        panel.tabs(listOf("All","Recent","Favorites","Imported"),tab) { tab = it; drawTabs(); drawCards() }
    }
    private fun drawCards() {
        grid.removeAllViews()
        val favorites = preferences.favoriteFonts()
        val visibleFonts = preferences.filterFonts(fonts.all(),tab,query)
        if (visibleFonts.isEmpty()) grid.addView(panel.text("No fonts here yet. Try another filter or import a font.").apply { setPadding(0,panel.dp(16),0,panel.dp(16)) })
        visibleFonts.forEach { font ->
            val selected = font.id == candidate.fontId
            val compatible = font.urdu || layer.text.none { it.code in 0x0600..0x08ff }
            val card = panel.column().apply {
                background = panel.shape(selected = selected); setPadding(panel.dp(12),panel.dp(8),panel.dp(8),panel.dp(8))
                isClickable = true; isFocusable = true; contentDescription = "Choose font ${font.name}"
                alpha = if (compatible) 1f else .5f
                setOnClickListener {
                    if (!compatible) Toast.makeText(context,"This font is Latin only. Select an Urdu font for this text.",Toast.LENGTH_SHORT).show()
                    else { candidate = candidate.copy(fontId = font.id); render(); drawCards() }
                }
            }
            val caption = panel.row().apply { gravity = Gravity.CENTER_VERTICAL }
            caption.addView(panel.text((if (selected) "✓ " else "")+font.name+(if (!font.urdu) " · Latin only" else ""),12),LinearLayout.LayoutParams(0,-2,1f))
            caption.addView(panel.button(if (font.id in favorites) "★" else "☆") {
                preferences.toggleFont(font.id); drawCards()
            }.apply { contentDescription = "Favorite font ${font.name}" },LinearLayout.LayoutParams(panel.dp(48),panel.dp(48)))
            card.addView(panel.text(layer.text.take(160).ifBlank { "خوابوں کو حقیقت بناؤ" },25).apply {
                typeface = this@FontLibraryInspector.fonts.resolve(font.id); gravity = Gravity.CENTER; maxLines = 2
                setPadding(0,panel.dp(8),0,panel.dp(8)); minimumHeight = panel.dp(64)
            })
            card.addView(caption)
            grid.addView(card,LinearLayout.LayoutParams(-1,-2).apply { bottomMargin = panel.dp(10) })
        }
    }
    fun show() = panel.show()
}
