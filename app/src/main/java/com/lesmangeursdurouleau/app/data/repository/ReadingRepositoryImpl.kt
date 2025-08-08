// PRÊT À COLLER - Remplacez tout le contenu de votre fichier ReadingRepositoryImpl.kt
package com.lesmangeursdurouleau.app.data.repository

import android.util.Log
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.Query
import com.lesmangeursdurouleau.app.data.model.CompletedReading
import com.lesmangeursdurouleau.app.data.model.UserBookReading
import com.lesmangeursdurouleau.app.utils.Resource
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import javax.inject.Inject

@Deprecated(
    message = "Ce repository est obsolète. La logique de gestion de la bibliothèque personnelle a été unifiée dans BookRepository.",
    replaceWith = ReplaceWith("BookRepositoryImpl", "com.lesmangeursdurouleau.app.data.repository.BookRepositoryImpl")
)
class ReadingRepositoryImpl @Inject constructor(
    private val firestore: FirebaseFirestore
) : ReadingRepository {

    companion object {
        private const val TAG = "ReadingRepositoryImpl"
    }

    override fun getCurrentReading(userId: String): Flow<Resource<UserBookReading?>> = callbackFlow {
        Log.w(TAG, "APPEL DÉPRÉCIÉ: getCurrentReading. Cette fonction ne doit plus être utilisée.")
        trySend(Resource.Success(null))
        awaitClose()
    }

    override suspend fun updateCurrentReading(userId: String, userBookReading: UserBookReading?): Resource<Unit> {
        Log.w(TAG, "APPEL DÉPRÉCIÉ: updateCurrentReading. Aucune action effectuée.")
        return Resource.Error("Fonctionnalité obsolète. Utilisez BookRepository.updateLibraryEntry.")
    }

    override suspend fun markActiveReadingAsCompleted(userId: String, activeReadingDetails: UserBookReading): Resource<Unit> {
        Log.w(TAG, "APPEL DÉPRÉCIÉ: markActiveReadingAsCompleted. Aucune action effectuée.")
        return Resource.Error("Fonctionnalité obsolète. Utilisez BookRepository.updateLibraryEntry.")
    }

    override suspend fun removeCompletedReading(userId: String, bookId: String): Resource<Unit> {
        Log.w(TAG, "APPEL DÉPRÉCIÉ: removeCompletedReading. Aucune action effectuée.")
        return Resource.Error("Fonctionnalité obsolète. Utilisez BookRepository.removeBookFromUserLibrary.")
    }

    override fun getCompletedReadings(userId: String, orderBy: String, direction: Query.Direction): Flow<Resource<List<CompletedReading>>> = callbackFlow {
        Log.w(TAG, "APPEL DÉPRÉCIÉ: getCompletedReadings. Cette fonction ne doit plus être utilisée.")
        trySend(Resource.Success(emptyList()))
        awaitClose()
    }

    override fun getCompletedReadingDetail(userId: String, bookId: String): Flow<Resource<CompletedReading?>> = callbackFlow {
        Log.w(TAG, "APPEL DÉPRÉCIÉ: getCompletedReadingDetail. Cette fonction ne doit plus être utilisée.")
        trySend(Resource.Success(null))
        awaitClose()
    }
}