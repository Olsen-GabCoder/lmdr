// PRÊT À COLLER - Fichier 100% complet
package com.lesmangeursdurouleau.app.ui.members.dictionary

import com.lesmangeursdurouleau.app.data.model.Definition

/**
 * Représente l'état de l'interface utilisateur pour l'écran du dictionnaire.
 *
 * @property isLoading Indique si une recherche est en cours.
 * @property definition Contient la définition trouvée en cas de succès. Null sinon.
 * @property errorMessage Contient le message d'erreur en cas d'échec. Null sinon.
 */
data class DictionaryUiState(
    val isLoading: Boolean = false,
    val definition: Definition? = null,
    val errorMessage: String? = null
)