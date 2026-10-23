package me.nomi.urdutyper.presentation.ui.dashboard.view

import me.nomi.urdutyper.domain.entity.Image

interface DashboardView {
    fun showImages(images: List<Image>)
    fun goToViewPagerFragment()
}