plugins {
    id("buildsrc.convention.kotlin-jvm")
}

dependencies {
    // Project dependencies
    implementation(project(":ddd-core"))

    // Implementation dependencies
    implementation(libs.fastjson)
    implementation(kotlin("reflect"))

    // Compile-only dependencies
    compileOnly(libs.spring.context)
    compileOnly(libs.spring.auto.configuration)
    compileOnly(libs.spring.tomcat)
    compileOnly(libs.spring.jdbc)
    compileOnly(libs.spring.web)

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
    testImplementation(libs.spring.tomcat)
    testImplementation(libs.spring.jdbc)
    testImplementation(libs.spring.web)
}
