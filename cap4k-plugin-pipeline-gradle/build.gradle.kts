import org.gradle.api.tasks.testing.Test
import org.gradle.jvm.tasks.Jar
import org.jetbrains.kotlin.gradle.dsl.JvmTarget
import java.time.Duration

plugins {
    id("buildsrc.convention.kotlin-jvm")
    `java-gradle-plugin`
}

dependencies {
    implementation(gradleApi())
    implementation(gradleKotlinDsl())
    implementation(project(":cap4k-plugin-pipeline-api"))
    implementation(project(":cap4k-plugin-pipeline-agent"))
    implementation(project(":cap4k-plugin-pipeline-core"))
    implementation(project(":cap4k-plugin-pipeline-generator-aggregate"))
    implementation(project(":cap4k-plugin-pipeline-generator-drawing-board"))
    implementation(project(":cap4k-plugin-pipeline-generator-flow"))
    implementation(project(":cap4k-plugin-pipeline-renderer-api"))
    implementation(project(":cap4k-plugin-pipeline-renderer-pebble"))
    implementation(project(":cap4k-plugin-pipeline-source-db"))
    implementation(project(":cap4k-plugin-pipeline-source-design-json"))
    implementation(project(":cap4k-plugin-pipeline-source-enum-manifest"))
    implementation(project(":cap4k-plugin-pipeline-source-value-object-manifest"))
    implementation(project(":cap4k-plugin-pipeline-source-ir-analysis"))
    implementation(project(":cap4k-plugin-pipeline-generator-design"))
    implementation(project(":cap4k-plugin-pipeline-generator-types"))
    implementation(project(":cap4k-plugin-pipeline-json"))
    implementation(libs.h2)

    testImplementation(gradleTestKit())
    testImplementation(platform(libs.junit.bom))
    testImplementation(project(":cap4k-analysis-metadata"))
    testImplementation(project(":cap4k-plugin-code-analysis-compiler"))
    testImplementation(project(":cap4k-plugin-code-analysis-core"))
    testImplementation(project(":ddd-core"))
    testImplementation(project(":ddd-domain-repo-jpa"))
    testImplementation("org.junit.jupiter:junit-jupiter-api")
    testImplementation(libs.kotlin.compile.testing)
    testImplementation("org.jetbrains.kotlin:kotlin-compiler-embeddable:2.2.20")
    testImplementation("org.jetbrains.kotlin:kotlin-annotation-processing-embeddable:2.2.20")
    testImplementation(libs.spring.context)
    testImplementation(libs.jpa)
    testImplementation(libs.jackson.module.kotlin)
    testImplementation(libs.postgresql)
    testRuntimeOnly("org.junit.jupiter:junit-jupiter-engine")
    testRuntimeOnly("org.junit.platform:junit-platform-launcher")
}

gradlePlugin {
    plugins {
        create("cap4kPipeline") {
            id = "io.github.ldmoxeii.cap4k.pipeline"
            implementationClass = "com.only4.cap4k.plugin.pipeline.gradle.PipelinePlugin"
            displayName = "Cap4k Pipeline Plugin"
            description = "Runs the minimal Cap4k pipeline vertical slice."
        }
    }
}

kotlin {
    compilerOptions {
        jvmTarget.set(JvmTarget.JVM_17)
    }
}

tasks.withType<Test>().configureEach {
    timeout.set(Duration.ofMinutes(60))
}

tasks.named<Test>("test") {
    // Functional fixtures include this repository as a composite build. Build the
    // runtime artifacts they consume before TestKit starts another Gradle process,
    // otherwise both builds can write the same Kotlin incremental-cache directory.
    dependsOn(
        ":ddd-core:jar",
        ":ddd-domain-repo-jpa:jar",
        ":cap4k-ddd-core-starter:jar",
        ":cap4k-ddd-jpa-starter:jar",
        ":cap4k-plugin-code-analysis-compiler:jar",
        ":cap4k-plugin-code-analysis-core:jar",
    )
    outputs.upToDateWhen { false }
    outputs.cacheIf { false }
}

tasks.named<Jar>("jar") {
    manifest.attributes["Implementation-Version"] = project.version.toString()
}
val capabilityContractFactsOutput = providers.gradleProperty("capabilityContractFactsOutput")
    .map { project.file(it) }
    .orElse(rootProject.layout.buildDirectory.file("cap4k/capability-contract-facts.json").map { it.asFile })
    .get()

tasks.register<JavaExec>("exportCapabilityContractFacts") {
    group = "verification"
    description = "Exports code-derived Cap4k capability contract facts for repository validation."
    classpath = sourceSets.main.get().runtimeClasspath
    mainClass.set("com.only4.cap4k.plugin.pipeline.gradle.CapabilityContractFactsExporter")
    outputs.file(capabilityContractFactsOutput)
    args(capabilityContractFactsOutput.absolutePath)
}
