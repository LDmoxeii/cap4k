package com.only4.cap4k.plugin.codeanalysis.compiler

internal data class AggregateElementRecord(
    val carrierQualifiedName: String,
    val aggregate: String,
    val name: String,
    val packageName: String,
    val description: String,
    val type: String,
    val root: Boolean,
)

internal class AggregateElementJsonWriter {
    fun write(elements: List<AggregateElementRecord>): String = buildString {
        append('[')
        elements.sortedBy(AggregateElementRecord::carrierQualifiedName).forEachIndexed { index, element ->
            if (index > 0) append(',')
            append('{')
            append("\"carrierQualifiedName\":\"").append(escape(element.carrierQualifiedName)).append("\",")
            append("\"aggregate\":\"").append(escape(element.aggregate)).append("\",")
            append("\"name\":\"").append(escape(element.name)).append("\",")
            append("\"packageName\":\"").append(escape(element.packageName)).append("\",")
            append("\"description\":\"").append(escape(element.description)).append("\",")
            append("\"type\":\"").append(escape(element.type)).append("\",")
            append("\"root\":").append(element.root)
            append('}')
        }
        append(']')
    }

    private fun escape(value: String): String = buildString {
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
