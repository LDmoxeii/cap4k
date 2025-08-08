plugins {
    id("buildsrc.convention.kotlin-jvm")
    application
}

dependencies {
    // Project dependencies - API exposure
    api(project(":ddd-core"))

    // Project dependencies - Implementation only
    implementation(project(":ddd-domain-event-jpa"))

    // API dependencies exposed to consumers
    api(libs.fastjson)

    // Implementation dependencies
    implementation(kotlin("reflect"))

    // Compile-only dependencies for optional integration
    compileOnly(libs.jpa)
    compileOnly(libs.springMessaging)

    // Test dependencies
    testImplementation(libs.jpa)
    testImplementation(libs.springMessaging)
    testImplementation(libs.springData)
    testImplementation(libs.hibernateCore)
    testImplementation(libs.mockk)
    testImplementation(libs.mockkAgentJvm)
    testImplementation("org.junit.jupiter:junit-jupiter-api")
    testImplementation("org.junit.jupiter:junit-jupiter-engine")
    testImplementation("org.jetbrains.kotlin:kotlin-test")
    testImplementation("org.jetbrains.kotlin:kotlin-test-junit5")
}

