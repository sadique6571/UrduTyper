package me.nomi.urdutyper.domain.repository

import me.nomi.urdutyper.domain.entity.Image
import me.nomi.urdutyper.domain.entity.User
import me.nomi.urdutyper.domain.utils.Result
import java.io.File

interface FirebaseRepository {
    suspend fun register(email: String, password: String): Result<User>
    suspend fun login(email: String, password: String): Result<User>
    suspend fun logOut(): Result<Unit>
    suspend fun getUserData(): Result<User>
    suspend fun loadImages(): Result<List<Image>>
    suspend fun deleteImage(file: Image): Result<Unit>
    suspend fun uploadImage(file: File): Result<Unit>
    suspend fun resetPassword(email: String): Result<Unit>
    suspend fun signInWithGoogle(idToken: String): Result<User>
}