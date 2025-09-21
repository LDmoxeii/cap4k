//<!-- [cap4j-ddd-codegen-maven-plugin:do-not-overwrite] -->

plugins {
    kotlin("jvm")
}

group = "${groupId}"
version = "${version}"

dependencies {
    // Platform for version management
    api(platform(libs.springBootDependencies))

    // Kotlin standard library
    api(kotlin("stdlib"))
    api(kotlin("reflect"))

    // Validation
    api("jakarta.validation:jakarta.validation-api")

    // Utilities
    api("org.apache.commons:commons-lang3")

    // Test dependencies
    testImplementation(libs.mockk)
    testImplementation("org.junit.jupiter:junit-jupiter")
    testImplementation("org.jetbrains.kotlin:kotlin-test")
    testRuntimeOnly("org.junit.platform:junit-platform-launcher")
}

java {
    toolchain {
        languageVersion.set(JavaLanguageVersion.of(17))
    }
}

kotlin {
    jvmToolchain(17)
}

tasks.test {
    useJUnitPlatform()
}
