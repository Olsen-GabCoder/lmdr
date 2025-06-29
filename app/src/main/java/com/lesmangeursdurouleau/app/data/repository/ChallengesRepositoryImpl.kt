// PRÊT À COLLER - Fichier complet
package com.lesmangeursdurouleau.app.data.repository

import com.google.firebase.firestore.FirebaseFirestore
import com.lesmangeursdurouleau.app.data.model.Challenge
import com.lesmangeursdurouleau.app.remote.FirebaseConstants
import com.lesmangeursdurouleau.app.utils.Resource
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import javax.inject.Inject

class ChallengesRepositoryImpl @Inject constructor(
    private val firestore: FirebaseFirestore
) : ChallengesRepository {

    // J'assume que vous avez une constante pour cette nouvelle collection.
    // Sinon, remplacez par "challenges"
    private val challengesCollection = firestore.collection(FirebaseConstants.COLLECTION_CHALLENGES)

    override fun getWeeklyChallenges(): Flow<Resource<List<Challenge>>> = callbackFlow {
        trySend(Resource.Loading())

        val docRef = challengesCollection.document("weekly")

        val listener = docRef.addSnapshotListener { snapshot, error ->
            if (error != null) {
                trySend(Resource.Error("Erreur de lecture des défis: ${error.localizedMessage}"))
                close(error)
                return@addSnapshotListener
            }

            if (snapshot != null && snapshot.exists()) {
                // Firestore ne peut pas désérialiser directement un champ de type Array en List<Challenge>.
                // Nous devons le faire manuellement.
                val challengesData = snapshot.get("challenges") as? List<Map<String, Any>> ?: emptyList()
                val challenges = challengesData.mapNotNull { data ->
                    try {
                        Challenge(
                            id = data["id"] as String,
                            title = data["title"] as String,
                            bonusPoints = (data["bonusPoints"] as Long).toInt() // Firestore lit les nombres comme Long
                        )
                    } catch (e: Exception) {
                        null
                    }
                }
                trySend(Resource.Success(challenges))
            } else {
                // Le document n'existe pas encore (la fonction ne s'est pas encore exécutée)
                trySend(Resource.Success(emptyList()))
            }
        }
        awaitClose { listener.remove() }
    }
}