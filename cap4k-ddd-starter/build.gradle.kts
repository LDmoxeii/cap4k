plugins {
    id("buildsrc.convention.kotlin-jvm")
    application
}

dependencies {
    // Project dependencies
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

    // Implementation dependencies
    implementation(libs.fastjson)
    implementation("org.jetbrains.kotlin:kotlin-reflect")

    // Compile-only dependencies
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
