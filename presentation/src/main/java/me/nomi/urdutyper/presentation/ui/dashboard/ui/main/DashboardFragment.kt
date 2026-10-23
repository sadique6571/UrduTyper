package me.nomi.urdutyper.presentation.ui.dashboard.ui.main

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import androidx.activity.addCallback
import androidx.core.view.GravityCompat
import androidx.drawerlayout.widget.DrawerLayout
import androidx.fragment.app.viewModels
import androidx.lifecycle.lifecycleScope
import androidx.navigation.fragment.findNavController
import androidx.viewpager2.widget.ViewPager2
import com.bumptech.glide.Glide
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch
import me.nomi.urdutyper.R
import me.nomi.urdutyper.databinding.FragmentDashboardBinding
import me.nomi.urdutyper.presentation.app.base.BaseFragment
import me.nomi.urdutyper.presentation.sharedviewmodel.SharedViewModel
import me.nomi.urdutyper.presentation.ui.dashboard.state.DashboardFragmentStateAdapter
import me.nomi.urdutyper.presentation.ui.dashboard.state.DashboardNavigationState
import me.nomi.urdutyper.presentation.ui.dashboard.state.DashboardUiState
import me.nomi.urdutyper.presentation.ui.dashboard.viewmodel.DashboardViewModel
import me.nomi.urdutyper.presentation.utils.extensions.common.dialog
import me.nomi.urdutyper.presentation.utils.extensions.views.launchAndRepeatWithViewLifecycle
import me.nomi.urdutyper.presentation.utils.extensions.views.setTextOrGone


@AndroidEntryPoint
class DashboardFragment : BaseFragment<FragmentDashboardBinding>() {
    private val sharedViewModel: SharedViewModel by viewModels()
    private val viewModel: DashboardViewModel by viewModels()
    override fun inflateViewBinding(inflater: LayoutInflater) =
        FragmentDashboardBinding.inflate(inflater)

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        binding.viewPager.adapter = DashboardFragmentStateAdapter(childFragmentManager, lifecycle)

        binding.viewPager.registerOnPageChangeCallback(object : ViewPager2.OnPageChangeCallback() {
            override fun onPageSelected(position: Int) {
                binding.bottomNav.menu.getItem(position).isChecked = true
            }
        })

        binding.bottomNav.setOnItemSelectedListener { item ->
            when (item.itemId) {
                R.id.cloud_dashboard_fragment -> binding.viewPager.currentItem = 0
                R.id.local_dashboard_fragment -> binding.viewPager.currentItem = 1
            }
            true
        }

        launchAndRepeatWithViewLifecycle {
            launch { sharedViewModel.openDrawer.collectLatest { handleDrawer(it) } }
            launch { viewModel.uiState.collect { handleUiState(it) } }
            launch { viewModel.navigationState.collect { handleNavigationState(it) } }
        }

        binding.profileIcon.setOnClickListener {
            lifecycleScope.launch { sharedViewModel.openDrawer.emit(true) }
        }

        binding.sideBar.logoutButton.setOnClickListener {
            viewModel.logout()
        }

        var name = prefs.user?.name
        if (name.isNullOrEmpty()) {
            name = prefs.user?.email
        }
        binding.sideBar.userEmail.setTextOrGone(prefs.user?.email)
        binding.userName.setTextOrGone(name)
        binding.sideBar.userName.setTextOrGone(prefs.user?.name)
        Glide.with(this).load(prefs.user?.photoUrl).error(R.drawable.profile_placeholder)
            .centerCrop().into(binding.profileIcon)
        Glide.with(this).load(prefs.user?.photoUrl).error(R.drawable.profile_placeholder)
            .centerCrop().into(binding.sideBar.userImage)

        requireActivity().onBackPressedDispatcher.addCallback(viewLifecycleOwner) {
            if (binding.viewPager.currentItem == 1) {
                binding.viewPager.currentItem = 0
            } else {
                isEnabled = false
                activity?.onBackPressed()
            }
        }

    }

    private fun handleNavigationState(state: DashboardNavigationState) = when (state) {
        is DashboardNavigationState.Logout -> logout()
        else -> {}
    }


    private fun logout() {
        findNavController().navigate(DashboardFragmentDirections.toOnboardingActivity())
        finishAffinity()
    }

    private fun handleUiState(state: DashboardUiState) = when (state) {
        is DashboardUiState.Error -> showDialog(state.message)
        else -> {}
    }

    private fun showDialog(message: String) {
        dialog(message).show()
    }

    private fun handleDrawer(shouldOpen: Boolean) {
        if (shouldOpen) {
            binding.drawerLayout.openDrawer(GravityCompat.START)
            binding.drawerLayout.addDrawerListener(object : DrawerLayout.DrawerListener {
                override fun onDrawerSlide(drawerView: View, slideOffset: Float) {
                    // Your sliding animation logic here
                    val diffScaledOffset = slideOffset * (1 - 0.7f)
                    val offsetScale = 1 - diffScaledOffset
                    binding.contentView.scaleX = offsetScale
                    binding.contentView.scaleY = offsetScale

                    val xOffset = drawerView.width * slideOffset
                    val xOffsetDiff = binding.contentView.width * diffScaledOffset / 2
                    val xTranslation = xOffset - xOffsetDiff
                    binding.contentView.translationX = xTranslation
                }

                override fun onDrawerOpened(drawerView: View) {
                    // Drawer opened callback
                }

                override fun onDrawerClosed(drawerView: View) {
                    // Drawer closed callback
                }

                override fun onDrawerStateChanged(newState: Int) {
                    // Drawer state changed callback
                }
            })
        } else binding.drawerLayout.closeDrawer(GravityCompat.START)
    }

    override fun onDestroyView() {
        super.onDestroyView()
        binding.viewPager.adapter = null
    }
}