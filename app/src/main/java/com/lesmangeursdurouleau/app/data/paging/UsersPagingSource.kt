package com.lesmangeursdurouleau.app.data.paging

import androidx.paging.PagingSource
import androidx.paging.PagingState
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.Query
import com.google.firebase.firestore.QuerySnapshot
import com.lesmangeursdurouleau.app.data.model.EnrichedUserListItem
import com.lesmangeursdurouleau.app.remote.FirebaseConstants
import kotlinx.coroutines.tasks.await

class UsersPagingSource(
    private val firestore: FirebaseFirestore
) : PagingSource<QuerySnapshot, EnrichedUserListItem>() {

    companion object {
        private const val USERS_PAGE_SIZE = 20L
    }

    override fun getRefreshKey(state: PagingState<QuerySnapshot, EnrichedUserListItem>): QuerySnapshot? {
        return null
    }

    override suspend fun load(params: LoadParams<QuerySnapshot>): LoadResult<QuerySnapshot, EnrichedUserListItem> {
        return try {
            var query: Query = firestore.collection(FirebaseConstants.COLLECTION_USERS)
                .orderBy("username", Query.Direction.ASCENDING)
                .limit(USERS_PAGE_SIZE)

            params.key?.let {
                val lastVisibleUser = it.documents.lastOrNull()
                if (lastVisibleUser != null) {
                    query = query.startAfter(lastVisibleUser)
                }
            }

            val querySnapshot = query.get().await()

            // === DÉBUT DE LA MODIFICATION ===
            // JUSTIFICATION : Conversion manuelle vers le modèle enrichi.
            // Nous récupérons chaque champ nécessaire pour construire notre nouvel objet.
            // L'utilisation de `.getLong()` et `.getBoolean()` est plus sûre que `toObject()`.
            val users = querySnapshot.documents.map { document ->
                EnrichedUserListItem(
                    uid = document.id,
                    username = document.getString("username") ?: "",
                    profilePictureUrl = document.getString("profilePictureUrl"),
                    city = document.getString("city"),
                    booksReadCount = document.getLong("booksReadCount")?.toInt() ?: 0,
                    isOnline = document.getBoolean("isOnline") ?: false
                    // isFollowedByCurrentUser est laissé à sa valeur par défaut (false).
                )
            }
            // === FIN DE LA MODIFICATION ===

            val nextKey = if (users.isNotEmpty() && users.size >= USERS_PAGE_SIZE) querySnapshot else null

            LoadResult.Page(
                data = users,
                prevKey = null,
                nextKey = nextKey
            )
        } catch (e: Exception) {
            LoadResult.Error(e)
        }
    }
}