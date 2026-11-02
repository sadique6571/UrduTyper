package me.nomi.urdutyper.presentation.ui.type.studio

import android.content.Context
import android.content.res.ColorStateList
import android.graphics.Bitmap
import android.graphics.Typeface
import android.graphics.drawable.GradientDrawable
import android.os.Handler
import android.os.Looper
import android.view.Gravity
import android.view.View
import android.view.WindowManager
import android.widget.*
import com.google.android.material.bottomsheet.BottomSheetBehavior
import com.google.android.material.bottomsheet.BottomSheetDialog
import com.google.android.material.button.MaterialButton
import kotlin.math.roundToInt

/** Shared inspector: live artwork, tabs, scrolling tools and a pinned action footer. */
class StudioInspector(context: Context, title: String, private val fonts: StudioFonts) : BottomSheetDialog(context) {
    val body = column()
    val tabBar = row()
    val footer = row()
    private val root = column()
    private val picture = ImageView(context).apply { scaleType = ImageView.ScaleType.CENTER_CROP; contentDescription = "Live artwork preview" }
    private val handler = Handler(Looper.getMainLooper())
    private val renderer = StudioRenderer(context,fonts)
    private val animator = StudioAnimationRenderer(renderer)
    private var bitmap: Bitmap? = null
    private var pending: StudioDocument? = null
    private var time: Long? = null
    private var large = true
    private var heightChanged: (() -> Unit)? = null
    private val renderTask = Runnable {
        pending?.let { doc ->
            val result = runCatching { if (time == null) renderer.bitmap(doc,360,false) else animator.bitmap(doc,360,time!!) }.getOrNull()
            if (result != null) { val previous = bitmap; bitmap = result; picture.setImageBitmap(result); previous?.recycle() }
        }
    }
    init {
        root.setBackgroundColor(LuxeTheme.background)
        root.contentDescription = "Studio inspector $title"
        root.addView(View(context).apply { background = shape(LuxeTheme.border,20) },LinearLayout.LayoutParams(dp(36),dp(4)).apply { gravity = Gravity.CENTER_HORIZONTAL; topMargin = dp(8) })
        val heading = row().apply { gravity = Gravity.CENTER_VERTICAL; setPadding(dp(16),0,dp(8),0) }
        heading.addView(text(title,20).apply { typeface = Typeface.DEFAULT_BOLD },LinearLayout.LayoutParams(0,dp(48),1f))
        val expand = button("Compact") { large = !large; heightChanged?.invoke() }
        expand.setOnClickListener { large = !large; expand.text = if (large) "Compact" else "Expand"; heightChanged?.invoke() }
        heading.addView(expand); root.addView(heading)
        root.addView(picture,LinearLayout.LayoutParams(-1,dp(106)))
        root.addView(tabBar,LinearLayout.LayoutParams(-1,dp(48)))
        val scroll = ScrollView(context).apply { isFillViewport = false; addView(body); clipToPadding = false }
        body.setPadding(dp(14),dp(10),dp(14),dp(16))
        root.addView(scroll,LinearLayout.LayoutParams(-1,0,1f))
        root.addView(View(context).apply { setBackgroundColor(LuxeTheme.border) },LinearLayout.LayoutParams(-1,dp(1)))
        footer.gravity = Gravity.CENTER_VERTICAL; footer.setPadding(dp(8),dp(6),dp(8),dp(6)); root.addView(footer,LinearLayout.LayoutParams(-1,dp(60)))
        setContentView(root)
        window?.setSoftInputMode(WindowManager.LayoutParams.SOFT_INPUT_ADJUST_RESIZE)
        setOnShowListener {
            val sheet = findViewById<View>(com.google.android.material.R.id.design_bottom_sheet)
            if (sheet != null) {
                heightChanged = {
                    sheet.layoutParams = sheet.layoutParams.apply { height = (context.resources.displayMetrics.heightPixels * if (large) .92f else .68f).toInt() }
                    BottomSheetBehavior.from(sheet).apply { skipCollapsed = true; state = BottomSheetBehavior.STATE_EXPANDED }
                }
                heightChanged?.invoke()
            }
        }
    }
    fun render(document: StudioDocument, timeMs: Long? = null) {
        pending = document; time = timeMs; handler.removeCallbacks(renderTask); handler.postDelayed(renderTask,32)
    }
    fun tabs(names: List<String>, selected: String, click: (String) -> Unit) {
        tabBar.removeAllViews()
        names.forEach { name -> tabBar.addView(button(name, name == selected) { click(name) }.apply {
            contentDescription = "Open $name tab"; textSize = 12f; setPadding(dp(3),0,dp(3),0)
        },LinearLayout.LayoutParams(0,dp(44),1f).apply { marginStart = dp(3); marginEnd = dp(3) }) }
    }
    fun actions(applyTitle: String, apply: () -> Unit, reset: (() -> Unit)? = null, preview: (() -> Unit)? = null) {
        footer.removeAllViews()
        if (reset != null) footer.addView(button("↺") { reset() }.apply { contentDescription = "Reset changes" },LinearLayout.LayoutParams(dp(44),dp(48)))
        footer.addView(button("Cancel") { dismiss() }.apply { contentDescription = "Cancel inspector changes" },LinearLayout.LayoutParams(0,dp(48),1f).apply { marginEnd = dp(5) })
        if (preview != null) footer.addView(button("Preview") { preview() },LinearLayout.LayoutParams(0,dp(48),1f).apply { marginEnd = dp(5) })
        footer.addView(button(applyTitle,true) { apply() }.apply { contentDescription = applyTitle },LinearLayout.LayoutParams(0,dp(48),1.4f))
    }
    fun text(value: String, size: Int = 13) = TextView(context).apply {
        text = value; textSize = size.toFloat(); setTextColor(LuxeTheme.text); gravity = Gravity.CENTER_VERTICAL; typeface = Typeface.DEFAULT
    }
    fun section(value: String) { body.addView(text(value,13).apply { setTextColor(LuxeTheme.muted); setPadding(0,dp(14),0,dp(8)) }) }
    fun button(value: String, selected: Boolean = false, click: () -> Unit) = MaterialButton(context,null,com.google.android.material.R.attr.materialButtonOutlinedStyle).apply {
        text = value; textSize = 12f; isAllCaps = false; letterSpacing = 0f; minWidth = 0; minimumWidth = 0
        insetTop = 0; insetBottom = 0; minHeight = dp(48); setPadding(dp(10),dp(3),dp(10),dp(3)); cornerRadius = dp(12)
        strokeColor = ColorStateList.valueOf(if (selected) LuxeTheme.gold else LuxeTheme.border)
        backgroundTintList = ColorStateList.valueOf(if (selected) LuxeTheme.gold else LuxeTheme.surface)
        setTextColor(if (selected) LuxeTheme.background else LuxeTheme.text); setOnClickListener { click() }
    }
    fun choices(labels: List<String>, selected: String, change: (String) -> Unit) {
        val line = row()
        labels.forEach { value -> line.addView(button(value,value == selected) { change(value) },LinearLayout.LayoutParams(0,dp(44),1f).apply { marginEnd = dp(4) }) }
        body.addView(line)
    }
    fun slider(title: String, initial: Float, min: Float, max: Float, format: (Float) -> String = { String.format(java.util.Locale.ROOT,"%.1f",it) }, change: (Float) -> Unit) {
        val caption = text("$title · ${format(initial)}",13); body.addView(caption)
        body.addView(SeekBar(context).apply {
            this.max = 1000; progress = ((initial.coerceIn(min,max)-min)/(max-min)*1000).toInt()
            progressTintList = ColorStateList.valueOf(LuxeTheme.gold); thumbTintList = ColorStateList.valueOf(LuxeTheme.gold)
            contentDescription = title
            setOnSeekBarChangeListener(object : SeekBar.OnSeekBarChangeListener {
                override fun onStartTrackingTouch(bar: SeekBar?) = Unit
                override fun onStopTrackingTouch(bar: SeekBar?) = Unit
                override fun onProgressChanged(bar: SeekBar?, value: Int, user: Boolean) {
                    if (user) { val number = min+(max-min)*value/1000f; caption.text = "$title · ${format(number)}"; change(number) }
                }
            })
        },LinearLayout.LayoutParams(-1,dp(48)))
    }
    fun toggle(title: String, checked: Boolean, change: (Boolean) -> Unit) {
        body.addView(com.google.android.material.switchmaterial.SwitchMaterial(context).apply {
            text = title; isChecked = checked; setTextColor(LuxeTheme.text); minHeight = dp(48)
            setOnCheckedChangeListener { _, value -> change(value) }
        },LinearLayout.LayoutParams(-1,dp(48)))
    }
    fun horizontal(build: LinearLayout.() -> Unit) = HorizontalScrollView(context).apply { isHorizontalScrollBarEnabled = false; addView(row().apply(build)) }
    fun column() = LinearLayout(context).apply { orientation = LinearLayout.VERTICAL }
    fun row() = LinearLayout(context).apply { orientation = LinearLayout.HORIZONTAL }
    fun shape(color: Int = LuxeTheme.surface, radius: Int = 14, selected: Boolean = false) = GradientDrawable().apply {
        setColor(color); cornerRadius = dp(radius).toFloat(); setStroke(dp(1),if (selected) LuxeTheme.gold else LuxeTheme.border)
    }
    fun dp(value: Int) = (value * context.resources.displayMetrics.density).roundToInt()
    override fun dismiss() { handler.removeCallbacksAndMessages(null); super.dismiss(); picture.setImageDrawable(null); bitmap?.recycle(); bitmap = null }
}
