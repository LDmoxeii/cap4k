plugins {
    id("com.only4.cap4k.plugin.pipeline")
}

cap4kPipeline {
    basePackage.set("com.acme.demo")
    irAnalysisInputDirs.from("analysis/app/build/cap4k-code-analysis")
    drawingBoardOutputDir.set("design")
    templateOverrideDir.set("template-overrides")
}
