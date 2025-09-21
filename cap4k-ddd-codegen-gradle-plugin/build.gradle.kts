import java.time.Duration

plugins {
    kotlin("jvm")
    `java-gradle-plugin`
    `maven-publish`
}

group = "com.only4"
version = "0.2.12-SNAPSHOT"

dependencies {
    // Platform dependencies for consistent versioning
    api(platform(libs.spring.boot.dependencies))

    // Project dependencies
    implementation(project(":ddd-core"))

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

    // Template engine (for file generation)
    implementation("org.apache.velocity:velocity-engine-core:2.3")

    // File utilities
    implementation("commons-io:commons-io:2.11.0")
    implementation("org.apache.commons:commons-lang3:3.12.0")

    // Test dependencies
    testImplementation(libs.mockk)
    testImplementation("org.junit.jupiter:junit-jupiter:5.10.2")
    testImplementation("org.jetbrains.kotlin:kotlin-test")
    testRuntimeOnly("org.junit.platform:junit-platform-launcher")
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

val sourcesJar by tasks.creating(Jar::class) {
    archiveClassifier.set("sources")
    from(sourceSets.main.get().allSource)
}

publishing {
    publications {
        create<MavenPublication>("maven") {
            from(components["java"])
            artifact(sourcesJar)
            groupId = project.group.toString()
            artifactId = project.name
            version = project.version.toString()
        }
    }
    repositories {
        maven {
            name = "AliYunMaven"
            url = uri("https://packages.aliyun.com/67053c6149e9309ce56b9e9e/maven/cap4k")
            credentials {
                username = providers.gradleProperty("aliyun.maven.username").orNull ?: "defaultUsername"
                password = providers.gradleProperty("aliyun.maven.password").orNull ?: "defaultPassword"
            }
        }
    }
}

// Configure the plugin
java {
    toolchain {
        languageVersion.set(JavaLanguageVersion.of(17))
    }
}

kotlin {
    jvmToolchain(17)
}

tasks.test {
    useJUnitPlatform()

    maxHeapSize = "2g"
    timeout.set(Duration.ofMinutes(10))

    testLogging {
        events("passed", "skipped", "failed")
        exceptionFormat = org.gradle.api.tasks.testing.logging.TestExceptionFormat.FULL
        showCauses = true
        showExceptions = true
        showStackTraces = true
    }
}
