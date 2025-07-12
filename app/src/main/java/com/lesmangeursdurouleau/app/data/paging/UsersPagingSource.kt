// NOUVEAU FICHIER : app/src/main/java/com/lesmangeursdurouleau/app/data/paging/UsersPagingSource.kt

package com.lesmangeursdurouleau.app.data.paging

import androidx.paging.PagingSource
import androidx.paging.PagingState
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.Query
import com.google.firebase.firestore.QuerySnapshot
import com.lesmangeursdurouleau.app.data.model.User
import com.lesmangeursdurouleau.app.remote.FirebaseConstants
import kotlinx.coroutines.tasks.await

/**
 * JUSTIFICATION DE LA CRÉATION : Cette classe est le cœur de la solution à la faille de performance critique.
 * Elle implémente la logique de chargement de données par "pages" depuis Firestore pour la collection des utilisateurs.
 * En ne chargeant que des petits lots de données à la fois (ex: 20 utilisateurs), elle garantit que l'application
 * reste performante, réactive et que les coûts Firestore restent maîtrisés, même avec des dizaines de milliers d'utilisateurs.
 * Elle remplace l'ancienne approche qui consistait à charger toute la collection en une seule fois.
 */
class UsersPagingSource(
    private val firestore: FirebaseFirestore
) : PagingSource<QuerySnapshot, User>() {

    companion object {
        private const val USERS_PAGE_SIZE = 20L
    }

    override fun getRefreshKey(state: PagingState<QuerySnapshot, User>): QuerySnapshot? {
        // La logique de getRefreshKey est complexe. Pour Firestore, la stratégie la plus simple
        // est de toujours recommencer depuis le début en cas de rafraîchissement.
        // Retourner null accomplit cela.
        return null
    }

    override suspend fun load(params: LoadParams<QuerySnapshot>): LoadResult<QuerySnapshot, User> {
        return try {
            // Construit la requête de base
            var query = firestore.collection(FirebaseConstants.COLLECTION_USERS)
                .orderBy("username", Query.Direction.ASCENDING)
                .limit(USERS_PAGE_SIZE)

            // Si `params.key` n'est pas null, cela signifie que nous chargeons une page autre que la première.
            // Nous utilisons la clé (le snapshot de la dernière page) pour commencer la requête après le dernier item.
            val currentPageKey = params.key
            if (currentPageKey != null) {
                // On récupère le dernier document de la page précédente pour savoir où continuer.
                val lastVisibleUser = currentPageKey.documents[currentPageKey.size() - 1]
                query = query.startAfter(lastVisibleUser)
            }

            // Exécute la requête
            val querySnapshot = query.get().await()

            // Transforme les documents en objets User
            val users = querySnapshot.documents.mapNotNull { document ->
                document.toObject(User::class.java)?.copy(uid = document.id)
            }

            // Détermine la clé pour la page suivante. Si la liste est vide, il n'y a plus de pages.
            val nextKey = if (users.isNotEmpty()) querySnapshot else null

            // Retourne le résultat
            LoadResult.Page(
                data = users,
                prevKey = null, // La pagination se fait uniquement vers l'avant
                nextKey = nextKey
            )
        } catch (e: Exception) {
            // En cas d'erreur (ex: problème réseau, permissions), on retourne un état d'erreur.
            LoadResult.Error(e)
        }
    }
}