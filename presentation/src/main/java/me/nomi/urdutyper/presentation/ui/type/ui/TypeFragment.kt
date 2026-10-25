package me.nomi.urdutyper.presentation.ui.type.ui

import android.Manifest
import android.app.Dialog
import android.graphics.BitmapFactory
import android.graphics.Color
import android.graphics.LinearGradient
import android.graphics.Paint
import android.graphics.Shader
import android.graphics.Typeface
import android.graphics.drawable.BitmapDrawable
import android.graphics.drawable.ColorDrawable
import android.graphics.drawable.Drawable
import android.graphics.drawable.GradientDrawable
import android.os.Build
import android.os.Bundle
import android.view.Gravity
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.view.WindowManager
import android.view.inputmethod.EditorInfo
import android.widget.SeekBar
import androidx.activity.addCallback
import androidx.core.content.FileProvider
import androidx.core.content.res.ResourcesCompat
import androidx.core.view.isVisible
import androidx.databinding.DataBindingUtil
import androidx.databinding.ViewDataBinding
import androidx.fragment.app.activityViewModels
import androidx.navigation.fragment.findNavController
import androidx.recyclerview.widget.GridLayoutManager
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.drop
import kotlinx.coroutines.launch
import me.nomi.urdutyper.R
import me.nomi.urdutyper.databinding.BackgroundColorDialogBinding
import me.nomi.urdutyper.databinding.BackgroundLibraryDialogBinding
import me.nomi.urdutyper.databinding.CanvasSizeDialogBinding
import me.nomi.urdutyper.databinding.ColorPickDialogBinding
import me.nomi.urdutyper.databinding.FontStyleDialogBinding
import me.nomi.urdutyper.databinding.FragmentTypeBinding
import me.nomi.urdutyper.databinding.LayersDialogBinding
import me.nomi.urdutyper.databinding.TextEffectDialogBinding
import me.nomi.urdutyper.presentation.app.base.BaseFragment
import me.nomi.urdutyper.presentation.ui.type.viewmodel.TypeViewModel
import me.nomi.urdutyper.presentation.ui.type.state.TypeUiState
import me.nomi.urdutyper.presentation.utils.common.ImageMaker
import me.nomi.urdutyper.presentation.utils.common.MultiTouchListener
import me.nomi.urdutyper.presentation.utils.common.Notification
import me.nomi.urdutyper.presentation.utils.extensions.common.dialog
import me.nomi.urdutyper.presentation.utils.extensions.views.hideKeyboard
import me.nomi.urdutyper.presentation.utils.extensions.views.launchAndRepeatWithViewLifecycle
import me.nomi.urdutyper.presentation.utils.extensions.views.showKeyboard
import me.nomi.urdutyper.presentation.utils.permissions.PermissionHandler
import me.nomi.urdutyper.presentation.utils.permissions.Permissions
import java.util.Random


@AndroidEntryPoint
class TypeFragment : BaseFragment<FragmentTypeBinding>() {
    var shouldShowSnackBar = true
    // True while a template's own background — or a background picked from
    // the library — is showing; cleared on any gradient/solid change.
    private var templateBackgroundApplied = false
    // Canva phase 2: canvas size. AUTO keeps the old wrap_content behaviour.
    private var canvasSize: CanvasSize = CanvasSize.AUTO
    // Text effect (Photoshop-style bevel): fragment-local like canvas size.
    // NONE = plain, EMBOSS = raised, ENGRAVE = carved.
    private enum class TextEffect { NONE, EMBOSS, ENGRAVE, GLOW, OUTLINE, GRADIENT }
    private var textEffect: TextEffect = TextEffect.NONE
    // Canva phase 4: layers panel state. The editor has exactly two layers.
    private var currentBackgroundLabel: String = "Default"
    private var backgroundLayerVisible = true
    private var savedBackground: Drawable? = null
    private var textLayerVisible = true
    override fun inflateViewBinding(inflater: LayoutInflater) =
        FragmentTypeBinding.inflate(layoutInflater)

