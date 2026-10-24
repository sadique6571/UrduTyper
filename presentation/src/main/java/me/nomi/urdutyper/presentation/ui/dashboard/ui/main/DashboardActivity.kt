package me.nomi.urdutyper.presentation.ui.dashboard.ui.main

import android.os.Bundle
import android.view.LayoutInflater
import androidx.activity.viewModels
import com.google.android.material.snackbar.Snackbar
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch
import me.nomi.urdutyper.databinding.ActivityDashboardBinding
import me.nomi.urdutyper.presentation.app.base.BaseActivity
import me.nomi.urdutyper.presentation.sharedviewmodel.SharedViewModel
import me.nomi.urdutyper.presentation.ui.type.state.TypeUiState
import me.nomi.urdutyper.presentation.ui.type.viewmodel.TypeViewModel
import me.nomi.urdutyper.presentation.utils.extensions.common.dialog
import me.nomi.urdutyper.presentation.utils.extensions.views.launchAndRepeatWithViewLifecycle

@AndroidEntryPoint
class DashboardActivity : BaseActivity<ActivityDashboardBinding>() {
    private val viewModel: TypeViewModel by viewModels()
    private val sharedViewModel: SharedViewModel by viewModels()
    override fun inflateViewBinding(inflater: LayoutInflater): ActivityDashboardBinding =
        ActivityDashboardBinding.inflate(inflater)

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        launchAndRepeatWithViewLifecycle() {
            launch {
                viewModel.uiState.collectLatest {
                    handleUiState(
                        it
                    )
                }
            }
        }
    }

    private fun handleUiState(state: TypeUiState) {
        when (state) {
            is TypeUiState.Loading -> Snackbar.make(
                binding.container,
                "Uploading your file",
                Snackbar.LENGTH_LONG
            )
                .show()

            is TypeUiState.Error -> dialog(state.message).show()
            is TypeUiState.ImageUploaded -> {
                Snackbar.make(binding.container, "Successfully Uploaded", Snackbar.LENGTH_LONG)
                    .show()
                sharedViewModel.shouldRefresh.value = true
            }

            else -> {}
        }
    }
}