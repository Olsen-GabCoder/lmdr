// PRÊT À COLLER - Fichier complet
package com.lesmangeursdurouleau.app.data.repository

import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.Query
import com.lesmangeursdurouleau.app.data.model.Conversation
import com.lesmangeursdurouleau.app.remote.FirebaseConstants
import com.lesmangeursdurouleau.app.utils.Resource
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import javax.inject.Inject

class LeaderboardRepositoryImpl @Inject constructor(
    private val firestore: FirebaseFirestore
) : LeaderboardRepository {

    // J'assume que vous avez une constante pour la collection des conversations.
    // Si ce n'est pas le cas, remplacez FirebaseConstants.COLLECTION_CONVERSATIONS par "conversations"
    private val conversationsCollection = firestore.collection(FirebaseConstants.COLLECTION_CONVERSATIONS)

    override fun getAffinityLeaderboard(limit: Long): Flow<Resource<List<Conversation>>> = callbackFlow {
        trySend(Resource.Loading())

        // La requête Firestore qui trie par score et limite le nombre de résultats
        val query = conversationsCollection
            .orderBy("affinityScore", Query.Direction.DESCENDING)
            .limit(limit)

        val listener = query.addSnapshotListener { snapshot, error ->
            if (error != null) {
                trySend(Resource.Error("Erreur de lecture du classement: ${error.localizedMessage}"))
                close(error)
                return@addSnapshotListener
            }

            if (snapshot != null) {
                // Firestore désérialise automatiquement les documents en objets Conversation
                val leaderboardEntries = snapshot.toObjects(Conversation::class.java)
                trySend(Resource.Success(leaderboardEntries))
            }
        }
        // Ferme l'écouteur lorsque le Flow est annulé
        awaitClose { listener.remove() }
    }
}