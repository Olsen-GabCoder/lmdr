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
 * Use Case unifié pour la création et la mise à jour d'une lecture mensuelle.
 * Il orchestre la sauvegarde du livre associé et de la lecture elle-même.
 */
class SaveMonthlyReadingUseCase @Inject constructor(
    private val bookRepository: BookRepository,
    private val monthlyReadingRepository: MonthlyReadingRepository
) {
    suspend operator fun invoke(
        monthlyReadingId: String?,
        year: Int,
        month: Int,
        analysisDate: Date,
        analysisStatus: PhaseStatus,
        analysisMeetingLink: String?,
        debateDate: Date,
        debateStatus: PhaseStatus,
        debateMeetingLink: String?,
        customDescription: String?,
        bookFromForm: Book,
        existingBookId: String?
    ): Resource<Unit> {
        return try {
            val bookResult = saveOrUpdateBook(bookFromForm, existingBookId)
            val finalBookId = when (bookResult) {
                is Resource.Success -> bookResult.data!!
                is Resource.Error -> return Resource.Error(bookResult.message ?: "Erreur livre")
                is Resource.Loading -> return Resource.Error("État de chargement inattendu.")
            }

            val monthlyReading = MonthlyReading(
                id = monthlyReadingId ?: "",
                bookId = finalBookId.toString(),
                year = year,
                month = month,
                analysisPhase = Phase(analysisDate, analysisStatus, analysisMeetingLink),
                debatePhase = Phase(debateDate, debateStatus, debateMeetingLink),
                customDescription = customDescription
            )

            if (monthlyReadingId.isNullOrBlank()) {
                monthlyReadingRepository.addMonthlyReading(monthlyReading)
            } else {
                monthlyReadingRepository.updateMonthlyReading(monthlyReading)
            }
        } catch (e: Exception) {
            Resource.Error("Erreur système : ${e.localizedMessage}")
        }
    }

    private suspend fun saveOrUpdateBook(bookFromForm: Book, existingBookId: String?): Resource<out Any> {
        return if (existingBookId == null) {
            bookRepository.addBook(bookFromForm.copy(id = ""))
        } else {
            val bookToUpdate = bookFromForm.copy(id = existingBookId)
            when (val result = bookRepository.updateBook(bookToUpdate)) {
                is Resource.Success -> Resource.Success(existingBookId)
                is Resource.Error -> result
                is Resource.Loading -> Resource.Error("État inattendu")
            }
        }
    }
}