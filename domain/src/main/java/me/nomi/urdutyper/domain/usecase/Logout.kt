package me.nomi.urdutyper.domain.usecase

import me.nomi.urdutyper.domain.repository.FirebaseRepository

open class Logout(private val firebaseRepository: FirebaseRepository) {
    suspend operator fun invoke() = firebaseRepository.logOut()
}
