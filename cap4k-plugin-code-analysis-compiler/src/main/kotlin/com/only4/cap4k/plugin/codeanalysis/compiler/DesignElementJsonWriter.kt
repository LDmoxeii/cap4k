package com.only4.cap4k.plugin.codeanalysis.compiler

import com.only4.cap4k.plugin.codeanalysis.core.model.DesignElement
import com.only4.cap4k.plugin.codeanalysis.core.model.DesignField
import com.only4.cap4k.plugin.codeanalysis.core.model.DesignParameter

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
                append("\"desc\":\"").append(escape(element.desc)).append("\"")
                if (element.aggregates.isNotEmpty()) {
                    append(",\"aggregates\":")
                    appendStringList(element.aggregates)
                }
                element.entity?.let { value ->
                    append(",\"entity\":\"").append(escape(value)).append("\"")
                }
                element.persist?.let { value ->
                    append(",\"persist\":").append(value)
                }
                if (element.traits.isNotEmpty()) {
                    append(",\"traits\":")
                    appendStringList(element.traits)
                }
                element.message?.let { value ->
                    append(",\"message\":\"").append(escape(value)).append("\"")
                }
                if (element.targets.isNotEmpty()) {
                    append(",\"targets\":")
                    appendStringList(element.targets)
                }
                element.valueType?.let { value ->
                    append(",\"valueType\":\"").append(escape(value)).append("\"")
                }
                if (element.parameters.isNotEmpty()) {
                    append(",\"parameters\":")
                    appendParameterList(element.parameters)
                }
                append(",\"requestFields\":")
                appendFieldList(element.requestFields)
                append(",\"responseFields\":")
                appendFieldList(element.responseFields)
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

    private fun StringBuilder.appendParameterList(parameters: List<DesignParameter>) {
        append('[')
        var firstParameter = true
        parameters.forEach { parameter ->
            if (!firstParameter) append(',') else firstParameter = false
            append("{\"name\":\"").append(escape(parameter.name)).append("\",")
            append("\"type\":\"").append(escape(parameter.type)).append("\",")
            append("\"nullable\":").append(parameter.nullable)
            val defaultValue = parameter.defaultValue
            if (defaultValue != null) {
                append(",\"defaultValue\":\"").append(escape(defaultValue)).append("\"")
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
