package com.only4.cap4k.plugin.codegen.pebble

import com.only4.cap4k.plugin.codegen.pebble.PebbleTemplateRenderer.renderString
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class AggregateTemplateAutoImportTest {
    private fun loadTemplate(name: String): String {
        val resource = javaClass.classLoader.getResource("templates/$name")
            ?: error("Template not found: $name")
        return resource.openStream().bufferedReader().use { it.readText() }
    }

    @Test
    fun `repository template imports static and entity types`() {
        val template = loadTemplate("repository.kt.peb")
        val context = mapOf(
            "basePackage" to "com.example",
            "templatePackage" to "",
            "package" to "",
            "date" to "2026-01-21",
            "Repository" to "OrderRepository",
            "Aggregate" to "Order",
            "AggregateType" to "com.example.domain.Order",
            "IdentityType" to "Long",
            "Comment" to "desc",
            "supportQuerydsl" to false,
            "typeMapping" to emptyMap<String, String>()
        )

        val rendered = renderString(template, context)
        assertTrue(rendered.contains("import com.example.domain.Order"))
        assertTrue(rendered.contains("import org.springframework.data.jpa.repository.JpaRepository"))
        assertTrue(rendered.contains("import org.springframework.data.jpa.repository.JpaSpecificationExecutor"))
        assertTrue(rendered.contains("import org.springframework.stereotype.Repository"))
        assertTrue(rendered.contains("import com.only4.cap4k.ddd.core.domain.aggregate.annotation.Aggregate"))
    }

    @Test
    fun `entity template adds jakarta and hibernate imports via use`() {
        val template = loadTemplate("entity.kt.peb")
        val context = mapOf(
            "basePackage" to "com.example",
            "templatePackage" to "",
            "package" to "",
            "date" to "2026-01-21",
            "entityType" to "Order",
            "Comment" to "desc",
            "columns" to emptyList<Map<String, Any?>>(),
            "relations" to emptyList<Map<String, Any?>>(),
            "annotationLines" to emptyList<String>(),
            "customerLines" to emptyList<String>(),
            "extendsClause" to "",
            "implementsClause" to "",
            "typeMapping" to emptyMap<String, String>()
        )

        val rendered = renderString(template, context)
        assertTrue(rendered.contains("import jakarta.persistence.*"))
        assertTrue(rendered.contains("import org.hibernate.annotations.DynamicInsert"))
        assertTrue(rendered.contains("import org.hibernate.annotations.DynamicUpdate"))
        assertTrue(rendered.contains("import com.only4.cap4k.ddd.core.domain.aggregate.annotation.Aggregate"))
    }

    @Test
    fun `unique validator template imports kotlin reflect memberProperties via use`() {
        val template = loadTemplate("unique_validator.kt.peb")
        val context = mapOf(
            "basePackage" to "com.example",
            "templatePackage" to "",
            "package" to "",
            "date" to "2026-01-21",
            "Validator" to "UniqueOrder",
            "Comment" to "desc",
            "FieldParams" to emptyList<Map<String, String>>(),
            "RequestProps" to emptyList<Map<String, String>>(),
            "EntityIdParam" to "orderIdField",
            "EntityIdDefault" to "orderId",
            "EntityIdVar" to "orderIdProperty",
            "IdType" to "Long",
            "ExcludeIdParamName" to "excludeOrderId",
            "Query" to "UniqueOrderQry",
            "QueryType" to "com.example.app.UniqueOrderQry",
            "typeMapping" to emptyMap<String, String>()
        )

        val rendered = renderString(template, context)
        assertTrue(rendered.contains("import kotlin.reflect.full.memberProperties"))
    }
}
