package me.nomi.urdutyper.presentation.ui.dashboard.ui.templates

import androidx.core.content.res.ResourcesCompat
import me.nomi.urdutyper.R
import me.nomi.urdutyper.databinding.ItemTemplateBinding
import me.nomi.urdutyper.domain.entity.Template
import me.nomi.urdutyper.domain.entity.TemplateStyle
import me.nomi.urdutyper.presentation.utils.extensions.adapter.BaseAdapter

class TemplatesAdapter : BaseAdapter<Template, ItemTemplateBinding>(R.layout.item_template) {
    override fun bind(binding: ItemTemplateBinding, item: Template) {
        binding.coupletText.text = item.couplet
        binding.coupletText.typeface =
            ResourcesCompat.getFont(binding.root.context, fontRes(item.fontKey))
        binding.categoryText.text = item.category.urduName
        binding.root.setBackgroundResource(styleRes(item.style))
    }

    private fun fontRes(fontKey: String): Int = when (fontKey) {
        "gandhara" -> R.font.gandhara_suls_regular
        "aadil" -> R.font.aadil_aadil
        "almas" -> R.font.aa_sameer_almas_regular
        "a300" -> R.font.a_300_regular
        "makki" -> R.font.alqalam_makki_regular
        else -> R.font.noto_nastaliq_urdu
    }

    private fun styleRes(style: TemplateStyle): Int = when (style) {
        TemplateStyle.INK_DARK -> R.drawable.template_bg_ink
        TemplateStyle.DUSK -> R.drawable.template_bg_dusk
        TemplateStyle.GRADIENT -> R.drawable.gradient_card
    }
}
