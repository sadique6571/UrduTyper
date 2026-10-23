package me.nomi.urdutyper.presentation.ui.dashboard.state

import me.nomi.urdutyper.domain.entity.Image

sealed class DashboardUiState {
    data object Loading : DashboardUiState()
    data class Error(val message: String) : DashboardUiState()
    data class ShowImages(val images: List<Image>) : DashboardUiState()
}