    private val viewModel: TypeViewModel by activityViewModels()
    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        arguments?.getString("templateText")?.takeIf { it.isNotEmpty() }?.let {
            binding.edittext2.setText(it)
        }
        arguments?.getString("templateFont")?.takeIf { it.isNotEmpty() }?.let { fontKey ->
            ResourcesCompat.getFont(requireActivity(), fontRes(fontKey))?.let {
                viewModel.font.value = it
            }
        }
        arguments?.getString("templateStyle")?.takeIf { it.isNotEmpty() }?.let { styleName ->
            templateBackgroundApplied = true
            binding.root2.setBackgroundResource(styleRes(styleName))
            // Light backgrounds (paper) need dark text; the editor defaults to white.
            viewModel.textColor.value =
                if (styleDarkText(styleName)) Color.BLACK else Color.WHITE
            currentBackgroundLabel = styleLabel(styleName)
        }
        // User-added templates carry a gallery file instead of a style drawable.
        arguments?.getString("templateImagePath")?.takeIf { it.isNotEmpty() }?.let { path ->
            templateBackgroundApplied = true
            BitmapFactory.decodeFile(path)?.let {
                binding.root2.background = BitmapDrawable(resources, it)
            }
            viewModel.textColor.value =
                if (arguments?.getBoolean("templateDarkText") == true) Color.BLACK else Color.WHITE
            currentBackgroundLabel = "My Template"
        }
        binding.edittext2.setOnTouchListener(MultiTouchListener(requireActivity()))

        launchAndRepeatWithViewLifecycle {
            launch {
                viewModel.uiState.collectLatest { state ->
                    if (state is TypeUiState.Error) {
                        dialog(state.message).show()
                    }
                }
            }
            // drop(1): the flows' initial values must not overwrite a template's
            // own background. Without this, opening any template ended on the
            // default purple gradient: the collectors fired at once, cleared
            // templateBackgroundApplied via setGradientBackground, and the
            // background collector then applied the gradient over the template.
            // User changes still flow through (2nd+ emissions) and replace the
            // template background as designed.
            launch { viewModel.gradientOrientation.drop(1).collectLatest { setGradientBackground() } }
            launch { viewModel.leftGradientColor.drop(1).collectLatest { setGradientBackground() } }
            launch { viewModel.rightGradientColor.drop(1).collectLatest { setGradientBackground() } }
            launch { viewModel.size.collectLatest { binding.edittext2.textSize = it } }
            launch {
                viewModel.typeface.collectLatest {
                    binding.edittext2.setTypeface(
                        viewModel.font.value,
                        it
                    )
                }
            }
            launch {
                viewModel.textColor.collectLatest {
                    binding.edittext2.setTextColor(it)
                    binding.edittext2.setHintTextColor(
                        Color.argb(
                            70,
                            Color.red(it),
                            Color.green(it),
                            Color.blue(it)
                        )
                    )
                    // The bevel's shadow edge depends on the text colour, so
                    // re-apply the effect whenever the colour changes.
                    applyTextEffect()
                }
            }
            launch {
                viewModel.font.collectLatest {
                    it?.let { font ->
                        binding.edittext2.setTypeface(
                            font,
                            viewModel.typeface.value
                        )
                    }
                }
            }
            launch {
                viewModel.background.collectLatest {
                    // A template's own background wins until the user picks a new one.
                    if (!templateBackgroundApplied) binding.root2.background = it
                }
            }
        }

        val random = Random()

        if (viewModel.font.value == null) {
            viewModel.font.value =
                ResourcesCompat.getFont(requireActivity(), R.font.noto_nastaliq_urdu)
        }

