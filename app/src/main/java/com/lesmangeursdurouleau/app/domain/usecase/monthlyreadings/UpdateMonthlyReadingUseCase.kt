// PRÊT À COLLER - Remplacez tout le contenu de votre fichier UpdateMonthlyReadingUseCase.kt par ceci.
package com.lesmangeursdurouleau.app.domain.usecase.monthlyreadings

import com.lesmangeursdurouleau.app.data.model.MonthlyReading
import com.lesmangeursdurouleau.app.data.model.Phase
import com.lesmangeursdurouleau.app.data.model.PhaseStatus
import com.lesmangeursdurouleau.app.data.repository.MonthlyReadingRepository
import com.lesmangeursdurouleau.app.utils.Resource
import java.util.Date
import javax.inject.Inject

class UpdateMonthlyReadingUseCase @Inject constructor(
    private val monthlyReadingRepository: MonthlyReadingRepository
) {
    suspend operator fun invoke(
        id: String,
        bookId: String,
        year: Int,
        month: Int,
        analysisDate: Date,
        // MODIFIÉ: Le type du paramètre est maintenant PhaseStatus.
        analysisStatus: PhaseStatus,
        analysisMeetingLink: String?,
        debateDate: Date,
        // MODIFIÉ: Le type du paramètre est maintenant PhaseStatus.
        debateStatus: PhaseStatus,
        debateMeetingLink: String?,
        customDescription: String?
    ): Resource<Unit> {
        if (id.isBlank() || bookId.isBlank()) {
            return Resource.Error("L'ID de la lecture et le livre sont obligatoires pour la mise à jour.")
        }
        if (year <= 0 || month !in 1..12) {
            return Resource.Error("La date (année/mois) est invalide.")
        }
        if (analysisDate == null || debateDate == null) {
            return Resource.Error("Les dates d'analyse et de débat sont obligatoires.")
        }

        val updatedMonthlyReading = MonthlyReading(
            id = id,
            bookId = bookId,
            year = year,
            month = month,
            analysisPhase = Phase(
                date = analysisDate,
                // MODIFIÉ: Assignation directe de l'enum.
                status = analysisStatus,
                meetingLink = analysisMeetingLink
            ),
            debatePhase = Phase(
                date = debateDate,
                // MODIFIÉ: Assignation directe de l'enum.
                status = debateStatus,
                meetingLink = debateMeetingLink
            ),
            customDescription = customDescription
        )
        return monthlyReadingRepository.updateMonthlyReading(updatedMonthlyReading)
    }
}