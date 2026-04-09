plugins {
    id("com.only4.cap4k.plugin.pipeline")
}

cap4kPipeline {
    basePackage.set("com.acme.demo")
    applicationModulePath.set("demo-application")
    designFiles.from("design/design.json")
    kspMetadataDir.set("domain/build/generated/ksp/main/resources/metadata")
    templateOverrideDir.set("codegen/templates")
}
