// PRÊT À COLLER - Nouveau Fichier
package com.lesmangeursdurouleau.app.domain.usecase.monthlyreadings

import com.lesmangeursdurouleau.app.data.model.Book
import com.lesmangeursdurouleau.app.data.model.MonthlyReading
import com.lesmangeursdurouleau.app.data.model.Phase
import com.lesmangeursdurouleau.app.data.model.PhaseStatus
import com.lesmangeursdurouleau.app.data.repository.BookRepository
import com.lesmangeursdurouleau.app.data.repository.MonthlyReadingRepository
import com.lesmangeursdurouleau.app.utils.Resource
import java.util.Date
import javax.inject.Inject

/**
 * Use Case unifié pour la création ou la mise à jour d'une lecture mensuelle.
 * Il orchestre la sauvegarde du livre associé et de la lecture elle-même.
 */
class SaveMonthlyReadingUseCase @Inject constructor(
    private val bookRepository: BookRepository,
    private val monthlyReadingRepository: MonthlyReadingRepository
) {
    suspend operator fun invoke(
        // Paramètres de la lecture mensuelle
        monthlyReadingId: String?, // Null si c'est une création
        year: Int,
        month: Int,
        analysisDate: Date,
        analysisStatus: PhaseStatus,
        analysisMeetingLink: String?,
        debateDate: Date,
        debateStatus: PhaseStatus,
        debateMeetingLink: String?,
        customDescription: String?,
        // Paramètres du livre
        book: Book,
        existingBookId: String? // ID du livre si sélectionné depuis l'autocomplete
    ): Resource<Unit> {

        // 1. Valider les entrées de la lecture mensuelle
        if (year <= 0 || month !in 1..12) return Resource.Error("La date (année/mois) est invalide.")
        if (analysisDate.after(debateDate)) return Resource.Error("La date d'analyse doit être antérieure à la date de débat.")

        // 2. Logique de sauvegarde ou de mise à jour du livre
        val bookResult = saveOrUpdateBook(book, existingBookId)
        val finalBookId = when (bookResult) {
            is Resource.Success -> bookResult.data
            is Resource.Error -> return Resource.Error(bookResult.message ?: "Erreur lors de la sauvegarde du livre.")
            else -> return Resource.Error("Erreur inattendue lors de la sauvegarde du livre.")
        }
        //if (finalBookId.isNullOrBlank()) return Resource.Error("Impossible d'obtenir un ID de livre valide.")

        // 3. Création ou mise à jour de la lecture mensuelle
        return if (monthlyReadingId.isNullOrBlank()) {
            // Création
            val newMonthlyReading = MonthlyReading(
                bookId = finalBookId.toString(),
                year = year,
                month = month,
                analysisPhase = Phase(analysisDate, analysisStatus, analysisMeetingLink),
                debatePhase = Phase(debateDate, debateStatus, debateMeetingLink),
                customDescription = customDescription
            )
            monthlyReadingRepository.addMonthlyReading(newMonthlyReading)
        } else {
            // Mise à jour
            val updatedMonthlyReading = MonthlyReading(
                id = monthlyReadingId,
                bookId = finalBookId.toString(),
                year = year,
                month = month,
                analysisPhase = Phase(analysisDate, analysisStatus, analysisMeetingLink),
                debatePhase = Phase(debateDate, debateStatus, debateMeetingLink),
                customDescription = customDescription
            )
            monthlyReadingRepository.updateMonthlyReading(updatedMonthlyReading)
        }
    }

    private suspend fun saveOrUpdateBook(book: Book, existingBookId: String?): Resource<out Any> {
        // Détermine si le livre est nouveau ou si ses informations ont changé
        return if (existingBookId.isNullOrBlank()) {
            // C'est un nouveau livre, on l'ajoute.
            bookRepository.addBook(book)
        } else {
            // Le livre existe, on le met à jour avec les nouvelles informations du formulaire.
            // L'objet `book` contient les nouvelles données, `existingBookId` est l'ID du document à écraser.
            val bookToUpdate = book.copy(id = existingBookId)
            when (val updateResult = bookRepository.updateBook(bookToUpdate)) {
                is Resource.Success -> Resource.Success(existingBookId) // La mise à jour a réussi, on renvoie l'ID
                is Resource.Error -> updateResult // On propage l'erreur
                else -> Resource.Error("Erreur inconnue lors de la mise à jour du livre.")
            }
        }
    }
}