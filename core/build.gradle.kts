plugins {
    id("org.jetbrains.kotlin.jvm")
}

java {
    sourceCompatibility = JavaVersion.VERSION_17
    targetCompatibility = JavaVersion.VERSION_17
}

kotlin {
    compilerOptions {
        jvmTarget.set(org.jetbrains.kotlin.gradle.dsl.JvmTarget.JVM_17)
    }
}

dependencies {
    // Расчёт зманим по методу Алтер Ребе (KosherJava)
    api("com.kosherjava:zmanim:3.1.0")
    testImplementation(kotlin("test"))
}

tasks.test { useJUnitPlatform() }
