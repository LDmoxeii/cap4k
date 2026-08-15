import org.jetbrains.kotlin.gradle.tasks.KotlinCompile

plugins {
    kotlin("jvm") version "2.2.20"
    id("io.github.ldmoxeii.cap4k.pipeline")
}

kotlin {
    jvmToolchain(17)
}

val cap4kAnalysisCompiler by configurations.creating

dependencies {
    cap4kAnalysisCompiler("io.github.ldmoxeii:cap4k-plugin-code-analysis-compiler:0.6.0-dev")
}

tasks.withType<KotlinCompile>().configureEach {
    compilerOptions.freeCompilerArgs.addAll(
        providers.provider {
            cap4kAnalysisCompiler.resolve().map { pluginJar ->
                "-Xplugin=${pluginJar.absolutePath}"
            }
        }
    )
}

val analysisOutputDir = layout.buildDirectory.dir("cap4k-code-analysis")

cap4k {
    project {
        basePackage.set("demo")
    }
    sources {
        irAnalysis {
            inputDirs.from(analysisOutputDir)
        }
    }
    layout {
        flow {
            outputRoot.set("flows")
        }
    }
    generators {
        flow {
        }
    }
    templates {
        overrideDirs.from("template-overrides")
    }
}
