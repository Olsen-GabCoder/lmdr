// PRÊT À COLLER - Remplacez le contenu de votre fichier GetMonthlyReadingsUseCase.kt
package com.lesmangeursdurouleau.app.domain.usecase.monthlyreadings

import com.lesmangeursdurouleau.app.data.model.MonthlyReading
import com.lesmangeursdurouleau.app.data.repository.MonthlyReadingRepository
import com.lesmangeursdurouleau.app.utils.Resource
import kotlinx.coroutines.flow.Flow
import javax.inject.Inject

/**
 * Use Case dont la responsabilité unique est de récupérer des LISTES de lectures mensuelles.
 */
class GetMonthlyReadingsUseCase @Inject constructor(
    private val monthlyReadingRepository: MonthlyReadingRepository
) {
    operator fun invoke(year: Int, month: Int): Flow<Resource<List<MonthlyReading>>> {
        return monthlyReadingRepository.getMonthlyReadings(year, month)
    }

    operator fun invoke(): Flow<Resource<List<MonthlyReading>>> {
        return monthlyReadingRepository.getAllMonthlyReadings()
    }
}