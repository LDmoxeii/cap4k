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

    // Test dependencies - 提供完整的运行时依赖
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

    // 添加项目模块依赖，使得测试可以访问所有功能
    // Core API dependencies - expose to consumers
    testImplementation(project(":ddd-core"))
    testImplementation(project(":ddd-distributed-snowflake"))
    testImplementation(project(":ddd-distributed-locker-jdbc"))
    testImplementation(project(":ddd-domain-repo-jpa"))
    testImplementation(project(":ddd-domain-repo-jpa-querydsl"))


    // Optional integration dependencies - implementation only
    testImplementation(project(":ddd-application-request-jpa"))
    testImplementation(project(":ddd-distributed-saga-jpa"))
    testImplementation(project(":ddd-domain-event-jpa"))

    testImplementation(project(":ddd-integration-event-http"))
    testImplementation(project(":ddd-integration-event-http-jpa"))

    testImplementation(project(":ddd-integration-event-rocketmq"))
    testImplementation(project(":ddd-integration-event-rabbitmq"))
//    testImplementation(project(":ddd-core"))
//    testImplementation(project(":ddd-domain-repo-jpa"))
//    testImplementation(project(":ddd-domain-event-jpa"))
//    testImplementation(project(":ddd-application-request-jpa"))
//    testImplementation(project(":ddd-application-saga-jpa"))
//    testImplementation(project(":ddd-distributed-locker-jdbc"))
//    testImplementation(project(":ddd-distributed-snowflake"))
//    testImplementation(project(":ddd-integration-event-http"))
//    testImplementation(project(":ddd-integration-event-rabbitmq"))
//    testImplementation(project(":ddd-integration-event-rocketmq"))

    testImplementation("com.h2database:h2")
    testImplementation(kotlin("test"))
    testImplementation("org.junit.jupiter:junit-jupiter")
    testRuntimeOnly("org.junit.platform:junit-platform-launcher")
}
