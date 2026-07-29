# Local Composite Development

## Purpose

This page records the current opt-in workflow for developing cap4k and a Gradle consumer together without publishing cap4k first. It is contributor and integration guidance, not a default public-project setting.

Normal consumers and the official GitHub Template continue to use stable Maven Central versions. A sibling cap4k checkout changes resolution only when the developer explicitly supplies `cap4k.local.path`.

## Consumer Settings

Keep the consumer's normal plugin and dependency repositories unchanged, then add an optional included build in `settings.gradle.kts`:

```kotlin
pluginManagement {
    repositories {
        gradlePluginPortal()
        mavenCentral()
    }
}

dependencyResolutionManagement {
    repositories {
        mavenCentral()
    }
}

val cap4kLocalPath = providers.gradleProperty("cap4k.local.path")
if (cap4kLocalPath.isPresent) {
    includeBuild(cap4kLocalPath.get())
}
```

Do not provide a default sibling path and do not detect `../cap4k` automatically. Without the property, the project resolves its declared stable version normally.

## Commands

From a consumer project next to the cap4k checkout:

```powershell
.\gradlew.bat build -Pcap4k.local.path=..\cap4k
```

The command-line shorthand is also available for ad-hoc work:

```powershell
.\gradlew.bat build --include-build ..\cap4k
```

The property-backed settings form is the maintained contract because it is explicit, reviewable, and can be reused by IDE Gradle import.

## Verified Substitution

The focused consumer fixture intentionally declares the unavailable version `999.0.0-local` and does not use TestKit `withPluginClasspath()`. It proves the included build supplies:

- Gradle plugin `io.github.ldmoxeii.cap4k.pipeline`;
- `io.github.ldmoxeii:ddd-core`;
- `io.github.ldmoxeii:ddd-domain-repo-jpa`;
- `io.github.ldmoxeii:cap4k-ddd-jpa-starter`.

It runs `cap4kPlan`, Kotlin compilation, a Spring Boot smoke test, and dependency insight. Runtime substitution currently requires `group = io.github.ldmoxeii` and `artifactId = project.name`.

## Boundaries

- Do not add this property or a local path to the official GitHub Template defaults.
- Do not use Aliyun, another remote Snapshot repository, or `mavenLocal()` for same-machine co-development.
- If an artifact's publication coordinates stop matching its project coordinates, re-run the fixture and add only the minimum explicit dependency substitution required by evidence.
- Cross-machine consumption of unreleased builds is a separate future design problem; do not recreate a long-lived publish branch for it.
