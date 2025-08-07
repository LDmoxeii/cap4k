plugins {
    id("buildsrc.convention.kotlin-jvm")
    application
}
dependencies {
    // Project dependencies
    api(project(":ddd-core"))

    // Implementation dependencies
    implementation(libs.fastjson)
    implementation("org.jetbrains.kotlin:kotlin-reflect")
    implementation(libs.springTx)

    // Compile-only dependencies for optional integration
    compileOnly(libs.springAmqp)

    // Test dependencies
    testImplementation(libs.springAmqp)

    testImplementation(libs.mockk)
    testImplementation(libs.mockkAgentJvm)
    testImplementation("org.junit.jupiter:junit-jupiter:5.10.2")
    testImplementation("org.jetbrains.kotlin:kotlin-test")
    testRuntimeOnly("org.junit.platform:junit-platform-launcher")
}
