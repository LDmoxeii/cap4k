plugins {
    kotlin("jvm") version "2.2.20"
}

kotlin {
    jvmToolchain(17)
}

dependencies {
    implementation(project(":demo-application"))
    implementation("io.github.ldmoxeii:ddd-core:0.5.0-dev")
    implementation("org.springframework:spring-context")
}

