package me.nomi.urdutyper.presentation.ui.type.state

sealed class TypeUiState {
    data object Init : TypeUiState()
    data object Loading : TypeUiState()
    data class Error(val message: String) : TypeUiState()
    data object ImageUploaded : TypeUiState()
}