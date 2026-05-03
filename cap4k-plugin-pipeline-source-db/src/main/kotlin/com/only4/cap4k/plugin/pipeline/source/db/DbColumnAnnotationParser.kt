package com.only4.cap4k.plugin.pipeline.source.db

import com.only4.cap4k.plugin.pipeline.api.EnumItemModel
import java.util.Locale

internal object DbColumnAnnotationParser {
    private val annotationPattern = Regex("@([A-Za-z]+)(=([^;]*))?;?")
    private val supportedGeneratedValueStrategies = setOf("uuid7", "snowflake-long", "identity", "database-identity")

    fun parse(comment: String): DbColumnAnnotationParseResult {
        val annotations = annotationPattern.findAll(comment)
            .map { match ->
                ParsedAnnotation(
                    key = match.groupValues[1].uppercase(Locale.ROOT),
                    value = match.groupValues[3].trim(),
                    hasExplicitValue = match.groups[2] != null,
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

        val generatedValue = resolveGeneratedValue(annotations)
        val deleted = resolveMarkerAnnotation(annotations, "DELETED", "Deleted")
        val version = resolveMarkerAnnotation(annotations, "VERSION", "Version")
        var insertable: Boolean? = null
        var updatable: Boolean? = null

        annotations.forEach { annotation ->
            val value = annotation.value
            when (annotation.key) {
                "INSERTABLE" -> insertable = parseBooleanAnnotationValue("Insertable", value)
                "UPDATABLE" -> updatable = parseBooleanAnnotationValue("Updatable", value)
            }
        }

        return DbColumnAnnotationParseResult(
            typeBinding = typeBinding,
            enumItems = parseEnumItems(enumConfig),
            generatedValueDeclared = generatedValue.declared,
            generatedValueStrategy = generatedValue.strategy,
            deleted = deleted,
            version = version,
            insertable = insertable,
            updatable = updatable,
        )
    }

    private fun resolveGeneratedValue(annotations: List<ParsedAnnotation>): ResolvedGeneratedValue {
        val generatedValueAnnotations = annotations.filter { it.key == "GENERATEDVALUE" }
        if (generatedValueAnnotations.isEmpty()) {
            return ResolvedGeneratedValue(declared = false, strategy = null)
        }

        val markerAnnotations = generatedValueAnnotations.filterNot { it.hasExplicitValue }
        val explicitStrategies = generatedValueAnnotations
            .filter { it.hasExplicitValue }
            .map { annotation ->
                require(annotation.value.isNotBlank()) { "invalid @GeneratedValue strategy in this slice: " }
                annotation.value.lowercase(Locale.ROOT).also { strategy ->
                    require(strategy in supportedGeneratedValueStrategies) {
                        "unsupported @GeneratedValue strategy in this slice: ${annotation.value}"
                    }
                }
            }
            .distinct()
        require(markerAnnotations.isEmpty() || explicitStrategies.isEmpty()) {
            "conflicting @GeneratedValue annotations on the same column comment."
        }
        require(explicitStrategies.size <= 1) {
            "conflicting @GeneratedValue strategies on the same column comment."
        }

        val resolvedStrategy = explicitStrategies.singleOrNull()?.let { strategy ->
            if (strategy == "database-identity") {
                "identity"
            } else {
                strategy
            }
        }
        return ResolvedGeneratedValue(
            declared = true,
            strategy = resolvedStrategy,
        )
    }

    private fun resolveMarkerAnnotation(
        annotations: List<ParsedAnnotation>,
        key: String,
        annotationName: String,
    ): Boolean? {
        val matchingAnnotations = annotations.filter { it.key == key }
        if (matchingAnnotations.isEmpty()) {
            return null
        }
        require(matchingAnnotations.none { it.hasExplicitValue }) {
            "invalid @$annotationName annotation: explicit values are not supported."
        }
        return true
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

    private fun parseBooleanAnnotationValue(annotationName: String, value: String): Boolean {
        return when {
            value.equals("true", ignoreCase = true) -> true
            value.equals("false", ignoreCase = true) -> false
            else -> throw IllegalArgumentException("invalid @$annotationName boolean value in this slice: $value")
        }
    }
}

private data class ParsedAnnotation(
    val key: String,
    val value: String,
    val hasExplicitValue: Boolean,
)

internal data class DbColumnAnnotationParseResult(
    val typeBinding: String? = null,
    val enumItems: List<EnumItemModel> = emptyList(),
    val generatedValueDeclared: Boolean = false,
    val generatedValueStrategy: String? = null,
    val deleted: Boolean? = null,
    val version: Boolean? = null,
    val insertable: Boolean? = null,
    val updatable: Boolean? = null,
)

private data class ResolvedGeneratedValue(
    val declared: Boolean,
    val strategy: String?,
)
