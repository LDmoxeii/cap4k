plugins {
    id("buildsrc.convention.kotlin-jvm")
}

dependencies {
    // Project dependencies - API exposure
    api(project(":ddd-core"))

    // Project dependencies - Implementation only
    implementation(project(":ddd-domain-event-jpa"))

    implementation(libs.fastjson)
    implementation(kotlin("reflect"))

    // Compile-only dependencies for optional integration
    compileOnly(libs.jpa)
    compileOnly(libs.spring.messaging)


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

    testImplementation(libs.jpa)
    testImplementation(libs.spring.messaging)
}

