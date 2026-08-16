import org.jetbrains.kotlin.gradle.tasks.KotlinCompile

plugins {
    kotlin("jvm")
}

kotlin {
    jvmToolchain(17)
}

val cap4kAnalysisCompiler by configurations.creating

dependencies {
    implementation("io.github.ldmoxeii:ddd-core:0.6.0-dev")
    implementation("org.springframework:spring-context:6.2.11")
    cap4kAnalysisCompiler("io.github.ldmoxeii:cap4k-plugin-code-analysis-compiler:0.6.0-dev")
}

tasks.named<KotlinCompile>("compileKotlin") {
    compilerOptions.freeCompilerArgs.addAll(providers.provider {
        cap4kAnalysisCompiler.resolve().map { "-Xplugin=${it.absolutePath}" }
    })
}

