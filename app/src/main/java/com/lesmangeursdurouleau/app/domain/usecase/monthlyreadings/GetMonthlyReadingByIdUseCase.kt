// PRÊT À COLLER - Nouveau Fichier
package com.lesmangeursdurouleau.app.domain.usecase.monthlyreadings

import com.lesmangeursdurouleau.app.data.model.MonthlyReading
import com.lesmangeursdurouleau.app.data.repository.MonthlyReadingRepository
import com.lesmangeursdurouleau.app.utils.Resource
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flowOf
import javax.inject.Inject

/**
 * Use Case avec une responsabilité unique et claire : récupérer une seule lecture mensuelle par son ID.
 */
class GetMonthlyReadingByIdUseCase @Inject constructor(
    private val monthlyReadingRepository: MonthlyReadingRepository
) {
    operator fun invoke(readingId: String): Flow<Resource<MonthlyReading?>> {
        if (readingId.isBlank()) {
            return flowOf(Resource.Error("L'ID de la lecture ne peut pas être vide."))
        }
        return monthlyReadingRepository.getMonthlyReadingById(readingId)
    }
}