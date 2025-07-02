plugins {
    // Déclaration explicite des plugins avec leur ID et version, sans `apply false`.
    // Cela les met à disposition de tous les modules, mais ne les applique pas.
    id("com.android.application") version "8.2.2" apply false
    id("org.jetbrains.kotlin.android") version "1.9.22" apply false
    id("com.google.gms.google-services") version "4.4.1" apply false
    id("androidx.navigation.safeargs.kotlin") version "2.7.7" apply false
    id("com.google.dagger.hilt.android") version "2.51.1" apply false
}