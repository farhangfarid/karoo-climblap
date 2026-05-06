plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.android)
}

android {
    namespace = "com.example.karoo_climblap"
    // karoo-ext targets SDK 34. compileSdk must be >= the library's targetSdk
    // to avoid compilation errors. minSdk stays 30 (Karoo 3 runs Android 11).
    compileSdk = 34

    defaultConfig {
        applicationId = "com.example.karoo_climblap"
        minSdk = 30
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
    // Karoo extension SDK — check https://github.com/hammerheadnav/karoo-ext/packages/2175616
    // for the latest version and replace 1.1.9 if needed.
    implementation("io.hammerhead:karoo-ext:1.1.9")

    implementation("androidx.core:core-ktx:1.9.0")
    implementation("androidx.appcompat:appcompat:1.6.1")
    implementation("com.google.android.material:material:1.8.0")

    // Coroutines — used for the background sensor-stream loop
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-android:1.7.3")
}
