plugins {
    id("com.only4.cap4k.plugin.pipeline")
}

tasks.register("kspKotlin") {
    outputs.file(layout.projectDirectory.file("domain/build/generated/ksp/main/resources/metadata/aggregate-Order.json"))

    doLast {
        val outputFile = layout.projectDirectory
            .file("domain/build/generated/ksp/main/resources/metadata/aggregate-Order.json")
            .asFile
        outputFile.parentFile.mkdirs()
        outputFile.writeText(
            """
            {
              "aggregateName": "Order",
              "aggregateRoot": {
                "className": "Order",
                "qualifiedName": "com.acme.demo.domain.aggregates.order.Order",
                "packageName": "com.acme.demo.domain.aggregates.order"
              }
            }
            """.trimIndent()
        )
    }
}

cap4kPipeline {
    basePackage.set("com.acme.demo")
    applicationModulePath.set("demo-application")
    designFiles.from("design/design.json")
    kspMetadataDir.set("domain/build/generated/ksp/main/resources/metadata")
    templateOverrideDir.set("codegen/templates")
}
