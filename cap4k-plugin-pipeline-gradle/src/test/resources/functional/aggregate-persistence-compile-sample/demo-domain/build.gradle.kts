plugins {
    kotlin("jvm") version "2.2.20"
}

kotlin {
    jvmToolchain(17)
}

dependencies {
    implementation("com.only4:ddd-core:0.6.1-SNAPSHOT")
    implementation("com.only4:ddd-domain-repo-jpa:0.6.1-SNAPSHOT")
    implementation("org.springframework:spring-context")
    implementation("org.springframework.data:spring-data-jpa")
    implementation("org.hibernate.orm:hibernate-core")
}

