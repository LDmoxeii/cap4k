plugins {
    kotlin("jvm") version "2.2.20"
}

kotlin {
    jvmToolchain(17)
}

dependencies {
    implementation("io.github.ldmoxeii:ddd-core:0.5.0-dev")
}

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
                "qualifiedName": "com.acme.demo.domain.order.Order",
                "packageName": "com.acme.demo.domain.order"
              }
            }
            """.trimIndent()
        )
    }
}

