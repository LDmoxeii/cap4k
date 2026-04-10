plugins {
    id("com.only4.cap4k.plugin.pipeline")
}

cap4k {
    project {
        basePackage.set("com.acme.demo")
    }
    sources {
        irAnalysis {
            enabled.set(true)
            inputDirs.from("analysis/app/build/cap4k-code-analysis")
        }
    }
    generators {
        flow {
            enabled.set(true)
            outputDir.set("flows")
        }
    }
    templates {
        overrideDirs.from("template-overrides")
    }
}
