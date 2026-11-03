plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
    id("org.jetbrains.kotlin.plugin.compose")
}

android {
    namespace = "com.romuel.pianosolfege"
    compileSdk = 35

    // The private signing key is injected by GitHub Actions secrets, never committed.
    val signingFile = System.getenv("PIANO_KEYSTORE_FILE")
    val signingStorePassword = System.getenv("PIANO_KEYSTORE_PASSWORD")
    val signingKeyPassword = System.getenv("PIANO_KEY_PASSWORD")
    val signingAlias = System.getenv("PIANO_KEY_ALIAS")
    val canSignRelease = !signingFile.isNullOrBlank() &&
        !signingStorePassword.isNullOrBlank() &&
        !signingKeyPassword.isNullOrBlank() &&
        !signingAlias.isNullOrBlank()

    signingConfigs {
        if (canSignRelease) {
            create("distribution") {
                storeFile = file(signingFile!!)
                storePassword = signingStorePassword
                keyAlias = signingAlias
                keyPassword = signingKeyPassword
            }
        }
    }

    buildTypes {
        getByName("release") {
            isMinifyEnabled = false
            if (canSignRelease) {
                signingConfig = signingConfigs.getByName("distribution")
            }
        }
    }

    defaultConfig {
        applicationId = "com.romuel.pianosolfege"
        minSdk = 26
        targetSdk = 35
        // GitHub run numbers make every distributed build newer than the previous.
        versionCode = System.getenv("GITHUB_RUN_NUMBER")?.toIntOrNull()?.plus(100) ?: 3
        versionName = "1.3.0"
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }

    buildFeatures {
        compose = true
    }

    packaging {
        resources {
            excludes += "/META-INF/{AL2.0,LGPL2.1}"
        }
    }
}

dependencies {
    val composeBom = platform("androidx.compose:compose-bom:2024.12.01")
    implementation(composeBom)
    androidTestImplementation(composeBom)

    implementation("androidx.core:core-ktx:1.15.0")
    implementation("androidx.activity:activity-compose:1.10.0")
    implementation("androidx.lifecycle:lifecycle-viewmodel-compose:2.8.7")
    implementation("androidx.lifecycle:lifecycle-runtime-compose:2.8.7")
    implementation("androidx.compose.ui:ui")
    implementation("androidx.compose.ui:ui-tooling-preview")
    implementation("androidx.compose.material3:material3")

    // Bundled Latin OCR model: available immediately, including offline PDF reading.
    implementation("com.google.mlkit:text-recognition:16.0.1")

    testImplementation("junit:junit:4.13.2")

    debugImplementation("androidx.compose.ui:ui-tooling")
}
