package me.nomi.urdutyper.domain.usecase

import me.nomi.urdutyper.domain.repository.FirebaseRepository

class LoginWithGoogle(private val firebaseRepository: FirebaseRepository) {
    suspend fun invoke(idToken: String) = firebaseRepository.signInWithGoogle(idToken)
}