// PRÊT À COLLER - Fichier 100% complet et corrigé
package com.lesmangeursdurouleau.app.data.remote

import com.lesmangeursdurouleau.app.data.remote.dto.MediaWikiResponse
import retrofit2.http.GET
import retrofit2.http.Query

/**
 * Service d'API pour interroger l'API MediaWiki du Wiktionnaire.
 */
interface DictionaryApiService {

    /**
     * Récupère le contenu d'une page du Wiktionnaire.
     * C'est la méthode standard et robuste pour obtenir des définitions.
     *
     * Exemple d'URL générée :
     * https://fr.wiktionary.org/w/api.php?action=query&format=json&prop=extracts&titles=partir&redirects=1
     */
    @GET("w/api.php")
    suspend fun getDefinition(
        @Query("action") action: String = "query",
        @Query("format") format: String = "json",
        @Query("prop") property: String = "extracts",
        @Query("titles") title: String, // Le mot à rechercher
        @Query("redirects") redirects: Int = 1 // Suivre les redirections (ex: "pommes" -> "pomme")
    ): MediaWikiResponse
}