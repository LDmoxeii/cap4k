plugins {
    id("buildsrc.convention.kotlin-jvm")
    application
}

dependencies {
    // Project dependencies
    implementation(project(":ddd-core"))
    implementation(project(":ddd-domain-event-jpa"))

    // Implementation dependencies
    implementation(libs.fastjson)
    implementation("org.jetbrains.kotlin:kotlin-reflect")

    // Compile-only dependencies
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

