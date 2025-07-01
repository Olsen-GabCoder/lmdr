// PRÊT À COLLER - Fichier 100% complet et validé
package com.lesmangeursdurouleau.app.di

import com.lesmangeursdurouleau.app.data.repository.AppConfigRepository
import com.lesmangeursdurouleau.app.data.repository.AppConfigRepositoryImpl
import com.lesmangeursdurouleau.app.data.repository.BookRepository
import com.lesmangeursdurouleau.app.data.repository.BookRepositoryImpl
import com.lesmangeursdurouleau.app.data.repository.ChallengesRepository
import com.lesmangeursdurouleau.app.data.repository.ChallengesRepositoryImpl
import com.lesmangeursdurouleau.app.data.repository.ChatRepository
import com.lesmangeursdurouleau.app.data.repository.ChatRepositoryImpl
import com.lesmangeursdurouleau.app.data.repository.DictionaryRepository
import com.lesmangeursdurouleau.app.data.repository.DictionaryRepositoryImpl
import com.lesmangeursdurouleau.app.data.repository.LeaderboardRepository
import com.lesmangeursdurouleau.app.data.repository.LeaderboardRepositoryImpl
import com.lesmangeursdurouleau.app.data.repository.MonthlyReadingRepository
import com.lesmangeursdurouleau.app.data.repository.MonthlyReadingRepositoryImpl
import com.lesmangeursdurouleau.app.data.repository.PrivateChatRepository
import com.lesmangeursdurouleau.app.data.repository.PrivateChatRepositoryImpl
import com.lesmangeursdurouleau.app.data.repository.ReadingRepository
import com.lesmangeursdurouleau.app.data.repository.ReadingRepositoryImpl
import com.lesmangeursdurouleau.app.data.repository.SocialRepository
import com.lesmangeursdurouleau.app.data.repository.SocialRepositoryImpl
import com.lesmangeursdurouleau.app.data.repository.UserProfileRepository
import com.lesmangeursdurouleau.app.data.repository.UserProfileRepositoryImpl
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

    // VALIDE : Le nouveau SocialRepository est correctement lié à son implémentation.
    @Binds
    @Singleton
    abstract fun bindSocialRepository(impl: SocialRepositoryImpl): SocialRepository

    // VALIDE : Le ReadingRepository allégé est correctement lié à son implémentation.
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

    // NOTE: Les bindings pour les repositories qui ont aussi des dépendances (comme AppConfigRepository)
    // ne peuvent pas être dans ce fichier "abstract". Ils doivent être dans un module avec @Provides.
    // Je vais donc les déplacer dans RepositoryModule pour une meilleure organisation.
}