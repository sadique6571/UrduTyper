package me.nomi.urdutyper.presentation.ui.type.studio

import android.app.Dialog
import android.content.Context
import android.content.res.ColorStateList
import android.os.SystemClock
import android.view.WindowManager
import android.widget.LinearLayout
import android.widget.ProgressBar
import android.widget.TextView

/** Determinate, read-only progress: 100 is reserved for a durably completed export. */
class StudioProgressDialog(context: Context, title: String, private val cancel: () -> Unit) {
    private val panel = StudioInspector(context,title,StudioFonts(context))
    private val started = SystemClock.elapsedRealtime()
    private var value = 1
    private val percent = panel.text("1%",32)
    private val stage = panel.text("Preparing animation",15)
    private val elapsed = panel.text("Keep the app open · 0s",12)
    private val fill = ProgressBar(context,null,android.R.attr.progressBarStyleHorizontal).apply {
        max = 100; progress = 1; isIndeterminate = false
        progressTintList = ColorStateList.valueOf(LuxeTheme.gold)
        progressBackgroundTintList = ColorStateList.valueOf(LuxeTheme.border)
        contentDescription = "Video export progress"
    }
    private val stop = panel.button("Cancel export") { requestCancel() }
    private var stopping = false
    private val clock=android.os.Handler(android.os.Looper.getMainLooper())
    private val tick=object: Runnable { override fun run() { elapsed.text="${(SystemClock.elapsedRealtime()-started)/1000}s elapsed · draft saved"; clock.postDelayed(this,1000) } }
    init {
        panel.heightRatio=.55f
        panel.showArtwork(false); panel.tabBar.visibility = android.view.View.GONE
        panel.body.addView(percent); panel.body.addView(stage)
        panel.body.addView(fill,LinearLayout.LayoutParams(-1,panel.dp(24)).apply { topMargin=panel.dp(12); bottomMargin=panel.dp(12) })
        panel.body.addView(elapsed)
        panel.footer.addView(stop,LinearLayout.LayoutParams(-1,panel.dp(48)))
        panel.setCancelable(false); panel.setCanceledOnTouchOutside(false)
    }
    private fun requestCancel() { if (!stopping) { stopping = true; stop.isEnabled = false; stage.text = "Cancelling · cleaning temporary files"; cancel() } }
    fun update(progress: Int, label: String) {
        value = maxOf(value,progress.coerceIn(1,100)); fill.progress = value; percent.text = "$value%"
        if (!stopping) stage.text = label
        elapsed.text = "${(SystemClock.elapsedRealtime()-started)/1000}s elapsed · draft saved"
        fill.contentDescription = "Video export progress $value percent"
    }
    fun show() { panel.show(); clock.post(tick); panel.window?.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON) }
    fun dismiss() { clock.removeCallbacksAndMessages(null); panel.dismiss() }
    val isShowing get() = panel.isShowing
}
