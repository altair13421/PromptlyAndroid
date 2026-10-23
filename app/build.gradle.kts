import java.util.Properties

plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.android)
    alias(libs.plugins.kotlin.compose)
    alias(libs.plugins.kotlin.serialization)
    alias(libs.plugins.ksp)
    alias(libs.plugins.hilt)
}

android {
    namespace = "com.promptly.app"
    compileSdk = 35

    defaultConfig {
        applicationId = "com.promptly.app"
        minSdk = 29
        targetSdk = 35
        versionCode = 10
        versionName = "0.3.1"

        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
        vectorDrawables { useSupportLibrary = true }
    }

    // --- Signing ------------------------------------------------------------
    //
    // Release builds are signed with a key whose details live OUTSIDE the repo
    // (so the password never lands in git history). The lookup order is:
    //
    //   1. keystore.properties file in the project root (gitignored)
    //      ─ used for local dev releases on your own machine
    //   2. Environment variables PROMPTLY_STORE_FILE / PROMPTLY_STORE_PASSWORD /
    //      PROMPTLY_KEY_ALIAS / PROMPTLY_KEY_PASSWORD
    //      ─ used by CI (GitHub Actions) — secrets injected via repo secrets
    //
    // If NEITHER source provides signing info, the release variant is still
    // built but UNSIGNED (Gradle emits a warning; the APK can't be installed
    // until signed with `apksigner`). This is intentional so debug builds and
    // PR CI runs don't fail for lack of a keystore.
    //
    // To set up your local release signing, see RELEASE.md → "One-time setup".

    val keystorePropertiesFile = rootProject.file("keystore.properties")
    val keystoreProperties = Properties()
    if (keystorePropertiesFile.exists()) {
        keystoreProperties.load(keystorePropertiesFile.inputStream())
    }

    signingConfigs {
        create("release") {
            // Lookup: keystore.properties first, then env vars
            storeFile = file(
                keystoreProperties.getProperty("storeFile")
                    ?: System.getenv("PROMPTLY_STORE_FILE")
                    ?: ""
            ).takeIf { it.exists() }
            storePassword = keystoreProperties.getProperty("storePassword")
                ?: System.getenv("PROMPTLY_STORE_PASSWORD")
            keyAlias = keystoreProperties.getProperty("keyAlias")
                ?: System.getenv("PROMPTLY_KEY_ALIAS")
            keyPassword = keystoreProperties.getProperty("keyPassword")
                ?: System.getenv("PROMPTLY_KEY_PASSWORD")
            enableV1Signing = true   // JAR signing — needed for older Android
            enableV2Signing = true   // APK Signature Scheme v2 (Android 7+)
            enableV3Signing = true   // APK Signature Scheme v3 (Android 9+, key rotation)
        }
    }

    buildTypes {
        debug {
            applicationIdSuffix = ".debug"
            versionNameSuffix = "-debug"
            isMinifyEnabled = false
        }
        release {
            isMinifyEnabled = true
            isShrinkResources = true
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro"
            )
            // Only attach the signing config if a keystore is actually available.
            // Otherwise the build still succeeds but produces an unsigned APK,
            // which is fine for CI runs that don't have the secret.
            if (signingConfigs.getByName("release").storeFile?.exists() == true) {
                signingConfig = signingConfigs.getByName("release")
            }
            // Always emit the obfuscation mapping file — useful for crash
            // deobfuscation later (R8 output).
            // Path: app/build/outputs/mapping/release/mapping.txt
        }
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }

    kotlinOptions {
        jvmTarget = "17"
    }

    buildFeatures {
        compose = true
        buildConfig = true
    }

    packaging {
        resources {
            excludes += "/META-INF/{AL2.0,LGPL2.1}"
        }
    }

    // Room schema export (helpful for migrations)
    ksp {
        arg("room.schemaLocation", "$projectDir/schemas")
        arg("room.incremental", "true")
    }
}

dependencies {
    // Core
    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.core.splashscreen)
    implementation(libs.androidx.lifecycle.runtime.ktx)
    implementation(libs.androidx.lifecycle.viewmodel.compose)
    implementation(libs.androidx.lifecycle.runtime.compose)
    implementation(libs.androidx.activity.compose)

    // Compose
    implementation(platform(libs.androidx.compose.bom))
    implementation(libs.androidx.compose.ui)
    implementation(libs.androidx.compose.ui.graphics)
    implementation(libs.androidx.compose.ui.tooling.preview)
    implementation(libs.androidx.compose.material3)
    implementation(libs.androidx.compose.material.icons.extended)
    implementation(libs.androidx.compose.animation)
    debugImplementation(libs.androidx.compose.ui.tooling)

    // Navigation
    implementation(libs.androidx.navigation.compose)

    // Room
    implementation(libs.androidx.room.runtime)
    implementation(libs.androidx.room.ktx)
    ksp(libs.androidx.room.compiler)

    // Hilt
    implementation(libs.hilt.android)
    ksp(libs.hilt.compiler)
    implementation(libs.androidx.hilt.navigation.compose)
    implementation(libs.androidx.hilt.work)
    ksp(libs.androidx.hilt.compiler)

    // WorkManager
    implementation(libs.androidx.work.runtime.ktx)

    // DataStore
    implementation(libs.androidx.datastore.preferences)

    // Coil
    implementation(libs.coil.compose)

    // Coroutines
    implementation(libs.kotlinx.coroutines.android)

    // Serialization
    implementation(libs.kotlinx.serialization.json)

    // SAF DocumentFile
    implementation(libs.androidx.documentfile)

    // Testing
    testImplementation("junit:junit:4.13.2")
    testImplementation("org.jetbrains.kotlinx:kotlinx-coroutines-test:1.9.0")
    testImplementation("androidx.room:room-testing:2.6.1")
    testImplementation("com.google.truth:truth:1.4.4")
    androidTestImplementation("androidx.test.ext:junit:1.2.1")
    androidTestImplementation("androidx.test.espresso:espresso-core:3.6.1")
    androidTestImplementation(platform(libs.androidx.compose.bom))
    androidTestImplementation("androidx.compose.ui:ui-test-junit4")
    debugImplementation("androidx.compose.ui:ui-test-manifest")
}
