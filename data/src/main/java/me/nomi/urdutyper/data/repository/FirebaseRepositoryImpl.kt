package me.nomi.urdutyper.data.repository


import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.auth.GoogleAuthProvider
import com.google.firebase.database.FirebaseDatabase
import com.google.firebase.storage.FirebaseStorage
import kotlinx.coroutines.tasks.await
import kotlinx.coroutines.withContext
import me.nomi.urdutyper.data.mapper.Mapper.toDomain
import me.nomi.urdutyper.data.source.PosterBackupClient
import me.nomi.urdutyper.domain.entity.Image
import me.nomi.urdutyper.domain.entity.StorageUsage
import me.nomi.urdutyper.domain.entity.User
import me.nomi.urdutyper.domain.repository.FirebaseRepository
import me.nomi.urdutyper.domain.utils.Result
import me.nomi.urdutyper.domain.utils.dispatchers.DispatchersProviders
import java.io.File
import javax.inject.Inject

class FirebaseRepositoryImpl @Inject constructor(
    private val firebaseAuth: FirebaseAuth,
    private val firebaseDatabase: FirebaseDatabase,
    private val firebaseStorage: FirebaseStorage,
    private val posterBackupClient: PosterBackupClient,
    private val dispatchers: DispatchersProviders
) : FirebaseRepository {

    override suspend fun register(
        email: String,
        password: String
    ): Result<User> = withContext(dispatchers.getIO()) {
        return@withContext safeApiCall {
            val result = firebaseAuth.createUserWithEmailAndPassword(email, password).await()
            firebaseAuth.currentUser?.sendEmailVerification()?.await()
            result.user?.toDomain() ?: throw Exception("Something went wrong")
        }
    }

    override suspend fun login(email: String, password: String): Result<User> {
        return safeApiCall {
            val result = firebaseAuth.signInWithEmailAndPassword(email, password).await()
            val user = result.user
            if (user != null) {
                if (user.isEmailVerified) {
                    user.toDomain()
                } else {
                    firebaseAuth.signOut()
                    throw Exception("Please verify your email first")
                }
            } else {
                throw Exception("User not found")
            }
        }
    }

    override suspend fun signInWithGoogle(idToken: String): Result<User> {
        return safeApiCall {
            val credential = GoogleAuthProvider.getCredential(idToken, null)
            val result = firebaseAuth.signInWithCredential(credential).await()
            val user = result.user
            user?.toDomain() ?: throw Exception("User not found")
        }
    }




    override suspend fun logOut(): Result<Unit> {
        return safeApiCall {
            firebaseAuth.signOut()
        }
    }

    override suspend fun getUserData(): Result<User> {
        return safeApiCall {
            val currentUser = firebaseAuth.currentUser
            currentUser?.toDomain() ?: throw Exception("No user data found")
        }
    }

    // Poster backup now lives on the Cloudflare Worker (KV), not Firebase:
    // login still uses Firebase Auth, but poster upload/list/delete go
    // through PosterBackupClient. Cloud Image.name is the full filename
    // (Worker list `name`), so the backup matcher compares full local
    // filenames against it. firebaseDatabase/firebaseStorage stay injected
    // for DI compatibility; nothing poster-related reads them anymore.
    override suspend fun loadImages(): Result<List<Image>> {
        return safeApiCall {
            posterBackupClient.listPosters().map { Image(it.name, it.url) }
        }
    }

    override suspend fun deleteImage(file: Image): Result<Unit> {
        return safeApiCall {
            posterBackupClient.deletePoster(file.name)
        }
    }

    override suspend fun uploadImage(file: File): Result<Unit> {
        return safeApiCall {
            posterBackupClient.uploadPoster(file)
        }
    }

    override suspend fun getStorageUsage(): Result<StorageUsage> {
        return safeApiCall {
            posterBackupClient.getStorageUsage()
        }
    }

    override suspend fun resetPassword(email: String): Result<Unit> {
        return safeApiCall {
            firebaseAuth.sendPasswordResetEmail(email).await()
        }
    }
}