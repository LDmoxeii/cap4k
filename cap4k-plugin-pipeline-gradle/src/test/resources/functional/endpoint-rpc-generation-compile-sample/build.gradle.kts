plugins {
    id("io.github.ldmoxeii.cap4k.pipeline")
    kotlin("jvm") version "2.2.20" apply false
}

subprojects {
    apply(plugin = "org.jetbrains.kotlin.jvm")
    apply(plugin = "java-library")
    extensions.configure<org.jetbrains.kotlin.gradle.dsl.KotlinJvmProjectExtension> { jvmToolchain(17) }
}

cap4k {
    project {
        basePackage.set("com.acme.rpc")
        contractModulePath.set("contract")
        adapterModulePath.set("provider-adapter")
        endpointClientModulePath.set("endpoint-client")
    }
    sources {
        designJson { files.from("design/design.json") }
    }
    generators {
        endpointRpc {
            serviceId.set("booking-service")
            operationNames.set(listOf("booking.create"))
        }
    }
}
