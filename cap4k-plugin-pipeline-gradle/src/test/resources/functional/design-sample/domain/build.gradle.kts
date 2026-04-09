tasks.register("kspKotlin") {
    outputs.file(layout.projectDirectory.file("build/generated/ksp/main/resources/metadata/aggregate-Order.json"))

    doLast {
        val outputFile = layout.projectDirectory
            .file("build/generated/ksp/main/resources/metadata/aggregate-Order.json")
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
