// PRÊT À COLLER - Remplacez tout le contenu de votre fichier RepositoryBindingsModule.kt
package com.lesmangeursdurouleau.app.di

import com.lesmangeursdurouleau.app.data.repository.*
import dagger.Binds
import dagger.Module
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
abstract class RepositoryBindingsModule {

    // === DÉBUT DE LA MODIFICATION ===
    /**
     * NOUVEAU: Ajout de la liaison pour le repository de livres hors-ligne.
     * Hilt saura désormais injecter une instance de OfflineBookRepositoryImpl
     * partout où l'interface OfflineBookRepository est requise.
     */
    @Binds
    @Singleton
    abstract fun bindOfflineBookRepository(impl: OfflineBookRepositoryImpl): OfflineBookRepository
    // === FIN DE LA MODIFICATION ===

    @Binds
    @Singleton
    abstract fun bindAuthRepository(impl: AuthRepositoryImpl): AuthRepository

    @Binds
    @Singleton
    abstract fun bindAdminRepository(impl: AdminRepositoryImpl): AdminRepository

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

    @Binds
    @Singleton
    abstract fun bindNotificationRepository(impl: NotificationRepositoryImpl): NotificationRepository
}