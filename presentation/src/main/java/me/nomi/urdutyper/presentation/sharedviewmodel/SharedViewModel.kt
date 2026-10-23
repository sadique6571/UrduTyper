package me.nomi.urdutyper.presentation.sharedviewmodel

import androidx.lifecycle.ViewModel
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import me.nomi.urdutyper.domain.entity.Image

class SharedViewModel : ViewModel() {
    val cloudImageList = MutableStateFlow(emptyList<Image>())
    val localImageList = MutableStateFlow(emptyList<Image>())
    val position = MutableStateFlow(0)
    val shouldRefresh = MutableStateFlow(false)
    val openDrawer = MutableSharedFlow<Boolean>()
}