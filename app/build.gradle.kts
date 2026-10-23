plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
}

android {
    namespace = "sk.stopscroll"
    compileSdk = 35

    defaultConfig {
        applicationId = "sk.stopscroll"
        minSdk = 26          // Android 8.0 a novší
        targetSdk = 34
        versionCode = 1
        versionName = "1.0"
    }

    buildTypes {
        release {
            isMinifyEnabled = false
        }
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }

    kotlinOptions {
        jvmTarget = "17"
    }
}

// Žiadne ďalšie knižnice – appka používa len čistý Android.
