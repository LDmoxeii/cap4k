package com.only4.cap4k.plugin.codegen.context.design.builders

import com.only4.cap4k.plugin.codegen.context.ContextBuilder
import com.only4.cap4k.plugin.codegen.context.design.MutableDesignContext
import com.only4.cap4k.plugin.codegen.context.design.models.*
import com.only4.cap4k.plugin.codegen.context.design.models.common.PayloadField
import com.only4.cap4k.plugin.codegen.core.TagAliasResolver
import com.only4.cap4k.plugin.codegen.misc.toUpperCamelCase

class UnifiedDesignBuilder : ContextBuilder<MutableDesignContext> {

    override val order: Int = 20

    private val designTypeToGeneratorTags = mapOf(
        "domain_event" to listOf("domain_event", "domain_event_handler"),
        "query" to listOf("query", "query_handler"),
        "api_payload" to listOf("api_payload"),
        "client" to listOf("client", "client_handler"),
        "validator" to listOf("validator"),
    )

    override fun build(context: MutableDesignContext) {
        val designMap = mutableMapOf<String, MutableList<BaseDesign>>()

        context.designElementMap.forEach { (type, elements) ->
            val normalizedType = TagAliasResolver.normalizeDesignTag(type)

            elements.forEach {
                val design = buildDesign(normalizedType, it, context)

                val targetTags = designTypeToGeneratorTags[normalizedType] ?: listOf(normalizedType)

                targetTags.forEach { tag ->
                    designMap.computeIfAbsent(tag) { mutableListOf() }.add(design)
                }
            }
        }

        context.designMap.putAll(designMap)
    }

    private fun buildDesign(
        type: String,
        element: DesignElement,
        context: MutableDesignContext,
    ): BaseDesign {
        val aggregates = extractAggregates(element)

        val aggregateMetadataList = aggregates.mapNotNull { aggName ->
            context.aggregateMap[aggName]
        }

        val primaryAggregate = aggregates.firstOrNull()
        val primaryAggregateMetadata = aggregateMetadataList.firstOrNull()

        return when (type) {
            "command" -> buildCommandDesign(
                element, primaryAggregate, aggregates, primaryAggregateMetadata, aggregateMetadataList
            )

            "query" -> buildQueryDesign(
                element, primaryAggregate, aggregates, primaryAggregateMetadata, aggregateMetadataList
            )

            "api_payload" -> buildApiPayloadDesign(
                element, primaryAggregate, aggregates, primaryAggregateMetadata, aggregateMetadataList
            )

            "saga" -> buildSagaDesign(
                element, primaryAggregate, aggregates, primaryAggregateMetadata, aggregateMetadataList
            )

            "client" -> buildClientDesign(
                element, primaryAggregate, aggregates, primaryAggregateMetadata, aggregateMetadataList
            )

            "domain_service" -> buildDomainServiceDesign(
                element, primaryAggregate, aggregates, primaryAggregateMetadata, aggregateMetadataList
            )

            "integration_event" -> buildIntegrationEventDesign(
                element, primaryAggregate, aggregates, primaryAggregateMetadata, aggregateMetadataList
            )

            "domain_event" -> buildDomainEventDesign(
                element, primaryAggregate, aggregates, primaryAggregateMetadata, aggregateMetadataList
            )

            "validator" -> buildValidatorDesign(
                element, primaryAggregate, aggregates, primaryAggregateMetadata, aggregateMetadataList
            )

            else -> throw IllegalArgumentException("Unknown design type: $type")
        }
    }

    private fun extractAggregates(element: DesignElement): List<String> {
        val aggregatesFromArray = element.aggregates ?: emptyList()
        if (aggregatesFromArray.isNotEmpty()) {
            return aggregatesFromArray
        }

        return emptyList()
    }

    private fun buildCommandDesign(
        element: DesignElement,
        primaryAggregate: String?,
        aggregates: List<String>,
        primaryAggregateMetadata: AggregateInfo?,
        aggregateMetadataList: List<AggregateInfo>,
    ): CommandDesign =
        CommandDesign(
            type = "command",
            `package` = element.`package`,
            name = element.name,
            desc = element.desc,
            aggregate = primaryAggregate,
            aggregates = aggregates,
            primaryAggregateMetadata = primaryAggregateMetadata,
            aggregateMetadataList = aggregateMetadataList,
            requestFields = parsePayloadFields(element.metadata["requestFields"]),
            responseFields = parsePayloadFields(element.metadata["responseFields"]),
        )

    private fun buildQueryDesign(
        element: DesignElement,
        primaryAggregate: String?,
        aggregates: List<String>,
        primaryAggregateMetadata: AggregateInfo?,
        aggregateMetadataList: List<AggregateInfo>,
    ): QueryDesign =
        QueryDesign(
            type = "query",
            `package` = element.`package`,
            name = element.name,
            desc = element.desc,
            aggregate = primaryAggregate,
            aggregates = aggregates,
            primaryAggregateMetadata = primaryAggregateMetadata,
            aggregateMetadataList = aggregateMetadataList,
            requestFields = parsePayloadFields(element.metadata["requestFields"]),
            responseFields = parsePayloadFields(element.metadata["responseFields"]),
        )

