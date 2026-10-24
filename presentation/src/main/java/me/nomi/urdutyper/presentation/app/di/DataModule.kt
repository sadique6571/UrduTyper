package me.nomi.urdutyper.presentation.app.di

import android.content.Context
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.database.FirebaseDatabase
import com.google.firebase.storage.FirebaseStorage
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import me.nomi.urdutyper.data.repository.FirebaseRepositoryImpl
import me.nomi.urdutyper.data.source.SharedPreference
import me.nomi.urdutyper.domain.repository.FirebaseRepository
import me.nomi.urdutyper.domain.repository.SharedPreferenceRepository
import me.nomi.urdutyper.domain.usecase.DeleteImage
import me.nomi.urdutyper.domain.usecase.LoadImages
import me.nomi.urdutyper.domain.usecase.Login
import me.nomi.urdutyper.domain.usecase.LoginWithGoogle
import me.nomi.urdutyper.domain.usecase.Logout
import me.nomi.urdutyper.domain.usecase.Register
import me.nomi.urdutyper.domain.usecase.ResetPassword
import me.nomi.urdutyper.domain.usecase.UploadImage
import me.nomi.urdutyper.domain.usecase.UserData
import me.nomi.urdutyper.domain.utils.dispatchers.DispatchersProviders
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
class DataModule {

    @Singleton
    @Provides
    fun providefirebaseRepository(
        firebaseAuth: FirebaseAuth,
        firebaseDatabase: FirebaseDatabase,
        firebaseStorage: FirebaseStorage,
        dispatcherProvider: DispatchersProviders
    ): FirebaseRepository =
        FirebaseRepositoryImpl(
            firebaseAuth,
            firebaseDatabase,
            firebaseStorage,
            dispatcherProvider
        )

    @Singleton
    @Provides
    fun provideSharedPref(@ApplicationContext context: Context): SharedPreferenceRepository =
        SharedPreference(context)

    @Provides
    fun provideLogin(firebaseRepository: FirebaseRepository): Login =
        Login(firebaseRepository)
    @Provides
    fun provideLoginWithGoogle(firebaseRepository: FirebaseRepository): LoginWithGoogle =
        LoginWithGoogle(firebaseRepository)

    @Provides
    fun provideLogout(firebaseRepository: FirebaseRepository): Logout =
        Logout(firebaseRepository)

    @Provides
    fun provideRegister(firebaseRepository: FirebaseRepository): Register =
        Register(firebaseRepository)

    @Provides
    fun provideUserData(firebaseRepository: FirebaseRepository): UserData =
        UserData(firebaseRepository)

    @Provides
    fun provideLoadImages(firebaseRepository: FirebaseRepository) = LoadImages(firebaseRepository)

    @Provides
    fun provideDeleteImage(firebaseRepository: FirebaseRepository) = DeleteImage(firebaseRepository)

    @Provides
    fun provideUploadImage(firebaseRepository: FirebaseRepository) = UploadImage(firebaseRepository)

    @Provides
    fun provideResetPassword(firebaseRepository: FirebaseRepository) = ResetPassword(firebaseRepository)
}
