package me.nomi.urdutyper.domain.usecase

import me.nomi.urdutyper.domain.repository.FirebaseRepository

class ResetPassword(
    private val firebaseRepository: FirebaseRepository
) {
    suspend operator fun invoke(email: String) = firebaseRepository.resetPassword(email)
}