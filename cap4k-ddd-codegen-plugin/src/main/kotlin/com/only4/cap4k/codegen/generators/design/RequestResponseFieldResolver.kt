package com.only4.cap4k.codegen.generators.design

import com.only4.cap4k.codegen.context.design.DesignContext
import com.only4.cap4k.codegen.context.design.models.BaseDesign
import com.only4.cap4k.codegen.context.design.models.common.PayloadField
import com.only4.cap4k.codegen.ksp.models.FieldMetadata

data class ResolvedRequestResponseFields(
    val requestFieldsForTemplate: List<Map<String, String>>,
    val responseFieldsForTemplate: List<Map<String, String>>,
    val imports: Set<String>,
)

context(ctx: DesignContext)
fun resolveRequestResponseFields(
    design: BaseDesign,
    requestFields: List<PayloadField>,
    responseFields: List<PayloadField>,
): ResolvedRequestResponseFields {
    val imports = mutableSetOf<String>()

    fun inferType(field: PayloadField): String {
        field.type?.takeIf { it.isNotBlank() }?.let { return it }

        design.aggregates.forEach { aggName ->
            val agg = ctx.aggregateMap[aggName] ?: return@forEach
            lookupFieldType(agg.aggregateRoot.fields, field.name)?.let { return it }
            agg.valueObjects.forEach { vo ->
                lookupFieldType(vo.fields, field.name)?.let { return it }
            }
            agg.entities.forEach { entity ->
                lookupFieldType(entity.fields, field.name)?.let { return it }
            }
        }
        return "kotlin.String"
    }

    fun renderType(rawType: String, nullable: Boolean): String {
        val cleaned = rawType.trim()
        val shortName = cleaned.substringAfterLast(".")
        val needsImport = cleaned.contains(".") && !cleaned.startsWith("kotlin.")
                && cleaned != shortName && !cleaned.startsWith("java.lang.")
        val mappedType = ctx.typeMapping[shortName]
        if (cleaned == shortName && mappedType != null) {
            imports.add(mappedType)
        } else if (needsImport) {
            imports.add(cleaned)
        }
        return if (nullable) "$shortName?" else shortName
    }

    fun toTemplateFields(fields: List<PayloadField>): List<Map<String, String>> =
        fields.map { f ->
            val rawType = inferType(f)
            val typeForCode = renderType(rawType, f.nullable)
            val defaultValue = f.defaultValue?.takeIf { it.isNotBlank() }
            buildMap {
                put("name", f.name)
                put("type", typeForCode)
                defaultValue?.let { put("defaultValue", it) }
            }
        }

    val request = toTemplateFields(requestFields)
    val response = toTemplateFields(responseFields)

    return ResolvedRequestResponseFields(
        requestFieldsForTemplate = request,
        responseFieldsForTemplate = response,
        imports = imports,
    )
}

private fun lookupFieldType(fields: List<FieldMetadata>, name: String): String? {
    val match = fields.firstOrNull { it.name.equals(name, ignoreCase = false) } ?: return null
    return match.type
}
