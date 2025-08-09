// PRÊT À COLLER - Remplacez TOUT le contenu de votre fichier CreateBookWithFilesUseCase.kt
package com.lesmangeursdurouleau.app.domain.usecase.books

import android.content.Context
import android.net.Uri
import com.lesmangeursdurouleau.app.data.repository.AdminRepository
import com.lesmangeursdurouleau.app.utils.Resource
import dagger.hilt.android.qualifiers.ApplicationContext
import java.io.IOException
import javax.inject.Inject

/**
 * JUSTIFICATION: Ce UseCase est entièrement refactorisé pour appeler la nouvelle logique serveur.
 * 1.  Il ne dépend plus de `BookRepository` ou `FirebaseStorageService`. Sa seule dépendance est `AdminRepository`.
 * 2.  Il prend en charge la seule tâche qui doit rester côté client : la lecture du fichier PDF à partir de son `Uri`
 *     pour le convertir en `ByteArray`. C'est une logique purement Android.
 * 3.  Il orchestre l'appel à la méthode `createBookWithFiles` du repository, en lui passant toutes les données prêtes à l'emploi.
 * 4.  L'ancienne logique complexe et faillible d'uploads multiples et de mises à jour a été entièrement supprimée.
 */
class CreateBookWithFilesUseCase @Inject constructor(
    private val adminRepository: AdminRepository,
    @ApplicationContext private val context: Context
) {
    suspend operator fun invoke(
        title: String,
        author: String,
        synopsis: String,
        totalPages: Int,
        coverImage: ByteArray?,
        pdfUri: Uri?
    ): Resource<String> {

        val pdfFile: ByteArray? = try {
            pdfUri?.let { uri ->
                context.contentResolver.openInputStream(uri)?.use { it.readBytes() }
            }
        } catch (e: IOException) {
            return Resource.Error("Erreur de lecture du fichier PDF.")
        }

        return adminRepository.createBookWithFiles(
            title = title,
            author = author,
            synopsis = synopsis,
            totalPages = totalPages,
            coverImage = coverImage,
            pdfFile = pdfFile
        )
    }
}