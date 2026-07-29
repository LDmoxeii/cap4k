package com.only4.cap4k.plugin.pipeline.generator.types

import com.only4.cap4k.plugin.pipeline.api.ArtifactOutputKind
import com.only4.cap4k.plugin.pipeline.api.CanonicalModel
import com.only4.cap4k.plugin.pipeline.api.CanonicalTypeIdentity
import com.only4.cap4k.plugin.pipeline.api.CanonicalTypeKind
import com.only4.cap4k.plugin.pipeline.api.ConflictPolicy
import com.only4.cap4k.plugin.pipeline.api.JsonValuePersistenceProjection
import com.only4.cap4k.plugin.pipeline.api.ProjectConfig
import com.only4.cap4k.plugin.pipeline.api.ProjectLayout
import com.only4.cap4k.plugin.pipeline.api.SemanticBuiltinType
import com.only4.cap4k.plugin.pipeline.api.SemanticBuiltinTypeRef
import com.only4.cap4k.plugin.pipeline.api.SemanticDefaultExpression
import com.only4.cap4k.plugin.pipeline.api.SemanticListTypeRef
import com.only4.cap4k.plugin.pipeline.api.SemanticMapTypeRef
import com.only4.cap4k.plugin.pipeline.api.SemanticNamedTypeRef
import com.only4.cap4k.plugin.pipeline.api.SemanticValueDefinition
import com.only4.cap4k.plugin.pipeline.api.SemanticValueField
import com.only4.cap4k.plugin.pipeline.api.SemanticValueRole
import com.only4.cap4k.plugin.pipeline.api.TemplateConfig
import com.only4.cap4k.plugin.pipeline.api.ValueObjectModel
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows

class ValueObjectArtifactPlannerTest {

    @Test
    fun `empty model returns empty without requiring domain module`() {
        assertEquals(emptyList<Any>(), ValueObjectArtifactPlanner().plan(config(modules = emptyMap()), CanonicalModel()))
    }

    @Test
    fun `non persistent value object plans only checked in semantic value`() {
        val item = ValueObjectArtifactPlanner().plan(
            config(),
            CanonicalModel(
                valueObjects = listOf(
                    valueObject(
                        fields = listOf(
                            SemanticValueField(
                                name = "amounts",
                                type = SemanticListTypeRef(
                                    SemanticNamedTypeRef(
                                        symbol = CanonicalTypeIdentity(
                                            packageName = "java.math",
                                            typePath = listOf("BigDecimal"),
                                            kind = CanonicalTypeKind.EXTERNAL,
                                        ),
                                        nullable = true,
                                    ),
                                ),
                                defaultValue = SemanticDefaultExpression("emptyList()", "emptyList()"),
                            ),
                            SemanticValueField(
                                name = "labels",
                                type = SemanticMapTypeRef(
                                    keyType = SemanticBuiltinTypeRef(SemanticBuiltinType.STRING),
                                    valueType = SemanticBuiltinTypeRef(SemanticBuiltinType.STRING, nullable = true),
                                    nullable = true,
                                ),
                            ),
                        ),
                    ),
                ),
            ),
        ).single()

        assertEquals("types/value-object", item.templateId)
        assertEquals("demo-domain/src/main/kotlin/com/acme/demo/domain/shared/values/Money.kt", item.outputPath)
        assertEquals(ArtifactOutputKind.CHECKED_IN_SOURCE, item.outputKind)
        assertEquals(ConflictPolicy.SKIP, item.conflictPolicy)
        assertEquals(listOf("java.math.BigDecimal"), item.context["imports"])
        val fields = item.context["fields"] as List<Map<*, *>>
        assertEquals("List<BigDecimal?>", fields[0]["renderedType"])
        assertEquals("emptyList()", fields[0]["defaultValue"])
        assertEquals("Map<String, String?>?", fields[1]["renderedType"])
        assertEquals(null, fields[1]["defaultValue"])
        assertTrue("storage" !in item.context)
    }

