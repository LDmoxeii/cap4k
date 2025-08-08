plugins {
    id("buildsrc.convention.kotlin-jvm")
    application
}

dependencies {
    // Project dependencies
    implementation(project(":ddd-core"))

    // Implementation dependencies
    implementation(libs.fastjson)
    implementation(kotlin("reflect"))

    // Compile-only dependencies
    compileOnly(libs.jpa)

    // Test dependencies
    testImplementation(libs.jpa)
    testImplementation(libs.mockk)
    testImplementation(libs.mockkAgentJvm)
    testImplementation("org.junit.jupiter:junit-jupiter:5.10.2")
    testImplementation("org.jetbrains.kotlin:kotlin-test")
    testRuntimeOnly("org.junit.platform:junit-platform-launcher")
}

