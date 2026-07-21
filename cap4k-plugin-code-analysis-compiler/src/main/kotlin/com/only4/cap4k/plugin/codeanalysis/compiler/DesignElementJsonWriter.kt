package com.only4.cap4k.plugin.codeanalysis.compiler

import com.only4.cap4k.plugin.codeanalysis.core.model.DesignElement
import com.only4.cap4k.plugin.codeanalysis.core.model.DesignArtifact
import com.only4.cap4k.plugin.codeanalysis.core.model.DesignField

class DesignElementJsonWriter {
    fun write(elements: List<DesignElement>): String {
        return buildString {
            append('[')
            var firstElement = true
            elements.forEach { element ->
                if (!firstElement) append(',') else firstElement = false
                append('{')
                append("\"tag\":\"").append(escape(element.tag)).append("\",")
                append("\"package\":\"").append(escape(element.`package`)).append("\",")
                append("\"name\":\"").append(escape(element.name)).append("\",")
                append("\"description\":\"").append(escape(element.description)).append("\"")
                if (element.aggregates.isNotEmpty()) {
                    append(",\"aggregates\":")
                    appendStringList(element.aggregates)
                }
                element.eventName.takeIf { it.isNotBlank() }?.let { value ->
                    append(",\"eventName\":\"").append(escape(value)).append("\"")
                }
                element.persist?.let { value ->
                    append(",\"persist\":").append(value)
                }
                if (element.artifacts.isNotEmpty()) {
                    append(",\"artifacts\":")
                    appendArtifactList(element.artifacts)
                }
                append(",\"fields\":")
                appendFieldList(element.fields)
                append(",\"resultFields\":")
                appendFieldList(element.resultFields)
                append('}')
            }
            append(']')
        }
    }

    private fun StringBuilder.appendFieldList(fields: List<DesignField>) {
        append('[')
        var firstField = true
        fields.forEach { field ->
            if (!firstField) append(',') else firstField = false
            append("{\"name\":\"").append(escape(field.name)).append("\",")
            append("\"type\":\"").append(escape(field.type)).append("\",")
            append("\"nullable\":").append(field.nullable)
            val defaultValue = field.defaultValue
            if (defaultValue != null) {
                append(",\"defaultValue\":\"").append(escape(defaultValue)).append("\"")
            }
            append('}')
        }
        append(']')
    }

    private fun StringBuilder.appendArtifactList(artifacts: List<DesignArtifact>) {
        append('[')
        var firstArtifact = true
        artifacts.forEach { artifact ->
            if (!firstArtifact) append(',') else firstArtifact = false
            append("{\"family\":\"").append(escape(artifact.family)).append("\"")
            if (artifact.variant.isNotBlank()) {
                append(",\"variant\":\"").append(escape(artifact.variant)).append("\"")
            }
            append('}')
        }
        append(']')
    }

    private fun StringBuilder.appendStringList(values: List<String>) {
        append('[')
        var firstValue = true
        values.forEach { value ->
            if (!firstValue) append(',') else firstValue = false
            append("\"").append(escape(value)).append("\"")
        }
        append(']')
    }

    private fun escape(value: String): String {
        return buildString {
            value.forEach { character ->
                when (character) {
                    '\\' -> append("\\\\")
                    '"' -> append("\\\"")
                    '\b' -> append("\\b")
                    '\u000C' -> append("\\f")
                    '\n' -> append("\\n")
                    '\r' -> append("\\r")
                    '\t' -> append("\\t")
                    else -> {
                        if (character.code in 0x00..0x1F) {
                            append("\\u")
                            append(character.code.toString(16).padStart(4, '0'))
                        } else {
                            append(character)
                        }
                    }
                }
            }
        }
    }
}