    @Test
    fun `json projection adds top level build owned converter`() {
        val items = ValueObjectArtifactPlanner().plan(
            config(),
            CanonicalModel(
                valueObjects = listOf(
                    valueObject(
                        persistence = JsonValuePersistenceProjection(
                            "com.acme.demo.domain.shared.values.MoneyJsonAttributeConverter"
                        ),
                    ),
                ),
            ),
        )

        assertEquals(2, items.size)
        val semanticValue = items.single { it.outputKind == ArtifactOutputKind.CHECKED_IN_SOURCE }
        val converter = items.single { it.outputKind == ArtifactOutputKind.GENERATED_SOURCE }
        assertEquals("types/value-object", semanticValue.templateId)
        assertEquals("types/value-object-json-converter", converter.templateId)
        assertEquals(ConflictPolicy.OVERWRITE, converter.conflictPolicy)
        assertEquals(
            "demo-domain/build/generated/cap4k/main/kotlin/com/acme/demo/domain/shared/values/MoneyJsonAttributeConverter.kt",
            converter.outputPath,
        )
        assertEquals("demo-domain/build/generated/cap4k/main/kotlin", converter.resolvedOutputRoot)
        assertEquals("MoneyJsonAttributeConverter", converter.context["typeName"])
        assertEquals("com.acme.demo.domain.shared.values.Money", converter.context["valueObjectTypeFqn"])
    }

    @Test
    fun `colliding named simple names render explicit fqns`() {
        val item = ValueObjectArtifactPlanner().plan(
            config(),
            CanonicalModel(
                valueObjects = listOf(
                    valueObject(
                        fields = listOf(
                            SemanticValueField("left", named("com.foo", "Status")),
                            SemanticValueField("right", named("com.bar", "Status")),
                        ),
                    ),
                ),
            ),
        ).single()

        assertEquals(emptyList<String>(), item.context["imports"])
        val fields = item.context["fields"] as List<Map<*, *>>
        assertEquals("com.foo.Status", fields[0]["renderedType"])
        assertEquals("com.bar.Status", fields[1]["renderedType"])
    }

    @Test
    fun `recursive nested value definitions are flattened into the checked in value object`() {
        val addressIdentity = identity(listOf("Money", "Address"))
        val itemIdentity = identity(listOf("Money", "Item"))
        val detailsIdentity = identity(listOf("Money", "Details"))
        val details = nestedValue(
            identity = detailsIdentity,
            fields = listOf(SemanticValueField("code", SemanticBuiltinTypeRef(SemanticBuiltinType.STRING))),
        )
        val item = nestedValue(
            identity = itemIdentity,
            fields = listOf(
                SemanticValueField("name", SemanticBuiltinTypeRef(SemanticBuiltinType.STRING)),
                SemanticValueField("details", SemanticNamedTypeRef(detailsIdentity)),
            ),
            nestedDefinitions = listOf(details),
        )
        val address = nestedValue(
            identity = addressIdentity,
            fields = listOf(
                SemanticValueField("city", SemanticBuiltinTypeRef(SemanticBuiltinType.STRING)),
                SemanticValueField("external", named("com.external", "Address")),
                SemanticValueField("observedAt", named("java.time", "Instant")),
            ),
        )

        val itemPlan = ValueObjectArtifactPlanner().plan(
            config(),
            CanonicalModel(
                valueObjects = listOf(
                    valueObject(
                        fields = listOf(
                            SemanticValueField("address", SemanticNamedTypeRef(addressIdentity)),
                            SemanticValueField(
                                "items",
                                SemanticListTypeRef(SemanticNamedTypeRef(itemIdentity)),
                            ),
                        ),
                        nestedDefinitions = listOf(address, item),
                    )
                )
            ),
        ).single()

        assertEquals(listOf("java.time.Instant"), itemPlan.context["imports"])
        val fields = itemPlan.context["fields"] as List<Map<*, *>>
        assertEquals("Address", fields[0]["renderedType"])
        assertEquals("List<Item>", fields[1]["renderedType"])
        val nestedTypes = itemPlan.context["nestedTypes"] as List<Map<*, *>>
        assertEquals(listOf("Address", "Item", "Details"), nestedTypes.map { it["name"] })
        val addressFields = nestedTypes[0]["fields"] as List<Map<*, *>>
        assertEquals("com.external.Address", addressFields[1]["renderedType"])
        assertEquals("Instant", addressFields[2]["renderedType"])
        val itemFields = nestedTypes[1]["fields"] as List<Map<*, *>>
        assertEquals("Details", itemFields[1]["renderedType"])
    }

