// PRÊT À COLLER - Fichier complet et corrigé
plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.android)
    alias(libs.plugins.googleGmsServices)
    id("androidx.navigation.safeargs.kotlin")
    id("kotlin-kapt")
    id("dagger.hilt.android.plugin")
    id("kotlin-parcelize")
}

android {
    namespace = "com.lesmangeursdurouleau.app"
    compileSdk = 35

    defaultConfig {
        applicationId = "com.lesmangeursdurouleau.app"
        minSdk = 23
        targetSdk = 35
        versionCode = 1
        versionName = "1.0"

        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
    }

    buildTypes {
        release {
            isMinifyEnabled = false
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro"
            )
        }
    }
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_11
        targetCompatibility = JavaVersion.VERSION_11
    }
    kotlinOptions {
        jvmTarget = "11"
    }
    buildFeatures {
        viewBinding = true
    }
}

dependencies {
    // =================================================================================
    // DÉBUT DE LA CORRECTION DÉFINITIVE
    // =================================================================================
    // AJOUT : Forcer la version de ConstraintLayout pour résoudre les conflits de dépendances transitives.
    // Cette ligne agit comme une instruction prioritaire pour Gradle et garantit que la version 2.1.4 est utilisée.
    implementation("androidx.constraintlayout:constraintlayout:2.1.4")
    // =================================================================================
    // FIN DE LA CORRECTION DÉFINITIVE
    // =================================================================================

    // AndroidX Core & UI - Toutes gérées via libs.versions.toml
    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.appcompat)
    implementation(libs.material)
    implementation(libs.androidx.activity.ktx)
    implementation(libs.androidx.constraintlayout) // Laisser cette ligne ne pose pas de problème, la déclaration explicite ci-dessus aura la priorité.
    implementation(libs.androidx.fragment.ktx)
    implementation(libs.androidx.recyclerview)

    // Navigation - Toutes gérées via libs.versions.toml
    implementation(libs.androidx.navigation.fragment.ktx)
    implementation(libs.androidx.navigation.ui.ktx)

    // Lifecycle - Toutes gérées via libs.versions.toml
    implementation(libs.androidx.lifecycle.runtime.ktx)
    implementation(libs.androidx.lifecycle.viewmodel.ktx)

    // Firebase - Utilisation de la BoM pour une gestion cohérente des versions
    implementation(platform(libs.firebase.bom))
    implementation(libs.firebase.auth.ktx)
    implementation(libs.firebase.firestore.ktx)
    implementation(libs.firebase.storage.ktx)
    implementation(libs.firebase.messaging.ktx)
    implementation("com.google.firebase:firebase-analytics-ktx")

    // Google Services - Versions spécifiques
    implementation("com.google.android.gms:play-services-auth:21.2.0")
    implementation("com.google.android.gms:play-services-maps:18.2.0")
    implementation("com.google.android.gms:play-services-location:21.3.0")
    implementation(libs.play.services.fido)

    // Work Manager
    implementation("androidx.work:work-runtime-ktx:2.9.0")

    // Kotlin Coroutines
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-android:1.7.3")
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-play-services:1.7.3")

    // === DÉBUT DE L'AJOUT : DÉPENDANCES RÉSEAU ===
    implementation("com.squareup.retrofit2:retrofit:2.9.0")
    implementation("com.squareup.retrofit2:converter-gson:2.9.0")
    // === FIN DE L'AJOUT : DÉPENDANCES RÉSEAU ===

    // Glide
    implementation(libs.glide)
    implementation(libs.androidx.leanback)
    implementation(libs.firebase.functions.ktx)
    kapt("com.github.bumptech.glide:compiler:4.16.0")

    // Shimmer
    implementation("com.facebook.shimmer:shimmer:0.5.0") // NETTOYAGE : Suppression de la ligne dupliquée pour Shimmer

    // UCrop
    implementation("com.github.yalantis:ucrop:2.2.8")

    // Librairie pour le zoom sur les images
    implementation("com.github.chrisbanes:PhotoView:2.3.0")

    implementation("com.squareup.okhttp3:logging-interceptor:4.12.0")
    // Hilt Dependencies
    implementation("com.google.dagger:hilt-android:2.51.1")
    kapt("com.google.dagger:hilt-android-compiler:2.51.1")

    // Tests
    testImplementation(libs.junit)
    androidTestImplementation(libs.androidx.junit)
    androidTestImplementation(libs.androidx.espresso.core)
}

kapt {
    correctErrorTypes = true
}