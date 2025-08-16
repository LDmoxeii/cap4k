plugins {
    id("buildsrc.convention.kotlin-jvm")
    kotlin("kapt")
}

dependencies {
    kapt("org.springframework.boot:spring-boot-configuration-processor:3.1.12")

    api(project(":ddd-core"))
    api(project(":ddd-distributed-snowflake"))
    api(project(":ddd-distributed-locker-jdbc"))
    api(project(":ddd-domain-repo-jpa"))
    api(project(":ddd-domain-repo-jpa-querydsl"))

    implementation(project(":ddd-application-request-jpa"))
    implementation(project(":ddd-distributed-saga-jpa"))
    implementation(project(":ddd-domain-event-jpa"))

    compileOnly(project(":ddd-integration-event-http"))
    compileOnly(project(":ddd-integration-event-http-jpa"))
    compileOnly(project(":ddd-integration-event-rocketmq"))
    compileOnly(project(":ddd-integration-event-rabbitmq"))

    implementation(libs.fastjson)
    implementation(kotlin("reflect"))

    compileOnly(libs.spring.boot.starter)
    compileOnly(libs.spring.tomcat)
    compileOnly(libs.spring.web)
    compileOnly(libs.spring.web.mvc)
    compileOnly(libs.spring.configuration.processor)
    compileOnly(libs.jpa)
    compileOnly(libs.validation)
    compileOnly(libs.spring.amqp)
    compileOnly(libs.rocketmq)

    implementation(libs.slf4j)

    testImplementation(libs.spring.boot.starter.test) {
        exclude(group = "org.junit.vintage", module = "junit-vintage-engine")
    }
    testImplementation(libs.spring.boot.starter)
    testImplementation(libs.jpa)
    testImplementation(libs.spring.web)
    testImplementation(libs.spring.web.mvc)
    testImplementation(libs.spring.tomcat)
    testImplementation(libs.validation)
    testImplementation(libs.spring.configuration.processor)
    testImplementation(libs.spring.context)

    testImplementation(project(":ddd-core"))
    testImplementation(project(":ddd-distributed-snowflake"))
    testImplementation(project(":ddd-distributed-locker-jdbc"))
    testImplementation(project(":ddd-domain-repo-jpa"))
    testImplementation(project(":ddd-domain-repo-jpa-querydsl"))

    testImplementation(project(":ddd-application-request-jpa"))
    testImplementation(project(":ddd-distributed-saga-jpa"))
    testImplementation(project(":ddd-domain-event-jpa"))

    testImplementation(project(":ddd-integration-event-http"))
    testImplementation(project(":ddd-integration-event-http-jpa"))

    testImplementation(project(":ddd-integration-event-rocketmq"))
    testImplementation(project(":ddd-integration-event-rabbitmq"))

    testImplementation("com.h2database:h2")
    testImplementation(kotlin("test"))
    testImplementation("org.junit.jupiter:junit-jupiter")
    testRuntimeOnly("org.junit.platform:junit-platform-launcher")
}
