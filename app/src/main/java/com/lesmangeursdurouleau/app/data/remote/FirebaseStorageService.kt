// PRÊT À COLLER - Fichier FirebaseStorageService.kt mis à jour
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
        if (imageData.isEmpty()) {
            return Resource.Error("Impossible d'uploader une image vide.")
        }
        val randomFileName = "${UUID.randomUUID()}.jpg"
        // JUSTIFICATION : Utilisation de la constante pour la robustesse.
        val storageRef = storage.reference.child("${FirebaseConstants.STORAGE_PROFILE_PICTURES}/$userId/$randomFileName")
        Log.i(TAG, "uploadProfilePicture: Tentative d'upload vers Storage Path: ${storageRef.path}")

        return try {
            storageRef.putBytes(imageData).await()
            val downloadUrl = storageRef.downloadUrl.await().toString()
            Log.i(TAG, "uploadProfilePicture: SUCCÈS. URL: $downloadUrl")
            Resource.Success(downloadUrl)
        } catch (e: Exception) {
            handleUploadException(e, storageRef.path)
        }
    }

    /**
     * JUSTIFICATION DE L'AJOUT : Nouvelle méthode pour uploader la photo de couverture.
     * Cette méthode est une symétrie de `uploadProfilePicture` mais utilise le chemin de stockage
     * dédié `cover_pictures` (via la constante `STORAGE_COVER_PICTURES`), que nous avons
     * précédemment défini et sécurisé dans `storage.rules`.
     * Elle encapsule la logique d'upload et de gestion d'erreurs, rendant le `UserProfileRepository`
     * plus propre et respectant le principe de responsabilité unique.
     *
     * @param userId L'ID de l'utilisateur.
     * @param imageData Les données binaires de l'image.
     * @return Une Resource contenant l'URL de l'image en cas de succès.
     */
    suspend fun uploadCoverPicture(userId: String, imageData: ByteArray): Resource<String> {
        if (imageData.isEmpty()) {
            return Resource.Error("Impossible d'uploader une image vide.")
        }
        val randomFileName = "${UUID.randomUUID()}.jpg"
        // JUSTIFICATION : Utilise le nouveau chemin dédié aux photos de couverture.
        val storageRef = storage.reference.child("${FirebaseConstants.STORAGE_COVER_PICTURES}/$userId/$randomFileName")
        Log.i(TAG, "uploadCoverPicture: Tentative d'upload vers Storage Path: ${storageRef.path}")

        return try {
            storageRef.putBytes(imageData).await()
            val downloadUrl = storageRef.downloadUrl.await().toString()
            Log.i(TAG, "uploadCoverPicture: SUCCÈS. URL: $downloadUrl")
            Resource.Success(downloadUrl)
        } catch (e: Exception) {
            handleUploadException(e, storageRef.path)
        }
    }

    suspend fun uploadChatMessageImage(
        conversationId: String,
        fileName: String,
        imageUri: Uri
    ): Resource<String> {
        // JUSTIFICATION : Utilisation de la constante pour la robustesse.
        val storageRef = storage.reference.child("${FirebaseConstants.STORAGE_CHAT_IMAGES}/$conversationId/$fileName")
        Log.i(TAG, "uploadChatMessageImage: Tentative d'upload vers Storage Path: ${storageRef.path}")

        return try {
            storageRef.putFile(imageUri).await()
            val downloadUrl = storageRef.downloadUrl.await().toString()
            Log.i(TAG, "uploadChatMessageImage: SUCCÈS. URL: $downloadUrl")
            Resource.Success(downloadUrl)
        } catch (e: Exception) {
            handleUploadException(e, storageRef.path)
        }
    }

    /**
     * JUSTIFICATION DE L'AJOUT : Extraction de la logique de gestion des exceptions.
     * Le bloc `catch` était dupliqué. Cette fonction privée centralise le logging et la
     * transformation de l'exception en un message d'erreur pour l'utilisateur,
     * respectant ainsi le principe DRY (Don't Repeat Yourself).
     */
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