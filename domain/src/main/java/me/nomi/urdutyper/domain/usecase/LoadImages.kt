package me.nomi.urdutyper.domain.usecase

import me.nomi.urdutyper.domain.repository.FirebaseRepository

class LoadImages(
    private val firebaseRepository: FirebaseRepository
) {
    suspend operator fun invoke() = firebaseRepository.loadImages()
}