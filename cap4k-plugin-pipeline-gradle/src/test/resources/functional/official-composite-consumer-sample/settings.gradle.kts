pluginManagement {
    repositories {
        gradlePluginPortal()
        mavenCentral()
    }
}

dependencyResolutionManagement {
    repositoriesMode.set(RepositoriesMode.FAIL_ON_PROJECT_REPOS)
    repositories {
        mavenCentral()
    }
}

val cap4kLocalPath = providers.gradleProperty("cap4k.local.path")
if (cap4kLocalPath.isPresent) {
    includeBuild(cap4kLocalPath.get())
}

rootProject.name = "official-composite-consumer-sample"
include("domain")
include("application")
include("adapter")
include("start")
