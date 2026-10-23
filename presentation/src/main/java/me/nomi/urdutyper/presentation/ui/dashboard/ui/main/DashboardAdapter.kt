package me.nomi.urdutyper.presentation.ui.dashboard.ui.main

import com.bumptech.glide.Glide
import me.nomi.urdutyper.R
import me.nomi.urdutyper.databinding.SinglerowBinding
import me.nomi.urdutyper.domain.entity.Image
import me.nomi.urdutyper.presentation.utils.extensions.adapter.BaseAdapter
class DashboardAdapter : BaseAdapter<Image, SinglerowBinding>(R.layout.singlerow) {
    override fun bind(binding: SinglerowBinding, item: Image) {
        Glide.with(binding.root.context)
            .load(item.url)
            .error(R.drawable.not_found)
            .centerInside()
            .into(binding.imageView)
    }
}
