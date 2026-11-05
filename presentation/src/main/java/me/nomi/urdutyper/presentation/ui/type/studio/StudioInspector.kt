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
    private val frames = PreviewFrames(context)
    private var bitmap: Bitmap? = null
    private var pending: StudioDocument? = null
    private var time: Long? = null
    private var large = true
    private val preferences = context.getSharedPreferences("studio-inspectors",0)
    private var artworkHidden = preferences.getBoolean("artwork-hidden",true)
    private var artworkAllowed = true
    private lateinit var artworkToggle: MaterialButton
    private var heightChanged: (() -> Unit)? = null
    private val renderTask = Runnable {
        pending?.let { doc -> frames.request(doc,time,360) { result ->
            val previous=bitmap; bitmap=result; picture.setImageBitmap(result); previous?.recycle()
        } }
    }
    init {
        root.setBackgroundColor(LuxeTheme.background)
        root.contentDescription = "Studio inspector $title"
        root.addView(View(context).apply { background = shape(LuxeTheme.border,20) },LinearLayout.LayoutParams(dp(36),dp(4)).apply { gravity = Gravity.CENTER_HORIZONTAL; topMargin = dp(8) })
        val heading = row().apply { gravity = Gravity.CENTER_VERTICAL; setPadding(dp(16),0,dp(8),0) }
        heading.addView(text(title,20).apply { typeface = Typeface.DEFAULT_BOLD },LinearLayout.LayoutParams(0,dp(48),1f))
        val expand = button("Compact") { large = !large; heightChanged?.invoke() }
        expand.setOnClickListener { large = !large; expand.text = if (large) "Compact" else "Expand"; heightChanged?.invoke() }
        artworkToggle = button(if (artworkHidden) "Show canvas" else "Hide canvas") {
            artworkHidden = !artworkHidden; preferences.edit().putBoolean("artwork-hidden",artworkHidden).apply()
            picture.visibility = if (artworkHidden) View.GONE else View.VISIBLE
            (heading.getChildAt(1) as android.widget.TextView).text = if (artworkHidden) "Show canvas" else "Hide canvas"
        }.apply { contentDescription = "Toggle inspector canvas preview"; textSize = 11f }
        heading.addView(artworkToggle)
        heading.addView(expand); root.addView(heading)
        root.addView(picture,LinearLayout.LayoutParams(-1,dp(82)))
        picture.visibility = if (artworkHidden) View.GONE else View.VISIBLE
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
    fun revealArtwork() { if(artworkAllowed) { artworkHidden=false; picture.visibility=View.VISIBLE; artworkToggle.text="Hide canvas" } }
    fun showArtwork(visible: Boolean) { artworkAllowed=visible; artworkToggle.visibility=if(visible) View.VISIBLE else View.GONE; picture.visibility = if (visible && !artworkHidden) View.VISIBLE else View.GONE }
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
        StudioMenu.slider(body,title,initial,min,max,format,change = change)
    }
    fun toggle(title: String, checked: Boolean, change: (Boolean) -> Unit) {
        StudioMenu.toggle(body,title,"",checked,change)
    }
    fun horizontal(build: LinearLayout.() -> Unit) = HorizontalScrollView(context).apply { isHorizontalScrollBarEnabled = false; addView(row().apply(build)) }
    fun column() = LinearLayout(context).apply { orientation = LinearLayout.VERTICAL }
    fun row() = LinearLayout(context).apply { orientation = LinearLayout.HORIZONTAL }
    fun shape(color: Int = LuxeTheme.surface, radius: Int = 14, selected: Boolean = false) = GradientDrawable().apply {
        setColor(color); cornerRadius = dp(radius).toFloat(); setStroke(dp(1),if (selected) LuxeTheme.gold else LuxeTheme.border)
    }
    fun dp(value: Int) = (value * context.resources.displayMetrics.density).roundToInt()
    override fun dismiss() { frames.close(); handler.removeCallbacksAndMessages(null); super.dismiss(); picture.setImageDrawable(null); bitmap?.recycle(); bitmap = null }
}
