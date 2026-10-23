package me.nomi.urdutyper.domain.usecase

import me.nomi.urdutyper.domain.repository.FirebaseRepository

open class Register(private val firebaseRepository: FirebaseRepository) {
    suspend operator fun invoke(email: String, password: String) =
        firebaseRepository.register(email = email, password = password)
}
