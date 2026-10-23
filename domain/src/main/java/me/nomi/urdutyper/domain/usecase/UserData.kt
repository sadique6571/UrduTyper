package me.nomi.urdutyper.domain.usecase

import me.nomi.urdutyper.domain.repository.FirebaseRepository

open class UserData(private val firebaseRepository: FirebaseRepository) {
    suspend operator fun invoke() = firebaseRepository.getUserData()
}