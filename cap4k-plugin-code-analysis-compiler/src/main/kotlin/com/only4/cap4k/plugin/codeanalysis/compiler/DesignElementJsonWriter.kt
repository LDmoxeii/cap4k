package com.only4.cap4k.plugin.codeanalysis.compiler

import com.only4.cap4k.plugin.codeanalysis.core.model.DesignElement
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
                if (element.requestFields.isNotEmpty()) {
                    append(",\"requestFields\":")
                    appendFieldList(element.requestFields)
                }
                if (element.responseFields.isNotEmpty()) {
                    append(",\"responseFields\":")
                    appendFieldList(element.responseFields)
                }
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
        return value
            .replace("\\", "\\\\")
            .replace("\"", "\\\"")
            .replace("\n", " ")
    }
}
