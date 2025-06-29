package com.lesmangeursdurouleau.app

import android.app.Application
import dagger.hilt.android.HiltAndroidApp

@HiltAndroidApp
class MainApplication : Application() {
    // Pour l'instant, cette classe peut rester vide.
    // Hilt s'occupe de la génération du code concessionaire.
    // Tu pourras y ajouter de la logique d'initialisation globale
    // pour ton application plus tard si besoin (ex: Timber, Stetho, etc.).
}