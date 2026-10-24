package me.nomi.urdutyper.presentation.ui.onboarding.state


sealed class OnboardingUiState {
    data object Loading : OnboardingUiState()
    data object Registered : OnboardingUiState()
    data class Error(val message: String) : OnboardingUiState()
    data class Message(val message: String) : OnboardingUiState()
}