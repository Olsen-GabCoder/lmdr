// PRÊT À COLLER - Créez un nouveau fichier AuthRepository.kt dans le package data/repository
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

    /**
     * Observe en temps réel l'état de l'utilisateur actuellement connecté, enrichi de son rôle.
     * Le rôle est récupéré à partir des custom claims du jeton d'authentification.
     *
     * @return Un Flow qui émet l'objet User complet (avec son rôle) si un utilisateur est connecté,
     *         ou null dans le cas contraire.
     */
    fun getCurrentUserWithRole(): Flow<User?>

    /**
     * Tente de connecter un utilisateur avec son email et son mot de passe.
     *
     * @param email L'adresse e-mail de l'utilisateur.
     * @param password Le mot de passe de l'utilisateur.
     * @return Un AuthResultWrapper indiquant le succès ou l'échec.
     */
    suspend fun loginUser(email: String, password: String): AuthResultWrapper

    /**
     * Tente de connecter ou d'inscrire un utilisateur via un jeton d'identification Google.
     *
     * @param idToken Le jeton fourni par Google Sign-In.
     * @return Un AuthResultWrapper indiquant le succès, l'échec ou une collision de compte.
     */
    suspend fun signInWithGoogleToken(idToken: String): AuthResultWrapper

    /**
     * Crée un nouveau compte utilisateur avec email, mot de passe et pseudo.
     *
     * @param email L'adresse e-mail.
     * @param password Le mot de passe.
     * @param username Le pseudo choisi.
     * @return Un AuthResultWrapper indiquant le succès ou l'échec.
     */
    suspend fun registerUser(email: String, password: String, username: String): AuthResultWrapper

    /**
     * Envoie un e-mail de réinitialisation de mot de passe à l'adresse fournie.
     *
     * @param email L'adresse e-mail de destination.
     * @return Un AuthResultWrapper indiquant le succès ou l'échec de l'envoi.
     */
    suspend fun sendPasswordResetEmail(email: String): AuthResultWrapper

    /**
     * Déconnecte l'utilisateur actuellement authentifié.
     */
    fun logoutUser()

    /**
     * Lie un compte Google à un compte email/mot de passe existant.
     *
     * @param pendingCredential Les informations d'identification Google en attente.
     * @param email L'e-mail du compte existant.
     * @param password Le mot de passe du compte existant pour la ré-authentification.
     * @return Un AuthResultWrapper indiquant le succès ou l'échec de la liaison.
     */
    suspend fun linkGoogleAccount(pendingCredential: AuthCredential, email: String, password: String): AuthResultWrapper
}