plugins {
    id("io.github.ldmoxeii.cap4k.pipeline")
}

cap4k {
    project {
        basePackage.set("com.acme.demo")
        domainModulePath.set("demo-domain")
        applicationModulePath.set("demo-application")
    }
    sources {
        designJson {
            enabled.set(true)
            files.from("design/design.json")
        }
        kspMetadata {
            enabled.set(true)
            inputDir.set("demo-domain/build/generated/ksp/main/resources/metadata")
        }
    }
}
