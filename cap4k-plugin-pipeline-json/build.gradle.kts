plugins {
    id("buildsrc.convention.kotlin-jvm")
}

dependencies {
    api(libs.jackson.module.kotlin)

    testImplementation(platform(libs.junit.bom))
    testImplementation("org.junit.jupiter:junit-jupiter-api")
    testRuntimeOnly("org.junit.jupiter:junit-jupiter-engine")
    testRuntimeOnly("org.junit.platform:junit-platform-launcher")
}
