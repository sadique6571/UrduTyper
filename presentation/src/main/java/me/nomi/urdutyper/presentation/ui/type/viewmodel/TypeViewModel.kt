package me.nomi.urdutyper.presentation.ui.type.viewmodel

import android.graphics.Color
import android.graphics.Typeface
import android.graphics.drawable.GradientDrawable
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asSharedFlow
import me.nomi.urdutyper.domain.usecase.UploadImage
import me.nomi.urdutyper.domain.utils.dispatchers.DispatchersProviders
import me.nomi.urdutyper.domain.utils.onError
import me.nomi.urdutyper.domain.utils.onSuccess
import me.nomi.urdutyper.presentation.app.base.BaseViewModel
import me.nomi.urdutyper.presentation.ui.type.state.TypeUiState
import java.io.File
import javax.inject.Inject

@HiltViewModel
class TypeViewModel @Inject constructor(
    private val uploadImage: UploadImage,
    dispatchers: DispatchersProviders
) : BaseViewModel(dispatchers) {
    private val _uiState = MutableSharedFlow<TypeUiState>()
    val uiState = _uiState.asSharedFlow()

    val gradientOrientation: MutableStateFlow<GradientDrawable.Orientation> =
        MutableStateFlow(GradientDrawable.Orientation.TR_BL)
    val font: MutableStateFlow<Typeface?> = MutableStateFlow(null)
    val typeface: MutableStateFlow<Int> = MutableStateFlow(0)
    val textColor = MutableStateFlow(Color.BLACK)
    val leftGradientColor = MutableStateFlow(-0xc0ae4b)
    val rightGradientColor = MutableStateFlow(-0xc0ae4b)
    val background = MutableStateFlow(
        GradientDrawable(
            GradientDrawable.Orientation.TR_BL,
            intArrayOf(-0xc0ae4b, -0xc0ae4b)
        )
    )
    val size = MutableStateFlow(50f)
    val isBold = MutableStateFlow(false)
    val isItalic = MutableStateFlow(false)

    fun setSolidColor(color: Int) {
        leftGradientColor.value = color
        rightGradientColor.value = color
    }


    fun uploadImage(file: File) = launchOnMainImmediate {
        _uiState.emit(TypeUiState.Loading)
        uploadImage.invoke(file)
            .onSuccess {
                _uiState.emit(TypeUiState.ImageUploaded)
            }.onError { error ->
                _uiState.emit(TypeUiState.Error(error.message ?: "Failed to Upload"))
            }
    }
}
