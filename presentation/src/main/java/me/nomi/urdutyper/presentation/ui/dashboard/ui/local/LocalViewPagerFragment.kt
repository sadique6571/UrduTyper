package me.nomi.urdutyper.presentation.ui.dashboard.ui.local

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import androidx.appcompat.widget.PopupMenu
import androidx.fragment.app.activityViewModels
import androidx.navigation.fragment.findNavController
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch
import me.nomi.urdutyper.R
import me.nomi.urdutyper.databinding.FragmentViewpagerBinding
import me.nomi.urdutyper.domain.entity.Image
import me.nomi.urdutyper.presentation.app.base.BaseFragment
import me.nomi.urdutyper.presentation.ui.dashboard.ui.main.FragmentViewPagerAdapter
import me.nomi.urdutyper.presentation.sharedviewmodel.SharedViewModel
import me.nomi.urdutyper.presentation.utils.common.ImageMaker.getListOfImages
import me.nomi.urdutyper.presentation.utils.extensions.views.launchAndRepeatWithViewLifecycle

@AndroidEntryPoint
class LocalViewPagerFragment : BaseFragment<FragmentViewpagerBinding>() {
    private val sharedViewModel: SharedViewModel by activityViewModels()
    private lateinit var adapter: FragmentViewPagerAdapter
    override fun inflateViewBinding(inflater: LayoutInflater) =
        FragmentViewpagerBinding.inflate(layoutInflater)

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        setViewPager()
        setCollectors()
        init()
    }

    private fun init() {
        sharedViewModel.localImageList.value = getListOfImages(requireActivity())
    }

    private fun setViewPager() {
        adapter = FragmentViewPagerAdapter()
        adapter.apply {
            isCloud = false
            onDelete = {
                sharedViewModel.shouldRefresh.value = true
                if (adapter.itemCount == 1)
                    findNavController().navigateUp()
            }
        }
        binding.viewPager.apply {
            adapter = this@LocalViewPagerFragment.adapter
        }

    }

    private fun setCollectors() {
        launchAndRepeatWithViewLifecycle {
            launch { sharedViewModel.localImageList.collectLatest { loadImages(it) } }
            launch { sharedViewModel.position.collectLatest { updatePosition(it) } }
        }
    }

    private fun updatePosition(position: Int) {
        binding.viewPager.setCurrentItem(position, false)
    }

    private fun loadImages(images: List<Image>) {
        adapter.pushData(images)
    }

}