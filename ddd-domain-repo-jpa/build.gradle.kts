plugins {
    // Apply the shared build logic from a convention plugin.
    // The shared code is located in `buildSrc/src/main/kotlin/kotlin-jvm.gradle.kts`.
    id("buildsrc.convention.kotlin-jvm")

    // Apply the Application plugin to add support for building an executable JVM application.
    application
}
dependencies {
    implementation(project(":ddd-core"))
    implementation(project(":ddd-domain-event-jpa"))

    implementation(libs.fastjson)
    implementation("org.jetbrains.kotlin:kotlin-reflect")

    compileOnly(libs.jpa)
    compileOnly(libs.springMassaging)

    // Add test dependencies
    testImplementation(libs.jpa)
    testImplementation(libs.springMassaging)
    testImplementation(libs.springData)
    testImplementation(libs.hibernateCore)
    testImplementation(libs.mockk)
    testImplementation(libs.mockkAgentJvm)
    testImplementation("org.junit.jupiter:junit-jupiter-api")
    testImplementation("org.junit.jupiter:junit-jupiter-engine")
    testImplementation("org.jetbrains.kotlin:kotlin-test")
    testImplementation("org.jetbrains.kotlin:kotlin-test-junit5")
}

