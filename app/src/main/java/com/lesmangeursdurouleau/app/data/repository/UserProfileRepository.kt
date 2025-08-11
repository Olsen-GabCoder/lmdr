package com.lesmangeursdurouleau.app.data.repository

import androidx.paging.PagingData
import com.lesmangeursdurouleau.app.data.model.EnrichedUserListItem
import com.lesmangeursdurouleau.app.data.model.User
import com.lesmangeursdurouleau.app.utils.Resource
import kotlinx.coroutines.flow.Flow

interface UserProfileRepository {
    suspend fun updateUserProfile(userId: String, username: String): Resource<Unit>
    suspend fun updateUserProfilePicture(userId: String, imageData: ByteArray): Resource<String>
    suspend fun updateUserCoverPicture(userId: String, imageData: ByteArray): Resource<String>

    // === DÉBUT DE LA MODIFICATION ===
    // JUSTIFICATION : Le type de retour est maintenant EnrichedUserListItem pour correspondre
    // au nouveau modèle de données plus riche que nous allons afficher dans la liste.
    fun getAllUsersPaginated(): Flow<PagingData<EnrichedUserListItem>>
    fun searchUsersPaginated(query: String): Flow<PagingData<EnrichedUserListItem>>
    // === FIN DE LA MODIFICATION ===

    fun getUserById(userId: String): Flow<Resource<User>>
    suspend fun updateUserTypingStatus(userId: String, isTyping: Boolean): Resource<Unit>
    suspend fun updateUserPresence(userId: String, isOnline: Boolean)
    suspend fun updateUserBio(userId: String, bio: String): Resource<Unit>
    suspend fun updateUserCity(userId: String, city: String): Resource<Unit>
    suspend fun updateUserLastPermissionTimestamp(userId: String, timestamp: Long?): Resource<Unit>
    suspend fun updateUserFCMToken(userId: String, token: String): Resource<Unit>
}