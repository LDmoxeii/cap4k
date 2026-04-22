import com.only4.cap4k.plugin.pipeline.api.BootstrapMode

plugins {
    id("com.only4.cap4k.plugin.pipeline")
}

repositories {
    mavenCentral()
}

val bootstrapHostBanner = "host-owned-build-logic"

// [cap4k-bootstrap:managed-begin:root-host]
cap4k {
    bootstrap {
        enabled.set(true)
        preset.set("ddd-multi-module")
        conflictPolicy.set("OVERWRITE")
        mode.set(BootstrapMode.IN_PLACE)
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
        }
    }
}
// [cap4k-bootstrap:managed-end:root-host]
