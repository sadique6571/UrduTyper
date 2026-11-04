package me.nomi.urdutyper.presentation.ui.type.studio

import android.content.Context
import android.content.res.ColorStateList
import android.graphics.Color
import android.view.Gravity
import android.widget.*
import com.google.android.material.slider.RangeSlider
import kotlinx.coroutines.*
import kotlin.math.roundToLong

class VideoStudioInspector(
    private val context: Context, private val fonts: StudioFonts, private val original: StudioDocument,
    private val selectedId: String?, initialTab: String,
    private val preview: (StudioDocument,Long) -> Unit, private val apply: (StudioDocument) -> Unit,
    private val fullPreview: (StudioDocument) -> Unit, private val external: (String) -> Unit,
    private val export: (StudioDocument,Int,Boolean) -> Unit, private val closed: () -> Unit,
    private val wordSync: ((StudioDocument) -> Unit)? = null
) {
    val panel = StudioInspector(context,"Video studio",fonts)
    private var candidate = original
    private var tab = initialTab
    private var format = 0
    private var previewTime = candidate.sceneOffsetMs()
    private val scope = CoroutineScope(SupervisorJob()+Dispatchers.Main.immediate)
    private val waveformJobs = mutableMapOf<String,Job>()
    private val animationRenderer = StudioAnimationRenderer(StudioRenderer(context,fonts))
    init {
        panel.setOnDismissListener { scope.cancel(); closed() }
        val animation = selected()?.animation?.let { VideoEditRules.animation(it,candidate.video.durationMs) }
        if (animation != null && animation.effect != "none") previewTime = candidate.sceneOffsetMs()+animation.startMs+minOf(animation.revealMs/2,(animation.endMs-animation.startMs)/2)
        draw(); render()
    }
    private fun selected() = candidate.layers.firstOrNull { it.id == selectedId && !it.locked }
    private fun change(next: StudioDocument) { candidate = next; render() }
    private fun render() {
        previewTime = previewTime.coerceIn(0,candidate.movieDurationMs()-1)
        preview(candidate,previewTime); panel.render(candidate,previewTime)
    }
    private fun layerChange(value: LayerAnimation) {
        val layer = selected() ?: return
        val updated = if (value.effect !in listOf("words","typing","highlight")) value.copy(wordCues = emptyList(),voiceSignature = "") else value
        candidate = VideoEditRules.timing(candidate,layer.id,updated)
        val current = selected()!!.animation
        previewTime = candidate.sceneOffsetMs()+current.startMs+minOf(current.revealMs/2,(current.endMs-current.startMs)/2)
        render()
    }
    private fun draw() {
        panel.tabs(listOf("Scene","Animate","Weather","Audio","Export"),tab) { tab = it; draw() }
        panel.body.removeAllViews()
        when(tab) { "Scene" -> scene(); "Animate" -> animate(); "Weather" -> weather(); "Audio" -> audio(); else -> exportMenu() }
        val title = when(tab) { "Scene" -> "Apply scene"; "Animate" -> "Apply animation"; "Audio" -> "Apply audio"; "Weather" -> "Apply weather"; else -> "Export" }
        panel.actions(title, {
            if (tab == "Export") { if (format == 2 && candidate.movieDurationMs() > 10000) return@actions; export(candidate,format,false) }
            else apply(candidate)
            panel.dismiss()
        }, preview = { fullPreview(VideoEditRules.exportPreview(candidate,if (tab == "Export") format else 0)) })
        if (tab == "Export" && format == 2 && candidate.movieDurationMs() > 10000) {
            panel.footer.getChildAt(panel.footer.childCount-1).isEnabled = false
        }
    }
    private fun stepper(title: String, ms: Long, min: Long, max: Long, step: Long = 500, change: (Long) -> Unit) {
        panel.section(title)
        val row = panel.row().apply { gravity = Gravity.CENTER_VERTICAL }
        row.addView(panel.button("−") { change((ms-step).coerceIn(min,max)); draw() }.apply { contentDescription = "Decrease $title"; isEnabled = ms > min },LinearLayout.LayoutParams(panel.dp(48),panel.dp(48)))
        row.addView(panel.text(String.format(java.util.Locale.ROOT,"%.1f s",ms/1000f),17).apply { gravity = Gravity.CENTER },LinearLayout.LayoutParams(0,panel.dp(48),1f))
        row.addView(panel.button("+") { change((ms+step).coerceIn(min,max)); draw() }.apply { contentDescription = "Increase $title"; isEnabled = ms < max },LinearLayout.LayoutParams(panel.dp(48),panel.dp(48)))
        panel.body.addView(row)
    }
    private fun atmosphereTitle(value: String) = when(value) { "auto" -> "Auto"; "leaves" -> "Autumn leaves"; "snow" -> "Snowfall"; "rain" -> "Rain"; "particles" -> "Particles"; else -> "None" }
    private fun fitVoice(whole: Boolean) {
        runCatching { if (whole) VoiceTiming.storyboard(candidate) else VoiceTiming.selected(candidate,selectedId) }
            .onSuccess { next -> change(next); previewTime = next.sceneOffsetMs()+next.video.durationMs/2; render(); draw(); Toast.makeText(context,"Timing fitted. Apply to keep changes.",Toast.LENGTH_SHORT).show() }
            .onFailure { Toast.makeText(context,it.message ?: "Could not fit timing",Toast.LENGTH_LONG).show() }
    }
    private fun voiceControls() {
        val voice = candidate.audio.firstOrNull { it.role == "voice" }
        panel.section("Word-by-word sync")
        selected()?.takeIf { it.animation.wordCues.isNotEmpty() }?.let { layer -> panel.body.addView(panel.text(if (WordSync.validSource(candidate,layer)) "Saved word timestamps · ${layer.animation.wordCues.size} words" else "Voice changed: word sync is paused. Re-align or clear saved timings.",12)) }
        if (wordSync != null) panel.body.addView(panel.button("Offline word sync",true) { panel.dismiss(); wordSync.invoke(candidate) })
        panel.section("Match text & voice · duration fit")
        if (voice == null) { panel.body.addView(panel.text("Record, import or generate a voice track to fit text timing.",12)); return }
        panel.body.addView(panel.text("Voice ${"%.1f".format(java.util.Locale.ROOT,voice.durationMs()/1000f)}s · Video ${"%.1f".format(java.util.Locale.ROOT,candidate.movieDurationMs()/1000f)}s",13))
        StudioMenu.grid(panel.body,listOf(
            StudioMenu.Action("Fit selected text","Duration","Scene timing",enabled = selected()?.let { it.kind == "text" && it.visible } == true) { fitVoice(false) },
            StudioMenu.Action("Fit whole video","Scenes","Pace all scenes") { fitVoice(true) }
        ),2)
        panel.body.addView(panel.text("Includes the voice start offset and up to 0.3s final hold. Word pace is estimated, not speech recognition. Trim voice first. Apply keeps changes; Undo restores them.",12).apply { setTextColor(LuxeTheme.muted) })
    }
    private fun scene() {
        panel.body.addView(panel.text("${candidate.orderedScenes().size} scenes · ${candidate.movieDurationMs()/1000f}s total · 60s maximum",12).apply { setTextColor(LuxeTheme.muted) })
        stepper("Scene duration",candidate.video.durationMs,1000,VideoEditRules.maximumSceneDuration(candidate)) {
            change(VideoEditRules.duration(candidate,it)); previewTime = candidate.sceneOffsetMs(); render()
        }
        panel.body.addView(panel.horizontal {
            listOf(5L,10L,15L,30L,60L).forEach { seconds ->
                addView(panel.button("${seconds}s", candidate.video.durationMs == seconds*1000) {
                    change(VideoEditRules.duration(candidate,seconds*1000)); draw()
                }.apply { isEnabled = seconds*1000 <= VideoEditRules.maximumSceneDuration(candidate); contentDescription = "Set scene duration ${seconds} seconds" })
            }
        })
        val maximum = VideoEditRules.maximumSceneDuration(candidate)
        if (maximum > 1000) panel.slider("Scene duration seconds",candidate.video.durationMs/1000f,1f,maximum/1000f,{ "%.1f s".format(java.util.Locale.ROOT,it) }) { seconds ->
            change(VideoEditRules.duration(candidate,(seconds*1000).roundToLong()))
        }
        val exactRow = panel.row()
        val secondsInput = EditText(context).apply {
            inputType = android.text.InputType.TYPE_CLASS_NUMBER or android.text.InputType.TYPE_NUMBER_FLAG_DECIMAL
            setText(String.format(java.util.Locale.ROOT,"%.1f",candidate.video.durationMs/1000f))
            setTextColor(LuxeTheme.text); setHintTextColor(LuxeTheme.muted); hint = "Seconds"; isSingleLine = true
            contentDescription = "Exact scene duration seconds"; backgroundTintList = ColorStateList.valueOf(LuxeTheme.gold)
        }
        exactRow.addView(secondsInput,LinearLayout.LayoutParams(0,panel.dp(48),1f))
        exactRow.addView(panel.button("Set seconds") {
            val value = secondsInput.text.toString().toDoubleOrNull()
            if (value == null || !value.isFinite() || value < 1 || value > maximum/1000.0) { secondsInput.error = "Enter 1–${maximum/1000f} seconds" }
            else { change(VideoEditRules.duration(candidate,(value*1000).roundToLong())); draw() }
        })
        panel.body.addView(exactRow)
        panel.body.addView(panel.text("Current scene ${candidate.video.durationMs/1000f}s · complete video ${candidate.movieDurationMs()/1000f}s. Apply scene saves the new time.",12))
        panel.section("Background motion")
        StudioMenu.grid(panel.body,listOf("none" to "Still","zoom" to "Zoom","pan" to "Pan").map { (value,title) ->
            StudioMenu.Action(title,if (value == "none") "Still" else if (value == "zoom") "Zoom" else "Pan",selected = candidate.video.backgroundMotion == value) {
                change(candidate.copy(video = candidate.video.copy(backgroundMotion = value))); draw()
            }
        })
        panel.toggle("Loop video preview",candidate.video.loopPreview) { change(candidate.copy(video = candidate.video.copy(loopPreview = it))) }
        val scenes = candidate.orderedScenes()
        val current = scenes.first { it.id == candidate.sceneId }
        if (scenes.indexOf(current) > 0) {
            panel.section("Entrance transition")
            panel.body.addView(panel.horizontal {
                listOf("none","fade","slide","dissolve").forEach { value -> addView(panel.button(value.replaceFirstChar { it.uppercase() },current.transition == value) {
                    change(candidate.copy(scenes = candidate.orderedScenes().map { if (it.id == candidate.sceneId) it.copy(transition = value) else it })); draw()
                }) }
            })
            panel.slider("Transition seconds",current.transitionMs/1000f,.1f,2f) { value ->
                change(candidate.copy(scenes = candidate.orderedScenes().map { if (it.id == candidate.sceneId) it.copy(transitionMs = (value*1000).roundToLong()) else it }))
            }
        }
        panel.body.addView(panel.button("Manage scenes / transitions") { panel.dismiss(); external("scenes") })
        panel.body.addView(panel.button("Video templates") { panel.dismiss(); external("templates") })
    }
    private fun weather() {
        panel.section("Atmosphere · match your background")
        val suggested = animationRenderer.suggestion(candidate)
        panel.body.addView(panel.text("Suggested: ${atmosphereTitle(suggested.effect)} · ${suggested.reason}. Auto uses names and colors, not AI scene recognition.",12).apply { setTextColor(LuxeTheme.muted) })
        StudioMenu.grid(panel.body,listOf("auto","none","leaves","rain","snow","particles").map { value ->
            StudioMenu.Action(atmosphereTitle(value),when(value) { "rain" -> "Rain"; "snow" -> "Snow"; "leaves" -> "Leaves"; "auto" -> "Effects"; else -> "Still" },selected = candidate.video.atmosphere == value) {
                change(candidate.copy(video = candidate.video.copy(atmosphere = value))); previewTime = candidate.sceneOffsetMs()+minOf(1500,candidate.video.durationMs/2); render(); draw()
            }
        })
        if (candidate.video.atmosphere != "none") {
            panel.slider("Weather intensity",candidate.video.atmosphereIntensity,0f,1f,{ "${(it*100).toInt()}%" }) { value -> change(candidate.copy(video = candidate.video.copy(atmosphereIntensity = value))) }
            panel.slider("Weather speed",candidate.video.atmosphereSpeed,.25f,2f,{ "%.2f×".format(java.util.Locale.ROOT,it) }) { value -> change(candidate.copy(video = candidate.video.copy(atmosphereSpeed = value))) }
        }
        panel.body.addView(panel.button("Play atmosphere preview") { fullPreview(candidate) })
    }
    private fun animate() {
        val layer = selected()
        if (layer == null) { panel.body.addView(panel.text("Select an unlocked text or image layer first. Scene, Audio and Export still work independently.")); return }
        val normalized = VideoEditRules.animation(layer.animation,candidate.video.durationMs)
        candidate = VideoEditRules.timing(candidate,layer.id,normalized)
        panel.body.addView(panel.text(if (layer.kind == "text") layer.text.take(60) else "Selected image layer",14).apply { maxLines = 2; setTextColor(LuxeTheme.muted) })
        val effects = if (layer.kind == "image") listOf("none","fade","slide","zoom") else AnimationTimeline.effects
        panel.section("Animation presets")
        panel.body.addView(panel.horizontal {
            effects.forEach { effect ->
                val sampleLayer = layer.copy(text = if (layer.kind == "text") "امید" else layer.text,x = 160f,y = 90f,width = 270f,height = 150f,size = 38f,rotation = 0f,
                    animation = LayerAnimation(effect,0,2000,1200))
                val sample = StudioDocument(width = 320,height = 180,backgroundColor = LuxeTheme.surface,gradientColor = LuxeTheme.surface,layers = listOf(sampleLayer),video = VideoSettings(2000))
                val card = panel.column().apply {
                    background = panel.shape(selected = effect == normalized.effect); setPadding(panel.dp(4),panel.dp(4),panel.dp(4),panel.dp(4))
                    contentDescription = "Animation preset $effect"; isClickable = true; isFocusable = true
                    setOnClickListener { layerChange(selected()!!.animation.copy(effect = effect)); draw() }
                }
                card.addView(ImageView(context).apply { setImageBitmap(animationRenderer.bitmap(sample,160,650)); scaleType = ImageView.ScaleType.CENTER_INSIDE },LinearLayout.LayoutParams(panel.dp(76),panel.dp(52)))
                card.addView(panel.text(effect.replaceFirstChar { it.uppercase() },12).apply { gravity = Gravity.CENTER })
                addView(card,LinearLayout.LayoutParams(panel.dp(84),-2).apply { marginEnd = panel.dp(7) })
            }
        })
        panel.section("Layer timing · seconds")
        val timingLabel = panel.text(""); panel.body.addView(timingLabel)
        fun timingCaption() { val a = selected()!!.animation; timingLabel.text = String.format(java.util.Locale.ROOT,"Start %.1fs  —  End %.1fs",a.startMs/1000f,a.endMs/1000f) }
        timingCaption()
        val range = range("Layer timing range",candidate.video.durationMs,normalized.startMs,normalized.endMs, finished = { draw() }) { from,until ->
            layerChange(selected()!!.animation.copy(startMs = from,endMs = until)); timingCaption()
        }
        panel.body.addView(range,LinearLayout.LayoutParams(-1,panel.dp(56)))
        if (normalized.effect != "none") {
            val window = normalized.endMs-normalized.startMs
            val maximumReveal = (window-100).coerceAtLeast(100)
            val speed = if (maximumReveal <= 100) 100f else (1f-(normalized.revealMs-100).toFloat()/(maximumReveal-100))*100
            panel.slider("Animation speed · slow → fast",speed.coerceIn(0f,100f),0f,100f,{ "${it.toInt()}%" }) { value ->
                val a = selected()!!.animation; val length = a.endMs-a.startMs
                layerChange(a.copy(revealMs = (100+(length-200).coerceAtLeast(0)*(1-value/100f)).roundToLong()))
            }
            panel.body.addView(panel.text("Reveal fits the layer timing with a short hold so the complete text stays visible.",12).apply { setTextColor(LuxeTheme.muted) })
            if (normalized.effect == "typing") panel.toggle("Blinking typing cursor",normalized.cursor) { layerChange(selected()!!.animation.copy(cursor = it)) }
            panel.toggle("Repeat animation",normalized.repeat) { layerChange(selected()!!.animation.copy(repeat = it)) }
        }
        if (layer.kind == "text") voiceControls()
        panel.body.addView(panel.button("Play preview") { fullPreview(candidate) })
    }
    private fun range(title: String, length: Long, start: Long, end: Long, finished: (() -> Unit)? = null, change: (Long,Long) -> Unit) = RangeSlider(context).apply {
        valueFrom = 0f; valueTo = length.coerceAtLeast(1)/1000f; stepSize = 0f
        setValues(start.coerceAtLeast(0)/1000f,end.coerceAtMost(length)/1000f)
        thumbTintList = ColorStateList.valueOf(LuxeTheme.gold); trackActiveTintList = ColorStateList.valueOf(LuxeTheme.gold)
        trackInactiveTintList = ColorStateList.valueOf(LuxeTheme.border); contentDescription = title
        setLabelFormatter { String.format(java.util.Locale.ROOT,"%.1fs",it) }
        addOnSliderTouchListener(object : RangeSlider.OnSliderTouchListener {
            override fun onStartTrackingTouch(slider: RangeSlider) = Unit
            override fun onStopTrackingTouch(slider: RangeSlider) { finished?.invoke() }
        })
        addOnChangeListener { slider,_,user ->
            if (user) {
                val from = (slider.values[0]*1000).roundToLong().coerceIn(0,length-1)
                val until = (slider.values[1]*1000).roundToLong().coerceIn(from+1,length)
                change(from,until)
            }
        }
    }
    private fun audio() {
        panel.body.addView(panel.text("Voice and music are global tracks across every scene. GIF is silent.",12).apply { setTextColor(LuxeTheme.muted) })
        voiceControls()
        StudioMenu.grid(panel.body,listOf(
            StudioMenu.Action("Record","Voice","Use microphone") { panel.dismiss(); external("record") },
            StudioMenu.Action("Voice","Voice","Import recording") { panel.dismiss(); external("voice") },
            StudioMenu.Action("Music","Music","Import a track") { panel.dismiss(); external("music") },
            StudioMenu.Action("Urdu text → voice","Text","Generate speech") { panel.dismiss(); external("tts") }
        ),2)
        panel.toggle("Lower music while voice plays",candidate.duckMusic) { change(candidate.copy(duckMusic = it)) }
        candidate.audio.forEach { source ->
            panel.section("${source.role.replaceFirstChar { it.uppercase() }} · ${source.name}")
            val wave = AudioWaveformView(context); panel.body.addView(wave,LinearLayout.LayoutParams(-1,panel.dp(48)))
            loadWaveform(source,wave)
            fun current() = candidate.audio.first { it.id == source.id }
            fun update(track: StudioAudio) {
                val normalizedTrack = track.copy(fadeInMs = track.fadeInMs.coerceAtMost(track.durationMs()),fadeOutMs = track.fadeOutMs.coerceAtMost(track.durationMs()))
                change(candidate.copy(audio = candidate.audio.map { if (it.id == track.id) normalizedTrack else it }))
            }
            val trimLabel = panel.text(""); panel.body.addView(trimLabel)
            fun caption() { val t = current(); trimLabel.text = String.format(java.util.Locale.ROOT,"Source trim %.1f–%.1fs",t.trimStartMs/1000f,t.trimEndMs/1000f) }
            caption()
            panel.body.addView(range("Trim ${source.role}",source.sourceDurationMs,source.trimStartMs,source.trimEndMs, finished = { draw() }) { start,end ->
                val minimum = minOf(100L,source.sourceDurationMs)
                val from = start.coerceAtMost(source.sourceDurationMs-minimum)
                val until = end.coerceAtLeast(from+minimum)
                update(current().copy(trimStartMs = from,trimEndMs = until)); caption(); loadWaveform(current(),wave)
            },LinearLayout.LayoutParams(-1,panel.dp(56)))
            if (source.startMs >= candidate.movieDurationMs()) panel.body.addView(panel.text("This track starts after the video ends. Move its start time to hear it.",12).apply { setTextColor(0xffff9d85.toInt()) })
            panel.slider("${source.role} volume",source.volume,0f,1f,{ "${(it*100).toInt()}%" }) { update(current().copy(volume = it)) }
            panel.slider("Start in video · seconds",source.startMs/1000f,0f,(candidate.movieDurationMs()-1)/1000f) { update(current().copy(startMs = (it*1000).roundToLong())) }
            panel.slider("Fade in · seconds",source.fadeInMs/1000f,0f,minOf(source.durationMs(),candidate.movieDurationMs())/1000f) { update(current().copy(fadeInMs = (it*1000).roundToLong())) }
            panel.slider("Fade out · seconds",source.fadeOutMs/1000f,0f,minOf(source.durationMs(),candidate.movieDurationMs())/1000f) { update(current().copy(fadeOutMs = (it*1000).roundToLong())) }
            panel.body.addView(panel.button("Remove ${source.role}") { change(candidate.copy(audio = candidate.audio.filterNot { it.id == source.id })); draw() })
        }
    }
    private fun loadWaveform(track: StudioAudio, view: AudioWaveformView) {
        waveformJobs[track.id]?.cancel()
        waveformJobs[track.id] = scope.launch {
            delay(200)
            val job = coroutineContext[Job]!!
            val peaks = runCatching { withContext(Dispatchers.IO) { StudioAudioEngine.waveform(track) { !job.isActive } } }.getOrNull()
            if (peaks != null && job.isActive) view.peaks = peaks
        }
    }
    private fun exportMenu() {
        if (candidate.orderedScenes().any { scene -> scene.layers.any { it.animation.wordCues.isNotEmpty() && !WordSync.validSource(candidate,it) } }) panel.body.addView(panel.text("Voice changed: saved word sync is paused. Select that text and re-align or clear its timings before exporting.",13).apply { setTextColor(0xffff9d85.toInt()) })
        panel.section("Export format")
        val formats = listOf("720p MP4","1080p MP4","GIF")
        StudioMenu.grid(panel.body,formats.mapIndexed { index, title ->
            StudioMenu.Action(title,if (index == 2) "Image" else "Video",if (index == 0) "Fast · smaller" else if (index == 1) "Full HD" else "Silent · up to 10s",selected = index == format) { format = index; draw() }
        })
        val (width,height) = StudioVideoExporter.dimensions(candidate,if (format == 1) 1080 else 720)
        val seconds = candidate.movieDurationMs()/1000f
        val audible = candidate.audio.any { it.volume > 0 && it.startMs < candidate.movieDurationMs() && it.durationMs() > 0 }
        val estimate = ((width.toLong()*height*4+if (!audible) 0 else 160000)*seconds/8/1000000)
        panel.section("${candidate.orderedScenes().size} scenes · ${String.format(java.util.Locale.ROOT,"%.1fs",seconds)}")
        panel.body.addView(panel.text(if (format == 2) "GIF · 256 colors · no audio · maximum 10 seconds" else "$width × $height · 24 fps · H.264\n${if (!audible) "Silent" else "AAC voice / music"}\nAbout ${String.format(java.util.Locale.ROOT,"%.1f",estimate)} MB (estimate)",15))
        if (format == 2 && candidate.movieDurationMs() > 10000) panel.body.addView(panel.text("Shorten the video to 10 seconds or choose MP4.",14).apply { setTextColor(0xffff9d85.toInt()) })
        panel.section("Delivery")
        StudioMenu.grid(panel.body,listOf(
            StudioMenu.Action("Export & share","Share","Choose an app",enabled = format != 2 || candidate.movieDurationMs() <= 10000) { export(candidate,format,true); panel.dismiss() },
            StudioMenu.Action("Saved videos / GIFs","Saved","Open your exports") { panel.dismiss(); external("saved") }
        ),2)
    }
    fun show() = panel.show()
}
