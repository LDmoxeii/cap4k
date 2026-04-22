import com.only4.cap4k.plugin.pipeline.api.BootstrapMode

plugins {
    id("com.only4.cap4k.plugin.pipeline")
}

cap4k {
    bootstrap {
        enabled.set(true)
        preset.set("ddd-multi-module")
        mode.set(BootstrapMode.PREVIEW_SUBTREE)
        previewDir.set("bootstrap-preview")
        projectName.set("only-danmuku")
        basePackage.set("edu.only4.danmuku")
        modules {
            domainModuleName.set("only-danmuku-domain")
            applicationModuleName.set("only-danmuku-application")
            adapterModuleName.set("only-danmuku-adapter")
            startModuleName.set("only-danmuku-start")
        }
        slots {
            root.from("codegen/bootstrap-slots/root")
            modulePackage("domain").from("codegen/bootstrap-slots/domain-package")
            modulePackage("start").from("codegen/bootstrap-slots/start-package")
            moduleResources("start").from("codegen/bootstrap-slots/start-resources")
        }
    }
}
