// PRÊT À COLLER - Créez un nouveau fichier AdminRepositoryImpl.kt dans le package data/repository
package com.lesmangeursdurouleau.app.data.repository

import com.google.firebase.functions.FirebaseFunctions
import com.google.firebase.functions.ktx.functions
import com.google.firebase.ktx.Firebase
import com.lesmangeursdurouleau.app.utils.Resource
import kotlinx.coroutines.tasks.await
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class AdminRepositoryImpl @Inject constructor(
    private val functions: FirebaseFunctions
) : AdminRepository {

    override suspend fun setUserAsAdmin(email: String): Resource<String> {
        val data = hashMapOf(
            "email" to email,
            "isAdmin" to true
        )
        return try {
            val result = functions
                .getHttpsCallable("setUserRole")
                .call(data)
                .await()

            // Firebase Callable Functions retournent un HttpsCallableResult qui contient les données
            val resultData = result.data as? Map<*, *>
            val message = resultData?.get("message") as? String ?: "Opération terminée."

            Resource.Success(message)
        } catch (e: Exception) {
            // L'exception contient déjà un message d'erreur clair de la Cloud Function
            Resource.Error(e.message ?: "Une erreur inconnue est survenue.")
        }
    }
}