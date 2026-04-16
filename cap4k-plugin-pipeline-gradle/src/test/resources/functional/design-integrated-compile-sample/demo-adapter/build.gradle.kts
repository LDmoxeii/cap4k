plugins {
    kotlin("jvm") version "2.2.20"
}

kotlin {
    jvmToolchain(17)
}

dependencies {
    implementation(project(":demo-domain"))
    implementation(project(":demo-application"))
    implementation("com.only4:ddd-core:0.4.2-SNAPSHOT")
    implementation("org.springframework:spring-context")
}
