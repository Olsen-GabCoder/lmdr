// Fichier Modifié : app/src/main/java/com/lesmangeursdurouleau/app/data/paging/UsersPagingSource.kt

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
 * JUSTIFICATION DE LA MODIFICATION : La classe est mise à jour pour charger et retourner le modèle
 * de données allégé `UserListItem` au lieu de l'objet `User` complet.
 * Le mapper `toObject(UserListItem::class.java)` de Firestore ne va maintenant désérialiser que les
 * champs présents dans `UserListItem`, ce qui constitue la principale optimisation.
 */
class UsersPagingSource(
    private val firestore: FirebaseFirestore
) : PagingSource<QuerySnapshot, UserListItem>() {

    companion object {
        private const val USERS_PAGE_SIZE = 20L
    }

    override fun getRefreshKey(state: PagingState<QuerySnapshot, UserListItem>): QuerySnapshot? {
        return null
    }

    override suspend fun load(params: LoadParams<QuerySnapshot>): LoadResult<QuerySnapshot, UserListItem> {
        return try {
            var query = firestore.collection(FirebaseConstants.COLLECTION_USERS)
                .orderBy("username", Query.Direction.ASCENDING)
                .limit(USERS_PAGE_SIZE)

            val currentPageKey = params.key
            if (currentPageKey != null) {
                val lastVisibleUser = currentPageKey.documents[currentPageKey.size() - 1]
                query = query.startAfter(lastVisibleUser)
            }

            val querySnapshot = query.get().await()

            // La transformation se fait maintenant vers le modèle allégé UserListItem.
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