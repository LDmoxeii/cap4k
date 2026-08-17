pluginManagement { repositories { gradlePluginPortal(); mavenCentral() } }
dependencyResolutionManagement { repositories { mavenCentral() } }
includeBuild("__CAP4K_REPO_ROOT__")
rootProject.name = "endpoint-rpc-generation-compile-sample"
include("contract", "provider-adapter", "provider-start", "endpoint-client", "consumer", "consumer-start")

