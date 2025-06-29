// PRÊT À COLLER - Fichier 100% complet
package com.lesmangeursdurouleau.app.data.repository

import com.lesmangeursdurouleau.app.data.model.Definition
import com.lesmangeursdurouleau.app.utils.Resource

/**
 * Interface définissant le contrat pour obtenir des définitions de mots.
 * Cela permet de découpler les ViewModels de l'implémentation concrète
 * de la source de données (API, base de données, etc.).
 */
interface DictionaryRepository {

    /**
     * Récupère la définition d'un mot donné.
     * La fonction est suspendue car elle représente une opération potentiellement longue (ex: appel réseau).
     *
     * @param word Le mot à définir.
     * @return Un objet Resource encapsulant le résultat :
     *         - Resource.Success avec la Définition si la recherche réussit.
     *         - Resource.Error avec un message si le mot n'est pas trouvé ou qu'une erreur survient.
     */
    suspend fun getDefinition(word: String): Resource<Definition>
}