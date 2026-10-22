plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
    id("org.jetbrains.kotlin.plugin.compose")
}

android {
    namespace = "com.personal.msgforwarder"
    compileSdk = 35

    val runNumber = System.getenv("GITHUB_RUN_NUMBER")?.toIntOrNull() ?: 1

    defaultConfig {
        applicationId = "com.personal.msgforwarder"
        minSdk = 26
        targetSdk = 35
        versionCode = runNumber
        versionName = "1.0.$runNumber"
    }

    signingConfigs {
        getByName("debug") {
            storeFile = file("debug.keystore")
            storePassword = "android"
            keyAlias = "androiddebugkey"
            keyPassword = "android"
        }
    }

    buildTypes {
        debug {
            signingConfig = signingConfigs.getByName("debug")
        }
        release {
            isMinifyEnabled = false
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro"
            )
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
}

dependencies {
    // Compose BOM — single version for all Compose libs
    val composeBom = platform("androidx.compose:compose-bom:2024.10.00")
    implementation(composeBom)

    // Compose
    implementation("androidx.compose.ui:ui")
    implementation("androidx.compose.ui:ui-graphics")
    implementation("androidx.compose.ui:ui-tooling-preview")
    implementation("androidx.compose.material3:material3")
    implementation("androidx.activity:activity-compose:1.9.3")
    implementation("androidx.navigation:navigation-compose:2.8.3")

    // Lifecycle
    implementation("androidx.lifecycle:lifecycle-runtime-ktx:2.8.6")
    implementation("androidx.lifecycle:lifecycle-runtime-compose:2.8.6")

    // Google Play Services Auth (Google Sign-In)
    implementation("com.google.android.gms:play-services-auth:21.3.0")

    // OkHttp (Google Sheets & Drive REST APIs)
    implementation("com.squareup.okhttp3:okhttp:4.12.0")

    // WorkManager (Reliable offline background syncing)
    implementation("androidx.work:work-runtime-ktx:2.10.0")

    // Coroutines Play Services (.await() support for Google Tasks)
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-play-services:1.9.0")

    // Biometric Authentication
    implementation("androidx.biometric:biometric-ktx:1.2.0-alpha05")

    // Core
    implementation("androidx.core:core-ktx:1.13.1")

    // Debug
    debugImplementation("androidx.compose.ui:ui-tooling")
}
