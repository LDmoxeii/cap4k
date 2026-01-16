package com.only4.cap4k.plugin.codegen.core

/**
 * Unifies tag alias normalization for aggregate/design generators.
 */
object TagAliasResolver {

    private val designTagAliasMap: Map<String, String> = mapOf(
        // Repository
        "repositories" to "repository",
        "repository" to "repository",
        "repos" to "repository",
        "repo" to "repository",

        // Factory
        "factories" to "factory",
        "factory" to "factory",
        "fac" to "factory",

        // Specification
        "specifications" to "specification",
        "specification" to "specification",
        "specs" to "specification",
        "spec" to "specification",
        "spe" to "specification",

        // Domain Event
        "domain_events" to "domain_event",
        "domain_event" to "domain_event",
        "d_e" to "domain_event",
        "de" to "domain_event",

        // Command
        "commands" to "command",
        "command" to "command",
        "cmd" to "command",

        // Query
        "queries" to "query",
        "query" to "query",
        "qry" to "query",

        // API Payload
        "api_payload" to "api_payload",
        "payload" to "api_payload",
        "request_payload" to "api_payload",
        "req_payload" to "api_payload",
        "request" to "api_payload",
        "req" to "api_payload",

        // Client (分布式/远程调用)
        "clients" to "client",
        "client" to "client",
        "cli" to "client",

        // Saga
        "saga" to "saga",
        "sagas" to "saga",

        // Validator
        "validators" to "validator",
        "validator" to "validator",
        "validater" to "validator",
        "validate" to "validator",

        // Integration Event
        "integration_events" to "integration_event",
        "integration_event" to "integration_event",
        "events" to "integration_event",
        "event" to "integration_event",
        "evt" to "integration_event",
        "i_e" to "integration_event",
        "ie" to "integration_event",

        // Domain Service
        "domain_service" to "domain_service",
        "domain_services" to "domain_service",
        "service" to "domain_service",
        "svc" to "domain_service",
    )

    private val aggregateTagAliasMap: Map<String, String> = mapOf(
        "entity" to "aggregate",
        "aggregate" to "aggregate",
        "entities" to "aggregate",
        "aggregates" to "aggregate",
        "schema" to "schema",
        "schemas" to "schema",
        "enum" to "enum",
        "enums" to "enum",
        "enumitem" to "enum_item",
        "enum_item" to "enum_item",
        "factories" to "factory",
        "factory" to "factory",
        "fac" to "factory",
        "specifications" to "specification",
        "specification" to "specification",
        "specs" to "specification",
        "spec" to "specification",
        "spe" to "specification",
        "domain_events" to "domain_event",
        "domain_event" to "domain_event",
        "d_e" to "domain_event",
        "de" to "domain_event",
        "domain_event_handlers" to "domain_event_handler",
        "domain_event_handler" to "domain_event_handler",
        "d_e_h" to "domain_event_handler",
        "deh" to "domain_event_handler",
        "domain_event_subscribers" to "domain_event_handler",
        "domain_event_subscriber" to "domain_event_handler",
        "d_e_s" to "domain_event_handler",
        "des" to "domain_event_handler",
        "domain_service" to "domain_service",
        "service" to "domain_service",
        "svc" to "domain_service",
    )

    fun normalizeDesignTag(name: String): String =
        designTagAliasMap[name.lowercase()] ?: name.lowercase()

    fun normalizeAggregateTag(name: String): String =
        aggregateTagAliasMap[name.lowercase()] ?: name
}
