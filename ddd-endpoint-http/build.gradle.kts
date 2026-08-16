plugins {
    id("buildsrc.convention.kotlin-jvm")
}

dependencies {
    api(platform(libs.spring.boot.dependencies))
    api(project(":cap4k-contract-api"))
    api(libs.spring.web.mvc)

    testImplementation(platform(libs.junit.bom))
    testImplementation("org.junit.jupiter:junit-jupiter-api")
    testRuntimeOnly("org.junit.jupiter:junit-jupiter-engine")
    testRuntimeOnly("org.junit.platform:junit-platform-launcher")
}
