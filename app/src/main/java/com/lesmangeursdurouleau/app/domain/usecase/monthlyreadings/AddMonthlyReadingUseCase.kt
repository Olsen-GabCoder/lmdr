// PRÊT À COLLER - Remplacez tout le contenu de votre fichier AddMonthlyReadingUseCase.kt par ceci.
package com.lesmangeursdurouleau.app.domain.usecase.monthlyreadings

import com.lesmangeursdurouleau.app.data.model.Book
import com.lesmangeursdurouleau.app.data.model.MonthlyReading
import com.lesmangeursdurouleau.app.data.model.Phase
import com.lesmangeursdurouleau.app.data.model.PhaseStatus // NOUVEL IMPORT
import com.lesmangeursdurouleau.app.data.repository.BookRepository
import com.lesmangeursdurouleau.app.data.repository.MonthlyReadingRepository
import com.lesmangeursdurouleau.app.utils.Resource
import java.util.Date
import javax.inject.Inject

class AddMonthlyReadingUseCase @Inject constructor(
    private val monthlyReadingRepository: MonthlyReadingRepository,
    private val bookRepository: BookRepository
) {
    suspend operator fun invoke(
        bookId: String,
        year: Int,
        month: Int,
        analysisDate: Date,
        analysisMeetingLink: String?,
        debateDate: Date,
        debateMeetingLink: String?,
        customDescription: String?
    ): Resource<Unit> {
        if (bookId.isBlank()) {
            return Resource.Error("L'ID du livre est obligatoire pour la lecture mensuelle.")
        }
        if (year <= 0 || month !in 1..12) {
            return Resource.Error("La date (année/mois) est invalide.")
        }
        if (analysisDate == null || debateDate == null) {
            return Resource.Error("Les dates d'analyse et de débat sont obligatoires.")
        }

        val monthlyReading = MonthlyReading(
            bookId = bookId,
            year = year,
            month = month,
            analysisPhase = Phase(
                date = analysisDate,
                // MODIFIÉ: Utilisation de l'enum au lieu de la chaîne de caractères.
                status = PhaseStatus.PLANIFIED,
                meetingLink = analysisMeetingLink
            ),
            debatePhase = Phase(
                date = debateDate,
                // MODIFIÉ: Utilisation de l'enum au lieu de la chaîne de caractères.
                status = PhaseStatus.PLANIFIED,
                meetingLink = debateMeetingLink
            ),
            customDescription = customDescription
        )
        return monthlyReadingRepository.addMonthlyReading(monthlyReading)
    }
}