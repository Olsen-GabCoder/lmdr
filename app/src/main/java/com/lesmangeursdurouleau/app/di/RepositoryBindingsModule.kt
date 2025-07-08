// PRÊT À COLLER - Fichier RepositoryBindingsModule.kt
package com.lesmangeursdurouleau.app.di

import com.lesmangeursdurouleau.app.data.repository.* // Import générique pour inclure le nouveau repository
import dagger.Binds
import dagger.Module
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
abstract class RepositoryBindingsModule {

    @Binds
    @Singleton
    abstract fun bindBookRepository(impl: BookRepositoryImpl): BookRepository

    @Binds
    @Singleton
    abstract fun bindPrivateChatRepository(impl: PrivateChatRepositoryImpl): PrivateChatRepository

    @Binds
    @Singleton
    abstract fun bindSocialRepository(impl: SocialRepositoryImpl): SocialRepository

    @Binds
    @Singleton
    abstract fun bindReadingRepository(impl: ReadingRepositoryImpl): ReadingRepository

    @Binds
    @Singleton
    abstract fun bindUserProfileRepository(impl: UserProfileRepositoryImpl): UserProfileRepository

    @Binds
    @Singleton
    abstract fun bindLeaderboardRepository(impl: LeaderboardRepositoryImpl): LeaderboardRepository

    @Binds
    @Singleton
    abstract fun bindChallengesRepository(impl: ChallengesRepositoryImpl): ChallengesRepository

    @Binds
    @Singleton
    abstract fun bindDictionaryRepository(impl: DictionaryRepositoryImpl): DictionaryRepository

    // NOUVELLE LIAISON POUR LE REPOSITORY DES NOTIFICATIONS
    @Binds
    @Singleton
    abstract fun bindNotificationRepository(impl: NotificationRepositoryImpl): NotificationRepository
}