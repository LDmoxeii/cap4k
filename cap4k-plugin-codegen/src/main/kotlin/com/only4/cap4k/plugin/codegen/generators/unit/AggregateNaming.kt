package com.only4.cap4k.plugin.codegen.generators.unit

import com.only4.cap4k.plugin.codegen.context.aggregate.AggregateContext
import com.only4.cap4k.plugin.codegen.misc.toUpperCamelCase
import com.only4.cap4k.plugin.codegen.pebble.PebbleTemplateRenderer.renderString

object AggregateNaming {
    fun enumName(enumType: String): String = enumType

    fun enumTranslationName(enumType: String): String = "${enumType}Translation"

    fun entityName(entityType: String): String = entityType

    fun querydslName(entityType: String): String = "Q$entityType"

    fun specificationName(entityType: String): String = "${entityType}Specification"

    fun factoryName(entityType: String): String = "${entityType}Factory"

    fun factoryPayloadName(entityType: String): String = "${entityType}Payload"

    fun repositoryName(ctx: AggregateContext, entityType: String): String =
        renderString(ctx.getString("repositoryNameTemplate"), mapOf("Aggregate" to entityType))

    fun aggregateName(ctx: AggregateContext, entityType: String): String =
        renderString(ctx.getString("aggregateTypeTemplate"), mapOf("Entity" to entityType))

    fun schemaBaseName(): String = "Schema"

    fun schemaName(entityType: String): String = "S$entityType"

    fun domainEventName(raw: String): String {
        val base = toUpperCamelCase(raw) ?: raw
        return if (base.endsWith("Event") || base.endsWith("Evt")) {
            base
        } else {
            "${base}DomainEvent"
        }
    }

    fun domainEventHandlerName(eventClass: String): String = "${eventClass}Subscriber"

    fun uniqueQueryName(entityType: String, suffix: String): String = "Unique${entityType}${suffix}Qry"

    fun uniqueQueryHandlerName(queryName: String): String {
        val raw = "${queryName}Handler"
        return toUpperCamelCase(raw) ?: raw
    }

    fun uniqueValidatorName(entityType: String, suffix: String): String = "Unique${entityType}${suffix}"

    fun uniqueConstraintSuffix(cons: Map<String, Any?>, deletedField: String): String {
        val constraintName = cons["constraintName"].toString()
        if ("uk_i" == constraintName) return ""
        val regex = Regex("^uk_v_(.+)$", RegexOption.IGNORE_CASE)
        val match = regex.find(constraintName)
        if (match != null) {
            val token = match.groupValues[1]
            return toUpperCamelCase(token) ?: token.replaceFirstChar {
                if (it.isLowerCase()) it.titlecase() else it.toString()
            }
        }

        val columns = (cons["columns"] as? List<Map<String, Any?>>).orEmpty()
        val filtered = columns.filter { column ->
            !column["columnName"].toString().equals(deletedField, ignoreCase = true)
        }
        if (filtered.isEmpty()) return ""
        return filtered.sortedBy { (it["ordinal"] as Number).toInt() }
            .joinToString("") { column ->
                toUpperCamelCase(column["columnName"].toString())
                    ?: column["columnName"].toString()
            }
    }
}