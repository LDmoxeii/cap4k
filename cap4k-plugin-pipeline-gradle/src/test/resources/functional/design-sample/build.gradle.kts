plugins {
    id("com.only4.cap4k.plugin.pipeline")
}

cap4k {
    project {
        basePackage.set("com.acme.demo")
        applicationModulePath.set("demo-application")
        adapterModulePath.set("demo-adapter")
    }
    sources {
        designJson {
            enabled.set(true)
            files.from("design/design.json")
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
        designClient {
            enabled.set(true)
        }
        designClientHandler {
            enabled.set(true)
        }
    }
}
