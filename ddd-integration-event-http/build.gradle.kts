plugins {
    id("buildsrc.convention.kotlin-jvm")
    application
}
dependencies {
    // Project dependencies
    implementation(project(":ddd-core"))

    // Implementation dependencies
    implementation(libs.fastjson)
    implementation("org.jetbrains.kotlin:kotlin-reflect")
    implementation(libs.springMessaging)
    implementation(libs.springWeb)

    // Compile-only dependencies
    compileOnly(libs.slf4j)

    // Test dependencies
    testImplementation(libs.springMessaging)
    testImplementation(libs.springWeb)
    testImplementation(libs.slf4j)
    testImplementation(libs.mockk)
    testImplementation(libs.mockkAgentJvm)
    testImplementation("org.junit.jupiter:junit-jupiter:5.10.2")
    testImplementation("org.jetbrains.kotlin:kotlin-test")
    testRuntimeOnly("org.junit.platform:junit-platform-launcher")
}
