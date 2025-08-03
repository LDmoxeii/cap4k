plugins {
    id("buildsrc.convention.kotlin-jvm")
    application
}

dependencies {
    // Platform dependencies
    api(platform(libs.springBootDependencies))

    // Compile-only dependencies - Spring framework
    compileOnly(libs.springContext)
    compileOnly(libs.springTx)
    compileOnly(libs.springMessaging)
    compileOnly(libs.springData)

    // Compile-only dependencies - Other
    compileOnly(libs.aspectjweaver)
    compileOnly(libs.validation)
    compileOnly(libs.slf4j)

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
