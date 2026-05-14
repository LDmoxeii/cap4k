// The code in this file is a convention plugin - a Gradle mechanism for sharing reusable build logic.
package buildsrc.convention

import org.gradle.api.publish.maven.MavenPublication
import org.gradle.api.publish.maven.tasks.PublishToMavenRepository
import org.gradle.api.tasks.testing.Test
import org.gradle.api.tasks.testing.logging.TestLogEvent
import java.time.Duration

plugins {
    kotlin("jvm")
    kotlin("plugin.spring")
    kotlin("plugin.jpa")
    `maven-publish`
    signing
}

fun MavenPublication.applyProjectCoordinates() {
    groupId = project.group.toString()
    artifactId = project.name
    version = project.version.toString()
}

fun MavenPublication.configurePomMetadata() {
    pom {
        name.set(project.name)
        description.set("cap4k module ${project.name}")
        url.set("https://github.com/LDmoxeii/cap4k")
        licenses {
            license {
                name.set("MIT License")
                url.set("https://github.com/LDmoxeii/cap4k/blob/master/LICENSE")
            }
        }
        developers {
            developer {
                id.set("LDmoxeii")
                name.set("LDmoxeii")
            }
        }
        scm {
            connection.set("scm:git:https://github.com/LDmoxeii/cap4k.git")
            developerConnection.set("scm:git:https://github.com/LDmoxeii/cap4k.git")
            url.set("https://github.com/LDmoxeii/cap4k")
        }
    }
}

val releaseVersionInput = providers.gradleProperty(CentralReleaseVersion.releaseVersionProperty).orNull
    ?: System.getenv(CentralReleaseVersion.releaseVersionEnvironment)
val centralUsername = providers.gradleProperty("central.username").orNull ?: System.getenv("CENTRAL_USERNAME")
val centralPassword = providers.gradleProperty("central.password").orNull ?: System.getenv("CENTRAL_PASSWORD")
val signingKey = providers.gradleProperty("signingKey").orNull ?: System.getenv("SIGNING_KEY")
val signingPassword = providers.gradleProperty("signingPassword").orNull ?: System.getenv("SIGNING_PASSWORD")
val isCentralRelease = CentralReleaseVersion.isReleaseBuild(releaseVersionInput)

group = CentralReleaseVersion.groupId
version = CentralReleaseVersion.resolve(releaseVersionInput)

java {
    withSourcesJar()
    withJavadocJar()
}

publishing {
    publications.withType<MavenPublication>().configureEach {
        applyProjectCoordinates()
        configurePomMetadata()
        versionMapping {
            usage("java-api") {
                fromResolutionOf("runtimeClasspath")
            }
            usage("java-runtime") {
                fromResolutionResult()
            }
        }
    }
    repositories {
        maven {
            name = "CentralPortal"
            url = uri("https://ossrh-staging-api.central.sonatype.com/service/local/staging/deploy/maven2/")
            credentials {
                username = centralUsername
                password = centralPassword
            }
        }
    }
}

afterEvaluate {
    if (!pluginManager.hasPlugin("java-gradle-plugin")) {
        publishing {
            publications {
                create<MavenPublication>("maven") {
                    from(components["java"])
                }
            }
        }
    }

    signing {
        setRequired {
            isCentralRelease && gradle.taskGraph.allTasks.any { task ->
                task is PublishToMavenRepository &&
                    CentralPublishTaskPolicy.isCentralPortalPublishTask(task.name) &&
                    !CentralPublishTaskPolicy.isPluginMarkerCentralPortalPublishTask(task.name)
            }
        }
        if (!signingKey.isNullOrBlank()) {
            useInMemoryPgpKeys(signingKey, signingPassword)
        }
        sign(publishing.publications)
    }
    tasks.withType<PublishToMavenRepository>().configureEach {
        if (CentralPublishTaskPolicy.isPluginMarkerCentralPortalPublishTask(name)) {
            enabled = false
            return@configureEach
        }

        if (CentralPublishTaskPolicy.isCentralPortalPublishTask(name)) {
            enabled = isCentralRelease
        }
    }
}

kotlin {
    jvmToolchain(17)
}

tasks.test {
    enabled = true
}

tasks.withType<Test>().configureEach {
    useJUnitPlatform()
    timeout.set(Duration.ofMinutes(10))
    jvmArgs(
        "-Xmx2g",
        "-Xms512m",
        "-XX:MaxMetaspaceSize=512m",
    )
    testLogging {
        events(
            TestLogEvent.FAILED,
            TestLogEvent.PASSED,
            TestLogEvent.SKIPPED
        )
    }
}

