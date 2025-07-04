// PRÊT À COLLER - Remplacez tout le contenu de votre fichier MonthlyReadingRepositoryImpl.kt par ceci.
package com.lesmangeursdurouleau.app.data.repository

import android.util.Log
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.Query
import com.lesmangeursdurouleau.app.data.model.MonthlyReading
import com.lesmangeursdurouleau.app.remote.FirebaseConstants
import com.lesmangeursdurouleau.app.utils.Resource
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.tasks.await
import javax.inject.Inject

class MonthlyReadingRepositoryImpl @Inject constructor(
    private val firestore: FirebaseFirestore
) : MonthlyReadingRepository {

    companion object {
        private const val TAG = "MonthlyReadingRepoImpl"
    }

    private val monthlyReadingsCollection = firestore.collection(FirebaseConstants.COLLECTION_MONTHLY_READINGS)

    override fun getMonthlyReadings(year: Int, month: Int): Flow<Resource<List<MonthlyReading>>> = callbackFlow {
        trySend(Resource.Loading())
        Log.d(TAG, "Fetching readings for $month/$year")

        val query = monthlyReadingsCollection
            .whereEqualTo("year", year)
            .whereEqualTo("month", month)
            .orderBy("analysisPhase.date", Query.Direction.ASCENDING)

        val listenerRegistration = query.addSnapshotListener { snapshot, error ->
            if (error != null) {
                Log.e(TAG, "Error listening for monthly readings: $error")
                trySend(Resource.Error("Erreur Firestore: ${error.localizedMessage ?: "Inconnue"}"))
                close(error)
                return@addSnapshotListener
            }

            if (snapshot != null) {
                val readings = snapshot.documents.mapNotNull { document ->
                    try {
                        // Firestore convertit automatiquement les strings en PhaseStatus enum
                        document.toObject(MonthlyReading::class.java)?.copy(id = document.id)
                    } catch (e: Exception) {
                        Log.e(TAG, "Error parsing MonthlyReading document ${document.id}: $e")
                        null
                    }
                }
                Log.d(TAG, "Fetched ${readings.size} readings for $month/$year")
                trySend(Resource.Success(readings))
            } else {
                trySend(Resource.Success(emptyList()))
            }
        }

        awaitClose {
            Log.d(TAG, "Closing monthly readings listener.")
            listenerRegistration.remove()
        }
    }

    override fun getAllMonthlyReadings(): Flow<Resource<List<MonthlyReading>>> = callbackFlow {
        trySend(Resource.Loading())
        Log.d(TAG, "Fetching all monthly readings (for global filters)")

        val listenerRegistration = monthlyReadingsCollection
            .orderBy("year", Query.Direction.DESCENDING)
            .orderBy("month", Query.Direction.DESCENDING)
            .orderBy("analysisPhase.date", Query.Direction.ASCENDING)
            .addSnapshotListener { snapshot, error ->
                if (error != null) {
                    Log.e(TAG, "Error listening for all monthly readings: $error")
                    trySend(Resource.Error("Erreur Firestore: ${error.localizedMessage ?: "Inconnue"}"))
                    close(error)
                    return@addSnapshotListener
                }

                if (snapshot != null) {
                    val readings = snapshot.documents.mapNotNull { document ->
                        try {
                            document.toObject(MonthlyReading::class.java)?.copy(id = document.id)
                        } catch (e: Exception) {
                            Log.e(TAG, "Error parsing MonthlyReading document ${document.id}: $e")
                            null
                        }
                    }
                    Log.d(TAG, "Fetched ${readings.size} all monthly readings")
                    trySend(Resource.Success(readings))
                } else {
                    trySend(Resource.Success(emptyList()))
                }
            }

        awaitClose {
            Log.d(TAG, "Closing all monthly readings listener.")
            listenerRegistration.remove()
        }
    }

    override fun getMonthlyReadingById(readingId: String): Flow<Resource<MonthlyReading?>> = callbackFlow {
        trySend(Resource.Loading())
        Log.d(TAG, "Fetching single reading by ID: $readingId")

        val documentRef = monthlyReadingsCollection.document(readingId)
        val listenerRegistration = documentRef.addSnapshotListener { documentSnapshot, error ->
            if (error != null) {
                Log.e(TAG, "Error listening for single monthly reading $readingId: $error")
                trySend(Resource.Error("Erreur Firestore: ${error.localizedMessage ?: "Inconnue"}"))
                close(error)
                return@addSnapshotListener
            }

            if (documentSnapshot != null && documentSnapshot.exists()) {
                try {
                    val reading = documentSnapshot.toObject(MonthlyReading::class.java)?.copy(id = documentSnapshot.id)
                    Log.d(TAG, "Fetched reading ID $readingId successfully (null? ${reading == null}).")
                    trySend(Resource.Success(reading))
                } catch (e: Exception) {
                    Log.e(TAG, "Error parsing MonthlyReading document for ID $readingId: $e")
                    trySend(Resource.Error("Erreur de conversion des données de lecture mensuelle."))
                }
            } else {
                Log.w(TAG, "Monthly reading with ID $readingId does not exist.")
                trySend(Resource.Success(null))
            }
        }

        awaitClose {
            Log.d(TAG, "Closing listener for monthly reading ID $readingId.")
            listenerRegistration.remove()
        }
    }

    override suspend fun addMonthlyReading(reading: MonthlyReading): Resource<Unit> {
        return try {
            // L'appel à .add() sérialise l'objet complet, y compris le nouvel enum PhaseStatus.
            val documentRef = monthlyReadingsCollection.add(reading).await()
            Log.d(TAG, "Monthly reading added with ID: ${documentRef.id}")
            Resource.Success(Unit)
        } catch (e: Exception) {
            Log.e(TAG, "Error adding monthly reading: $e")
            Resource.Error("Erreur lors de l'ajout de la lecture mensuelle: ${e.localizedMessage}")
        }
    }

    override suspend fun updateMonthlyReading(reading: MonthlyReading): Resource<Unit> {
        if (reading.id.isBlank()) {
            return Resource.Error("L'ID de la lecture mensuelle est requis pour la mise à jour.")
        }
        return try {
            // MODIFIÉ : Remplacement de la map manuelle par un appel direct à .set().
            // C'est plus propre, plus sûr et plus maintenable.
            // Firestore gère la sérialisation de l'objet, y compris l'enum PhaseStatus.
            // Les champs annotés @ServerTimestamp seront automatiquement mis à jour.
            monthlyReadingsCollection.document(reading.id).set(reading).await()
            Log.d(TAG, "Monthly reading ID ${reading.id} updated successfully.")
            Resource.Success(Unit)
        } catch (e: Exception) {
            Log.e(TAG, "Error updating monthly reading ${reading.id}: $e")
            Resource.Error("Erreur lors de la mise à jour de la lecture mensuelle: ${e.localizedMessage}")
        }
    }
}