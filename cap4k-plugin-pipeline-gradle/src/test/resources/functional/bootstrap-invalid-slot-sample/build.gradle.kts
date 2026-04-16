plugins {
    id("com.only4.cap4k.plugin.pipeline")
}

cap4k {
    project {
        basePackage.set("edu.only4.danmuku")
    }
    bootstrap {
        enabled.set(true)
        preset.set("ddd-multi-module")
        projectName.set("only-danmuku")
        basePackage.set("edu.only4.danmuku")
        modules {
            domainModuleName.set("only-danmuku-domain")
            applicationModuleName.set("only-danmuku-application")
            adapterModuleName.set("only-danmuku-adapter")
        }
        slots {
            moduleRoot("start").from("codegen/bootstrap-slots/start-root")
        }
    }
}
