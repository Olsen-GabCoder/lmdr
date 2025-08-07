// PRÊT À COLLER - Remplacez TOUT le contenu de votre fichier FirebaseStorageService.kt
package com.lesmangeursdurouleau.app.data.remote

import android.net.Uri
import android.util.Log
import com.google.firebase.storage.FirebaseStorage
import com.google.firebase.storage.StorageException
import com.lesmangeursdurouleau.app.remote.FirebaseConstants
import com.lesmangeursdurouleau.app.utils.Resource
import kotlinx.coroutines.tasks.await
import java.util.UUID
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class FirebaseStorageService @Inject constructor(
    private val storage: FirebaseStorage
) {
    companion object {
        private const val TAG = "FirebaseStorageService"
    }

    suspend fun uploadProfilePicture(userId: String, imageData: ByteArray): Resource<String> {
        return uploadData(FirebaseConstants.STORAGE_PROFILE_PICTURES, "$userId/${UUID.randomUUID()}.jpg", imageData)
    }

    suspend fun uploadCoverPicture(userId: String, imageData: ByteArray): Resource<String> {
        return uploadData(FirebaseConstants.STORAGE_COVER_PICTURES, "$userId/${UUID.randomUUID()}.jpg", imageData)
    }

    suspend fun uploadChatMessageImage(conversationId: String, fileName: String, imageUri: Uri): Resource<String> {
        return uploadUri(FirebaseConstants.STORAGE_CHAT_IMAGES, "$conversationId/$fileName", imageUri)
    }

    /**
     * JUSTIFICATION DE L'AJOUT : Nouvelle méthode pour uploader l'image de couverture d'un livre.
     * Elle est distincte de `uploadCoverPicture` (pour les utilisateurs) et utilise un chemin dédié.
     * @param bookId L'ID du livre, pour organiser les fichiers.
     * @param imageData Les données binaires de l'image.
     * @return Une Resource contenant l'URL de l'image.
     */
    suspend fun uploadBookCover(bookId: String, imageData: ByteArray): Resource<String> {
        return uploadData(FirebaseConstants.STORAGE_BOOK_COVERS, "$bookId/${UUID.randomUUID()}.jpg", imageData)
    }

    /**
     * JUSTIFICATION DE L'AJOUT : Nouvelle méthode pour uploader le fichier PDF d'un livre.
     * Elle utilise `uploadUri` car les fichiers PDF seront sélectionnés depuis le système de fichiers de l'appareil.
     * @param bookId L'ID du livre.
     * @param pdfUri L'URI du fichier PDF sélectionné.
     * @return Une Resource contenant l'URL du PDF.
     */
    suspend fun uploadBookPdf(bookId: String, pdfUri: Uri): Resource<String> {
        return uploadUri(FirebaseConstants.STORAGE_BOOK_PDFS, "$bookId/content.pdf", pdfUri)
    }

    // --- Méthodes d'upload génériques refactorisées ---

    private suspend fun uploadData(basePath: String, filePath: String, data: ByteArray): Resource<String> {
        if (data.isEmpty()) {
            return Resource.Error("Impossible d'uploader des données vides.")
        }
        val storageRef = storage.reference.child("$basePath/$filePath")
        Log.i(TAG, "Tentative d'upload de données vers Storage Path: ${storageRef.path}")

        return try {
            storageRef.putBytes(data).await()
            val downloadUrl = storageRef.downloadUrl.await().toString()
            Log.i(TAG, "SUCCÈS de l'upload. URL: $downloadUrl")
            Resource.Success(downloadUrl)
        } catch (e: Exception) {
            handleUploadException(e, storageRef.path)
        }
    }

    private suspend fun uploadUri(basePath: String, filePath: String, uri: Uri): Resource<String> {
        val storageRef = storage.reference.child("$basePath/$filePath")
        Log.i(TAG, "Tentative d'upload de fichier (Uri) vers Storage Path: ${storageRef.path}")

        return try {
            storageRef.putFile(uri).await()
            val downloadUrl = storageRef.downloadUrl.await().toString()
            Log.i(TAG, "SUCCÈS de l'upload. URL: $downloadUrl")
            Resource.Success(downloadUrl)
        } catch (e: Exception) {
            handleUploadException(e, storageRef.path)
        }
    }

    private fun handleUploadException(e: Exception, path: String): Resource.Error<String> {
        Log.e(TAG, "EXCEPTION lors de l'opération sur $path. Type: ${e.javaClass.simpleName}, Message: ${e.message}", e)
        val errorMessage = if (e is StorageException) {
            "Erreur Firebase Storage (code ${e.errorCode}): ${e.localizedMessage}"
        } else {
            "Erreur lors de l'upload: ${e.localizedMessage}"
        }
        return Resource.Error(errorMessage)
    }
}