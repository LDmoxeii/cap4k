import org.jetbrains.kotlin.gradle.dsl.JvmTarget

plugins {
    id("buildsrc.convention.kotlin-jvm")
    `java-gradle-plugin`
}

dependencies {
    implementation(gradleApi())
    implementation(gradleKotlinDsl())
    implementation(libs.jackson.module.kotlin)

    testImplementation(gradleTestKit())
    testImplementation(platform(libs.junit.bom))
    testImplementation("org.junit.jupiter:junit-jupiter-api")
    testRuntimeOnly("org.junit.jupiter:junit-jupiter-engine")
    testRuntimeOnly("org.junit.platform:junit-platform-launcher")
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
