//<!-- [cap4j-ddd-codegen-maven-plugin:do-not-overwrite] -->

plugins {
    id("buildsrc.convention.kotlin-jvm")
    id("com.google.devtools.ksp") version "2.1.20-2.0.0"
}

dependencies {
    api(libs.spring.data)
    api(libs.spring.validation)
    api(project(":${artifactId}-domain"))

    api(libs.ddd.integration.event.http)
    api(libs.ddd.integration.event.http.jpa)

    api(libs.engine.common)

    implementation(libs.jimmer.core)
    compileOnly(libs.jimmer.sql)
    ksp(libs.jimmer.ksp)
}

kotlin {
    sourceSets.main {
        kotlin.srcDir("build/generated/ksp/main/kotlin")
    }
}
