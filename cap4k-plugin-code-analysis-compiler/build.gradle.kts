plugins {
    id("buildsrc.convention.kotlin-jvm")
}

dependencies {
    implementation(project(":cap4k-plugin-code-analysis-core"))
    compileOnly("org.jetbrains.kotlin:kotlin-compiler-embeddable:2.2.20")
}
