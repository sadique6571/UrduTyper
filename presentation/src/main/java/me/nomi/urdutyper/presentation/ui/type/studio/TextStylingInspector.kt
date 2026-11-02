package me.nomi.urdutyper.presentation.ui.type.studio

import android.content.Context
import android.graphics.Color
import android.graphics.Typeface
import android.text.Editable
import android.text.TextWatcher
import android.view.Gravity
import android.view.View
import android.widget.*
import com.google.android.material.chip.Chip
import com.google.android.material.chip.ChipGroup

class TextStylingInspector(
    private val context: Context, private val fonts: StudioFonts, private val original: StudioDocument,
    private val layer: StudioLayer, initialTab: String, private val preview: (StudioDocument) -> Unit,
    private val apply: (StudioLayer) -> Unit, private val closed: () -> Unit
) {
    val panel = StudioInspector(context,"Text style",fonts)
    private val preferences = StudioDesignPreferences(context)
    private val renderer = StudioRenderer(context,fonts)
    private var candidate = layer
    private var tab = initialTab
    private var gradientSide = "Start"
    private var advanced = false
    private var allEffects = false
    private val fills = listOf("Gradient","Horizontal gradient","Diagonal gradient","Gold","Rainbow","Pattern")
    init {
        panel.setOnDismissListener { closed() }
        panel.actions("Apply style", {
            preferences.recordColor(candidate.color)
            candidate.gradientStart?.let { preferences.recordColor(it) }; candidate.gradientEnd?.let { preferences.recordColor(it) }
            apply(candidate); panel.dismiss()
        }, reset = { candidate = layer; render(); draw() })
        draw(); render()
    }
    private fun change(next: StudioLayer) { candidate = next; render() }
    private fun render() {
        val doc = original.copy(layers = original.layers.map { if (it.id == layer.id) candidate else it })
        preview(doc); panel.render(doc)
    }
    private fun preset(name: String): StudioLayer = when(name) {
        "Gold" -> candidate.copy(color = LuxeTheme.gold,effects = listOf("Gold","Emboss","Drop shadow"),effectIntensity = 1f,shadowStrength = .6f)
        "Neon" -> candidate.copy(color = 0xffbf70ef.toInt(),effects = listOf("Neon","Drop shadow"),effectIntensity = 1f,shadowStrength = .6f)
        "Emboss" -> candidate.copy(color = LuxeTheme.text,effects = listOf("Emboss","Drop shadow"),effectIntensity = 1f,shadowStrength = .6f)
        else -> candidate.copy(color = layer.color,effects = emptyList(),effectIntensity = 1f,shadowStrength = 1f)
    }
    private fun draw() {
        panel.tabs(listOf("Style","Color","Effects"),tab) { tab = it; draw() }
        panel.body.removeAllViews()
        when(tab) { "Style" -> drawStyle(); "Color" -> drawColor(); else -> drawEffects() }
    }
    private fun drawStyle() {
        panel.section("Text formatting")
        val formatting = panel.row()
        formatting.addView(panel.button("B",candidate.bold) { change(candidate.copy(bold = !candidate.bold)); draw() }.apply { typeface = Typeface.DEFAULT_BOLD; contentDescription = "Bold text" },LinearLayout.LayoutParams(0,panel.dp(48),1f))
        formatting.addView(panel.button("I",candidate.italic) { change(candidate.copy(italic = !candidate.italic)); draw() }.apply { setTypeface(Typeface.DEFAULT,Typeface.ITALIC); contentDescription = "Italic text" },LinearLayout.LayoutParams(0,panel.dp(48),1f))
        listOf("left" to "⇤","center" to "≡","right" to "⇥").forEach { (alignment,label) ->
            formatting.addView(panel.button(label,candidate.alignment == alignment) { change(candidate.copy(alignment = alignment)); draw() }.apply { contentDescription = "Align text $alignment" },LinearLayout.LayoutParams(0,panel.dp(48),1f))
        }
        panel.body.addView(formatting)
        panel.section("Font size")
        panel.body.addView(FontSizeControl(context).apply {
            showSelection(candidate.size,true)
            onPreview = { change(candidate.copy(size = it)) }; onCommit = { change(candidate.copy(size = it)) }
        })
        panel.slider("Line spacing",candidate.lineSpacing,.8f,2.4f) { change(candidate.copy(lineSpacing = it)) }
        panel.body.addView(panel.button(if (advanced) "Hide transform controls" else "Transform · width · rotation · opacity") { advanced = !advanced; draw() })
        if (advanced) {
            panel.slider("Text box width",candidate.width,40f,original.width*1.5f) { change(candidate.copy(width = it)) }
            panel.slider("Rotation",candidate.rotation,0f,359f) { change(candidate.copy(rotation = it)) }
            panel.slider("Opacity",candidate.opacity,.05f,1f) { change(candidate.copy(opacity = it)) }
        }
    }
    private fun drawColor() {
        val gradient = "Gradient" in candidate.effects
        panel.choices(listOf("Solid","Gradient"),if (gradient) "Gradient" else "Solid") { mode ->
            if (mode == "Solid") change(candidate.copy(effects = candidate.effects.filterNot { it in fills }))
            else change(candidate.copy(effects = candidate.effects.filterNot { it in fills }+"Gradient",gradientStart = candidate.gradientStart ?: candidate.color,gradientEnd = candidate.gradientEnd ?: 0xff9b6bd3.toInt()))
            draw()
        }
        if (gradient) panel.choices(listOf("Start","End"),gradientSide) { gradientSide = it; draw() }
        fun selectedColor() = if (!gradient) candidate.color else if (gradientSide == "Start") candidate.gradientStart ?: candidate.color else candidate.gradientEnd ?: candidate.color
        fun choose(color: Int) {
            change(if (!gradient) candidate.copy(color = color) else if (gradientSide == "Start") candidate.copy(gradientStart = color) else candidate.copy(gradientEnd = color))
        }
        panel.section("Palette")
        val colors = (listOf(LuxeTheme.gold,LuxeTheme.text,0xffed7781.toInt(),0xffa65bd5.toInt(),0xff62c7ba.toInt(),Color.BLACK,Color.WHITE)+preferences.colors()).distinct()
        panel.body.addView(panel.horizontal {
            colors.forEach { color ->
                val swatch = FrameLayout(context).apply {
                    contentDescription = String.format(java.util.Locale.ROOT,"Color #%08X",color)
                    isClickable = true; isFocusable = true
                    setOnClickListener { choose(color); draw() }
                }
                swatch.addView(View(context).apply { background = android.graphics.drawable.GradientDrawable().apply {
                    shape = android.graphics.drawable.GradientDrawable.OVAL; setColor(color)
                    setStroke(panel.dp(if (color == selectedColor()) 3 else 1),if (color == selectedColor()) LuxeTheme.gold else LuxeTheme.border)
                } },FrameLayout.LayoutParams(panel.dp(34),panel.dp(34),Gravity.CENTER))
                addView(swatch,LinearLayout.LayoutParams(panel.dp(48),panel.dp(48)))
            }
        })
        panel.section("Exact color")
        val hex = EditText(context).apply {
            isSingleLine = true; setText(String.format(java.util.Locale.ROOT,"#%08X",selectedColor()))
            hint = "#RRGGBB or #AARRGGBB"; setTextColor(LuxeTheme.text); setHintTextColor(LuxeTheme.muted)
            background = panel.shape(); setPadding(panel.dp(12),0,panel.dp(12),0); contentDescription = "Exact hex color"
            addTextChangedListener(object : TextWatcher {
                override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) = Unit
                override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {
                    val value = runCatching { Color.parseColor(s.toString().trim()) }.getOrNull()
                    if (value != null) { error = null; choose(value) }
                }
                override fun afterTextChanged(s: Editable?) = Unit
            })
        }
        panel.body.addView(hex,LinearLayout.LayoutParams(-1,panel.dp(48)))
        if (gradient) panel.body.addView(panel.text("Gradient colors are editable and saved with this text layer.").apply { setPadding(0,panel.dp(12),0,0) })
    }
    private fun drawEffects() {
        panel.section("Style presets")
        panel.body.addView(panel.horizontal {
            listOf("Classic","Gold","Neon","Emboss").forEach { name ->
                val sampleLayer = preset(name).copy(text = if (layer.text.any { it.code in 0x0600..0x08ff }) "امید" else "Aa",x = 160f,y = 90f,width = 280f,size = 38f,rotation = 0f,opacity = 1f)
                val sample = StudioDocument(width = 320,height = 180,backgroundColor = LuxeTheme.surface,gradientColor = LuxeTheme.surface,layers = listOf(sampleLayer))
                val selected = when(name) { "Classic" -> candidate.effects.isEmpty(); "Gold" -> "Gold" in candidate.effects; "Neon" -> "Neon" in candidate.effects; else -> "Emboss" in candidate.effects && "Gold" !in candidate.effects }
                val card = panel.column().apply { background = panel.shape(selected = selected); setPadding(panel.dp(4),panel.dp(4),panel.dp(4),panel.dp(4)); isClickable = true; isFocusable = true
                    contentDescription = "Style preset $name"; setOnClickListener { change(preset(name)); draw() } }
                card.addView(ImageView(context).apply { setImageBitmap(renderer.bitmap(sample,160,false)); scaleType = ImageView.ScaleType.CENTER_INSIDE },LinearLayout.LayoutParams(panel.dp(76),panel.dp(58)))
                card.addView(panel.text(name,12).apply { gravity = Gravity.CENTER })
                addView(card,LinearLayout.LayoutParams(panel.dp(84),-2).apply { marginEnd = panel.dp(7) })
            }
        })
        panel.body.addView(panel.button(if (allEffects) "Hide effect library" else "All effects · combine styles") { allEffects = !allEffects; draw() })
        val chips = ChipGroup(context)
        STUDIO_EFFECTS.forEach { effect ->
            chips.addView(Chip(context).apply {
                text = effect; isCheckable = true; isChecked = effect in candidate.effects; setTextColor(LuxeTheme.text)
                chipBackgroundColor = android.content.res.ColorStateList(arrayOf(intArrayOf(android.R.attr.state_checked),intArrayOf()),intArrayOf(LuxeTheme.raised,LuxeTheme.surface))
                setOnCheckedChangeListener { _, selected ->
                    change(candidate.copy(effects = if (selected) (candidate.effects+effect).distinct() else candidate.effects-effect))
                }
            })
        }
        if (allEffects) panel.body.addView(chips)
        panel.slider("Effect strength",candidate.effectIntensity,0f,2f,{ "${(it*100).toInt()}%" }) { change(candidate.copy(effectIntensity = it)) }
        panel.slider("Shadow amount",candidate.shadowStrength,0f,1f,{ "${(it*100).toInt()}%" }) {
            change(candidate.copy(shadowStrength = it,effects = if (candidate.effects.any { e -> e in listOf("Drop shadow","Long shadow","Inner shadow") }) candidate.effects else candidate.effects+"Drop shadow"))
        }
    }
    fun show() = panel.show()
}
