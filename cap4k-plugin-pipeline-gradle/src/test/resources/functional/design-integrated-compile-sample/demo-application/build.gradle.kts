plugins {
    kotlin("jvm") version "2.2.20"
}

kotlin {
    jvmToolchain(17)
}

dependencies {
    implementation(project(":demo-domain"))
    implementation("com.only4:ddd-core:0.5.0-SNAPSHOT")
    implementation("jakarta.validation:jakarta.validation-api:3.0.2")
    implementation("org.jetbrains.kotlin:kotlin-reflect:2.2.20")
    implementation("org.springframework:spring-context")
}

