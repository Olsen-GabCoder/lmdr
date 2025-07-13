// PRÊT À COLLER - Fichier UserProfileRepository.kt mis à jour
package com.lesmangeursdurouleau.app.data.repository

import androidx.paging.PagingData
import com.lesmangeursdurouleau.app.data.model.User
import com.lesmangeursdurouleau.app.data.model.UserListItem
import com.lesmangeursdurouleau.app.utils.Resource
import kotlinx.coroutines.flow.Flow

interface UserProfileRepository {
    suspend fun updateUserProfile(userId: String, username: String): Resource<Unit>
    suspend fun updateUserProfilePicture(userId: String, imageData: ByteArray): Resource<String>

    /**
     * JUSTIFICATION DE L'AJOUT : Ajout du contrat pour la mise à jour de la photo de couverture.
     * Cette nouvelle méthode définit l'opération que l'implémentation devra fournir.
     * Sa signature est cohérente avec `updateUserProfilePicture`, garantissant une uniformité
     * architecturale pour les opérations de mise à jour d'images.
     * @param userId L'ID de l'utilisateur qui met à jour sa couverture.
     * @param imageData Les données binaires de la nouvelle image.
     * @return Un `Resource<String>` contenant l'URL de l'image uploadée en cas de succès.
     */
    suspend fun updateUserCoverPicture(userId: String, imageData: ByteArray): Resource<String>

    @Deprecated("Non scalable. Utiliser getAllUsersPaginated() à la place.", ReplaceWith("getAllUsersPaginated()"))
    fun getAllUsers(): Flow<Resource<List<User>>>

    fun getAllUsersPaginated(): Flow<PagingData<UserListItem>>

    fun searchUsersPaginated(query: String): Flow<PagingData<UserListItem>>

    fun getUserById(userId: String): Flow<Resource<User>>
    suspend fun updateUserTypingStatus(userId: String, isTyping: Boolean): Resource<Unit>
    suspend fun updateUserPresence(userId: String, isOnline: Boolean)
    suspend fun updateUserBio(userId: String, bio: String): Resource<Unit>
    suspend fun updateUserCity(userId: String, city: String): Resource<Unit>
    suspend fun updateUserEditPermission(userId: String, canEdit: Boolean): Resource<Unit>
    suspend fun updateUserLastPermissionTimestamp(userId: String, timestamp: Long?): Resource<Unit>
    suspend fun updateUserFCMToken(userId: String, token: String): Resource<Unit>
}