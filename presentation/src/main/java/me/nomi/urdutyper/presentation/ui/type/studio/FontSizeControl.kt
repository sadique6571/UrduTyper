package me.nomi.urdutyper.presentation.ui.type.studio

import android.app.AlertDialog
import android.content.Context
import android.content.res.ColorStateList
import android.graphics.Color
import android.graphics.Typeface
import android.text.InputType
import android.view.Gravity
import android.widget.EditText
import android.widget.LinearLayout
import android.widget.SeekBar
import android.widget.TextView
import com.google.android.material.button.MaterialButton
import kotlin.math.roundToInt

/** Always-visible control: preview every drag frame, commit one undo state on release. */
class FontSizeControl(context: Context) : LinearLayout(context) {
    var onPreview: (Float) -> Unit = {}
    var onCommit: (Float) -> Unit = {}
    private var tracking = false
    private var currentSize = 76f
    private var editable = false
    private val green = 0xff124b3e.toInt()
    val seekBar = SeekBar(context)
    val decreaseButton = action("−", "Decrease font size") { change(currentSize.roundToInt() - 1) }
    val increaseButton = action("+", "Increase font size") { change(currentSize.roundToInt() + 1) }
    val valueButton = action("76", "Enter exact font size") { exactSize() }

    init {
        orientation = HORIZONTAL
        gravity = Gravity.CENTER_VERTICAL
        setBackgroundColor(Color.WHITE)
        setPadding(dp(8), dp(4), dp(8), dp(4))
        addView(TextView(context).apply {
            text = "Font\nsize"; textSize = 12f; setTextColor(green); typeface = Typeface.DEFAULT_BOLD
            gravity = Gravity.CENTER; contentDescription = "Font size"
        }, LayoutParams(dp(44), dp(48)))
        addView(decreaseButton, LayoutParams(dp(48), dp(48)))
        seekBar.apply {
            max = 592; progress = 68; keyProgressIncrement = 1
            progressTintList = ColorStateList.valueOf(green)
            thumbTintList = ColorStateList.valueOf(green)
            progressBackgroundTintList = ColorStateList.valueOf(0xffdce6df.toInt())
            contentDescription = "Font size slider, 8 to 600"
            setOnSeekBarChangeListener(object : SeekBar.OnSeekBarChangeListener {
                override fun onStartTrackingTouch(bar: SeekBar?) { tracking = true }
                override fun onProgressChanged(bar: SeekBar?, progress: Int, fromUser: Boolean) {
                    if (!fromUser || !editable) return
                    currentSize = (progress + 8).toFloat()
                    valueButton.text = currentSize.roundToInt().toString()
                    onPreview(currentSize)
                    // Keyboard/accessibility changes have no touch-release callback.
                    if (!tracking) onCommit(currentSize)
                }
                override fun onStopTrackingTouch(bar: SeekBar?) { finishEditing() }
            })
        }
        addView(seekBar, LayoutParams(0, dp(48), 1f))
        addView(increaseButton, LayoutParams(dp(48), dp(48)))
        addView(valueButton, LayoutParams(dp(60), dp(48)))
    }

    fun showSelection(size: Float?, canEdit: Boolean) {
        editable = size != null && canEdit
        seekBar.isEnabled = editable
        decreaseButton.isEnabled = editable; increaseButton.isEnabled = editable; valueButton.isEnabled = editable
        alpha = if (editable) 1f else .5f
        if (!tracking) {
            currentSize = size ?: 76f
            seekBar.progress = (currentSize.roundToInt() - 8).coerceIn(0, seekBar.max)
            valueButton.text = size?.roundToInt()?.toString() ?: "—"
        }
    }

    fun finishEditing() {
        if (tracking) {
            tracking = false
            if (editable) onCommit(currentSize)
        }
    }

    private fun change(value: Int) {
        if (!editable) return
        currentSize = value.coerceIn(8, 600).toFloat()
        seekBar.progress = currentSize.roundToInt() - 8
        valueButton.text = currentSize.roundToInt().toString()
        onPreview(currentSize); onCommit(currentSize)
    }

    private fun exactSize() {
        val input = EditText(context).apply {
            inputType = InputType.TYPE_CLASS_NUMBER; setText(currentSize.roundToInt().toString()); selectAll()
        }
        val dialog = AlertDialog.Builder(context).setTitle("Font size · 8–600").setView(input)
            .setNegativeButton("Cancel", null).setPositiveButton("Apply", null).create()
        dialog.setOnShowListener {
            dialog.getButton(AlertDialog.BUTTON_POSITIVE).setOnClickListener {
                val size = input.text.toString().toIntOrNull()
                if (size == null || size !in 8..600) input.error = "Enter a size from 8 to 600"
                else { change(size); dialog.dismiss() }
            }
        }
        dialog.show()
    }

    private fun action(label: String, description: String, click: () -> Unit) =
        MaterialButton(context, null, com.google.android.material.R.attr.materialButtonOutlinedStyle).apply {
            text = label; contentDescription = description; isAllCaps = false; textSize = 15f
            setTextColor(green); insetTop = 0; insetBottom = 0; minimumWidth = 0; minWidth = 0
            setPadding(0, 0, 0, 0); cornerRadius = dp(14); setOnClickListener { click() }
        }
    private fun dp(value: Int) = (value * resources.displayMetrics.density).roundToInt()
}
