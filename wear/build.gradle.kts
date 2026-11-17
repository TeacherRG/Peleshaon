plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
    id("org.jetbrains.kotlin.plugin.compose")
}

android {
    namespace = "app.mychitas.peleshaon.wear"
    compileSdk = 35

    defaultConfig {
        // Тот же id, что у телефона: Google Play связывает приложение на телефоне и на часах
        applicationId = "app.mychitas.peleshaon.beta"
        minSdk = 30
        targetSdk = 35
        versionCode = 3
        versionName = "1.0.0-beta2-wear"
    }

    // Один ключ для всех тестовых сборок: иначе каждая сборка на GitHub подписана новым ключом,
    // и Android не ставит её поверх прошлой («Приложение не установлено»).
    signingConfigs {
        getByName("debug") {
            storeFile = rootProject.file("debug.keystore")
            storePassword = "android"
            keyAlias = "androiddebugkey"
            keyPassword = "android"
        }
    }

    buildTypes {
        getByName("debug") {
            signingConfig = signingConfigs.getByName("debug")
        }
    }

    buildFeatures {
        compose = true
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
    kotlinOptions {
        jvmTarget = "17"
    }
}

dependencies {
    implementation(project(":core"))

    val composeBom = platform("androidx.compose:compose-bom:2024.09.03")
    implementation(composeBom)
    implementation("androidx.compose.ui:ui")
    implementation("androidx.activity:activity-compose:1.9.2")
    implementation("androidx.wear.compose:compose-material:1.4.0")
    implementation("androidx.wear.compose:compose-foundation:1.4.0")
}
