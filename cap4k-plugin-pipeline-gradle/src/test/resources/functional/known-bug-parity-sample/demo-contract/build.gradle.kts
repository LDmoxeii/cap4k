plugins {
    kotlin("jvm") version "2.2.20"
}

kotlin {
    jvmToolchain(17)
}

dependencies {
    implementation("io.github.ldmoxeii:cap4k-contract-api:0.6.0-dev")
    compileOnly("io.github.ldmoxeii:cap4k-analysis-metadata:0.6.0-dev")
}
