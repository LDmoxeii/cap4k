plugins {
    id("com.only4.cap4k.plugin.pipeline")
}

val generatedDesignFile = layout.buildDirectory.file("generated/design/design.json")

val writeDesignFixture = tasks.register("writeDesignFixture") {
    outputs.file(generatedDesignFile)
    doLast {
        val targetFile = generatedDesignFile.get().asFile
        targetFile.parentFile.mkdirs()
        targetFile.writeText(
            """
            [
              {
                "tag": "cmd",
                "package": "order.submit",
                "name": "SubmitOrder",
                "desc": "submit order command",
                "aggregates": ["Order"],
                "requestFields": [
                  { "name": "orderId", "type": "Long" },
                  { "name": "submittedAt", "type": "java.time.LocalDateTime" },
                  { "name": "externalId", "type": "java.util.UUID" },
                  { "name": "requestStatus", "type": "com.foo.Status" },
                  { "name": "address", "type": "Address", "nullable": true },
                  { "name": "address.city", "type": "String" },
                  { "name": "address.addressId", "type": "java.util.UUID" }
                ],
                "responseFields": [
                  { "name": "accepted", "type": "Boolean" },
                  { "name": "responseStatus", "type": "com.bar.Status" },
                  { "name": "result", "type": "Result", "nullable": true },
                  { "name": "result.receiptId", "type": "java.util.UUID" }
                ]
              },
              {
                "tag": "qry",
                "package": "order.read",
                "name": "FindOrder",
                "desc": "find order query",
                "aggregates": ["Order"],
                "requestFields": [
                  { "name": "orderId", "type": "Long" },
                  { "name": "lookupId", "type": "java.util.UUID" },
                  { "name": "requestStatus", "type": "com.foo.Status" }
                ],
                "responseFields": [
                  { "name": "responseStatus", "type": "com.bar.Status" },
                  { "name": "snapshot", "type": "Snapshot", "nullable": true },
                  { "name": "snapshot.updatedAt", "type": "java.time.LocalDateTime" },
                  { "name": "snapshot.snapshotId", "type": "java.util.UUID" }
                ]
              }
            ]
            """.trimIndent()
        )
    }
}

cap4k {
    project {
        basePackage.set("com.acme.demo")
        applicationModulePath.set("demo-application")
    }
    sources {
        designJson {
            enabled.set(true)
            files.from(generatedDesignFile)
        }
        kspMetadata {
            enabled.set(true)
            inputDir.set("domain/build/generated/ksp/main/resources/metadata")
        }
    }
    generators {
        design {
            enabled.set(true)
        }
    }
}

tasks.named("cap4kPlan") {
    dependsOn(writeDesignFixture)
}

tasks.named("cap4kGenerate") {
    dependsOn(writeDesignFixture)
}
