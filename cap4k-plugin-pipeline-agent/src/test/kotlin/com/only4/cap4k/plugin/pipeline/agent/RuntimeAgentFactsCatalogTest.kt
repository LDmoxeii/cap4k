package com.only4.cap4k.plugin.pipeline.agent

import com.only4.cap4k.plugin.pipeline.api.AgentRuntimeApplicationAssembly
import com.only4.cap4k.plugin.pipeline.api.AgentRuntimeFrameworkSupport
import com.only4.cap4k.plugin.pipeline.api.AgentRuntimeLiveStateSource
import com.only4.cap4k.plugin.pipeline.api.AgentRuntimeObservation
import com.only4.cap4k.plugin.pipeline.api.AgentRuntimeOperationalState
import com.only4.cap4k.plugin.pipeline.api.AgentRuntimeVerification
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class RuntimeAgentFactsCatalogTest {
    @Test
    fun `catalog publishes the confirmed capability identities and ownership`() {
        val facts = RuntimeAgentFactsCatalog.capabilities().associateBy { fact -> fact.capabilityId }

        assertEquals(
            setOf(
                "runtime.core-dispatch",
                "runtime.endpoint-http-provider",
                "runtime.identifier-allocation",
                "runtime.local-domain-event",
                "runtime.jpa-persistence",
                "runtime.reliable-command",
                "runtime.reliable-event",
                "runtime.integration-event-transport",
            ),
            facts.keys,
        )
        assertOwnership(facts.getValue("runtime.core-dispatch"), "ddd-core", "cap4k-ddd-core-starter")
        assertOwnership(
            facts.getValue("runtime.endpoint-http-provider"),
            "ddd-endpoint-http",
            "cap4k-ddd-endpoint-http-starter",
        )
        assertOwnership(facts.getValue("runtime.identifier-allocation"), "ddd-core", "cap4k-ddd-core-starter")
        assertOwnership(facts.getValue("runtime.local-domain-event"), "ddd-core", "cap4k-ddd-core-starter")
        assertOwnership(facts.getValue("runtime.jpa-persistence"), "ddd-domain-repo-jpa", "cap4k-ddd-jpa-starter")
        assertOwnership(
            facts.getValue("runtime.reliable-command"),
            "ddd-application-command-jpa",
            "cap4k-ddd-command-jpa-starter",
        )
        assertOwnership(
            facts.getValue("runtime.reliable-event"),
            "ddd-domain-event-jpa",
            "cap4k-ddd-domain-event-jpa-starter",
        )
        val transport = facts.getValue("runtime.integration-event-transport")
        assertEquals("ddd-core", transport.ownership.contractModule)
        assertEquals(null, transport.ownership.implementationModule)
        assertEquals(null, transport.ownership.starterModule)
        assertEquals(
            listOf(
                RuntimeAgentFactsCatalog.HTTP_PROVIDER_ID,
                RuntimeAgentFactsCatalog.RABBITMQ_PROVIDER_ID,
                RuntimeAgentFactsCatalog.ROCKETMQ_PROVIDER_ID,
            ),
            transport.providerIds,
        )
        facts.values.forEach { fact ->
            assertEquals(AgentRuntimeFrameworkSupport.SUPPORTED, fact.frameworkSupport)
            assertEquals(AgentRuntimeApplicationAssembly.UNKNOWN, fact.applicationAssembly)
            assertEquals(AgentRuntimeObservation.NOT_PERFORMED, fact.runtimeObservation)
            assertEquals(AgentRuntimeVerification.NOT_PERFORMED, fact.verification)
        }
    }

    @Test
    fun `transport provider facts use registry identities and static unknown states`() {
        val providers = RuntimeAgentFactsCatalog.providers()

        assertEquals(
            listOf(
                "integration-event-transport.http",
                "integration-event-transport.rabbitmq",
                "integration-event-transport.rocketmq",
            ),
            providers.map { provider -> provider.providerId },
        )
        assertEquals(
            listOf(
                "ddd-integration-event-http",
                "ddd-integration-event-rabbitmq",
                "ddd-integration-event-rocketmq",
            ),
            providers.map { provider -> provider.ownership.implementationModule },
        )
        providers.forEach { provider ->
            assertEquals("runtime.integration-event-transport", provider.capabilityId)
            assertEquals("ddd-core", provider.ownership.contractModule)
            assertEquals(AgentRuntimeFrameworkSupport.SUPPORTED, provider.frameworkSupport)
            assertEquals(AgentRuntimeApplicationAssembly.UNKNOWN, provider.applicationAssembly)
            assertEquals(AgentRuntimeObservation.NOT_PERFORMED, provider.runtimeObservation)
            assertEquals(AgentRuntimeOperationalState.UNKNOWN, provider.operationalState)
            assertEquals(AgentRuntimeVerification.NOT_PERFORMED, provider.verification)
            assertEquals(
                AgentRuntimeLiveStateSource.RUNTIME_PROVIDER_STATE_REGISTRY,
                provider.liveStateSource,
            )
        }
    }

    @Test
    fun `active facts contain no retired runtime identities`() {
        val retired = setOf("console", "snowflake", "locker", "saga")
        val activeIdentities = RuntimeAgentFactsCatalog.capabilities().map { fact -> fact.capabilityId } +
            RuntimeAgentFactsCatalog.providers().map { fact -> fact.providerId }

        activeIdentities.forEach { identity ->
            assertFalse(identity.split('.').any(retired::contains), identity)
        }
        assertTrue(RuntimeAgentFactsPolicy.diagnostics(
            com.only4.cap4k.plugin.pipeline.api.AgentRuntimeSection(
                status = com.only4.cap4k.plugin.pipeline.api.AgentSnapshotStatus.OK,
                capabilities = RuntimeAgentFactsCatalog.capabilities(),
                providers = RuntimeAgentFactsCatalog.providers(),
            )
        ).isEmpty())
    }

    private fun assertOwnership(
        fact: com.only4.cap4k.plugin.pipeline.api.AgentRuntimeCapabilityFact,
        implementationModule: String,
        starterModule: String,
    ) {
        assertEquals(implementationModule, fact.ownership.contractModule)
        assertEquals(implementationModule, fact.ownership.implementationModule)
        assertEquals(starterModule, fact.ownership.starterModule)
    }
}