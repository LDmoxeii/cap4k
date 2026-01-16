import org.jetbrains.kotlin.gradle.dsl.JvmTarget

plugins {
    id("buildsrc.convention.kotlin-jvm")
    `java-gradle-plugin`
}

dependencies {
    implementation(gradleApi())
    implementation(gradleKotlinDsl())
    implementation(libs.jackson.module.kotlin)
    implementation(project(":cap4k-plugin-codegen"))
}

gradlePlugin {
    plugins {
        create("cap4kFlowExport") {
            id = "com.only4.cap4k.plugin.codeanalysis.flow-export"
            implementationClass = "com.only4.cap4k.plugin.codeanalysis.flow.Cap4kFlowExportPlugin"
            displayName = "Cap4k Code Analysis Flow Export Plugin"
            description = "Exports Cap4k processing flows from code analysis metadata."
        }
    }
}

kotlin {
    compilerOptions {
        jvmTarget.set(JvmTarget.JVM_17)
    }
}
