package me.nomi.urdutyper.presentation.ui.type.ui

import android.app.Activity
import android.content.Intent
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import androidx.activity.result.ActivityResult
import androidx.activity.result.ActivityResultLauncher
import androidx.activity.result.contract.ActivityResultContracts
import androidx.core.content.res.ResourcesCompat
import androidx.fragment.app.activityViewModels
import androidx.navigation.fragment.findNavController
import dagger.hilt.android.AndroidEntryPoint
import me.nomi.urdutyper.R
import me.nomi.urdutyper.databinding.FragmentFontsBinding
import me.nomi.urdutyper.presentation.app.base.BaseFragment
import me.nomi.urdutyper.presentation.ui.type.viewmodel.TypeViewModel
import me.nomi.urdutyper.presentation.utils.common.Fonts
import me.nomi.urdutyper.presentation.utils.common.Fonts.getAllFontsFromCache
import me.nomi.urdutyper.presentation.utils.extensions.adapter.attach
import me.nomi.urdutyper.presentation.utils.extensions.views.setVisible


@AndroidEntryPoint
class FontsFragment : BaseFragment<FragmentFontsBinding>() {
    private lateinit var filePickerResultLauncher: ActivityResultLauncher<Intent>
    private val viewModel: TypeViewModel by activityViewModels()
    override fun inflateViewBinding(inflater: LayoutInflater) =
        FragmentFontsBinding.inflate(layoutInflater)

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        val fonts = listOf(
            ResourcesCompat.getFont(requireActivity(), R.font.a_300_regular),
            ResourcesCompat.getFont(requireActivity(), R.font.aa_sameer_almas_regular),
            ResourcesCompat.getFont(requireActivity(), R.font.aa_sameer_divangiry_regular),
            ResourcesCompat.getFont(requireActivity(), R.font.aa_sameer_qamri_regular),
            ResourcesCompat.getFont(requireActivity(), R.font.aa_sameer_rafiya_unicode_regular),
            ResourcesCompat.getFont(requireActivity(), R.font.aa_sameer_zikran_regular),
            ResourcesCompat.getFont(requireActivity(), R.font.aadil_aadil),
            ResourcesCompat.getFont(requireActivity(), R.font.gandhara_suls_regular),
            ResourcesCompat.getFont(requireActivity(), R.font.jameel_noori_nastaleeq),
            ResourcesCompat.getFont(requireActivity(), R.font.jameel_noori_nastaleeq_kasheeda),
            ResourcesCompat.getFont(requireActivity(), R.font.lobster_regular),
            ResourcesCompat.getFont(requireActivity(), R.font.alqalam_khat_e_sumbali_regular),
            ResourcesCompat.getFont(requireActivity(), R.font.alqalam_makki_regular)
        ).plus(getAllFontsFromCache(binding.root.context))
        val adapter = FontsAdapter(viewModel)
        binding.fontsRecView.attach(
            adapter = adapter,
            onItemClick = { _, item ->
                viewModel.font.value = item
                findNavController().navigateUp()
            },
            isNestedScrollingEnabled = true,
            onBottomReached = {
                binding.floatingActionButton.setVisible(it, true)
            }
        )
        binding.floatingActionButton.setOnClickListener {
            openFilePicker()
        }
        adapter.pushData(fonts.filterNotNull())
        filePickerResultLauncher =
            registerForActivityResult(ActivityResultContracts.StartActivityForResult()) { result: ActivityResult ->
                if (result.resultCode == Activity.RESULT_OK) {
                    val uri = result.data?.data
                    if (uri != null) {
                        Fonts.copyFontToCache(requireActivity(), uri)
                        val cacheFonts = getAllFontsFromCache(requireActivity())
                        adapter.pushData(
                            fonts.filterNotNull().plus(cacheFonts).toSet().toList()
                        )
                    }
                }
            }
    }

    private fun openFilePicker() {
        val intent = Intent(Intent.ACTION_OPEN_DOCUMENT).apply {
            addCategory(Intent.CATEGORY_OPENABLE)
            type = "*/*"
        }
        filePickerResultLauncher.launch(intent)
    }
}