    @Test
    fun `colliding flattened nested type names fail instead of dropping a declaration`() {
        val first = nestedValue(
            identity = identity(listOf("Money", "Entry")),
            fields = listOf(SemanticValueField("left", SemanticBuiltinTypeRef(SemanticBuiltinType.STRING))),
        )
        val second = nestedValue(
            identity = identity(listOf("Money", "Container", "Entry")),
            fields = listOf(SemanticValueField("right", SemanticBuiltinTypeRef(SemanticBuiltinType.STRING))),
        )
        val container = nestedValue(
            identity = identity(listOf("Money", "Container")),
            fields = listOf(
                SemanticValueField("entry", SemanticNamedTypeRef(second.identity)),
            ),
            nestedDefinitions = listOf(second),
        )

        val error = assertThrows<IllegalArgumentException> {
            ValueObjectArtifactPlanner().plan(
                config(),
                CanonicalModel(
                    valueObjects = listOf(
                        valueObject(nestedDefinitions = listOf(first, container))
                    )
                ),
            )
        }

        assertEquals(
            "value object com.acme.demo.domain.shared.values.Money has colliding flattened nested type Entry",
            error.message,
        )
    }

    @Test
    fun `value object requires fields and domain module`() {
        val fieldsError = assertThrows<IllegalArgumentException> {
            ValueObjectArtifactPlanner().plan(
                config(),
                CanonicalModel(valueObjects = listOf(valueObject(fields = emptyList()))),
            )
        }
        assertEquals("value object Money must declare at least one field", fieldsError.message)

        val moduleError = assertThrows<IllegalArgumentException> {
            ValueObjectArtifactPlanner().plan(
                config(modules = emptyMap()),
                CanonicalModel(valueObjects = listOf(valueObject())),
            )
        }
        assertEquals("domain module is required", moduleError.message)
    }

    private fun valueObject(
        fields: List<SemanticValueField> = listOf(
            SemanticValueField("amount", SemanticBuiltinTypeRef(SemanticBuiltinType.LONG))
        ),
        persistence: JsonValuePersistenceProjection? = null,
        nestedDefinitions: List<SemanticValueDefinition> = emptyList(),
    ): ValueObjectModel = ValueObjectModel(
        definition = SemanticValueDefinition(
            identity = identity(listOf("Money"), CanonicalTypeKind.VALUE_OBJECT),
            role = SemanticValueRole.VALUE_OBJECT,
            fields = fields,
            nestedDefinitions = nestedDefinitions,
        ),
        persistence = persistence,
    )

    private fun nestedValue(
        identity: CanonicalTypeIdentity,
        fields: List<SemanticValueField>,
        nestedDefinitions: List<SemanticValueDefinition> = emptyList(),
    ): SemanticValueDefinition = SemanticValueDefinition(
        identity = identity,
        role = SemanticValueRole.VALUE_OBJECT,
        fields = fields,
        nestedDefinitions = nestedDefinitions,
    )

    private fun identity(
        typePath: List<String>,
        kind: CanonicalTypeKind = CanonicalTypeKind.NESTED_VALUE,
    ): CanonicalTypeIdentity = CanonicalTypeIdentity(
        packageName = "com.acme.demo.domain.shared.values",
        typePath = typePath,
        kind = kind,
    )

    private fun named(packageName: String, name: String): SemanticNamedTypeRef = SemanticNamedTypeRef(
        CanonicalTypeIdentity(packageName, listOf(name), CanonicalTypeKind.EXTERNAL)
    )

    private fun config(modules: Map<String, String> = mapOf("domain" to "demo-domain")): ProjectConfig =
        ProjectConfig(
            basePackage = "com.acme.demo",
            layout = ProjectLayout.MULTI_MODULE,
            modules = modules,
            templates = TemplateConfig("ddd-default", emptyList(), ConflictPolicy.SKIP),
        )
}
