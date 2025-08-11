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
     * MODIFIÉ : Déconnecte l'utilisateur actuellement authentifié et met à jour son statut de présence.
     *
     * @param userId L'ID de l'utilisateur qui se déconnecte, nécessaire pour mettre à jour son statut.
     *               Peut être null si l'ID n'est pas disponible, auquel cas seule la déconnexion locale aura lieu.
     */
    suspend fun logoutUser(userId: String?)
    // === FIN DE LA MODIFICATION ===

    suspend fun linkGoogleAccount(pendingCredential: AuthCredential, email: String, password: String): AuthResultWrapper
}