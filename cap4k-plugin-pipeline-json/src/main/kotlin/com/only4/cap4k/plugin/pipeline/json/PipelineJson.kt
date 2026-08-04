package com.only4.cap4k.plugin.pipeline.json

import com.fasterxml.jackson.annotation.JsonInclude
import com.fasterxml.jackson.core.json.JsonWriteFeature
import com.fasterxml.jackson.core.util.DefaultIndenter
import com.fasterxml.jackson.core.util.DefaultPrettyPrinter
import com.fasterxml.jackson.core.util.Separators
import com.fasterxml.jackson.databind.DeserializationFeature
import com.fasterxml.jackson.databind.MapperFeature
import com.fasterxml.jackson.databind.ObjectMapper
import com.fasterxml.jackson.databind.ObjectWriter
import com.fasterxml.jackson.databind.SerializationFeature
import com.fasterxml.jackson.databind.cfg.EnumFeature
import com.fasterxml.jackson.databind.json.JsonMapper
import com.fasterxml.jackson.module.kotlin.kotlinModule

/**
 * Creates Jackson mappers for Cap4k build-time JSON boundaries.
 *
 * Callers deliberately own boundary-specific choices such as enum wire names,
 * redaction, and tree canonicalization. This factory keeps the shared Kotlin,
 * escaping, unknown-field, map-ordering, and line-ending rules identical without
 * collapsing Agent, plan, flow, and authoring inputs into one mutable global mapper.
 */
object PipelineJson {
    fun newMapper(
        includeNulls: Boolean = false,
        lowercaseEnums: Boolean = false,
    ): ObjectMapper {
        val builder = JsonMapper.builder()
            .addModule(kotlinModule())
            .disable(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES)
            .enable(SerializationFeature.ORDER_MAP_ENTRIES_BY_KEYS)
            .disable(JsonWriteFeature.ESCAPE_NON_ASCII)

        if (lowercaseEnums) {
            builder.enable(EnumFeature.WRITE_ENUMS_TO_LOWERCASE)
            builder.enable(MapperFeature.ACCEPT_CASE_INSENSITIVE_ENUMS)
        }

        return builder.build()
            .setSerializationInclusion(
                if (includeNulls) JsonInclude.Include.ALWAYS else JsonInclude.Include.NON_NULL,
            )
    }

    fun prettyWriter(mapper: ObjectMapper): ObjectWriter =
        mapper.writer(stablePrettyPrinter())

    private fun stablePrettyPrinter(): DefaultPrettyPrinter = DefaultPrettyPrinter(
        Separators.createDefaultInstance()
            .withObjectFieldValueSpacing(Separators.Spacing.AFTER)
            .withObjectEmptySeparator("")
            .withArrayEmptySeparator(""),
    ).apply {
        val indenter = DefaultIndenter("  ", "\n")
        indentObjectsWith(indenter)
        indentArraysWith(indenter)
    }
}
