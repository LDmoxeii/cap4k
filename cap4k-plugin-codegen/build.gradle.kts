import org.jetbrains.kotlin.gradle.dsl.JvmTarget

plugins {
    id("buildsrc.convention.kotlin-jvm")
    `java-gradle-plugin`
}

dependencies {
    implementation(gradleApi())
    implementation(gradleKotlinDsl())
    implementation(project(":cap4k-plugin-codegen-ksp"))

    runtimeOnly(libs.mysql)
    runtimeOnly(libs.postgresql)
    implementation(libs.gson)
    implementation(libs.pebble)
    implementation(kotlin("reflect"))
}

gradlePlugin {
    plugins {
        create("cap4kCodegen") {
            id = "com.only4.cap4k.plugin.codegen"
            implementationClass = "com.only4.cap4k.plugin.codegen.gradle.CodegenPlugin"
            displayName = "Cap4k DDD Codegen Plugin"
            description = "Generates Cap4k DDD skeleton code from design and schema inputs."
        }
    }
}

kotlin {
    compilerOptions {
        freeCompilerArgs.add("-Xcontext-parameters")
        jvmTarget.set(JvmTarget.JVM_17)
    }
}
