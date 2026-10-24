package me.nomi.urdutyper.presentation.ui.splash.viewmodel

import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.asSharedFlow
import me.nomi.urdutyper.domain.usecase.UserData
import me.nomi.urdutyper.domain.utils.dispatchers.DispatchersProviders
import me.nomi.urdutyper.domain.utils.onError
import me.nomi.urdutyper.domain.utils.onSuccess
import me.nomi.urdutyper.presentation.app.base.BaseViewModel
import me.nomi.urdutyper.presentation.ui.splash.state.SplashNavigationState
import me.nomi.urdutyper.presentation.ui.splash.state.SplashUiState
import javax.inject.Inject

@HiltViewModel
class SplashScreenViewModel
@Inject
constructor(
    private val userData: UserData,
    dispatchers: DispatchersProviders
) : BaseViewModel(dispatchers) {

    private val _viewState = MutableSharedFlow<SplashUiState>()
    val viewState = _viewState.asSharedFlow()

    private val _navigationState: MutableSharedFlow<SplashNavigationState> = MutableSharedFlow()
    val navigationState = _navigationState.asSharedFlow()


    init {
        onInitialState()
    }

    private fun onInitialState() = launchOnMainImmediate {
        _viewState.emit(SplashUiState.Loading)
        delay(2000)
        userData().onSuccess { result ->
            _viewState.emit(SplashUiState.Success(result))
            _navigationState.emit(SplashNavigationState.LoggedIn)
        }.onError {
            _navigationState.emit(SplashNavigationState.NotLoggedIn)
        }
    }
}

