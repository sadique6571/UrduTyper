package me.nomi.urdutyper.presentation.ui.dashboard.ui.templates

import android.app.AlertDialog
import android.content.res.ColorStateList
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.text.Editable
import android.text.TextWatcher
import androidx.activity.result.contract.ActivityResultContracts
import androidx.core.content.ContextCompat
import androidx.navigation.fragment.findNavController
import androidx.recyclerview.widget.GridLayoutManager
import androidx.recyclerview.widget.LinearLayoutManager
import com.google.android.material.chip.Chip
import dagger.hilt.android.AndroidEntryPoint
import me.nomi.urdutyper.R
import me.nomi.urdutyper.databinding.FragmentTemplatesBinding
import me.nomi.urdutyper.domain.TemplateCatalog
import me.nomi.urdutyper.domain.entity.Template
import me.nomi.urdutyper.domain.entity.TemplateCategory
import me.nomi.urdutyper.presentation.app.base.BaseFragment
import me.nomi.urdutyper.presentation.sharedviewmodel.TemplateFilterRequest
import me.nomi.urdutyper.presentation.ui.dashboard.ui.main.DashboardFragment


@AndroidEntryPoint
class TemplatesFragment : BaseFragment<FragmentTemplatesBinding>() {
    private val adapter by lazy { TemplatesAdapter() }
    private val recentAdapter by lazy { TemplatesAdapter() }
    private var selectedCategory: TemplateCategory? = null
    private var searchQuery: String = ""

    // Gallery pick for "Add Template": the image is downscaled + stored by
    // UserTemplateStore, then the grid refreshes.
    private val pickImage =
        registerForActivityResult(ActivityResultContracts.GetContent()) { uri ->
            if (uri == null) return@registerForActivityResult
            val added = UserTemplateStore.addFromUri(requireContext(), uri)
            if (added == null) {
                AlertDialog.Builder(requireContext())
                    .setMessage("Template add nahi ho saki, dobara try karein.")
                    .setPositiveButton("OK", null)
                    .show()
            }
            showTemplates()
        }

    // Chip handles, so a Home-tab filter request can check the right chip.
    private var allChipRef: Chip? = null
    private val categoryChipMap = mutableMapOf<TemplateCategory, Chip>()

