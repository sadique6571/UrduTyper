package me.nomi.urdutyper.presentation.ui.dashboard.state

import me.nomi.urdutyper.domain.entity.Image

sealed class DashboardNavigationState {
    data class GoToViewPagerFragment(val images: List<Image>, val position: Int) : DashboardNavigationState()
    data class ImageDeleted(val image: Image): DashboardNavigationState()
    data object Logout : DashboardNavigationState()
}