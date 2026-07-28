plugins {
    alias(libs.plugins.kotlin.jvm)
    alias(libs.plugins.kotlin.spring)
}

kotlin {
    jvmToolchain(17)
}

dependencies {
    implementation(platform(libs.spring.boot.dependencies))

    implementation(project(":domain"))
    implementation(libs.cap4k.ddd.core)
    implementation(libs.jakarta.validation)
    implementation(libs.spring.context)
}
