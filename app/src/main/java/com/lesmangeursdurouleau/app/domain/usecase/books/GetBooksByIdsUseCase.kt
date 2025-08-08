package com.lesmangeursdurouleau.app.domain.usecase.books

import com.lesmangeursdurouleau.app.data.model.Book
import com.lesmangeursdurouleau.app.data.repository.BookRepository
import com.lesmangeursdurouleau.app.utils.Resource
import kotlinx.coroutines.flow.Flow
import javax.inject.Inject

/**
 * Use Case pour récupérer les détails de plusieurs livres en une seule fois
 * à partir d'une liste d'identifiants.
 */
class GetBooksByIdsUseCase @Inject constructor(
    private val bookRepository: BookRepository
) {
    /**
     * Exécute le cas d'utilisation.
     * @param bookIds La liste des identifiants des livres à récupérer.
     * @return Un [Flow] de [Resource] qui émet la liste des objets [Book] correspondants.
     */
    operator fun invoke(bookIds: List<String>): Flow<Resource<List<Book>>> {
        return bookRepository.getBooksByIds(bookIds)
    }
}