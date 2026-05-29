plugins {
    id("io.github.ldmoxeii.cap4k.pipeline")
}

cap4k {
    project {
        basePackage.set("com.acme.demo")
        adapterModulePath.set("demo-adapter")
    }
    sources {
        designJson {
            files.from("design/design.json")
        }
    }
    templates {
        conflictPolicy.set("OVERWRITE")
    }
}
