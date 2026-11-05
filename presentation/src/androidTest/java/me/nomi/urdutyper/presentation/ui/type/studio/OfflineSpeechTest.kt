package me.nomi.urdutyper.presentation.ui.type.studio

import android.content.ContentValues
import android.graphics.Bitmap
import android.media.MediaMetadataRetriever
import android.os.Bundle
import android.os.StrictMode
import android.provider.MediaStore
import android.view.View
import android.view.ViewGroup
import android.view.inspector.WindowInspector
import android.widget.EditText
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

@RunWith(AndroidJUnit4::class)
class OfflineSpeechTest {
    @org.junit.Rule @JvmField val timeout = org.junit.rules.Timeout.seconds(180)
    @org.junit.Rule @JvmField val diagnostics = object : org.junit.rules.TestWatcher() {
        override fun starting(description: org.junit.runner.Description) { android.util.Log.i("StudioTests","START ${description.className}.${description.methodName}") }
        override fun finished(description: org.junit.runner.Description) { android.util.Log.i("StudioTests","END ${description.className}.${description.methodName}") }
    }
    private val instrumentation get() = InstrumentationRegistry.getInstrumentation()
    private val context get() = instrumentation.targetContext
    private fun descendants(view: View): List<View> = listOf(view)+if (view is ViewGroup) (0 until view.childCount).flatMap { descendants(view.getChildAt(it)) } else emptyList()
    private fun windows() = WindowInspector.getGlobalWindowViews().filter { it.isAttachedToWindow }.flatMap { descendants(it) }
    private fun tapDescription(value: String) { instrumentation.runOnMainSync { windows().last { it.contentDescription?.toString() == value }.performClick() }; instrumentation.waitForIdleSync() }
    private fun tapText(value: String) {
        instrumentation.runOnMainSync { var view: View = windows().filterIsInstance<TextView>().last { it.text.toString() == value }; while (!view.hasOnClickListeners()) view = view.parent as View; view.performClick() }
        instrumentation.waitForIdleSync()
    }
    private fun fixture(): Pair<File,Long> {
        val file = File(context.cacheDir,"speech-jfk.wav")
        instrumentation.context.assets.open("jfk.wav").use { input -> file.outputStream().use { input.copyTo(it) } }
        val metadata = MediaMetadataRetriever()
        val duration = try { metadata.setDataSource(file.path); metadata.extractMetadata(MediaMetadataRetriever.METADATA_KEY_DURATION)!!.toLong() } finally { metadata.release() }
        return file to duration
    }
    @Test fun nativeEngineRecognizesRealAudioWithoutJavaNetworkAndRendersTimestampedWords() {
        val models = SpeechModels(context)
        val installed = models.importModel(instrumentation.context.assets.open("ggml-tiny.bin"),{ false })
        assertEquals("tiny",installed.id)
        val (file,duration) = fixture()
        val track = StudioAudio(path = file.path,sourceDurationMs = duration,trimEndMs = duration)
        val policy = StrictMode.getThreadPolicy()
        val recognized = try {
            StrictMode.setThreadPolicy(StrictMode.ThreadPolicy.Builder().detectNetwork().penaltyDeathOnNetwork().build())
            OfflineSpeechTask().run(models,installed,track,"en")
        } finally { StrictMode.setThreadPolicy(policy) }
        assertTrue("Real model should recognize several words",recognized.size > 5)
        assertTrue(recognized.joinToString(" ") { it.text }.lowercase().contains("country"))
        assertTrue(recognized.all { it.startMs >= 0 && it.endMs <= duration && it.endMs > it.startMs })
        val layer = StudioLayer(text = "temporary",x = 160f,y = 160f,width = 280f,size = 22f,color = android.graphics.Color.WHITE)
        val original = StudioDocument(width = 320,height = 320,layers = listOf(layer),audio = listOf(track),video = VideoSettings((duration+300).coerceAtMost(60000)))
        val text = recognized.joinToString(" ") { it.text }
        val synced = WordSync.apply(original,layer.id,text,WordSync.transcript(recognized),"words")
        assertTrue(WordSync.validSource(synced,synced.layers.single()))
        val renderer = StudioAnimationRenderer(StudioRenderer(context,StudioFonts(context)))
        val a = renderer.bitmap(synced,320,recognized.first().startMs)
        val b = renderer.bitmap(synced,320,recognized.last().endMs.coerceAtMost(synced.movieDurationMs()-1))
        try { assertFalse("Actual cue frames should differ",a.sameAs(b)) } finally { a.recycle(); b.recycle() }
    }
    @Test fun reviewCanCorrectMissingWordsPreviewCancelApplyUndoAndPersistWithoutAModel() {
        val (file,duration) = fixture()
        val track = StudioAudio(path = file.path,sourceDurationMs = duration,trimEndMs = 3000)
        val layer = StudioLayer(text = "we choose hope",x = 160f,y = 160f,width = 280f,size = 24f)
        val original = StudioDocument(width = 320,height = 320,layers = listOf(layer),audio = listOf(track),video = VideoSettings(3300))
        val doc = WordSync.apply(original,layer.id,layer.text,listOf(ReviewWord("we",0,800,.9f),ReviewWord("choose",1000,1800,.9f),ReviewWord("hope",2000,2800,.9f)),"highlight")
        val store = StudioStore(File(context.filesDir,"studio-drafts")); store.save(doc)
        val scenario = ActivityScenario.launch(DashboardActivity::class.java)
        try {
            scenario.onActivity { activity -> (activity.supportFragmentManager.findFragmentById(R.id.container) as NavHostFragment).navController.navigate(R.id.type_fragment,Bundle().apply { putString("draftId",doc.id); putBoolean("startVideo",true) }) }
            instrumentation.waitForIdleSync(); tapDescription("Word sync"); tapDescription("Open Model tab"); capture("21-offline-models")
            tapDescription("Open Review tab"); capture("22-word-timing-review")
            tapText("Zoom +"); tapText("Zoom +"); tapText("2. choose")
            instrumentation.runOnMainSync {
                val wave=windows().filterIsInstance<WordTimingView>().single()
                assertTrue(wave.zoom>1f); assertEquals(1,wave.selected)
            }
            tapText("Loop word")
            instrumentation.runOnMainSync { assertTrue(windows().filterIsInstance<TextView>().any { it.text.toString()=="Loop word: on" }) }
            tapDescription("Preview quality")
            instrumentation.runOnMainSync {
                val list=windows().filterIsInstance<android.widget.ListView>().last()
                list.performItemClick(list.getChildAt(1),1,list.adapter.getItemId(1))
            }
            instrumentation.waitForIdleSync()
            assertEquals("Smooth",PreviewQuality.selected(context))
            capture("23-word-loop-preview"); tapDescription("Close full-screen preview")
            PreviewQuality.select(context,"Auto")
            instrumentation.runOnMainSync { windows().filterIsInstance<EditText>().single { it.contentDescription == "Word 1 start seconds" }.setText("0.10") }
            tapDescription("Cancel inspector changes")
            scenario.onActivity { activity -> assertEquals(0L,descendants(activity.window.decorView).filterIsInstance<StudioCanvas>().single().document.layers.single().animation.wordCues.first().startMs) }
            tapDescription("Word sync"); tapText("Edit / correct text")
            instrumentation.runOnMainSync {
                val editor = windows().filterIsInstance<EditText>().single { it.contentDescription == "Word sync transcript" }
                assertEquals("we choose hope",editor.text.toString()); editor.setText("we really choose hope")
            }
            tapText("Update word list")
            instrumentation.runOnMainSync {
                assertFalse(windows().last { it.contentDescription == "Apply sync" }.isEnabled)
                windows().filterIsInstance<EditText>().single { it.contentDescription == "Word 2 start seconds" }.setText("0.40")
                windows().filterIsInstance<EditText>().single { it.contentDescription == "Word 2 end seconds" }.setText("0.70")
                assertTrue(windows().last { it.contentDescription == "Apply sync" }.isEnabled)
            }
            tapText("Reveal words"); tapText("Preview")
            instrumentation.runOnMainSync { assertTrue(windows().any { it.contentDescription == "Full-screen artwork preview" }) }
            tapDescription("Close full-screen preview"); tapDescription("Apply sync")
            scenario.onActivity { activity ->
                val all = descendants(activity.window.decorView); val current = all.filterIsInstance<StudioCanvas>().single().document
                assertEquals("we really choose hope",current.layers.single().text)
                assertEquals(4,current.layers.single().animation.wordCues.size)
                assertTrue(current.layers.single().animation.wordCues[1].manual)
                store.save(current); assertEquals(current.layers.single().animation,store.load(doc.id)!!.layers.single().animation)
                all.filterIsInstance<EditorQuickControls>().single().undo.performClick()
                assertEquals("we choose hope",all.filterIsInstance<StudioCanvas>().single().document.layers.single().text)
            }
        } finally { scenario.close() }
    }
    @Test fun exportProgressFillsMonotonicallyAndCancelKeepsDraftSafe() {
        val scenario=ActivityScenario.launch(DashboardActivity::class.java)
        var dialog: StudioProgressDialog?=null; var cancelled=false
        try {
            scenario.onActivity { activity -> dialog=StudioProgressDialog(activity,"Exporting video") { cancelled=true }.also { it.show(); it.update(42,"Rendering video frames"); it.update(20,"Rendering video frames") } }
            instrumentation.waitForIdleSync()
            instrumentation.runOnMainSync {
                val progress=windows().filterIsInstance<android.widget.ProgressBar>().last { it.contentDescription.toString().startsWith("Video export progress") }
                assertEquals(42,progress.progress)
            }
            capture("24-video-export-progress")
            tapText("Cancel export"); assertTrue(cancelled)
            val target=File(context.cacheDir,"cancelled-polish.mp4.pending")
            val tempBefore=context.cacheDir.listFiles().orEmpty().filter { it.name.startsWith("studio-video-") || it.name.startsWith("studio-audio-") }.map { it.name }.toSet()
            try { StudioVideoExporter.export(context,StudioDocument(video=VideoSettings(1000)),target,720,{}, { true }); fail("Should cancel") } catch(_: java.util.concurrent.CancellationException) {}
            assertFalse(target.exists())
            assertEquals(tempBefore,context.cacheDir.listFiles().orEmpty().filter { it.name.startsWith("studio-video-") || it.name.startsWith("studio-audio-") }.map { it.name }.toSet())
            scenario.onActivity { dialog?.dismiss() }
        } finally { scenario.onActivity { dialog?.dismiss() }; scenario.close() }
    }
    private fun capture(name: String) {
        instrumentation.uiAutomation.waitForIdle(150,3000)
        val bitmap = checkNotNull(instrumentation.uiAutomation.takeScreenshot())
        val resolver = context.contentResolver
        val values = ContentValues().apply { put(MediaStore.Images.Media.DISPLAY_NAME,"$name.png"); put(MediaStore.Images.Media.MIME_TYPE,"image/png"); put(MediaStore.Images.Media.RELATIVE_PATH,"Pictures/UrduTyper-ui"); put(MediaStore.Images.Media.IS_PENDING,1) }
        val uri = checkNotNull(resolver.insert(MediaStore.Images.Media.EXTERNAL_CONTENT_URI,values))
        try { checkNotNull(resolver.openOutputStream(uri)).use { bitmap.compress(Bitmap.CompressFormat.PNG,100,it) }; values.clear(); values.put(MediaStore.Images.Media.IS_PENDING,0); resolver.update(uri,values,null,null) } finally { bitmap.recycle() }
    }
}
