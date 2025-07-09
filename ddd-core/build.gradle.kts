plugins {
    id("buildsrc.convention.kotlin-jvm")
    application
}

dependencies {
    api(platform(libs.springBootDependencies))
    compileOnly(libs.springContext)
    compileOnly(libs.springTx)
    compileOnly(libs.springMassaging)
    compileOnly(libs.springData)

    compileOnly(libs.aspectjweaver)
    compileOnly(libs.validation)
    compileOnly(libs.slf4j)

    testImplementation(libs.springContext)
    testImplementation(libs.springTx)
    testImplementation(libs.springMassaging)
    testImplementation(libs.springData)

    testImplementation(libs.aspectjweaver)
    testImplementation(libs.validation)
    testImplementation(libs.slf4j)


    // Add mockk test framework
    testImplementation(libs.mockk)
    testImplementation(libs.mockkAgentJvm)

    // Add JUnit and Kotlin Test
    testImplementation("org.junit.jupiter:junit-jupiter:5.10.2")
    testImplementation("org.jetbrains.kotlin:kotlin-test")
    testRuntimeOnly("org.junit.platform:junit-platform-launcher")
}
