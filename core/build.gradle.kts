plugins {
    // Apply the shared build logic from a convention plugin.
    // The shared code is located in `buildSrc/src/main/kotlin/kotlin-jvm.gradle.kts`.
    id("buildsrc.convention.kotlin-jvm")

    // Apply the Application plugin to add support for building an executable JVM application.
    application
}

dependencies {
    api(platform(libs.springBootDependencies))
    compileOnly(libs.springContext)
    compileOnly(libs.springTx)
    compileOnly(libs.springMassaging)

    compileOnly(libs.aspectjweaver)
    compileOnly(libs.validation)

    implementation(libs.kotlinLogging)
}
