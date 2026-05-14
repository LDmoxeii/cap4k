plugins {
    `kotlin-dsl`
    `java-gradle-plugin`
}

dependencies {
    implementation(libs.kotlin.gradle.plugin)
    implementation(libs.kotlin.allopen.plugin)
    implementation(libs.kotlin.noarg.plugin)
    implementation(gradleApi())
    implementation(gradleKotlinDsl())

    testImplementation(kotlin("test"))
}

tasks.test {
    useJUnitPlatform()
}
