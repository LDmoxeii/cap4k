plugins {
    id("buildsrc.convention.kotlin-jvm")
}

dependencies {
    api(project(":cap4k-ddd-core-starter"))
    api(project(":ddd-endpoint-http"))
    api(libs.spring.web.mvc)
    api(libs.spring.tomcat)
    implementation(libs.spring.boot.starter)

    testImplementation(libs.spring.boot.starter.test) {
        exclude(group = "org.junit.vintage", module = "junit-vintage-engine")
    }
    testRuntimeOnly("org.junit.platform:junit-platform-launcher")
}
