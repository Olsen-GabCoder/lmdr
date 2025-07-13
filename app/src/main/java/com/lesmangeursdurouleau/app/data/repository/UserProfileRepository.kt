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

    fun getAllUsersPaginated(): Flow<PagingData<UserListItem>>

    /**
     * JUSTIFICATION DE L'AJOUT : Cette nouvelle méthode est ajoutée au contrat pour permettre
     * au ViewModel de demander une liste paginée et filtrée d'utilisateurs.
     * @param query Le terme de recherche pour filtrer les utilisateurs.
     * @return Un Flow de PagingData contenant les utilisateurs correspondants à la recherche.
     */
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