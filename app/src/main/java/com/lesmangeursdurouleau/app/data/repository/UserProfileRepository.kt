// Fichier complet et corrigé : UserProfileRepository.kt

package com.lesmangeursdurouleau.app.data.repository

import androidx.paging.PagingData
import com.lesmangeursdurouleau.app.data.model.User
import com.lesmangeursdurouleau.app.utils.Resource
import kotlinx.coroutines.flow.Flow

/**
 * Repository dédié à la gestion du profil et des données de base des utilisateurs.
 */
interface UserProfileRepository {

    /**
     * Met à jour le nom d'utilisateur (pseudo) d'un utilisateur.
     * @param userId L'ID de l'utilisateur à modifier.
     * @param username Le nouveau nom d'utilisateur.
     * @return Une Resource indiquant le succès ou l'échec.
     */
    suspend fun updateUserProfile(userId: String, username: String): Resource<Unit>

    /**
     * Met à jour la photo de profil d'un utilisateur.
     * @param userId L'ID de l'utilisateur à modifier.
     * @param imageData Les données binaires de la nouvelle image.
     * @return Une Resource contenant l'URL de la nouvelle photo de profil.
     */
    suspend fun updateUserProfilePicture(userId: String, imageData: ByteArray): Resource<String>

    /**
     * JUSTIFICATION DE LA MODIFICATION : La méthode est dépréciée dans l'interface pour signaler
     * à tous les futurs développeurs qu'elle n'est plus la méthode recommandée et qu'elle présente
     * des risques de performance. Cela garantit la cohérence avec l'implémentation.
     */
    @Deprecated("Non scalable. Utiliser getAllUsersPaginated() à la place.", ReplaceWith("getAllUsersPaginated()"))
    fun getAllUsers(): Flow<Resource<List<User>>>

    /**
     * JUSTIFICATION DE L'AJOUT : Cette déclaration est la correction directe de l'erreur de compilation.
     * En ajoutant la signature de la méthode à l'interface, nous la rendons visible et utilisable par
     * tous les composants qui dépendent de `UserProfileRepository`, comme notre `MembersViewModel`.
     * @return Un Flow de PagingData contenant les utilisateurs paginés.
     */
    fun getAllUsersPaginated(): Flow<PagingData<User>>

    /**
     * Récupère en temps réel les informations d'un utilisateur spécifique par son ID.
     * @param userId L'ID de l'utilisateur à récupérer.
     * @return Un Flow de Resource contenant l'objet User.
     */
    fun getUserById(userId: String): Flow<Resource<User>>

    /**
     * Met à jour le statut "est en train d'écrire" d'un utilisateur (pour le chat général).
     * @param userId L'ID de l'utilisateur.
     * @param isTyping Le statut de frappe (true/false).
     * @return Une Resource indiquant le succès ou l'échec.
     */
    suspend fun updateUserTypingStatus(userId: String, isTyping: Boolean): Resource<Unit>

    /**
     * Met à jour le statut de présence (en ligne/hors ligne) et l'horodatage de la dernière vue.
     * @param userId L'ID de l'utilisateur concerné.
     * @param isOnline Le nouveau statut de présence.
     */
    suspend fun updateUserPresence(userId: String, isOnline: Boolean)


    /**
     * Met à jour la biographie d'un utilisateur.
     * @param userId L'ID de l'utilisateur.
     * @param bio La nouvelle biographie.
     * @return Une Resource indiquant le succès ou l'échec.
     */
    suspend fun updateUserBio(userId: String, bio: String): Resource<Unit>

    /**
     * Met à jour la ville d'un utilisateur.
     * @param userId L'ID de l'utilisateur.
     * @param city La nouvelle ville.
     * @return Une Resource indiquant le succès ou l'échec.
     */
    suspend fun updateUserCity(userId: String, city: String): Resource<Unit>

    /**
     * Met à jour la permission d'édition des lectures pour un utilisateur.
     * @param userId L'ID de l'utilisateur.
     * @param canEdit La nouvelle valeur de la permission.
     * @return Une Resource indiquant le succès ou l'échec.
     */
    suspend fun updateUserEditPermission(userId: String, canEdit: Boolean): Resource<Unit>

    /**
     * Met à jour le timestamp de la dernière fois que la permission a été accordée.
     * @param userId L'ID de l'utilisateur.
     * @param timestamp Le timestamp en millisecondes, ou null pour le supprimer.
     * @return Une Resource indiquant le succès ou l'échec.
     */
    suspend fun updateUserLastPermissionTimestamp(userId: String, timestamp: Long?): Resource<Unit>

    /**
     * Met à jour le jeton FCM (Firebase Cloud Messaging) pour les notifications push.
     * @param userId L'ID de l'utilisateur.
     * @param token Le nouveau jeton FCM.
     * @return Une Resource indiquant le succès ou l'échec.
     */
    suspend fun updateUserFCMToken(userId: String, token: String): Resource<Unit>
}