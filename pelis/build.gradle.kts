plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
    id("org.jetbrains.kotlin.plugin.compose")
}

android {
    namespace = "com.tua.pelis"
    compileSdk = 35

    defaultConfig {
        applicationId = "com.tua.pelis"
        minSdk = 29
        targetSdk = 35
        versionCode = 5
        versionName = "1.4"
    }

    // Llave fija: así cada APK nuevo se instala encima del anterior
    // sin desinstalar (y sin perder el progreso guardado).
    signingConfigs {
        create("pelis") {
            storeFile = file("pelis.keystore")
            storePassword = "pelis123"
            keyAlias = "pelis"
            keyPassword = "pelis123"
        }
    }

    buildTypes {
        debug {
            signingConfig = signingConfigs.getByName("pelis")
        }
        release {
            isMinifyEnabled = false
            signingConfig = signingConfigs.getByName("pelis")
        }
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
    kotlinOptions {
        jvmTarget = "17"
        freeCompilerArgs += listOf("-opt-in=androidx.media3.common.util.UnstableApi")
    }
    buildFeatures {
        compose = true
    }
}

dependencies {
    val composeBom = platform("androidx.compose:compose-bom:2024.12.01")
    implementation(composeBom)
    implementation("androidx.core:core-ktx:1.15.0")
    implementation("androidx.activity:activity-compose:1.9.3")
    implementation("androidx.lifecycle:lifecycle-runtime-compose:2.8.7")
    implementation("androidx.lifecycle:lifecycle-runtime-ktx:2.8.7")
    implementation("androidx.compose.ui:ui")
    implementation("androidx.compose.foundation:foundation")
    implementation("androidx.compose.material3:material3")
    implementation("androidx.compose.material:material-icons-extended")
    implementation("androidx.media3:media3-exoplayer:1.5.1")
    implementation("androidx.media3:media3-ui:1.5.1")
}
