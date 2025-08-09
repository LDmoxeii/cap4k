plugins {
    id("buildsrc.convention.kotlin-jvm")
}

dependencies {
    // Platform dependencies for BOM management
    api(platform(libs.spring.boot.dependencies))

    // Core API dependencies that are exposed to consumers
    api(libs.validation)

    // Compile-only dependencies - Spring framework
    compileOnly(libs.spring.context)
    compileOnly(libs.spring.tx)
    compileOnly(libs.spring.messaging)
    // 由 Spring-Data-JPA 替换而成
    compileOnly(libs.jpa)
    compileOnly(libs.aspectjweaver)

    // Common dependencies
    implementation(libs.slf4j)

    // Test dependencies
    testImplementation(platform(libs.junit.bom))
    testImplementation(libs.mockk) {
        exclude(group = "org.slf4j", module = "slf4j-api")
    }
    testImplementation("org.junit.jupiter:junit-jupiter-api")
    testImplementation("org.junit.jupiter:junit-jupiter-params")
    testRuntimeOnly("org.junit.platform:junit-platform-launcher")
    testRuntimeOnly("org.junit.jupiter:junit-jupiter-engine")

    testImplementation(libs.spring.context)
    testImplementation(libs.spring.tx)
    testImplementation(libs.spring.messaging)
    testImplementation(libs.jpa)
    testImplementation(libs.aspectjweaver)
}
