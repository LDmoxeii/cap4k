plugins {
    id("buildsrc.convention.kotlin-jvm")
    application
}

dependencies {
    // Platform dependencies for BOM management
    api(platform(libs.springBootDependencies))

    // Core API dependencies that are exposed to consumers
    api(libs.validation)
    api(libs.slf4j)

    // Compile-only dependencies - Spring framework
    compileOnly(libs.springContext)
    compileOnly(libs.springTx)
    compileOnly(libs.springMessaging)
    compileOnly(libs.springData)
    compileOnly(libs.aspectjweaver)

    // Test dependencies - Spring framework
    testImplementation(libs.springContext)
    testImplementation(libs.springTx)
    testImplementation(libs.springMessaging)
    testImplementation(libs.springData)

    // Test dependencies - Other
    testImplementation(libs.aspectjweaver)
    testImplementation(libs.validation)
    testImplementation(libs.slf4j)

    // Test framework dependencies
    testImplementation(libs.mockk)
    testImplementation(libs.mockkAgentJvm)
    testImplementation("org.junit.jupiter:junit-jupiter:5.10.2")
    testImplementation("org.jetbrains.kotlin:kotlin-test")
    testRuntimeOnly("org.junit.platform:junit-platform-launcher")
}
