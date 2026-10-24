package me.nomi.urdutyper.presentation.ui.dashboard.state

import androidx.fragment.app.Fragment
import androidx.fragment.app.FragmentManager
import androidx.lifecycle.Lifecycle
import androidx.viewpager2.adapter.FragmentStateAdapter
import me.nomi.urdutyper.presentation.ui.dashboard.ui.cloud.CloudDashboardFragment
import me.nomi.urdutyper.presentation.ui.dashboard.ui.home.HomeFragment
import me.nomi.urdutyper.presentation.ui.dashboard.ui.local.LocalDashboardFragment
import me.nomi.urdutyper.presentation.ui.dashboard.ui.templates.TemplatesFragment

class DashboardFragmentStateAdapter(
    fragmentManager: FragmentManager,
    lifecycle: Lifecycle
) : FragmentStateAdapter(fragmentManager, lifecycle) {

    override fun getItemCount() = 4

    override fun createFragment(position: Int): Fragment {
        return when (position) {
            0 -> HomeFragment()
            1 -> LocalDashboardFragment()
            2 -> CloudDashboardFragment()
            3 -> TemplatesFragment()
            else -> HomeFragment()
        }
    }
}
