package com.only4.cap4k.plugin.codeanalysis.core.model

data class Relationship(
    val fromId: String,
    val toId: String,
    val type: RelationshipType,
    val label: String? = null
)

enum class RelationshipType {
    ControllerMethodToCommand,
    ControllerMethodToQuery,
    ControllerMethodToCapability,
    TemporalTriggerMethodToCommand,
    EndpointHttpBindingToCommand,
    EndpointHttpBindingToQuery,
    QuerySenderMethodToQuery,
    CapabilitySenderMethodToCapability,
    ValidatorToQuery,
    CommandToCommandHandler,
    QueryToQueryHandler,
    CapabilityToCapabilityHandler,
    CommandHandlerToAggregate,
    CommandHandlerToEntityMethod,
    AggregateToEntityMethod,
    EntityMethodToEntityMethod,
    EntityMethodToDomainEvent,
    DomainEventToHandler,
    DomainEventHandlerToCommand,
    DomainEventHandlerToQuery,
    DomainEventHandlerToCapability,
    DomainEventToIntegrationEvent,
    IntegrationEventToHandler,
    IntegrationEventHandlerToCommand,
    IntegrationEventHandlerToQuery,
    IntegrationEventHandlerToCapability
}
