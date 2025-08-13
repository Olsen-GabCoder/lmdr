// PRÊT À COLLER - Remplacez TOUT le contenu de votre fichier AuthRepository.kt
package com.lesmangeursdurouleau.app.data.repository

import com.google.firebase.auth.AuthCredential
import com.lesmangeursdurouleau.app.data.model.User
import com.lesmangeursdurouleau.app.ui.auth.AuthResultWrapper
import kotlinx.coroutines.flow.Flow

/**
 * Repository centralisant toutes les opérations liées à l'authentification des utilisateurs.
 * C'est la seule source de vérité pour l'état d'authentification et les opérations associées.
 */
interface AuthRepository {

    fun getCurrentUserWithRole(): Flow<User?>
    suspend fun loginUser(email: String, password: String): AuthResultWrapper
    suspend fun signInWithGoogleToken(idToken: String): AuthResultWrapper
    suspend fun registerUser(email: String, password: String, username: String): AuthResultWrapper
    suspend fun sendPasswordResetEmail(email: String): AuthResultWrapper

    // === DÉBUT DE LA MODIFICATION ===
    /**
     * Déconnecte l'utilisateur et nettoie les données de sa session.
     * Met à jour le statut de présence et, de manière critique,
     * supprime le jeton FCM de l'utilisateur pour éviter les notifications croisées.
     *
     * @param userId L'ID de l'utilisateur qui se déconnecte. Requis pour le nettoyage des données.
     */
    suspend fun logoutUser(userId: String?)
    // === FIN DE LA MODIFICATION ===

    suspend fun linkGoogleAccount(pendingCredential: AuthCredential, email: String, password: String): AuthResultWrapper
}