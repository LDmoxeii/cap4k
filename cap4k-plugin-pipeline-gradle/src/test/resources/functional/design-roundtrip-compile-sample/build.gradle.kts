plugins {
    id("io.github.ldmoxeii.cap4k.pipeline")
}

val schemaScriptPath = layout.projectDirectory.file("schema.sql").asFile.absolutePath.replace("\\", "/")
val databaseName = "cap4k_roundtrip_" +
    rootProject.projectDir.absolutePath.hashCode().toUInt().toString(16)

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
                "jdbc:h2:mem:$databaseName;MODE=MySQL;DATABASE_TO_UPPER=false;" +
                    "INIT=RUNSCRIPT FROM '$schemaScriptPath'"
            )
            username.set("sa")
            password.set("secret")
            schema.set("PUBLIC")
            includeTables.set(listOf("order"))
            excludeTables.set(emptyList())
        }
        designJson {
            files.from("design/design.json")
        }
    }
    types {
        enumManifest {
            files.from("design/enums.json")
        }
        valueObjectManifest {
            files.from("design/value-objects.json")
        }
    }
    generators {
        aggregate {
        }
    }
}
