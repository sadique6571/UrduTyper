package me.nomi.urdutyper.presentation.ui.splash.ui

import android.annotation.SuppressLint
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import androidx.fragment.app.viewModels
import androidx.navigation.fragment.findNavController
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.launch
import me.nomi.urdutyper.databinding.FragmentSplashScreenBinding
import me.nomi.urdutyper.presentation.app.base.BaseFragment
import me.nomi.urdutyper.presentation.ui.splash.state.SplashNavigationState
import me.nomi.urdutyper.presentation.ui.splash.viewmodel.SplashScreenViewModel
import me.nomi.urdutyper.presentation.ui.splash.state.SplashUiState
import me.nomi.urdutyper.presentation.utils.extensions.common.dialog
import me.nomi.urdutyper.presentation.utils.extensions.views.launchAndRepeatWithViewLifecycle

@SuppressLint("CustomSplashScreen")
@AndroidEntryPoint
class SplashScreenFragment : BaseFragment<FragmentSplashScreenBinding>() {

    private val viewModel: SplashScreenViewModel by viewModels()

    override fun inflateViewBinding(inflater: LayoutInflater): FragmentSplashScreenBinding =
        FragmentSplashScreenBinding.inflate(inflater)

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        observeViewModel()
    }


    private fun observeViewModel() = with(viewModel) {
        launchAndRepeatWithViewLifecycle {
            launch { viewState.collect { handleUiState(it) } }
            launch { navigationState.collect { handleNavigationState(it) } }
        }

    }

    private fun handleUiState(it: SplashUiState) {
        when (it) {
            is SplashUiState.Error -> {
                dialog(it.message).show()
            }
            else -> {}
        }
    }

    private fun handleNavigationState(state: SplashNavigationState) = when (state) {
        is SplashNavigationState.LoggedIn -> navigateToDashboard()
        is SplashNavigationState.NotLoggedIn -> navigateToMain()
    }

    private fun navigateToDashboard() {
        findNavController().navigate(SplashScreenFragmentDirections.toDashboardActivity())
        finishActivity()
    }

    private fun navigateToMain() {
        findNavController().navigate(SplashScreenFragmentDirections.toOnboardingActivity())
        finishActivity()
    }
}