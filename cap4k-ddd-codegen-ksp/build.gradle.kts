import org.jetbrains.kotlin.gradle.dsl.JvmTarget

plugins {
    id("buildsrc.convention.kotlin-jvm")
}

dependencies {
    implementation(libs.ksp.symbol.processing.api)
    implementation(libs.gson)
    implementation(kotlin("stdlib"))
}

kotlin {
    compilerOptions {
        jvmTarget.set(JvmTarget.JVM_17)
    }
}
