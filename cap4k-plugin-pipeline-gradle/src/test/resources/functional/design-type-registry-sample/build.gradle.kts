plugins {
    id("com.only4.cap4k.plugin.pipeline")
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
    generators {
        design {
            enabled.set(true)
        }
    }
    types {
        registryFile.set("iterate/type-registry.json")
    }
}
