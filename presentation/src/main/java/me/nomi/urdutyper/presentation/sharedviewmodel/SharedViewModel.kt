package me.nomi.urdutyper.presentation.sharedviewmodel

import androidx.lifecycle.ViewModel
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import me.nomi.urdutyper.domain.entity.Image
import me.nomi.urdutyper.domain.entity.TemplateCategory

/**
 * Filter request a tab (Home) hands to the Templates tab. The DashboardFragment
 * holds the pending request; TemplatesFragment applies and clears it in onResume,
 * so a filter is never re-applied on a later visit.
 */
sealed class TemplateFilterRequest {
    object All : TemplateFilterRequest()
    data class Category(val category: TemplateCategory) : TemplateFilterRequest()
}

class SharedViewModel : ViewModel() {
    val cloudImageList = MutableStateFlow(emptyList<Image>())
    val localImageList = MutableStateFlow(emptyList<Image>())
    val position = MutableStateFlow(0)
    val shouldRefresh = MutableStateFlow(false)
    val openDrawer = MutableSharedFlow<Boolean>()
}