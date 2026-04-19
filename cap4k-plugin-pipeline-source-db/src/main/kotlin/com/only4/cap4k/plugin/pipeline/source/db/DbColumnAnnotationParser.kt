package com.only4.cap4k.plugin.pipeline.source.db

import com.only4.cap4k.plugin.pipeline.api.EnumItemModel
import java.util.Locale

internal object DbColumnAnnotationParser {
    private val annotationPattern = Regex("@([A-Za-z]+)(=([^;]*))?;?")

    fun parse(comment: String): DbColumnAnnotationParseResult {
        val annotations = annotationPattern.findAll(comment)
            .map { match ->
                ParsedAnnotation(
                    key = match.groupValues[1].uppercase(Locale.ROOT),
                    value = match.groupValues[3].trim(),
                )
            }
            .toList()
        val typeBinding = resolveAnnotationValue(
            annotations = annotations,
            aliases = setOf("TYPE", "T"),
            conflictMessage = "conflicting @T/@TYPE annotations on the same column comment.",
        )
        val enumConfig = resolveAnnotationValue(
            annotations = annotations,
            aliases = setOf("ENUM", "E"),
            conflictMessage = "conflicting @E/@ENUM annotations on the same column comment.",
        )

        if (enumConfig != null && typeBinding.isNullOrBlank()) {
            throw IllegalArgumentException("@E requires @T on the same column comment.")
        }

        var generatedValueStrategy: String? = null
        var version = false
        var insertable: Boolean? = null
        var updatable: Boolean? = null

        annotations.forEach { annotation ->
            val value = annotation.value
            when (annotation.key) {
                "GENERATEDVALUE" -> {
                    val strategy = value.uppercase(Locale.ROOT)
                    require(strategy == "IDENTITY") {
                        "unsupported @GeneratedValue strategy in this slice: $strategy"
                    }
                    generatedValueStrategy = strategy
                }
                "VERSION" -> version = value.equals("true", ignoreCase = true)
                "INSERTABLE" -> insertable = value.equals("true", ignoreCase = true)
                "UPDATABLE" -> updatable = value.equals("true", ignoreCase = true)
            }
        }

        return DbColumnAnnotationParseResult(
            typeBinding = typeBinding,
            enumItems = parseEnumItems(enumConfig),
            generatedValueStrategy = generatedValueStrategy,
            version = version,
            insertable = insertable,
            updatable = updatable,
        )
    }

    private fun parseEnumItems(enumConfig: String?): List<EnumItemModel> {
        if (enumConfig.isNullOrBlank()) {
            return emptyList()
        }

        return enumConfig
            .split('|')
            .asSequence()
            .map(String::trim)
            .filter(String::isNotEmpty)
            .map(::parseEnumItem)
            .toList()
    }

    private fun parseEnumItem(rawItem: String): EnumItemModel {
        val parts = rawItem.split(':', limit = 3).map(String::trim)
        require(parts.size == 3) {
            "invalid @E item format: $rawItem"
        }
        val value = parts[0].toIntOrNull()
            ?: throw IllegalArgumentException("invalid @E item value: ${parts[0]}")
        require(parts[1].isNotEmpty()) {
            "invalid @E item name: $rawItem"
        }
        return EnumItemModel(
            value = value,
            name = parts[1],
            description = parts[2],
        )
    }

    private fun resolveAnnotationValue(
        annotations: List<ParsedAnnotation>,
        aliases: Set<String>,
        conflictMessage: String,
    ): String? {
        val values = annotations
            .asSequence()
            .filter { it.key in aliases }
            .map { it.value }
            .filter { it.isNotBlank() }
            .distinct()
            .toList()
        require(values.size <= 1) { conflictMessage }
        return values.singleOrNull()
    }
}

private data class ParsedAnnotation(
    val key: String,
    val value: String,
)

internal data class DbColumnAnnotationParseResult(
    val typeBinding: String? = null,
    val enumItems: List<EnumItemModel> = emptyList(),
    val generatedValueStrategy: String? = null,
    val version: Boolean = false,
    val insertable: Boolean? = null,
    val updatable: Boolean? = null,
)
