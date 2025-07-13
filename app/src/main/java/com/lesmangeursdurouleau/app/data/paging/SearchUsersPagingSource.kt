// NOUVEAU FICHIER : app/src/main/java/com/lesmangeursdurouleau/app/data/paging/SearchUsersPagingSource.kt

package com.lesmangeursdurouleau.app.data.paging

import androidx.paging.PagingSource
import androidx.paging.PagingState
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.Query
import com.google.firebase.firestore.QuerySnapshot
import com.lesmangeursdurouleau.app.data.model.UserListItem
import com.lesmangeursdurouleau.app.remote.FirebaseConstants
import kotlinx.coroutines.tasks.await

/**
 * JUSTIFICATION DE LA CRÉATION : Cette PagingSource est dédiée à la recherche d'utilisateurs.
 * Elle implémente une requête "prefix search" efficace sur Firestore (`startAt` et `endAt`)
 * pour retourner uniquement les utilisateurs dont le nom correspond à la requête de recherche,
 * tout en restant paginée pour garantir la performance.
 */
class SearchUsersPagingSource(
    private val firestore: FirebaseFirestore,
    private val query: String
) : PagingSource<QuerySnapshot, UserListItem>() {

    companion object {
        private const val USERS_PAGE_SIZE = 20L
    }

    override fun getRefreshKey(state: PagingState<QuerySnapshot, UserListItem>): QuerySnapshot? {
        return null
    }

    override suspend fun load(params: LoadParams<QuerySnapshot>): LoadResult<QuerySnapshot, UserListItem> {
        return try {
            // Requête de base pour la recherche. Utilise `startAt` et `endAt` avec un caractère
            // de fin de plage unicode (`\uf8ff`) pour simuler une recherche "commence par".
            var firestoreQuery: Query = firestore.collection(FirebaseConstants.COLLECTION_USERS)
                .orderBy("username")
                .startAt(query)
                .endAt(query + '\uf8ff')
                .limit(USERS_PAGE_SIZE)

            val currentPageKey = params.key
            if (currentPageKey != null) {
                val lastVisibleUser = currentPageKey.documents[currentPageKey.size() - 1]
                firestoreQuery = firestoreQuery.startAfter(lastVisibleUser)
            }

            val querySnapshot = firestoreQuery.get().await()
            val users = querySnapshot.documents.mapNotNull { document ->
                document.toObject(UserListItem::class.java)?.copy(uid = document.id)
            }

            val nextKey = if (users.isNotEmpty()) querySnapshot else null

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