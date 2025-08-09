// PRÊT À COLLER - Remplacez TOUT le contenu de votre fichier AdminRepositoryImpl.kt
package com.lesmangeursdurouleau.app.data.repository

import android.util.Base64
import android.util.Log
import com.google.firebase.functions.FirebaseFunctions
import com.lesmangeursdurouleau.app.utils.Resource
import kotlinx.coroutines.tasks.await
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class AdminRepositoryImpl @Inject constructor(
    private val functions: FirebaseFunctions
) : AdminRepository {

    companion object {
        private const val TAG = "AdminRepositoryImpl"
    }

    override suspend fun setUserAsAdmin(email: String): Resource<String> {
        val data = hashMapOf(
            "email" to email,
            "isAdmin" to true
        )
        return try {
            val result = functions
                .getHttpsCallable("setUserRole")
                .call(data)
                .await()

            val resultData = result.data as? Map<*, *>
            val message = resultData?.get("message") as? String ?: "Opération terminée."

            Resource.Success(message)
        } catch (e: Exception) {
            Log.e(TAG, "setUserAsAdmin failed", e)
            Resource.Error(e.message ?: "Une erreur inconnue est survenue.")
        }
    }

    // === DÉBUT DE LA MODIFICATION ===
    /**
     * JUSTIFICATION: C'est l'implémentation de l'appel à notre nouvelle Cloud Function sécurisée.
     * 1.  Elle prend les données brutes (y compris les ByteArray pour les fichiers).
     * 2.  Elle convertit les fichiers en chaînes Base64, le format standard pour sérialiser
     *     des données binaires et les envoyer via JSON à une fonction backend.
     * 3.  Elle construit un payload (data) qui correspond exactement à ce que la Cloud Function attend.
     * 4.  Elle appelle la fonction `createBookWithFiles`.
     * 5.  En cas de succès, elle parse la réponse pour extraire le `bookId` retourné par le serveur.
     * 6.  La gestion des erreurs est robuste, propageant le message d'erreur exact renvoyé par la fonction.
     */
    override suspend fun createBookWithFiles(
        title: String,
        author: String,
        synopsis: String,
        totalPages: Int,
        coverImage: ByteArray?,
        pdfFile: ByteArray?
    ): Resource<String> {
        Log.d(TAG, "Calling createBookWithFiles Cloud Function for title: $title")
        val data = hashMapOf(
            "title" to title,
            "author" to author,
            "synopsis" to synopsis,
            "totalPages" to totalPages,
            "coverImageBase64" to coverImage?.let { Base64.encodeToString(it, Base64.NO_WRAP) },
            "pdfFileBase64" to pdfFile?.let { Base64.encodeToString(it, Base64.NO_WRAP) }
        )

        return try {
            val result = functions
                .getHttpsCallable("createBookWithFiles")
                .call(data)
                .await()

            val resultData = result.data as? Map<*, *>
            val bookId = resultData?.get("bookId") as? String

            if (bookId != null) {
                Log.i(TAG, "Cloud Function returned success with bookId: $bookId")
                Resource.Success(bookId)
            } else {
                val errorMessage = (resultData?.get("error") as? String) ?: "La fonction a réussi mais n'a pas renvoyé d'ID de livre."
                Log.e(TAG, "createBookWithFiles error: $errorMessage")
                Resource.Error(errorMessage)
            }
        } catch (e: Exception) {
            // L'exception contient déjà un message d'erreur clair de la Cloud Function
            Log.e(TAG, "createBookWithFiles function call failed", e)
            Resource.Error(e.message ?: "Une erreur inconnue est survenue lors de l'appel serveur.")
        }
    }
}