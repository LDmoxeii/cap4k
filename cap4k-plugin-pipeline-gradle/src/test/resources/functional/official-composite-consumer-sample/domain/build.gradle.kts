plugins {
    alias(libs.plugins.kotlin.jvm)
    alias(libs.plugins.kotlin.spring)
    alias(libs.plugins.kotlin.jpa)
}

kotlin {
    jvmToolchain(17)
}

dependencies {
    implementation(platform(libs.spring.boot.dependencies))

    implementation(libs.cap4k.ddd.core)
    implementation(libs.jakarta.persistence)
    implementation(libs.jackson.annotations)
    implementation(libs.jackson.databind)
    implementation(libs.jackson.module.kotlin)
    implementation(libs.spring.context)
}
