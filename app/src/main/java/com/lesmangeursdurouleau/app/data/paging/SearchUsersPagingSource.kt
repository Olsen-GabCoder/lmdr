package com.lesmangeursdurouleau.app.data.paging

import androidx.paging.PagingSource
import androidx.paging.PagingState
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.Query
import com.google.firebase.firestore.QuerySnapshot
import com.lesmangeursdurouleau.app.data.model.EnrichedUserListItem
import com.lesmangeursdurouleau.app.remote.FirebaseConstants
import kotlinx.coroutines.tasks.await

class SearchUsersPagingSource(
    private val firestore: FirebaseFirestore,
    private val query: String
) : PagingSource<QuerySnapshot, EnrichedUserListItem>() {

    companion object {
        private const val USERS_PAGE_SIZE = 20L
    }

    override fun getRefreshKey(state: PagingState<QuerySnapshot, EnrichedUserListItem>): QuerySnapshot? {
        return null
    }

    override suspend fun load(params: LoadParams<QuerySnapshot>): LoadResult<QuerySnapshot, EnrichedUserListItem> {
        return try {
            var firestoreQuery: Query = firestore.collection(FirebaseConstants.COLLECTION_USERS)
                .orderBy("username")
                .startAt(query)
                .endAt(query + '\uf8ff')
                .limit(USERS_PAGE_SIZE)

            params.key?.let {
                val lastVisibleUser = it.documents.lastOrNull()
                if (lastVisibleUser != null) {
                    firestoreQuery = firestoreQuery.startAfter(lastVisibleUser)
                }
            }

            val querySnapshot = firestoreQuery.get().await()

            // === DÉBUT DE LA MODIFICATION ===
            // JUSTIFICATION : Conversion manuelle vers le modèle enrichi, comme pour UsersPagingSource.
            val users = querySnapshot.documents.map { document ->
                EnrichedUserListItem(
                    uid = document.id,
                    username = document.getString("username") ?: "",
                    profilePictureUrl = document.getString("profilePictureUrl"),
                    city = document.getString("city"),
                    booksReadCount = document.getLong("booksReadCount")?.toInt() ?: 0,
                    isOnline = document.getBoolean("isOnline") ?: false
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