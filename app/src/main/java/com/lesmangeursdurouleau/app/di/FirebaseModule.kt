package com.lesmangeursdurouleau.app.di

import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.FirebaseFirestoreSettings
import com.google.firebase.firestore.ktx.firestore
import com.google.firebase.firestore.ktx.firestoreSettings
import com.google.firebase.functions.FirebaseFunctions
import com.google.firebase.ktx.Firebase
import com.google.firebase.storage.FirebaseStorage
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
object FirebaseModule {

    @Provides
    @Singleton
    fun provideFirebaseAuth(): FirebaseAuth {
        return FirebaseAuth.getInstance()
    }

    // === DÉBUT DE LA MODIFICATION ===
    @Provides
    @Singleton
    fun provideFirebaseFirestore(): FirebaseFirestore {
        // Obtenir l'instance de Firestore
        val firestore = Firebase.firestore

        // Configurer les paramètres de persistance
        // Cela permet à l'application de lire les données déjà chargées
        // même en l'absence de connexion réseau.
        val settings = firestoreSettings {
            isPersistenceEnabled = true
        }
        firestore.firestoreSettings = settings

        return firestore
    }
    // === FIN DE LA MODIFICATION ===

    @Provides
    @Singleton
    fun provideFirebaseStorage(): FirebaseStorage {
        return FirebaseStorage.getInstance()
    }

    @Provides
    @Singleton
    fun provideFirebaseFunctions(): FirebaseFunctions {
        // Important : Spécifiez la région pour correspondre à la configuration
        // de vos Cloud Functions.
        return FirebaseFunctions.getInstance("europe-west1")
    }
}