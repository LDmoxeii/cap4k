package com.only4.cap4k.plugin.pipeline.source.db

import com.only4.cap4k.plugin.pipeline.api.EnumItemModel
import java.util.Locale

internal object DbColumnAnnotationParser {
    private val annotationPattern = Regex("@([A-Za-z]+)(=([^;]*))?;?")

    fun parse(comment: String): DbColumnAnnotationMetadata {
        val annotations = annotationPattern.findAll(comment).associate { match ->
            val key = match.groupValues[1].uppercase(Locale.ROOT)
            val value = match.groupValues[3].trim()
            key to value
        }
        val typeBinding = (annotations["TYPE"] ?: annotations["T"]).takeIf { !it.isNullOrBlank() }
        val enumConfig = annotations["ENUM"] ?: annotations["E"]

        if (enumConfig != null && typeBinding.isNullOrBlank()) {
            throw IllegalArgumentException("@E requires @T on the same column comment.")
        }

        return DbColumnAnnotationMetadata(
            typeBinding = typeBinding,
            enumItems = parseEnumItems(enumConfig),
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
}