        binding.root2.setOnClickListener {
            binding.menu2.collapse()
            binding.edittext2.clearFocus()
            hideKeyboard(it)
        }
        binding.edittext2.setOnClickListener {
            binding.menu2.collapse()
            showKeyboard(it)
        }
        binding.edittext2.setOnFocusChangeListener { view, b ->
            if (!b) {
                hideKeyboard(view)
            } else {
                binding.menu2.collapse()
                showKeyboard(view)
            }
        }
        binding.edittext2.setOnEditorActionListener { _, actionId: Int, _ -> actionId == EditorInfo.IME_ACTION_DONE }
        binding.saveImage2.setOnClickListener {
            checkPermission(object : PermissionHandler {
                override fun onGranted() {
                    binding.menu2.collapse()
                    binding.menu2.visibility = View.GONE
                    binding.edittext2.isCursorVisible = false
                    binding.edittext2.clearFocus()
                    // Forced justification: justificationMode leaves the very last line
                    // unjustified, so add a trailing newline for the capture only — it
                    // makes the last couplet line stretch to full width too. The canvas
                    // is re-laid out synchronously so the capture uses the new layout.
                    val editable = binding.edittext2.text
                    val addedNewline = editable.isNotEmpty() && !editable.endsWith("\n")
                    if (addedNewline) {
                        editable.append("\n")
                        binding.root2.measure(
                            View.MeasureSpec.makeMeasureSpec(binding.root2.width, View.MeasureSpec.EXACTLY),
                            View.MeasureSpec.makeMeasureSpec(binding.root2.height, View.MeasureSpec.EXACTLY)
                        )
                        binding.root2.layout(
                            binding.root2.left, binding.root2.top,
                            binding.root2.right, binding.root2.bottom
                        )
                    }
                    val file = ImageMaker.saveBitMap(requireActivity(), binding.root2)
                        .also { if (addedNewline) editable.delete(editable.length - 1, editable.length) }
                        ?: return
                    val uri = FileProvider.getUriForFile(
                        requireActivity(),
                        requireActivity().applicationContext.packageName + ".provider",
                        file
                    )
                    viewModel.uploadImage(file)
                    Notification.create(requireActivity(), file.nameWithoutExtension, uri)
                    binding.menu2.visibility = View.VISIBLE
                    binding.edittext2.isCursorVisible = true
                }

                override fun onDenied() {
                    dialog("Required Permissions are denied").show()
                }
            })
        }

        binding.saveImageHd2.setOnClickListener {
            checkPermission(object : PermissionHandler {
                override fun onGranted() {
                    binding.menu2.collapse()
                    // Off-screen render: the live view is never touched, so no
                    // menu hiding, cursor hiding or text modification is needed.
                    val file = ImageMaker.saveBitMapHighRes(
                        requireActivity(), binding.root2, binding.edittext2,
                        textEffect.ordinal
                    ) ?: return
                    val uri = FileProvider.getUriForFile(
                        requireActivity(),
                        requireActivity().applicationContext.packageName + ".provider",
                        file
                    )
                    viewModel.uploadImage(file)
                    Notification.create(requireActivity(), file.nameWithoutExtension, uri)
                }

                override fun onDenied() {
                    dialog("Required Permissions are denied").show()
                }
            })
        }

        binding.backgroundColor2.setOnClickListener {
            binding.menu2.collapse()
            createDialog(R.layout.background_color_dialog) { dialogBinding, dialog ->
                (dialogBinding as BackgroundColorDialogBinding).apply {
                    orientation.setOnClickListener {
                        viewModel.gradientOrientation.value =
                            when (viewModel.gradientOrientation.value) {
                                GradientDrawable.Orientation.TR_BL -> GradientDrawable.Orientation.TOP_BOTTOM
                                GradientDrawable.Orientation.TOP_BOTTOM -> GradientDrawable.Orientation.TL_BR
                                GradientDrawable.Orientation.TL_BR -> GradientDrawable.Orientation.LEFT_RIGHT
                                else -> GradientDrawable.Orientation.TR_BL
                            }
                    }
                    autoBackgroundColor.setOnClickListener { _ ->
                        if (solid.isChecked) {
                            viewModel.setSolidColor(
                                Color.rgb(
                                    random.nextInt(),
                                    random.nextInt(),
                                    random.nextInt()
                                )
                            )
                        } else {
                            viewModel.leftGradientColor.value = Color.rgb(
                                random.nextInt(),
                                random.nextInt(),
                                random.nextInt()
                            )
                            viewModel.rightGradientColor.value = Color.rgb(
                                random.nextInt(),
                                random.nextInt(),
                                random.nextInt()
                            )
                        }
                    }

                    gradient.setOnCheckedChangeListener { _, isChecked ->
                        orientation.isVisible = isChecked
                        leftGradient.isVisible = isChecked
                        rightGradient.isVisible = isChecked
                        solidBackgroundColor.isVisible = !isChecked
                    }


                    leftGradient.setOnClickListener {
                        showGradientBackgroundColorPickDialog(true)
                    }
                    rightGradient.setOnClickListener {
                        showGradientBackgroundColorPickDialog(false)
                    }

                    solidBackgroundColor.setOnClickListener { showSolidBackgroundColorPickDialog() }

                    // Canva phase 3 entry point: the app's own background styles.
                    backgroundLibrary.setOnClickListener {
                        dialog.dismiss()
                        showBackgroundLibraryDialog()
                    }
                }
            }.show()
        }

