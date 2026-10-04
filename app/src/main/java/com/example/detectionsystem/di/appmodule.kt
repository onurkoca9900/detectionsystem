package com.example.detectionsystem.di

import android.content.Context
import com.example.detectionsystem.data.DmsDatabase
import com.example.detectionsystem.data.DrivingDao
import com.google.firebase.auth.FirebaseAuth
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
object AppModule {

    // 1. Firebase'i tüm projeye tek noktadan sunar
    @Provides
    @Singleton
    fun provideFirebaseAuth(): FirebaseAuth {
        return FirebaseAuth.getInstance()
    }

    // 2. Room Veritabanını tüm projeye tek noktadan sunar
    @Provides
    @Singleton
    fun provideDatabase(@ApplicationContext context: Context): DmsDatabase {
        return DmsDatabase.getDatabase(context)
    }

    // 3. Veritabanı sorgularını (Dao) sunar
    @Provides
    @Singleton
    fun provideDrivingDao(database: DmsDatabase): DrivingDao {
        return database.drivingDao()
    }
}