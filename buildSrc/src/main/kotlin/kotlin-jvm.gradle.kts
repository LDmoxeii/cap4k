// The code in this file is a convention plugin - a Gradle mechanism for sharing reusable build logic.
package buildsrc.convention

import org.gradle.api.tasks.testing.logging.TestLogEvent
import java.time.Duration

plugins {
    kotlin("jvm")
    kotlin("plugin.spring")
    kotlin("plugin.jpa")
    `maven-publish`
    signing
}

group = "io.github.ldmoxeii"
version = "0.5.0-SNAPSHOT"

java {
    withSourcesJar()
    withJavadocJar()
}

publishing {
    repositories {
        maven {
            name = "CentralPortal"
            val releasesRepoUrl = uri("https://ossrh-staging-api.central.sonatype.com/service/local/staging/deploy/maven2/")
            val snapshotsRepoUrl = uri("https://central.sonatype.com/repository/maven-snapshots/")
            url = if (version.toString().endsWith("-SNAPSHOT")) snapshotsRepoUrl else releasesRepoUrl
            credentials {
                username = providers.gradleProperty("central.username").orNull ?: System.getenv("CENTRAL_USERNAME")
                password = providers.gradleProperty("central.password").orNull ?: System.getenv("CENTRAL_PASSWORD")
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
                    groupId = project.group.toString()
                    artifactId = project.name
                    version = project.version.toString()
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
            }
        }
    }

    signing {
        val signingKey = providers.gradleProperty("signingKey").orNull ?: System.getenv("SIGNING_KEY")
        val signingPassword = providers.gradleProperty("signingPassword").orNull ?: System.getenv("SIGNING_PASSWORD")
        setRequired {
            !version.toString().endsWith("-SNAPSHOT") && gradle.taskGraph.allTasks.any { it.name.startsWith("publish") }
        }
        if (!signingKey.isNullOrBlank()) {
            useInMemoryPgpKeys(signingKey, signingPassword)
            sign(publishing.publications)
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