        binding.textStyle2.setOnClickListener {
            binding.menu2.collapse()
            createDialog(R.layout.font_style_dialog) { dialogBinding, dialog ->
                (dialogBinding as FontStyleDialogBinding).apply {
                    bold.isChecked = viewModel.isBold.value
                    italic.isChecked = viewModel.isItalic.value

                    bold.setOnCheckedChangeListener { _, isChecked ->
                        viewModel.isBold.value = isChecked
                        val typeface = when {
                            isChecked && italic.isChecked -> Typeface.BOLD_ITALIC
                            isChecked -> Typeface.BOLD
                            italic.isChecked -> Typeface.ITALIC
                            else -> Typeface.NORMAL
                        }
                        viewModel.typeface.value = typeface
                    }

                    italic.setOnCheckedChangeListener { _, isChecked ->
                        viewModel.isItalic.value = isChecked
                        val typeface = when {
                            isChecked && bold.isChecked -> Typeface.BOLD_ITALIC
                            isChecked -> Typeface.ITALIC
                            bold.isChecked -> Typeface.BOLD
                            else -> Typeface.NORMAL
                        }
                        viewModel.typeface.value = typeface
                    }

                    fontSize.progress = viewModel.size.value.toInt() - 20
                    alignLeft.setOnClickListener {
                        binding.edittext2.textAlignment = View.TEXT_ALIGNMENT_VIEW_START
                        binding.edittext2.textSize = viewModel.size.value + 1
                        binding.edittext2.textSize = viewModel.size.value - 1
                    }
                    alignRight.setOnClickListener {
                        binding.edittext2.textAlignment = View.TEXT_ALIGNMENT_VIEW_END
                        binding.edittext2.textSize = viewModel.size.value + 1
                        binding.edittext2.textSize = viewModel.size.value - 1
                    }
                    alignCenter.setOnClickListener {
                        binding.edittext2.textAlignment = View.TEXT_ALIGNMENT_CENTER
                        binding.edittext2.textSize = viewModel.size.value + 1
                        binding.edittext2.textSize = viewModel.size.value - 1
                    }
                    autoTextColor.setOnClickListener {
                        viewModel.textColor.value =
                            Color.rgb(random.nextInt(), random.nextInt(), random.nextInt())
                    }

                    textColor.setOnClickListener { showTextColorPickDialog() }

                    myFont.setOnClickListener {
                        dialog.dismiss()
                        findNavController().navigate(TypeFragmentDirections.toFontsFragment())
                    }

                    fontSize.setOnSeekBarChangeListener(object : SeekBar.OnSeekBarChangeListener {
                        override fun onProgressChanged(
                            seekBar: SeekBar?,
                            progress: Int,
                            fromUser: Boolean
                        ) {
                            viewModel.size.value = (progress + 20).toFloat()
                        }

                        override fun onStartTrackingTouch(seekBar: SeekBar?) {}
                        override fun onStopTrackingTouch(seekBar: SeekBar?) {}
                    })
                }
            }.show()
        }


