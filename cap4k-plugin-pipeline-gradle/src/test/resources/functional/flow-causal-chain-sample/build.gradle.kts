plugins {
    id("io.github.ldmoxeii.cap4k.pipeline")
}

cap4k {
    project {
        basePackage.set("com.acme.demo")
    }
    sources {
        irAnalysis {
            inputDirs.from("analysis/app/build/cap4k-code-analysis")
        }
    }
    layout {
        flow {
            outputRoot.set("flows")
        }
    }
    generators {
        flow {
        }
    }
    templates {
        overrideDirs.from("template-overrides")
    }
}
