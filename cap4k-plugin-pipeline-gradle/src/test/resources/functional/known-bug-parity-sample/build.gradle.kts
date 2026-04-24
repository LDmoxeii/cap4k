plugins {
    id("com.only4.cap4k.plugin.pipeline")
}

val schemaScriptPath = layout.projectDirectory.file("schema.sql").asFile.absolutePath.replace("\\", "/")
val dbFilePath = layout.buildDirectory.file("h2/demo").get().asFile.absolutePath.replace("\\", "/")

cap4k {
    project {
        basePackage.set("com.acme.demo")
        domainModulePath.set("demo-domain")
        applicationModulePath.set("demo-application")
        adapterModulePath.set("demo-adapter")
    }
    sources {
        db {
            enabled.set(true)
            url.set(
                "jdbc:h2:file:$dbFilePath;MODE=MySQL;DB_CLOSE_DELAY=-1;DATABASE_TO_UPPER=false;INIT=RUNSCRIPT FROM '$schemaScriptPath'"
            )
            username.set("sa")
            password.set("secret")
            schema.set("PUBLIC")
            includeTables.set(listOf("user_message"))
            excludeTables.set(emptyList())
        }
        designJson {
            enabled.set(true)
            files.from("design/design.json")
        }
    }
    generators {
        aggregate {
            enabled.set(true)
        }
        designCommand {
            enabled.set(true)
        }
        designQuery {
            enabled.set(true)
        }
        designQueryHandler {
            enabled.set(true)
        }
        designClient {
            enabled.set(true)
        }
        designClientHandler {
            enabled.set(true)
        }
        designValidator {
            enabled.set(true)
        }
        designApiPayload {
            enabled.set(true)
        }
        designDomainEvent {
            enabled.set(true)
        }
        designDomainEventHandler {
            enabled.set(true)
        }
    }
}

val normalizeKnownBugGeneratedSources by tasks.registering {
    dependsOn("cap4kGenerate")

    doLast {
        val generatedRoots = listOf(
            layout.projectDirectory.dir("demo-domain/src/main/kotlin").asFile,
            layout.projectDirectory.dir("demo-application/src/main/kotlin").asFile,
            layout.projectDirectory.dir("demo-adapter/src/main/kotlin").asFile,
        )

        generatedRoots
            .filter { it.exists() }
            .forEach { root ->
                root.walkTopDown()
                    .filter { it.isFile && it.extension == "kt" }
                    .forEach { file ->
                        val original = file.readText()
                        val normalized = original
                            .replace("\r\n", "\n")
                            .lineSequence()
                            .joinToString("\n") { it.trimEnd() }
                            .replace(Regex("\n{4,}"), "\n\n\n")
                            .trimEnd('\n') + "\n"
                        if (normalized != original.replace("\r\n", "\n")) {
                            file.writeText(normalized)
                        }
                    }
            }
    }
}

subprojects {
    tasks.matching { it.name == "compileKotlin" }.configureEach {
        dependsOn(rootProject.tasks.named("normalizeKnownBugGeneratedSources"))
    }
}
