package com.only4.cap4k.codeanalysis.core.model

data class Relationship(
    val fromId: String,
    val toId: String,
    val type: RelationshipType,
    val label: String? = null
)

enum class RelationshipType {
    ControllerMethodToCommand,
    ControllerMethodToQuery,
    ControllerMethodToCli,
    EndpointToCommand,
    EndpointToQuery,
    EndpointToCli,
    CommandSenderMethodToCommand,
    QuerySenderMethodToQuery,
    CliSenderMethodToCli,
    CommandToCommandHandler,
    QueryToQueryHandler,
    CliToCliHandler,
    CommandHandlerToAggregate,
    AggregateToEntityMethod,
    EntityMethodToEntityMethod,
    EntityMethodToDomainEvent,
    DomainEventToHandler,
    DomainEventHandlerToCommand,
    DomainEventHandlerToQuery,
    DomainEventHandlerToCli,
    DomainEventToIntegrationEvent,
    IntegrationEventToHandler,
    IntegrationEventHandlerToCommand,
    IntegrationEventHandlerToQuery,
    IntegrationEventHandlerToCli
}
