package me.nomi.urdutyper.presentation.ui.dashboard.viewmodel

import android.os.Environment
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.update
import me.nomi.urdutyper.domain.entity.Image
import java.io.File
import me.nomi.urdutyper.domain.usecase.DeleteImage
import me.nomi.urdutyper.domain.usecase.LoadImages
import me.nomi.urdutyper.domain.usecase.Logout
import me.nomi.urdutyper.domain.usecase.UploadImage
import me.nomi.urdutyper.domain.utils.dispatchers.DispatchersProviders
import me.nomi.urdutyper.domain.utils.onError
import me.nomi.urdutyper.domain.utils.onSuccess
import me.nomi.urdutyper.presentation.app.base.BaseViewModel
import me.nomi.urdutyper.presentation.ui.dashboard.state.DashboardNavigationState
import me.nomi.urdutyper.presentation.ui.dashboard.state.DashboardUiState
import javax.inject.Inject

@HiltViewModel
class DashboardViewModel @Inject constructor(
    private val loadImages: LoadImages,
    private val logout: Logout,
    private val deleteImage: DeleteImage,
    private val uploadImage: UploadImage,
    dispatchers: DispatchersProviders
) : BaseViewModel(dispatchers) {

    private val _uiState = MutableSharedFlow<DashboardUiState>()
    val uiState = _uiState.asSharedFlow()

    private val _navigationState: MutableSharedFlow<DashboardNavigationState> =
        MutableSharedFlow()
    val navigationState = _navigationState.asSharedFlow()

    init {
        loadImageList()
    }

    fun loadImageList() = launchOnMainImmediate {
        _uiState.emit(DashboardUiState.Loading)
        loadImages()
            .onSuccess { images ->
                _uiState.emit(DashboardUiState.ShowImages(images))
            }.onError { error ->
                _uiState.emit(
                    DashboardUiState.Error(
                        error.message ?: "Failed to load images from the database"
                    )
                )
            }
    }

    fun logout() = launchOnMainImmediate {
        _uiState.emit(DashboardUiState.Loading)
        logout.invoke()
            .onSuccess {
                _navigationState.emit(DashboardNavigationState.Logout)
            }.onError { error ->
                _uiState.emit(
                    DashboardUiState.Error(
                        error.message ?: "Failed to logout"
                    )
                )
            }
    }

    fun deleteImage(file: Image) = launchOnMainImmediate {
        _uiState.emit(DashboardUiState.Loading)
        deleteImage.invoke(file)
            .onSuccess {
                _navigationState.emit(DashboardNavigationState.ImageDeleted(file))
            }.onError { error ->
                _uiState.emit(
                    DashboardUiState.Error(
                        error.message ?: "Failed to Delete"
                    )
                )
            }
    }

    fun backupMissingImages(localImages: List<Image>) = launchOnMainImmediate {
        if (localImages.isEmpty()) {
            _uiState.emit(DashboardUiState.Error("Koi local image nahi mili"))
            return@launchOnMainImmediate
        }
        _uiState.emit(DashboardUiState.Loading)
        loadImages()
            .onSuccess { cloudImages ->
                val cloudNames = cloudImages.map { it.name }.toSet()
                val missing = localImages.filter { it.name.substringBeforeLast('.') !in cloudNames }
                var backedUp = 0
                var failed = 0
                for (image in missing) {
                    val pictureFileDir = File(
                        Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DCIM),
                        "Nomiii"
                    )
                    val file = File(pictureFileDir, image.name)
                    uploadImage.invoke(file)
                        .onSuccess { backedUp++ }
                        .onError { failed++ }
                }
                if (failed > 0) {
                    _uiState.emit(
                        DashboardUiState.Error("$failed images fail ho gayin, $backedUp backup ho gayin")
                    )
                } else {
                    _uiState.emit(
                        DashboardUiState.BackupDone(
                            backedUp = backedUp,
                            alreadyThere = localImages.size - missing.size
                        )
                    )
                }
            }.onError { error ->
                _uiState.emit(
                    DashboardUiState.Error(
                        error.message ?: "Failed to load images from the database"
                    )
                )
            }
    }

    fun onImageClick(images: List<Image>, position: Int) = launchOnMainImmediate {
        _navigationState.emit(DashboardNavigationState.GoToViewPagerFragment(images, position))
    }
}