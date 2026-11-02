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
        setupCreateShortcuts()
        setupMidnightTemplates()
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

    private fun setupCreateShortcuts() {
        binding.homeCreateButton.icon = me.nomi.urdutyper.presentation.ui.type.studio.LuxeIcon("Media")
        binding.homeCreateButton.contentDescription = "New design · create a poetry poster"
        binding.homeVideoButton.icon = me.nomi.urdutyper.presentation.ui.type.studio.LuxeIcon("Video")
        binding.homeVideoButton.contentDescription = "New video · scenes, voice and music"
        binding.homeVideoButton.setOnClickListener {
            findNavController().navigate(R.id.to_typeFragment, Bundle().apply { putBoolean("startVideo", true) })
        }
        binding.homeTemplatesSeeAll.setOnClickListener { openTemplates(null) }
    }
    private fun setupMidnightTemplates() {
        val collection = me.nomi.urdutyper.domain.TemplateCatalog.templates.filter { it.backgroundKey?.startsWith("LUXE_") == true }
        val density = resources.displayMetrics.density
        binding.homeTemplateGrid.removeAllViews()
        collection.chunked(3).forEach { templates ->
            val line = LinearLayout(requireContext())
            templates.forEach { template ->
                val card = android.widget.FrameLayout(requireContext()).apply {
                    background = GradientDrawable().apply { setColor(0xff111f2f.toInt()); cornerRadius = 12 * density }
                    clipToOutline = true
                    contentDescription = "${template.title} · editable Urdu template"
                }
                val entry = me.nomi.urdutyper.presentation.ui.type.ui.BACKGROUND_ENTRIES.first { it.styleName == template.backgroundKey }
                card.addView(ImageView(requireContext()).apply { setImageResource(entry.drawableRes); scaleType = ImageView.ScaleType.CENTER_CROP }, android.widget.FrameLayout.LayoutParams(-1,-1))
                card.addView(TextView(requireContext()).apply {
                    text = template.couplet; textSize = 12f; typeface = StudioFonts(requireContext()).resolve(template.fontKey)
                    setTextColor(if (template.style.darkText) 0xff1d2734.toInt() else android.graphics.Color.WHITE)
                    gravity = android.view.Gravity.CENTER; setPadding((6*density).toInt(),0,(6*density).toInt(),0)
                }, android.widget.FrameLayout.LayoutParams(-1,-1))
                card.setOnClickListener { findNavController().navigate(R.id.to_typeFragment, Bundle().apply {
                    putString("templateText",template.couplet); putString("templateStyle",template.backgroundKey)
                    putString("templateFont",template.fontKey); putBoolean("templateDarkText",template.style.darkText)
                }) }
                line.addView(card, LinearLayout.LayoutParams(0,(164*density).toInt(),1f).apply { setMargins((3*density).toInt(),(3*density).toInt(),(3*density).toInt(),(3*density).toInt()) })
            }
            binding.homeTemplateGrid.addView(line)
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
            setTextColor(ContextCompat.getColor(requireContext(), R.color.studio_cream))
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
                ContextCompat.getColor(requireContext(), R.color.luxe_surface)
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
            drafts.forEach { (draft, preview) ->
                val card = LinearLayout(requireContext()).apply {
                    orientation = LinearLayout.HORIZONTAL; gravity = android.view.Gravity.CENTER_VERTICAL
                    setPadding((10*dp).toInt(),(10*dp).toInt(),(10*dp).toInt(),(10*dp).toInt())
                    background = GradientDrawable().apply { setColor(0xff111f2f.toInt()); cornerRadius = 14*dp; setStroke(dp.toInt().coerceAtLeast(1),0xff28394c.toInt()) }
                }
                card.addView(ImageView(requireContext()).apply { setImageBitmap(preview); scaleType = ImageView.ScaleType.CENTER_CROP; contentDescription = "${draft.title} preview" }, LinearLayout.LayoutParams((76*dp).toInt(),(66*dp).toInt()))
                val titles = LinearLayout(requireContext()).apply { orientation = LinearLayout.VERTICAL; setPadding((14*dp).toInt(),0,0,0) }
                titles.addView(TextView(requireContext()).apply { text = draft.title; textSize = 15f; setTextColor(0xfff4f0e8.toInt()); maxLines = 1 })
                titles.addView(TextView(requireContext()).apply { text = "${draft.width} × ${draft.height} · ${draft.orderedScenes().size} scene(s)"; textSize = 12f; setTextColor(0xffa7b4c5.toInt()) })
                card.addView(titles,LinearLayout.LayoutParams(0,-2,1f))
                card.setOnClickListener { findNavController().navigate(R.id.to_typeFragment,Bundle().apply { putString("draftId",draft.id) }) }
                card.setOnLongClickListener {
                    android.app.AlertDialog.Builder(requireContext()).setTitle("Delete design?").setMessage("Delete ${draft.title}? Exported posters remain.")
                        .setNegativeButton("Cancel",null).setPositiveButton("Delete") { _,_ -> StudioStore(File(context.filesDir,"studio-drafts")).delete(draft.id); loadDrafts() }.show(); true
                }
                binding.homeDraftsGrid.addView(card,LinearLayout.LayoutParams(-1,-2).apply { bottomMargin = (8*dp).toInt() })
            }
        }
    }
}

