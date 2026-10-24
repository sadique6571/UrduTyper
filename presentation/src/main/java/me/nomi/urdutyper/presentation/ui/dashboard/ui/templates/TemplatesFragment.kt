package me.nomi.urdutyper.presentation.ui.dashboard.ui.templates

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import androidx.navigation.fragment.findNavController
import androidx.recyclerview.widget.GridLayoutManager
import com.google.android.material.chip.Chip
import dagger.hilt.android.AndroidEntryPoint
import me.nomi.urdutyper.R
import me.nomi.urdutyper.databinding.FragmentTemplatesBinding
import me.nomi.urdutyper.domain.TemplateCatalog
import me.nomi.urdutyper.domain.entity.Template
import me.nomi.urdutyper.domain.entity.TemplateCategory
import me.nomi.urdutyper.presentation.app.base.BaseFragment


@AndroidEntryPoint
class TemplatesFragment : BaseFragment<FragmentTemplatesBinding>() {
    private val adapter by lazy { TemplatesAdapter() }
    private var selectedCategory: TemplateCategory? = null

    override fun inflateViewBinding(inflater: LayoutInflater) =
        FragmentTemplatesBinding.inflate(inflater)

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        binding.templatesGrid.layoutManager = GridLayoutManager(requireActivity(), 2)
        binding.templatesGrid.adapter = adapter
        adapter.onItemClickListener = { position ->
            onTemplateClick(adapter.getItem(position))
        }
        setupChips()
        showTemplates()
    }

    private fun setupChips() {
        val allChip = Chip(
            requireContext(),
            null,
            com.google.android.material.R.style.Widget_MaterialComponents_Chip_Choice
        ).apply {
            text = "سب"
            isCheckable = true
            isChecked = true
            id = View.generateViewId()
            setOnCheckedChangeListener { _, checked ->
                if (checked) {
                    selectedCategory = null
                    showTemplates()
                }
            }
        }
        binding.categoryChips.addView(allChip)
        TemplateCategory.values().forEach { category ->
            val chip = Chip(
                requireContext(),
                null,
                com.google.android.material.R.style.Widget_MaterialComponents_Chip_Choice
            ).apply {
                text = category.urduName
                isCheckable = true
                id = View.generateViewId()
                setOnCheckedChangeListener { _, checked ->
                    if (checked) {
                        selectedCategory = category
                        showTemplates()
                    }
                }
            }
            binding.categoryChips.addView(chip)
        }
    }

    private fun showTemplates() {
        val list = selectedCategory?.let { category ->
            TemplateCatalog.templates.filter { it.category == category }
        } ?: TemplateCatalog.templates
        adapter.pushData(list)
    }

    private fun onTemplateClick(template: Template) {
        val args = Bundle().apply { putString("templateText", template.couplet) }
        findNavController().navigate(R.id.to_typeFragment, args)
    }
}
