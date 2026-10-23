package me.nomi.urdutyper.presentation.ui.dashboard.ui.cloud

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import androidx.core.view.isVisible
import androidx.fragment.app.activityViewModels
import androidx.fragment.app.viewModels
import androidx.lifecycle.lifecycleScope
import androidx.navigation.fragment.findNavController
import androidx.recyclerview.widget.GridLayoutManager
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch
import me.nomi.urdutyper.databinding.FragmentCloudDashboardBinding
import me.nomi.urdutyper.domain.entity.Image
import me.nomi.urdutyper.presentation.app.base.BaseFragment
import me.nomi.urdutyper.presentation.sharedviewmodel.SharedViewModel
import me.nomi.urdutyper.presentation.ui.dashboard.state.DashboardNavigationState
import me.nomi.urdutyper.presentation.ui.dashboard.state.DashboardUiState
import me.nomi.urdutyper.presentation.ui.dashboard.ui.main.DashboardAdapter
import me.nomi.urdutyper.presentation.ui.dashboard.ui.main.DashboardFragmentDirections
import me.nomi.urdutyper.presentation.ui.dashboard.view.DashboardView
import me.nomi.urdutyper.presentation.ui.dashboard.viewmodel.DashboardViewModel
import me.nomi.urdutyper.presentation.utils.extensions.adapter.attach
import me.nomi.urdutyper.presentation.utils.extensions.views.launchAndRepeatWithViewLifecycle


@AndroidEntryPoint
class CloudDashboardFragment : BaseFragment<FragmentCloudDashboardBinding>(), DashboardView {
    private val viewModel: DashboardViewModel by viewModels()
    private val sharedViewModel: SharedViewModel by activityViewModels()
    private val adapter by lazy { DashboardAdapter() }

    override fun inflateViewBinding(inflater: LayoutInflater) =
        FragmentCloudDashboardBinding.inflate(inflater)

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        setupViews()
        observeViewModel()
        init()
    }

    private fun init() {
        viewLifecycleOwner.lifecycleScope.launch {
            delay(5000)
            binding.swipeRefresh.setOnRefreshListener {
                sharedViewModel.shouldRefresh.value = true
                binding.swipeRefresh.isRefreshing = false
                sharedViewModel.shouldRefresh.value = false
            }
        }
    }

    private fun setupViews() {
        setupRecyclerView()
        setClickListeners()
    }

    private fun setupRecyclerView() {
        binding.recView.attach(
            layoutManager = GridLayoutManager(requireActivity(), 2),
            adapter = adapter,
            onItemClick = { position, _ ->
                viewModel.onImageClick(adapter.getData(), position)
            },
            isNestedScrollingEnabled = true
        )
    }

    private fun setClickListeners() {
        binding.apply {
            newImage.setOnClickListener {
                createNew()
            }
        }
    }

    private fun observeViewModel() = with(viewModel) {
        launchAndRepeatWithViewLifecycle {
            launch { uiState.collect { handleUiState(it) } }
            launch { navigationState.collect { handleNavigationState(it) } }
            launch { sharedViewModel.cloudImageList.collectLatest { showImages(it) } }
            launch {
                sharedViewModel.shouldRefresh.collectLatest { refresh(it) }
            }
        }
    }

    private fun refresh(shouldRefresh: Boolean) {
        if (shouldRefresh) {
            viewModel.loadImageList()
        }
    }

    private fun handleUiState(it: DashboardUiState) {
        when (it) {
            is DashboardUiState.ShowImages -> {
                sharedViewModel.cloudImageList.value = it.images
            }

            else -> {}
        }
    }

    private fun handleNavigationState(state: DashboardNavigationState) = when (state) {
        is DashboardNavigationState.Logout -> {}
        is DashboardNavigationState.GoToViewPagerFragment -> {
            sharedViewModel.cloudImageList.value = state.images
            sharedViewModel.position.value = state.position
            goToViewPagerFragment()
        }

        is DashboardNavigationState.ImageDeleted -> {}
    }

    override fun showImages(images: List<Image>) {
        binding.noImage.isVisible = images.isEmpty()
        adapter.pushData(emptyList())
        adapter.pushData(images)
    }


    override fun goToViewPagerFragment() {
        findNavController().navigate(DashboardFragmentDirections.toCloudViewPagerFragment())
    }

    private fun createNew() =
        findNavController().navigate(DashboardFragmentDirections.toTypeFragment())
}