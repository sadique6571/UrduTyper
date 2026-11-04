package me.nomi.urdutyper.presentation.ui.type.studio

import android.app.Application
import com.google.gson.Gson
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config
import java.io.ByteArrayInputStream

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [28], application = Application::class)
class WordSyncPersistenceTest {
    @get:Rule val folder = TemporaryFolder()
    @Test fun savedCuesAndManualEditsSurviveReloadAndOldDraftsGetEmptyCues() {
        val track = StudioAudio(path = "voice.wav",sourceDurationMs = 3000,trimEndMs = 3000)
        val original = StudioDocument(layers = listOf(StudioLayer(text = "ہم تم")),audio = listOf(track))
        val aligned = WordSync.apply(original,original.layers[0].id,"ہم تم",listOf(ReviewWord("ہم",100,700,.9f),ReviewWord("تم",900,1700,.4f,manual = true)),"highlight")
        val store = StudioStore(folder.newFolder()); store.save(aligned)
        assertEquals(aligned.layers.single().animation,store.load(aligned.id)!!.layers.single().animation)
        assertTrue(Gson().fromJson("{}",LayerAnimation::class.java).wordCues.isEmpty())
    }
    @Test fun unsupportedModelImportAndCancellationNeverInstallOrLeavePartialFiles() {
        val context: Application = RuntimeEnvironment.getApplication()
        val models = SpeechModels(context)
        assertThrows(IllegalStateException::class.java) { models.importModel(ByteArrayInputStream(ByteArray(2048)),{ false }) }
        assertThrows(java.util.concurrent.CancellationException::class.java) { models.importModel(ByteArrayInputStream(ByteArray(2048)),{ true }) }
        assertTrue(java.io.File(context.filesDir,"speech-models").listFiles().orEmpty().none { it.extension == "part" })
        assertTrue(SpeechModels.all.none { it.id.endsWith(".en") })
    }
}
