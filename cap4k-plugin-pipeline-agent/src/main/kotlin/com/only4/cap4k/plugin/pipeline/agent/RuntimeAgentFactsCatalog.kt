package com.only4.cap4k.plugin.pipeline.agent

import com.only4.cap4k.plugin.pipeline.api.AgentRuntimeCapabilityFact
import com.only4.cap4k.plugin.pipeline.api.AgentRuntimeOwnership
import com.only4.cap4k.plugin.pipeline.api.AgentRuntimeProviderFact

object RuntimeAgentFactsCatalog {
    const val HTTP_PROVIDER_ID: String = "integration-event-transport.http"
    const val RABBITMQ_PROVIDER_ID: String = "integration-event-transport.rabbitmq"
    const val ROCKETMQ_PROVIDER_ID: String = "integration-event-transport.rocketmq"

    private const val INTEGRATION_EVENT_TRANSPORT_CAPABILITY_ID = "runtime.integration-event-transport"

    fun capabilities(): List<AgentRuntimeCapabilityFact> = listOf(
        capability(
            capabilityId = "runtime.core-dispatch",
            displayName = "Core Dispatch",
            implementationModule = "ddd-core",
            starterModule = "cap4k-ddd-core-starter",
        ),
        capability(
            capabilityId = "runtime.endpoint-http-provider",
            displayName = "Endpoint HTTP Provider",
            implementationModule = "ddd-endpoint-http",
            starterModule = "cap4k-ddd-endpoint-http-starter",
        ),
        endpointRpcCapability(
            capabilityId = "runtime.endpoint-rpc-provider",
            displayName = "Endpoint RPC Provider",
        ),
        endpointRpcCapability(
            capabilityId = "runtime.endpoint-rpc-consumer",
            displayName = "Endpoint RPC Consumer",
        ),
        capability(
            capabilityId = "runtime.identifier-allocation",
            displayName = "Identifier Allocation",
            implementationModule = "ddd-core",
            starterModule = "cap4k-ddd-core-starter",
        ),
        capability(
            capabilityId = "runtime.local-domain-event",
            displayName = "Local Domain Event",
            implementationModule = "ddd-core",
            starterModule = "cap4k-ddd-core-starter",
        ),
        capability(
            capabilityId = "runtime.jpa-persistence",
            displayName = "JPA Persistence",
            implementationModule = "ddd-domain-repo-jpa",
            starterModule = "cap4k-ddd-jpa-starter",
        ),
        capability(
            capabilityId = "runtime.reliable-command",
            displayName = "Reliable Command",
            implementationModule = "ddd-application-command-jpa",
            starterModule = "cap4k-ddd-command-jpa-starter",
        ),
        capability(
            capabilityId = "runtime.reliable-event",
            displayName = "Reliable Event",
            implementationModule = "ddd-domain-event-jpa",
            starterModule = "cap4k-ddd-domain-event-jpa-starter",
        ),
        AgentRuntimeCapabilityFact(
            capabilityId = INTEGRATION_EVENT_TRANSPORT_CAPABILITY_ID,
            displayName = "Integration Event Transport",
            ownership = AgentRuntimeOwnership(contractModule = "ddd-core"),
            providerIds = listOf(HTTP_PROVIDER_ID, RABBITMQ_PROVIDER_ID, ROCKETMQ_PROVIDER_ID),
        ),
    )

    fun providers(): List<AgentRuntimeProviderFact> = listOf(
        provider(
            providerId = HTTP_PROVIDER_ID,
            displayName = "HTTP Integration Event Transport",
            implementationModule = "ddd-integration-event-http",
            starterModule = "cap4k-ddd-integration-event-http-starter",
        ),
        provider(
            providerId = RABBITMQ_PROVIDER_ID,
            displayName = "RabbitMQ Integration Event Transport",
            implementationModule = "ddd-integration-event-rabbitmq",
            starterModule = "cap4k-ddd-integration-event-rabbitmq-starter",
        ),
        provider(
            providerId = ROCKETMQ_PROVIDER_ID,
            displayName = "RocketMQ Integration Event Transport",
            implementationModule = "ddd-integration-event-rocketmq",
            starterModule = "cap4k-ddd-integration-event-rocketmq-starter",
        ),
    )

    private fun capability(
        capabilityId: String,
        displayName: String,
        implementationModule: String,
        starterModule: String,
    ) = AgentRuntimeCapabilityFact(
        capabilityId = capabilityId,
        displayName = displayName,
        ownership = AgentRuntimeOwnership(
            contractModule = implementationModule,
            implementationModule = implementationModule,
            starterModule = starterModule,
        ),
    )

    private fun endpointRpcCapability(
        capabilityId: String,
        displayName: String,
    ) = AgentRuntimeCapabilityFact(
        capabilityId = capabilityId,
        displayName = displayName,
        ownership = AgentRuntimeOwnership(
            contractModule = "ddd-endpoint-rpc",
            implementationModule = "ddd-endpoint-rpc-http",
            starterModule = "cap4k-ddd-endpoint-rpc-http-starter",
        ),
    )

    private fun provider(
        providerId: String,
        displayName: String,
        implementationModule: String,
        starterModule: String,
    ) = AgentRuntimeProviderFact(
        providerId = providerId,
        capabilityId = INTEGRATION_EVENT_TRANSPORT_CAPABILITY_ID,
        displayName = displayName,
        ownership = AgentRuntimeOwnership(
            contractModule = "ddd-core",
            implementationModule = implementationModule,
            starterModule = starterModule,
        ),
    )
}