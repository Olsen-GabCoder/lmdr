// PRÊT À COLLER - Fichier 100% complet et modifié
package com.lesmangeursdurouleau.app.di

import com.lesmangeursdurouleau.app.data.repository.BookRepository
import com.lesmangeursdurouleau.app.data.repository.BookRepositoryImpl
import com.lesmangeursdurouleau.app.data.repository.ChallengesRepository
import com.lesmangeursdurouleau.app.data.repository.ChallengesRepositoryImpl
import com.lesmangeursdurouleau.app.data.repository.DictionaryRepository
import com.lesmangeursdurouleau.app.data.repository.DictionaryRepositoryImpl // NOUVEL IMPORT
import com.lesmangeursdurouleau.app.data.repository.LeaderboardRepository
import com.lesmangeursdurouleau.app.data.repository.LeaderboardRepositoryImpl
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
    abstract fun bindBookRepository(
        bookRepositoryImpl: BookRepositoryImpl
    ): BookRepository

    @Binds
    @Singleton
    abstract fun bindPrivateChatRepository(
        privateChatRepositoryImpl: PrivateChatRepositoryImpl
    ): PrivateChatRepository

    @Binds
    @Singleton
    abstract fun bindSocialRepository(
        socialRepositoryImpl: SocialRepositoryImpl
    ): SocialRepository

    @Binds
    @Singleton
    abstract fun bindReadingRepository(
        readingRepositoryImpl: ReadingRepositoryImpl
    ): ReadingRepository

    @Binds
    @Singleton
    abstract fun bindUserProfileRepository(
        userProfileRepositoryImpl: UserProfileRepositoryImpl
    ): UserProfileRepository

    @Binds
    @Singleton
    abstract fun bindLeaderboardRepository(
        leaderboardRepositoryImpl: LeaderboardRepositoryImpl
    ): LeaderboardRepository

    @Binds
    @Singleton
    abstract fun bindChallengesRepository(
        challengesRepositoryImpl: ChallengesRepositoryImpl
    ): ChallengesRepository

    // ==========================================================
    // DÉBUT DE LA MODIFICATION : Remplacement du Repository factice
    // ==========================================================
    @Binds
    @Singleton
    abstract fun bindDictionaryRepository(
        dictionaryRepositoryImpl: DictionaryRepositoryImpl // On utilise maintenant l'implémentation réelle
    ): DictionaryRepository
    // ========================================================
    // FIN DE LA MODIFICATION
    // ========================================================
}