plugins {
    // The Kotlin DSL plugin provides a convenient way to develop convention plugins.
    // Convention plugins are located in `src/main/kotlin`, with the file extension `.gradle.kts`,
    // and are applied in the project's `build.gradle.kts` files as required.
    `kotlin-dsl`
}

kotlin {
    jvmToolchain(17)
}

dependencies {
    // Add a dependency on the Kotlin Gradle plugin, so that convention plugins can apply it.
    implementation(libs.kotlin.gradle.plugin)
    // Add a dependency on the Kotlin Spring plugin for Spring framework integration.
    implementation(libs.kotlin.allopen.plugin)
    // Add a dependency on the Kotlin JPA plugin for JPA entity class generation.
    implementation(libs.kotlin.noarg.plugin)
}
