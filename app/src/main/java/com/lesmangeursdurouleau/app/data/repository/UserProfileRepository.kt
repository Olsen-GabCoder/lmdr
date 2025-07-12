// Fichier Modifié : UserProfileRepository.kt

package com.lesmangeursdurouleau.app.data.repository

import androidx.paging.PagingData
import com.lesmangeursdurouleau.app.data.model.User
import com.lesmangeursdurouleau.app.data.model.UserListItem
import com.lesmangeursdurouleau.app.utils.Resource
import kotlinx.coroutines.flow.Flow

interface UserProfileRepository {
    suspend fun updateUserProfile(userId: String, username: String): Resource<Unit>
    suspend fun updateUserProfilePicture(userId: String, imageData: ByteArray): Resource<String>

    @Deprecated("Non scalable. Utiliser getAllUsersPaginated() à la place.", ReplaceWith("getAllUsersPaginated()"))
    fun getAllUsers(): Flow<Resource<List<User>>>

    /**
     * JUSTIFICATION DE LA MODIFICATION : La signature de la méthode est mise à jour dans l'interface
     * pour refléter le fait qu'elle retourne maintenant un flux de données paginées du type allégé `UserListItem`.
     * Cela assure la cohérence du contrat à travers l'application.
     */
    fun getAllUsersPaginated(): Flow<PagingData<UserListItem>>

    fun getUserById(userId: String): Flow<Resource<User>>
    suspend fun updateUserTypingStatus(userId: String, isTyping: Boolean): Resource<Unit>
    suspend fun updateUserPresence(userId: String, isOnline: Boolean)
    suspend fun updateUserBio(userId: String, bio: String): Resource<Unit>
    suspend fun updateUserCity(userId: String, city: String): Resource<Unit>
    suspend fun updateUserEditPermission(userId: String, canEdit: Boolean): Resource<Unit>
    suspend fun updateUserLastPermissionTimestamp(userId: String, timestamp: Long?): Resource<Unit>
    suspend fun updateUserFCMToken(userId: String, token: String): Resource<Unit>
}