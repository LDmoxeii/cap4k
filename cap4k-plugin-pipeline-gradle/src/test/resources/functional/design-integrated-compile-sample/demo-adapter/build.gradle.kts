plugins {
    kotlin("jvm") version "2.2.20"
}

kotlin {
    jvmToolchain(17)
}

dependencies {
    implementation(project(":demo-domain"))
    implementation(project(":demo-application"))
    implementation("io.github.ldmoxeii:ddd-core:0.6.0-dev")
    implementation("org.springframework:spring-context")
}

