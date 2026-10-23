package me.nomi.urdutyper.presentation.ui.dashboard.ui.local

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import androidx.core.view.isVisible
import androidx.fragment.app.activityViewModels
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
import me.nomi.urdutyper.presentation.utils.common.ImageMaker.getListOfImages
import me.nomi.urdutyper.presentation.utils.extensions.adapter.attach
import me.nomi.urdutyper.presentation.utils.extensions.common.dialog
import me.nomi.urdutyper.presentation.utils.extensions.views.launchAndRepeatWithViewLifecycle


@AndroidEntryPoint
class LocalDashboardFragment : BaseFragment<FragmentLocalDashboardBinding>(), DashboardView {
    private val sharedViewModel: SharedViewModel by activityViewModels()
    private val adapter by lazy { DashboardAdapter() }

    override fun inflateViewBinding(inflater: LayoutInflater) =
        FragmentLocalDashboardBinding.inflate(inflater)

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        setupViews()
        setCollector()
        init()
    }

    private fun init() {
        sharedViewModel.localImageList.value = getListOfImages(requireActivity())
    }

    private fun setupViews() {
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