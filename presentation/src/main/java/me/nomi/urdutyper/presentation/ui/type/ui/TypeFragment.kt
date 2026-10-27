package me.nomi.urdutyper.presentation.ui.type.ui

import android.Manifest
import android.app.Dialog
import android.content.Intent
import android.graphics.BitmapFactory
import android.graphics.BitmapShader
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
import android.text.Editable
import android.text.TextWatcher
import android.widget.SeekBar
import androidx.activity.addCallback
import androidx.core.content.FileProvider
import androidx.core.content.res.ResourcesCompat
import androidx.core.graphics.ColorUtils
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
import me.nomi.urdutyper.presentation.utils.common.EditorHistory
import me.nomi.urdutyper.presentation.utils.common.EditorSnapshot
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
    // Text effects (Photoshop-style): fragment-local like canvas size.
    // Ordinals match ImageMaker's effect ints 0-17 and must stay stable.
    // A SET, not one effect: the dialog offers checkboxes, so 2+ effects
    // stack in one fixed order (see applyTextEffect / ImageMaker).
    private enum class TextEffect { NONE, EMBOSS, ENGRAVE, GLOW, OUTLINE, GRADIENT, DROP_SHADOW, NEON, TEXT_3D, LONG_SHADOW, GRADIENT_HORIZONTAL, GRADIENT_DIAGONAL, GRADIENT_GOLD, GRADIENT_RAINBOW, INNER_SHADOW, INNER_GLOW, PATTERN_FILL, REFLECTION }
    private val textEffects = linkedSetOf<TextEffect>()
    // Canva phase 4: layers panel state. The editor has exactly two layers.
    private var currentBackgroundLabel: String = "Default"
    private var backgroundLayerVisible = true
    private var savedBackground: Drawable? = null
    private var textLayerVisible = true
    // Full editor-state history: text + background + colour + font + size +
    // effects. The old text-only history could never bring a template back.
    private val editorHistory = EditorHistory()
    private var restoringHistory = false
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
        // Full editor-state history (text + template/background + colour +
        // font + size + effects). Programmatic undo/redo restores are
        // excluded from recording so they do not create history loops.
        // The first entry IS the opened template's state, so undo after a
        // background/template change brings that template back.
        editorHistory.reset(currentSnapshot())
        binding.edittext2.addTextChangedListener(object : TextWatcher {
            override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) = Unit
            override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {
                if (!restoringHistory) editorHistory.record(currentSnapshot(s?.toString()))
            }
            override fun afterTextChanged(s: Editable?) = Unit
        })
        binding.undoText2.setOnClickListener {
            editorHistory.undo(currentSnapshot())?.let { restoreEditorHistory(it) }
        }
        binding.redoText2.setOnClickListener {
            editorHistory.redo()?.let { restoreEditorHistory(it) }
        }

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
                    if (!templateBackgroundApplied) {
                        binding.root2.background = it
                        // Gradient/solid picks flow through here: record so
                        // undo restores the previous background.
                        if (!restoringHistory) editorHistory.record(currentSnapshot())
                    }
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
                        textEffects.map { it.ordinal }.toSet()
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

        // WhatsApp-size save: the same poster at max width 1080, JPEG 85,
        // into the same Pictures/Nomiii folder — a much smaller file for
        // sharing over a WhatsApp-only data bundle.
        binding.saveImageWa2.setOnClickListener {
            checkPermission(object : PermissionHandler {
                override fun onGranted() {
                    binding.menu2.collapse()
                    val file = ImageMaker.saveBitMapWhatsApp(
                        requireActivity(), binding.root2, binding.edittext2,
                        textEffects.map { it.ordinal }.toSet()
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

        // Direct share: renders the HD poster, then hands it to the system
        // chooser (WhatsApp, Instagram, …) via the app's FileProvider.
        binding.sharePoster2.setOnClickListener {
            checkPermission(object : PermissionHandler {
                override fun onGranted() {
                    binding.menu2.collapse()
                    val file = ImageMaker.saveBitMapHighRes(
                        requireActivity(), binding.root2, binding.edittext2,
                        textEffects.map { it.ordinal }.toSet()
                    ) ?: return
                    val uri = FileProvider.getUriForFile(
                        requireActivity(),
                        requireActivity().applicationContext.packageName + ".provider",
                        file
                    )
                    viewModel.uploadImage(file)
                    val share = Intent(Intent.ACTION_SEND).apply {
                        type = "image/jpeg"
                        putExtra(Intent.EXTRA_STREAM, uri)
                        addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                    }
                    startActivity(Intent.createChooser(share, "Share poster"))
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

                    // One-tap typography presets; these only affect the active text layer.
                    presetClassic.setOnClickListener {
                        viewModel.size.value = 40f
                        fontSize.progress = 20
                        viewModel.isBold.value = false
                        viewModel.isItalic.value = false
                        bold.isChecked = false
                        italic.isChecked = false
                        viewModel.typeface.value = Typeface.NORMAL
                        viewModel.textColor.value = Color.WHITE
                        textEffects.clear()
                        applyTextEffect()
                        editorHistory.record(currentSnapshot())
                    }
                    presetBold.setOnClickListener {
                        viewModel.size.value = 48f
                        fontSize.progress = 28
                        viewModel.isBold.value = true
                        viewModel.isItalic.value = false
                        bold.isChecked = true
                        italic.isChecked = false
                        viewModel.typeface.value = Typeface.BOLD
                        viewModel.textColor.value = Color.WHITE
                        textEffects.clear()
                        textEffects.add(TextEffect.OUTLINE)
                        applyTextEffect()
                        editorHistory.record(currentSnapshot())
                    }
                    presetGold.setOnClickListener {
                        viewModel.size.value = 44f
                        fontSize.progress = 24
                        viewModel.isBold.value = true
                        viewModel.isItalic.value = false
                        bold.isChecked = true
                        italic.isChecked = false
                        viewModel.typeface.value = Typeface.BOLD
                        viewModel.textColor.value = Color.rgb(255, 205, 92)
                        textEffects.clear()
                        textEffects.add(TextEffect.GRADIENT_GOLD)
                        applyTextEffect()
                        editorHistory.record(currentSnapshot())
                    }
                    presetNeon.setOnClickListener {
                        viewModel.size.value = 42f
                        fontSize.progress = 22
                        viewModel.isBold.value = true
                        viewModel.isItalic.value = false
                        bold.isChecked = true
                        italic.isChecked = false
                        viewModel.typeface.value = Typeface.BOLD
                        viewModel.textColor.value = Color.rgb(95, 255, 240)
                        textEffects.clear()
                        textEffects.add(TextEffect.NEON)
                        applyTextEffect()
                        editorHistory.record(currentSnapshot())
                    }

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
    // (no fill), 3D and long shadow show a single offset hint, inner
    // shadow / inner glow show a tight edge hint, reflection is HD only;
    // glow, drop shadow, neon, gradients and pattern fill show fully.
    // The HD export draws every effect in full (see ImageMaker).
    private fun showTextEffectDialog() {
        createDialog(R.layout.text_effect_dialog) { dialogBinding, dialog ->
            (dialogBinding as TextEffectDialogBinding).apply {
                // Checkboxes reflect the current SET; NONE is checked only
                // when the set is empty (it is not itself an effect).
                effectNone.isChecked = textEffects.isEmpty()
                effectEmboss.isChecked = TextEffect.EMBOSS in textEffects
                effectEngrave.isChecked = TextEffect.ENGRAVE in textEffects
                effectGlow.isChecked = TextEffect.GLOW in textEffects
                effectOutline.isChecked = TextEffect.OUTLINE in textEffects
                effectGradient.isChecked = TextEffect.GRADIENT in textEffects
                effectDropShadow.isChecked = TextEffect.DROP_SHADOW in textEffects
                effectNeon.isChecked = TextEffect.NEON in textEffects
                effect3d.isChecked = TextEffect.TEXT_3D in textEffects
                effectLongShadow.isChecked = TextEffect.LONG_SHADOW in textEffects
                effectGradientH.isChecked = TextEffect.GRADIENT_HORIZONTAL in textEffects
                effectGradientD.isChecked = TextEffect.GRADIENT_DIAGONAL in textEffects
                effectGradientGold.isChecked = TextEffect.GRADIENT_GOLD in textEffects
                effectGradientRainbow.isChecked = TextEffect.GRADIENT_RAINBOW in textEffects
                effectInnerShadow.isChecked = TextEffect.INNER_SHADOW in textEffects
                effectInnerGlow.isChecked = TextEffect.INNER_GLOW in textEffects
                effectPatternFill.isChecked = TextEffect.PATTERN_FILL in textEffects
                effectReflection.isChecked = TextEffect.REFLECTION in textEffects
                // Tapping an effect unchecks NONE; tapping NONE clears the rest.
                val effectBoxes = listOf(
                    effectEmboss, effectEngrave, effectGlow, effectOutline,
                    effectGradient, effectDropShadow, effectNeon, effect3d,
                    effectLongShadow, effectGradientH, effectGradientD,
                    effectGradientGold, effectGradientRainbow, effectInnerShadow,
                    effectInnerGlow, effectPatternFill, effectReflection
                )
                effectBoxes.forEach { box ->
                    box.setOnClickListener { if (box.isChecked) effectNone.isChecked = false }
                }
                effectNone.setOnClickListener {
                    if (effectNone.isChecked) effectBoxes.forEach { it.isChecked = false }
                }
                // Apply reads the whole set at once, then records one
                // history step so undo restores the previous effects.
                effectApply.setOnClickListener {
                    textEffects.clear()
                    if (effectEmboss.isChecked) textEffects.add(TextEffect.EMBOSS)
                    if (effectEngrave.isChecked) textEffects.add(TextEffect.ENGRAVE)
                    if (effectGlow.isChecked) textEffects.add(TextEffect.GLOW)
                    if (effectOutline.isChecked) textEffects.add(TextEffect.OUTLINE)
                    if (effectGradient.isChecked) textEffects.add(TextEffect.GRADIENT)
                    if (effectDropShadow.isChecked) textEffects.add(TextEffect.DROP_SHADOW)
                    if (effectNeon.isChecked) textEffects.add(TextEffect.NEON)
                    if (effect3d.isChecked) textEffects.add(TextEffect.TEXT_3D)
                    if (effectLongShadow.isChecked) textEffects.add(TextEffect.LONG_SHADOW)
                    if (effectGradientH.isChecked) textEffects.add(TextEffect.GRADIENT_HORIZONTAL)
                    if (effectGradientD.isChecked) textEffects.add(TextEffect.GRADIENT_DIAGONAL)
                    if (effectGradientGold.isChecked) textEffects.add(TextEffect.GRADIENT_GOLD)
                    if (effectGradientRainbow.isChecked) textEffects.add(TextEffect.GRADIENT_RAINBOW)
                    if (effectInnerShadow.isChecked) textEffects.add(TextEffect.INNER_SHADOW)
                    if (effectInnerGlow.isChecked) textEffects.add(TextEffect.INNER_GLOW)
                    if (effectPatternFill.isChecked) textEffects.add(TextEffect.PATTERN_FILL)
                    if (effectReflection.isChecked) textEffects.add(TextEffect.REFLECTION)
                    applyTextEffect()
                    editorHistory.record(currentSnapshot())
                    dialog.dismiss()
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
        if (textEffects.isEmpty()) return
        val tc = edit.textColors.defaultColor
        val lum = (0.299f * Color.red(tc) + 0.587f * Color.green(tc) +
            0.114f * Color.blue(tc)) / 255f
        val darkText = lum < 0.5f
        // Shared gradient stops. Theme = app gradient, gold = metallic,
        // rainbow = full spectrum.
        val themeColors = intArrayOf(
            Color.parseColor("#22D3EE"),
            Color.parseColor("#A855F7"),
            Color.parseColor("#6366F1")
        )
        val goldColors = intArrayOf(
            Color.parseColor("#FFFBEB"),
            Color.parseColor("#FDE68A"),
            Color.parseColor("#D97706"),
            Color.parseColor("#92400E"),
            Color.parseColor("#FDE68A")
        )
        val rainbowColors = intArrayOf(
            Color.parseColor("#EF4444"),
            Color.parseColor("#F97316"),
            Color.parseColor("#EAB308"),
            Color.parseColor("#22C55E"),
            Color.parseColor("#3B82F6"),
            Color.parseColor("#A855F7")
        )
        val w = if (edit.width > 0) edit.width.toFloat() else edit.textSize * 8
        val h = if (edit.height > 0) edit.height.toFloat() else edit.textSize * 4
        // Stacking, fixed order. The live EditText has ONE shadow channel
        // and ONE shader, so within a channel the LATER effect wins:
        // bevel first, then inner shadow, glow, drop shadow, neon, 3D hint,
        // long shadow hint, inner glow last; fill shader: the last gradient
        // / pattern in the list below wins. HD draws every selected effect
        // in full (see ImageMaker) — live shows one shadow + one fill.
        val bevel = if (TextEffect.ENGRAVE in textEffects) TextEffect.ENGRAVE
            else if (TextEffect.EMBOSS in textEffects) TextEffect.EMBOSS else null
        if (bevel != null) {
            // One shadow edge only: pick the edge Photoshop would show
            // most clearly for this text colour. Dark text -> top-left
            // edge, light text -> bottom-right edge. Emboss shows the
            // light edge, engrave the dark edge. ENGRAVE wins if both
            // are ticked (opposite bevels would cancel into mud).
            val dx = if (darkText) -2f else 2f
            val dy = if (darkText) -2f else 2f
            val shadow = when (bevel) {
                TextEffect.EMBOSS -> if (darkText) Color.WHITE else Color.BLACK
                else -> if (darkText) Color.BLACK else Color.WHITE
            }
            edit.setShadowLayer(2f, dx, dy, shadow)
        }
        if (TextEffect.INNER_SHADOW in textEffects) {
            // Live hint only: a tight dark edge hugging the glyphs.
            // The HD export clips the dark edge to the inside of the
            // glyphs; one EditText paint cannot do that clip live.
            val edge = if (darkText) Color.argb(170, 0, 0, 0)
            else ColorUtils.blendARGB(tc, Color.BLACK, 0.5f)
            edit.setShadowLayer(2f, 0f, 1f, edge)
        }
        if (TextEffect.GLOW in textEffects) {
            // Halo in the text's own colour, centred on the glyphs.
            edit.setShadowLayer(24f, 0f, 0f, tc)
        }
        if (TextEffect.DROP_SHADOW in textEffects) {
            // Classic soft shadow, offset down-right.
            edit.setShadowLayer(8f, 4f, 6f, Color.argb(160, 0, 0, 0))
        }
        if (TextEffect.NEON in textEffects) {
            // Strong halo in the text's own colour; the glyph itself
            // stays the bright core.
            edit.setShadowLayer(36f, 0f, 0f, tc)
        }
        if (TextEffect.TEXT_3D in textEffects) {
            // Live hint only: one hard offset edge in the same
            // mid-tone shade the HD export stacks. The HD export
            // stacks the full extrude.
            val extrude = if (darkText)
                ColorUtils.blendARGB(tc, Color.WHITE, 0.55f)
            else
                ColorUtils.blendARGB(tc, Color.BLACK, 0.55f)
            edit.setShadowLayer(0f, 5f, 5f, extrude)
        }
        if (TextEffect.LONG_SHADOW in textEffects) {
            // Live hint only: one hard flat offset. The HD export
            // stacks the full long shadow.
            edit.setShadowLayer(0f, 10f, 10f, Color.argb(100, 0, 0, 0))
        }
        if (TextEffect.INNER_GLOW in textEffects) {
            // Live hint only: a soft light halo centred on the glyphs.
            // The HD export clips the light edge to the inside.
            val edge = if (darkText) ColorUtils.blendARGB(tc, Color.WHITE, 0.6f)
            else Color.argb(200, 255, 255, 255)
            edit.setShadowLayer(6f, 0f, 0f, edge)
        }
        if (TextEffect.OUTLINE in textEffects) {
            // Live stroke only: the stock EditText has one paint, so a
            // stroked glyph cannot also be filled. The HD export draws
            // fill + stroke together.
            edit.paint.style = Paint.Style.STROKE
            edit.paint.strokeWidth = 4f
            edit.paint.strokeJoin = Paint.Join.ROUND
        }
        // Fill shader: last selected fill in this fixed order wins.
        val fill = listOf(
            TextEffect.GRADIENT, TextEffect.GRADIENT_HORIZONTAL,
            TextEffect.GRADIENT_DIAGONAL, TextEffect.GRADIENT_GOLD,
            TextEffect.GRADIENT_RAINBOW, TextEffect.PATTERN_FILL
        ).lastOrNull { it in textEffects }
        when (fill) {
            TextEffect.GRADIENT -> {
                // App theme gradient, top to bottom across the text block.
                edit.paint.shader = LinearGradient(
                    0f, 0f, 0f, h,
                    themeColors, null, Shader.TileMode.CLAMP
                )
            }
            TextEffect.GRADIENT_HORIZONTAL -> {
                edit.paint.shader = LinearGradient(
                    0f, 0f, w, 0f,
                    themeColors, null, Shader.TileMode.CLAMP
                )
            }
            TextEffect.GRADIENT_DIAGONAL -> {
                edit.paint.shader = LinearGradient(
                    0f, 0f, w, h,
                    themeColors, null, Shader.TileMode.CLAMP
                )
            }
            TextEffect.GRADIENT_GOLD -> {
                edit.paint.shader = LinearGradient(
                    0f, 0f, 0f, h,
                    goldColors, null, Shader.TileMode.CLAMP
                )
            }
            TextEffect.GRADIENT_RAINBOW -> {
                edit.paint.shader = LinearGradient(
                    0f, 0f, w, 0f,
                    rainbowColors, null, Shader.TileMode.CLAMP
                )
            }
            TextEffect.PATTERN_FILL -> {
                // Full live: an EditText paint accepts a BitmapShader, so
                // the stripe pattern shows exactly as the HD export draws it.
                edit.paint.shader = BitmapShader(
                    ImageMaker.patternTile(tc, 1f),
                    Shader.TileMode.REPEAT, Shader.TileMode.REPEAT
                )
            }
            else -> Unit
        }
        // REFLECTION is HD only: a mirrored copy needs a second text block,
        // which the single live EditText cannot draw. Live shows plain text.
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
        editorHistory.record(currentSnapshot())
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

    private fun currentSnapshot(textOverride: String? = null): EditorSnapshot {
        return EditorSnapshot(
            text = textOverride ?: binding.edittext2.text?.toString().orEmpty(),
            background = binding.root2.background,
            backgroundLabel = currentBackgroundLabel,
            textColor = viewModel.textColor.value,
            font = viewModel.font.value,
            typefaceStyle = viewModel.typeface.value,
            textSize = viewModel.size.value,
            effects = textEffects.map { it.ordinal }.toSet()
        )
    }

    private fun restoreEditorHistory(snapshot: EditorSnapshot) {
        restoringHistory = true
        try {
            binding.edittext2.setText(snapshot.text)
            binding.edittext2.setSelection(binding.edittext2.text?.length ?: 0)
            // The restored background wins like a template's own: collectors
            // must not overwrite it until the user picks another one.
            binding.root2.background = snapshot.background
            templateBackgroundApplied = true
            currentBackgroundLabel = snapshot.backgroundLabel
            viewModel.textColor.value = snapshot.textColor
            viewModel.font.value = snapshot.font
            viewModel.typeface.value = snapshot.typefaceStyle
            viewModel.size.value = snapshot.textSize
            textEffects.clear()
            textEffects.addAll(snapshot.effects.mapNotNull { TextEffect.values().getOrNull(it) })
            applyTextEffect()
        } finally {
            restoringHistory = false
        }
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
        "INK_SPLATTER" -> R.drawable.template_bg_splatter
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