    private fun buildApiPayloadDesign(
        element: DesignElement,
        primaryAggregate: String?,
        aggregates: List<String>,
        primaryAggregateMetadata: AggregateInfo?,
        aggregateMetadataList: List<AggregateInfo>,
    ): ApiPayloadDesign =
        ApiPayloadDesign(
            type = "api_payload",
            `package` = element.`package`,
            name = element.name,
            desc = element.desc,
            aggregate = primaryAggregate,
            aggregates = aggregates,
            primaryAggregateMetadata = primaryAggregateMetadata,
            aggregateMetadataList = aggregateMetadataList,
            requestFields = parsePayloadFields(element.metadata["requestFields"]),
            responseFields = parsePayloadFields(element.metadata["responseFields"]),
        )

    private fun buildSagaDesign(
        element: DesignElement,
        primaryAggregate: String?,
        aggregates: List<String>,
        primaryAggregateMetadata: AggregateInfo?,
        aggregateMetadataList: List<AggregateInfo>,
    ): SagaDesign =
        SagaDesign(
            type = "saga",
            `package` = element.`package`,
            name = element.name,
            desc = element.desc,
            aggregate = primaryAggregate,
            aggregates = aggregates,
            primaryAggregateMetadata = primaryAggregateMetadata,
            aggregateMetadataList = aggregateMetadataList,
        )

    private fun buildClientDesign(
        element: DesignElement,
        primaryAggregate: String?,
        aggregates: List<String>,
        primaryAggregateMetadata: AggregateInfo?,
        aggregateMetadataList: List<AggregateInfo>,
    ): ClientDesign =
        ClientDesign(
            type = "client",
            `package` = element.`package`,
            name = element.name,
            desc = element.desc,
            aggregate = primaryAggregate,
            aggregates = aggregates,
            primaryAggregateMetadata = primaryAggregateMetadata,
            aggregateMetadataList = aggregateMetadataList,
            requestFields = parsePayloadFields(element.metadata["requestFields"]),
            responseFields = parsePayloadFields(element.metadata["responseFields"]),
        )

    private fun buildDomainServiceDesign(
        element: DesignElement,
        primaryAggregate: String?,
        aggregates: List<String>,
        primaryAggregateMetadata: AggregateInfo?,
        aggregateMetadataList: List<AggregateInfo>,
    ): DomainServiceDesign =
        DomainServiceDesign(
            type = "domain_service",
            `package` = element.`package`,
            name = element.name,
            desc = element.desc,
            aggregate = primaryAggregate,
            aggregates = aggregates,
            primaryAggregateMetadata = primaryAggregateMetadata,
            aggregateMetadataList = aggregateMetadataList,
        )

    private fun buildIntegrationEventDesign(
        element: DesignElement,
        primaryAggregate: String?,
        aggregates: List<String>,
        primaryAggregateMetadata: AggregateInfo?,
        aggregateMetadataList: List<AggregateInfo>,
    ): IntegrationEventDesign {
        return IntegrationEventDesign(
            type = "integration_event",
            `package` = element.`package`,
            name = element.name,
            desc = element.desc,
            aggregate = primaryAggregate,
            aggregates = aggregates,
            primaryAggregateMetadata = primaryAggregateMetadata,
            aggregateMetadataList = aggregateMetadataList,
            mqTopic = element.metadata["mqTopic"] as? String,
            mqConsumer = element.metadata["mqConsumer"] as? String,
        )
    }

    private fun buildDomainEventDesign(
        element: DesignElement,
        primaryAggregate: String?,
        aggregates: List<String>,
        primaryAggregateMetadata: AggregateInfo?,
        aggregateMetadataList: List<AggregateInfo>,
    ): DomainEventDesign {
        require(primaryAggregate != null) { "Domain event must have an aggregate: ${element.name}" }

        return DomainEventDesign(
            type = "domain_event",
            `package` = element.`package`,
            name = element.name,
            desc = element.desc,
            aggregate = primaryAggregate,
            aggregates = aggregates,
            primaryAggregateMetadata = primaryAggregateMetadata,
            aggregateMetadataList = aggregateMetadataList,
            requestFields = parsePayloadFields(element.metadata["requestFields"]),
            responseFields = parsePayloadFields(element.metadata["responseFields"]),
            persist = element.metadata["persist"] as? Boolean ?: false,
        )
    }

    private fun buildValidatorDesign(
        element: DesignElement,
        primaryAggregate: String?,
        aggregates: List<String>,
        primaryAggregateMetadata: AggregateInfo?,
        aggregateMetadataList: List<AggregateInfo>,
    ): ValidatorDesign =
        ValidatorDesign(
            type = "validator",
            `package` = element.`package`,
            name = element.name,
            desc = element.desc,
            aggregate = primaryAggregate,
            aggregates = aggregates,
            primaryAggregateMetadata = primaryAggregateMetadata,
            aggregateMetadataList = aggregateMetadataList,
        )

    private fun parsePayloadFields(raw: Any?): List<PayloadField> {
        val list = raw as? List<*> ?: return emptyList()
        return list.mapNotNull { item ->
            val map = item as? Map<*, *> ?: return@mapNotNull null
            val name = map["name"]?.toString()?.takeIf { it.isNotBlank() } ?: return@mapNotNull null
            PayloadField(
                name = name,
                type = map["type"]?.toString()?.takeIf { it.isNotBlank() },
                defaultValue = map["defaultValue"]?.toString()?.takeIf { it.isNotBlank() },
                nullable = map["nullable"]?.toString()?.toBoolean() ?: false,
            )
        }
    }
}
