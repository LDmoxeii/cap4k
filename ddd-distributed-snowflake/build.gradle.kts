plugins {
    id("buildsrc.convention.kotlin-jvm")
}

dependencies {
    // Platform dependencies
    implementation(platform(libs.spring.boot.dependencies))

    // Compile-only dependencies
    compileOnly(libs.spring.jdbc)
    compileOnly(libs.hibernate.core)

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

    testImplementation(libs.spring.jdbc)
    testImplementation(libs.hibernate.core)


}
