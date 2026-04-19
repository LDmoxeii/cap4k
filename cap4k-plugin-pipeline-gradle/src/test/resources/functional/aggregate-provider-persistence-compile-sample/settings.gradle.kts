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

rootProject.name = "aggregate-provider-persistence-compile-sample"
include("demo-domain", "demo-application", "demo-adapter")
