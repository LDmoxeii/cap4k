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

// [cap4k-bootstrap:managed-begin:root-host]
rootProject.name = "only-danmuku"

include(":only-danmuku-domain")
include(":only-danmuku-application")
include(":only-danmuku-adapter")
// [cap4k-bootstrap:managed-end:root-host]
