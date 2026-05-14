plugins {
    kotlin("jvm") version "2.2.20"
}

kotlin {
    jvmToolchain(17)
}

dependencies {
    implementation("io.github.ldmoxeii:ddd-core:0.5.0-dev")
    implementation("io.github.ldmoxeii:ddd-domain-repo-jpa:0.5.0-dev")
    implementation("org.springframework:spring-context")
    implementation("org.springframework.data:spring-data-jpa")
    implementation(project(":demo-domain"))
    implementation(project(":demo-application"))
}

