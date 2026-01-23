package com.only4.cap4k.plugin.codegen.generators.drawingboard

import com.google.gson.Gson
import com.google.gson.GsonBuilder
import com.only4.cap4k.plugin.codegen.context.drawingboard.DrawingBoardContext
import com.only4.cap4k.plugin.codeanalysis.core.model.DesignElement

class DrawingBoardGenerator(
    private val gson: Gson = GsonBuilder()
        .disableHtmlEscaping()
        .setPrettyPrinting()
        .create()
) {
    val tag: String = "drawing_board"
    val order: Int = 10

    context(ctx: DrawingBoardContext)
    fun documents(): List<DrawingBoardDocument> {
        val json = renderJson(ctx.elements)
        return listOf(
            DrawingBoardDocument(
                tag = tag,
                generatorName = tag,
                content = json,
                context = mapOf("drawingBoardTag" to tag)
            )
        )
    }

    context(ctx: DrawingBoardContext)
    fun buildContext(document: DrawingBoardDocument): Map<String, Any?> = document.context

    fun generatorName(document: DrawingBoardDocument): String = document.generatorName

    private fun renderJson(elements: List<DesignElement>): String {
        val pretty = gson.toJson(elements)
        return inlineFieldArrays(pretty)
    }

    private fun inlineFieldArrays(json: String): String {
        val lines = json.split("\n")
        if (lines.size == 1) return json

        val output = StringBuilder()
        var inTargetArray = false
        var i = 0
        while (i < lines.size) {
            val line = lines[i]
            val isTargetLine = line.contains("\"requestFields\": [") || line.contains("\"responseFields\": [")
            val isInlineEmpty = line.contains("\"requestFields\": []") || line.contains("\"responseFields\": []")

            if (!inTargetArray) {
                if (isTargetLine && !isInlineEmpty) {
                    inTargetArray = true
                }
                output.append(line)
                if (i < lines.lastIndex) output.append("\n")
                i++
                continue
            }

            val trimmed = line.trimStart()
            if (trimmed.startsWith("]")) {
                inTargetArray = false
                output.append(line)
                if (i < lines.lastIndex) output.append("\n")
                i++
                continue
            }

            if (trimmed.startsWith("{")) {
                val startIndent = line.takeWhile { it == ' ' || it == '\t' }
                val props = mutableListOf<String>()
                var j = i + 1
                var closingLine: String? = null
                while (j < lines.size) {
                    val current = lines[j]
                    val currentTrimmed = current.trim()
                    if (currentTrimmed.startsWith("}")) {
                        closingLine = currentTrimmed
                        break
                    }
                    props.add(currentTrimmed.trimEnd(','))
                    j++
                }
                if (closingLine == null) {
                    output.append(line)
                    if (i < lines.lastIndex) output.append("\n")
                    i++
                    continue
                }
                val suffixComma = if (closingLine.endsWith(",")) "," else ""
                output.append(startIndent)
                    .append("{ ")
                    .append(props.joinToString(", "))
                    .append(" }")
                    .append(suffixComma)
                if (j < lines.lastIndex) output.append("\n")
                i = j + 1
                continue
            }

            output.append(line)
            if (i < lines.lastIndex) output.append("\n")
            i++
        }

        return output.toString()
    }
}
