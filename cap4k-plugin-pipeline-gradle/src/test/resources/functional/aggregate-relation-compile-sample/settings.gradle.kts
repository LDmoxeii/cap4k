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

rootProject.name = "aggregate-relation-compile-sample"
include("demo-domain")
