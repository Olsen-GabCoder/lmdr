// PRÊT À COLLER - Créez un nouveau fichier CreateBookWithFilesUseCase.kt dans le package domain/usecase/books
package com.lesmangeursdurouleau.app.domain.usecase.books

import android.net.Uri
import com.lesmangeursdurouleau.app.data.model.Book
import com.lesmangeursdurouleau.app.data.remote.FirebaseStorageService
import com.lesmangeursdurouleau.app.data.repository.BookRepository
import com.lesmangeursdurouleau.app.utils.Resource
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope
import javax.inject.Inject

/**
 * Use Case pour gérer le processus complet de création d'un nouveau livre,
 * incluant l'upload de son image de couverture et de son fichier PDF.
 */
class CreateBookWithFilesUseCase @Inject constructor(
    private val bookRepository: BookRepository,
    private val storageService: FirebaseStorageService
) {
    suspend operator fun invoke(
        book: Book,
        coverImage: ByteArray?,
        pdfUri: Uri?
    ): Resource<String> {
        // Étape 1 : Créer un document de livre initial dans Firestore pour obtenir un ID.
        val initialBookResult = bookRepository.addBook(book)
        val bookId = when (initialBookResult) {
            is Resource.Success -> initialBookResult.data
            is Resource.Error -> return Resource.Error(initialBookResult.message ?: "Erreur création livre initial")
            is Resource.Loading -> return Resource.Error("État inattendu")
        }
        if (bookId.isNullOrBlank()) {
            return Resource.Error("Impossible d'obtenir un ID pour le nouveau livre.")
        }

        var finalCoverUrl: String? = null
        var finalPdfUrl: String? = null

        // Étape 2 : Lancer les uploads en parallèle pour plus d'efficacité.
        try {
            coroutineScope {
                val coverUploadJob = coverImage?.let {
                    async { storageService.uploadBookCover(bookId, it) }
                }
                val pdfUploadJob = pdfUri?.let {
                    async { storageService.uploadBookPdf(bookId, it) }
                }

                val coverUploadResult = coverUploadJob?.await()
                if (coverUploadResult is Resource.Error) throw Exception(coverUploadResult.message)
                finalCoverUrl = (coverUploadResult as? Resource.Success)?.data

                val pdfUploadResult = pdfUploadJob?.await()
                if (pdfUploadResult is Resource.Error) throw Exception(pdfUploadResult.message)
                finalPdfUrl = (pdfUploadResult as? Resource.Success)?.data
            }
        } catch (e: Exception) {
            // En cas d'échec de l'upload, il faudrait idéalement supprimer le document livre créé
            // Pour l'instant, nous retournons une erreur claire.
            return Resource.Error("Échec de l'upload : ${e.message}")
        }

        // Étape 3 : Mettre à jour le document du livre avec les URLs des fichiers.
        val updatedBook = book.copy(
            id = bookId,
            coverImageUrl = finalCoverUrl,
            contentUrl = finalPdfUrl
        )
        val updateResult = bookRepository.updateBook(updatedBook)

        return when (updateResult) {
            is Resource.Success -> Resource.Success(bookId)
            is Resource.Error -> Resource.Error(updateResult.message ?: "Erreur de mise à jour finale du livre")
            is Resource.Loading -> Resource.Error("État inattendu")
        }
    }
}