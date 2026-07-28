plugins {
    alias(libs.plugins.cap4k.pipeline)
}

cap4k {
    project {
        basePackage.set("com.example.demo")
        domainModulePath.set("domain")
        applicationModulePath.set("application")
        adapterModulePath.set("adapter")
    }
}
