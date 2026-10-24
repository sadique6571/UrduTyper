package me.nomi.urdutyper.presentation.ui.dashboard.ui.home

import android.content.res.ColorStateList
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import androidx.core.content.ContextCompat
import androidx.core.view.isVisible
import androidx.fragment.app.activityViewModels
import androidx.navigation.fragment.findNavController
import androidx.recyclerview.widget.GridLayoutManager
import com.google.android.material.chip.Chip
import dagger.hilt.android.AndroidEntryPoint
import me.nomi.urdutyper.R
import me.nomi.urdutyper.databinding.FragmentHomeBinding
import me.nomi.urdutyper.domain.entity.Image
import me.nomi.urdutyper.domain.entity.TemplateCategory
import me.nomi.urdutyper.presentation.app.base.BaseFragment
import me.nomi.urdutyper.presentation.sharedviewmodel.SharedViewModel
import me.nomi.urdutyper.presentation.sharedviewmodel.TemplateFilterRequest
import me.nomi.urdutyper.presentation.ui.dashboard.ui.main.DashboardAdapter
import me.nomi.urdutyper.presentation.ui.dashboard.ui.main.DashboardFragment
import me.nomi.urdutyper.presentation.utils.common.ImageMaker.getListOfImages
import me.nomi.urdutyper.presentation.utils.extensions.adapter.attach

/**
 * Home tab (first dashboard page): Demo 4 featured card on top, recent local
 * posters below, then category shortcuts into the Templates tab. The profile
 * gradient header above the ViewPager is shared by all tabs and lives in
 * fragment_dashboard.xml, so it is not duplicated here.
 */
@AndroidEntryPoint
class HomeFragment : BaseFragment<FragmentHomeBinding>() {
    private val sharedViewModel: SharedViewModel by activityViewModels()
    private val adapter by lazy { DashboardAdapter() }

    // Full local list, newest first (filenames are UTyyyyMMdd_hhmmss, so name
    // order is chronological). The grid shows the first 6; taps map back here.
    private var allPosters: List<Image> = emptyList()

    override fun inflateViewBinding(inflater: LayoutInflater) =
        FragmentHomeBinding.inflate(inflater)

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        setupFeaturedCard()
        setupPosters()
        setupChips()
        loadPosters()
    }

    override fun onResume() {
        super.onResume()
        // Fresh after returning from the editor: a just-saved poster shows up.
        loadPosters()
    }

    private fun setupFeaturedCard() {
        binding.featuredCard.setOnClickListener {
            val args = Bundle().apply {
                putString("templateText", binding.featuredCouplet.text.toString())
                putString("templateStyle", "GRADIENT")
                putString("templateFont", "nastaliq")
            }
            findNavController().navigate(R.id.to_typeFragment, args)
        }
    }

    private fun setupPosters() {
        binding.homePostersGrid.attach(
            layoutManager = GridLayoutManager(requireActivity(), 2),
            adapter = adapter,
            onItemClick = { _, item ->
                val fullIndex = allPosters.indexOf(item)
                if (fullIndex >= 0) {
                    sharedViewModel.localImageList.value = allPosters
                    sharedViewModel.position.value = fullIndex
                    findNavController().navigate(R.id.to_localViewPagerFragment)
                }
            },
            isNestedScrollingEnabled = false
        )
        binding.homeSeeAll.setOnClickListener {
            (parentFragment as? DashboardFragment)?.showPage(1)
        }
        binding.homeCreateButton.setOnClickListener {
            findNavController().navigate(R.id.to_typeFragment)
        }
    }

    private fun loadPosters() {
        allPosters = getListOfImages(requireActivity()).sortedByDescending { it.name }
        val recent = allPosters.take(6)
        adapter.pushData(recent)
        val empty = recent.isEmpty()
        binding.homeEmptyText.isVisible = empty
        binding.homeCreateButton.isVisible = empty
        binding.homePostersGrid.isVisible = !empty
    }

    private fun setupChips() {
        val allChip = Chip(
            requireContext(),
            null,
            com.google.android.material.R.style.Widget_MaterialComponents_Chip_Choice
        ).apply {
            text = "سب"
            isCheckable = false
            id = View.generateViewId()
            styleChip(this)
            // Demo: the "All" shortcut reads as the active chip (lime fill).
            chipBackgroundColor = ColorStateList.valueOf(
                ContextCompat.getColor(requireContext(), R.color.accent_lime)
            )
            setTextColor(ContextCompat.getColor(requireContext(), R.color.surface_dark))
            setOnClickListener { openTemplates(null) }
        }
        binding.homeCategoryChips.addView(allChip)
        TemplateCategory.values().forEach { category ->
            val chip = Chip(
                requireContext(),
                null,
                com.google.android.material.R.style.Widget_MaterialComponents_Chip_Choice
            ).apply {
                text = category.urduName
                isCheckable = false
                id = View.generateViewId()
                styleChip(this)
                setOnClickListener { openTemplates(category) }
            }
            binding.homeCategoryChips.addView(chip)
        }
    }

    // Dark-theme chips: elevated idle, white text (same look as Templates tab).
    private fun styleChip(chip: Chip) {
        chip.setTextColor(ContextCompat.getColor(requireContext(), R.color.white))
        chip.chipBackgroundColor = ColorStateList(
            arrayOf(intArrayOf(android.R.attr.state_checked), intArrayOf()),
            intArrayOf(
                ContextCompat.getColor(requireContext(), R.color.gradient_purple),
                ContextCompat.getColor(requireContext(), R.color.surface_dark_elevated)
            )
        )
    }

    private fun openTemplates(category: TemplateCategory?) {
        val dashboard = parentFragment as? DashboardFragment ?: return
        dashboard.showTemplatesPage(
            if (category == null) TemplateFilterRequest.All
            else TemplateFilterRequest.Category(category)
        )
    }
}
