// PRÊT À COLLER - Fichier complet
package com.lesmangeursdurouleau.app.data.remote.dto

import com.google.gson.annotations.SerializedName

/**
 * Représente la réponse de l'API MediaWiki pour une recherche de définition.
 * C'est une structure imbriquée.
 */
data class MediaWikiResponse(
    @SerializedName("query")
    val query: Query?
)

data class Query(
    @SerializedName("pages")
    val pages: Map<String, Page>?
)

data class Page(
    @SerializedName("pageid")
    val pageId: Int,

    @SerializedName("title")
    val title: String,

    @SerializedName("extract")
    val extract: String? // Le contenu HTML brut de la définition
)