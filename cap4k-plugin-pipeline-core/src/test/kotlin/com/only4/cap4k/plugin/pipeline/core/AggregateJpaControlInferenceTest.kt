package com.only4.cap4k.plugin.pipeline.core

import com.only4.cap4k.plugin.pipeline.api.ArtifactLayoutResolver
import com.only4.cap4k.plugin.pipeline.api.CanonicalTypeIdentity
import com.only4.cap4k.plugin.pipeline.api.CanonicalTypeKind
import com.only4.cap4k.plugin.pipeline.api.DbColumnSnapshot
import com.only4.cap4k.plugin.pipeline.api.DbSchemaSnapshot
import com.only4.cap4k.plugin.pipeline.api.DbTableSnapshot
import com.only4.cap4k.plugin.pipeline.api.EntityModel
import com.only4.cap4k.plugin.pipeline.api.FieldModel
import com.only4.cap4k.plugin.pipeline.api.JsonValuePersistenceProjection
import com.only4.cap4k.plugin.pipeline.api.SemanticBuiltinType
import com.only4.cap4k.plugin.pipeline.api.SemanticBuiltinTypeRef
import com.only4.cap4k.plugin.pipeline.api.SemanticValueDefinition
import com.only4.cap4k.plugin.pipeline.api.SemanticValueField
import com.only4.cap4k.plugin.pipeline.api.SemanticValueRole
import com.only4.cap4k.plugin.pipeline.api.ValueObjectModel
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows
import java.sql.Types

class AggregateJpaControlInferenceTest {

    @Test
    fun `json value object uses explicit top level converter fqn`() {
        val jpa = infer(
            typeBinding = "Snapshot",
            valueObject = valueObject(
                persistence = JsonValuePersistenceProjection(
                    "com.acme.demo.domain.shared.values.SnapshotJsonAttributeConverter"
                )
            ),
        )

        val column = jpa.columns.single { it.fieldName == "snapshot" }
        assertEquals("com.acme.demo.domain.shared.values.Snapshot", column.converterTypeFqn)
        assertEquals(
            "com.acme.demo.domain.shared.values.SnapshotJsonAttributeConverter",
            column.converterClassFqn,
        )
    }

    @Test
    fun `manifest value object resolves explicit fqn before generic nested converter fallback`() {
        val jpa = infer(
            typeBinding = "com.acme.demo.domain.shared.values.Snapshot",
            valueObject = valueObject(
                persistence = JsonValuePersistenceProjection(
                    "com.acme.demo.domain.shared.values.SnapshotJsonAttributeConverter"
                )
            ),
        )

        assertEquals(
            "com.acme.demo.domain.shared.values.SnapshotJsonAttributeConverter",
            jpa.columns.single { it.fieldName == "snapshot" }.converterClassFqn,
        )
    }

    @Test
    fun `database field cannot bind non persistent value object`() {
        val error = assertThrows<IllegalArgumentException> {
            infer(typeBinding = "Snapshot", valueObject = valueObject(persistence = null))
        }

        assertEquals(
            "value object com.acme.demo.domain.shared.values.Snapshot has no persistence projection " +
                "for database type binding Snapshot",
            error.message,
        )
    }

    private fun infer(typeBinding: String, valueObject: ValueObjectModel) =
        AggregateJpaControlInference.fromModel(
            entities = listOf(
                EntityModel(
                    name = "Content",
                    packageName = "com.acme.demo.domain.aggregates.content",
                    tableName = "content",
                    comment = "content",
                    fields = listOf(
                        FieldModel("id", "Long", columnName = "id"),
                        FieldModel("snapshot", "Snapshot", typeBinding = typeBinding, columnName = "snapshot"),
                    ),
                    idField = FieldModel("id", "Long", columnName = "id"),
                )
            ),
            schema = DbSchemaSnapshot(
                tables = listOf(
                    DbTableSnapshot(
                        tableName = "content",
                        comment = "content",
                        columns = listOf(
                            DbColumnSnapshot(
                                name = "id",
                                dbType = "bigint",
                                kotlinType = "Long",
                                nullable = false,
                                isPrimaryKey = true,
                                jdbcType = Types.BIGINT,
                            ),
                            DbColumnSnapshot(
                                name = "snapshot",
                                dbType = "varchar",
                                kotlinType = "String",
                                nullable = true,
                                typeBinding = typeBinding,
                                jdbcType = Types.VARCHAR,
                            ),
                        ),
                        primaryKey = listOf("id"),
                        uniqueConstraints = emptyList(),
                    )
                )
            ),
            sharedEnums = emptyList(),
            valueObjects = listOf(valueObject),
            typeRegistry = emptyMap(),
            artifactLayout = ArtifactLayoutResolver("com.acme.demo"),
        ).single()

    private fun valueObject(persistence: JsonValuePersistenceProjection?): ValueObjectModel = ValueObjectModel(
        definition = SemanticValueDefinition(
            identity = CanonicalTypeIdentity(
                packageName = "com.acme.demo.domain.shared.values",
                typePath = listOf("Snapshot"),
                kind = CanonicalTypeKind.VALUE_OBJECT,
            ),
            role = SemanticValueRole.VALUE_OBJECT,
            fields = listOf(
                SemanticValueField("state", SemanticBuiltinTypeRef(SemanticBuiltinType.STRING))
            ),
        ),
        persistence = persistence,
    )
}
