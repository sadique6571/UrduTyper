package me.nomi.urdutyper.presentation.ui.onboarding.ui

import android.content.Intent
import android.os.Bundle
import android.util.Patterns
import android.view.LayoutInflater
import android.view.View
import androidx.activity.result.ActivityResult
import androidx.activity.result.ActivityResultLauncher
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import androidx.core.view.isVisible
import androidx.fragment.app.viewModels
import androidx.navigation.fragment.findNavController
import com.google.android.gms.auth.api.signin.GoogleSignIn
import com.google.android.gms.auth.api.signin.GoogleSignInOptions
import com.google.android.gms.common.api.ApiException
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.launch
import me.nomi.urdutyper.R
import me.nomi.urdutyper.databinding.FragmentLoginBinding
import me.nomi.urdutyper.presentation.app.base.BaseFragment
import me.nomi.urdutyper.presentation.ui.onboarding.state.OnboardingNavigationState
import me.nomi.urdutyper.presentation.ui.onboarding.state.OnboardingUiState
import me.nomi.urdutyper.presentation.ui.onboarding.viewmodel.AuthViewModel
import me.nomi.urdutyper.presentation.utils.common.BiometricAuthenticator
import me.nomi.urdutyper.presentation.utils.extensions.common.dialog
import me.nomi.urdutyper.presentation.utils.extensions.common.dismissProgressDialog
import me.nomi.urdutyper.presentation.utils.extensions.common.showProgressDialog
import me.nomi.urdutyper.presentation.utils.extensions.views.launchAndRepeatWithViewLifecycle

@AndroidEntryPoint
class LoginFragment : BaseFragment<FragmentLoginBinding>() {
    private lateinit var biometricAuthenticator: BiometricAuthenticator
    private lateinit var signInWithGoogleLauncher: ActivityResultLauncher<Intent>
    private val viewModel: AuthViewModel by viewModels()
    override fun inflateViewBinding(inflater: LayoutInflater) =
        FragmentLoginBinding.inflate(inflater)

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        observeViewModel()
        setClickListeners()
        init()
    }

    private fun init() {
        signInWithGoogleLauncher = registerForActivityResult(
            ActivityResultContracts.StartActivityForResult()
        ) { result: ActivityResult ->
            dismissProgressDialog()
            val data = result.data
            if (data == null) {
                if (result.resultCode != AppCompatActivity.RESULT_CANCELED)
                    dialog("Google Sign-In returned no account. Please try again.").show()
            } else {
                GoogleSignIn.getSignedInAccountFromIntent(data)
                    .addOnSuccessListener { account ->
                        val token = account.idToken
                        if (token.isNullOrBlank()) {
                            dialog("Google ID token missing. Check Firebase SHA-1 and Google provider setup.").show()
                        } else {
                            viewModel.firebaseAuthWithGoogle(token)
                        }
                    }
                    .addOnFailureListener { error ->
                        val code = (error as? ApiException)?.statusCode
                        dialog("Google Sign-In failed" + (code?.let { " (code $it)" } ?: "") +
                            ". Check Google provider and APK signing SHA-1.").show()
                    }
            }
        }

        setBiometricAuthenticator()

    }

    private fun setClickListeners() {
        binding.loginButton.setOnClickListener {
            val email = binding.emailText.text.toString()
            val password = binding.passowrdText.text.toString()

            when {
                email.isEmpty() || password.isEmpty() ->
                    dialog("Please enter both email and password").show()

                !Patterns.EMAIL_ADDRESS.matcher(email)
                    .matches() -> dialog("Invalid Email Address").show()

                password.length < 6 -> dialog("Password must be at least 6 characters").show()
                else -> viewModel.login(email, password)
            }
        }

        binding.signupText.setOnClickListener { viewModel.signUpClicked() }

        binding.forgotPasswordText.setOnClickListener {
            val email = binding.emailText.text.toString()
            when {
                email.isEmpty() ->
                    dialog("Please enter your email address.").show()

                !Patterns.EMAIL_ADDRESS.matcher(email)
                    .matches() -> dialog("Invalid Email Address").show()

                else -> viewModel.resetPassword(email)
            }
        }

        binding.google.setOnClickListener {
            val gso = GoogleSignInOptions.Builder(GoogleSignInOptions.DEFAULT_SIGN_IN)
                .requestIdToken(getString(R.string.default_web_client_id))
                .requestEmail()
                .build()
            val signInIntent = GoogleSignIn.getClient(requireActivity(), gso).signInIntent
            signInWithGoogleLauncher.launch(signInIntent)
        }

        binding.btnFingerprint.setOnClickListener {
            if (prefs.email.isEmpty() || prefs.password.isEmpty()) {
                if (prefs.tokenId.isNotEmpty()) {
                    biometricAuthenticator.authenticate()
                } else dialog("Please login at least once to use this method").show()
            } else biometricAuthenticator.authenticate()
        }
    }

    private fun observeViewModel() = with(viewModel) {
        launchAndRepeatWithViewLifecycle {
            launch { viewModel.viewState.collect { handleUiState(it) } }
            launch { navigationState.collect { handleNavigationState(it) } }
        }
    }

    private fun handleUiState(it: OnboardingUiState) {
        binding.progressBar.isVisible = it is OnboardingUiState.Loading
        binding.loginButton.isVisible = it !is OnboardingUiState.Loading
        binding.google.isVisible = it !is OnboardingUiState.Loading
        binding.signupText.isVisible = it !is OnboardingUiState.Loading
        binding.forgotPasswordText.isVisible = it !is OnboardingUiState.Loading
        binding.btnFingerprint.isVisible = it !is OnboardingUiState.Loading
        when (it) {
            is OnboardingUiState.Error -> {
                if (it.message.contains("malformed"))
                    dialog("Your google session is expired, Please login using a different method!").show()
                else
                    dialog(it.message).show()
            }

            is OnboardingUiState.Message -> dialog(it.message).show()
            else -> {}
        }
    }

    private fun handleNavigationState(state: OnboardingNavigationState) = when (state) {
        is OnboardingNavigationState.GoToSignUp -> {
            navigateToSignUp()
        }

        is OnboardingNavigationState.GoToDashboard -> {
            prefs.user = state.user
            prefs.email = binding.emailText.text.toString()
            prefs.password = binding.passowrdText.text.toString()
            navigateToDashboard()
        }
    }

    private fun setBiometricAuthenticator() {
        biometricAuthenticator = BiometricAuthenticator(
            requireActivity(),
            "Biometric Authentication",
            "Please authenticate to proceed",
            "Cancel",
            onAuthenticationSucceeded = {
                if (prefs.tokenId.isEmpty()) {
                    if (prefs.email.isNotEmpty() && prefs.password.isNotEmpty())
                        viewModel.login(prefs.email, prefs.password)
                    else dialog("Please login at least once to use this method").show()
                } else viewModel.firebaseAuthWithGoogle(prefs.tokenId)
            },
            onAuthenticationError = { errorCode, errString ->
                if ((errorCode == 10 || errorCode == 13).not())
                    dialog(errString.toString()).show()
            },
            onAuthenticationFailed = {
                dialog("Authentication Failed").show()
            },
            onBiometricUnavailable = {
                dialog("Biometric Unavailable").show()
            }
        )
    }


    private fun navigateToSignUp() =
        findNavController().navigate(LoginFragmentDirections.toSignUpFragment())

    private fun navigateToDashboard() {
        findNavController().navigate(LoginFragmentDirections.toDashboardActivity())
        finishAffinity()
    }
}