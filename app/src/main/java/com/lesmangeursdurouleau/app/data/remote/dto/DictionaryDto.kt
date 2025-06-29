// PRÊT À COLLER - Version pour l'endpoint /definition (plus stable)
package com.lesmangeursdurouleau.app.data.remote.dto

import com.google.gson.annotations.SerializedName

data class WiktionaryDefinitionResponse(
    @SerializedName("fr") val fr: List<WiktionaryEntryDto>?
)

data class WiktionaryEntryDto(
    @SerializedName("partOfSpeech") val partOfSpeech: String?,
    @SerializedName("language") val language: String?,
    @SerializedName("definitions") val definitions: List<WiktionaryDefinitionDto>?
)

data class WiktionaryDefinitionDto(
    @SerializedName("definition") val definition: String?,
    @SerializedName("examples") val examples: List<String>?
)