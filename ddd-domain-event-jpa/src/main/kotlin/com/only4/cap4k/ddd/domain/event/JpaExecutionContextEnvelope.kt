package com.only4.cap4k.ddd.domain.event

import com.alibaba.fastjson.JSON
import com.alibaba.fastjson.JSONObject
import com.only4.cap4k.ddd.core.application.context.EncodedExecutionContextElement
import com.only4.cap4k.ddd.core.application.context.ExecutionContextDecodingException
import java.nio.charset.StandardCharsets

internal object JpaExecutionContextEnvelope {
    private const val MAX_ENVELOPE_BYTES = 65_535

    fun encode(elements: Collection<EncodedExecutionContextElement>): String {
        val envelope = elements
            .sortedBy { it.name }
            .map { element ->
                linkedMapOf(
                    "name" to element.name,
                    "version" to element.version,
                    "value" to element.value,
                )
            }
            .let(JSON::toJSONString)
        requireSize(envelope)
        return envelope
    }

    fun decode(envelope: String?): List<EncodedExecutionContextElement> {
        if (envelope.isNullOrBlank()) return emptyList()
        requireSize(envelope)
        val values = try {
            JSON.parseArray(envelope)
        } catch (ex: Exception) {
            throw ExecutionContextDecodingException("Malformed reliable Event ExecutionContext envelope", ex)
        } ?: throw ExecutionContextDecodingException("Malformed reliable Event ExecutionContext envelope")

        val names = mutableSetOf<String>()
        return values.mapIndexed { index, raw ->
            val value = raw as? JSONObject
                ?: throw ExecutionContextDecodingException("ExecutionContext element at index $index must be an object")
            val name = value.getString("name")
                ?.takeIf(String::isNotBlank)
                ?: throw ExecutionContextDecodingException("ExecutionContext element at index $index has no name")
            if (!names.add(name)) {
                throw ExecutionContextDecodingException("Duplicate ExecutionContext element '$name'")
            }
            val version = value.getInteger("version")
                ?: throw ExecutionContextDecodingException("ExecutionContext element '$name' has no version")
            val encodedValue = value.getString("value")
                ?: throw ExecutionContextDecodingException("ExecutionContext element '$name' has no value")
            try {
                EncodedExecutionContextElement(name, version, encodedValue)
            } catch (ex: IllegalArgumentException) {
                throw ExecutionContextDecodingException("Malformed ExecutionContext element '$name'", ex)
            }
        }
    }

    private fun requireSize(envelope: String) {
        val size = envelope.toByteArray(StandardCharsets.UTF_8).size
        require(size <= MAX_ENVELOPE_BYTES) {
            "Reliable Event ExecutionContext envelope exceeds $MAX_ENVELOPE_BYTES bytes: $size"
        }
    }
}
