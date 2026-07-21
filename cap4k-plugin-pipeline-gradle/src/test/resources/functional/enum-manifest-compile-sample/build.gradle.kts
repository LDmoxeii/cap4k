plugins {
    id("io.github.ldmoxeii.cap4k.pipeline")
}

cap4k {
    project {
        basePackage.set("com.acme.demo")
        domainModulePath.set("demo-domain")
    }
    types {
        enumManifest {
            files.from("design/enums.json")
        }
    }
}
