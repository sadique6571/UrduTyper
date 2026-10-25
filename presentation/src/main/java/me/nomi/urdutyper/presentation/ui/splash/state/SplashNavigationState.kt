package me.nomi.urdutyper.presentation.ui.splash.state

sealed class SplashNavigationState {
    data object LoggedIn : SplashNavigationState()
    data object NotLoggedIn : SplashNavigationState()
}