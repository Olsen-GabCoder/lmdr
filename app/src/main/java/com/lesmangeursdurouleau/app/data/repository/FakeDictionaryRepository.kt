// PRÊT À COLLER - Fichier 100% complet
package com.lesmangeursdurouleau.app.data.repository

import com.lesmangeursdurouleau.app.data.model.Definition
import com.lesmangeursdurouleau.app.utils.Resource
import kotlinx.coroutines.delay
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Implémentation factice du DictionaryRepository pour le développement et les tests.
 * Simule un appel réseau avec un délai.
 */
@Singleton
class FakeDictionaryRepository @Inject constructor() : DictionaryRepository {

    override suspend fun getDefinition(word: String): Resource<Definition> {
        // Simule la latence d'un appel réseau
        delay(1500)

        // Logique de simulation
        return when {
            word.isBlank() -> {
                Resource.Error("Veuillez saisir un mot.")
            }
            word.equals("inconnu", ignoreCase = true) -> {
                Resource.Error("Le mot \"$word\" n'a pas été trouvé dans notre dictionnaire.")
            }
            else -> {
                val fakeDefinition = Definition(
                    word = word,
                    phonetic = "/fausse-phonétique/",
                    meaning = "Ceci est une définition factice pour le mot '$word'. " +
                            "Elle sert à démontrer le bon fonctionnement de l'API du dictionnaire " +
                            "dans l'application 'Les Mangeurs du Rouleau'."
                )
                Resource.Success(fakeDefinition)
            }
        }
    }
}