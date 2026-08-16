import org.jetbrains.kotlin.gradle.tasks.KotlinCompile

plugins {
    kotlin("jvm")
}

kotlin {
    jvmToolchain(17)
}

val cap4kAnalysisCompiler by configurations.creating

dependencies {
    implementation("io.github.ldmoxeii:cap4k-contract-api:0.6.0-dev")
    implementation("jakarta.validation:jakarta.validation-api:3.1.1")
    compileOnly("io.github.ldmoxeii:cap4k-analysis-metadata:0.6.0-dev")
    cap4kAnalysisCompiler("io.github.ldmoxeii:cap4k-plugin-code-analysis-compiler:0.6.0-dev")
}

tasks.named<KotlinCompile>("compileKotlin") {
    compilerOptions.freeCompilerArgs.addAll(providers.provider {
        cap4kAnalysisCompiler.resolve().map { "-Xplugin=${it.absolutePath}" }
    })
}

