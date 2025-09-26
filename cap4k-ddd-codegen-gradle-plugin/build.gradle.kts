plugins {
    id("buildsrc.convention.kotlin-jvm")
    `java-gradle-plugin`
}

dependencies {
    // Gradle plugin API
    implementation(gradleApi())
    implementation(gradleKotlinDsl())

    // Database dependencies
    implementation("mysql:mysql-connector-java:8.0.33")
    implementation("org.postgresql:postgresql:42.7.2")

    // JSON processing
    implementation(libs.fastjson)

    // Kotlin reflection
    implementation(kotlin("reflect"))
}

gradlePlugin {
    plugins {
        create("cap4kDddCodegen") {
            id = "com.only4.cap4k.ddd.codegen"
            implementationClass = "com.only4.cap4k.gradle.codegen.Cap4kDddCodegenPlugin"
            displayName = "Cap4k DDD Code Generator"
            description = "Generates DDD code from database schema for Cap4k framework"
        }
    }
}
