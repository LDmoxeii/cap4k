plugins {
    id("buildsrc.convention.kotlin-jvm")
}
dependencies {
    implementation(project(":ddd-core"))

    implementation(libs.fastjson)
    implementation(kotlin("reflect"))

    implementation(libs.spring.amqp)

    implementation(libs.slf4j)

    testImplementation(platform(libs.junit.bom))
    testImplementation(libs.mockk) {
        exclude(group = "org.slf4j", module = "slf4j-api")
    }
    testImplementation("org.junit.jupiter:junit-jupiter-api")
    testImplementation("org.junit.jupiter:junit-jupiter-params")
    testRuntimeOnly("org.junit.platform:junit-platform-launcher")
    testRuntimeOnly("org.junit.jupiter:junit-jupiter-engine")

    testImplementation(libs.spring.amqp)
}
