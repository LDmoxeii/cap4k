import org.jetbrains.kotlin.gradle.tasks.KotlinCompile

plugins {
    kotlin("jvm")
    kotlin("plugin.spring")
}

kotlin {
    jvmToolchain(17)
}

val cap4kAnalysisCompiler by configurations.creating

dependencies {
    implementation(project(":endpoint-contract"))
    implementation(project(":provider-application"))
    implementation(platform("org.springframework.boot:spring-boot-dependencies:3.5.6"))
    implementation("io.github.ldmoxeii:ddd-endpoint-http:0.6.0-dev")
    implementation("io.github.ldmoxeii:cap4k-ddd-endpoint-http-starter:0.6.0-dev")
    implementation("org.springframework.boot:spring-boot-starter-web")
    implementation("org.springframework.boot:spring-boot-starter-validation")
    implementation("com.fasterxml.jackson.module:jackson-module-kotlin")
    implementation(kotlin("reflect"))

    cap4kAnalysisCompiler("io.github.ldmoxeii:cap4k-plugin-code-analysis-compiler:0.6.0-dev")

    testImplementation("org.springframework.boot:spring-boot-starter-test")
    testRuntimeOnly("org.junit.platform:junit-platform-launcher")
}

tasks.named<KotlinCompile>("compileKotlin") {
    compilerOptions.freeCompilerArgs.addAll(providers.provider {
        cap4kAnalysisCompiler.resolve().map { "-Xplugin=${it.absolutePath}" }
    })
}

tasks.test {
    useJUnitPlatform()
}

