plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
    id("org.jetbrains.kotlin.plugin.compose")
}

android {
    namespace = "app.mychitas.peleshaon"
    compileSdk = 35

    defaultConfig {
        applicationId = "app.mychitas.peleshaon"
        minSdk = 26
        targetSdk = 35
        versionCode = 2
        versionName = "1.0.0-beta3"
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
    val composeBom = platform("androidx.compose:compose-bom:2024.09.03")
    implementation(composeBom)
    implementation("androidx.compose.ui:ui")
    implementation("androidx.compose.material3:material3")
    implementation("androidx.compose.ui:ui-tooling-preview")
    implementation("androidx.activity:activity-compose:1.9.2")
    implementation("androidx.lifecycle:lifecycle-viewmodel-compose:2.8.6")
    implementation("androidx.savedstate:savedstate-ktx:1.2.1")

    // Расчёт зманим и Шабат — общий с часами модуль
    implementation(project(":core"))

}
