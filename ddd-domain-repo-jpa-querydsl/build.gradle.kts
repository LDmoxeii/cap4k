plugins {
    id("buildsrc.convention.kotlin-jvm")
}
dependencies {
    // Project dependencies - API exposure
    implementation(project(":ddd-core"))
    implementation(project(":ddd-domain-repo-jpa"))

    // Project dependencies - Implementation only
    implementation(project(":ddd-domain-event-jpa"))

    // Implementation dependencies
    implementation(libs.fastjson)
    implementation(kotlin("reflect"))

    // Compile-only dependencies for optional integration
    compileOnly(libs.jpa)
    compileOnly(libs.querydsl)

    // Test dependencies
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
    testImplementation(libs.querydsl)

}

