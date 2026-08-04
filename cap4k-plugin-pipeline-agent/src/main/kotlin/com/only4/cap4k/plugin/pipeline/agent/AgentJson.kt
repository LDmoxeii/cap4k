package com.only4.cap4k.plugin.pipeline.agent

import com.fasterxml.jackson.databind.JsonNode
import com.fasterxml.jackson.databind.node.ArrayNode
import com.fasterxml.jackson.databind.node.JsonNodeFactory
import com.fasterxml.jackson.databind.node.ObjectNode
import com.only4.cap4k.plugin.pipeline.json.PipelineJson
import java.lang.reflect.Type

internal class AgentStableJson(
    private val redactor: AgentCredentialRedactor,
) {
    private val mapper = PipelineJson.newMapper(
        includeNulls = true,
        lowercaseEnums = true,
    )
    private val writer = PipelineJson.prettyWriter(mapper)

    fun toJson(value: Any): String = encodeTree(redactor.redactJson(mapper.valueToTree(value)))

    fun identityJson(value: Any): String = encodeTree(
        redactor.identityProjection(mapper.valueToTree(value))
    )

    fun <T> fromJson(json: String, type: Class<T>): T = mapper.readValue(json, type)

    fun <T> fromJson(json: String, type: Type): T =
        mapper.readValue(json, mapper.typeFactory.constructType(type))

    fun parseTree(json: String): JsonNode = mapper.readTree(json)

    private fun encodeTree(element: JsonNode): String = writer.writeValueAsString(canonicalize(element))

    private fun canonicalize(element: JsonNode): JsonNode = when {
        element.isNull -> JsonNodeFactory.instance.nullNode()
        element.isArray -> JsonNodeFactory.instance.arrayNode().also { result ->
            (element as ArrayNode).forEach { value -> result.add(canonicalize(value)) }
        }
        element.isObject -> JsonNodeFactory.instance.objectNode().also { result ->
            (element as ObjectNode).fields().asSequence()
                .sortedBy(Map.Entry<String, JsonNode>::key)
                .forEach { (key, value) -> result.set<JsonNode>(key, canonicalize(value)) }
        }
        else -> element.deepCopy<JsonNode>()
    }
}