    override fun inflateViewBinding(inflater: LayoutInflater) =
        FragmentTemplatesBinding.inflate(inflater)

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        binding.templatesGrid.layoutManager = GridLayoutManager(requireActivity(), 2)
        binding.templatesGrid.adapter = adapter
        adapter.onItemClickListener = { position ->
            onTemplateClick(adapter.getItem(position))
        }
        adapter.onLongItemClickListener = { position ->
            onTemplateLongClick(adapter, position)
        }
        // "Recently used" strip above the grid: a horizontal list of the
        // last-opened templates. Same cards, same tap-to-open behaviour.
        binding.recentGrid.layoutManager =
            LinearLayoutManager(requireActivity(), LinearLayoutManager.HORIZONTAL, false)
        binding.recentGrid.adapter = recentAdapter
        recentAdapter.onItemClickListener = { position ->
            onTemplateClick(recentAdapter.getItem(position))
        }
        recentAdapter.onLongItemClickListener = { position ->
            onTemplateLongClick(recentAdapter, position)
        }
        binding.templateSearch.addTextChangedListener(object : TextWatcher {
            override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) = Unit
            override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {
                searchQuery = s?.toString()?.trim().orEmpty()
                showTemplates()
            }
            override fun afterTextChanged(s: Editable?) = Unit
        })
        binding.addTemplateButton.setOnClickListener { pickImage.launch("image/*") }
        setupChips()
        showTemplates()
        // Demo 4's big featured card: tap opens the editor with this couplet,
        // gradient background and Nastaleeq font.
        binding.featuredCard.setOnClickListener {
            val args = Bundle().apply {
                putString("templateText", binding.featuredCouplet.text.toString())
                putString("templateStyle", "GRADIENT")
                putString("templateFont", "nastaliq")
            }
            findNavController().navigate(R.id.to_typeFragment, args)
        }
    }

    override fun onResume() {
        super.onResume()
        // Apply a pending Home-tab filter exactly once, then clear it so a
        // later visit is not snapped back to an old category.
        val dashboard = parentFragment as? DashboardFragment ?: return
        val request = dashboard.pendingTemplateFilter ?: return
        dashboard.pendingTemplateFilter = null
        when (request) {
            is TemplateFilterRequest.All -> selectCategory(null)
            is TemplateFilterRequest.Category -> selectCategory(request.category)
        }
    }

    // Dark-theme chips: elevated when idle, purple when selected, white text.
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

    // Same pattern as Home (plain click listeners): the checkable-chip +
    // OnCheckedChangeListener version did not respond to taps on this tab,
    // while Home's click-listener chips work. Selection is tracked manually
    // so a tap always filters.
    private fun setupChips() {
        binding.categoryChips.removeAllViews()
        categoryChipMap.clear()
        val allChip = Chip(
            requireContext(),
            null,
            com.google.android.material.R.style.Widget_MaterialComponents_Chip_Choice
        ).apply {
            text = "سب"
            isCheckable = false
            id = View.generateViewId()
            styleChip(this)
            setOnClickListener { selectCategory(null) }
        }
        binding.categoryChips.addView(allChip)
        allChipRef = allChip
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
                setOnClickListener { selectCategory(category) }
            }
            binding.categoryChips.addView(chip)
            categoryChipMap[category] = chip
        }
        selectCategory(null)
    }

    private fun selectCategory(category: TemplateCategory?) {
        selectedCategory = category
        val purple = ContextCompat.getColor(requireContext(), R.color.studio_selected)
        val elevated = ContextCompat.getColor(requireContext(), R.color.white)
        allChipRef?.chipBackgroundColor =
            ColorStateList.valueOf(if (category == null) purple else elevated)
        categoryChipMap.forEach { (cat, chip) ->
            chip.chipBackgroundColor =
                ColorStateList.valueOf(if (cat == category) purple else elevated)
        }
        showTemplates()
    }

    private fun showTemplates() {
        val all = TemplateCatalog.templates + UserTemplateStore.load(requireContext())
        val categoryFiltered = selectedCategory?.let { category ->
            all.filter { it.category == category }
        } ?: all
        val query = searchQuery.trim()
        val list = if (query.isBlank()) categoryFiltered else categoryFiltered.filter { template ->
            template.title.contains(query, ignoreCase = true) ||
                template.couplet.contains(query, ignoreCase = true) ||
                template.category.urduName.contains(query, ignoreCase = true)
        }
        adapter.pushData(list)
        showRecents()
    }

    // "Recently used" strip above the grid: the user's last-opened
    // templates, most recent first. Shown only when at least one stored id
    // still resolves; independent of the category/search filter, so it is
    // always a shortcut back to what was just used.
    private fun showRecents() {
        val all = TemplateCatalog.templates + UserTemplateStore.load(requireContext())
        val byId = all.associateBy { it.id }
        val recents = RecentTemplates.loadIds(requireContext()).mapNotNull { byId[it] }
        recentAdapter.pushData(recents)
        val visible = if (recents.isEmpty()) View.GONE else View.VISIBLE
        binding.recentLabel.visibility = visible
        binding.recentGrid.visibility = visible
    }

    private fun onTemplateLongClick(adapter: TemplatesAdapter, position: Int): Boolean {
        val item = adapter.getItem(position)
        if (item.imagePath != null) {
            AlertDialog.Builder(requireContext())
                .setMessage("Ye apni template delete kar dein?")
                .setPositiveButton("Delete") { _, _ ->
                    UserTemplateStore.remove(requireContext(), item)
                    showTemplates()
                }
                .setNegativeButton("Cancel", null)
                .show()
            return true
        }
        return false
    }

    private fun onTemplateClick(template: Template) {
        // Opening a template makes it "recently used" (recorded before
        // navigating, so the strip is fresh on the way back).
        RecentTemplates.record(requireContext(), template.id)
        // Pass style + font too: earlier only the couplet text was passed, so the
        // editor always opened on the flat blue default instead of the template's background.
        // User templates additionally carry their gallery file + dark-text flag.
        val args = Bundle().apply {
            putString("templateText", template.couplet)
            putString("templateStyle", template.style.name)
            putString("templateFont", template.fontKey)
            template.imagePath?.let { putString("templateImagePath", it) }
            template.darkTextOverride?.let { putBoolean("templateDarkText", it) }
        }
        findNavController().navigate(R.id.to_typeFragment, args)
    }
}
