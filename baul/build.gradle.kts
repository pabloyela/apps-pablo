plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
    id("org.jetbrains.kotlin.plugin.compose")
}

android {
    namespace = "com.tua.baul"
    compileSdk = 35

    defaultConfig {
        applicationId = "com.tua.baul"
        minSdk = 30
        targetSdk = 35
        versionCode = 11
        versionName = "2.8"
    }

    // Llave fija: cada APK nuevo se instala encima del anterior (¡y no se pierde la bóveda!)
    signingConfigs {
        create("baul") {
            storeFile = file("baul.keystore")
            storePassword = "baul1234"
            keyAlias = "baul"
            keyPassword = "baul1234"
        }
    }

    buildTypes {
        debug {
            signingConfig = signingConfigs.getByName("baul")
        }
        release {
            isMinifyEnabled = false
            signingConfig = signingConfigs.getByName("baul")
        }
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
    kotlinOptions {
        jvmTarget = "17"
        freeCompilerArgs += listOf(
            "-opt-in=androidx.compose.foundation.ExperimentalFoundationApi",
            "-opt-in=androidx.media3.common.util.UnstableApi"
        )
    }
    buildFeatures {
        compose = true
    }
}

dependencies {
    implementation(project(":comun"))
    val composeBom = platform("androidx.compose:compose-bom:2024.12.01")
    implementation(composeBom)
    implementation("androidx.core:core-ktx:1.15.0")
    implementation("androidx.activity:activity-compose:1.9.3")
    implementation("androidx.lifecycle:lifecycle-runtime-ktx:2.8.7")
    implementation("androidx.lifecycle:lifecycle-runtime-compose:2.8.7")
    implementation("androidx.compose.ui:ui")
    implementation("androidx.compose.foundation:foundation")
    implementation("androidx.compose.material3:material3")
    implementation("androidx.compose.material:material-icons-extended")
    implementation("androidx.exifinterface:exifinterface:1.3.7")
    implementation("androidx.media3:media3-exoplayer:1.5.1")
    implementation("androidx.media3:media3-ui:1.5.1")
    // Detección del sujeto para «Fondo automático» (el modelo lo descarga Google Play, ~200 KB en la app)
    implementation("com.google.android.gms:play-services-mlkit-subject-segmentation:16.0.0-beta1")
    implementation("com.google.android.gms:play-services-base:18.5.0")
}
