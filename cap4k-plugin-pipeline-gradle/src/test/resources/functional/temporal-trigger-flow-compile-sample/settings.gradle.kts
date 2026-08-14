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

rootProject.name = "temporal-trigger-flow-compile-sample"
