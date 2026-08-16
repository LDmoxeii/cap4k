plugins {
    kotlin("jvm") version "2.2.20" apply false
    kotlin("plugin.spring") version "2.2.20" apply false
    id("io.github.ldmoxeii.cap4k.pipeline")
}

cap4k {
    project {
        basePackage.set("com.acme.endpoint")
    }
    sources {
        irAnalysis {
            inputDirs.from(
                project(":endpoint-contract").layout.buildDirectory.dir("cap4k-code-analysis"),
                project(":provider-application").layout.buildDirectory.dir("cap4k-code-analysis"),
                project(":provider-adapter").layout.buildDirectory.dir("cap4k-code-analysis"),
            )
        }
    }
    layout {
        flow {
            outputRoot.set("flows")
        }
    }
    generators {
        flow {}
    }
    templates {
        overrideDirs.from("template-overrides")
    }
}
