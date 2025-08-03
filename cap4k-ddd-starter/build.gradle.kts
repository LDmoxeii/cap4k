plugins {
    // Apply the shared build logic from a convention plugin.
    // The shared code is located in `buildSrc/src/main/kotlin/kotlin-jvm.gradle.kts`.
    id("buildsrc.convention.kotlin-jvm")

    // Apply the Application plugin to add support for building an executable JVM application.
    application
}

dependencies {
    implementation(project(":ddd-core"))
    implementation(project(":ddd-distributed-snowflake"))
    implementation(project(":ddd-distributed-locker-jdbc"))
    implementation(project(":ddd-distributed-saga-jpa"))
    implementation(project(":ddd-domain-repo-jpa"))
    implementation(project(":ddd-domain-repo-jpa-querydsl"))
    implementation(project(":ddd-application-request-jpa"))
    implementation(project(":ddd-integration-event-http"))
    implementation(project(":ddd-integration-event-http-jpa"))
    implementation(project(":ddd-integration-event-rocketmq"))
    implementation(project(":ddd-integration-event-rabbitmq"))
    implementation(project(":ddd-domain-event-jpa"))
    implementation(project(":ddd-core"))

    compileOnly(libs.springBootStarter)
    compileOnly(libs.springTomcat)
    compileOnly(libs.springWeb)
    compileOnly(libs.springWebMvc)
    compileOnly(libs.springConfigurationProcessor)
    compileOnly(libs.jpa)
    compileOnly(libs.validation)

    compileOnly(libs.slf4j)

    implementation(libs.fastjson)
    implementation("org.jetbrains.kotlin:kotlin-reflect")

    // Add mockk test framework
    testImplementation(platform(libs.springBootDependencies))
    testImplementation(project(":ddd-core"))
    testImplementation(libs.springJdbc)
    testImplementation(libs.slf4j)

    testImplementation(libs.mockk)
    testImplementation(libs.mockkAgentJvm)

    // Add JUnit and Kotlin Test
    testImplementation("org.junit.jupiter:junit-jupiter:5.10.2")
    testImplementation("org.jetbrains.kotlin:kotlin-test")
    testRuntimeOnly("org.junit.platform:junit-platform-launcher")
}
