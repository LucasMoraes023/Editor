import org.jetbrains.kotlin.gradle.dsl.JvmTarget

// Módulo de Kotlin puro (sem Android): modelo de edição, histórico, JSON, plano de exportação e testes.
plugins {
    alias(libs.plugins.kotlin.jvm)
}

java {
    sourceCompatibility = JavaVersion.VERSION_17
    targetCompatibility = JavaVersion.VERSION_17
}

kotlin {
    compilerOptions { jvmTarget.set(JvmTarget.JVM_17) }
}

dependencies {
    testImplementation(libs.junit)
}
