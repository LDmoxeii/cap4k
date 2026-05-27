plugins {
    kotlin("jvm") version "2.2.20"
}

kotlin {
    jvmToolchain(17)
}

dependencies {
    implementation("io.github.ldmoxeii:ddd-core:0.6.0-dev")
    implementation("jakarta.validation:jakarta.validation-api:3.0.2")
    implementation("org.jetbrains.kotlin:kotlin-reflect:2.2.20")
}

