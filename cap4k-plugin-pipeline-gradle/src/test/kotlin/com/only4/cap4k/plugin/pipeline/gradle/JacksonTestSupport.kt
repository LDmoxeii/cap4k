package com.only4.cap4k.plugin.pipeline.gradle

import com.fasterxml.jackson.databind.JsonNode
import com.fasterxml.jackson.databind.node.ArrayNode
import com.fasterxml.jackson.databind.node.ObjectNode

internal fun JsonNode.requireObjectNode(): ObjectNode {
    require(isObject) { "Expected JSON object but was $nodeType" }
    return this as ObjectNode
}

internal fun JsonNode.requireArrayNode(): ArrayNode {
    require(isArray) { "Expected JSON array but was $nodeType" }
    return this as ArrayNode
}

internal fun ObjectNode.requireObjectNode(field: String): ObjectNode =
    requireNotNull(get(field)) { "Missing JSON object field $field" }.requireObjectNode()

internal fun ObjectNode.requireArrayNode(field: String): ArrayNode =
    requireNotNull(get(field)) { "Missing JSON array field $field" }.requireArrayNode()
