plugins {
    id("com.only4.cap4k.plugin.pipeline")
}

val analysisDir = layout.buildDirectory.dir("cap4k-code-analysis")

tasks.register("compileKotlin") {
    outputs.dir(analysisDir)
    doLast {
        val outputDir = analysisDir.get().asFile
        outputDir.mkdirs()
        outputDir.resolve("nodes.json").writeText("""[]""")
        outputDir.resolve("rels.json").writeText("""[]""")
        outputDir.resolve("design-elements.json").writeText(
            """
            [
              {
                "tag": "validator",
                "package": "danmuku",
                "name": "DanmukuDeletePermission",
                "desc": "delete permission",
                "message": "no delete permission",
                "targets": ["CLASS"],
                "valueType": "Any",
                "parameters": [
                  {
                    "name": "danmukuIdField",
                    "type": "String",
                    "nullable": false,
                    "defaultValue": "danmukuId"
                  }
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
        irAnalysis {
            enabled.set(true)
            inputDirs.from(analysisDir)
        }
        designJson {
            enabled.set(true)
            files.from("design/drawing_board_validator.json")
        }
    }
    generators {
        drawingBoard {
            enabled.set(true)
        }
        designValidator {
            enabled.set(true)
        }
    }
}
