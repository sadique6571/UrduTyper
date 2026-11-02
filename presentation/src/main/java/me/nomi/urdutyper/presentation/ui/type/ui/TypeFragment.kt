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
    private val importImage = registerForActivityResult(ActivityResultContracts.GetContent()) { uri ->
        if (uri != null) {
            val background = imageAsBackground
            lifecycleScope.launch {
                runCatching { withContext(Dispatchers.IO) { copyImage(uri) } }
                    .onSuccess { file ->
                        if (view == null) return@onSuccess
                        if (background) commit(document.copy(backgroundImage = file.path, backgroundStyle = "", backgroundVisible = true))
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
        dock.addView(horizontal { toolActions = this })
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
            toolActions.addView(target, LinearLayout.LayoutParams(resources.displayMetrics.widthPixels / (if (name == "Media") 5 else 6), dp(64)))
        }
        when (name) {
            "Text", "Selection" -> {
                if (name == "Selection") action("Edit") { editSelected() } else action("Text") { textDialog(null) }
                action("Fonts") { fontsSheet() }
                if (name == "Selection") action("Size") { fontSizeControl.visibility = if (fontSizeControl.visibility == View.VISIBLE) View.GONE else View.VISIBLE }
                action("Color") { textStyleSheet("Color") }
                if (name != "Selection") action("Effects") { effectsSheet() }
                action("Layers") { layersSheet() }
                action("More") { toolsSheet() }
            }
            "Video" -> {
                action("Media") { pickImage(false) }
                action("Text") { textDialog(null) }
                action("Voice") { audioSheet() }
                action("Music") { audioRole = "music"; importAudio.launch("audio/*") }
                action("Animate") { animationSheet() }
                action("More") { toolsSheet() }
            }
            "Media" -> {
                action("Media") { pickImage(false) }
                action("Style") { styleSheet() }
                action("Background") { backgroundSheet() }
                action("Layers") { layersSheet() }
                action("More") { toolsSheet() }
            }
            else -> { action("Text") { showToolGroup("Text") }; action("Video") { showToolGroup("Video") }; action("More") { toolsSheet() } }
        }
    }

    private fun toolsSheet() {
        sheet("Studio tools") { content, dialog ->
            fun item(title: String, click: () -> Unit) { content.addView(button(title) { dialog.dismiss(); click() }) }
            item("Undo last change") { history.undo()?.let { restore(it) } }
            item("Redo last change") { history.redo()?.let { restore(it) } }
            item("Canvas size") { sizeSheet() }
            item("Add text") { textDialog(null) }
            item("Text effects") { effectsSheet() }
            item("Full-screen preview") { openFullPreview(activeTool == "Video") }
            item("Edit selected layer") { editSelected() }
            item("Font size slider") { fontSizeControl.visibility = View.VISIBLE }
            item("Style · alignment · crop · opacity") { styleSheet() }
            item("Add image") { pickImage(false) }
            item("Background") { backgroundSheet() }
            item("Layers") { layersSheet() }
            item("Duplicate selected layer") { selected()?.let { duplicateLayer(it) } }
            item("Center selected layer") { selected()?.takeIf { !it.locked }?.let { replaceLayer(it.copy(x = document.width / 2f, y = document.height / 2f)) } }
            item("Delete selected layer") { selected()?.takeIf { !it.locked }?.let { layer ->
                AlertDialog.Builder(requireContext()).setTitle("Delete selected layer?").setNegativeButton("Cancel", null)
                    .setPositiveButton("Delete") { _, _ -> commit(document.copy(layers = document.layers.filterNot { it.id == layer.id })) }.show()
            } }
            item("Canvas zoom / pan") { canvasControls.visibility = if (canvasControls.visibility == View.VISIBLE) View.GONE else View.VISIBLE }
            item(if (gridEnabled) "Hide grid" else "Show grid") { gridButton.performClick() }
            item(if (snapEnabled) "Disable snapping" else "Enable snapping") { snapButton.performClick() }
            item("Safe margins") { commit(document.copy(safeMargins = !document.safeMargins)) }
            item("Video editor") { showToolGroup("Video") }
            item("Poster editor") { showToolGroup("Text") }
            item("Scenes / transitions") { scenesSheet() }
            item("Voice / music / recording") { audioSheet() }
            item("Import SRT captions") { importCaptions.launch("*/*") }
            item("Beat timing") { beatTimingSheet() }
            item("Video templates") { videoTemplates() }
            item("Saved animations") { savedAnimations() }
            item("Brand kit") { brandKitSheet() }
            item("Batch poetry") { batchPoetrySheet() }
            item("Rename project") { rename() }
            item("Drafts") { draftsSheet() }
            item("Save draft") { if (saveNow()) toast("Draft saved") }
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

    private fun openFullPreview(video: Boolean, previewDocument: StudioDocument = document) {
        exitPreview(); fullPreview?.dismiss()
        fullPreview = FullScreenPreviewDialog(requireContext(),previewDocument,video).also { it.show() }
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
        sheet("Scenes · ${document.movieDurationMs() / 1000f}s total") { content, dialog ->
            content.addView(horizontal {
                addView(button("+ Blank scene") { dialog.dismiss(); addScene(false) })
                addView(button("Duplicate scene") { dialog.dismiss(); addScene(true) })
                addView(button("Batch poetry") { dialog.dismiss(); batchPoetrySheet() })
            })
            val all = document.orderedScenes()
            all.forEachIndexed { index, scene ->
                val card = column().apply { background = rounded(LuxeTheme.surface); setPadding(dp(8)) }
                val thumb = ImageView(requireContext()).apply {
                    val preview = runCatching { StudioRenderer(requireContext(), fonts).bitmap(scene.applyTo(document), 160, false) }.getOrNull()
                    setImageBitmap(preview); scaleType = ImageView.ScaleType.CENTER_INSIDE
                    contentDescription = "Scene ${index + 1} preview"
                }
                card.addView(thumb, LinearLayout.LayoutParams(-1, dp(88)))
                card.addView(label("Scene ${index + 1} · ${scene.video.durationMs / 1000f}s${if (scene.id == document.sceneId) " · Editing" else ""}", 15))
                card.addView(horizontal {
                    addView(button("Edit") { commit(document.selectScene(scene.id)); canvas.selectedId = document.layers.lastOrNull()?.id; refresh(); dialog.dismiss() })
                    fun move(delta: Int) {
                        val target = index + delta
                        if (target !in all.indices) return
                        val reordered = all.toMutableList(); java.util.Collections.swap(reordered, index, target)
                        commit(document.copy(scenes = reordered)); dialog.dismiss(); scenesSheet()
                    }
                    addView(button("←", "Move scene earlier") { move(-1) }.apply { isEnabled = index > 0 })
                    addView(button("→", "Move scene later") { move(1) }.apply { isEnabled = index < all.lastIndex })
                    if (all.size > 1) addView(button("Delete") {
                        AlertDialog.Builder(requireContext()).setTitle("Delete scene ${index + 1}?")
                            .setNegativeButton("Cancel", null).setPositiveButton("Delete") { _, _ ->
                                val remaining = all.filterNot { it.id == scene.id }
                                val next = if (document.sceneId == scene.id) remaining.first().applyTo(document) else document
                                commit(next.copy(scenes = remaining)); dialog.dismiss(); scenesSheet()
                            }.show()
                    })
                })
                val transitions = listOf("none", "fade", "slide", "dissolve")
                val choice = Spinner(requireContext()).apply {
                    adapter = ArrayAdapter(requireContext(), android.R.layout.simple_spinner_dropdown_item, transitions)
                    setSelection(transitions.indexOf(scene.transition).coerceAtLeast(0))
                }
                card.addView(label("Entrance transition (first scene has no transition)", 12)); card.addView(choice)
                val length = secondsInput(scene.transitionMs)
                card.addView(label("Transition length · 0.1–2 seconds", 12)); card.addView(length)
                card.addView(button("Apply transition") {
                    val ms = milliseconds(length)
                    if (ms == null || ms !in 100..2000) { length.error = "Enter 0.1–2 seconds"; return@button }
                    commit(document.copy(scenes = document.orderedScenes().map {
                        if (it.id == scene.id) it.copy(transition = choice.selectedItem.toString(), transitionMs = ms) else it
                    })); toast("Transition saved")
                })
                content.addView(card, LinearLayout.LayoutParams(-1, -2).apply { bottomMargin = dp(12) })
            }
        }
    }
    private fun batchPoetrySheet() {
        val input = EditText(requireContext()).apply {
            hint = "ہر شعر کے درمیان ایک خالی سطر رکھیں"; minLines = 5; maxLines = 10
            inputType = InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_FLAG_MULTI_LINE
            filters = arrayOf(android.text.InputFilter.LengthFilter(12000))
        }
        val dialog = AlertDialog.Builder(requireContext()).setTitle("Poetry → video scenes")
            .setMessage("Separate verses with a blank line. Creates a separate saved project, up to 12 scenes, 5 seconds each.")
            .setView(input).setNegativeButton("Cancel", null).setPositiveButton("Create video", null).create()
        dialog.setOnShowListener { dialog.getButton(AlertDialog.BUTTON_POSITIVE).setOnClickListener {
            val verses = input.text.toString().trim().split(Regex("\\n\\s*\\n")).filter { it.isNotBlank() }
            if (verses.isEmpty() || verses.size > 12 || verses.any { it.length > 2000 }) { input.error = "Use 1–12 verses, each up to 2000 characters"; return@setOnClickListener }
            if (!saveNow()) { message("Save the current draft before creating another project."); return@setOnClickListener }
            val base = selected()?.takeIf { it.kind == "text" } ?: StudioLayer(x = document.width / 2f, y = document.height / 2f)
            val scenes = verses.map { verse -> document.captureScene().copy(id = UUID.randomUUID().toString(),
                video = document.video.copy(durationMs = 5000), layers = listOf(base.copy(id = UUID.randomUUID().toString(),
                    text = verse, locked = false, visible = true, animation = LayerAnimation("words", 0, 5000, 3500)))) }
            exitPreview()
            document = scenes.first().applyTo(document).copy(id = UUID.randomUUID().toString(), title = "Poetry reel", audio = emptyList(), scenes = scenes)
            history.reset(document); canvas.selectedId = document.layers.first().id; refresh(); scheduleSave(); showToolGroup("Video"); dialog.dismiss()
        } }; dialog.show()
    }
    private fun beatTimingSheet() {
        val body = column().apply { setPadding(dp(16)) }
        val bpm = numberInput(90); val beats = numberInput(4)
        body.addView(label("Music BPM (enter the tempo)", 14)); body.addView(bpm)
        body.addView(label("Beats per scene", 14)); body.addView(beats)
        val dialog = AlertDialog.Builder(requireContext()).setTitle("Align scene cuts to a beat grid")
            .setMessage("Uses your entered tempo; does not detect beats automatically. The grid starts at the music track's video offset.")
            .setView(body).setNegativeButton("Cancel", null).setPositiveButton("Apply", null).create()
        dialog.setOnShowListener { dialog.getButton(AlertDialog.BUTTON_POSITIVE).setOnClickListener {
            val tempo = bpm.text.toString().toIntOrNull(); val count = beats.text.toString().toIntOrNull()
            if (tempo == null || tempo !in 30..240 || count == null || count !in 1..32) { bpm.error = "BPM 30–240, beats 1–32"; return@setOnClickListener }
            val duration = (60000L * count / tempo).coerceAtLeast(1000)
            val musicOffset = document.audio.firstOrNull { it.role == "music" }?.startMs ?: 0
            val all = document.orderedScenes().mapIndexed { index, scene ->
                val length = duration + if (index == 0) musicOffset else 0
                scene.copy(video = scene.video.copy(durationMs = length), layers = scene.layers.map { layer ->
                    layer.copy(animation = VideoEditRules.animation(layer.animation,length))
                })
            }
            if (all.sumOf { it.video.durationMs } > 60000) { beats.error = "Video exceeds 60 seconds; reduce beats per scene"; return@setOnClickListener }
            commit(all.first { it.id == document.sceneId }.applyTo(document).copy(scenes = all)); dialog.dismiss()
        } }; dialog.show()
    }
    private fun brandKitSheet() {
        val prefs = requireContext().getSharedPreferences("studio-brand", 0)
        sheet("My brand kit") { content, dialog ->
            content.addView(label("Save your selected text's font, color and size for the next design. Your imported font must remain on this device.", 13))
            content.addView(button("Save selected text as brand style") {
                val layer = selectedText() ?: return@button
                prefs.edit().putString("font", layer.fontId).putInt("color", layer.color).putFloat("size", layer.size).apply()
                toast("Brand style saved")
            })
            content.addView(button("Apply brand style") {
                val layer = selectedText() ?: return@button
                if (!prefs.contains("font")) { toast("Save a brand style first"); return@button }
                replaceLayer(layer.copy(fontId = prefs.getString("font", "nastaliq") ?: "nastaliq", color = prefs.getInt("color", green), size = prefs.getFloat("size", 76f)))
                dialog.dismiss()
            })
            content.addView(button("Save selected image as logo") {
                val layer = selected()?.takeIf { it.kind == "image" } ?: run { toast("Select an image layer first"); return@button }
                prefs.edit().putString("logo", layer.imagePath).putFloat("logoRatio", layer.height / layer.width).apply(); toast("Logo saved")
            })
            content.addView(button("Add my logo") {
                val path = prefs.getString("logo", "").orEmpty()
                if (!File(path).isFile) { toast("Save an image layer as your logo first"); return@button }
                addLayer(StudioLayer(kind = "image", imagePath = path, width = document.width * .2f, height = document.width * .2f * prefs.getFloat("logoRatio", 1f),
                    x = document.width * .82f, y = document.height * .12f)); dialog.dismiss()
            })
        }
    }
    /** Preview changes never enter history or autosave until Apply. Dismiss always restores the document. */
    private fun previewSheet(title: String, build: (LinearLayout, BottomSheetDialog, (StudioDocument) -> Unit) -> Unit) {
        exitPreview()
        var candidate = document
        sheet(title, showDone = false) { content, dialog ->
            content.addView(label("Tap options to preview on the canvas. Apply keeps the result; Cancel restores it.", 12))
            build(content, dialog) { next -> candidate = next; canvas.document = next; canvas.invalidate() }
            content.addView(horizontal {
                addView(button("Cancel") { dialog.dismiss() })
                addView(button("Apply") { commit(candidate); dialog.dismiss() })
            })
        }
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
        AlertDialog.Builder(requireContext()).setTitle(if (layer == null) "Add text" else "Edit text")
            .setView(input).setNegativeButton("Cancel", null)
            .setPositiveButton("Apply") { _, _ ->
                val value = input.text.toString()
                if (value.isBlank()) return@setPositiveButton
                if (layer != null) replaceLayer(layer.copy(text = value))
                else addLayer(StudioLayer(text = value, x = document.width / 2f, y = document.height / 2f,
                    width = document.width * .8f, size = minOf(document.width, document.height) * .07f))
            }.show()
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
            if (layer.kind == "text") {
                content.addView(CheckBox(requireContext()).apply { text = "Bold"; isChecked = layer.bold; setTextColor(ink)
                    setOnCheckedChangeListener { _, value -> update(layer.copy(bold = value)) } })
                content.addView(CheckBox(requireContext()).apply { text = "Italic"; isChecked = layer.italic; setTextColor(ink)
                    setOnCheckedChangeListener { _, value -> update(layer.copy(italic = value)) } })
                slider(content, "Font size", layer.size, 8f, 600f, live = true) { update(layer.copy(size = it)) }
                slider(content, "Line spacing", layer.lineSpacing, .8f, 2.4f, live = true) { update(layer.copy(lineSpacing = it)) }
                val align = row()
                listOf("left", "center", "right").forEach { value -> align.addView(button(value.replaceFirstChar { it.uppercase() }) { update(layer.copy(alignment = value)) }) }
                content.addView(align)
            }
            slider(content, "Box width", layer.width, 40f, document.width * 1.5f, live = true) { update(layer.copy(width = it)) }
            if (layer.kind == "image") content.addView(CheckBox(requireContext()).apply {
                text = "Fill crop frame (preserve image proportions)"; isChecked = layer.cropImage; setTextColor(ink)
                setOnCheckedChangeListener { _, value -> update(layer.copy(cropImage = value)) }
            })
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
        val swatches = row()
        colors.forEach { color -> swatches.addView(button("●") { input.setText(String.format("#%08X", color)) }.apply { setTextColor(color) }, LinearLayout.LayoutParams(0, dp(48), 1f)) }
        body.addView(swatches)
        val dialog = AlertDialog.Builder(requireContext()).setTitle("Choose color").setView(body)
            .setNegativeButton("Cancel", null).setPositiveButton("Apply", null).create()
        dialog.setOnShowListener { dialog.getButton(AlertDialog.BUTTON_POSITIVE).setOnClickListener {
            val color = runCatching { Color.parseColor(input.text.toString().trim()) }.getOrNull()
            if (color == null) input.error = "Enter a valid hex color"
            else { onPick(color); dialog.dismiss() }
        } }
        dialog.setOnDismissListener { canvas.document = document; canvas.invalidate() }
        dialog.show()
    }
    private fun pickImage(background: Boolean) { imageAsBackground = background; importImage.launch("image/*") }
    private fun backgroundSheet() {
        previewSheet("Background · live preview") { content, dialog, preview ->
            content.addView(horizontal {
                addView(button("Solid color") { dialog.dismiss(); colorPicker(document.backgroundColor) { commit(document.copy(backgroundColor = it, gradientColor = it, backgroundStyle = "", backgroundImage = "", backgroundVisible = true)) } })
                addView(button("Gradient start") { dialog.dismiss(); colorPicker(document.backgroundColor) { commit(document.copy(backgroundColor = it, backgroundStyle = "", backgroundImage = "", backgroundVisible = true)) } })
                addView(button("Gradient end") { dialog.dismiss(); colorPicker(document.gradientColor) { commit(document.copy(gradientColor = it, backgroundStyle = "", backgroundImage = "", backgroundVisible = true)) } })
            })
            content.addView(button("Choose gallery background") { dialog.dismiss(); pickImage(true) })
            content.addView(button(if (document.backgroundVisible) "Make background transparent" else "Show background") { commit(document.copy(backgroundVisible = !document.backgroundVisible)); dialog.dismiss() })
            BACKGROUND_ENTRIES.forEach { entry ->
                val card = row().apply { gravity = Gravity.CENTER_VERTICAL; setPadding(dp(4)) }
                card.addView(View(requireContext()).apply { setBackgroundResource(entry.drawableRes) }, LinearLayout.LayoutParams(dp(64), dp(56)))
                card.addView(label(entry.label, 16).apply { setPadding(dp(16)) })
                card.setOnClickListener { preview(document.copy(backgroundStyle = entry.styleName, backgroundImage = "", backgroundVisible = true)) }
                content.addView(card)
            }
        }
    }
    private fun layersSheet() {
        sheet("Layers · top first") { content, dialog ->
            document.layers.asReversed().forEach { layer ->
                val card = column().apply { setPadding(dp(8)); background = rounded(if (layer.id == canvas.selectedId) LuxeTheme.raised else LuxeTheme.surface) }
                val layerHeader = row().apply { gravity = Gravity.CENTER_VERTICAL }
                layerHeader.addView(ImageView(requireContext()).apply {
                    scaleType = ImageView.ScaleType.CENTER_INSIDE; contentDescription = "Layer preview"
                    setImageBitmap(runCatching { StudioRenderer(requireContext(),fonts).bitmap(document.copy(layers = listOf(layer.copy(visible = true))),96,false) }.getOrNull())
                },LinearLayout.LayoutParams(dp(48),dp(64)))
                layerHeader.addView(button((if (layer.id == canvas.selectedId) "✓ " else "") + if (layer.kind == "image") "Image" else layer.text.take(28)) {
                    canvas.selectedId = layer.id
                    if (activeTool != "Video") showToolGroup(if (layer.kind == "image") "Media" else "Selection")
                    refresh(); dialog.dismiss()
                }.apply { maxLines = 1; ellipsize = android.text.TextUtils.TruncateAt.END },LinearLayout.LayoutParams(0,dp(56),1f))
                card.addView(layerHeader)
                card.addView(horizontal {
                    addView(button(if (layer.visible) "Hide" else "Show") { replaceLayer(layer.copy(visible = !layer.visible)); dialog.dismiss(); layersSheet() })
                    addView(button(if (layer.locked) "Unlock" else "Lock") { replaceLayer(layer.copy(locked = !layer.locked)); dialog.dismiss(); layersSheet() })
                    addView(button("Forward") { reorder(layer, 1); dialog.dismiss(); layersSheet() }.apply { isEnabled = !layer.locked && document.layers.last().id != layer.id })
                    addView(button("Back") { reorder(layer, -1); dialog.dismiss(); layersSheet() }.apply { isEnabled = !layer.locked && document.layers.first().id != layer.id })
                    addView(button("Duplicate") { duplicateLayer(layer); dialog.dismiss(); layersSheet() })
                    addView(button("Delete") { commit(document.copy(layers = document.layers.filterNot { it.id == layer.id })); dialog.dismiss(); layersSheet() }.apply { isEnabled = !layer.locked })
                })
                content.addView(card, LinearLayout.LayoutParams(-1, -2).apply { bottomMargin = dp(8) })
            }
            content.addView(button("Background · " + if (document.backgroundVisible) "visible" else "hidden") { dialog.dismiss(); backgroundSheet() })
            if (document.layers.isEmpty()) content.addView(label("No layers yet. Add text or an image from the toolbar.", 14))
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
            presets.forEach { (name, dimensions) ->
                val (w,h) = dimensions
                content.addView(button("${if (document.width == w && document.height == h) "✓ " else ""}$name · $w × $h") {
                    commit(document.resized(w,h)); canvas.fitToScreen(); dialog.dismiss(); toast("Canvas: $w × $h")
                }.apply { icon = CanvasRatioIcon(w.toFloat()/h); iconSize = dp(24); iconPadding = dp(12) })
            }
            content.addView(button("Custom size…") {
                dialog.dismiss()
                val body = column().apply { setPadding(dp(20)) }
                val width = numberInput(document.width); val height = numberInput(document.height)
                body.addView(label("Width · 320–4096 pixels",14)); body.addView(width)
                body.addView(label("Height · 320–4096 pixels",14)); body.addView(height)
                val custom = AlertDialog.Builder(requireContext()).setTitle("Custom canvas").setView(body).setNegativeButton("Cancel",null).setPositiveButton("Apply",null).create()
                custom.setOnShowListener { custom.getButton(AlertDialog.BUTTON_POSITIVE).setOnClickListener {
                    val w = width.text.toString().toIntOrNull(); val h = height.text.toString().toIntOrNull()
                    if (w == null || w !in 320..4096) { width.error = "Enter 320–4096"; return@setOnClickListener }
                    if (h == null || h !in 320..4096) { height.error = "Enter 320–4096"; return@setOnClickListener }
                    commit(document.resized(w,h)); canvas.fitToScreen(); custom.dismiss()
                } }; custom.show()
            })
        }
    }
    private fun rename() {
        val input = EditText(requireContext()).apply { setText(document.title); isSingleLine = true; filters = arrayOf(android.text.InputFilter.LengthFilter(60)) }
        AlertDialog.Builder(requireContext()).setTitle("Design name").setView(input).setNegativeButton("Cancel", null)
            .setPositiveButton("Save") { _, _ -> commit(document.copy(title = input.text.toString().trim().ifBlank { "Untitled design" })) }.show()
    }
    private fun draftsSheet() {
        saveNow()
        sheet("Saved designs") { content, dialog ->
            content.addView(button("＋ New blank design") {
                saveNow(); document = StudioDocument(); history.reset(document)
                canvas.selectedId = document.layers.firstOrNull()?.id; canvas.fitToScreen()
                refresh(); scheduleSave(); dialog.dismiss()
            })
            store.list().forEach { draft ->
                content.addView(button("${draft.title} · ${draft.width} × ${draft.height}") {
                    saveNow(); document = draft; history.reset(document); canvas.selectedId = draft.layers.lastOrNull()?.id
                    canvas.fitToScreen(); refresh(); dialog.dismiss()
                })
            }
        }
    }
    private fun exportSheet() {
        if (exporting) return
        saveNow()
        sheet("Export design") { content, dialog ->
            content.addView(button("Animated MP4 / GIF…") { dialog.dismiss(); animationSheet() })
            val formats = Spinner(requireContext()).apply { adapter = ArrayAdapter(requireContext(), android.R.layout.simple_spinner_dropdown_item, listOf("PNG · lossless", "JPG · smaller file")) }
            content.addView(label("Format", 14)); content.addView(formats)
            val width = numberInput(document.width)
            content.addView(label("Output width · 320–4096 pixels (maximum 8 megapixels)", 13)); content.addView(width)
            val transparent = CheckBox(requireContext()).apply { text = "Transparent background (PNG only)"; isChecked = !document.backgroundVisible; setTextColor(ink) }
            content.addView(transparent)
            var quality = 95
            val exportInfo = label("",14); content.addView(exportInfo)
            fun updateExportInfo() {
                val w = width.text.toString().toIntOrNull()
                val h = if (w != null) w.toLong()*document.height/document.width else 0
                exportInfo.text = if (w == null) "Enter an output width" else "$w × $h px · " +
                    if (formats.selectedItemPosition == 0) "PNG · ${if (transparent.isChecked) "transparent" else "with background"}" else "JPG · $quality% quality"
            }
            width.addTextChangedListener(object : android.text.TextWatcher {
                override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) = Unit
                override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) { updateExportInfo() }
                override fun afterTextChanged(s: android.text.Editable?) = Unit
            })
            transparent.setOnCheckedChangeListener { _,_ -> updateExportInfo() }
            formats.onItemSelectedListener = object : AdapterView.OnItemSelectedListener {
                override fun onItemSelected(parent: AdapterView<*>?, view: View?, position: Int, id: Long) { transparent.isEnabled = position == 0; if (position != 0) transparent.isChecked = false; updateExportInfo() }
                override fun onNothingSelected(parent: AdapterView<*>?) = Unit
            }
            slider(content, "JPG quality", 95f, 60f, 100f) { quality = it.roundToInt(); updateExportInfo() }
            updateExportInfo()
            content.addView(label("Preview and export use the same fonts, positions and effects. PNG transparency removes only the background.", 13))
            fun start(share: Boolean) {
                val w = width.text.toString().toIntOrNull()
                val h = if (w == null) 0L else w.toLong() * document.height / document.width
                if (w == null || w !in 320..4096 || h > 4096 || w.toLong() * h > 8_000_000) { width.error = "Choose a smaller output size (maximum 8 MP and 4096 per side)"; return }
                dialog.dismiss(); export(w, formats.selectedItemPosition == 0, transparent.isChecked, quality, share)
            }
            content.addView(button("Preview final design") {
                val w = width.text.toString().toIntOrNull()
                val h = if (w == null) 0L else w.toLong() * document.height / document.width
                if (w == null || w !in 320..4096 || h !in 1..4096 || w.toLong()*h > 8_000_000) { width.error = "Choose a valid output size"; return@button }
                val png = formats.selectedItemPosition == 0
                val snapshot = if (png && transparent.isChecked) document.copy(backgroundVisible = false)
                    else if (!png && !document.backgroundVisible) document.copy(backgroundVisible = true,backgroundStyle = "",backgroundImage = "",backgroundColor = Color.WHITE,gradientColor = Color.WHITE) else document
                openFullPreview(false,snapshot)
            })
            content.addView(button("Save to gallery") { start(false) })
            content.addView(button("Share…") { start(true) })
        }
    }
    private fun export(width: Int, png: Boolean, transparent: Boolean, quality: Int, share: Boolean) {
        exporting = true; exportButton.isEnabled = false; status.text = "Exporting…"
        val snapshot = document
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
        AlertDialog.Builder(requireContext()).setTitle("Start a video design").setItems(names) { _, which ->
            if (!saveNow()) { message("Save the current draft before starting a video template."); return@setItems }
            val text = StudioLayer(text = phrases[which], x = 540f, y = 960f, width = 900f, color = Color.WHITE,
                animation = LayerAnimation(if (which == 0) "words" else "typing", 500, 10000, 5500, which != 0))
            document = StudioDocument(title = names[which], width = 1080, height = 1920,
                backgroundColor = LuxeTheme.background, gradientColor = LuxeTheme.surface,
                backgroundStyle = listOf("LUXE_MOONLIT_LAKE", "LUXE_SUNSET_PEAK", "LUXE_LANTERN_NIGHT", "LUXE_MOSQUE_SUNSET")[which], layers = listOf(text),
                video = VideoSettings(10000, "zoom", if (which >= 2) "particles" else "none"))
            exitPreview(); history.reset(document); canvas.selectedId = text.id; canvas.fitToScreen(); refresh(); scheduleSave(); animationSheet()
        }.setNegativeButton("Cancel", null).show()
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
                trimEndMs = minOf(duration, document.movieDurationMs()), volume = if (role == "music") .3f else 1f)
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
                attachAudio(StudioAudio(path = file.path, name = "My recording", role = "voice", sourceDurationMs = duration, trimEndMs = minOf(duration, document.movieDurationMs())))
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
                content.addView(label(file.name, 13))
                val mime = if (file.extension == "mp4") "video/mp4" else "image/gif"
                val uri = FileProvider.getUriForFile(requireContext(), requireContext().packageName + ".provider", file)
                content.addView(button("Open / play") {
                    runCatching { startActivity(Intent(Intent.ACTION_VIEW).setDataAndType(uri,mime).addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)) }
                        .onFailure { message("No player is installed for this format. Use Share to choose another app.") }
                })
                content.addView(button("Share") { startActivity(Intent.createChooser(Intent(Intent.ACTION_SEND).apply {
                    type = mime; putExtra(Intent.EXTRA_STREAM,uri); clipData = ClipData.newRawUri("Animation",uri); addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                }, "Share animation")) })
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

    private fun sheet(title: String, showDone: Boolean = true, build: (LinearLayout, BottomSheetDialog) -> Unit) {
        val dialog = BottomSheetDialog(requireContext())
        val content = column().apply { setPadding(dp(20)); background = rounded(cream) }
        content.addView(View(requireContext()).apply { background = rounded(LuxeTheme.border) }, LinearLayout.LayoutParams(dp(40), dp(4)).apply { gravity = Gravity.CENTER_HORIZONTAL; bottomMargin = dp(16) })
        content.addView(label(title, 21).apply { setTypeface(Typeface.DEFAULT, Typeface.BOLD); setPadding(0, 0, 0, dp(12)) })
        build(content, dialog)
        if (showDone) content.addView(button("Done") { dialog.dismiss() })
        val scroll = ScrollView(requireContext()).apply { addView(content); isFillViewport = false }
        dialog.setContentView(scroll)
        dialog.setOnShowListener {
            dialog.findViewById<View>(com.google.android.material.R.id.design_bottom_sheet)?.let {
                it.layoutParams.height = (resources.displayMetrics.heightPixels * .52f).toInt()
                com.google.android.material.bottomsheet.BottomSheetBehavior.from(it).apply { state = com.google.android.material.bottomsheet.BottomSheetBehavior.STATE_EXPANDED; skipCollapsed = true }
            }
        }
        dialog.setOnDismissListener { if (view != null) { canvas.document = document; canvas.invalidate() } }
        dialog.show()
    }
    private fun slider(content: LinearLayout, name: String, initial: Float, min: Float, max: Float, live: Boolean = false, change: (Float) -> Unit) {
        val label = label("$name · ${String.format(java.util.Locale.ROOT, "%.2f", initial)}", 14)
        content.addView(label)
        content.addView(SeekBar(requireContext()).apply {
            this.max = 1000; progress = ((initial.coerceIn(min, max) - min) / (max - min) * 1000).toInt()
            var value = initial
            setOnSeekBarChangeListener(object : SeekBar.OnSeekBarChangeListener {
                override fun onProgressChanged(bar: SeekBar?, progress: Int, fromUser: Boolean) {
                    if (!fromUser) return
                    value = min + (max - min) * progress / 1000f
                    label.text = "$name · ${String.format(java.util.Locale.ROOT, "%.2f", value)}"
                    if (live) change(value)
                }
                override fun onStartTrackingTouch(bar: SeekBar?) = Unit
                override fun onStopTrackingTouch(bar: SeekBar?) { change(value) }
            })
        }, LinearLayout.LayoutParams(-1, dp(48)))
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

