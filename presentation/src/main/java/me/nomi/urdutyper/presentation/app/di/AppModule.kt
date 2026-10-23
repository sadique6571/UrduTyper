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
import me.nomi.urdutyper.domain.utils.dispatchers.DispatchersProviders
import me.nomi.urdutyper.domain.utils.dispatchers.DispatchersProvidersImpl
import javax.inject.Singleton


@Module
@InstallIn(SingletonComponent::class)
class AppModule {

    @Provides
    @Singleton
    fun provideContext(@ApplicationContext appContext: Context): Context = appContext


    @Singleton
    @Provides
    fun provideFirebaseAuth(): FirebaseAuth = FirebaseAuth.getInstance()

    @Singleton
    @Provides
    fun provideFirebaseDatabase(): FirebaseDatabase {
        val instance = FirebaseDatabase.getInstance()
        instance.setPersistenceEnabled(true)
        return instance
    }

    @Singleton
    @Provides
    fun provideFirebaseStorage() = FirebaseStorage.getInstance()

    @Provides
    fun provideDispatchersProvider(): DispatchersProviders {
        return DispatchersProvidersImpl
    }

}