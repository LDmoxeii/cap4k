plugins {
    // Apply the shared build logic from a convention plugin.
    // The shared code is located in `buildSrc/src/main/kotlin/kotlin-jvm.gradle.kts`.
    id("buildsrc.convention.kotlin-jvm")

    // Apply the Application plugin to add support for building an executable JVM application.
    application
}

dependencies {
    implementation(project(":ddd-core"))
    compileOnly(libs.springContext)
    compileOnly(libs.springAotoConfiguration)
    compileOnly(libs.springTomcat)
    compileOnly(libs.springJdbc)
    compileOnly(libs.springWeb)

    compileOnly(libs.slf4j)

    implementation(libs.fastjson)
    implementation("org.jetbrains.kotlin:kotlin-reflect")

    // Add mockk test framework
    testImplementation(platform(libs.springBootDependencies))
    testImplementation(project(":ddd-core"))
    testImplementation(libs.springContext)
    testImplementation(libs.springAotoConfiguration)
    testImplementation(libs.springTomcat)
    testImplementation(libs.springJdbc)
    testImplementation(libs.springWeb)

    testImplementation(libs.slf4j)

    testImplementation(libs.mockk)
    testImplementation(libs.mockkAgentJvm)

    // Add JUnit and Kotlin Test
    testImplementation("org.junit.jupiter:junit-jupiter:5.10.2")
    testImplementation("org.jetbrains.kotlin:kotlin-test")
    testRuntimeOnly("org.junit.platform:junit-platform-launcher")

    // Add Spring Test
    testImplementation("org.springframework:spring-test")
    testImplementation("org.springframework.boot:spring-boot-test")
    testImplementation("org.springframework.boot:spring-boot-test-autoconfigure")
}
