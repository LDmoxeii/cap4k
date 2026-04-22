package com.only4.cap4k.plugin.pipeline.core

class BootstrapManagedSectionMerger {

    fun merge(existingContent: String, generatedContent: String): String {
        val existingDocument = parseDocument(existingContent, "Existing content")
        val generatedDocument = parseDocument(generatedContent, "Generated content")
        val generatedSectionsById = generatedDocument.sections.associateBy { it.id }

        val existingIds = existingDocument.sections.map { it.id }.toSet()
        val generatedIds = generatedSectionsById.keys
        if (existingIds != generatedIds) {
            val missingInExisting = generatedIds - existingIds
            val missingInGenerated = existingIds - generatedIds
            throw IllegalArgumentException(
                buildString {
                    append("Managed section ids must match between existing and generated content.")
                    if (missingInExisting.isNotEmpty()) {
                        append(" Missing in existing: ${missingInExisting.sorted().joinToString(", ")}.")
                    }
                    if (missingInGenerated.isNotEmpty()) {
                        append(" Missing in generated: ${missingInGenerated.sorted().joinToString(", ")}.")
                    }
                }
            )
        }

        val mergedLines = mutableListOf<String>()
        var cursor = 0
        for (section in existingDocument.sections) {
            mergedLines += existingDocument.lines.subList(cursor, section.beginLineIndex + 1)
            mergedLines += generatedSectionsById.getValue(section.id).bodyLines
            mergedLines += existingDocument.lines.subList(section.endLineIndex, section.endLineIndex + 1)
            cursor = section.endLineIndex + 1
        }
        mergedLines += existingDocument.lines.subList(cursor, existingDocument.lines.size)
        return mergedLines.joinToString("\n")
    }

    private fun parseDocument(content: String, sourceLabel: String): ParsedDocument {
        val lines = content.split("\n")
        val sections = mutableListOf<ParsedSection>()
        var openSectionId: String? = null
        var openSectionBeginIndex: Int? = null

        for (index in lines.indices) {
            val marker = parseMarker(lines[index]) ?: continue
            if (openSectionId == null) {
                if (marker.kind == MarkerKind.END) {
                    throw IllegalArgumentException("$sourceLabel has managed end marker without matching begin: ${marker.sectionId}")
                }
                if (sections.any { it.id == marker.sectionId }) {
                    throw IllegalArgumentException("$sourceLabel has duplicate managed section: ${marker.sectionId}")
                }
                openSectionId = marker.sectionId
                openSectionBeginIndex = index
                continue
            }

            if (marker.kind == MarkerKind.BEGIN) {
                throw IllegalArgumentException(
                    "$sourceLabel has nested managed begin marker ${marker.sectionId} before closing $openSectionId"
                )
            }
            if (marker.sectionId != openSectionId) {
                throw IllegalArgumentException(
                    "$sourceLabel has mismatched managed end marker ${marker.sectionId} for section $openSectionId"
                )
            }

            val beginIndex = requireNotNull(openSectionBeginIndex)
            sections += ParsedSection(
                id = openSectionId,
                beginLineIndex = beginIndex,
                endLineIndex = index,
                bodyLines = lines.subList(beginIndex + 1, index),
            )
            openSectionId = null
            openSectionBeginIndex = null
        }

        if (openSectionId != null) {
            throw IllegalArgumentException("$sourceLabel is missing managed end marker for section $openSectionId")
        }
        if (sections.isEmpty()) {
            throw IllegalArgumentException("$sourceLabel must contain at least one managed section.")
        }

        return ParsedDocument(lines, sections)
    }

    private fun parseMarker(line: String): Marker? {
        val trimmedLine = line.removeSuffix("\r").trim()
        val match = MARKER_REGEX.matchEntire(trimmedLine) ?: return null
        val kind = when (match.groupValues[1]) {
            "begin" -> MarkerKind.BEGIN
            "end" -> MarkerKind.END
            else -> error("Unexpected managed marker kind")
        }
        return Marker(kind, match.groupValues[2])
    }

    private data class ParsedDocument(
        val lines: List<String>,
        val sections: List<ParsedSection>,
    )

    private data class ParsedSection(
        val id: String,
        val beginLineIndex: Int,
        val endLineIndex: Int,
        val bodyLines: List<String>,
    )

    private data class Marker(
        val kind: MarkerKind,
        val sectionId: String,
    )

    private enum class MarkerKind {
        BEGIN,
        END,
    }

    private companion object {
        val MARKER_REGEX = Regex("""^// \[cap4k-bootstrap:managed-(begin|end):([^\]]+)]$""")
    }
}
