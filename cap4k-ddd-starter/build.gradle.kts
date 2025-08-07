plugins {
    id("buildsrc.convention.kotlin-jvm")
    application
}

dependencies {
    // Core API dependencies - expose to consumers
    api(project(":ddd-core"))
    api(project(":ddd-distributed-snowflake"))
    api(project(":ddd-distributed-locker-jdbc"))
    api(project(":ddd-distributed-saga-jpa"))
    api(project(":ddd-domain-repo-jpa"))
    api(project(":ddd-domain-repo-jpa-querydsl"))
    api(project(":ddd-application-request-jpa"))
    api(project(":ddd-integration-event-http"))
    api(project(":ddd-integration-event-http-jpa"))
    api(project(":ddd-domain-event-jpa"))

    // Optional integration dependencies - implementation only
    implementation(project(":ddd-integration-event-rocketmq"))
    implementation(project(":ddd-integration-event-rabbitmq"))

    // API dependencies for consumers
    api(libs.fastjson)
    api("org.jetbrains.kotlin:kotlin-reflect")

    // Compile-only dependencies for Spring Boot autoconfiguration
    compileOnly(libs.springBootStarter)
    compileOnly(libs.springTomcat)
    compileOnly(libs.springWeb)
    compileOnly(libs.springWebMvc)
    compileOnly(libs.springConfigurationProcessor)
    compileOnly(libs.jpa)
    compileOnly(libs.validation)
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
