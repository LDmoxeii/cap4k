plugins {
    id("buildsrc.convention.kotlin-jvm")
    application
}

dependencies {
    // Project dependencies
    implementation(project(":ddd-core"))

    // Compile-only dependencies
    compileOnly(libs.springJdbc)
    compileOnly(libs.slf4j)

    // Test dependencies - Platform
    testImplementation(platform(libs.springBootDependencies))

    // Test dependencies - Projects
    testImplementation(project(":ddd-core"))

    // Test dependencies - Other
    testImplementation(libs.springJdbc)
    testImplementation(libs.slf4j)

    // Test framework dependencies
    testImplementation(libs.mockk)
    testImplementation(libs.mockkAgentJvm)
    testImplementation("org.junit.jupiter:junit-jupiter:5.10.2")
    testImplementation("org.jetbrains.kotlin:kotlin-test")
    testRuntimeOnly("org.junit.platform:junit-platform-launcher")
}
