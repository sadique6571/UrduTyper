package me.nomi.urdutyper.domain.usecase

import me.nomi.urdutyper.domain.repository.FirebaseRepository

class Login(private val firebaseRepository: FirebaseRepository)  {
    suspend fun invoke(email: String, password: String) = firebaseRepository.login(email = email, password = password)
}