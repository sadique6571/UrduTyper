package me.nomi.urdutyper.presentation.ui.splash.state

import me.nomi.urdutyper.domain.entity.User


sealed class SplashUiState {
    data object Loading : SplashUiState()
    data class Success(val user: User) : SplashUiState()
    data class Error(val message: String) : SplashUiState()
}