package me.nomi.urdutyper.domain.usecase

import me.nomi.urdutyper.domain.repository.FirebaseRepository
import java.io.File

class UploadImage(
    private val firebaseRepository: FirebaseRepository
) {
    suspend operator fun invoke(file: File) = firebaseRepository.uploadImage(file)
}