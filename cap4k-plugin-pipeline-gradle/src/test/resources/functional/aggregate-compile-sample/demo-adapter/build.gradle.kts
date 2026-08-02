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
    implementation("io.github.ldmoxeii:ddd-domain-repo-jpa:0.6.0-dev")
    implementation("jakarta.persistence:jakarta.persistence-api:3.1.0")
    implementation("org.springframework:spring-context")
    implementation("org.springframework.data:spring-data-jpa")

    testImplementation(platform("org.springframework.boot:spring-boot-dependencies:3.5.6"))
    testImplementation("org.junit.jupiter:junit-jupiter")
    testImplementation("org.springframework:spring-test")
    testImplementation("org.springframework:spring-webmvc")
    testImplementation("jakarta.servlet:jakarta.servlet-api")
    testRuntimeOnly("org.junit.platform:junit-platform-launcher")
}

tasks.test {
    useJUnitPlatform()
}
