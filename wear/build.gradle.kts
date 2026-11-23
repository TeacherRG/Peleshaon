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
        applicationId = "app.niflaot.peleshaon"
        minSdk = 30
        targetSdk = 35
        // Google Play: у телефона и часов с одним applicationId коды версий должны различаться.
        // Часы = 100000 + код телефона (телефон 7 → часы 100007).
        versionCode = 100007
        versionName = "1.0.0-beta4-wear"
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

    // Ключ загрузки Google Play (Play App Signing). В репозиторий не кладётся: путь и пароли —
    // из переменных окружения (секреты GitHub Actions) или из ~/.gradle/gradle.properties.
    // Нет ключа — release собирается неподписанным, как раньше. Подробно — PLAY_RELEASE.md.
    val uploadStore = (System.getenv("PELE_UPLOAD_STORE_FILE") ?: findProperty("pele.upload.storeFile") as String?)
        ?.takeIf { it.isNotBlank() && file(it).exists() }
    if (uploadStore != null) {
        signingConfigs.create("upload") {
            storeFile = file(uploadStore)
            storePassword = System.getenv("PELE_UPLOAD_STORE_PASSWORD") ?: findProperty("pele.upload.storePassword") as String?
            keyAlias = System.getenv("PELE_UPLOAD_KEY_ALIAS") ?: findProperty("pele.upload.keyAlias") as String?
            keyPassword = System.getenv("PELE_UPLOAD_KEY_PASSWORD") ?: findProperty("pele.upload.keyPassword") as String?
        }
    }

    buildTypes {
        getByName("debug") {
            signingConfig = signingConfigs.getByName("debug")
        }
        // Release output is built unsigned until the production upload key is configured.
        getByName("release") {
            isMinifyEnabled = false
            signingConfigs.findByName("upload")?.let { signingConfig = it }
            isShrinkResources = false
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
