package me.nomi.urdutyper.presentation.ui.type.studio

import android.content.Context
import android.content.res.ColorStateList
import android.graphics.Bitmap
import android.graphics.Typeface
import android.graphics.drawable.Drawable
import android.graphics.drawable.GradientDrawable
import android.view.Gravity
import android.view.View
import android.widget.*
import com.google.android.material.slider.Slider
import com.google.android.material.switchmaterial.SwitchMaterial
import kotlin.math.roundToInt

/** Actual menu content: accessible icon tiles, artwork cards and compact settings. */
object StudioMenu {
    data class Action(val title: String, val icon: String, val detail: String = "", val enabled: Boolean = true, val selected: Boolean = false, val run: () -> Unit)
    fun dp(context: Context, value: Int) = (context.resources.displayMetrics.density*value).roundToInt()
    fun surface(context: Context, selected: Boolean = false) = GradientDrawable().apply {
        setColor(if (selected) LuxeTheme.raised else LuxeTheme.surface); cornerRadius = dp(context,16).toFloat()
        setStroke(dp(context,1),if (selected) LuxeTheme.gold else LuxeTheme.border)
    }
    fun label(context: Context, value: String, size: Int = 13, muted: Boolean = false) = TextView(context).apply {
        text = value; textSize = size.toFloat(); setTextColor(if (muted) LuxeTheme.muted else LuxeTheme.text)
        gravity = Gravity.CENTER_VERTICAL; includeFontPadding = false
    }
    fun section(host: LinearLayout, title: String, detail: String = "") {
        host.addView(label(host.context,title,14).apply {
            typeface = Typeface.DEFAULT_BOLD; setPadding(0,dp(context,16),0,dp(context,10))
        })
        if (detail.isNotBlank()) host.addView(label(host.context,detail,12,true).apply { setPadding(0,0,0,dp(context,10)) })
    }
    fun grid(host: LinearLayout, actions: List<Action>, columns: Int = 3, compact: Boolean = false) = views(host,actions.map { action ->
        val context = host.context
        LinearLayout(context).apply {
            orientation = LinearLayout.VERTICAL; gravity = Gravity.CENTER; background = surface(context,action.selected)
            setPadding(dp(context,6),dp(context,if (compact) 8 else 12),dp(context,6),dp(context,if (compact) 8 else 12)); minimumHeight = dp(context,if (compact) 64 else 88)
            addView(ImageView(context).apply { setImageDrawable(LuxeIcon(action.icon,if (action.icon == "Delete") 0xffff9d85.toInt() else if (action.selected) LuxeTheme.gold else LuxeTheme.text)); importantForAccessibility = View.IMPORTANT_FOR_ACCESSIBILITY_NO },LinearLayout.LayoutParams(dp(context,24),dp(context,24)).apply { bottomMargin = dp(context,9) })
            addView(label(context,action.title,12).apply { gravity = Gravity.CENTER; maxLines = 2 },LinearLayout.LayoutParams(-1,-2))
            if (action.detail.isNotBlank()) addView(label(context,action.detail,10,true).apply { gravity = Gravity.CENTER; maxLines = 2; setPadding(0,dp(context,4),0,0) })
            contentDescription = action.title; isEnabled = action.enabled; alpha = if (action.enabled) 1f else .35f
            isClickable = true; isFocusable = true; setOnClickListener { if (action.enabled) action.run() }
            accessibilityDelegate = object : View.AccessibilityDelegate() {
                override fun onInitializeAccessibilityNodeInfo(host: View, info: android.view.accessibility.AccessibilityNodeInfo) { super.onInitializeAccessibilityNodeInfo(host,info); info.className = android.widget.Button::class.java.name; info.isSelected = action.selected }
            }
        }
    },columns)
    fun views(host: LinearLayout, items: List<View>, columns: Int = 2) {
        items.chunked(columns).forEach { group ->
            val row = LinearLayout(host.context).apply { orientation = LinearLayout.HORIZONTAL; gravity = Gravity.TOP }
            repeat(columns) { index ->
                row.addView(group.getOrNull(index) ?: View(host.context),LinearLayout.LayoutParams(0,-2,1f).apply {
                    if (index > 0) marginStart = dp(host.context,8)
                })
            }
            host.addView(row,LinearLayout.LayoutParams(-1,-2).apply { bottomMargin = dp(host.context,8) })
        }
    }
    fun artwork(context: Context, title: String, detail: String, bitmap: Bitmap?, selected: Boolean = false, height: Int = 108, click: () -> Unit): View {
        return LinearLayout(context).apply {
            orientation = LinearLayout.VERTICAL; background = surface(context,selected); clipToOutline = true
            addView(ImageView(context).apply { setImageBitmap(bitmap); scaleType = ImageView.ScaleType.CENTER_CROP; contentDescription = "$title preview" },LinearLayout.LayoutParams(-1,dp(context,height)))
            addView(label(context,(if (selected) "✓ " else "")+title,13).apply { typeface = Typeface.DEFAULT_BOLD; maxLines = 2; setPadding(dp(context,10),dp(context,10),dp(context,10),dp(context,5)) })
            if (detail.isNotBlank()) addView(label(context,detail,11,true).apply { maxLines = 2; setPadding(dp(context,10),0,dp(context,10),dp(context,12)) })
            contentDescription = title; isClickable = true; isFocusable = true; setOnClickListener { click() }
        }
    }
    fun ratio(context: Context, title: String, width: Int, height: Int, selected: Boolean, click: () -> Unit): View = LinearLayout(context).apply {
        orientation = LinearLayout.VERTICAL; gravity = Gravity.CENTER; background = surface(context,selected); setPadding(dp(context,6),dp(context,16),dp(context,6),dp(context,12))
        addView(ImageView(context).apply { setImageDrawable(CanvasRatioIcon(width.toFloat()/height)); importantForAccessibility = View.IMPORTANT_FOR_ACCESSIBILITY_NO },LinearLayout.LayoutParams(dp(context,48),dp(context,48)))
        addView(label(context,(if (selected) "✓ " else "")+title,12).apply { gravity = Gravity.CENTER; maxLines = 2; setPadding(0,dp(context,10),0,dp(context,6)) })
        addView(label(context,"$width × $height",10,true).apply { gravity = Gravity.CENTER })
        contentDescription = title; isClickable = true; isFocusable = true; setOnClickListener { click() }
    }
    fun toggle(host: LinearLayout, title: String, detail: String, checked: Boolean, change: (Boolean) -> Unit) {
        val context = host.context
        val row = LinearLayout(context).apply { gravity = Gravity.CENTER_VERTICAL; background = surface(context); setPadding(dp(context,12),dp(context,8),dp(context,8),dp(context,8)) }
        val labels = LinearLayout(context).apply { orientation = LinearLayout.VERTICAL; addView(label(context,title,13)); if (detail.isNotBlank()) addView(label(context,detail,11,true).apply { setPadding(0,dp(context,5),0,0) }) }
        row.addView(labels,LinearLayout.LayoutParams(0,-2,1f))
        row.addView(SwitchMaterial(context).apply { isChecked = checked; minHeight = dp(context,48); contentDescription = title; setOnCheckedChangeListener { _, value -> change(value) } })
        host.addView(row,LinearLayout.LayoutParams(-1,-2).apply { bottomMargin = dp(context,8) })
    }
    fun slider(host: LinearLayout, title: String, initial: Float, min: Float, max: Float, format: (Float) -> String = { "%.1f".format(java.util.Locale.ROOT,it) }, finished: (() -> Unit)? = null, change: (Float) -> Unit) {
        val context = host.context
        val card = LinearLayout(context).apply { orientation = LinearLayout.VERTICAL; background = surface(context); setPadding(dp(context,12),dp(context,12),dp(context,12),0) }
        val header = LinearLayout(context).apply { gravity = Gravity.CENTER_VERTICAL }
        header.addView(label(context,title,12),LinearLayout.LayoutParams(0,-2,1f))
        val number = label(context,format(initial),13).apply { setTextColor(LuxeTheme.gold); typeface = Typeface.DEFAULT_BOLD }
        header.addView(number); card.addView(header)
        card.addView(Slider(context).apply {
            valueFrom = min; valueTo = max; value = initial.coerceIn(min,max); contentDescription = title
            thumbTintList = ColorStateList.valueOf(LuxeTheme.gold); trackActiveTintList = ColorStateList.valueOf(LuxeTheme.gold); trackInactiveTintList = ColorStateList.valueOf(LuxeTheme.border)
            setLabelFormatter { format(it) }
            addOnChangeListener { _, value, user -> if (user) { number.text = format(value); change(value) } }
            addOnSliderTouchListener(object : Slider.OnSliderTouchListener { override fun onStartTrackingTouch(slider: Slider) = Unit; override fun onStopTrackingTouch(slider: Slider) { finished?.invoke() } })
        },LinearLayout.LayoutParams(-1,dp(context,48)))
        host.addView(card,LinearLayout.LayoutParams(-1,-2).apply { bottomMargin = dp(context,8) })
    }
}
