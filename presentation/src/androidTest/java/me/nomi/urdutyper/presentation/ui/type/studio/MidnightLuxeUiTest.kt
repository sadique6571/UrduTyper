package me.nomi.urdutyper.presentation.ui.type.studio

import android.graphics.Bitmap
import android.graphics.Canvas
import android.os.Bundle
import android.os.Environment
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import androidx.navigation.fragment.NavHostFragment
import androidx.test.core.app.ActivityScenario
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import me.nomi.urdutyper.R
import me.nomi.urdutyper.presentation.ui.dashboard.ui.main.DashboardActivity
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import java.io.File

/** Capture actual native app screens, not generated mockups. No sign-in or network action is performed. */
@RunWith(AndroidJUnit4::class)
class MidnightLuxeUiTest {
    @Test fun homePosterAndVideoHaveWorkingControlsAndUsableCanvas() {
        val instrumentation = InstrumentationRegistry.getInstrumentation()
        val scenario = ActivityScenario.launch(DashboardActivity::class.java)
        try {
            instrumentation.waitForIdleSync()
            scenario.onActivity { activity ->
                val root = activity.window.decorView
                assertTrue(descendants(root).filterIsInstance<TextView>().any { it.text.toString() == "UrduTyper" })
                capture(root,"01-home")
                val nav = activity.supportFragmentManager.findFragmentById(R.id.container) as NavHostFragment
                nav.navController.navigate(R.id.type_fragment, Bundle().apply {
                    putString("templateStyle","LUXE_MOONLIT_LAKE"); putString("templateText","خوابوں کو حقیقت بناؤ")
                    putBoolean("templateDarkText",false)
                })
            }
            instrumentation.waitForIdleSync()
            scenario.onActivity { activity ->
                val root = activity.window.decorView
                val canvas = descendants(root).filterIsInstance<StudioCanvas>().single()
                assertTrue("Poster needs a large canvas",canvas.height > 180 * activity.resources.displayMetrics.density)
                assertTrue(descendants(root).filterIsInstance<TextView>().any { it.text.toString() == "Fonts" })
                capture(root,"02-poster")
                val nav = activity.supportFragmentManager.findFragmentById(R.id.container) as NavHostFragment
                nav.navController.navigate(R.id.type_fragment, Bundle().apply {
                    putString("templateStyle","LUXE_MOONLIT_LAKE"); putString("templateText","خوابوں کو حقیقت بناؤ")
                    putBoolean("templateDarkText",false); putBoolean("startVideo",true)
                })
            }
            instrumentation.waitForIdleSync()
            scenario.onActivity { activity ->
                val root = activity.window.decorView
                val canvas = descendants(root).filterIsInstance<StudioCanvas>().last()
                assertTrue("Video needs usable preview space",canvas.height > 120 * activity.resources.displayMetrics.density)
                assertTrue(descendants(root).filterIsInstance<TextView>().any { it.text.toString() == "Voice" })
                capture(root,"03-video")
            }
        } finally { scenario.close() }
    }
    private fun descendants(view: View): List<View> = listOf(view) +
        if (view is ViewGroup) (0 until view.childCount).flatMap { descendants(view.getChildAt(it)) } else emptyList()
    private fun capture(root: View, name: String) {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val directory = File(context.getExternalFilesDir(Environment.DIRECTORY_PICTURES),"ui-screenshots").apply { mkdirs() }
        val bitmap = Bitmap.createBitmap(root.width,root.height,Bitmap.Config.ARGB_8888)
        try { root.draw(Canvas(bitmap)); File(directory,"$name.png").outputStream().use { bitmap.compress(Bitmap.CompressFormat.PNG,100,it) } }
        finally { bitmap.recycle() }
    }
}
