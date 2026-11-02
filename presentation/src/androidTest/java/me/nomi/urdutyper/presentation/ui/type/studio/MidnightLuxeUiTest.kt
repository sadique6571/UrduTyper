package me.nomi.urdutyper.presentation.ui.type.studio

import android.content.ContentValues
import android.provider.MediaStore
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
                val quick = descendants(root).filterIsInstance<EditorQuickControls>().single()
                val canvasPosition = IntArray(2); val quickPosition = IntArray(2)
                canvas.getLocationOnScreen(canvasPosition); quick.getLocationOnScreen(quickPosition)
                assertTrue("Quick controls belong below the canvas",quickPosition[1] >= canvasPosition[1] + canvas.height)
                quick.size.performClick()
            }
            instrumentation.waitForIdleSync()
            instrumentation.runOnMainSync {
                val preset = android.view.inspector.WindowInspector.getGlobalWindowViews().flatMap { descendants(it) }
                    .filterIsInstance<TextView>().first { it.text.toString() == "Story / Status · 1080 × 1920" }
                preset.performClick()
            }
            instrumentation.waitForIdleSync()
            scenario.onActivity { activity ->
                val root = activity.window.decorView
                val canvas = descendants(root).filterIsInstance<StudioCanvas>().single()
                val quick = descendants(root).filterIsInstance<EditorQuickControls>().single()
                assertEquals(1920,canvas.document.height)
                assertTrue(quick.undo.isEnabled)
                quick.undo.performClick(); assertEquals(1620,canvas.document.height)
                quick.redo.performClick(); assertEquals(1920,canvas.document.height)
                quick.undo.performClick()
                val id = canvas.document.id
                quick.video.performClick(); assertTrue(quick.video.isSelected); assertEquals(id,canvas.document.id)
                quick.poster.performClick(); assertTrue(quick.poster.isSelected); assertEquals(id,canvas.document.id)
                quick.preview.performClick()
            }
            instrumentation.waitForIdleSync()
            instrumentation.runOnMainSync {
                val views = android.view.inspector.WindowInspector.getGlobalWindowViews().flatMap { descendants(it) }
                val preview = views.filterIsInstance<StudioCanvas>().first { it.contentDescription == "Full-screen artwork preview" }
                assertNull(preview.selectedId); assertFalse(preview.showGrid)
                views.first { it.contentDescription == "Close full-screen preview" }.performClick()
            }
            instrumentation.waitForIdleSync()
            scenario.onActivity { activity ->
                val root = activity.window.decorView
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
                assertTrue("Video needs usable preview space",canvas.height > 220 * activity.resources.displayMetrics.density)
                assertTrue(descendants(root).filterIsInstance<TextView>().any { it.text.toString() == "Voice" })
                capture(root,"03-video")
            }
        } finally { scenario.close() }
    }
    private fun descendants(view: View): List<View> = listOf(view) +
        if (view is ViewGroup) (0 until view.childCount).flatMap { descendants(view.getChildAt(it)) } else emptyList()
    private fun capture(root: View, name: String) {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val bitmap = Bitmap.createBitmap(root.width,root.height,Bitmap.Config.ARGB_8888)
        var uri: android.net.Uri? = null
        try {
            root.draw(Canvas(bitmap))
            val values = ContentValues().apply {
                put(MediaStore.Images.Media.DISPLAY_NAME,"$name.png")
                put(MediaStore.Images.Media.MIME_TYPE,"image/png")
                put(MediaStore.Images.Media.RELATIVE_PATH,"Pictures/UrduTyper-ui")
                put(MediaStore.Images.Media.IS_PENDING,1)
            }
            uri = checkNotNull(context.contentResolver.insert(MediaStore.Images.Media.EXTERNAL_CONTENT_URI,values))
            checkNotNull(context.contentResolver.openOutputStream(uri)).use { bitmap.compress(Bitmap.CompressFormat.PNG,100,it) }
            values.clear(); values.put(MediaStore.Images.Media.IS_PENDING,0)
            context.contentResolver.update(uri,values,null,null)
        } catch (error: Throwable) { uri?.let { context.contentResolver.delete(it,null,null) }; throw error }
        finally { bitmap.recycle() }
    }
}
