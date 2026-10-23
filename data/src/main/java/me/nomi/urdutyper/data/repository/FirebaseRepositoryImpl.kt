package me.nomi.urdutyper.data.repository


import android.net.Uri
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.auth.GoogleAuthProvider
import com.google.firebase.database.FirebaseDatabase
import com.google.firebase.storage.FirebaseStorage
import kotlinx.coroutines.tasks.await
import kotlinx.coroutines.withContext
import me.nomi.urdutyper.data.mapper.Mapper.toDomain
import me.nomi.urdutyper.domain.entity.Image
import me.nomi.urdutyper.domain.entity.User
import me.nomi.urdutyper.domain.repository.FirebaseRepository
import me.nomi.urdutyper.domain.utils.Result
import me.nomi.urdutyper.domain.utils.dispatchers.DispatchersProviders
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import javax.inject.Inject

class FirebaseRepositoryImpl @Inject constructor(
    private val firebaseAuth: FirebaseAuth,
    private val firebaseDatabase: FirebaseDatabase,
    private val firebaseStorage: FirebaseStorage,
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

    override suspend fun loadImages(): Result<List<Image>> {
        return safeApiCall {
            val images = mutableListOf<Image>()
            val reference =
                firebaseDatabase.reference.child(firebaseAuth.currentUser?.uid!!).child("Images")
            reference.keepSynced(true)
            val snapshot = reference.get().await()
            for (childSnapshot in snapshot.children) {
                val filename = childSnapshot.key.toString()
                val url = childSnapshot.value.toString()
                images.add(Image(filename, url))
            }
            images
        }
    }

    override suspend fun deleteImage(file: Image): Result<Unit> {
        return safeApiCall {
            firebaseDatabase.reference.child(firebaseAuth.currentUser?.uid!!).child("Images")
                .child(file.name).removeValue()
                .await()
            firebaseStorage.getReferenceFromUrl(file.url).delete().await()
        }
    }

    override suspend fun uploadImage(file: File): Result<Unit> {
        return safeApiCall {
            val filepath = "${firebaseAuth.currentUser?.uid}/Images/${file.name}"
            val mountainImagesRef = firebaseStorage.reference.child(filepath)
            mountainImagesRef.putFile(Uri.fromFile(file)).await()
            val uri = mountainImagesRef.downloadUrl.await()
            firebaseDatabase.reference.child(firebaseAuth.currentUser?.uid!!).child("Images").child(
                "UT" + SimpleDateFormat("yyyyMMdd_hhmmss", Locale.getDefault()).format(
                    Date()
                )
            ).setValue(uri.toString()).await()
        }
    }

    override suspend fun resetPassword(email: String): Result<Unit> {
        return safeApiCall {
            firebaseAuth.sendPasswordResetEmail(email).await()
        }
    }
}