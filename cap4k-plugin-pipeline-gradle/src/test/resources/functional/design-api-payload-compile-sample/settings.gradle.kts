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

rootProject.name = "design-api-payload-compile-sample"
include("demo-adapter")
