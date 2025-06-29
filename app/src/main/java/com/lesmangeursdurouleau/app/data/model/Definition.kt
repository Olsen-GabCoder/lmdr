// PRÊT À COLLER - Fichier 100% complet
package com.lesmangeursdurouleau.app.data.model

/**
 * Représente la définition d'un mot.
 *
 * @property word Le mot recherché.
 * @property phonetic La transcription phonétique du mot.
 * @property meaning La définition principale du mot.
 */
data class Definition(
    val word: String,
    val phonetic: String,
    val meaning: String
)