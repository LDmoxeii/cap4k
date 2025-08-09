plugins {
    id("buildsrc.convention.kotlin-jvm")
}

dependencies {
    // Core API dependencies - expose to consumers
    api(project(":ddd-core"))
    api(project(":ddd-distributed-snowflake"))
    api(project(":ddd-distributed-locker-jdbc"))
    api(project(":ddd-domain-repo-jpa"))
    api(project(":ddd-domain-repo-jpa-querydsl"))


    // Optional integration dependencies - implementation only
    implementation(project(":ddd-application-request-jpa"))
    implementation(project(":ddd-distributed-saga-jpa"))
    implementation(project(":ddd-domain-event-jpa"))

    implementation(project(":ddd-integration-event-http"))
    implementation(project(":ddd-integration-event-http-jpa"))

    implementation(project(":ddd-integration-event-rocketmq"))
    implementation(project(":ddd-integration-event-rabbitmq"))

    // API dependencies for consumers
    api(libs.fastjson)
    api(kotlin("reflect"))

    // Compile-only dependencies for Spring Boot autoconfiguration
    compileOnly(libs.spring.boot.starter)
    compileOnly(libs.spring.tomcat)
    compileOnly(libs.spring.web)
    compileOnly(libs.spring.web.mvc)
    compileOnly(libs.spring.configuration.processor)
    compileOnly(libs.jpa)
    compileOnly(libs.validation)
    compileOnly(libs.spring.amqp)
    compileOnly(libs.rocketmq)

    // Common dependencies
    implementation(libs.slf4j)
}
