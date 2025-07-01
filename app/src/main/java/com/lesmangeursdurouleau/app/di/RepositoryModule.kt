// PRÊT À COLLER - Fichier 100% complet et nettoyé
package com.lesmangeursdurouleau.app.di

import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.functions.FirebaseFunctions
import com.lesmangeursdurouleau.app.data.remote.FirebaseStorageService
import com.lesmangeursdurouleau.app.data.repository.*
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
object RepositoryModule {

    // SUPPRIMÉ : L'ancien bloc commenté pour UserRepository a été définitivement enlevé.

    // NOTE : Ce module est l'endroit idéal pour tous les repositories qui nécessitent
    // des dépendances (comme firestore, firebaseAuth, etc.) et ne peuvent donc pas
    // être simplement "liés" avec @Binds.

    @Provides
    @Singleton
    fun provideChatRepository(
        firestore: FirebaseFirestore,
        firebaseAuth: FirebaseAuth
    ): ChatRepository {
        return ChatRepositoryImpl(firestore, firebaseAuth)
    }

    @Provides
    @Singleton
    fun provideMonthlyReadingRepository(
        firestore: FirebaseFirestore
    ): MonthlyReadingRepository {
        return MonthlyReadingRepositoryImpl(firestore)
    }

    @Provides
    @Singleton
    fun provideAppConfigRepository(
        firestore: FirebaseFirestore
    ): AppConfigRepository {
        return AppConfigRepositoryImpl(firestore)
    }

    // AJOUT : Bien que PrivateChatRepository soit dans le module Binds,
    // son implémentation a des dépendances. Il est plus cohérent de le mettre ici.
    // Pour l'instant, je respecte votre structure. Mais si des erreurs surviennent,
    // il faudra le déplacer de RepositoryBindingsModule à ici.
    // Idem pour SocialRepositoryImpl.
}