        binding.canvasSize2.setOnClickListener {
            binding.menu2.collapse()
            showCanvasSizeDialog()
        }

        binding.layers2.setOnClickListener {
            binding.menu2.collapse()
            showLayersDialog()
        }

        binding.textEffect2.setOnClickListener {
            binding.menu2.collapse()
            showTextEffectDialog()
        }

        requireActivity().onBackPressedDispatcher.addCallback(viewLifecycleOwner) {
            if (binding.edittext2.isFocused) {
                binding.edittext2.clearFocus()
            } else {
                isEnabled = false
                activity?.onBackPressed()
            }
        }
    }

    private fun showColorPickDialog(
        initialColor: Int,
        onColorSelected: (Int) -> Unit
    ) {
        createDialog(R.layout.color_pick_dialog) { dialogBinding, dialog ->
            (dialogBinding as ColorPickDialogBinding).apply {
                picker.showOldCenterColor = false
                picker.addSVBar(svbar)
                picker.setNewCenterColor(initialColor)
                picker.color = if (initialColor != Color.BLACK) initialColor else -0xffffff
                okButton.setOnClickListener { _ ->
                    val selectedColor = picker.color
                    onColorSelected(selectedColor)
                    dialog.dismiss()
                }
            }
        }.show()
    }

    private fun showTextColorPickDialog() {
        showColorPickDialog(
            binding.edittext2.textColors.defaultColor
        ) { selectedColor ->
            viewModel.textColor.value = selectedColor
        }
    }

    private fun showSolidBackgroundColorPickDialog() {
        showColorPickDialog(
            viewModel.leftGradientColor.value
        ) { selectedColor ->
            viewModel.leftGradientColor.value = selectedColor
            viewModel.rightGradientColor.value = selectedColor
            viewModel.setSolidColor(selectedColor)
        }
    }

    private fun showGradientBackgroundColorPickDialog(isLeft: Boolean) {
        showColorPickDialog(
            if (isLeft) viewModel.leftGradientColor.value else viewModel.rightGradientColor.value
        ) { selectedColor ->
            if (isLeft) {
                viewModel.leftGradientColor.value = selectedColor
            } else {
                viewModel.rightGradientColor.value = selectedColor
            }
        }
    }

    private fun setGradientBackground() {
        // Any user background change replaces the template's background.
        templateBackgroundApplied = false
        // A solid pick sets both colors equal, so the label follows the
        // actual look instead of the dialog's Solid/Gradient toggle.
        currentBackgroundLabel =
            if (viewModel.leftGradientColor.value == viewModel.rightGradientColor.value) {
                "Solid color"
            } else {
                "Gradient"
            }
        backgroundLayerVisible = true
        viewModel.background.value = GradientDrawable(
            viewModel.gradientOrientation.value,
            intArrayOf(
                viewModel.leftGradientColor.value,
                viewModel.rightGradientColor.value
            )
        )
    }

    // ---- Canva phase 2: canvas sizes ----
    // Photoshop-style text effects. Live editor limits, stated honestly in
    // the dialog: bevel shows one shadow edge only, outline is stroke-only
    // (no fill); glow and gradient show fully. The HD export draws every
    // effect in full (see ImageMaker.saveBitMapHighRes).
    private fun showTextEffectDialog() {
        createDialog(R.layout.text_effect_dialog) { dialogBinding, dialog ->
            (dialogBinding as TextEffectDialogBinding).apply {
                when (textEffect) {
                    TextEffect.NONE -> effectNone.isChecked = true
                    TextEffect.EMBOSS -> effectEmboss.isChecked = true
                    TextEffect.ENGRAVE -> effectEngrave.isChecked = true
                    TextEffect.GLOW -> effectGlow.isChecked = true
                    TextEffect.OUTLINE -> effectOutline.isChecked = true
                    TextEffect.GRADIENT -> effectGradient.isChecked = true
                }
                effectNone.setOnClickListener {
                    textEffect = TextEffect.NONE; applyTextEffect(); dialog.dismiss()
                }
                effectEmboss.setOnClickListener {
                    textEffect = TextEffect.EMBOSS; applyTextEffect(); dialog.dismiss()
                }
                effectEngrave.setOnClickListener {
                    textEffect = TextEffect.ENGRAVE; applyTextEffect(); dialog.dismiss()
                }
                effectGlow.setOnClickListener {
                    textEffect = TextEffect.GLOW; applyTextEffect(); dialog.dismiss()
                }
                effectOutline.setOnClickListener {
                    textEffect = TextEffect.OUTLINE; applyTextEffect(); dialog.dismiss()
                }
                effectGradient.setOnClickListener {
                    textEffect = TextEffect.GRADIENT; applyTextEffect(); dialog.dismiss()
                }
            }
        }.show()
    }

    private fun applyTextEffect() {
        val edit = binding.edittext2
        // Reset every effect channel first, so switching effects never
        // leaves a stale shadow, stroke or shader behind.
        edit.setShadowLayer(0f, 0f, 0f, Color.TRANSPARENT)
        edit.paint.style = Paint.Style.FILL
        edit.paint.shader = null
        if (textEffect == TextEffect.NONE) return
        val tc = edit.textColors.defaultColor
        val lum = (0.299f * Color.red(tc) + 0.587f * Color.green(tc) +
            0.114f * Color.blue(tc)) / 255f
        val darkText = lum < 0.5f
        when (textEffect) {
            TextEffect.EMBOSS, TextEffect.ENGRAVE -> {
                // One shadow edge only: pick the edge Photoshop would show
                // most clearly for this text colour. Dark text -> top-left
                // edge, light text -> bottom-right edge. Emboss shows the
                // light edge, engrave the dark edge.
                val dx = if (darkText) -2f else 2f
                val dy = if (darkText) -2f else 2f
                val shadow = when (textEffect) {
                    TextEffect.EMBOSS -> if (darkText) Color.WHITE else Color.BLACK
                    TextEffect.ENGRAVE -> if (darkText) Color.BLACK else Color.WHITE
                    else -> Color.TRANSPARENT
                }
                edit.setShadowLayer(2f, dx, dy, shadow)
            }
            TextEffect.GLOW -> {
                // Halo in the text's own colour, centred on the glyphs.
                edit.setShadowLayer(24f, 0f, 0f, tc)
            }
            TextEffect.OUTLINE -> {
                // Live stroke only: the stock EditText has one paint, so a
                // stroked glyph cannot also be filled. The HD export draws
                // fill + stroke together.
                edit.paint.style = Paint.Style.STROKE
                edit.paint.strokeWidth = 4f
                edit.paint.strokeJoin = Paint.Join.ROUND
            }
            TextEffect.GRADIENT -> {
                // App theme gradient, top to bottom across the text block.
                val h = if (edit.height > 0) edit.height.toFloat()
                    else edit.textSize * 4
                edit.paint.shader = LinearGradient(
                    0f, 0f, 0f, h,
                    intArrayOf(
                        Color.parseColor("#22D3EE"),
                        Color.parseColor("#A855F7"),
                        Color.parseColor("#6366F1")
                    ),
                    null, Shader.TileMode.CLAMP
                )
            }
            TextEffect.NONE -> Unit
        }
    }

    private fun showCanvasSizeDialog() {
        createDialog(R.layout.canvas_size_dialog) { dialogBinding, dialog ->
            (dialogBinding as CanvasSizeDialogBinding).apply {
                // Checked state is set before the click listeners, so the
                // programmatic check must not re-apply the size.
                when (canvasSize) {
                    CanvasSize.AUTO -> sizeAuto.isChecked = true
                    CanvasSize.SQUARE -> sizeSquare.isChecked = true
                    CanvasSize.POST -> sizePost.isChecked = true
                    CanvasSize.STORY -> sizeStory.isChecked = true
                }
                sizeAuto.setOnClickListener { applyCanvasSize(CanvasSize.AUTO); dialog.dismiss() }
                sizeSquare.setOnClickListener { applyCanvasSize(CanvasSize.SQUARE); dialog.dismiss() }
                sizePost.setOnClickListener { applyCanvasSize(CanvasSize.POST); dialog.dismiss() }
                sizeStory.setOnClickListener { applyCanvasSize(CanvasSize.STORY); dialog.dismiss() }
            }
        }.show()
    }

    private fun applyCanvasSize(size: CanvasSize) {
        canvasSize = size
        // Defer until the canvas is laid out so its width is known. The
        // normal Save Image captures root2 itself, so the saved poster's
        // aspect follows the canvas; HD export stays fixed 1440x2560.
        binding.root2.post {
            val width = binding.root2.width
            val params = binding.root2.layoutParams
            params.height = if (size.heightRatio != null && width > 0) {
                (width * size.heightRatio).toInt()
            } else {
                ViewGroup.LayoutParams.WRAP_CONTENT
            }
            binding.root2.layoutParams = params
        }
    }

    // ---- Canva phase 3: background library ----
    private fun showBackgroundLibraryDialog() {
        createDialog(R.layout.background_library_dialog) { dialogBinding, dialog ->
            (dialogBinding as BackgroundLibraryDialogBinding).apply {
                backgroundGrid.layoutManager = GridLayoutManager(requireActivity(), 3)
                backgroundGrid.adapter = BackgroundsAdapter { entry ->
                    applyLibraryBackground(entry)
                    dialog.dismiss()
                }
            }
        }.show()
    }

    private fun applyLibraryBackground(entry: BackgroundEntry) {
        // A library pick wins like a template's own background: the background
        // flow must not overwrite it until the user picks another background.
        templateBackgroundApplied = true
        binding.root2.setBackgroundResource(entry.drawableRes)
        viewModel.textColor.value =
            if (styleDarkText(entry.styleName)) Color.BLACK else Color.WHITE
        currentBackgroundLabel = entry.label
        backgroundLayerVisible = true
    }

    // ---- Canva phase 4: layers panel ----
    private fun showLayersDialog() {
        createDialog(R.layout.layers_dialog) { dialogBinding, dialog ->
            (dialogBinding as LayersDialogBinding).apply {
                layersTextPreview.text = binding.edittext2.text?.toString()
                    ?.lineSequence()?.firstOrNull()?.takeIf { it.isNotBlank() }
                    ?.take(30) ?: "No text yet"
                // Checked states are set before the listeners, so the
                // programmatic check must not toggle anything.
                layersTextSwitch.isChecked = textLayerVisible
                layersBackgroundName.text = currentBackgroundLabel
                layersBackgroundSwitch.isChecked = backgroundLayerVisible
                layersTextSwitch.setOnCheckedChangeListener { _, isChecked ->
                    textLayerVisible = isChecked
                    binding.edittext2.isVisible = isChecked
                }
                layersBackgroundSwitch.setOnCheckedChangeListener { _, isChecked ->
                    if (isChecked) {
                        savedBackground?.let { binding.root2.background = it }
                        backgroundLayerVisible = true
                    } else {
                        savedBackground = binding.root2.background
                        binding.root2.background = null
                        backgroundLayerVisible = false
                    }
                }
                layersTextRow.setOnClickListener {
                    dialog.dismiss()
                    binding.edittext2.requestFocus()
                    showKeyboard(binding.edittext2)
                }
                layersBackgroundRow.setOnClickListener {
                    dialog.dismiss()
                    binding.backgroundColor2.performClick()
                }
            }
        }.show()
    }

    private fun styleLabel(styleName: String): String =
        BACKGROUND_ENTRIES.firstOrNull { it.styleName == styleName }?.label ?: styleName

    private fun fontRes(fontKey: String): Int = when (fontKey) {
        "gandhara" -> R.font.gandhara_suls_regular
        "aadil" -> R.font.aadil_aadil
        "almas" -> R.font.aa_sameer_almas_regular
        "a300" -> R.font.a_300_regular
        "makki" -> R.font.alqalam_makki_regular
        "shekastah" -> R.font.alqalam_shekastah
        "pencil" -> R.font.pdms_pencil_nafees
        "alvi" -> R.font.alqalam_alvi_nastaleeq
        else -> R.font.noto_nastaliq_urdu
    }

    private fun styleRes(styleName: String): Int = when (styleName) {
        "INK_DARK" -> R.drawable.template_bg_ink
        "DUSK" -> R.drawable.template_bg_dusk
        "DARK_GLOW" -> R.drawable.template_bg_dark_glow
        "RAIN_GLASS" -> R.drawable.template_bg_rain
        "AUTUMN" -> R.drawable.template_bg_autumn
        "PAPER" -> R.drawable.template_bg_paper
        "TRUCK_ART" -> R.drawable.template_bg_truck
        "FRAMED" -> R.drawable.template_bg_framed
        "DEMO_GLOW" -> R.drawable.template_bg_demo_glow
        "DEMO_RAIN" -> R.drawable.template_bg_demo_rain
        "DEMO_AUTUMN" -> R.drawable.template_bg_demo_autumn
        "DEMO_PAPER" -> R.drawable.template_bg_demo_paper
        "PIC_GLOW" -> R.drawable.template_bg_pic_glow
        "PIC_RAIN" -> R.drawable.template_bg_pic_rain
        "PIC_AUTUMN" -> R.drawable.template_bg_pic_autumn
        "PIC_PAPER" -> R.drawable.template_bg_pic_paper
        "PIC_INK" -> R.drawable.template_bg_pic_ink
        "PIC_TRUCK" -> R.drawable.template_bg_pic_truck
        "PIC_FRAMED" -> R.drawable.template_bg_pic_arch
        "PIC_FLORAL" -> R.drawable.template_bg_pic_floral
        "PIC_VINTAGE" -> R.drawable.template_bg_pic_vintage
        "PIC_AZURE" -> R.drawable.template_bg_pic_azure
        "PIC_VINTAGE2" -> R.drawable.template_bg_pic_vintage2
        "PIC_VINTAGE3" -> R.drawable.template_bg_pic_vintage3
        else -> R.drawable.gradient_card
    }

    private fun styleDarkText(styleName: String): Boolean = when (styleName) {
        "PAPER", "DEMO_PAPER", "PIC_PAPER",
        "PIC_VINTAGE", "PIC_VINTAGE2", "PIC_VINTAGE3" -> true
        else -> false
    }

    private fun checkPermission(handler: PermissionHandler?) {
        val firstPermission: String
        val secondPermission: String
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            firstPermission = Manifest.permission.READ_MEDIA_IMAGES
            secondPermission = Manifest.permission.POST_NOTIFICATIONS
        } else {
            firstPermission = Manifest.permission.READ_EXTERNAL_STORAGE
            secondPermission = Manifest.permission.WRITE_EXTERNAL_STORAGE
        }
        val permissions = arrayOf(firstPermission, secondPermission)
        Permissions.check(requireActivity(), permissions, handler!!)
    }

    private fun createDialog(layoutId: Int, onShow: (ViewDataBinding, Dialog) -> Unit): Dialog {
        val dialog = Dialog(requireActivity(), R.style.WideDialog)
        dialog.window?.setBackgroundDrawable(ColorDrawable(Color.TRANSPARENT))
        val dialogBinding = DataBindingUtil.inflate<ViewDataBinding>(
            layoutInflater, layoutId, null, false
        )
        dialog.setContentView(dialogBinding.root)
        val window = dialog.window
        window?.attributes?.let {
            it.gravity = Gravity.BOTTOM
            it.flags = it.flags and WindowManager.LayoutParams.FLAG_DIM_BEHIND.inv()
        }
        onShow(dialogBinding, dialog)
        return dialog
    }

    override fun onDestroy() {
        super.onDestroy()
        shouldShowSnackBar = false
    }
}

// Canva phase 2: height = canvas width * heightRatio; AUTO keeps the old
// wrap_content canvas (height follows the text).
private enum class CanvasSize(val heightRatio: Float?) {
    AUTO(null),
    SQUARE(1f),
    POST(1.25f),
    STORY(16f / 9f)
}
