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
    @org.junit.Rule @JvmField val timeout = org.junit.rules.Timeout.seconds(120)
    @org.junit.Rule @JvmField val diagnostics = object : org.junit.rules.TestWatcher() {
        override fun starting(description: org.junit.runner.Description) { android.util.Log.i("StudioTests","START ${description.className}.${description.methodName}") }
        override fun finished(description: org.junit.runner.Description) { android.util.Log.i("StudioTests","END ${description.className}.${description.methodName}") }
    }

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
            instrumentation.runOnMainSync {
                assertFalse("No overflow menu should hide tools",windows().any { it.contentDescription?.toString() == "More" })
                listOf("Duration","Scenes","Captions","Beats","Templates","Saved","Canvas","Project").forEach { name ->
                    assertTrue("Direct tool icon $name is missing",windows().any { it.contentDescription?.toString() == name })
                }
            }
            tapDescription("Duration"); tapDescription("Set scene duration 15 seconds"); capture("07-video-duration")
            tapDescription("Apply scene")
            scenario.onActivity { activity ->
                val all = descendants(activity.window.decorView)
                val doc = all.filterIsInstance<StudioCanvas>().single().document
                assertEquals(15000L,doc.video.durationMs)
                assertEquals(15000L,doc.layers.single().animation.endMs)
                all.filterIsInstance<EditorQuickControls>().single().undo.performClick()
            }
            tapDescription("Layers"); capture("08-layer-menu")
            instrumentation.runOnMainSync {
                listOf("Hide","Lock","Duplicate","Forward","Back","Center","Delete layer").forEach { title ->
                    assertTrue("Layer action $title must have its own icon tile",windows().any { it.contentDescription == title })
                }
            }
            tapDescription("Lock")
            scenario.onActivity { activity -> assertTrue(descendants(activity.window.decorView).filterIsInstance<StudioCanvas>().single().document.layers.single().locked) }
            tapDescription("Unlock"); tapText("Done")
            tapDescription("Canvas"); tapDescription("Canvas size"); capture("09-canvas-menu"); tapText("Done")
            tapDescription("Templates"); capture("10-template-menu"); tapText("Done")
            tapDescription("Scenes"); capture("11-scenes-menu"); tapText("Done")
            tapDescription("Voice"); capture("12-audio-menu"); tapDescription("Cancel inspector changes")
            tapDescription("Canvas"); capture("13-canvas-options"); tapText("Done")
            tapDescription("Project"); capture("14-project-menu"); tapText("Done")
            tapDescription("Beats"); tapDescription("Cancel inspector changes")
            tapDescription("Project"); tapText("Rename project"); tapDescription("Cancel inspector changes")
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
            scenario.onActivity { activity -> descendants(activity.window.decorView).filterIsInstance<EditorQuickControls>().single().poster.performClick() }
            instrumentation.waitForIdleSync()
            tapDescription("Media")
            tapDescription("Background"); capture("15-background-menu"); tapDescription("Cancel inspector changes")
            tapDescription("Brand"); capture("16-brand-menu"); tapText("Done")
            tapText("Export"); capture("17-image-export"); tapDescription("Cancel inspector changes")
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
