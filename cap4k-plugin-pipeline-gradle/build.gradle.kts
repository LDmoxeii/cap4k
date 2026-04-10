import org.jetbrains.kotlin.gradle.dsl.JvmTarget

plugins {
    id("buildsrc.convention.kotlin-jvm")
    `java-gradle-plugin`
}

dependencies {
    implementation(gradleApi())
    implementation(gradleKotlinDsl())
    implementation(project(":cap4k-plugin-pipeline-api"))
    implementation(project(":cap4k-plugin-pipeline-core"))
    implementation(project(":cap4k-plugin-pipeline-generator-aggregate"))
    implementation(project(":cap4k-plugin-pipeline-generator-flow"))
    implementation(project(":cap4k-plugin-pipeline-renderer-api"))
    implementation(project(":cap4k-plugin-pipeline-renderer-pebble"))
    implementation(project(":cap4k-plugin-pipeline-source-db"))
    implementation(project(":cap4k-plugin-pipeline-source-design-json"))
    implementation(project(":cap4k-plugin-pipeline-source-ir-analysis"))
    implementation(project(":cap4k-plugin-pipeline-source-ksp-metadata"))
    implementation(project(":cap4k-plugin-pipeline-generator-design"))
    implementation(libs.gson)
    implementation(libs.h2)

    testImplementation(gradleTestKit())
    testImplementation(platform(libs.junit.bom))
    testImplementation("org.junit.jupiter:junit-jupiter-api")
    testRuntimeOnly("org.junit.jupiter:junit-jupiter-engine")
    testRuntimeOnly("org.junit.platform:junit-platform-launcher")
}

gradlePlugin {
    plugins {
        create("cap4kPipeline") {
            id = "com.only4.cap4k.plugin.pipeline"
            implementationClass = "com.only4.cap4k.plugin.pipeline.gradle.PipelinePlugin"
            displayName = "Cap4k Pipeline Plugin"
            description = "Runs the minimal Cap4k pipeline vertical slice."
        }
    }
}

kotlin {
    compilerOptions {
        jvmTarget.set(JvmTarget.JVM_17)
    }
}
