plugins {
    kotlin("jvm") version "2.2.20"
}

kotlin {
    jvmToolchain(17)
}

dependencies {
    implementation("io.github.ldmoxeii:ddd-core:0.6.0-dev")
    implementation("io.github.ldmoxeii:ddd-domain-repo-jpa:0.6.0-dev")
    implementation("org.springframework:spring-context")
    implementation("org.springframework.data:spring-data-jpa")
    implementation("org.hibernate.orm:hibernate-core")
    implementation("jakarta.persistence:jakarta.persistence-api:3.1.0")
}

