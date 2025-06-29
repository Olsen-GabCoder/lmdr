// PRÊT À COLLER - Version avec logging intensif
package com.lesmangeursdurouleau.app.di

import com.lesmangeursdurouleau.app.data.remote.DictionaryApiService
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import okhttp3.OkHttpClient
import okhttp3.logging.HttpLoggingInterceptor
import retrofit2.Retrofit
import retrofit2.converter.gson.GsonConverterFactory
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
object NetworkModule {

    private const val BASE_URL = "https://fr.wiktionary.org/"

    @Provides
    @Singleton
    fun provideOkHttpClient(): OkHttpClient {
        // Création de l'intercepteur de logging
        val loggingInterceptor = HttpLoggingInterceptor().apply {
            level = HttpLoggingInterceptor.Level.BODY // Affiche tout: URL, méthode, en-têtes, corps
        }

        return OkHttpClient.Builder()
            .addInterceptor { chain ->
                val originalRequest = chain.request()
                val requestWithHeaders = originalRequest.newBuilder()
                    .header("User-Agent", "LesMangeursDuRouleauApp/1.0 (Android)")
                    .build()
                chain.proceed(requestWithHeaders)
            }
            // Ajout de l'intercepteur de logging comme DERNIER intercepteur
            .addInterceptor(loggingInterceptor)
            .build()
    }

    @Provides
    @Singleton
    fun provideRetrofit(okHttpClient: OkHttpClient): Retrofit {
        return Retrofit.Builder()
            .baseUrl(BASE_URL)
            .client(okHttpClient)
            .addConverterFactory(GsonConverterFactory.create())
            .build()
    }

    @Provides
    @Singleton
    fun provideDictionaryApiService(retrofit: Retrofit): DictionaryApiService {
        return retrofit.create(DictionaryApiService::class.java)
    }
}