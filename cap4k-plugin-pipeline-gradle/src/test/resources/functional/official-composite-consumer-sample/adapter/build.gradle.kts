plugins {
    alias(libs.plugins.kotlin.jvm)
    alias(libs.plugins.kotlin.spring)
}

kotlin {
    jvmToolchain(17)
}

dependencies {
    implementation(platform(libs.spring.boot.dependencies))

    implementation(project(":application"))
    implementation(project(":domain"))
    implementation(libs.cap4k.ddd.core)
    implementation(libs.cap4k.ddd.domain.repo.jpa)
    implementation(libs.jakarta.persistence)
    implementation(libs.spring.context)
    implementation(libs.spring.data.jpa)
}
