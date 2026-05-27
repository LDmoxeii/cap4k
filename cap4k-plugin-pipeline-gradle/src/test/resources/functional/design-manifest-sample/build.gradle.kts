plugins {
    id("io.github.ldmoxeii.cap4k.pipeline")
}

cap4k {
    project {
        basePackage.set("com.acme.demo")
        applicationModulePath.set("demo-application")
    }
    sources {
        designJson {
            enabled.set(true)
            manifestFile.set("iterate/design-manifest.json")
        }
    }
}
