// PRÊT À COLLER - Logique adaptée à la nouvelle API MediaWiki
package com.lesmangeursdurouleau.app.data.repository

import android.os.Build
import android.text.Html
import android.util.Log
import androidx.annotation.RequiresApi
import com.lesmangeursdurouleau.app.data.model.Definition
import com.lesmangeursdurouleau.app.data.remote.DictionaryApiService
import com.lesmangeursdurouleau.app.utils.Resource
import retrofit2.HttpException
import java.io.IOException
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class DictionaryRepositoryImpl @Inject constructor(
    private val apiService: DictionaryApiService
) : DictionaryRepository {

    @RequiresApi(Build.VERSION_CODES.N)
    override suspend fun getDefinition(word: String): Resource<Definition> {
        if (word.isBlank()) {
            return Resource.Error("Veuillez saisir un mot.")
        }

        return try {
            // L'appel à l'API est maintenant différent
            val response = apiService.getDefinition(title = word.lowercase())

            // Le parsing de la réponse est adapté à la structure de MediaWiki
            val page = response.query?.pages?.values?.firstOrNull()
            val extractHtml = page?.extract

            // La page avec un pageId de -1 signifie que le mot n'a pas été trouvé.
            if (page == null || page.pageId == -1 || extractHtml.isNullOrBlank()) {
                return Resource.Error("Définition non trouvée pour \"$word\".")
            }

            // Nettoyage du HTML pour obtenir du texte brut.
            val cleanDefinition = Html.fromHtml(extractHtml, Html.FROM_HTML_MODE_LEGACY).toString().trim()

            Resource.Success(
                Definition(
                    word = page.title, // On utilise le titre retourné par l'API (gère les redirections)
                    phonetic = "[Wiktionnaire]", // La phonétique n'est pas dans cet extrait simple.
                    meaning = cleanDefinition
                )
            )
        } catch (e: HttpException) {
            Log.e("DictionaryRepoImpl", "HttpException: ${e.code()} - ${e.message()}", e)
            Resource.Error("Une erreur serveur est survenue (code: ${e.code()}).")
        } catch (e: IOException) {
            Log.e("DictionaryRepoImpl", "IOException: Problème de connexion", e)
            Resource.Error("Veuillez vérifier votre connexion internet.")
        } catch (e: Exception) {
            Log.e("DictionaryRepoImpl", "Exception inattendue: ${e.message}", e)
            Resource.Error("Une erreur inattendue est survenue.")
        }
    }
}
