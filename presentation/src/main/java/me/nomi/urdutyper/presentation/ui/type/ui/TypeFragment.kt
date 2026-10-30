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
    private val cream = 0xfff7f5ef.toInt()
    private val green = 0xff124b3e.toInt()
    private val ink = 0xff1d352e.toInt()
    private lateinit var canvas: StudioCanvas
    private lateinit var fonts: StudioFonts
    private lateinit var store: StudioStore
    private lateinit var document: StudioDocument
    private lateinit var status: TextView
    private lateinit var selection: TextView
    private lateinit var undoButton: MaterialButton
    private lateinit var redoButton: MaterialButton
    private lateinit var zoomButton: MaterialButton
    private lateinit var exportButton: MaterialButton
    private lateinit var modeButton: MaterialButton
    private lateinit var fontSizeControl: FontSizeControl
    private lateinit var gridButton: MaterialButton
    private lateinit var snapButton: MaterialButton
    private var gridEnabled = false
    private var snapEnabled = true
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
        val id = savedInstanceState?.getString("documentId") ?: arguments?.getString("draftId")
        document = id?.let { store.load(it) } ?: fromTemplate()
        history.reset(document)
    }

    private fun fromTemplate(): StudioDocument {
        val text = arguments?.getString("templateText").orEmpty()
        val style = arguments?.getString("templateStyle").orEmpty()
        val image = arguments?.getString("templateImagePath").orEmpty()
        val dark = if (arguments?.containsKey("templateDarkText") == true) arguments!!.getBoolean("templateDarkText")
            else if (style == "STUDIO_GREEN") false else TemplateStyle.values().firstOrNull { it.name == style }?.darkText ?: true
        return StudioDocument(backgroundStyle = style, backgroundImage = image,
            layers = listOf(StudioLayer(text = text.ifBlank { "خوابوں کو حقیقت بناؤ" },
                fontId = arguments?.getString("templateFont").orEmpty().ifBlank { "nastaliq" },
                color = if (dark) green else Color.WHITE)))
    }

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, state: Bundle?): View {
        val root = column().apply { setBackgroundColor(cream) }
        val header = row().apply { setPadding(dp(8)); gravity = Gravity.CENTER_VERTICAL }
        header.addView(button("‹", "Back to designs") { saveNow(); findNavController().navigateUp() }, LinearLayout.LayoutParams(dp(48), dp(48)))
        val titles = column()
        titles.addView(label(document.title, 17).apply {
            setTypeface(Typeface.DEFAULT, Typeface.BOLD); maxLines = 1
            setOnClickListener { rename() }
        })
        status = label("Draft saved on this device", 11)
        titles.addView(status)
        header.addView(titles, LinearLayout.LayoutParams(0, -2, 1f))
        exportButton = button("Export") { exportSheet() }
        exportButton.isEnabled = !exporting
        exportButton.setBackgroundColor(green); exportButton.setTextColor(Color.WHITE)
        header.addView(exportButton)
        root.addView(header)
        root.addView(horizontal {
            undoButton = button("↶ Undo") { history.undo()?.let { restore(it) } }
            redoButton = button("↷ Redo") { history.redo()?.let { restore(it) } }
            addView(undoButton); addView(redoButton)
            addView(button("Size") { sizeSheet() })
            addView(button("Animate / Video") { animationSheet() })
            gridButton = button(if (gridEnabled) "Grid ✓" else "Grid") {
                gridEnabled = !gridEnabled; canvas.showGrid = gridEnabled; canvas.invalidate()
                gridButton.text = if (gridEnabled) "Grid ✓" else "Grid"
            }
            snapButton = button(if (snapEnabled) "Snap ✓" else "Snap") {
                snapEnabled = !snapEnabled; canvas.snapToCenter = snapEnabled
                snapButton.text = if (snapEnabled) "Snap ✓" else "Snap"
            }
            addView(gridButton); addView(snapButton)
            addView(button("Drafts") { draftsSheet() })
            addView(button("Save") { if (saveNow()) toast("Editable draft saved") else message("Draft could not be saved. Check available storage and try again.") })
        })
        canvas = StudioCanvas(requireContext(), StudioRenderer(requireContext(), fonts)).apply {
            document = this@TypeFragment.document
            selectedId = document.layers.lastOrNull()?.id
            showGrid = gridEnabled; snapToCenter = snapEnabled
            onChange = { commit(it) }
            onSelect = { refresh() }
            onEdit = { editSelected() }
            onZoom = { zoomButton.text = "$it% · Fit" }
        }
        root.addView(canvas, LinearLayout.LayoutParams(-1, 0, 1f))
        val canvasControls = row().apply { gravity = Gravity.CENTER_VERTICAL; setPadding(dp(8), 0, dp(8), 0) }
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
        root.addView(horizontal {
            addView(button("Edit") { editSelected() })
            addView(button("Fonts") { fontsSheet() })
            addView(button("Color") { selectedText()?.let { layer -> colorPicker(layer.color) { replaceLayer(layer.copy(color = it)) } } })
            addView(button("Style") { styleSheet() })
            addView(button("Effects") { effectsSheet() })
            addView(button("Duplicate") { selected()?.let { duplicateLayer(it) } })
            addView(button("Center") { selected()?.takeIf { !it.locked }?.let { replaceLayer(it.copy(x = document.width / 2f, y = document.height / 2f)) } })
            addView(button("Delete") { selected()?.takeIf { !it.locked }?.let { layer -> commit(document.copy(layers = document.layers.filterNot { it.id == layer.id })) } })
        })
        root.addView(horizontal {
            addView(button("+ Text") { textDialog(null) })
            addView(button("+ Image") { pickImage(false) })
            addView(button("Background") { backgroundSheet() })
            addView(button("Layers") { layersSheet() })
        })
        return root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        refresh()
        scheduleSave()
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

    private fun selected(): StudioLayer? = document.layers.firstOrNull { it.id == canvas.selectedId }
    private fun selectedText(): StudioLayer? {
        val layer = selected()
        return if (layer == null || layer.kind != "text" || layer.locked) {
            toast(if (layer?.locked == true) "Unlock this layer in Layers first" else "Select a text layer first"); null
        } else layer
    }
    private fun commit(next: StudioDocument) {
        if (next.copy(updatedAt = document.updatedAt) == document) return
        document = next.copy(updatedAt = maxOf(System.currentTimeMillis(), document.updatedAt + 1))
        history.record(document)
        refresh(); scheduleSave()
    }
    private fun restore(snapshot: StudioDocument) {
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
        if (::fontSizeControl.isInitialized) fontSizeControl.showSelection(layer?.takeIf { it.kind == "text" }?.size, layer != null && !layer.locked && layer.visible)
        selection.text = when {
            layer == null -> "${document.width} × ${document.height} · Select a layer"
            layer.locked -> "Locked · use Layers to unlock"
            !layer.visible -> "Hidden · show it in Layers"
            layer.kind == "image" -> "Image · drag or resize"
            else -> (fonts.all().firstOrNull { it.id == layer.fontId }?.name ?: "Nastaliq") + " · ${layer.size.roundToInt()}"
        }
        undoButton.isEnabled = history.canUndo(); redoButton.isEnabled = history.canRedo()
        (status.parent as? LinearLayout)?.let { (it.getChildAt(0) as? TextView)?.text = document.title }
    }
    private fun scheduleSave() {
        if (view == null) return
        saveJob?.cancel()
        status.text = "Saving draft…"
        val snapshot = document
        saveJob = viewLifecycleOwner.lifecycleScope.launch {
            delay(600)
            runCatching { withContext(Dispatchers.IO) { store.save(snapshot) } }
                .onSuccess { status.text = "Draft saved on this device" }
                .onFailure { status.text = "Save failed · tap Save to retry" }
        }
    }
    private fun saveNow(): Boolean {
        if (::fontSizeControl.isInitialized) fontSizeControl.finishEditing()
        saveJob?.cancel()
        return runCatching { store.save(document) }
            .onSuccess { if (::status.isInitialized) status.text = "Draft saved on this device" }
            .onFailure { if (::status.isInitialized) status.text = "Save failed · tap Save to retry" }
            .isSuccess
    }
    override fun onStop() {
        speechJob?.cancel(); speechDialog?.dismiss(); speechDialog = null
        animationPreview?.dismiss(); animationPreview = null
        videoProgressDialog?.dismiss()
        stopRecording(false); saveNow(); super.onStop()
    }
    override fun onSaveInstanceState(outState: Bundle) {
        saveNow(); outState.putString("documentId", document.id)
        outState.putString("pendingFontLayer", pendingFontLayer)
        outState.putBoolean("imageAsBackground", imageAsBackground)
        outState.putString("audioRole", audioRole)
        outState.putBoolean("gridEnabled", gridEnabled); outState.putBoolean("snapEnabled", snapEnabled)
        super.onSaveInstanceState(outState)
    }
    override fun onDestroyView() { saveJob?.cancel(); super.onDestroyView() }

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
        sheet("Choose font") { content, dialog ->
            content.addView(label("Each preview uses its actual font. Imported fonts stay on this device.", 13))
            content.addView(button("Import TTF / OTF font") {
                pendingFontLayer = layer.id; dialog.dismiss(); importFont.launch(arrayOf("*/*"))
            })
            fonts.all().forEach { font ->
                val item = column().apply {
                    setPadding(dp(12)); background = rounded(if (font.id == layer.fontId) 0xffdbece3.toInt() else Color.WHITE)
                }
                item.addView(label((if (font.id == layer.fontId) "✓ " else "") + font.name, 13))
                item.addView(label(if (font.urdu) "خوابوں کو حقیقت بناؤ" else "Create something beautiful", 25).apply {
                    typeface = fonts.resolve(font.id); gravity = Gravity.CENTER; setPadding(0, dp(10), 0, dp(10))
                })
                item.setOnClickListener {
                    if (!font.urdu && layer.text.any { it.code in 0x0600..0x08ff }) {
                        message("This font contains Latin letters only. Choose an Urdu font for Urdu text.")
                    } else { replaceLayer(layer.copy(fontId = font.id)); dialog.dismiss() }
                }
                content.addView(item, LinearLayout.LayoutParams(-1, -2).apply { bottomMargin = dp(8) })
            }
        }
    }
    private fun styleSheet() {
        val layer = selected() ?: run { toast("Select a layer first"); return }
        if (layer.locked) { toast("Unlock this layer first"); return }
        sheet("Layer style") { content, dialog ->
            if (layer.kind == "text") {
                content.addView(button(if (layer.bold) "✓ Bold" else "Bold") { replaceLayer(selected()!!.copy(bold = !selected()!!.bold)); dialog.dismiss(); styleSheet() })
                content.addView(button(if (layer.italic) "✓ Italic" else "Italic") { replaceLayer(selected()!!.copy(italic = !selected()!!.italic)); dialog.dismiss(); styleSheet() })
                slider(content, "Font size", layer.size, 8f, 600f) { replaceLayer(selected()!!.copy(size = it)) }
                slider(content, "Line spacing", layer.lineSpacing, .8f, 2.4f) { replaceLayer(selected()!!.copy(lineSpacing = it)) }
                val align = row()
                listOf("left", "center", "right").forEach { value -> align.addView(button(value.replaceFirstChar { it.uppercase() }) { replaceLayer(selected()!!.copy(alignment = value)) }) }
                content.addView(align)
            }
            slider(content, "Box width", layer.width, 40f, document.width * 1.5f) { replaceLayer(selected()!!.copy(width = it)) }
            if (layer.kind == "image") slider(content, "Image height", layer.height, 40f, document.height * 1.5f) { replaceLayer(selected()!!.copy(height = it)) }
            slider(content, "Rotation", layer.rotation, 0f, 359f) { replaceLayer(selected()!!.copy(rotation = it)) }
            slider(content, "Opacity", layer.opacity, .05f, 1f) { replaceLayer(selected()!!.copy(opacity = it)) }
            content.addView(button("Center on canvas") { replaceLayer(selected()!!.copy(x = document.width / 2f, y = document.height / 2f)) })
            content.addView(label("Drag the bottom-right handle to resize. Use two fingers to resize and rotate together.", 13))
        }
    }
    private fun effectsSheet() {
        val layer = selectedText() ?: return
        val chosen = layer.effects.toMutableSet()
        AlertDialog.Builder(requireContext()).setTitle("Text effects")
            .setMultiChoiceItems(STUDIO_EFFECTS.toTypedArray(), STUDIO_EFFECTS.map { it in chosen }.toBooleanArray()) { _, which, checked ->
                if (checked) chosen.add(STUDIO_EFFECTS[which]) else chosen.remove(STUDIO_EFFECTS[which])
            }.setNeutralButton("Clear") { _, _ -> replaceLayer(layer.copy(effects = emptyList())) }
            .setNegativeButton("Cancel", null).setPositiveButton("Apply") { _, _ -> replaceLayer(layer.copy(effects = chosen.toList())) }.show()
    }
    private fun colorPicker(initial: Int, onPick: (Int) -> Unit) {
        val body = column().apply { setPadding(dp(20)) }
        val input = EditText(requireContext()).apply { setText(String.format("#%08X", initial)); hint = "#RRGGBB or #AARRGGBB"; isSingleLine = true }
        body.addView(input)
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
        } }; dialog.show()
    }
    private fun pickImage(background: Boolean) { imageAsBackground = background; importImage.launch("image/*") }
    private fun backgroundSheet() {
        sheet("Background") { content, dialog ->
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
                card.setOnClickListener { commit(document.copy(backgroundStyle = entry.styleName, backgroundImage = "", backgroundVisible = true)); dialog.dismiss() }
                content.addView(card)
            }
        }
    }
    private fun layersSheet() {
        sheet("Layers · top first") { content, dialog ->
            document.layers.asReversed().forEach { layer ->
                val card = column().apply { setPadding(dp(8)); background = rounded(if (layer.id == canvas.selectedId) 0xffdbece3.toInt() else Color.WHITE) }
                card.addView(button((if (layer.id == canvas.selectedId) "✓ " else "") + if (layer.kind == "image") "Image" else layer.text.take(28)) { canvas.selectedId = layer.id; refresh(); dialog.dismiss() })
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
        val options = arrayOf("Square · 1080 × 1080", "Portrait post · 1080 × 1350", "Story · 1080 × 1920", "Landscape · 1920 × 1080", "Custom size")
        AlertDialog.Builder(requireContext()).setTitle("Canvas size").setItems(options) { _, which ->
            val sizes = listOf(1080 to 1080, 1080 to 1350, 1080 to 1920, 1920 to 1080)
            if (which < sizes.size) { val (w, h) = sizes[which]; commit(document.resized(w, h)); canvas.fitToScreen() }
            else {
                val content = column().apply { setPadding(dp(20)) }
                val width = numberInput(document.width); val height = numberInput(document.height)
                content.addView(label("Width · 320–4096 pixels", 14)); content.addView(width)
                content.addView(label("Height · 320–4096 pixels", 14)); content.addView(height)
                val dialog = AlertDialog.Builder(requireContext()).setTitle("Custom canvas").setView(content).setNegativeButton("Cancel", null).setPositiveButton("Apply", null).create()
                dialog.setOnShowListener { dialog.getButton(AlertDialog.BUTTON_POSITIVE).setOnClickListener {
                    val w = width.text.toString().toIntOrNull(); val h = height.text.toString().toIntOrNull()
                    if (w == null || h == null || w !in 320..4096 || h !in 320..4096) { width.error = "Both sizes must be 320–4096" }
                    else { commit(document.resized(w, h)); canvas.fitToScreen(); dialog.dismiss() }
                } }; dialog.show()
            }
        }.show()
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
            formats.onItemSelectedListener = object : AdapterView.OnItemSelectedListener {
                override fun onItemSelected(parent: AdapterView<*>?, view: View?, position: Int, id: Long) { transparent.isEnabled = position == 0; if (position != 0) transparent.isChecked = false }
                override fun onNothingSelected(parent: AdapterView<*>?) = Unit
            }
            var quality = 95
            slider(content, "JPG quality", 95f, 60f, 100f) { quality = it.roundToInt() }
            content.addView(label("Preview and export use the same fonts, positions and effects. PNG transparency removes only the background.", 13))
            fun start(share: Boolean) {
                val w = width.text.toString().toIntOrNull()
                val h = if (w == null) 0L else w.toLong() * document.height / document.width
                if (w == null || w !in 320..4096 || h > 4096 || w.toLong() * h > 8_000_000) { width.error = "Choose a smaller output size (maximum 8 MP and 4096 per side)"; return }
                dialog.dismiss(); export(w, formats.selectedItemPosition == 0, transparent.isChecked, quality, share)
            }
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

    private fun animationSheet() {
        sheet("Animation & video", showDone = false) { content, dialog ->
            val duration = secondsInput(document.video.durationMs)
            content.addView(label("Scene duration · 1–60 seconds", 14)); content.addView(duration)
            val motion = Spinner(requireContext()).apply {
                adapter = ArrayAdapter(requireContext(), android.R.layout.simple_spinner_dropdown_item, listOf("none", "zoom", "pan"))
                setSelection(listOf("none", "zoom", "pan").indexOf(document.video.backgroundMotion).coerceAtLeast(0))
            }
            content.addView(label("Background motion", 14)); content.addView(motion)
            val atmosphere = Spinner(requireContext()).apply {
                adapter = ArrayAdapter(requireContext(), android.R.layout.simple_spinner_dropdown_item, listOf("none", "rain", "particles"))
                setSelection(listOf("none", "rain", "particles").indexOf(document.video.atmosphere).coerceAtLeast(0))
            }
            content.addView(label("Atmosphere", 14)); content.addView(atmosphere)
            val loop = CheckBox(requireContext()).apply { text = "Loop preview"; isChecked = document.video.loopPreview; setTextColor(ink) }
            content.addView(loop)
            val layer = selected()?.takeIf { !it.locked }
            val effects = if (layer?.kind == "image") listOf("none", "fade", "slide", "zoom") else AnimationTimeline.effects
            val effect = Spinner(requireContext()).apply {
                adapter = ArrayAdapter(requireContext(), android.R.layout.simple_spinner_dropdown_item, effects)
                setSelection(effects.indexOf(layer?.animation?.effect).coerceAtLeast(0))
            }
            val start = secondsInput(layer?.animation?.startMs ?: 0)
            val end = secondsInput(minOf(layer?.animation?.endMs ?: document.video.durationMs, document.video.durationMs))
            val reveal = secondsInput(layer?.animation?.revealMs ?: 3000)
            val cursor = CheckBox(requireContext()).apply { text = "Blinking typing cursor"; isChecked = layer?.animation?.cursor == true; setTextColor(ink) }
            val repeat = CheckBox(requireContext()).apply { text = "Repeat selected layer animation (1 second hold)"; isChecked = layer?.animation?.repeat == true; setTextColor(ink) }
            if (layer != null) {
                content.addView(label("Selected layer · ${if (layer.kind == "text") layer.text.take(24) else "Image"}", 17))
                content.addView(effect)
                content.addView(label("Appear at · seconds", 14)); content.addView(start)
                content.addView(label("Disappear at · seconds", 14)); content.addView(end)
                content.addView(label("Reveal duration · 0.1–60 seconds (smaller = faster)", 14)); content.addView(reveal)
                if (layer.kind == "text") content.addView(cursor)
                content.addView(repeat)
                content.addView(label("Typing reveals complete Urdu characters. Words reveals one word at a time. Highlight follows this timing; align it with your recording manually.", 13))
            } else content.addView(label("Select an unlocked text or image layer to set its individual timing.", 13))
            fun apply(): Boolean {
                val total = milliseconds(duration)
                if (total == null || total !in 1000..60000) { duration.error = "Enter 1–60 seconds"; return false }
                var layers = document.layers
                if (layer != null) {
                    val from = milliseconds(start); val until = milliseconds(end); val speed = milliseconds(reveal)
                    if (from == null || until == null || from >= until || until > total) { end.error = "End must follow start and fit inside the scene"; return false }
                    if (speed == null || speed !in 100..60000) { reveal.error = "Enter 0.1–60 seconds"; return false }
                    val animation = LayerAnimation(effect.selectedItem.toString(), from, until, speed, cursor.isChecked, repeat.isChecked)
                    layers = layers.map { if (it.id == layer.id) it.copy(animation = animation) else it }
                }
                commit(document.copy(video = VideoSettings(total, motion.selectedItem.toString(), atmosphere.selectedItem.toString(), loop.isChecked), layers = layers))
                return true
            }
            content.addView(button("Apply animation") { if (apply()) { dialog.dismiss(); toast("Animation and timeline saved") } })
            content.addView(button("Preview with audio") { if (apply()) { dialog.dismiss(); animationPreview = AnimationPreviewDialog(requireContext(), document).also { it.show() } } })
            content.addView(button("Voice / music…") { if (apply()) { dialog.dismiss(); audioSheet() } })
            content.addView(button("Layer timeline…") { if (apply()) { dialog.dismiss(); timelineSheet() } })
            content.addView(button("Export MP4 / GIF…") { if (apply()) { dialog.dismiss(); motionExportSheet() } })
            content.addView(button("Video templates…") { dialog.dismiss(); videoTemplates() })
            content.addView(button("Cancel") { dialog.dismiss() })
        }
    }
    private fun videoTemplates() {
        val names = arrayOf("Poetry reel", "Animated quote", "Birthday greeting", "Invitation")
        val phrases = listOf("خوابوں کو حقیقت بناؤ", "ہر دن ایک نئی امید ہے", "سالگرہ مبارک", "آپ کی آمد ہمارے لیے باعثِ مسرت ہوگی")
        AlertDialog.Builder(requireContext()).setTitle("Start a video design").setItems(names) { _, which ->
            saveNow()
            val text = StudioLayer(text = phrases[which], x = 540f, y = 960f, width = 900f, color = Color.WHITE,
                animation = LayerAnimation(if (which == 0) "words" else "typing", 500, 10000, 5500, which != 0))
            document = StudioDocument(title = names[which], width = 1080, height = 1920,
                backgroundColor = green, gradientColor = 0xff356e59.toInt(), layers = listOf(text),
                video = VideoSettings(10000, "zoom", if (which >= 2) "particles" else "none"))
            history.reset(document); canvas.selectedId = text.id; canvas.fitToScreen(); refresh(); scheduleSave(); animationSheet()
        }.setNegativeButton("Cancel", null).show()
    }
    private fun timelineSheet() {
        sheet("Layer timeline") { content, dialog ->
            content.addView(label("Scene: ${document.video.durationMs / 1000f}s. Tap an unlocked layer to edit its animation and timing.",13))
            document.layers.forEach { layer ->
                val a = layer.animation
                content.addView(button("${if (layer.kind == "text") layer.text.take(22) else "Image"} · ${a.effect}\n${a.startMs / 1000f}s – ${minOf(a.endMs,document.video.durationMs) / 1000f}s${if (layer.locked) " · locked" else ""}") {
                    if (layer.locked) toast("Unlock this layer in Layers first")
                    else { canvas.selectedId = layer.id; refresh(); dialog.dismiss(); animationSheet() }
                })
            }
            document.audio.forEach { audio -> content.addView(label("${audio.role}: ${audio.startMs / 1000f}s – ${minOf(audio.startMs + audio.durationMs(),document.video.durationMs) / 1000f}s",13)) }
        }
    }
    private fun attachAudio(track: StudioAudio) {
        commit(document.copy(audio = document.audio.filterNot { it.role == track.role } + track))
    }
    private fun audioSheet() {
        sheet("Voice & music") { content, dialog ->
            content.addView(label("One voice track + one music track. Both are mixed into MP4. GIF has no sound. Align text timings manually in Animation & video.", 13))
            content.addView(button("Record my voice") { dialog.dismiss(); microphonePermission.launch(android.Manifest.permission.RECORD_AUDIO) })
            content.addView(button("Import voice audio") { audioRole = "voice"; dialog.dismiss(); importAudio.launch("audio/*") })
            content.addView(button("Import music") { audioRole = "music"; dialog.dismiss(); importAudio.launch("audio/*") })
            content.addView(button("Urdu text → voice (phone engine)") { dialog.dismiss(); generateVoice() })
            document.audio.forEach { track ->
                content.addView(label("${track.role}: ${track.name}", 16))
                content.addView(button("Timing / trim / volume / fades") { dialog.dismiss(); editAudio(track) })
                content.addView(button("Remove ${track.role}") { commit(document.copy(audio = document.audio.filterNot { it.id == track.id })); dialog.dismiss(); audioSheet() })
            }
        }
    }
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
                commit(document.copy(video = document.video.copy(durationMs = maxOf(document.video.durationMs,minOf(duration + 1000,60000))),
                    audio = document.audio.filterNot { it.role == "voice" } + track))
            }.onFailure {
                file.delete()
                if (it !is kotlinx.coroutines.CancellationException && view != null) message(it.message ?: "Could not generate Urdu voice")
            }.onSuccess { if (view != null) toast("Urdu voice added. Set layer timings to match it.") }
            speechDialog?.dismiss(); speechDialog = null; speechJob = null
        }
    }
    private fun editAudio(track: StudioAudio) {
        val body = column().apply { setPadding(dp(16)) }
        val start = secondsInput(track.startMs); val trimIn = secondsInput(track.trimStartMs); val trimOut = secondsInput(track.trimEndMs)
        val fadeIn = secondsInput(track.fadeInMs); val fadeOut = secondsInput(track.fadeOutMs)
        listOf("Start in video (seconds)" to start, "Trim from source (seconds)" to trimIn, "Trim until (seconds)" to trimOut,
            "Fade in (seconds)" to fadeIn, "Fade out (seconds)" to fadeOut).forEach { (title, input) -> body.addView(label(title, 13)); body.addView(input) }
        val volume = SeekBar(requireContext()).apply { max = 100; progress = (track.volume * 100).toInt() }
        body.addView(label("Volume · 0–100%", 13)); body.addView(volume)
        body.addView(label("Source length: ${track.sourceDurationMs / 1000f}s. Only the part inside the scene is exported.", 12))
        val scroll = ScrollView(requireContext()).apply { addView(body) }
        val dialog = AlertDialog.Builder(requireContext()).setTitle(track.name).setView(scroll).setNegativeButton("Cancel", null).setPositiveButton("Apply", null).create()
        dialog.setOnShowListener { dialog.getButton(AlertDialog.BUTTON_POSITIVE).setOnClickListener {
            val offset = milliseconds(start); val from = milliseconds(trimIn); val until = milliseconds(trimOut)
            val fi = milliseconds(fadeIn); val fo = milliseconds(fadeOut)
            if (offset == null || offset >= document.video.durationMs) { start.error = "Start must be inside the scene"; return@setOnClickListener }
            if (from == null || until == null || from >= until || until > track.sourceDurationMs) { trimOut.error = "Choose a valid range inside the source"; return@setOnClickListener }
            if (fi == null || fo == null || fi > until - from || fo > until - from) { fadeOut.error = "Fades must fit the trimmed clip"; return@setOnClickListener }
            attachAudio(track.copy(startMs = offset, trimStartMs = from, trimEndMs = until, volume = volume.progress / 100f, fadeInMs = fi, fadeOutMs = fo))
            dialog.dismiss(); audioSheet()
        } }; dialog.show()
    }
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
                trimEndMs = minOf(duration, document.video.durationMs), volume = if (role == "music") .3f else 1f)
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
                attachAudio(StudioAudio(path = file.path, name = "My recording", role = "voice", sourceDurationMs = duration, trimEndMs = minOf(duration, document.video.durationMs)))
            }.onSuccess { if (view != null) toast("Voice recording added") }
                .onFailure { file.delete(); if (view != null) message("Recording was too short. Please try again.") }
        } else file?.delete()
    }
    private fun motionExportSheet() {
        if (exporting) return
        sheet("Export animation") { content, dialog ->
            val quality = Spinner(requireContext()).apply { adapter = ArrayAdapter(requireContext(), android.R.layout.simple_spinner_dropdown_item, listOf("720p MP4", "1080p MP4", "GIF · 360px, no audio, up to 10s")) }
            content.addView(quality)
            val (w, h) = StudioVideoExporter.dimensions(document, 720)
            content.addView(label("MP4: H.264 video + AAC voice/music, 24 fps. Your canvas ratio is preserved ($w × $h at 720p). GIF uses 256 colors and has no audio.", 13))
            fun start(share: Boolean) { dialog.dismiss(); exportMotion(quality.selectedItemPosition, share) }
            content.addView(button("Save animation") { start(false) })
            content.addView(button("Share animation…") { start(true) })
            content.addView(button("Saved animations…") { dialog.dismiss(); savedAnimations() })
        }
    }
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
        if (format == 2 && document.video.durationMs > 10000) { message("GIF supports up to 10 seconds. Choose MP4 for longer scenes."); return }
        saveNow(); exporting = true; cancelVideo = false; exportButton.isEnabled = false
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
                else AlertDialog.Builder(requireContext()).setMessage("Animation saved on this device. Use Share to send it to WhatsApp or another app.")
                    .setPositiveButton("Share") { _, _ -> startActivity(Intent.createChooser(Intent(Intent.ACTION_SEND).apply { type = mime; putExtra(Intent.EXTRA_STREAM,uri); clipData = ClipData.newRawUri("Animation",uri); addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION) },"Share animation")) }
                    .setNegativeButton("Done",null).show()
            }.onFailure { status.text = "Draft saved"; message(if (cancelVideo) "Export cancelled" else it.message ?: "Could not export animation. Try 720p or a shorter scene.") }
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
        val content = column().apply { setPadding(dp(20)); setBackgroundColor(cream) }
        content.addView(label(title, 21).apply { setTypeface(Typeface.DEFAULT, Typeface.BOLD); setPadding(0, 0, 0, dp(12)) })
        build(content, dialog)
        if (showDone) content.addView(button("Done") { dialog.dismiss() })
        val scroll = ScrollView(requireContext()).apply { addView(content); isFillViewport = false }
        dialog.setContentView(scroll)
        dialog.setOnShowListener {
            dialog.findViewById<View>(com.google.android.material.R.id.design_bottom_sheet)?.let {
                it.layoutParams.height = (resources.displayMetrics.heightPixels * .72f).toInt()
                com.google.android.material.bottomsheet.BottomSheetBehavior.from(it).apply { state = com.google.android.material.bottomsheet.BottomSheetBehavior.STATE_EXPANDED; skipCollapsed = true }
            }
        }
        dialog.show()
    }
    private fun slider(content: LinearLayout, name: String, initial: Float, min: Float, max: Float, change: (Float) -> Unit) {
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
        insetTop = 0; insetBottom = 0; cornerRadius = dp(16)
        setPadding(dp(12), dp(4), dp(12), dp(4)); setOnClickListener { click() }
    }
    private fun rounded(color: Int) = GradientDrawable().apply { setColor(color); cornerRadius = dp(16).toFloat() }
    private fun dp(value: Int) = (value * resources.displayMetrics.density).roundToInt()
    private fun toast(value: String) { Toast.makeText(requireContext(), value, Toast.LENGTH_SHORT).show() }
    private fun message(value: String) { AlertDialog.Builder(requireContext()).setMessage(value).setPositiveButton("OK", null).show() }
}
