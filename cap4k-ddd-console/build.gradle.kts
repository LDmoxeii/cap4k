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
    compileOnly(libs.springContext)
    compileOnly(libs.springAutoConfiguration)
    compileOnly(libs.springTomcat)
    compileOnly(libs.springJdbc)
    compileOnly(libs.springWeb)
    compileOnly(libs.slf4j)

    // Test dependencies - Platform
    testImplementation(platform(libs.springBootDependencies))

    // Test dependencies - Projects
    testImplementation(project(":ddd-core"))

    // Test dependencies - Spring
    testImplementation(libs.springContext)
    testImplementation(libs.springAutoConfiguration)
    testImplementation(libs.springTomcat)
    testImplementation(libs.springJdbc)
    testImplementation(libs.springWeb)
    testImplementation("org.springframework:spring-test")
    testImplementation("org.springframework.boot:spring-boot-test")
    testImplementation("org.springframework.boot:spring-boot-test-autoconfigure")

    // Test dependencies - Other
    testImplementation(libs.slf4j)

    // Test framework dependencies
    testImplementation(libs.mockk)
    testImplementation(libs.mockkAgentJvm)
    testImplementation("org.junit.jupiter:junit-jupiter:5.10.2")
    testImplementation("org.jetbrains.kotlin:kotlin-test")
    testRuntimeOnly("org.junit.platform:junit-platform-launcher")
}
