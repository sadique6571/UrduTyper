package me.nomi.urdutyper.presentation.ui.onboarding.ui

import android.os.Bundle
import android.util.Patterns
import android.view.LayoutInflater
import android.view.View
import androidx.core.view.isVisible
import androidx.fragment.app.viewModels
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.launch
import me.nomi.urdutyper.databinding.FragmentSignupBinding
import me.nomi.urdutyper.presentation.app.base.BaseFragment
import me.nomi.urdutyper.presentation.ui.onboarding.state.OnboardingUiState
import me.nomi.urdutyper.presentation.ui.onboarding.viewmodel.AuthViewModel
import me.nomi.urdutyper.presentation.utils.extensions.common.dialog
import me.nomi.urdutyper.presentation.utils.extensions.views.launchAndRepeatWithViewLifecycle

@AndroidEntryPoint
class SignupFragment : BaseFragment<FragmentSignupBinding>() {
    override fun inflateViewBinding(inflater: LayoutInflater) =
        FragmentSignupBinding.inflate(inflater)

    private val viewModel: AuthViewModel by viewModels()

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)


        binding.signupButton.setOnClickListener {
            val email = binding.emailText.text.toString()
            val password = binding.passowrdText.text.toString()

            when {
                email.isEmpty() || password.isEmpty() -> {
                    dialog("Please enter both email and password").show()
                }
                !Patterns.EMAIL_ADDRESS.matcher(email).matches() -> {
                    dialog("Invalid Email Address").show()
                }
                password.length < 10 -> {
                    dialog("Password must at least be 10 characters in length").show()
                }
                else -> viewModel.register(email, password)
            }
        }

        binding.backToLogin.setOnClickListener {
            goBack()
        }

        observeViewModel()

    }

    private fun observeViewModel() = with(viewModel) {
        launchAndRepeatWithViewLifecycle {
            launch { viewState.collect { handleUiState(it) } }
        }
    }

    private fun handleUiState(it: OnboardingUiState) {
        binding.progressBar.isVisible = it is OnboardingUiState.Loading
        binding.signupButton.isVisible = it !is OnboardingUiState.Loading
        when (it) {
            is OnboardingUiState.Registered -> dialog("Account has been registered! Please check your inbox, verify your email and proceed to login!") {
                cancellable(false)
                positiveButton {
                    dismiss()
                    goBack()
                }
            }.show()

            is OnboardingUiState.Error -> {
                dialog(it.message).show()
            }

            else -> {}
        }
    }
}