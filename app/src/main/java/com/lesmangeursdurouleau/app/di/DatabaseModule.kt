package com.lesmangeursdurouleau.app.di

import android.content.Context
import androidx.room.Room
import com.lesmangeursdurouleau.app.data.database.AppLocalDatabase
import com.lesmangeursdurouleau.app.data.database.dao.HiddenCommentDao
import com.lesmangeursdurouleau.app.data.repository.LocalUserPreferencesRepository
import com.lesmangeursdurouleau.app.data.repository.LocalUserPreferencesRepositoryImpl
import dagger.Binds
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
object DatabaseModule {

    @Provides
    @Singleton
    fun provideAppLocalDatabase(@ApplicationContext context: Context): AppLocalDatabase {
        return Room.databaseBuilder(
            context,
            AppLocalDatabase::class.java,
            "les_mangeurs_du_rouleau_local_db"
        ).build()
    }

    @Provides
    @Singleton
    fun provideHiddenCommentDao(database: AppLocalDatabase): HiddenCommentDao {
        return database.hiddenCommentDao()
    }
}

@Module
@InstallIn(SingletonComponent::class)
abstract class LocalRepositoryModule {

    @Binds
    @Singleton
    abstract fun bindLocalUserPreferencesRepository(
        impl: LocalUserPreferencesRepositoryImpl
    ): LocalUserPreferencesRepository
}