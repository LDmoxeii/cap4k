pluginManagement {
    repositories {
        gradlePluginPortal()
        mavenCentral()
    }
}

dependencyResolutionManagement {
    repositories {
        mavenCentral()
        maven {
            url = uri("https://maven.aliyun.com/repository/public")
        }
    }
}

includeBuild("__CAP4K_REPO_ROOT__")

rootProject.name = "design-compile-sample"
include("domain")
include("demo-application")
include("demo-adapter")
