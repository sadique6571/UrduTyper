package me.nomi.urdutyper.presentation.ui.dashboard.ui.home

import android.content.res.ColorStateList
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.TextView
import android.graphics.Typeface
import android.graphics.drawable.GradientDrawable
import androidx.core.content.ContextCompat
import androidx.core.view.isVisible
import androidx.fragment.app.activityViewModels
import androidx.navigation.fragment.findNavController
import androidx.lifecycle.lifecycleScope
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
import me.nomi.urdutyper.presentation.ui.type.studio.StudioFonts
import me.nomi.urdutyper.presentation.ui.type.studio.StudioRenderer
import me.nomi.urdutyper.presentation.ui.type.studio.StudioStore
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File

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
    private var draftsJob: Job? = null

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
        loadDrafts()
    }

    private fun setupFeaturedCard() {
        binding.featuredCard.setOnClickListener {
            val args = Bundle().apply {
                putString("templateText", binding.featuredCouplet.text.toString())
                putString("templateStyle", "STUDIO_GREEN")
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
        binding.homeCreateButton.isVisible = true
        binding.homePostersGrid.isVisible = !empty
    }

    private fun setupChips() {
        binding.homeCategoryChips.removeAllViews()
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
                ContextCompat.getColor(requireContext(), R.color.studio_green)
            )
            setTextColor(ContextCompat.getColor(requireContext(), R.color.white))
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
        chip.setTextColor(ContextCompat.getColor(requireContext(), R.color.studio_ink))
        chip.chipBackgroundColor = ColorStateList(
            arrayOf(intArrayOf(android.R.attr.state_checked), intArrayOf()),
            intArrayOf(
                ContextCompat.getColor(requireContext(), R.color.studio_selected),
                ContextCompat.getColor(requireContext(), R.color.white)
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

    private fun loadDrafts() {
        draftsJob?.cancel()
        val context = requireContext().applicationContext
        draftsJob = viewLifecycleOwner.lifecycleScope.launch {
            val drafts = withContext(Dispatchers.IO) {
                val renderer = StudioRenderer(context, StudioFonts(context))
                StudioStore(File(context.filesDir, "studio-drafts")).list().take(8).map { draft ->
                    draft to runCatching { renderer.bitmap(draft, 240, false) }.getOrNull()
                }
            }
            binding.homeDraftsGrid.removeAllViews()
            binding.homeDraftsHint.text = if (drafts.isEmpty()) "Editable drafts appear here automatically." else "Tap to continue editing · hold to delete"
            val dp = resources.displayMetrics.density
            drafts.chunked(2).forEach { pair ->
                val row = LinearLayout(requireContext()).apply { orientation = LinearLayout.HORIZONTAL }
                pair.forEach { (draft, preview) ->
                    val card = LinearLayout(requireContext()).apply {
                        orientation = LinearLayout.VERTICAL; setPadding((8 * dp).toInt(), (8 * dp).toInt(), (8 * dp).toInt(), (8 * dp).toInt())
                        background = GradientDrawable().apply { setColor(android.graphics.Color.WHITE); cornerRadius = 16 * dp }
                    }
                    card.addView(ImageView(requireContext()).apply {
                        setImageBitmap(preview); scaleType = ImageView.ScaleType.CENTER_INSIDE
                        contentDescription = "Preview of ${draft.title}"
                    }, LinearLayout.LayoutParams(-1, (150 * dp).toInt()))
                    card.addView(TextView(requireContext()).apply {
                        text = draft.title; textSize = 14f; typeface = Typeface.DEFAULT_BOLD
                        setTextColor(ContextCompat.getColor(requireContext(), R.color.studio_ink)); maxLines = 1
                    })
                    card.addView(TextView(requireContext()).apply {
                        text = "${draft.width} × ${draft.height} · ${draft.layers.size} layers"; textSize = 11f
                        setTextColor(ContextCompat.getColor(requireContext(), R.color.studio_muted))
                    })
                    card.setOnClickListener { findNavController().navigate(R.id.to_typeFragment, Bundle().apply { putString("draftId", draft.id) }) }
                    card.setOnLongClickListener {
                        android.app.AlertDialog.Builder(requireContext()).setTitle("Delete design?")
                            .setMessage("Delete ${draft.title}? Exported posters will remain.")
                            .setNegativeButton("Cancel", null).setPositiveButton("Delete") { _, _ ->
                                StudioStore(File(context.filesDir, "studio-drafts")).delete(draft.id); loadDrafts()
                            }.show()
                        true
                    }
                    row.addView(card, LinearLayout.LayoutParams(0, -2, 1f).apply { setMargins((4 * dp).toInt(), (4 * dp).toInt(), (4 * dp).toInt(), (4 * dp).toInt()) })
                }
                if (pair.size == 1) row.addView(View(requireContext()), LinearLayout.LayoutParams(0, 1, 1f))
                binding.homeDraftsGrid.addView(row)
            }
        }
    }
}
