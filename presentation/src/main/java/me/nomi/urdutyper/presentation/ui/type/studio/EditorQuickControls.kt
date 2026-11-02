package me.nomi.urdutyper.presentation.ui.type.studio

import android.content.Context
import android.content.res.ColorStateList
import android.graphics.Typeface
import android.graphics.drawable.GradientDrawable
import android.view.Gravity
import android.view.View
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.TextView
import kotlin.math.roundToInt

/** Permanent bottom actions: history, canvas dimensions and explicit editing modes. */
class EditorQuickControls(context: Context) : LinearLayout(context) {
    var onUndo: () -> Unit = {}
    var onRedo: () -> Unit = {}
    var onSize: () -> Unit = {}
    var onPreview: () -> Unit = {}
    var onMode: (Boolean) -> Unit = {}
    private val labels = mutableMapOf<View, TextView>()
    private val icons = mutableMapOf<View, ImageView>()
    val undo = control("Undo", "Undo", "Undo last edit") { onUndo() }
    val redo = control("Redo", "Redo", "Redo last undone edit") { onRedo() }
    val size = control("Size", "Canvas", "Change canvas size") { onSize() }
    val preview = control("Size", "Preview", "Full-screen preview") { onPreview() }
    val poster = control("Media", "Poster", "Switch to poster editor") { onMode(false) }
    val video = control("Video", "Video", "Switch to video editor") { onMode(true) }
    init {
        orientation = HORIZONTAL
        gravity = Gravity.CENTER_VERTICAL
        setPadding(dp(4),0,dp(4),0)
        listOf(undo,redo,size,preview,poster,video).forEach { addView(it, LayoutParams(0,dp(48),1f)) }
        bind(false,false,false)
    }
    fun bind(canUndo: Boolean, canRedo: Boolean, videoMode: Boolean) {
        undo.isEnabled = canUndo; redo.isEnabled = canRedo
        undo.alpha = if (canUndo) 1f else .35f; redo.alpha = if (canRedo) 1f else .35f
        listOf(poster to !videoMode, video to videoMode).forEach { (view, selected) ->
            view.isSelected = selected
            labels[view]?.setTextColor(if (selected) LuxeTheme.gold else LuxeTheme.muted)
            icons[view]?.imageTintList = ColorStateList.valueOf(if (selected) LuxeTheme.gold else LuxeTheme.muted)
            view.background = GradientDrawable().apply {
                setColor(if (selected) LuxeTheme.raised else android.graphics.Color.TRANSPARENT)
                cornerRadius = dp(10).toFloat()
            }
        }
    }
    private fun control(icon: String, title: String, description: String, action: () -> Unit): View {
        val root = LinearLayout(context).apply {
            orientation = VERTICAL; gravity = Gravity.CENTER; minimumHeight = dp(48)
            isClickable = true; isFocusable = true; contentDescription = description
            setOnClickListener { action() }
            accessibilityDelegate = object : View.AccessibilityDelegate() {
                override fun onInitializeAccessibilityNodeInfo(host: View, info: android.view.accessibility.AccessibilityNodeInfo) {
                    super.onInitializeAccessibilityNodeInfo(host,info); info.className = android.widget.Button::class.java.name
                }
            }
        }
        val image = ImageView(context).apply { setImageDrawable(LuxeIcon(icon)); importantForAccessibility = IMPORTANT_FOR_ACCESSIBILITY_NO }
        val label = TextView(context).apply {
            text = title; textSize = 10f; setTextColor(LuxeTheme.text); typeface = Typeface.DEFAULT
            gravity = Gravity.CENTER; maxLines = 1; importantForAccessibility = IMPORTANT_FOR_ACCESSIBILITY_NO
        }
        root.addView(image, LayoutParams(dp(18),dp(18)))
        root.addView(label, LayoutParams(-1,-2))
        labels[root] = label; icons[root] = image
        return root
    }
    private fun dp(value: Int) = (value * resources.displayMetrics.density).roundToInt()
}
