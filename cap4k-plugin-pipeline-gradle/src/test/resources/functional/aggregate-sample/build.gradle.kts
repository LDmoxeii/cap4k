plugins {
    id("com.only4.cap4k.plugin.pipeline")
}

val schemaScriptPath = layout.projectDirectory.file("schema.sql").asFile.absolutePath.replace("\\", "/")
val dbFilePath = layout.buildDirectory.file("h2/demo").get().asFile.absolutePath.replace("\\", "/")

cap4kPipeline {
    basePackage.set("com.acme.demo")
    domainModulePath.set("demo-domain")
    adapterModulePath.set("demo-adapter")
    dbUrl.set(
        "jdbc:h2:file:$dbFilePath;MODE=MySQL;DB_CLOSE_DELAY=-1;DATABASE_TO_UPPER=false;INIT=RUNSCRIPT FROM '$schemaScriptPath'"
    )
    dbUsername.set("sa")
    dbPassword.set("")
    dbSchema.set("PUBLIC")
    dbIncludeTables.set(listOf("video_post"))
    dbExcludeTables.set(emptyList())
    templateOverrideDir.set("template-overrides")
}
