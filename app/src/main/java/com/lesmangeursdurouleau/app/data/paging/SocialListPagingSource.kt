package com.lesmangeursdurouleau.app.data.paging

import android.util.Log
import androidx.paging.PagingSource
import androidx.paging.PagingState
import com.google.firebase.firestore.FieldPath
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.Query
import com.google.firebase.firestore.QuerySnapshot
import com.lesmangeursdurouleau.app.data.model.EnrichedUserListItem
import com.lesmangeursdurouleau.app.remote.FirebaseConstants
import kotlinx.coroutines.tasks.await

class SocialListPagingSource(
    private val firestore: FirebaseFirestore,
    private val userId: String,
    private val subCollectionName: String // "followers" ou "following"
) : PagingSource<QuerySnapshot, EnrichedUserListItem>() {

    companion object {
        private const val PAGE_SIZE = 20L
        private const val TAG = "SocialListPagingSource"
    }

    override suspend fun load(params: LoadParams<QuerySnapshot>): LoadResult<QuerySnapshot, EnrichedUserListItem> {
        return try {
            var idQuery: Query = firestore.collection(FirebaseConstants.COLLECTION_USERS)
                .document(userId)
                .collection(subCollectionName)
                .orderBy("timestamp", Query.Direction.DESCENDING)
                .limit(PAGE_SIZE)

            params.key?.let {
                val lastVisibleDoc = it.documents.lastOrNull()
                if (lastVisibleDoc != null) {
                    idQuery = idQuery.startAfter(lastVisibleDoc)
                }
            }

            val idQuerySnapshot = idQuery.get().await()
            val orderedUserIds = idQuerySnapshot.documents.map { it.id }

            if (orderedUserIds.isEmpty()) {
                return LoadResult.Page(data = emptyList(), prevKey = null, nextKey = null)
            }

            val usersSnapshot = firestore.collection(FirebaseConstants.COLLECTION_USERS)
                .whereIn(FieldPath.documentId(), orderedUserIds)
                .get()
                .await()

            val usersMap = usersSnapshot.documents.associateBy { it.id }

            val orderedUserListItems = orderedUserIds.mapNotNull { id ->
                usersMap[id]?.let { document ->
                    EnrichedUserListItem(
                        uid = document.id,
                        username = document.getString("username") ?: "",
                        profilePictureUrl = document.getString("profilePictureUrl"),
                        city = document.getString("city"),
                        booksReadCount = document.getLong("booksReadCount")?.toInt() ?: 0,
                        isOnline = document.getBoolean("isOnline") ?: false
                    )
                }
            }

            val nextKey = if (idQuerySnapshot.size() >= PAGE_SIZE) idQuerySnapshot else null

            LoadResult.Page(
                data = orderedUserListItems,
                prevKey = null,
                nextKey = nextKey
            )

        } catch (e: Exception) {
            Log.e(TAG, "Erreur lors du chargement de la page Paging", e)
            LoadResult.Error(e)
        }
    }

    // === DÉBUT DE LA CORRECTION ===
    // JUSTIFICATION : La signature de la fonction était incorrecte.
    // La méthode `getRefreshKey` doit retourner un type `PagingState.RefreshKey?` nullable.
    // L'implémentation la plus simple et la plus sûre est de retourner null,
    // ce qui indique à la librairie Paging de simplement recharger depuis le début en cas de refresh.
    override fun getRefreshKey(state: PagingState<QuerySnapshot, EnrichedUserListItem>): QuerySnapshot? {
        return null
    }
    // === FIN DE LA CORRECTION ===
}