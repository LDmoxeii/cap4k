plugins {
    id("buildsrc.convention.kotlin-jvm")
}

dependencies {
    implementation(project(":cap4k-plugin-code-analysis-core"))
    compileOnly("org.jetbrains.kotlin:kotlin-compiler-embeddable:2.2.20")

    testImplementation(platform(libs.junit.bom))
    testImplementation("org.junit.jupiter:junit-jupiter-api")
    testRuntimeOnly("org.junit.jupiter:junit-jupiter-engine")
    testRuntimeOnly("org.junit.platform:junit-platform-launcher")
    testImplementation(libs.kotlin.compile.testing)
    testImplementation("org.jetbrains.kotlin:kotlin-compiler-embeddable:2.2.20")
    testImplementation("org.jetbrains.kotlin:kotlin-annotation-processing-embeddable:2.2.20")
}
