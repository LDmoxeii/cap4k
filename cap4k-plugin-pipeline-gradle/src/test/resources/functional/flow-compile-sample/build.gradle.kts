plugins {
    id("com.only4.cap4k.plugin.pipeline")
}

val analysisDir = layout.buildDirectory.dir("cap4k-code-analysis")

tasks.register("compileKotlin") {
    outputs.dir(analysisDir)
    doLast {
        val outputDir = analysisDir.get().asFile
        outputDir.mkdirs()
        outputDir.resolve("nodes.json").writeText(
            """
            [
              {
                "id": "OrderController::submit",
                "name": "OrderController::submit",
                "fullName": "com.acme.demo.adapter.web.OrderController::submit",
                "type": "controllermethod"
              },
              {
                "id": "SubmitOrderCmd",
                "name": "SubmitOrderCmd",
                "fullName": "com.acme.demo.application.commands.SubmitOrderCmd",
                "type": "command"
              },
              {
                "id": "SubmitOrderHandler",
                "name": "SubmitOrderHandler",
                "fullName": "com.acme.demo.application.handlers.SubmitOrderHandler",
                "type": "commandhandler"
              }
            ]
            """.trimIndent()
        )
        outputDir.resolve("rels.json").writeText(
            """
            [
              {
                "fromId": "OrderController::submit",
                "toId": "SubmitOrderCmd",
                "type": "ControllerMethodToCommand"
              },
              {
                "fromId": "SubmitOrderCmd",
                "toId": "SubmitOrderHandler",
                "type": "CommandToCommandHandler"
              }
            ]
            """.trimIndent()
        )
    }
}

cap4kPipeline {
    basePackage.set("com.acme.demo")
    irAnalysisInputDirs.from(analysisDir)
    flowOutputDir.set("flows")
    templateOverrideDir.set("template-overrides")
}
