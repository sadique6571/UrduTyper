package me.nomi.urdutyper.presentation.ui.dashboard.ui.local

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import androidx.core.view.isVisible
import androidx.fragment.app.activityViewModels
import androidx.fragment.app.viewModels
import androidx.navigation.fragment.findNavController
import androidx.recyclerview.widget.GridLayoutManager
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch
import me.nomi.urdutyper.databinding.FragmentLocalDashboardBinding
import me.nomi.urdutyper.domain.entity.Image
import me.nomi.urdutyper.presentation.app.base.BaseFragment
import me.nomi.urdutyper.presentation.sharedviewmodel.SharedViewModel
import me.nomi.urdutyper.presentation.ui.dashboard.ui.main.DashboardAdapter
import me.nomi.urdutyper.presentation.ui.dashboard.ui.main.DashboardFragmentDirections
import me.nomi.urdutyper.presentation.ui.dashboard.view.DashboardView
import me.nomi.urdutyper.presentation.ui.dashboard.state.DashboardUiState
import me.nomi.urdutyper.presentation.ui.dashboard.viewmodel.DashboardViewModel
import me.nomi.urdutyper.presentation.utils.common.ImageMaker.getListOfImages
import me.nomi.urdutyper.presentation.utils.extensions.adapter.attach
import me.nomi.urdutyper.presentation.utils.extensions.common.dialog
import me.nomi.urdutyper.presentation.utils.extensions.views.launchAndRepeatWithViewLifecycle


@AndroidEntryPoint
class LocalDashboardFragment : BaseFragment<FragmentLocalDashboardBinding>(), DashboardView {
    private val sharedViewModel: SharedViewModel by activityViewModels()
    private val dashboardViewModel: DashboardViewModel by viewModels()
    private val adapter by lazy { DashboardAdapter() }
    private var backupInProgress = false

    // One-shot silent auto-backup: runs once per Local visit session. If it
    // fails (offline / logged out) it stays armed so the next visit retries;
    // the manual button is always available with full error reporting.
    private var autoBackupInProgress = false
    private var autoBackupDone = false

    override fun inflateViewBinding(inflater: LayoutInflater) =
        FragmentLocalDashboardBinding.inflate(inflater)

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        setupViews()
        setCollector()
        init()
    }

    override fun onResume() {
        super.onResume()
        // Fresh after returning from the editor, and the trigger for the
        // one-shot auto-backup of posters saved while offline / logged out.
        val images = getListOfImages(requireActivity())
        sharedViewModel.localImageList.value = images
        if (!autoBackupDone && images.isNotEmpty()) {
            autoBackupInProgress = true
            dashboardViewModel.backupMissingImages(images)
        }
    }

    private fun init() {
        sharedViewModel.localImageList.value = getListOfImages(requireActivity())
    }

    private fun setupViews() {
        binding.backupButton.setOnClickListener {
            backupInProgress = true
            binding.backupButton.isEnabled = false
            dashboardViewModel.backupMissingImages(sharedViewModel.localImageList.value)
        }
        binding.swipeRefresh.setOnRefreshListener {
            sharedViewModel.shouldRefresh.value = true
            binding.swipeRefresh.isRefreshing = false
            sharedViewModel.shouldRefresh.value = false
        }
        binding.recView.attach(
            layoutManager = GridLayoutManager(requireActivity(), 2),
            adapter = adapter,
            onItemClick = { position, _ ->
                sharedViewModel.position.value = position
                goToViewPagerFragment()
            },
            isNestedScrollingEnabled = true
        )
    }

    private fun setCollector() {
        launchAndRepeatWithViewLifecycle {
            launch { sharedViewModel.localImageList.collectLatest { showImages(it) } }
            launch { sharedViewModel.shouldRefresh.collectLatest { refresh(it) } }
            launch { dashboardViewModel.uiState.collectLatest { handleBackupState(it) } }
        }
    }

    private fun handleBackupState(state: DashboardUiState) {
        when (state) {
            is DashboardUiState.Loading -> {
                if (backupInProgress) binding.backupButton.isEnabled = false
            }

            is DashboardUiState.Error -> {
                if (backupInProgress) {
                    backupInProgress = false
                    binding.backupButton.isEnabled = true
                    dialog(state.message).show()
                }
                // Silent for auto-backup: no nagging on offline visits; the
                // manual button still reports the full error, and the next
                // visit retries automatically.
                if (autoBackupInProgress) autoBackupInProgress = false
            }

            is DashboardUiState.BackupDone -> {
                if (backupInProgress) {
                    backupInProgress = false
                    binding.backupButton.isEnabled = true
                    dialog("${state.backedUp} images Cloud par backup ho gayin (${state.alreadyThere} pehle se thin)").show()
                }
                if (autoBackupInProgress) {
                    autoBackupInProgress = false
                    autoBackupDone = true
                    if (state.backedUp > 0) {
                        dialog("${state.backedUp} images Cloud par auto-backup ho gayin").show()
                    }
                }
            }

            else -> {}
        }
    }

    private fun refresh(shouldRefresh: Boolean) {
        if (shouldRefresh) {
            sharedViewModel.localImageList.value = emptyList()
            sharedViewModel.localImageList.value = getListOfImages(requireActivity())
        }
    }

    override fun showImages(images: List<Image>) {
        binding.noImage.isVisible = images.isEmpty()
        adapter.pushData(images)
        binding.swipeRefresh.isRefreshing = false
        sharedViewModel.shouldRefresh.value = false
    }


    override fun goToViewPagerFragment() {
        findNavController().navigate(DashboardFragmentDirections.toLocalViewPagerFragment())
    }
}