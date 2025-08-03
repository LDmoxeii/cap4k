plugins {
    id("buildsrc.convention.kotlin-jvm")
    application
}
dependencies {
    // Project dependencies
    implementation(project(":ddd-core"))
    implementation(project(":ddd-domain-event-jpa"))
    implementation(project(":ddd-domain-repo-jpa"))

    // Implementation dependencies
    implementation(libs.fastjson)
    implementation("org.jetbrains.kotlin:kotlin-reflect")

    // Compile-only dependencies
    compileOnly(libs.jpa)
    compileOnly(libs.querydsl)

    // Test dependencies
    testImplementation(libs.jpa)
    testImplementation(libs.querydsl)
    testImplementation(libs.springData)
    testImplementation(libs.hibernateCore)
    testImplementation(libs.mockk)
    testImplementation(libs.mockkAgentJvm)
    testImplementation("org.junit.jupiter:junit-jupiter:5.10.2")
    testImplementation("org.jetbrains.kotlin:kotlin-test")
    testRuntimeOnly("org.junit.platform:junit-platform-launcher")
}

