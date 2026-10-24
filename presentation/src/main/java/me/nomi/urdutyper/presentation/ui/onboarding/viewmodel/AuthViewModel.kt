package me.nomi.urdutyper.presentation.ui.onboarding.viewmodel

import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.asSharedFlow
import me.nomi.urdutyper.domain.usecase.Login
import me.nomi.urdutyper.domain.usecase.LoginWithGoogle
import me.nomi.urdutyper.domain.usecase.Register
import me.nomi.urdutyper.domain.usecase.ResetPassword
import me.nomi.urdutyper.domain.utils.dispatchers.DispatchersProviders
import me.nomi.urdutyper.domain.utils.onError
import me.nomi.urdutyper.domain.utils.onSuccess
import me.nomi.urdutyper.presentation.app.base.BaseViewModel
import me.nomi.urdutyper.presentation.ui.onboarding.state.OnboardingNavigationState
import me.nomi.urdutyper.presentation.ui.onboarding.state.OnboardingUiState
import javax.inject.Inject

@HiltViewModel
class AuthViewModel
@Inject
constructor(
    private val login: Login,
    private val loginWithGoogle: LoginWithGoogle,
    private val register: Register,
    private val resetPassword: ResetPassword,
    dispatchers: DispatchersProviders
) : BaseViewModel(dispatchers) {

    private val _viewState = MutableSharedFlow<OnboardingUiState>()
    val viewState = _viewState.asSharedFlow()

    private val _navigationState: MutableSharedFlow<OnboardingNavigationState> = MutableSharedFlow()
    val navigationState = _navigationState.asSharedFlow()

    fun login(email: String, password: String) {
        launchOnMainImmediate {
            _viewState.emit(OnboardingUiState.Loading)
            login.invoke(email, password).onSuccess {
                _navigationState.emit(OnboardingNavigationState.GoToDashboard(it))
            }.onError { error ->
                _viewState.emit(OnboardingUiState.Error(error.message ?: "Something went wrong"))
            }
        }
    }

    fun register(email: String, password: String) {
        launchOnMainImmediate {
            _viewState.emit(OnboardingUiState.Loading)
            register.invoke(email, password).onSuccess {
                _viewState.emit(OnboardingUiState.Registered)
            }.onError { error ->
                _viewState.emit(
                    OnboardingUiState.Error(
                        error.message ?: "Something went wrong"
                    )
                )
            }
        }
    }

    fun resetPassword(email: String) {
        launchOnMainImmediate {
            _viewState.emit(OnboardingUiState.Loading)
            resetPassword.invoke(email).onSuccess {
                _viewState.emit(OnboardingUiState.Message("A password reset email has been sent to the email address. Please make sure that your new password is at least 10 of length"))
            }.onError { error ->
                _viewState.emit(OnboardingUiState.Error(error.message ?: "Something went wrong"))
            }
        }
    }

    fun firebaseAuthWithGoogle(idToken: String) {
        launchOnMainImmediate {
            _viewState.emit(OnboardingUiState.Loading)
            loginWithGoogle.invoke(idToken).onSuccess {
                _navigationState.emit(OnboardingNavigationState.GoToDashboard(it))
            }.onError { error ->
                _viewState.emit(OnboardingUiState.Error(error.message ?: "Something went wrong"))
            }
        }
    }


    fun signUpClicked() {
        launchOnMainImmediate { _navigationState.emit(OnboardingNavigationState.GoToSignUp) }
    }
}
