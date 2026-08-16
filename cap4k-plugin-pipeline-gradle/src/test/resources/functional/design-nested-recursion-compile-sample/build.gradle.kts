plugins {
    id("io.github.ldmoxeii.cap4k.pipeline")
}

cap4k {
    project {
        basePackage.set("com.acme.demo")
        contractModulePath.set("demo-contract")
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
