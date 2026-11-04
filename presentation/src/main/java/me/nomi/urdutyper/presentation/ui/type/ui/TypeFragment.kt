package me.nomi.urdutyper.presentation.ui.type.ui

import android.app.AlertDialog
import android.content.ClipData
import android.content.ContentValues
import android.content.Intent
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Color
import android.graphics.Typeface
import android.graphics.drawable.GradientDrawable
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.os.Environment
import android.provider.MediaStore
import android.text.InputType
import android.view.Gravity
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.*
import androidx.activity.addCallback
import androidx.activity.result.contract.ActivityResultContracts
import androidx.core.content.FileProvider
import androidx.core.view.setPadding
import androidx.fragment.app.Fragment
import androidx.fragment.app.activityViewModels
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.repeatOnLifecycle
import androidx.lifecycle.lifecycleScope
import androidx.navigation.fragment.findNavController
import com.google.android.material.bottomsheet.BottomSheetDialog
import com.google.android.material.button.MaterialButton
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.isActive
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import me.nomi.urdutyper.domain.entity.TemplateStyle
import me.nomi.urdutyper.presentation.ui.type.studio.*
import me.nomi.urdutyper.presentation.ui.type.viewmodel.TypeViewModel
import me.nomi.urdutyper.presentation.ui.type.state.TypeUiState
import java.io.File
import java.util.UUID
import kotlin.math.roundToInt

