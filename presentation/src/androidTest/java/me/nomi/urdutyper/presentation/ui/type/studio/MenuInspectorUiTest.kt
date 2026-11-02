package me.nomi.urdutyper.presentation.ui.type.studio

import android.content.ContentValues
import android.graphics.Bitmap
import android.os.Bundle
import android.provider.MediaStore
import android.view.View
import android.view.ViewGroup
import android.view.inspector.WindowInspector
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

@RunWith(AndroidJUnit4::class)
class MenuInspectorUiTest {
    private val instrumentation get() = InstrumentationRegistry.getInstrumentation()
    private fun descendants(view: View): List<View> = listOf(view)+if (view is ViewGroup) (0 until view.childCount).flatMap { descendants(view.getChildAt(it)) } else emptyList()
    private fun windows() = WindowInspector.getGlobalWindowViews().filter { it.isAttachedToWindow }.flatMap { descendants(it) }
    private fun tapDescription(value: String) {
        instrumentation.runOnMainSync { windows().first { it.contentDescription?.toString() == value }.performClick() }
        instrumentation.waitForIdleSync()
    }
    private fun tapText(value: String) {
        instrumentation.runOnMainSync {
            var view: View = windows().filterIsInstance<TextView>().last { it.text.toString() == value }
            while (!view.hasOnClickListeners()) view = view.parent as View
            view.performClick()
        }
        instrumentation.waitForIdleSync()
    }
    @Test fun inspectorsPreviewCancelApplyAndNavigateWithoutChangingDraftUnexpectedly() {
        val scenario = ActivityScenario.launch(DashboardActivity::class.java)
        try {
            instrumentation.waitForIdleSync()
            scenario.onActivity { activity ->
                val nav = activity.supportFragmentManager.findFragmentById(R.id.container) as NavHostFragment
                nav.navController.navigate(R.id.type_fragment,Bundle().apply {
                    putString("templateStyle","LUXE_MOONLIT_LAKE"); putString("templateText","خوابوں کو\nحقیقت بناؤ"); putBoolean("templateDarkText",false)
                })
            }
            instrumentation.waitForIdleSync()
            tapText("Fonts"); capture("04-font-library")
            tapDescription("Choose font AlQalam Makki")
            scenario.onActivity { activity -> assertEquals("makki",descendants(activity.window.decorView).filterIsInstance<StudioCanvas>().single().document.layers.single().fontId) }
            tapDescription("Cancel inspector changes")
            scenario.onActivity { activity -> assertEquals("nastaliq",descendants(activity.window.decorView).filterIsInstance<StudioCanvas>().single().document.layers.single().fontId) }
            tapText("Fonts"); tapDescription("Choose font AlQalam Makki"); tapDescription("Apply font")
            scenario.onActivity { activity ->
                val all = descendants(activity.window.decorView)
                assertEquals("makki",all.filterIsInstance<StudioCanvas>().single().document.layers.single().fontId)
                all.filterIsInstance<EditorQuickControls>().single().undo.performClick()
                assertEquals("nastaliq",all.filterIsInstance<StudioCanvas>().single().document.layers.single().fontId)
            }
            tapText("Effects"); tapDescription("Style preset Gold"); capture("05-text-styling")
            tapDescription("Cancel inspector changes")
            scenario.onActivity { activity -> assertFalse("Cancel must discard staged effects",descendants(activity.window.decorView).filterIsInstance<StudioCanvas>().single().document.layers.single().effects.contains("Gold")) }
            tapText("Effects"); tapDescription("Style preset Gold"); tapDescription("Apply style")
            scenario.onActivity { activity ->
                val all = descendants(activity.window.decorView)
                assertTrue(all.filterIsInstance<StudioCanvas>().single().document.layers.single().effects.contains("Gold"))
                all.filterIsInstance<EditorQuickControls>().single().undo.performClick()
                all.filterIsInstance<EditorQuickControls>().single().video.performClick()
            }
            instrumentation.waitForIdleSync()
            tapText("Animate"); tapDescription("Open Scene tab")
            repeat(6) { tapDescription("Decrease Scene duration") }
            tapDescription("Open Animate tab"); tapDescription("Animation preset typing"); capture("06-video-studio")
            tapDescription("Open Audio tab")
            instrumentation.runOnMainSync { assertTrue("Audio navigation must not depend on animation validation",windows().filterIsInstance<TextView>().any { it.text.toString() == "Record" }) }
            tapDescription("Open Export tab"); tapText("GIF"); tapText("Preview")
            instrumentation.runOnMainSync { assertTrue(windows().any { it.contentDescription == "Full-screen artwork preview" }) }
            tapDescription("Close full-screen preview"); tapDescription("Cancel inspector changes")
            scenario.onActivity { activity ->
                val doc = descendants(activity.window.decorView).filterIsInstance<StudioCanvas>().single().document
                assertEquals("Preview/Cancel must not apply a scene edit",8000L,doc.video.durationMs)
                assertEquals("none",doc.layers.single().animation.effect)
            }
        } finally { scenario.close() }
    }
    private fun capture(name: String) {
        instrumentation.uiAutomation.waitForIdle(150,3000)
        val bitmap = checkNotNull(instrumentation.uiAutomation.takeScreenshot())
        val resolver = instrumentation.targetContext.contentResolver
        val values = ContentValues().apply {
            put(MediaStore.Images.Media.DISPLAY_NAME,"$name.png"); put(MediaStore.Images.Media.MIME_TYPE,"image/png")
            put(MediaStore.Images.Media.RELATIVE_PATH,"Pictures/UrduTyper-ui"); put(MediaStore.Images.Media.IS_PENDING,1)
        }
        val uri = checkNotNull(resolver.insert(MediaStore.Images.Media.EXTERNAL_CONTENT_URI,values))
        try {
            checkNotNull(resolver.openOutputStream(uri)).use { bitmap.compress(Bitmap.CompressFormat.PNG,100,it) }
            values.clear(); values.put(MediaStore.Images.Media.IS_PENDING,0); resolver.update(uri,values,null,null)
        } finally { bitmap.recycle() }
    }
}
