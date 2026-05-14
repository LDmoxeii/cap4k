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

includeBuild("__CAP4K_REPO_ROOT__")

rootProject.name = "aggregate-provider-persistence-compile-sample"
include("demo-domain", "demo-application", "demo-adapter")
