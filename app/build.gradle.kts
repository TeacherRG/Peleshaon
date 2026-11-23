plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
    id("org.jetbrains.kotlin.plugin.compose")
}

android {
    namespace = "app.mychitas.peleshaon"
    compileSdk = 36

    defaultConfig {
        applicationId = "app.niflaot.peleshaon"
        minSdk = 26
        targetSdk = 36
        versionCode = 7
        versionName = "1.0.0-beta4"
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
        // Safe sideload variant: a different package ID means an old app with
        // an incompatible signing key can remain installed with all its data.
        // Does not share preferences or alarm schedules with the original app.
        create("qa") {
            initWith(getByName("debug"))
            applicationIdSuffix = ".qa"
            versionNameSuffix = "-parallel"
            matchingFallbacks += listOf("debug")
            signingConfig = signingConfigs.getByName("debug")
        }
        // Prepare a production variant. It intentionally remains unsigned until
        // a dedicated Play upload key is created and configured securely.
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
