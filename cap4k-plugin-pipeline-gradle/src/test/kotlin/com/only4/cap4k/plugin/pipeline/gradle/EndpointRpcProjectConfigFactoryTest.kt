package com.only4.cap4k.plugin.pipeline.gradle

import com.only4.cap4k.plugin.pipeline.api.GeneratorConfig
import com.only4.cap4k.plugin.pipeline.api.ProjectConfig
import org.gradle.testfixtures.ProjectBuilder
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertThrows
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class EndpointRpcProjectConfigFactoryTest {
    @Test
    fun `endpoint rpc projects feature module and stable explicit options`() {
        val project = ProjectBuilder.builder().build()
        val extension = project.extensions.create("cap4kRpcTest", Cap4kExtension::class.java)
        extension.project.basePackage.set("com.acme")
        extension.project.contractModulePath.set("contract")
        extension.project.adapterModulePath.set("adapter")
        extension.project.endpointClientModulePath.set("endpoint-client")
        extension.generators.endpointRpc {
            serviceId.set(" booking-service ")
            operationNames.set(listOf("booking.create", "booking.cancel"))
        }

        val config = Cap4kProjectConfigFactory().build(project, extension)

        assertEquals("endpoint-client", config.modules["endpoint-client"])
        assertEquals("booking-service", config.generators.getValue("endpoint-rpc").options["serviceId"])
        assertEquals(listOf("booking.create", "booking.cancel"), config.generators.getValue("endpoint-rpc").options["operationNames"])
    }

    @Test
    fun `endpoint rpc rejects duplicate explicit operation names`() {
        val project = ProjectBuilder.builder().build()
        val extension = project.extensions.create("cap4kRpcTest", Cap4kExtension::class.java)
        extension.project.basePackage.set("com.acme")
        extension.project.contractModulePath.set("contract")
        extension.project.adapterModulePath.set("adapter")
        extension.project.endpointClientModulePath.set("endpoint-client")
        extension.generators.endpointRpc {
            serviceId.set("booking-service")
            operationNames.set(listOf("booking.create", "booking.create"))
        }

        val error = assertThrows(IllegalArgumentException::class.java) {
            Cap4kProjectConfigFactory().build(project, extension)
        }
        assertEquals("generators.endpointRpc.operationNames must not contain duplicates.", error.message)
    }
    @Test
    fun `endpoint rpc rejects module roles that resolve to the same Gradle project`() {
        listOf("adapter", ":adapter", "./adapter", "adapter/").forEach { endpointClientPath ->
            val project = ProjectBuilder.builder().build()
            val extension = project.extensions.create("cap4kRpcTest", Cap4kExtension::class.java)
            extension.project.basePackage.set("com.acme")
            extension.project.contractModulePath.set("contract")
            extension.project.adapterModulePath.set(":adapter")
            extension.project.endpointClientModulePath.set(endpointClientPath)
            extension.generators.endpointRpc {
                serviceId.set("booking-service")
                operationNames.set(listOf("booking.create"))
            }

            val error = assertThrows(IllegalArgumentException::class.java) {
                Cap4kProjectConfigFactory().build(project, extension)
            }
            assertTrue(error.message!!.contains("roles [adapter, endpoint-client] conflict at ':adapter'"), error.message)
        }
    }

    @Test
    fun `endpoint client resources are generated before processResources`() {
        val root = ProjectBuilder.builder().withName("root").build()
        val client = ProjectBuilder.builder().withParent(root).withName("endpoint-client").build()
        client.pluginManager.apply("java")
        val generate = root.tasks.register("cap4kGenerateSources")
        val config = ProjectConfig(
            modules = mapOf("endpoint-client" to ":endpoint-client"),
            generators = mapOf("endpoint-rpc" to GeneratorConfig()),
        )

        wireGeneratedSourceCompilation(root, config, generate)

        val processResources = client.tasks.getByName("processResources")
        assertTrue(
            generate.get() in processResources.taskDependencies.getDependencies(processResources),
            "processResources must depend on cap4kGenerateSources for managed AutoConfiguration metadata",
        )
    }
}


