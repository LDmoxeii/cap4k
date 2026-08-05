package com.only4.cap4k.ddd.core.share.json

import com.fasterxml.jackson.annotation.JsonAutoDetect
import com.fasterxml.jackson.annotation.JsonInclude
import com.fasterxml.jackson.annotation.JsonPropertyOrder
import com.fasterxml.jackson.annotation.PropertyAccessor
import com.fasterxml.jackson.databind.DeserializationFeature
import com.fasterxml.jackson.databind.JsonNode
import com.fasterxml.jackson.databind.MapperFeature
import com.fasterxml.jackson.databind.ObjectMapper
import com.fasterxml.jackson.databind.SerializationFeature
import com.fasterxml.jackson.databind.json.JsonMapper
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule
import com.fasterxml.jackson.module.kotlin.kotlinModule
import com.only4.cap4k.ddd.core.application.context.EncodedExecutionContextElement
import com.only4.cap4k.ddd.core.application.context.ExecutionContextDecodingException
import java.nio.charset.StandardCharsets

/** Single JSON boundary for the surviving Runtime persistence and transport paths. */
object RuntimeJson {
    private val mapper: ObjectMapper = JsonMapper.builder()
        .addModule(kotlinModule())
        .addModule(JavaTimeModule())
        .enable(MapperFeature.SORT_PROPERTIES_ALPHABETICALLY)
        .enable(SerializationFeature.ORDER_MAP_ENTRIES_BY_KEYS)
        .disable(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES)
        .build()
        .setSerializationInclusion(JsonInclude.Include.ALWAYS)
        .setVisibility(PropertyAccessor.FIELD, JsonAutoDetect.Visibility.ANY)
        .setVisibility(PropertyAccessor.GETTER, JsonAutoDetect.Visibility.NONE)
        .setVisibility(PropertyAccessor.IS_GETTER, JsonAutoDetect.Visibility.NONE)
        .setVisibility(PropertyAccessor.SETTER, JsonAutoDetect.Visibility.NONE)

    fun write(value: Any?): String {
        val typeName = value?.javaClass?.name ?: "null"
        return try {
            mapper.writeValueAsString(value)
        } catch (_: Exception) {
            throw RuntimeJsonEncodingException("Unable to encode JSON for $typeName")
        }
    }

    fun <T : Any> read(json: String, type: Class<T>): T = try {
        mapper.readValue(json, type)
    } catch (_: Exception) {
        throw RuntimeJsonDecodingException("Unable to decode JSON for ${type.name}")
    }

    fun tree(json: String): JsonNode = mapper.readTree(json)

    fun requireSize(json: String, limit: Int, boundary: String) {
        val size = json.toByteArray(StandardCharsets.UTF_8).size
        require(size <= limit) { "$boundary exceeds $limit bytes: $size" }
    }
}

class RuntimeJsonEncodingException(message: String) : IllegalArgumentException(message)

class RuntimeJsonDecodingException(message: String) : IllegalArgumentException(message)

/** Deterministic JSON representation of encoded ExecutionContext elements. */
object RuntimeExecutionContextJson {
    private const val MAX_ENVELOPE_BYTES = 65_535

    @JsonPropertyOrder("name", "version", "value")
    private data class Element(
        val name: String,
        val version: Int,
        val value: String,
    )

    fun encode(elements: Collection<EncodedExecutionContextElement>, boundary: String): String {
        val json = RuntimeJson.write(elements.sortedBy { it.name }.map { Element(it.name, it.version, it.value) })
        RuntimeJson.requireSize(json, MAX_ENVELOPE_BYTES, boundary)
        return json
    }

    fun decode(rawEnvelope: Any?, boundary: String): List<EncodedExecutionContextElement> {
        val envelope = when (rawEnvelope) {
            null -> return emptyList()
            is ByteArray -> rawEnvelope.toString(StandardCharsets.UTF_8)
            else -> rawEnvelope.toString()
        }
        if (envelope.isBlank()) return emptyList()
        RuntimeJson.requireSize(envelope, MAX_ENVELOPE_BYTES, boundary)

        val values = try {
            RuntimeJson.tree(envelope)
        } catch (ex: Exception) {
            throw ExecutionContextDecodingException("Malformed $boundary", ex)
        }
        if (!values.isArray) throw ExecutionContextDecodingException("$boundary must be an array")

        val names = mutableSetOf<String>()
        return values.mapIndexed { index, value ->
            if (!value.isObject) {
                throw ExecutionContextDecodingException("ExecutionContext element at index $index must be an object")
            }
            val name = value.get("name")?.takeIf { it.isTextual && it.textValue().isNotBlank() }?.textValue()
                ?: throw ExecutionContextDecodingException("ExecutionContext element at index $index has no name")
            if (!names.add(name)) throw ExecutionContextDecodingException("Duplicate ExecutionContext element '$name'")
            val version = value.get("version")?.takeIf { it.isIntegralNumber }?.intValue()
                ?: throw ExecutionContextDecodingException("ExecutionContext element '$name' has no version")
            val encodedValue = value.get("value")?.takeIf { it.isTextual }?.textValue()
                ?: throw ExecutionContextDecodingException("ExecutionContext element '$name' has no value")
            try {
                EncodedExecutionContextElement(name, version, encodedValue)
            } catch (ex: IllegalArgumentException) {
                throw ExecutionContextDecodingException("Malformed ExecutionContext element '$name'", ex)
            }
        }
    }
}
