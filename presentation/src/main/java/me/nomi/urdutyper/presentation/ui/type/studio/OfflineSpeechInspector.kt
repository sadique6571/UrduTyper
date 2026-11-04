package me.nomi.urdutyper.presentation.ui.type.studio

import android.content.Context
import android.graphics.Color
import android.os.Handler
import android.os.Looper
import android.text.Editable
import android.text.TextWatcher
import android.view.Gravity
import android.view.View
import android.widget.*
import kotlinx.coroutines.*
import java.util.concurrent.atomic.AtomicBoolean

/** Audio stays on-device. Model assets and transcript edits have separate lifetimes. */
class OfflineSpeechInspector(
    private val context: Context, fonts: StudioFonts, private val original: StudioDocument, private val selectedId: String?,
    private val preview: (StudioDocument,Long) -> Unit, private val apply: (StudioDocument) -> Unit,
    private val fullPreview: (StudioDocument,Long,Boolean) -> Unit, private val importModel: () -> Unit,
    private val addVoice: () -> Unit, private val closed: () -> Unit
) {
    val panel = StudioInspector(context,"Offline word sync",fonts)
    private val models = SpeechModels(context)
    private var model = models.selected()
    private val layer = original.layers.firstOrNull { it.id == selectedId && it.kind == "text" && it.visible && !it.locked }
    private val voice = original.audio.firstOrNull { it.role == "voice" }
    private val scope = CoroutineScope(SupervisorJob()+Dispatchers.Main.immediate)
    private val handler = Handler(Looper.getMainLooper())
    private var job: Job? = null; private var task: OfflineSpeechTask? = null
    private val modelCancelled = AtomicBoolean(false)
    private var busy = false; private var transcribe = false; private var language = "ur"
    private var status = "Model is used locally. Audio is not uploaded."
    private var statusText: TextView? = null; private var errorText: TextView? = null
    private var text = layer?.text.orEmpty()
    private var words = if (layer != null && WordSync.validSource(original,layer)) WordSync.review(text,layer.animation.wordCues) else WordSync.match(text,emptyList())
    private var mode = layer?.animation?.effect?.takeIf { it in listOf("words","typing","highlight") } ?: "words"
    private var page = 0; private var editText = false; private var clear = false
    private var tab = if (layer?.animation?.wordCues?.isNotEmpty() == true) "Review" else if (models.installed(model)) "Analyse" else "Model"
    init {
        panel.setOnDismissListener { task?.cancel(); modelCancelled.set(true); scope.cancel(); handler.removeCallbacksAndMessages(null); closed() }
        if (layer?.animation?.wordCues?.isNotEmpty() == true && !WordSync.validSource(original,layer)) status = "Voice changed: saved word sync is paused. Re-analyse or clear it."
        draw(); panel.render(original)
    }
    private fun status(value: String) { handler.post { if (scope.isActive) { status = value; statusText?.text = value } } }
    private fun candidate(): StudioDocument? = runCatching {
        val id = layer?.id ?: error("Select an unlocked visible text layer")
        if (clear) original.copy(layers = original.layers.map { if (it.id == id) it.copy(animation = it.animation.copy(wordCues = emptyList(),voiceSignature = "")) else it })
        else WordSync.apply(original,id,text,words,mode)
    }.getOrNull()
    private fun validation(): String? {
        if (clear) return null
        val selectedLayer = layer ?: return "Select an unlocked visible text layer first"
        val track = voice ?: return "Import or record a voice track first"
        return WordSync.validationError(text,words,track) ?: runCatching { WordSync.apply(original,selectedLayer.id,text,words,mode); null }.getOrElse { it.message ?: "Check timing" }
    }
    private fun refreshPreview(time: Long? = null) {
        val doc = candidate()
        if (doc != null) { val at = (time ?: words.firstOrNull()?.startMs ?: 0).coerceIn(0,doc.movieDurationMs()-1); preview(doc,at); panel.render(doc,at) }
        val message = validation(); errorText?.text = message ?: if (clear) "Apply to remove saved word sync" else "Ready to preview / apply · review uncertain words"
        if (panel.footer.childCount > 0) panel.footer.getChildAt(panel.footer.childCount-1).isEnabled = !busy && message == null
    }
    private fun draw() {
        panel.tabs(listOf("Model","Analyse","Review"),tab) { if (!busy) { tab = it; draw() } }
        panel.body.keepScreenOn = busy
        panel.body.removeAllViews(); errorText = null
        statusText = panel.text(status,12).apply { setTextColor(LuxeTheme.muted) }; panel.body.addView(statusText)
        if (busy) {
            panel.body.addView(ProgressBar(context),LinearLayout.LayoutParams(panel.dp(48),panel.dp(48)).apply { gravity = Gravity.CENTER_HORIZONTAL; topMargin = panel.dp(20) })
            panel.body.addView(panel.text("Keep the app open. Closing this panel cancels the operation. Larger models need more memory and time.",13))
            panel.body.addView(panel.button("Stop processing") { task?.cancel(); modelCancelled.set(true); job?.cancel() })
        } else when(tab) { "Model" -> modelMenu(); "Analyse" -> analyseMenu(); else -> reviewMenu() }
        panel.actions(if (clear) "Clear sync" else "Apply sync", {
            val message = validation()
            if (message != null) { status(message); return@actions }
            candidate()?.let { apply(it); panel.dismiss() }
        },preview = {
            val doc = candidate()
            if (doc == null) status(validation() ?: "Finish reviewing word timings first")
            else fullPreview(doc,(words.firstOrNull()?.startMs ?: 0).coerceAtLeast(0),false)
        })
        panel.footer.getChildAt(panel.footer.childCount-1).isEnabled = !busy && validation() == null
    }
    private fun modelMenu() {
        panel.section("Multilingual model · one-time setup")
        StudioMenu.grid(panel.body,SpeechModels.all.map { option -> StudioMenu.Action(option.title,"Voice",option.size,selected = option.id == model.id) { model = option; models.select(option); draw() } })
        panel.body.addView(panel.text(if (models.installed(model)) "${model.title} installed · verified again before analysis" else "${model.title} not installed",13))
        panel.body.addView(panel.text("Download uses internet/data once. Recognition then works without internet or an API key. Small uses more RAM; try Tiny/Base first. Imported files must be original multilingual GGML models.",12).apply { setTextColor(LuxeTheme.muted) })
        StudioMenu.grid(panel.body,listOf(
            StudioMenu.Action(if (models.installed(model)) "Download again" else "Download ${model.title}","Saved",model.size) { download() },
            StudioMenu.Action("Import model","Media","Choose .bin file") { panel.dismiss(); importModel() },
            StudioMenu.Action("Remove model","Delete",enabled = models.installed(model)) { runCatching { models.delete(model) }.onFailure { status(it.message.orEmpty()) }; draw() }
        ))
        panel.body.addView(panel.button("Continue to analysis",true) { tab = "Analyse"; draw() })
    }
    private fun download() {
        busy = true; clear = false; modelCancelled.set(false); status = "Downloading ${model.title} · ${model.size}"; draw()
        job = scope.launch {
            try {
                var last = 0L
                withContext(Dispatchers.IO) { models.download(model,{ modelCancelled.get() || !scope.isActive }) { copied,total ->
                    val now = android.os.SystemClock.elapsedRealtime()
                    if (now-last > 200) { last = now; status("Downloaded ${copied/1048576} MiB${if (total > 0) " / ${total/1048576} MiB" else ""}") }
                } }
                status = "${model.title} installed and checksum verified"; tab = "Analyse"
            } catch (error: CancellationException) { status = "Download cancelled; partial file removed" }
            catch (error: Throwable) { status = error.message ?: "Model download failed" }
            finally { busy = false; if (scope.isActive) draw() }
        }
    }
    private fun analyseMenu() {
        panel.section("Voice → word timestamps")
        if (layer == null) panel.body.addView(panel.text("Select an unlocked visible text layer in the editor first. New transcription replaces it only after Apply.",13))
        panel.body.addView(panel.text("${model.title} · ${if (models.installed(model)) "installed" else "download it in Model"}",13))
        if (voice != null) panel.body.addView(panel.text("${voice.name} · ${"%.1f".format(java.util.Locale.ROOT,voice.durationMs()/1000f)} seconds selected",13))
        else panel.body.addView(panel.button("Add voice / recording") { panel.dismiss(); addVoice() })
        panel.section("Language")
        panel.choices(listOf("Urdu","English","Auto"),when(language) { "ur" -> "Urdu"; "en" -> "English"; else -> "Auto" }) { language = when(it) { "Urdu" -> "ur"; "English" -> "en"; else -> "auto" }; draw() }
        panel.section("What to do")
        panel.choices(listOf("Align written text","Voice → new text"),if (transcribe) "Voice → new text" else "Align written text") { transcribe = it == "Voice → new text"; draw() }
        panel.body.addView(panel.text(if (transcribe) "Recognizes a transcript and timing. It replaces the selected text only after Apply." else "Keeps your written Urdu text and matches it to recognized words. Missing or uncertain words need manual review.",12))
        if (layer != null) panel.body.addView(panel.text(layer.text.take(180),18).apply { maxLines = 4 })
        panel.body.addView(panel.button("Analyse offline",true) { analyse() }.apply { isEnabled = layer != null && voice != null && models.installed(model) })
        panel.body.addView(panel.text("Word timestamps are approximate. Clear speech without background music gives better results. Review the text and timing before applying.",12).apply { setTextColor(LuxeTheme.muted) })
    }
    private fun analyse() {
        val track = voice ?: return; if (layer == null) return
        val operation = OfflineSpeechTask(); task = operation; busy = true; clear = false; status = "Preparing offline analysis"; draw()
        job = scope.launch {
            val poll = launch { while (isActive) { status = "${operation.stage} · ${operation.progress()}%"; statusText?.text = status; delay(400) } }
            try {
                val result = withContext(Dispatchers.IO) { operation.run(models,model,track,language) }
                val global = result.map { it.copy(startMs = it.startMs+track.startMs,endMs = it.endMs+track.startMs) }
                poll.cancel(); status = "Matching word timestamps"; statusText?.text = status
                text = if (transcribe) global.joinToString(" ") { it.text } else layer.text
                words = withContext(Dispatchers.Default) { if (transcribe) WordSync.transcript(global) else WordSync.match(text,global) }
                page = 0; tab = "Review"; status = "Analysis complete · review ${words.count { it.startMs == null || it.estimated || it.confidence < .5f }} missing / uncertain words"
            } catch (error: CancellationException) { status = "Analysis cancelled" }
            catch (error: Throwable) { status = error.message ?: "Offline analysis failed" }
            finally { poll.cancel(); task = null; busy = false; if (scope.isActive) { draw(); refreshPreview() } }
        }
    }
    private fun reviewMenu() {
        StudioMenu.section(panel.body,"Review before applying","Voice timestamps are approximate")
        panel.choices(listOf("Reveal words","Highlight","Typing"),when(mode) { "highlight" -> "Highlight"; "typing" -> "Typing"; else -> "Reveal words" }) { mode = when(it) { "Highlight" -> "highlight"; "Typing" -> "typing"; else -> "words" }; refreshPreview(); draw() }
        panel.body.addView(panel.button(if (editText) "Hide text editor" else "Edit / correct text") { editText = !editText; draw() })
        if (editText) {
            val editor = EditText(context).apply { setText(this@OfflineSpeechInspector.text); minLines = 2; maxLines = 5; setTextColor(LuxeTheme.text); contentDescription = "Word sync transcript"; inputType = android.text.InputType.TYPE_CLASS_TEXT or android.text.InputType.TYPE_TEXT_FLAG_MULTI_LINE; filters = arrayOf(android.text.InputFilter.LengthFilter(2000)) }
            panel.body.addView(editor)
            panel.body.addView(panel.button("Update word list") {
                val value = editor.text.toString()
                val old = words.filter { it.startMs != null && it.endMs != null }.map { SpeechWord(it.text,it.startMs!!,it.endMs!!,it.confidence,it.estimated) }
                runCatching { WordSync.match(value,old) }.onSuccess { text = value; words = it; page = 0; clear = false; draw(); refreshPreview() }.onFailure { status(it.message.orEmpty()) }
            })
        }
        panel.body.addView(panel.text("Times are seconds on the complete video timeline. Blank times need correction. Listen starts near that word.",12).apply { setTextColor(LuxeTheme.muted) })
        val pages = ((words.size+2)/3).coerceAtLeast(1); page = page.coerceIn(0,pages-1)
        val navigation = panel.row()
        navigation.addView(panel.button("←") { page--; draw() }.apply { contentDescription = "Previous timing page"; isEnabled = page > 0 })
        navigation.addView(panel.text("${page+1} / $pages",13).apply { gravity = Gravity.CENTER },LinearLayout.LayoutParams(0,panel.dp(48),1f))
        navigation.addView(panel.button("→") { page++; draw() }.apply { contentDescription = "Next timing page"; isEnabled = page < pages-1 }); panel.body.addView(navigation)
        words.indices.drop(page*3).take(3).forEach { index ->
            val word = words[index]; val card = panel.column().apply { background = panel.shape(); setPadding(panel.dp(10),panel.dp(8),panel.dp(10),panel.dp(8)) }
            card.addView(panel.text("${index+1}. ${word.text}",18))
            val quality = panel.text(if (word.startMs == null) "Missing timestamp" else if (word.manual) "Manually adjusted timing" else if (word.estimated || word.confidence < .5f) "Approximate / low confidence · check it" else "Recognized · ${(word.confidence*100).toInt()}% model confidence",11).apply { setTextColor(LuxeTheme.muted) }
            card.addView(quality)
            val line = panel.row()
            fun timeInput(value: Long?, start: Boolean): EditText = EditText(context).apply {
                setText(value?.let { "%.2f".format(java.util.Locale.ROOT,it/1000f) }.orEmpty()); hint = if (start) "Start s" else "End s"; setTextColor(LuxeTheme.text); setHintTextColor(LuxeTheme.muted)
                inputType = android.text.InputType.TYPE_CLASS_NUMBER or android.text.InputType.TYPE_NUMBER_FLAG_DECIMAL; filters = arrayOf(android.text.InputFilter.LengthFilter(8)); isSingleLine = true; contentDescription = "Word ${index+1} ${if (start) "start" else "end"} seconds"
                addTextChangedListener(object : TextWatcher {
                    override fun beforeTextChanged(s: CharSequence?, at: Int, count: Int, after: Int) = Unit
                    override fun afterTextChanged(s: Editable?) = Unit
                    override fun onTextChanged(s: CharSequence?, at: Int, before: Int, count: Int) {
                        val ms = WordSync.parseSeconds(s?.toString().orEmpty())
                        words = words.mapIndexed { i,item -> if (i != index) item else if (start) item.copy(startMs = ms,manual = true) else item.copy(endMs = ms,manual = true) }
                        quality.text = if (words[index].startMs == null || words[index].endMs == null) "Missing timestamp" else "Manually adjusted timing"
                        clear = false; refreshPreview(ms)
                    }
                })
            }
            line.addView(timeInput(word.startMs,true),LinearLayout.LayoutParams(0,panel.dp(48),1f)); line.addView(timeInput(word.endMs,false),LinearLayout.LayoutParams(0,panel.dp(48),1f))
            line.addView(panel.button("Listen") { val start = words[index].startMs ?: voice?.startMs ?: 0; fullPreview(candidate() ?: original,(start-150).coerceAtLeast(0),true) })
            card.addView(line); panel.body.addView(card,LinearLayout.LayoutParams(-1,-2).apply { bottomMargin = panel.dp(8) })
        }
        errorText = panel.text(validation() ?: "Ready to preview / apply · review uncertain words",12).apply { setTextColor(LuxeTheme.gold) }; panel.body.addView(errorText)
        if (layer?.animation?.wordCues?.isNotEmpty() == true) panel.body.addView(panel.button("Clear saved word sync") { clear = true; words = emptyList(); status = "Apply to clear word sync"; draw() })
    }
    fun show() = panel.show()
}
