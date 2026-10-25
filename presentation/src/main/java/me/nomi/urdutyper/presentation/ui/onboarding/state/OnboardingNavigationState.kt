package me.nomi.urdutyper.presentation.ui.onboarding.state

import me.nomi.urdutyper.domain.entity.User

sealed class OnboardingNavigationState {
    data class GoToDashboard(val user: User) : OnboardingNavigationState()
    data object GoToSignUp: OnboardingNavigationState()
}