/** Native, document-based editor. The canvas is a preview, never the source of an export. */
@AndroidEntryPoint
class TypeFragment : Fragment() {
    private val cream = LuxeTheme.background
    private val green = LuxeTheme.gold
    private val ink = LuxeTheme.text
    private lateinit var canvas: StudioCanvas
    private lateinit var fonts: StudioFonts
    private lateinit var store: StudioStore
    private lateinit var document: StudioDocument
    private lateinit var status: TextView
    private lateinit var selection: TextView
    private lateinit var undoButton: View
    private lateinit var redoButton: View
    private lateinit var zoomButton: MaterialButton
    private lateinit var exportButton: MaterialButton
    private lateinit var modeButton: MaterialButton
    private lateinit var fontSizeControl: FontSizeControl
    private lateinit var gridButton: MaterialButton
    private lateinit var snapButton: MaterialButton
    private var gridEnabled = false
    private var snapEnabled = true
    private lateinit var toolActions: LinearLayout
    private val toolTabs = mutableMapOf<String, MaterialButton>()
    private var activeTool = "Text"
    private lateinit var playbackRow: LinearLayout
    private lateinit var canvasControls: LinearLayout
    private lateinit var quickControls: EditorQuickControls
    private lateinit var previewPlay: MaterialButton
    private lateinit var previewClock: TextView
    private lateinit var previewSeek: SeekBar
    private lateinit var timeline: StudioTimelineView
    private lateinit var timelinePanel: LinearLayout
    private lateinit var sceneStrip: LinearLayout
    private lateinit var playback: StudioPlayback
    private val waveforms = mutableMapOf<String, FloatArray>()
    private val waveformJobs = mutableMapOf<String, Job>()
    private var previewPosition = 0L
    private var showingPreview = false
    private val history = StudioHistory()
    private var saveJob: Job? = null
    private var pendingFontLayer: String? = null
    private var imageAsBackground = false
    private var exporting = false
    private val viewModel: TypeViewModel by activityViewModels()
    private var audioRole = "voice"
    private var recorder: android.media.MediaRecorder? = null
    private var recordingFile: File? = null
    private var recordingDialog: AlertDialog? = null
    private var activeInspector: StudioInspector? = null
    private var fullPreview: FullScreenPreviewDialog? = null
    private var animationPreview: AnimationPreviewDialog? = null
    private var speechJob: Job? = null
    private var speechDialog: AlertDialog? = null
    @Volatile private var cancelVideo = false
    private val microphonePermission = registerForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
        if (granted) startRecording() else message("Microphone permission is needed only to record your voice. You can still import an audio file.")
    }
    private val importAudio = registerForActivityResult(ActivityResultContracts.GetContent()) { uri ->
        if (uri != null) {
            val role = audioRole
            lifecycleScope.launch {
                runCatching { withContext(Dispatchers.IO) { copyAudio(uri, role) } }
                    .onSuccess { if (view != null) { attachAudio(it); audioSheet() } }
                    .onFailure { if (view != null) message(it.message ?: "Could not import audio") }
            }
        }
    }

    private val importCaptions = registerForActivityResult(ActivityResultContracts.GetContent()) { uri ->
        if (uri != null) viewLifecycleOwner.lifecycleScope.launch {
            runCatching { withContext(Dispatchers.IO) {
                val text = requireContext().contentResolver.openInputStream(uri)!!.bufferedReader().use { reader ->
                    val buffer = CharArray(100001); var total = 0
                    while (total < buffer.size) { val count = reader.read(buffer, total, buffer.size - total); if (count < 0) break; total += count }
                    require(total <= 100000) { "Caption file must be under 100 KB" }; String(buffer, 0, total)
                }
                StudioCaptions.parse(text)
            } }.onSuccess { captions -> if (view != null) runCatching { commit(StudioCaptions.apply(document, captions)) }
                .onSuccess { toast("Timed captions added; edit each layer to correct Urdu text") }.onFailure { message(it.message ?: "Could not add captions") } }
                .onFailure { if (view != null) message(it.message ?: "Choose a valid SRT subtitle file") }
        }
    }

    // Register launchers before the Fragment is created, never from onViewCreated.
    private val importFont = registerForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        if (uri != null) {
            val layerId = pendingFontLayer
            lifecycleScope.launch {
                runCatching { withContext(Dispatchers.IO) { fonts.import(uri) } }
                    .onSuccess { font ->
                        if (view == null) return@onSuccess
                        val layer = document.layers.firstOrNull { it.id == layerId && !it.locked }
                        if (layer != null) replaceLayer(layer.copy(fontId = font.id))
                        toast("Font imported and saved on this device")
                    }.onFailure { if (view != null) message(it.message ?: "Could not import font") }
            }
        }
    }
    private val importSpeechModel = registerForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        if (uri != null) viewLifecycleOwner.lifecycleScope.launch {
            val cancelled = java.util.concurrent.atomic.AtomicBoolean(false)
            val progress = StudioInspector(requireContext(),"Importing speech model",fonts)
            activeInspector = progress; progress.showArtwork(false); progress.tabBar.visibility = View.GONE
            progress.body.addView(progress.text("Verifying the model file. This may take a moment for large models.",14))
            progress.footer.addView(progress.button("Cancel import",true) { progress.dismiss() },LinearLayout.LayoutParams(-1,dp(48)))
            progress.setOnDismissListener { cancelled.set(true) }; progress.show()
            try {
                val selected = withContext(Dispatchers.IO) {
                    val stream = requireContext().contentResolver.openInputStream(uri) ?: error("Cannot read model file")
                    SpeechModels(requireContext()).importModel(stream,{ cancelled.get() || !isActive })
                }
                if (view != null && !cancelled.get()) { toast("${selected.title} model imported and verified"); wordSyncSheet() }
            } catch (error: Exception) { if (view != null && !cancelled.get()) message(error.message ?: "Model import failed") }
            finally { progress.dismiss() }
        }
    }

    private val importImage = registerForActivityResult(ActivityResultContracts.GetContent()) { uri ->
        if (uri != null) {
            val background = imageAsBackground
            lifecycleScope.launch {
                runCatching { withContext(Dispatchers.IO) {
                    val name = runCatching { requireContext().contentResolver.query(uri,arrayOf(android.provider.OpenableColumns.DISPLAY_NAME),null,null,null)?.use { cursor -> if (cursor.moveToFirst()) cursor.getString(0).orEmpty().take(200) else "" } ?: "" }.getOrDefault("")
                    copyImage(uri) to name
                } }
                    .onSuccess { (file,name) ->
                        if (view == null) return@onSuccess
                        if (background) commit(document.copy(backgroundImage = file.path, backgroundName = name, backgroundStyle = "", backgroundVisible = true,video = document.video.copy(atmosphere = if (document.video.atmosphere in listOf("none","auto")) "auto" else document.video.atmosphere)))
                        else {
                            val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
                            BitmapFactory.decodeFile(file.path, bounds)
                            val width = document.width * .65f
                            addLayer(StudioLayer(kind = "image", imagePath = file.path, x = document.width / 2f,
                                y = document.height / 2f, width = width, height = width * bounds.outHeight / bounds.outWidth))
                        }
                    }.onFailure { if (view != null) message(it.message ?: "Could not open image") }
            }
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        fonts = StudioFonts(requireContext().applicationContext)
        store = StudioStore(File(requireContext().filesDir, "studio-drafts"))
        pendingFontLayer = savedInstanceState?.getString("pendingFontLayer")
        imageAsBackground = savedInstanceState?.getBoolean("imageAsBackground") ?: false
        audioRole = savedInstanceState?.getString("audioRole") ?: "voice"
        gridEnabled = savedInstanceState?.getBoolean("gridEnabled") ?: false
        snapEnabled = savedInstanceState?.getBoolean("snapEnabled") ?: true
        activeTool = savedInstanceState?.getString("activeTool") ?: if (arguments?.getBoolean("startVideo") == true) "Video" else "Text"
        val id = savedInstanceState?.getString("documentId") ?: arguments?.getString("draftId")
        document = id?.let { store.load(it) } ?: fromTemplate()
        history.reset(document)
    }

    private fun fromTemplate(): StudioDocument {
        val text = arguments?.getString("templateText").orEmpty()
        val style = arguments?.getString("templateStyle").orEmpty().ifBlank { "LUXE_MOONLIT_LAKE" }
        val image = arguments?.getString("templateImagePath").orEmpty()
        val dark = if (arguments?.containsKey("templateDarkText") == true) arguments!!.getBoolean("templateDarkText")
            else if (style.startsWith("LUXE_")) style == "LUXE_IVORY_BLOSSOM"
            else if (style == "STUDIO_GREEN") false else TemplateStyle.values().firstOrNull { it.name == style }?.darkText ?: true
        return StudioDocument(width = 1080, height = if (style.startsWith("LUXE_")) 1620 else 1080,
            backgroundStyle = style, backgroundImage = image,
            layers = listOf(StudioLayer(y = if (style.startsWith("LUXE_")) 810f else 540f, text = text.ifBlank { "خوابوں کو حقیقت بناؤ" },
                fontId = arguments?.getString("templateFont").orEmpty().ifBlank { "nastaliq" },
                color = if (dark) 0xff1d352e.toInt() else Color.WHITE)))
    }

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, state: Bundle?): View {
        val root = column().apply { setBackgroundColor(cream) }
        val header = row().apply { setPadding(dp(8)); gravity = Gravity.CENTER_VERTICAL }
        header.addView(button("", "Back to designs") { saveNow(); findNavController().navigateUp() }.apply { icon = LuxeIcon("Back"); iconPadding = 0 }, LinearLayout.LayoutParams(dp(48), dp(48)))
        val titles = column()
        titles.addView(label(document.title, 17).apply {
            setTypeface(Typeface.DEFAULT, Typeface.BOLD); maxLines = 1
            setOnClickListener { rename() }
        })
        status = label("Saved · device", 11)
        titles.addView(status)
        header.addView(titles, LinearLayout.LayoutParams(0, -2, 1f))
        exportButton = button("Export") { if (activeTool == "Video") motionExportSheet() else exportSheet() }
        exportButton.isEnabled = !exporting
        exportButton.setBackgroundColor(green); exportButton.setTextColor(LuxeTheme.background)
        header.addView(exportButton)
        root.addView(header)
        gridButton = button(if (gridEnabled) "Grid ✓" else "Grid") {
            gridEnabled = !gridEnabled; canvas.showGrid = gridEnabled; canvas.invalidate()
            gridButton.text = if (gridEnabled) "Grid ✓" else "Grid"
        }
        snapButton = button(if (snapEnabled) "Snap ✓" else "Snap") {
            snapEnabled = !snapEnabled; canvas.snapToCenter = snapEnabled
            snapButton.text = if (snapEnabled) "Snap ✓" else "Snap"
        }
        canvas = StudioCanvas(requireContext(), StudioRenderer(requireContext(), fonts)).apply {
            document = this@TypeFragment.document
            selectedId = document.layers.lastOrNull()?.id
            showGrid = gridEnabled; snapToCenter = snapEnabled
            onChange = { commit(it) }
            onSelect = {
                exitPreview()
                if (::toolActions.isInitialized && activeTool != "Video") showToolGroup(if (selected()?.kind == "image") "Media" else "Selection")
                refresh()
            }
            onEdit = { editSelected() }
            onZoom = { zoomButton.text = "$it% · Fit" }
        }
        root.addView(canvas, LinearLayout.LayoutParams(-1, 0, 1f))
        playback = StudioPlayback({ position, playing ->
            previewPosition = position
            if (showingPreview) { canvas.previewTimeMs = position.coerceAtMost(document.movieDurationMs() - 1); canvas.invalidate() }
            previewSeek.progress = position.toInt()
            previewPlay.text = if (playing) "Ⅱ Pause" else "▶ Play"
            previewClock.text = String.format(java.util.Locale.ROOT, "%.1f / %.1fs", position / 1000f, document.movieDurationMs() / 1000f)
            if (::timeline.isInitialized) timeline.positionMs = position
        }, { toast(it) })
        playbackRow = row().apply { gravity = Gravity.CENTER_VERTICAL; setPadding(dp(8), 0, dp(8), 0) }
        previewPlay = button("▶ Play") { showingPreview = true; playback.toggle(document) }
        playbackRow.addView(previewPlay)
        previewSeek = SeekBar(requireContext()).apply {
            max = document.movieDurationMs().toInt()
            contentDescription = "Video playhead"
            setOnSeekBarChangeListener(object : SeekBar.OnSeekBarChangeListener {
                override fun onStartTrackingTouch(bar: SeekBar?) { playback.pause(); showingPreview = true }
                override fun onProgressChanged(bar: SeekBar?, value: Int, user: Boolean) { if (user) playback.seek(document, value.toLong()) }
                override fun onStopTrackingTouch(bar: SeekBar?) = Unit
            })
        }
        playbackRow.addView(previewSeek, LinearLayout.LayoutParams(0, dp(48), 1f))
        previewClock = label("0.0s", 11)
        playbackRow.addView(previewClock)
        playbackRow.addView(button("Edit") { exitPreview() })
        root.addView(playbackRow)
        timelinePanel = column().apply { visibility = View.GONE }
        timelinePanel.addView(horizontal { sceneStrip = this })
        timeline = StudioTimelineView(requireContext()).apply {
            onScrub = { showingPreview = true; playback.seek(document, it) }
            onClip = { id ->
                val audio = document.audio.firstOrNull { it.id == id }
                if (audio != null) editAudio(audio)
                else { canvas.selectedId = id; exitPreview(); refresh(); animationSheet() }
            }
            onRange = { id, start, end ->
                exitPreview()
                val audio = document.audio.firstOrNull { it.id == id }
                if (audio != null) {
                    val oldEnd = minOf(audio.startMs + audio.durationMs(), document.movieDurationMs())
                    val trimStart = if (end == oldEnd && start != audio.startMs) (audio.trimStartMs + start - audio.startMs).coerceAtLeast(0) else audio.trimStartMs
                    val trimEnd = (trimStart + end - start).coerceAtMost(audio.sourceDurationMs)
                    if (trimEnd > trimStart) attachAudio(audio.copy(startMs = start, trimStartMs = trimStart, trimEndMs = trimEnd))
                } else document.layers.firstOrNull { it.id == id && !it.locked }?.let { layer ->
                    val offset = document.sceneOffsetMs()
                    val from = (start - offset).coerceIn(0, document.video.durationMs - 100)
                    val until = (end - offset).coerceIn(from + 100, document.video.durationMs)
                    replaceLayer(layer.copy(animation = VideoEditRules.animation(layer.animation.copy(startMs = from,endMs = until),document.video.durationMs)))
                }
            }
        }
        timelinePanel.addView(ScrollView(requireContext()).apply { addView(timeline) }, LinearLayout.LayoutParams(-1, dp(if (resources.configuration.orientation == android.content.res.Configuration.ORIENTATION_LANDSCAPE) 76 else 76)))
        root.addView(timelinePanel)
        canvasControls = row().apply { gravity = Gravity.CENTER_VERTICAL; setPadding(dp(8), 0, dp(8), 0) }
        val zoomOut = button("−", "Zoom canvas out") { canvas.zoomBy(.8f) }.apply { setPadding(0, 0, 0, 0); minWidth = 0; minimumWidth = 0 }
        canvasControls.addView(zoomOut, LinearLayout.LayoutParams(dp(40), dp(48)))
        zoomButton = button("100% · Fit") { canvas.fitToScreen() }.apply { setPadding(dp(4), 0, dp(4), 0); minWidth = 0; minimumWidth = 0 }
        canvasControls.addView(zoomButton)
        val zoomIn = button("+", "Zoom canvas in") { canvas.zoomBy(1.25f) }.apply { setPadding(0, 0, 0, 0); minWidth = 0; minimumWidth = 0 }
        canvasControls.addView(zoomIn, LinearLayout.LayoutParams(dp(40), dp(48)))
        selection = label("", 12).apply { maxLines = 2; gravity = Gravity.CENTER }
        canvasControls.addView(selection, LinearLayout.LayoutParams(0, -2, 1f))
        modeButton = button("Pan", "Switch between canvas navigation and layer editing") {
            canvas.moveCanvas = !canvas.moveCanvas
            modeButton.text = if (canvas.moveCanvas) "Edit" else "Pan"
            modeButton.contentDescription = if (canvas.moveCanvas) "Return to layer editing" else "Pan and zoom the canvas"
            toast(if (canvas.moveCanvas) "Pinch to zoom, drag to pan the canvas" else "Drag a layer; pinch and twist to resize and rotate")
        }
        canvasControls.addView(modeButton)
        canvasControls.visibility = View.GONE
        root.addView(canvasControls)
        fontSizeControl = FontSizeControl(requireContext()).apply {
            onPreview = { size ->
                val layer = selected()?.takeIf { it.kind == "text" && !it.locked }
                if (layer != null) {
                    canvas.document = document.copy(layers = document.layers.map { if (it.id == layer.id) it.copy(size = size) else it })
                    canvas.invalidate()
                }
            }
            onCommit = { size -> selected()?.takeIf { it.kind == "text" && !it.locked }?.let { replaceLayer(it.copy(size = size)) } }
        }
        root.addView(fontSizeControl, LinearLayout.LayoutParams(-1, -2))
        val dock = column().apply {
            background = rounded(LuxeTheme.surface)
            elevation = dp(8).toFloat()
            setPadding(0, dp(8), 0, dp(8))
        }
        quickControls = EditorQuickControls(requireContext()).apply {
            onUndo = { history.undo()?.let { restore(it) } }
            onRedo = { history.redo()?.let { restore(it) } }
            onSize = { sizeSheet() }
            onPreview = { openFullPreview(activeTool == "Video") }
            onMode = { video -> showToolGroup(if (video) "Video" else "Text"); refresh() }
        }
        undoButton = quickControls.undo; redoButton = quickControls.redo
        dock.addView(quickControls, LinearLayout.LayoutParams(-1,dp(48)))
        dock.addView(View(requireContext()).apply { setBackgroundColor(LuxeTheme.border) }, LinearLayout.LayoutParams(-1,dp(1)))
        dock.addView(horizontal { toolActions = this }.apply {
            isHorizontalScrollBarEnabled = true; isScrollbarFadingEnabled = false
            contentDescription = "Editor tools · swipe horizontally for more icons"
        })
        root.addView(dock)
        showToolGroup(activeTool)
        return root
    }

    /** One tool family at a time keeps the canvas usable on small phones. */
    private fun showToolGroup(name: String) {
        exitPreview()
        activeTool = name
        toolActions.removeAllViews()
        if (::timelinePanel.isInitialized) timelinePanel.visibility = if (name == "Video") View.VISIBLE else View.GONE
        toolTabs.forEach { (key, tab) ->
            tab.setBackgroundColor(if (key == name) LuxeTheme.raised else Color.TRANSPARENT)
            tab.setTextColor(if (key == name) Color.WHITE else green)
            tab.isSelected = key == name
            tab.iconTint = android.content.res.ColorStateList.valueOf(if (key == name) Color.WHITE else green)
        }
        playbackRow.visibility = if (name == "Video") View.VISIBLE else View.GONE
        quickControls.bind(history.canUndo(), history.canRedo(), name == "Video")
        fontSizeControl.visibility = View.GONE
        fun action(title: String, click: () -> Unit) {
            val color = if (title in listOf("Effects", "Color")) green else ink
            val target = column().apply {
                gravity = Gravity.CENTER; setPadding(dp(3), dp(6), dp(3), dp(6))
                isClickable = true; isFocusable = true; contentDescription = title
                val ripple = android.util.TypedValue()
                requireContext().theme.resolveAttribute(android.R.attr.selectableItemBackgroundBorderless, ripple, true)
                if (ripple.resourceId != 0) setBackgroundResource(ripple.resourceId)
                setOnClickListener { click() }
                accessibilityDelegate = object : View.AccessibilityDelegate() {
                    override fun onInitializeAccessibilityNodeInfo(host: View, info: android.view.accessibility.AccessibilityNodeInfo) {
                        super.onInitializeAccessibilityNodeInfo(host, info); info.className = android.widget.Button::class.java.name
                    }
                }
            }
            target.addView(ImageView(requireContext()).apply { setImageDrawable(LuxeIcon(title,color)); importantForAccessibility = View.IMPORTANT_FOR_ACCESSIBILITY_NO }, LinearLayout.LayoutParams(dp(24),dp(24)))
            target.addView(label(title,11).apply {
                setTextColor(color); maxLines = 1; ellipsize = android.text.TextUtils.TruncateAt.END
                gravity = Gravity.CENTER; letterSpacing = 0f; setPadding(0,dp(6),0,0)
                importantForAccessibility = View.IMPORTANT_FOR_ACCESSIBILITY_NO
            }, LinearLayout.LayoutParams(-1,-2))
            toolActions.addView(target, LinearLayout.LayoutParams(dp(72), dp(64)))
        }
        when (name) {
            "Text", "Selection" -> {
                if (name == "Selection") action("Edit") { editSelected() } else action("Text") { textDialog(null) }
                action("Fonts") { fontsSheet() }
                action("Style") { styleSheet() }
                action("Color") { textStyleSheet("Color") }
                action("Effects") { effectsSheet() }
                action("Layers") { layersSheet() }
                action("Media") { showToolGroup("Media") }
                action("Brand") { brandKitSheet() }
                action("Poetry") { batchPoetrySheet() }
            }
            "Video" -> {
                action("Duration") { videoStudioSheet("Scene") }
                action("Scenes") { scenesSheet() }
                action("Animate") { animationSheet() }
                action("Weather") { videoStudioSheet("Weather") }
                action("Word sync") { wordSyncSheet() }
                action("Voice") { audioSheet() }
                action("Music") { audioSheet() }
                action("Text") { textDialog(null) }
                action("Media") { pickImage(false) }
                action("Captions") { importCaptions.launch("*/*") }
                action("Beats") { beatTimingSheet() }
                action("Templates") { videoTemplates() }
                action("Saved") { savedAnimations() }
                action("Layers") { layersSheet() }
            }
            "Media" -> {
                action("Media") { pickImage(false) }
                action("Style") { styleSheet() }
                action("Background") { backgroundSheet() }
                action("Layers") { layersSheet() }
                action("Brand") { brandKitSheet() }
            }
            else -> { action("Text") { showToolGroup("Text") }; action("Video") { showToolGroup("Video") } }
        }
        action("Canvas") { canvasOptionsSheet() }
        action("Project") { projectSheet() }
    }

    private fun canvasOptionsSheet() {
        sheet("Canvas") { content, dialog ->
            StudioMenu.section(content,"Layout", "${document.width} × ${document.height} px")
            StudioMenu.grid(content,listOf(
                StudioMenu.Action("Canvas size","Size","Aspect ratio") { dialog.dismiss(); sizeSheet() },
                StudioMenu.Action("Zoom / pan controls","Zoom","Move your view") { canvasControls.visibility = View.VISIBLE; dialog.dismiss() },
                StudioMenu.Action("Fit artwork to screen","Fit","Reset view") { canvas.fitToScreen(); dialog.dismiss() }
            ))
            StudioMenu.section(content,"Alignment guides")
            StudioMenu.toggle(content,"Grid","Composition guide",gridEnabled) { gridButton.performClick() }
            StudioMenu.toggle(content,"Snapping","Align layers to the center",snapEnabled) { snapButton.performClick() }
            StudioMenu.toggle(content,"Safe margins","Keep content inside the frame",document.safeMargins) { commit(document.copy(safeMargins = it)) }
        }
    }
    private fun projectSheet() {
        sheet("Project") { content, dialog ->
            StudioMenu.section(content,document.title,"Saved on this device · ${document.layers.size} layers")
            StudioMenu.grid(content,listOf(
                StudioMenu.Action("Rename project","Edit","Design title") { dialog.dismiss(); rename() },
                StudioMenu.Action("Save draft","Saved","Keep your changes") { if (saveNow()) toast("Draft saved"); dialog.dismiss() },
                StudioMenu.Action("Drafts","Project","Browse your designs") { dialog.dismiss(); draftsSheet() }
            ))
        }
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        refresh()
        scheduleSave()
        if (arguments?.getBoolean("startVideo") == true && savedInstanceState == null) showToolGroup("Video")
        viewLifecycleOwner.lifecycleScope.launch {
            viewLifecycleOwner.repeatOnLifecycle(Lifecycle.State.STARTED) {
                viewModel.uiState.collect { state ->
                    when (state) {
                        is TypeUiState.ImageUploaded -> toast("Poster backed up to Cloud")
                        is TypeUiState.Error -> toast("Poster saved locally. Cloud backup unavailable.")
                        else -> Unit
                    }
                }
            }
        }
        requireActivity().onBackPressedDispatcher.addCallback(viewLifecycleOwner) {
            saveNow(); findNavController().navigateUp()
        }
    }

    private fun openFullPreview(video: Boolean, previewDocument: StudioDocument = document, startAtMs: Long = 0, autoPlay: Boolean = false) {
        exitPreview(); fullPreview?.dismiss()
        fullPreview = FullScreenPreviewDialog(requireContext(),previewDocument,video,startAtMs,autoPlay).also { it.show() }
    }
    private fun exitPreview() {
        if (::playback.isInitialized) playback.pause()
        showingPreview = false
        if (::canvas.isInitialized) { canvas.previewTimeMs = null; canvas.document = document; canvas.invalidate() }
    }
    private fun refreshTimeline() {
        if (!::timeline.isInitialized) return
        val total = document.movieDurationMs()
        val offset = document.sceneOffsetMs()
        previewSeek.max = total.toInt()
        previewClock.text = String.format(java.util.Locale.ROOT, "%.1f / %.1fs", previewPosition / 1000f, total / 1000f)
        timeline.durationMs = total
        timeline.clips = document.layers.map { layer ->
            StudioTimelineView.Clip(layer.id, if (layer.kind == "text") layer.text.take(12) else "Image",
                offset + layer.animation.startMs.coerceAtMost(document.video.durationMs - 100),
                offset + layer.animation.endMs.coerceIn(100, document.video.durationMs), locked = layer.locked)
        } + document.audio.filter { it.startMs < total }.map { track ->
            val key = "${track.id}:${track.trimStartMs}:${track.trimEndMs}"
            if (waveforms[key] == null && waveformJobs[key] == null && view != null) {
                waveformJobs[key] = viewLifecycleOwner.lifecycleScope.launch {
                    val peaks = runCatching { withContext(Dispatchers.IO) {
                        StudioAudioEngine.waveform(track) { !isActiveJob() }
                    } }.getOrNull()
                    if (peaks != null && view != null) { waveforms[key] = peaks; refreshTimeline() }
                }
            }
            StudioTimelineView.Clip(track.id, track.role, track.startMs, minOf(total, track.startMs + track.durationMs()),
                audio = true, peaks = waveforms[key])
        }
        sceneStrip.removeAllViews()
        document.orderedScenes().forEachIndexed { index, scene ->
            val tile = column().apply {
                setPadding(dp(3)); background = GradientDrawable().apply {
                    setColor(LuxeTheme.surface); cornerRadius = dp(8).toFloat()
                    setStroke(dp(1), if (scene.id == document.sceneId) green else LuxeTheme.border)
                }
                contentDescription = "Edit scene ${index + 1} · ${scene.video.durationMs / 1000f} seconds"
            }
            tile.addView(ImageView(requireContext()).apply {
                scaleType = ImageView.ScaleType.CENTER_CROP
                if (activeTool == "Video") setImageBitmap(runCatching { StudioRenderer(requireContext(), fonts).bitmap(scene.applyTo(document), 96, false) }.getOrNull())
            }, LinearLayout.LayoutParams(dp(68), dp(36)))
            tile.addView(label("${index + 1} · ${scene.video.durationMs / 1000f}s", 10).apply { setTextColor(if (scene.id == document.sceneId) green else LuxeTheme.muted); setPadding(dp(1),dp(1),dp(1),dp(1)) })
            tile.setOnClickListener { commit(document.selectScene(scene.id)); canvas.selectedId = document.layers.lastOrNull()?.id; refresh() }
            sceneStrip.addView(tile, LinearLayout.LayoutParams(dp(76), -2).apply { setMargins(dp(3), dp(4), dp(3), dp(4)) })
        }
        sceneStrip.addView(button("+ Scene") { addScene(false) })
    }
    private fun isActiveJob(): Boolean = view != null && isAdded
    private fun addScene(duplicate: Boolean) {
        val all = document.orderedScenes()
        val remaining = 60000 - document.movieDurationMs()
        if (all.size >= 20 || remaining < 1000) { toast("Maximum 20 scenes and 60 seconds total"); return }
        val source = document.captureScene()
        val duration = minOf(if (duplicate) source.video.durationMs else 5000, remaining)
        val scene = source.copy(id = UUID.randomUUID().toString(), video = source.video.copy(durationMs = duration),
            layers = if (duplicate) source.layers.map { it.copy(id = UUID.randomUUID().toString(), animation = VideoEditRules.animation(it.animation,duration)) }
            else listOf(StudioLayer(text = "اپنا متن لکھیں", x = document.width / 2f, y = document.height / 2f,
                width = document.width * .8f, size = document.width * .07f,
                animation = LayerAnimation("fade", 0, duration, 1000))))
        commit(scene.applyTo(document).copy(scenes = all + scene))
        canvas.selectedId = document.layers.lastOrNull()?.id; refresh()
    }
    private fun scenesSheet() {
        sheet("Scenes") { content, dialog ->
            StudioMenu.section(content,"${document.orderedScenes().size} scenes · ${document.movieDurationMs()/1000f}s","Select a scene to edit. Complete video: up to 60 seconds.")
            StudioMenu.grid(content,listOf(
                StudioMenu.Action("Blank scene","Scenes") { dialog.dismiss(); addScene(false) },
                StudioMenu.Action("Duplicate scene","Duplicate") { dialog.dismiss(); addScene(true) },
                StudioMenu.Action("Batch poetry","Poetry") { dialog.dismiss(); batchPoetrySheet() }
            ))
            val all = document.orderedScenes()
            all.forEachIndexed { index, scene ->
                val card = column().apply { background = StudioMenu.surface(requireContext(),scene.id == document.sceneId); setPadding(dp(10)) }
                card.addView(StudioMenu.artwork(requireContext(),"Scene ${index+1}","${scene.video.durationMs/1000f}s · ${if (scene.id == document.sceneId) "Editing" else "Tap to edit"}",StudioRenderer(requireContext(),fonts).bitmap(scene.applyTo(document),240,false),scene.id == document.sceneId,height = 100) {
                    commit(document.selectScene(scene.id)); canvas.selectedId = document.layers.lastOrNull()?.id; refresh(); dialog.dismiss()
                })
                fun move(delta: Int) {
                    val target = index+delta; if (target !in all.indices) return
                    val reordered = all.toMutableList(); java.util.Collections.swap(reordered,index,target)
                    commit(document.copy(scenes = reordered)); dialog.dismiss(); scenesSheet()
                }
                StudioMenu.grid(card,listOf(
                    StudioMenu.Action("Duration","Duration","${scene.video.durationMs/1000f}s") { commit(document.selectScene(scene.id)); dialog.dismiss(); videoStudioSheet("Scene") },
                    StudioMenu.Action("Move earlier","Backward",enabled = index > 0) { move(-1) },
                    StudioMenu.Action("Move later","Forward",enabled = index < all.lastIndex) { move(1) }
                ))
                if (index > 0) {
                    StudioMenu.section(card,"Entrance transition")
                    var transition = scene.transition
                    val choices = column(); card.addView(choices)
                    fun drawChoices() {
                        choices.removeAllViews()
                        StudioMenu.grid(choices,listOf("none","fade","slide","dissolve").map { value ->
                            StudioMenu.Action(value.replaceFirstChar { it.uppercase() },if (value == "none") "Still" else "Animate",selected = value == transition) { transition = value; drawChoices() }
                        },4)
                    }
                    drawChoices()
                    var length = scene.transitionMs.coerceIn(100,2000)
                    StudioMenu.slider(card,"Transition length",length/1000f,.1f,2f,{ "%.1fs".format(java.util.Locale.ROOT,it) }) { length = (it*1000).toLong() }
                    StudioMenu.grid(card,listOf(StudioMenu.Action("Apply transition","Check") { commit(document.copy(scenes = document.orderedScenes().map { if (it.id == scene.id) it.copy(transition = transition,transitionMs = length) else it })); toast("Transition saved") }),1)
                }
                if (all.size > 1) StudioMenu.grid(card,listOf(StudioMenu.Action("Delete scene","Delete") {
                    AlertDialog.Builder(requireContext()).setTitle("Delete scene ${index+1}?").setNegativeButton("Cancel",null).setPositiveButton("Delete") { _,_ ->
                        val remaining = all.filterNot { it.id == scene.id }; val next = if (document.sceneId == scene.id) remaining.first().applyTo(document) else document
                        commit(next.copy(scenes = remaining)); dialog.dismiss(); scenesSheet()
                    }.show()
                }),1)
                content.addView(card,LinearLayout.LayoutParams(-1,-2).apply { bottomMargin = dp(12) })
            }
        }
    }
    private fun batchPoetrySheet() {
        val input = EditText(requireContext()).apply {
            hint = "ہر شعر کے درمیان ایک خالی سطر رکھیں"; minLines = 5; maxLines = 10
            inputType = InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_FLAG_MULTI_LINE
            filters = arrayOf(android.text.InputFilter.LengthFilter(12000))
        }
        val dialog = formInspector("Poetry → video scenes",input)
        dialog.body.addView(label("Separate verses with a blank line. Creates 1–12 scenes, 5 seconds each.",13))
        dialog.actions("Create video", {
            val verses = input.text.toString().trim().split(Regex("\\n\\s*\\n")).filter { it.isNotBlank() }
            if (verses.isEmpty() || verses.size > 12 || verses.any { it.length > 2000 }) { input.error = "Use 1–12 verses, each up to 2000 characters"; return@actions }
            if (!saveNow()) { message("Save the current draft before creating another project."); return@actions }
            val base = selected()?.takeIf { it.kind == "text" } ?: StudioLayer(x = document.width / 2f, y = document.height / 2f)
            val scenes = verses.map { verse -> document.captureScene().copy(id = UUID.randomUUID().toString(),
                video = document.video.copy(durationMs = 5000), layers = listOf(base.copy(id = UUID.randomUUID().toString(),
                    text = verse, locked = false, visible = true, animation = LayerAnimation("words", 0, 5000, 3500)))) }
            exitPreview()
            document = scenes.first().applyTo(document).copy(id = UUID.randomUUID().toString(), title = "Poetry reel", audio = emptyList(), scenes = scenes)
            history.reset(document); canvas.selectedId = document.layers.first().id; refresh(); scheduleSave(); showToolGroup("Video"); dialog.dismiss()
        }); dialog.show()
    }
    private fun beatTimingSheet() {
        val body = column().apply { setPadding(dp(16)) }
        val bpm = numberInput(90); val beats = numberInput(4)
        body.addView(label("Music BPM (enter the tempo)", 14)); body.addView(bpm)
        body.addView(label("Beats per scene", 14)); body.addView(beats)
        val dialog = formInspector("Beat timing",body)
        dialog.body.addView(label("Enter tempo manually. The beat grid begins at the music offset.",13))
        dialog.actions("Apply beats", {
            val tempo = bpm.text.toString().toIntOrNull(); val count = beats.text.toString().toIntOrNull()
            if (tempo == null || tempo !in 30..240 || count == null || count !in 1..32) { bpm.error = "BPM 30–240, beats 1–32"; return@actions }
            val duration = (60000L * count / tempo).coerceAtLeast(1000)
            val musicOffset = document.audio.firstOrNull { it.role == "music" }?.startMs ?: 0
            val all = document.orderedScenes().mapIndexed { index, scene ->
                val length = duration + if (index == 0) musicOffset else 0
                scene.copy(video = scene.video.copy(durationMs = length), layers = scene.layers.map { layer ->
                    layer.copy(animation = VideoEditRules.animation(layer.animation,length))
                })
            }
            if (all.sumOf { it.video.durationMs } > 60000) { beats.error = "Video exceeds 60 seconds; reduce beats per scene"; return@actions }
            commit(all.first { it.id == document.sceneId }.applyTo(document).copy(scenes = all)); dialog.dismiss()
        }); dialog.show()
    }
    private fun brandKitSheet() {
        val prefs = requireContext().getSharedPreferences("studio-brand", 0)
        sheet("Brand kit") { content, dialog ->
            StudioMenu.section(content,"Your signature style","Keep a favorite text style and logo on this device.")
            val style = selected()?.takeIf { it.kind == "text" } ?: StudioLayer()
            val font = prefs.getString("font",style.fontId) ?: style.fontId
            val sample = StudioDocument(width = 480,height = 220,backgroundColor = LuxeTheme.surface,gradientColor = LuxeTheme.surface,
                layers = listOf(style.copy(text = "آپ کا انداز",fontId = font,color = prefs.getInt("color",style.color),x = 240f,y = 110f,width = 400f,size = 56f,rotation = 0f)))
            content.addView(StudioMenu.artwork(requireContext(),"Brand typography",if (prefs.contains("font")) "Saved style" else "Save a style below",StudioRenderer(requireContext(),fonts).bitmap(sample,360,false),height = 100) {})
            StudioMenu.section(content,"Typography")
            StudioMenu.grid(content,listOf(
                StudioMenu.Action("Save text style","Saved","From selected text") {
                    val layer = selectedText() ?: return@Action
                    prefs.edit().putString("font",layer.fontId).putInt("color",layer.color).putFloat("size",layer.size).apply(); toast("Brand style saved")
                },
                StudioMenu.Action("Apply text style","Fonts","To selected text",enabled = prefs.contains("font")) {
                    val layer = selectedText() ?: return@Action
                    replaceLayer(layer.copy(fontId = prefs.getString("font","nastaliq") ?: "nastaliq",color = prefs.getInt("color",green),size = prefs.getFloat("size",76f))); dialog.dismiss()
                }
            ),2)
            StudioMenu.section(content,"Logo")
            StudioMenu.grid(content,listOf(
                StudioMenu.Action("Save logo","Saved","From selected image") {
                    val layer = selected()?.takeIf { it.kind == "image" } ?: run { toast("Select an image layer first"); return@Action }
                    prefs.edit().putString("logo",layer.imagePath).putFloat("logoRatio",layer.height/layer.width).apply(); toast("Logo saved")
                },
                StudioMenu.Action("Add my logo","Media","Place on artwork") {
                    val path = prefs.getString("logo","").orEmpty()
                    if (!File(path).isFile) { toast("Save an image layer as your logo first"); return@Action }
                    addLayer(StudioLayer(kind = "image",imagePath = path,width = document.width*.2f,height = document.width*.2f*prefs.getFloat("logoRatio",1f),x = document.width*.82f,y = document.height*.12f)); dialog.dismiss()
                }
            ),2)
        }
    }
    /** Preview changes never enter history or autosave until Apply. Dismiss always restores the document. */
    private fun previewSheet(title: String, build: (LinearLayout, BottomSheetDialog, (StudioDocument) -> Unit) -> Unit) {
        activeInspector?.dismiss(); exitPreview()
        var candidate = document
        val panel = StudioInspector(requireContext(), title, fonts)
        activeInspector = panel
        panel.tabBar.visibility = View.GONE
        panel.render(document)
        build(panel.body, panel) { next -> candidate = next; canvas.document = next; canvas.invalidate(); panel.render(next) }
        skinMenu(panel.body)
        panel.actions("Apply", { commit(candidate); panel.dismiss() })
        panel.setOnDismissListener { if (view != null) { canvas.document = document; canvas.invalidate() } }
        panel.show()
    }

    private fun selected(): StudioLayer? = document.layers.firstOrNull { it.id == canvas.selectedId }
    private fun selectedText(): StudioLayer? {
        val layer = selected()
        return if (layer == null || layer.kind != "text" || layer.locked) {
            toast(if (layer?.locked == true) "Unlock this layer in Layers first" else "Select a text layer first"); null
        } else layer
    }
    private fun commit(next: StudioDocument) {
        if (next.copy(updatedAt = document.updatedAt) == document) return
        if (next.movieDurationMs() !in 1000..60000 || next.orderedScenes().size > 20) {
            toast("Keep the complete video within 60 seconds and 20 scenes"); return
        }
        exitPreview()
        document = next.copy(updatedAt = maxOf(System.currentTimeMillis(), document.updatedAt + 1))
        history.record(document)
        refresh(); scheduleSave()
    }
    private fun restore(snapshot: StudioDocument) {
        exitPreview()
        document = snapshot.copy(updatedAt = maxOf(System.currentTimeMillis(), document.updatedAt + 1))
        refresh(); scheduleSave()
    }
    private fun replaceLayer(layer: StudioLayer) = commit(document.copy(layers = document.layers.map { if (it.id == layer.id) layer else it }))
    private fun addLayer(layer: StudioLayer) {
        if (document.layers.size >= 100) { toast("Maximum 100 layers per design"); return }
        canvas.selectedId = layer.id
        commit(document.copy(layers = document.layers + layer))
    }
    private fun refresh() {
        if (!::canvas.isInitialized) return
        canvas.document = document
        if (canvas.selectedId != null && document.layers.none { it.id == canvas.selectedId }) canvas.selectedId = document.layers.lastOrNull()?.id
        canvas.invalidate()
        val layer = selected()
        undoButton.alpha = if (history.canUndo()) 1f else .35f
        redoButton.alpha = if (history.canRedo()) 1f else .35f
        if (::fontSizeControl.isInitialized) fontSizeControl.showSelection(layer?.takeIf { it.kind == "text" }?.size, layer != null && !layer.locked && layer.visible)
        selection.text = when {
            layer == null -> "${document.width} × ${document.height} · Select a layer"
            layer.locked -> "Locked · use Layers to unlock"
            !layer.visible -> "Hidden · show it in Layers"
            layer.kind == "image" -> "Image · drag or resize"
            else -> (fonts.all().firstOrNull { it.id == layer.fontId }?.name ?: "Nastaliq") + " · ${layer.size.roundToInt()}"
        }
        undoButton.isEnabled = history.canUndo(); redoButton.isEnabled = history.canRedo()
        quickControls.bind(history.canUndo(),history.canRedo(),activeTool == "Video")
        refreshTimeline()
        (status.parent as? LinearLayout)?.let { (it.getChildAt(0) as? TextView)?.text = document.title }
    }
    private fun scheduleSave() {
        if (view == null) return
        saveJob?.cancel()
        status.text = "Saving…"
        val snapshot = document
        saveJob = viewLifecycleOwner.lifecycleScope.launch {
            delay(600)
            runCatching { withContext(Dispatchers.IO) { store.save(snapshot) } }
                .onSuccess { status.text = "Saved · device" }
                .onFailure { status.text = "Save failed · tap Save to retry" }
        }
    }
    private fun saveNow(): Boolean {
        if (::fontSizeControl.isInitialized) fontSizeControl.finishEditing()
        saveJob?.cancel()
        return runCatching { store.save(document) }
            .onSuccess { if (::status.isInitialized) status.text = "Saved · device" }
            .onFailure { if (::status.isInitialized) status.text = "Save failed · tap Save to retry" }
            .isSuccess
    }
    override fun onStop() {
        exitPreview()
        activeInspector?.dismiss(); activeInspector = null
        speechJob?.cancel(); speechDialog?.dismiss(); speechDialog = null
        animationPreview?.dismiss(); animationPreview = null
        fullPreview?.dismiss(); fullPreview = null
        videoProgressDialog?.dismiss()
        stopRecording(false); saveNow(); super.onStop()
    }
    override fun onSaveInstanceState(outState: Bundle) {
        saveNow(); outState.putString("documentId", document.id)
        outState.putString("pendingFontLayer", pendingFontLayer)
        outState.putBoolean("imageAsBackground", imageAsBackground)
        outState.putString("audioRole", audioRole)
        outState.putBoolean("gridEnabled", gridEnabled); outState.putBoolean("snapEnabled", snapEnabled)
        outState.putString("activeTool", activeTool)
        super.onSaveInstanceState(outState)
    }
    override fun onDestroyView() {
        saveJob?.cancel(); waveformJobs.values.forEach { it.cancel() }; waveformJobs.clear()
        if (::playback.isInitialized) playback.release()
        toolTabs.clear()
        super.onDestroyView()
    }

    private fun editSelected() {
        val layer = selected() ?: return
        if (layer.locked) { toast("Unlock this layer first"); return }
        if (layer.kind == "text") textDialog(layer) else styleSheet()
    }
    private fun textDialog(layer: StudioLayer?) {
        val input = EditText(requireContext()).apply {
            setText(layer?.text.orEmpty()); hint = "یہاں اپنا متن لکھیں"
            typeface = fonts.resolve(layer?.fontId ?: "nastaliq")
            textSize = 22f; minLines = 3; maxLines = 8; gravity = Gravity.TOP
            inputType = InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_FLAG_MULTI_LINE
            filters = arrayOf(android.text.InputFilter.LengthFilter(2000)); setPadding(dp(16))
        }
        activeInspector?.dismiss(); exitPreview()
        val panel = StudioInspector(requireContext(), if (layer == null) "Add text" else "Edit text", fonts)
        activeInspector = panel
        panel.tabBar.visibility = View.GONE
        input.setTextColor(LuxeTheme.text); input.setHintTextColor(LuxeTheme.muted)
        input.background = panel.shape(); panel.body.addView(input, LinearLayout.LayoutParams(-1, -2))
        panel.body.addView(panel.text("Urdu and English · up to 2,000 characters", 12))
        fun draft(): StudioDocument {
            val value = input.text.toString()
            return if (layer != null) document.copy(layers = document.layers.map { if (it.id == layer.id) it.copy(text = value,animation = if (value != layer.text) it.animation.copy(wordCues = emptyList(),voiceSignature = "") else it.animation) else it })
            else document.copy(layers = document.layers + StudioLayer(text = value, x = document.width / 2f, y = document.height / 2f,
                width = document.width * .8f, size = minOf(document.width, document.height) * .07f))
        }
        input.addTextChangedListener(object : android.text.TextWatcher {
            override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) = Unit
            override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) { panel.render(draft()) }
            override fun afterTextChanged(s: android.text.Editable?) = Unit
        })
        panel.render(if (layer == null) draft() else document)
        panel.actions("Apply text", {
            if (input.text.isNullOrBlank()) { input.error = "Enter some text"; return@actions }
            if (layer == null && document.layers.size >= 100) { input.error = "Maximum 100 layers"; return@actions }
            val next = draft(); if (layer == null) canvas.selectedId = next.layers.last().id
            commit(next); panel.dismiss()
        })
        panel.setOnDismissListener { if (view != null) { canvas.document = document; canvas.invalidate() } }
        panel.show()
    }
    private fun fontsSheet() {
        val layer = selectedText() ?: return
        activeInspector?.dismiss(); exitPreview()
        val inspector = FontLibraryInspector(requireContext(),fonts,document,layer,
            preview = { next -> canvas.document = next; canvas.invalidate() },
            apply = { next -> document.layers.firstOrNull { it.id == next.id && !it.locked }?.let { replaceLayer(next) } },
            importFont = { pendingFontLayer = layer.id; importFont.launch(arrayOf("*/*")) },
            closed = { restoreInspectorCanvas() })
        activeInspector = inspector.panel; inspector.show()
    }
    private fun textStyleSheet(tab: String) {
        val layer = selectedText() ?: return
        activeInspector?.dismiss(); exitPreview()
        val inspector = TextStylingInspector(requireContext(),fonts,document,layer,tab,
            preview = { next -> canvas.document = next; canvas.invalidate() },
            apply = { next -> document.layers.firstOrNull { it.id == next.id && !it.locked }?.let { replaceLayer(next) } },
            closed = { restoreInspectorCanvas() })
        activeInspector = inspector.panel; inspector.show()
    }
    private fun restoreInspectorCanvas() {
        if (view != null) { canvas.document = document; canvas.previewTimeMs = null; canvas.invalidate() }
    }
    private fun videoStudioSheet(tab: String) {
        activeInspector?.dismiss(); exitPreview()
        val inspector = VideoStudioInspector(requireContext(),fonts,document,canvas.selectedId,tab,
            preview = { next,time -> canvas.document = next; canvas.previewTimeMs = time; canvas.invalidate() },
            apply = { next -> commit(next) },
            fullPreview = { next -> openFullPreview(true,next) },
            external = { action -> when(action) {
                "record" -> microphonePermission.launch(android.Manifest.permission.RECORD_AUDIO)
                "voice" -> { audioRole = "voice"; importAudio.launch("audio/*") }
                "music" -> { audioRole = "music"; importAudio.launch("audio/*") }
                "tts" -> generateVoice()
                "scenes" -> scenesSheet()
                "templates" -> videoTemplates()
                "saved" -> savedAnimations()
                else -> Unit
            } },
            export = { next,format,share -> commit(next); exportMotion(format,share) },
            closed = { restoreInspectorCanvas() },
            wordSync = { next -> wordSyncSheet(next) })
        activeInspector = inspector.panel; inspector.show()
    }
    private fun wordSyncSheet(source: StudioDocument = document) {
        activeInspector?.dismiss(); exitPreview()
        val inspector = OfflineSpeechInspector(requireContext(),fonts,source,canvas.selectedId,
            preview = { next,time -> canvas.document = next; canvas.previewTimeMs = time; canvas.invalidate() },
            apply = { next -> commit(next) },
            fullPreview = { next,time,play -> openFullPreview(true,next,time,play) },
            importModel = { importSpeechModel.launch(arrayOf("*/*")) },
            addVoice = { audioSheet() },
            closed = { restoreInspectorCanvas() })
        activeInspector = inspector.panel; inspector.show()
    }
    private fun styleSheet() {
        if (selected()?.kind == "text") textStyleSheet("Style") else imageStyleSheet()
    }
    private fun imageStyleSheet() {
        var layer = selected() ?: run { toast("Select a layer first"); return }
        if (layer.locked) { toast("Unlock this layer first"); return }
        previewSheet("Layer style · live preview") { content, _, preview ->
            fun update(next: StudioLayer) {
                layer = next
                preview(document.copy(layers = document.layers.map { if (it.id == layer.id) next else it }))
            }
            slider(content, "Box width", layer.width, 40f, document.width * 1.5f, live = true) { update(layer.copy(width = it)) }
            StudioMenu.toggle(content,"Fill crop frame","Preserve image proportions",layer.cropImage) { update(layer.copy(cropImage = it)) }
            if (layer.kind == "image") slider(content, "Crop frame height", layer.height, 40f, document.height * 1.5f, live = true) { update(layer.copy(height = it)) }
            slider(content, "Rotation", layer.rotation, 0f, 359f, live = true) { update(layer.copy(rotation = it)) }
            slider(content, "Opacity", layer.opacity, .05f, 1f, live = true) { update(layer.copy(opacity = it)) }
            content.addView(button("Center on canvas") { update(layer.copy(x = document.width / 2f, y = document.height / 2f)) })
        }
    }
    private fun effectsSheet() = textStyleSheet("Effects")
    private fun colorPicker(initial: Int, preview: ((Int) -> Unit)? = null, onPick: (Int) -> Unit) {
        val body = column().apply { setPadding(dp(20)) }
        val input = EditText(requireContext()).apply { setText(String.format("#%08X", initial)); hint = "#RRGGBB or #AARRGGBB"; isSingleLine = true }
        body.addView(input)
        input.addTextChangedListener(object : android.text.TextWatcher {
            override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) = Unit
            override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {
                runCatching { Color.parseColor(s.toString().trim()) }.getOrNull()?.let { preview?.invoke(it) }
            }
            override fun afterTextChanged(s: android.text.Editable?) = Unit
        })
        val colors = listOf(0xff123e34.toInt(), 0xfff8f3e8.toInt(), Color.WHITE, Color.BLACK, 0xffd1a74e.toInt(), 0xffbd6b52.toInt(), 0xff426bd4.toInt(), 0xffdb5579.toInt())
        StudioMenu.section(body,"Palette","Tap a color or enter its HEX code.")
        StudioMenu.views(body,colors.map { color ->
            column().apply {
                gravity = Gravity.CENTER; setPadding(dp(6),dp(8),dp(6),dp(8)); background = StudioMenu.surface(requireContext())
                addView(View(requireContext()).apply { background = GradientDrawable().apply { shape = GradientDrawable.OVAL; setColor(color); setStroke(dp(1),LuxeTheme.border) } },LinearLayout.LayoutParams(dp(36),dp(36)))
                addView(label(String.format("#%06X",color and 0xffffff),10).apply { gravity = Gravity.CENTER })
                contentDescription = String.format("Choose color #%06X",color and 0xffffff); isClickable = true; isFocusable = true
                setOnClickListener { input.setText(String.format("#%08X",color)) }
            }
        },4)
        val dialog = formInspector("Choose color",body)
        dialog.actions("Apply color", {
            val color = runCatching { Color.parseColor(input.text.toString().trim()) }.getOrNull()
            if (color == null) input.error = "Enter a valid hex color"
            else { onPick(color); dialog.dismiss() }
        })
        dialog.show()
    }
    private fun pickImage(background: Boolean) { imageAsBackground = background; importImage.launch("image/*") }
    private fun backgroundSheet() {
        var candidate = document
        previewSheet("Background") { content, dialog, preview ->
            fun update(next: StudioDocument) { candidate = next; preview(next) }
            StudioMenu.section(content,"Your background")
            StudioMenu.grid(content,listOf(
                StudioMenu.Action("Gallery","Media","Use your photo") { dialog.dismiss(); pickImage(true) },
                StudioMenu.Action("Solid color","Color","One color") { dialog.dismiss(); colorPicker(document.backgroundColor) { commit(document.copy(backgroundColor = it,gradientColor = it,backgroundStyle = "",backgroundImage = "",backgroundName = "",backgroundVisible = true)) } },
                StudioMenu.Action("Gradient","Gradient","Two colors") { dialog.dismiss(); gradientBackgroundSheet() }
            ))
            StudioMenu.toggle(content,"Transparent background","Export as PNG to keep transparency",!document.backgroundVisible) { value -> update(candidate.copy(backgroundVisible = !value)) }
            StudioMenu.section(content,"Studio collection","Tap artwork to preview. Apply keeps it.")
            val thumbnails = BACKGROUND_ENTRIES.map { entry ->
                val sample = document.copy(backgroundStyle = entry.styleName,backgroundImage = "",backgroundName = "",backgroundVisible = true,layers = emptyList())
                StudioMenu.artwork(requireContext(),entry.label,"",StudioRenderer(requireContext(),fonts).bitmap(sample,240,false),entry.styleName == document.backgroundStyle) {
                    update(candidate.copy(backgroundStyle = entry.styleName,backgroundImage = "",backgroundName = "",backgroundVisible = true))
                }
            }
            StudioMenu.views(content,thumbnails)
        }
    }
    private fun gradientBackgroundSheet() {
        var start = document.backgroundColor; var end = document.gradientColor
        previewSheet("Gradient background") { content, _, preview ->
            StudioMenu.section(content,"Choose a palette")
            val palettes = listOf("Midnight" to (0xff08121e.toInt() to 0xff304563.toInt()),"Sunset" to (0xff421b46.toInt() to 0xffd18d61.toInt()),"Forest" to (0xff092f2d.toInt() to 0xff497d60.toInt()),"Ivory" to (0xffe6d4af.toInt() to 0xfffffaf0.toInt()))
            fun update() { preview(document.copy(backgroundColor = start,gradientColor = end,backgroundStyle = "",backgroundImage = "",backgroundName = "",backgroundVisible = true)) }
            StudioMenu.views(content,palettes.map { (name,colors) ->
                val sample = StudioDocument(width = 320,height = 180,backgroundColor = colors.first,gradientColor = colors.second,layers = emptyList())
                StudioMenu.artwork(requireContext(),name,"",StudioRenderer(requireContext(),fonts).bitmap(sample,240,false),height = 64) { start = colors.first; end = colors.second; update() }
            })
            StudioMenu.section(content,"Custom colors","Use #RRGGBB or #AARRGGBB. Apply keeps the gradient.")
            val startInput = EditText(requireContext()).apply { setText(String.format("#%08X",start)); isSingleLine = true; contentDescription = "Gradient start hex" }
            val endInput = EditText(requireContext()).apply { setText(String.format("#%08X",end)); isSingleLine = true; contentDescription = "Gradient end hex" }
            content.addView(label("Start color",12)); content.addView(startInput)
            content.addView(label("End color",12)); content.addView(endInput)
            listOf(startInput,endInput).forEach { input -> input.addTextChangedListener(object : android.text.TextWatcher {
                override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) = Unit
                override fun afterTextChanged(s: android.text.Editable?) = Unit
                override fun onTextChanged(s: CharSequence?, at: Int, before: Int, count: Int) {
                    val first = runCatching { Color.parseColor(startInput.text.toString().trim()) }.getOrNull()
                    val second = runCatching { Color.parseColor(endInput.text.toString().trim()) }.getOrNull()
                    if (first != null && second != null) { start = first; end = second; update() }
                }
            }) }
            skinMenu(content)
        }
    }
    private fun layersSheet() {
        sheet("Layers") { content, dialog ->
            StudioMenu.section(content,"${document.layers.size} layers","Top layer appears first. Tap a layer to select it.")
            document.layers.asReversed().forEach { layer ->
                val card = column().apply { setPadding(dp(10)); background = StudioMenu.surface(requireContext(),layer.id == canvas.selectedId) }
                val header = row().apply { gravity = Gravity.CENTER_VERTICAL; minimumHeight = dp(64); contentDescription = "Select layer ${layer.id}"; isClickable = true; isFocusable = true
                    setOnClickListener { canvas.selectedId = layer.id; if (activeTool != "Video") showToolGroup(if (layer.kind == "image") "Media" else "Selection"); refresh(); dialog.dismiss() }
                }
                header.addView(ImageView(requireContext()).apply {
                    scaleType = ImageView.ScaleType.CENTER_CROP; background = rounded(LuxeTheme.background)
                    setImageBitmap(StudioRenderer(requireContext(),fonts).bitmap(document.copy(layers = listOf(layer.copy(visible = true))),96,false))
                },LinearLayout.LayoutParams(dp(48),dp(56)))
                header.addView(column().apply {
                    addView(label((if (layer.id == canvas.selectedId) "✓ " else "")+if (layer.kind == "image") "Image layer" else layer.text.take(48),14).apply { maxLines = 2 })
                    addView(label("${if (layer.locked) "Locked" else "Editable"} · ${if (layer.visible) "Visible" else "Hidden"}",11).apply { setTextColor(LuxeTheme.muted) })
                },LinearLayout.LayoutParams(0,-2,1f).apply { marginStart = dp(8) })
                card.addView(header)
                fun refreshPanel() { dialog.dismiss(); layersSheet() }
                StudioMenu.grid(card,listOf(
                    StudioMenu.Action(if (layer.visible) "Hide" else "Show","Visibility",selected = !layer.visible) { replaceLayer(layer.copy(visible = !layer.visible)); refreshPanel() },
                    StudioMenu.Action(if (layer.locked) "Unlock" else "Lock","Lock",selected = layer.locked) { replaceLayer(layer.copy(locked = !layer.locked)); refreshPanel() },
                    StudioMenu.Action("Duplicate","Duplicate") { duplicateLayer(layer); refreshPanel() },
                    StudioMenu.Action("Forward","Forward",enabled = !layer.locked && document.layers.last().id != layer.id) { reorder(layer,1); refreshPanel() },
                    StudioMenu.Action("Back","Backward",enabled = !layer.locked && document.layers.first().id != layer.id) { reorder(layer,-1); refreshPanel() },
                    StudioMenu.Action("Center","Center",enabled = !layer.locked) { replaceLayer(layer.copy(x = document.width/2f,y = document.height/2f)); refreshPanel() },
                    StudioMenu.Action("Delete layer","Delete",enabled = !layer.locked) { commit(document.copy(layers = document.layers.filterNot { it.id == layer.id })); refreshPanel() }
                ),4,compact = true)
                content.addView(card,LinearLayout.LayoutParams(-1,-2).apply { bottomMargin = dp(12) })
            }
            StudioMenu.grid(content,listOf(StudioMenu.Action("Background","Background",if (document.backgroundVisible) "Visible" else "Transparent") { dialog.dismiss(); backgroundSheet() }),1)
            if (document.layers.isEmpty()) content.addView(label("No layers yet. Add text or an image from the toolbar.",14))
        }
    }
    private fun duplicateLayer(layer: StudioLayer) {
        addLayer(layer.copy(id = UUID.randomUUID().toString(), locked = false, visible = true,
            x = (layer.x + 30).coerceAtMost(document.width.toFloat()), y = (layer.y + 30).coerceAtMost(document.height.toFloat())))
    }
    private fun reorder(layer: StudioLayer, delta: Int) {
        val layers = document.layers.toMutableList()
        val index = layers.indexOfFirst { it.id == layer.id }
        val next = (index + delta).coerceIn(0, layers.lastIndex)
        layers.removeAt(index); layers.add(next, layer); commit(document.copy(layers = layers))
    }
    private fun sizeSheet() {
        val presets = listOf("Square" to (1080 to 1080), "Portrait post" to (1080 to 1350), "Poetry portrait" to (1080 to 1620), "Story / Status" to (1080 to 1920), "Landscape" to (1920 to 1080))
        sheet("Canvas size") { content, dialog ->
            content.addView(label("Current: ${document.width} × ${document.height} px. All scenes scale together. Undo restores the previous size.", 13))
            StudioMenu.views(content,presets.map { (name, dimensions) ->
                val (w,h) = dimensions
                StudioMenu.ratio(requireContext(),name,w,h,document.width == w && document.height == h) {
                    commit(document.resized(w,h)); canvas.fitToScreen(); dialog.dismiss(); toast("Canvas: $w × $h")
                }
            },3)
            content.addView(button("Custom size…") {
                dialog.dismiss()
                val body = column().apply { setPadding(dp(20)) }
                val width = numberInput(document.width); val height = numberInput(document.height)
                body.addView(label("Width · 320–4096 pixels",14)); body.addView(width)
                body.addView(label("Height · 320–4096 pixels",14)); body.addView(height)
                val custom = formInspector("Custom canvas",body)
                custom.actions("Apply size", {
                    val w = width.text.toString().toIntOrNull(); val h = height.text.toString().toIntOrNull()
                    if (w == null || w !in 320..4096) { width.error = "Enter 320–4096"; return@actions }
                    if (h == null || h !in 320..4096) { height.error = "Enter 320–4096"; return@actions }
                    commit(document.resized(w,h)); canvas.fitToScreen(); custom.dismiss()
                }); custom.show()
            })
        }
    }
    private fun rename() {
        val input = EditText(requireContext()).apply { setText(document.title); isSingleLine = true; filters = arrayOf(android.text.InputFilter.LengthFilter(60)) }
        val panel = formInspector("Design name",input)
        panel.actions("Save name", { commit(document.copy(title = input.text.toString().trim().ifBlank { "Untitled design" })); panel.dismiss() })
        panel.show()
    }
    private fun draftsSheet() {
        saveNow()
        sheet("Saved designs") { content, dialog ->
            StudioMenu.grid(content,listOf(StudioMenu.Action("New blank design","Text","Start fresh") {
                if (!saveNow()) return@Action
                document = StudioDocument(); history.reset(document); canvas.selectedId = document.layers.firstOrNull()?.id; canvas.fitToScreen(); refresh(); scheduleSave(); dialog.dismiss()
            }),1)
            StudioMenu.section(content,"Your designs")
            StudioMenu.views(content,store.list().map { draft ->
                StudioMenu.artwork(requireContext(),draft.title,"${draft.width} × ${draft.height}",runCatching { StudioRenderer(requireContext(),fonts).bitmap(draft,240,false) }.getOrNull(),draft.id == document.id) {
                    if (saveNow()) { document = draft; history.reset(document); canvas.selectedId = draft.layers.lastOrNull()?.id; canvas.fitToScreen(); refresh(); dialog.dismiss() }
                }
            })
        }
    }
    private fun exportSheet() {
        if (exporting) return
        saveNow()
        sheet("Export design",showDone = false) { content, dialog ->
            val panel = dialog as StudioInspector
            var format = 0; var quality = 95; var transparent = !document.backgroundVisible
            val width = numberInput(document.width).apply { contentDescription = "Image export width" }
            val summary = label("",13).apply { setTextColor(LuxeTheme.muted) }
            fun dimensions(): Pair<Int,Long>? {
                val w = width.text.toString().toIntOrNull() ?: return null
                val h = w.toLong()*document.height/document.width
                return if (w in 320..4096 && h in 1..4096 && w.toLong()*h <= 8_000_000) w to h else null
            }
            fun updateSummary() {
                val size = dimensions()
                summary.text = if (size == null) "Choose up to 4096 px per side and 8 megapixels." else "${size.first} × ${size.second} px · ${if (format == 0) "PNG" else "JPG"} · ${if (format == 0 && transparent) "Transparent" else "With background"}"
            }
            StudioMenu.section(content,"File format")
            val formats = column(); content.addView(formats)
            val options = column()
            fun drawOptions() {
                options.removeAllViews()
                if (format == 0) StudioMenu.toggle(options,"Transparent background","Remove the background in the saved PNG",transparent) { transparent = it; updateSummary() }
                else StudioMenu.slider(options,"JPG quality",quality.toFloat(),60f,100f,{ "${it.roundToInt()}%" }) { quality = it.roundToInt() }
            }
            fun drawFormats() {
                formats.removeAllViews()
                StudioMenu.grid(formats,listOf(
                    StudioMenu.Action("PNG","Image","Lossless · transparency",selected = format == 0) { format = 0; drawFormats(); drawOptions(); updateSummary() },
                    StudioMenu.Action("JPG","Image","Smaller file",selected = format == 1) { format = 1; drawFormats(); drawOptions(); updateSummary() }
                ),2)
            }
            drawFormats()
            StudioMenu.section(content,"Resolution")
            StudioMenu.grid(content,listOf(720,1080,2160).map { w -> StudioMenu.Action("${w}px","Size",if (w == 1080) "Recommended" else "Width",enabled = w.toLong()*(w.toLong()*document.height/document.width) <= 8_000_000 && w.toLong()*document.height/document.width <= 4096) { width.setText(w.toString()) } })
            content.addView(width); content.addView(options); drawOptions(); content.addView(summary)
            width.addTextChangedListener(object : android.text.TextWatcher {
                override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) = Unit
                override fun afterTextChanged(s: android.text.Editable?) = Unit
                override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) { updateSummary() }
            }); updateSummary()
            fun start(share: Boolean) {
                val size = dimensions() ?: run { width.error = "Maximum 8 MP and 4096 px per side"; return }
                dialog.dismiss(); export(size.first,format == 0,format == 0 && transparent,quality,share)
            }
            StudioMenu.section(content,"Other options")
            StudioMenu.grid(content,listOf(
                StudioMenu.Action("Share image","Share","Choose an app") { start(true) },
                StudioMenu.Action("Video / GIF","Video","Animate this design") { dialog.dismiss(); motionExportSheet() }
            ),2)
            panel.actions("Save image", { start(false) },preview = {
                if (dimensions() == null) width.error = "Choose a valid output size"
                else {
                    val snapshot = StudioImageExportRules.snapshot(document,format == 0,transparent)
                    openFullPreview(false,snapshot)
                }
            })
        }
    }
    private fun export(width: Int, png: Boolean, transparent: Boolean, quality: Int, share: Boolean) {
        exporting = true; exportButton.isEnabled = false; status.text = "Exporting…"
        val snapshot = StudioImageExportRules.snapshot(document,png,transparent)
        val context = requireContext().applicationContext
        lifecycleScope.launch {
            val result = runCatching { withContext(Dispatchers.IO) {
                val renderer = StudioRenderer(context, StudioFonts(context))
                val bitmap = renderer.bitmap(snapshot, width, transparent && png)
                val directory = File(context.getExternalFilesDir(Environment.DIRECTORY_PICTURES) ?: context.filesDir, "Studio").apply { check(isDirectory || mkdirs()) }
                val file = File(directory, "UT${System.currentTimeMillis()}_${UUID.randomUUID().toString().take(6)}.${if (png) "png" else "jpg"}")
                try {
                    // JPG cannot carry alpha: composite hidden/transparent backgrounds onto white.
                    val output = if (png) bitmap else Bitmap.createBitmap(bitmap.width, bitmap.height, Bitmap.Config.ARGB_8888).apply {
                        android.graphics.Canvas(this).apply { drawColor(Color.WHITE); drawBitmap(bitmap, 0f, 0f, null) }
                    }
                    try { file.outputStream().use { check(output.compress(if (png) Bitmap.CompressFormat.PNG else Bitmap.CompressFormat.JPEG, quality, it)) } }
                    finally { if (output !== bitmap) output.recycle() }
                } catch (error: Exception) { file.delete(); throw error }
                finally { bitmap.recycle() }
                var gallerySaved = false
                if (!share && Build.VERSION.SDK_INT >= 29) {
                    val values = ContentValues().apply {
                        put(MediaStore.Images.Media.DISPLAY_NAME, file.name)
                        put(MediaStore.Images.Media.MIME_TYPE, if (png) "image/png" else "image/jpeg")
                        put(MediaStore.Images.Media.RELATIVE_PATH, "Pictures/UrduTyper")
                        put(MediaStore.Images.Media.IS_PENDING, 1)
                    }
                    val resolver = context.contentResolver
                    val uri = runCatching { resolver.insert(MediaStore.Images.Media.EXTERNAL_CONTENT_URI, values) }.getOrNull()
                    if (uri != null) {
                        runCatching {
                            checkNotNull(resolver.openOutputStream(uri)).use { out -> file.inputStream().use { it.copyTo(out) } }
                            values.clear(); values.put(MediaStore.Images.Media.IS_PENDING, 0)
                            resolver.update(uri, values, null, null); gallerySaved = true
                        }.onFailure { runCatching { resolver.delete(uri, null, null) } }
                    }
                } else if (!share) {
                    // Older Android saves to the app's Pictures directory; sharing requires no storage permission.
                    android.media.MediaScannerConnection.scanFile(context, arrayOf(file.path), arrayOf(if (png) "image/png" else "image/jpeg"), null)
                }
                file to gallerySaved
            } }
            exporting = false
            if (view == null) return@launch
            exportButton.isEnabled = true
            result.onSuccess { (file, gallerySaved) ->
                status.text = "Draft saved · export complete"
                viewModel.uploadImage(file)
                if (share) {
                    val uri = FileProvider.getUriForFile(context, context.packageName + ".provider", file)
                    startActivity(Intent.createChooser(Intent(Intent.ACTION_SEND).apply {
                        type = if (png) "image/png" else "image/jpeg"
                        putExtra(Intent.EXTRA_STREAM, uri); clipData = ClipData.newRawUri("Design", uri)
                        addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                    }, "Share design"))
                } else message(if (gallerySaved) "Saved to Gallery → Pictures/UrduTyper and Local posters." else "Saved to Local posters. Use Share to send or save it to another app.")
            }.onFailure { status.text = "Export failed · draft is safe"; message(it.message ?: "Could not export. Try a smaller output size.") }
        }
    }
    private fun secondsInput(ms: Long) = EditText(requireContext()).apply {
        inputType = InputType.TYPE_CLASS_NUMBER or InputType.TYPE_NUMBER_FLAG_DECIMAL
        setText(String.format(java.util.Locale.ROOT, "%.1f", ms / 1000f)); isSingleLine = true
    }
    private fun milliseconds(input: EditText): Long? = input.text.toString().toDoubleOrNull()
        ?.takeIf { it.isFinite() && it >= 0 && it <= 86400 }?.let { (it * 1000).toLong() }

    private fun animationSheet() = videoStudioSheet("Animate")
    private fun videoTemplates() {
        val names = arrayOf("Poetry reel", "Animated quote", "Birthday greeting", "Invitation")
        val phrases = listOf("خوابوں کو حقیقت بناؤ", "ہر دن ایک نئی امید ہے", "سالگرہ مبارک", "آپ کی آمد ہمارے لیے باعثِ مسرت ہوگی")
        sheet("Video templates") { content, dialog ->
            StudioMenu.section(content,"Start with a scene","Your current project is saved before opening a template.")
            StudioMenu.views(content,names.mapIndexed { which, name ->
                val style = listOf("LUXE_MOONLIT_LAKE","LUXE_SUNSET_PEAK","LUXE_LANTERN_NIGHT","LUXE_MOSQUE_SUNSET")[which]
                val text = StudioLayer(text = phrases[which],x = 540f,y = 960f,width = 900f,color = Color.WHITE,
                    animation = LayerAnimation(if (which == 0) "words" else "typing",500,10000,5500,which != 0))
                val sample = StudioDocument(title = name,width = 1080,height = 1920,backgroundStyle = style,layers = listOf(text),
                    video = VideoSettings(10000,"zoom",if (which >= 2) "particles" else "none"))
                StudioMenu.artwork(requireContext(),name,"10s · ${if (which == 0) "Word reveal" else "Typing"}",StudioRenderer(requireContext(),fonts).bitmap(sample,240,false),height = 140) {
                    if (saveNow()) {
                        document = sample; dialog.dismiss(); exitPreview(); history.reset(document); canvas.selectedId = text.id; canvas.fitToScreen(); refresh(); scheduleSave(); animationSheet()
                    } else message("Save the current draft before starting a video template.")
                }
            })
        }
    }
    private fun timelineSheet() {
        showToolGroup("Video"); timelinePanel.visibility = View.VISIBLE
        toast("Drag clip edges to trim; drag the middle to move. Tap a clip for exact settings.")
    }
    private fun attachAudio(track: StudioAudio) {
        commit(document.copy(audio = document.audio.filterNot { it.role == track.role } + track))
    }
    private fun audioSheet() = videoStudioSheet("Audio")
    private fun generateVoice() {
        val text = selected()?.takeIf { it.kind == "text" }?.text
            ?: document.layers.filter { it.kind == "text" && it.visible }.joinToString("\n") { it.text }
        if (text.isBlank()) { message("Add some Urdu text first."); return }
        speechDialog = AlertDialog.Builder(requireContext()).setTitle("Creating Urdu voice…")
            .setMessage("Uses your phone's text-to-speech engine. Urdu voice availability and network requirements depend on that engine.")
            .setCancelable(false).setNegativeButton("Cancel") { _, _ -> speechJob?.cancel() }.show()
        val directory = File(requireContext().filesDir,"studio-audio").apply { mkdirs() }
        val file = File(directory,"${UUID.randomUUID()}.wav")
        speechJob = lifecycleScope.launch {
            runCatching {
                StudioSpeech.synthesize(requireContext(),text,file)
                val duration = withContext(Dispatchers.IO) { audioDuration(file) }
                val track = StudioAudio(path = file.path,name = "Urdu generated voice",role = "voice",sourceDurationMs = duration,trimEndMs = minOf(duration,60000))
                commit(VideoEditRules.duration(document,maxOf(document.video.durationMs,minOf(duration+1000,VideoEditRules.maximumSceneDuration(document)))).copy(
                    audio = document.audio.filterNot { it.role == "voice" } + track))
            }.onFailure {
                file.delete()
                if (it !is kotlinx.coroutines.CancellationException && view != null) message(it.message ?: "Could not generate Urdu voice")
            }.onSuccess { if (view != null) toast("Urdu voice added. Set layer timings to match it.") }
            speechDialog?.dismiss(); speechDialog = null; speechJob = null
        }
    }
    private fun editAudio(track: StudioAudio) { videoStudioSheet("Audio") }
    private fun audioDuration(file: File): Long {
        val metadata = android.media.MediaMetadataRetriever()
        try {
            metadata.setDataSource(file.path)
            return metadata.extractMetadata(android.media.MediaMetadataRetriever.METADATA_KEY_DURATION)?.toLongOrNull()?.takeIf { it > 0 } ?: error("Could not read audio duration")
        } finally { metadata.release() }
    }
    private fun copyAudio(uri: Uri, role: String): StudioAudio {
        val context = requireContext().applicationContext
        var name = if (role == "voice") "Voice audio" else "Music"
        context.contentResolver.query(uri, arrayOf(android.provider.OpenableColumns.DISPLAY_NAME), null, null, null)?.use { if (it.moveToFirst()) name = it.getString(0) ?: name }
        val directory = File(context.filesDir, "studio-audio").apply { check(isDirectory || mkdirs()) }
        val file = File(directory, "${UUID.randomUUID()}.media")
        try {
            context.contentResolver.openInputStream(uri)!!.use { input -> file.outputStream().use { output ->
                val buffer = ByteArray(8192); var total = 0L
                while (true) {
                    val count = input.read(buffer); if (count < 0) break
                    total += count; require(total <= 100L * 1024 * 1024) { "Audio file must be under 100 MB" }; output.write(buffer,0,count)
                }
            } }
            val duration = audioDuration(file)
            return StudioAudio(path = file.path, name = name.take(80), role = role, sourceDurationMs = duration,
                trimEndMs = minOf(duration,if (role == "voice") 60000 else document.movieDurationMs()), volume = if (role == "music") .3f else 1f)
        } catch (error: Throwable) { file.delete(); throw error }
    }
    @Suppress("DEPRECATION")
    private fun startRecording() {
        if (recorder != null) return
        val directory = File(requireContext().filesDir, "studio-audio").apply { mkdirs() }
        val file = File(directory, "${UUID.randomUUID()}.m4a")
        val microphone = android.media.MediaRecorder()
        try {
            microphone.setAudioSource(android.media.MediaRecorder.AudioSource.MIC)
            microphone.setOutputFormat(android.media.MediaRecorder.OutputFormat.MPEG_4)
            microphone.setAudioEncoder(android.media.MediaRecorder.AudioEncoder.AAC)
            microphone.setAudioSamplingRate(44100); microphone.setAudioEncodingBitRate(128000)
            microphone.setMaxDuration(60000); microphone.setOutputFile(file.path)
            microphone.setOnInfoListener { _, what, _ -> if (what == android.media.MediaRecorder.MEDIA_RECORDER_INFO_MAX_DURATION_REACHED) stopRecording(true) }
            microphone.prepare(); microphone.start(); recorder = microphone; recordingFile = file
            recordingDialog = AlertDialog.Builder(requireContext()).setTitle("Recording your voice…")
                .setMessage("Microphone is recording. Maximum 60 seconds. Leaving the app cancels this recording.")
                .setCancelable(false).setPositiveButton("Stop & use") { _, _ -> stopRecording(true) }
                .setNegativeButton("Cancel") { _, _ -> stopRecording(false) }.show()
        } catch (error: Exception) { microphone.release(); file.delete(); message(error.message ?: "Could not start microphone") }
    }
    private fun stopRecording(keep: Boolean) {
        val microphone = recorder ?: return
        recorder = null
        val file = recordingFile; recordingFile = null
        val valid = runCatching { microphone.stop() }.isSuccess
        microphone.release(); recordingDialog?.dismiss(); recordingDialog = null
        if (file != null && keep && (valid || file.length() > 0)) {
            runCatching {
                val duration = audioDuration(file)
                attachAudio(StudioAudio(path = file.path, name = "My recording", role = "voice", sourceDurationMs = duration, trimEndMs = minOf(duration,60000)))
            }.onSuccess { if (view != null) toast("Voice recording added") }
                .onFailure { file.delete(); if (view != null) message("Recording was too short. Please try again.") }
        } else file?.delete()
    }
    private fun motionExportSheet() { if (!exporting) videoStudioSheet("Export") }
    private fun savedAnimations() {
        val directory = File(requireContext().getExternalFilesDir(Environment.DIRECTORY_PICTURES) ?: requireContext().filesDir, "Studio")
        sheet("Saved animations") { content, _ ->
            val files = directory.listFiles().orEmpty().filter { it.extension in listOf("mp4", "gif") }.sortedByDescending { it.lastModified() }
            if (files.isEmpty()) content.addView(label("Export an MP4 or GIF to see it here.", 14))
            files.forEach { file ->
                val card = column().apply { setPadding(dp(12)); background = StudioMenu.surface(requireContext()) }
                StudioMenu.section(card,file.name,"${file.extension.uppercase()} · ${String.format(java.util.Locale.ROOT,"%.1f",file.length()/1000000f)} MB")
                val mime = if (file.extension == "mp4") "video/mp4" else "image/gif"
                val uri = FileProvider.getUriForFile(requireContext(),requireContext().packageName+".provider",file)
                StudioMenu.grid(card,listOf(
                    StudioMenu.Action("Open / play","Play") { runCatching { startActivity(Intent(Intent.ACTION_VIEW).setDataAndType(uri,mime).addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)) }.onFailure { message("No player is installed for this format. Use Share to choose another app.") } },
                    StudioMenu.Action("Share","Share") { startActivity(Intent.createChooser(Intent(Intent.ACTION_SEND).apply { type = mime; putExtra(Intent.EXTRA_STREAM,uri); clipData = ClipData.newRawUri("Animation",uri); addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION) },"Share animation")) }
                ),2)
                content.addView(card,LinearLayout.LayoutParams(-1,-2).apply { bottomMargin = dp(10) })
            }
        }
    }
    private var videoProgressDialog: AlertDialog? = null
    private fun exportMotion(format: Int, share: Boolean) {
        if (exporting) return
        if (format == 2 && document.movieDurationMs() > 10000) { message("GIF supports up to 10 seconds. Choose MP4 for longer scenes."); return }
        exitPreview(); saveNow(); exporting = true; cancelVideo = false; exportButton.isEnabled = false
        val snapshot = document
        val context = requireContext().applicationContext
        val caption = TextView(requireContext()).apply { text = "Preparing animation…"; setPadding(dp(20)) }
        val progressDialog = AlertDialog.Builder(requireContext()).setTitle("Exporting animation").setView(caption).setCancelable(false)
            .setNegativeButton("Cancel") { _, _ -> cancelVideo = true }.show()
        videoProgressDialog = progressDialog
        lifecycleScope.launch {
            val job = coroutineContext[Job]!!
            val result = runCatching { withContext(Dispatchers.IO) {
                val directory = File(context.getExternalFilesDir(Environment.DIRECTORY_PICTURES) ?: context.filesDir, "Studio").apply { check(isDirectory || mkdirs()) }
                val file = File(directory, "UT${System.currentTimeMillis()}.${if (format == 2) "gif" else "mp4"}")
                val progress: (Int) -> Unit = { percent -> android.os.Handler(android.os.Looper.getMainLooper()).post { if (progressDialog.isShowing) caption.text = "$percent% · keep the app open" } }
                val cancelled = { cancelVideo || !job.isActive }
                if (format == 2) StudioGifExporter.export(context, snapshot, file, progress, cancelled)
                else StudioVideoExporter.export(context, snapshot, file, if (format == 0) 720 else 1080, progress, cancelled)
                val mime = if (format == 2) "image/gif" else "video/mp4"
                if (!share && Build.VERSION.SDK_INT >= 29) {
                    val collection = if (format == 2) MediaStore.Images.Media.EXTERNAL_CONTENT_URI else MediaStore.Video.Media.EXTERNAL_CONTENT_URI
                    var uri: Uri? = null
                    runCatching {
                        val values = ContentValues().apply {
                            put(MediaStore.MediaColumns.DISPLAY_NAME, file.name); put(MediaStore.MediaColumns.MIME_TYPE, mime)
                            put(MediaStore.MediaColumns.RELATIVE_PATH, if (format == 2) "Pictures/UrduTyper" else "Movies/UrduTyper")
                            put(MediaStore.MediaColumns.IS_PENDING, 1)
                        }
                        uri = context.contentResolver.insert(collection, values)
                        checkNotNull(uri)
                        checkNotNull(context.contentResolver.openOutputStream(uri!!)).use { out -> file.inputStream().use { it.copyTo(out) } }
                        values.clear(); values.put(MediaStore.MediaColumns.IS_PENDING,0); context.contentResolver.update(uri!!,values,null,null)
                    }.onFailure { uri?.let { runCatching { context.contentResolver.delete(it,null,null) } } }
                }
                file to mime
            } }
            exporting = false; progressDialog.dismiss(); videoProgressDialog = null
            if (view == null) return@launch
            exportButton.isEnabled = true
            result.onSuccess { (file, mime) ->
                status.text = "Animation exported · draft saved"
                val uri = FileProvider.getUriForFile(context, context.packageName + ".provider", file)
                if (share) startActivity(Intent.createChooser(Intent(Intent.ACTION_SEND).apply {
                    type = mime; putExtra(Intent.EXTRA_STREAM, uri); clipData = ClipData.newRawUri("Animation",uri); addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                }, "Share animation"))
                else AlertDialog.Builder(requireContext()).setTitle("Video ready")
                    .setMessage("Saved on this device · ${file.length() / 1024} KB")
                    .setNeutralButton("Play") { _, _ -> runCatching { startActivity(Intent(Intent.ACTION_VIEW).setDataAndType(uri, mime).addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)) }.onFailure { message("Use Saved animations → Share to choose a player.") } }
                    .setPositiveButton("Share") { _, _ -> startActivity(Intent.createChooser(Intent(Intent.ACTION_SEND).apply { type = mime; putExtra(Intent.EXTRA_STREAM,uri); clipData = ClipData.newRawUri("Animation",uri); addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION) },"Share animation")) }
                    .setNegativeButton("Done",null).show()
            }.onFailure { error ->
                status.text = "Draft saved"
                if (cancelVideo) toast("Export cancelled") else AlertDialog.Builder(requireContext()).setTitle("Video export failed")
                    .setMessage(error.message ?: "Try 720p, a shorter video or free up device storage.")
                    .setNegativeButton("Close", null).setPositiveButton("Retry / change quality") { _, _ -> motionExportSheet() }.show()
            }
        }
    }

    private fun copyImage(uri: Uri): File {
        val context = requireContext().applicationContext
        val options = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        context.contentResolver.openInputStream(uri)!!.use { BitmapFactory.decodeStream(it, null, options) }
        require(options.outWidth > 0 && options.outHeight > 0) { "Choose a valid image" }
        val sample = BitmapFactory.Options().apply { inSampleSize = 1; while (maxOf(options.outWidth, options.outHeight) / inSampleSize > 2048) inSampleSize *= 2 }
        val decoded = context.contentResolver.openInputStream(uri)!!.use { BitmapFactory.decodeStream(it, null, sample) } ?: error("Could not decode image")
        val orientation = runCatching {
            context.contentResolver.openInputStream(uri)!!.use {
                android.media.ExifInterface(it).getAttributeInt(android.media.ExifInterface.TAG_ORIENTATION, android.media.ExifInterface.ORIENTATION_NORMAL)
            }
        }.getOrDefault(android.media.ExifInterface.ORIENTATION_NORMAL)
        val transform = android.graphics.Matrix().apply {
            when (orientation) {
                android.media.ExifInterface.ORIENTATION_FLIP_HORIZONTAL -> setScale(-1f, 1f)
                android.media.ExifInterface.ORIENTATION_ROTATE_180 -> setRotate(180f)
                android.media.ExifInterface.ORIENTATION_FLIP_VERTICAL -> setScale(1f, -1f)
                android.media.ExifInterface.ORIENTATION_TRANSPOSE -> { setRotate(90f); postScale(-1f, 1f) }
                android.media.ExifInterface.ORIENTATION_ROTATE_90 -> setRotate(90f)
                android.media.ExifInterface.ORIENTATION_TRANSVERSE -> { setRotate(270f); postScale(-1f, 1f) }
                android.media.ExifInterface.ORIENTATION_ROTATE_270 -> setRotate(270f)
            }
        }
        val bitmap = if (transform.isIdentity) decoded else Bitmap.createBitmap(decoded, 0, 0, decoded.width, decoded.height, transform, true).also { if (it !== decoded) decoded.recycle() }
        val directory = File(context.filesDir, "studio-images").apply { check(isDirectory || mkdirs()) }
        val target = File(directory, "${UUID.randomUUID()}.png")
        try { target.outputStream().use { check(bitmap.compress(Bitmap.CompressFormat.PNG, 100, it)) } }
        catch (error: Exception) { target.delete(); throw error }
        finally { bitmap.recycle() }
        return target
    }

    private fun formInspector(title: String, content: View): StudioInspector {
        activeInspector?.dismiss(); exitPreview()
        val panel = StudioInspector(requireContext(),title,fonts)
        activeInspector = panel; panel.tabBar.visibility = View.GONE
        panel.body.addView(content); panel.render(document)
        skinMenu(content)
        panel.setOnDismissListener { if (view != null) { canvas.document = document; canvas.invalidate() } }
        return panel
    }
    private fun skinMenu(view: View) {
        if (view is EditText) {
            view.setTextColor(LuxeTheme.text); view.setHintTextColor(LuxeTheme.muted)
            view.backgroundTintList = null; view.background = StudioMenu.surface(requireContext())
            view.setPadding(dp(12),dp(10),dp(12),dp(10)); view.minHeight = dp(48)
        }
        if (view is SeekBar) {
            view.progressTintList = android.content.res.ColorStateList.valueOf(LuxeTheme.gold)
            view.thumbTintList = android.content.res.ColorStateList.valueOf(LuxeTheme.gold)
        }
        if (view is ViewGroup) for (i in 0 until view.childCount) skinMenu(view.getChildAt(i))
    }
    private fun sheet(title: String, showDone: Boolean = true, build: (LinearLayout, BottomSheetDialog) -> Unit) {
        activeInspector?.dismiss(); exitPreview()
        val dialog = StudioInspector(requireContext(), title, fonts)
        activeInspector = dialog
        dialog.showArtwork(false)
        dialog.tabBar.visibility = View.GONE
        dialog.render(document)
        build(dialog.body, dialog)
        skinMenu(dialog.body)
        if (showDone) dialog.footer.addView(dialog.button("Done", true) { dialog.dismiss() }, LinearLayout.LayoutParams(-1, dp(48)))
        else if (dialog.footer.childCount == 0) dialog.footer.visibility = View.GONE
        dialog.setOnDismissListener { if (view != null) { canvas.document = document; canvas.invalidate() } }
        dialog.show()
    }
    private fun slider(content: LinearLayout, name: String, initial: Float, min: Float, max: Float, live: Boolean = false, change: (Float) -> Unit) {
        var value = initial
        StudioMenu.slider(content,name,initial,min,max,finished = { if (!live) change(value) }) { next -> value = next; if (live) change(next) }
    }
    private fun numberInput(value: Int) = EditText(requireContext()).apply { inputType = InputType.TYPE_CLASS_NUMBER; setText(value.toString()); isSingleLine = true }
    private fun column() = LinearLayout(requireContext()).apply { orientation = LinearLayout.VERTICAL }
    private fun row() = LinearLayout(requireContext()).apply { orientation = LinearLayout.HORIZONTAL }
    private fun horizontal(build: LinearLayout.() -> Unit): HorizontalScrollView = HorizontalScrollView(requireContext()).apply {
        isHorizontalScrollBarEnabled = false
        addView(row().apply { setPadding(dp(4), 0, dp(4), 0); build() })
    }
    private fun label(value: String, size: Int) = TextView(requireContext()).apply { text = value; textSize = size.toFloat(); setTextColor(ink); typeface = Typeface.DEFAULT; setPadding(dp(4)) }
    private fun button(value: String, description: String = value, click: () -> Unit) = MaterialButton(requireContext(), null, com.google.android.material.R.attr.materialButtonOutlinedStyle).apply {
        text = value; contentDescription = description; isAllCaps = false; textSize = 13f
        typeface = Typeface.DEFAULT; setTextColor(green); minHeight = dp(48); minimumWidth = dp(48)
        insetTop = 0; insetBottom = 0; cornerRadius = dp(12)
        strokeColor = android.content.res.ColorStateList.valueOf(LuxeTheme.border)
        backgroundTintList = android.content.res.ColorStateList.valueOf(LuxeTheme.surface)
        iconTint = android.content.res.ColorStateList.valueOf(ink)
        letterSpacing = 0f
        setPadding(dp(12), dp(4), dp(12), dp(4)); setOnClickListener { click() }
    }
    private fun rounded(color: Int) = GradientDrawable().apply { setColor(color); cornerRadius = dp(16).toFloat() }
    private fun dp(value: Int) = (value * resources.displayMetrics.density).roundToInt()
    private fun toast(value: String) { Toast.makeText(requireContext(), value, Toast.LENGTH_SHORT).show() }
    private fun message(value: String) { AlertDialog.Builder(requireContext()).setMessage(value).setPositiveButton("OK", null).show() }
}

