package me.nomi.urdutyper.presentation.ui.type.ui

import android.Manifest
import android.app.Dialog
import android.graphics.Color
import android.graphics.Typeface
import android.graphics.drawable.ColorDrawable
import android.graphics.drawable.GradientDrawable
import android.os.Build
import android.os.Bundle
import android.view.Gravity
import android.view.LayoutInflater
import android.view.View
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
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch
import me.nomi.urdutyper.R
import me.nomi.urdutyper.databinding.BackgroundColorDialogBinding
import me.nomi.urdutyper.databinding.ColorPickDialogBinding
import me.nomi.urdutyper.databinding.FontStyleDialogBinding
import me.nomi.urdutyper.databinding.FragmentTypeBinding
import me.nomi.urdutyper.presentation.app.base.BaseFragment
import me.nomi.urdutyper.presentation.ui.type.viewmodel.TypeViewModel
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
    override fun inflateViewBinding(inflater: LayoutInflater) =
        FragmentTypeBinding.inflate(layoutInflater)

    private val viewModel: TypeViewModel by activityViewModels()
    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        binding.edittext2.setOnTouchListener(MultiTouchListener(requireActivity()))

        launchAndRepeatWithViewLifecycle {
            launch { viewModel.gradientOrientation.collectLatest { setGradientBackground() } }
            launch { viewModel.leftGradientColor.collectLatest { setGradientBackground() } }
            launch { viewModel.rightGradientColor.collectLatest { setGradientBackground() } }
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
            launch { viewModel.background.collectLatest { binding.root2.background = it } }
        }

        val random = Random()

        if (viewModel.font.value == null) {
            viewModel.font.value =
                ResourcesCompat.getFont(requireActivity(), R.font.jameel_noori_nastaleeq)
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
                    val file = ImageMaker.saveBitMap(requireActivity(), binding.root2)
                    val uri = FileProvider.getUriForFile(
                        requireActivity(),
                        requireActivity().applicationContext.packageName + ".provider",
                        file!!
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

        binding.backgroundColor2.setOnClickListener {
            binding.menu2.collapse()
            createDialog(R.layout.background_color_dialog) { dialogBinding, _ ->
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
        viewModel.background.value = GradientDrawable(
            viewModel.gradientOrientation.value,
            intArrayOf(
                viewModel.leftGradientColor.value,
                viewModel.rightGradientColor.value
            )
        )
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
