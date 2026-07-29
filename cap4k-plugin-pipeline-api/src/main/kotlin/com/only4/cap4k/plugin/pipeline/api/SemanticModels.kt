package com.only4.cap4k.plugin.pipeline.api

/** Source-level structured-value field. The type expression is compiled before generator planning. */
data class SemanticFieldSnapshot(
    val name: String,
    val typeExpression: String,
    val defaultValue: String? = null,
    val sourcePath: String = name,
)

enum class CanonicalTypeKind {
    BUILTIN,
    EXTERNAL,
    ENTITY,
    STRONG_ID,
    ENUM,
    VALUE_OBJECT,
    CREATION_VALUE,
    NESTED_VALUE,
}

data class CanonicalTypeIdentity(
    val packageName: String,
    val typePath: List<String>,
    val kind: CanonicalTypeKind,
    val ownerAggregateName: String? = null,
) {
    init {
        require(typePath.isNotEmpty() && typePath.none { it.isBlank() }) {
            "canonical type identity must declare a non-empty type path"
        }
    }

    val simpleName: String
        get() = typePath.last()

    val fqn: String
        get() = (listOf(packageName).filter { it.isNotBlank() } + typePath).joinToString(".")
}

enum class SemanticBuiltinType {
    ANY,
    BOOLEAN,
    BYTE,
    CHAR,
    DOUBLE,
    FLOAT,
    INT,
    LONG,
    NOTHING,
    NUMBER,
    SHORT,
    STRING,
    UNIT,
}

sealed interface SemanticTypeRef {
    val nullable: Boolean
}

data class SemanticBuiltinTypeRef(
    val kind: SemanticBuiltinType,
    override val nullable: Boolean = false,
) : SemanticTypeRef

data class SemanticNamedTypeRef(
    val symbol: CanonicalTypeIdentity,
    override val nullable: Boolean = false,
) : SemanticTypeRef

data class SemanticListTypeRef(
    val elementType: SemanticTypeRef,
    override val nullable: Boolean = false,
) : SemanticTypeRef

data class SemanticSetTypeRef(
    val elementType: SemanticTypeRef,
    override val nullable: Boolean = false,
) : SemanticTypeRef

data class SemanticMapTypeRef(
    val keyType: SemanticTypeRef,
    val valueType: SemanticTypeRef,
    override val nullable: Boolean = false,
) : SemanticTypeRef

data class SemanticDefaultExpression(
    val kotlinExpression: String,
    val sourceExpression: String,
)

data class SemanticValueField(
    val name: String,
    val type: SemanticTypeRef,
    val defaultValue: SemanticDefaultExpression? = null,
    val sourcePath: String = name,
)

enum class SemanticValueRole {
    COMMAND_REQUEST,
    COMMAND_RESPONSE,
    QUERY_REQUEST,
    QUERY_RESPONSE,
    CLIENT_REQUEST,
    CLIENT_RESPONSE,
    API_PAYLOAD_REQUEST,
    API_PAYLOAD_RESPONSE,
    DOMAIN_EVENT,
    INTEGRATION_EVENT,
    SAGA_REQUEST,
    SAGA_RESPONSE,
    VALUE_OBJECT,
    OWNED_ENTITY_CREATION,
    FACTORY_PAYLOAD,
}

sealed interface SemanticValueEnvelope {
    data class Page(
        val itemDefinition: SemanticValueDefinition,
    ) : SemanticValueEnvelope
}

data class SemanticValueDefinition(
    val identity: CanonicalTypeIdentity,
    val role: SemanticValueRole,
    val fields: List<SemanticValueField> = emptyList(),
    val nestedDefinitions: List<SemanticValueDefinition> = emptyList(),
    val envelope: SemanticValueEnvelope? = null,
)

data class ValueObjectPersistenceSnapshot(
    val kind: String,
    val options: Map<String, String> = emptyMap(),
)

sealed interface ValuePersistenceProjection {
    val kind: String
}

data class JsonValuePersistenceProjection(
    val converterClassFqn: String,
) : ValuePersistenceProjection {
    override val kind: String = "json"
}

data class ValueObjectDeclarationSnapshot(
    val name: String,
    val packageName: String,
    val aggregates: List<String> = emptyList(),
    val persistence: ValueObjectPersistenceSnapshot? = null,
    val fields: List<SemanticFieldSnapshot> = emptyList(),
    val description: String? = null,
)

data class AggregateCreationRelationModel(
    val path: List<String>,
    val ownerEntity: CanonicalTypeIdentity,
    val targetEntity: CanonicalTypeIdentity,
    val fieldName: String,
    val cardinality: OwnedRelationCardinality,
    val attachmentAccessorName: String,
)

data class AggregateCreationNodeModel(
    val entity: CanonicalTypeIdentity,
    val value: SemanticValueDefinition,
    val constructorFieldNames: List<String>,
    val relations: List<AggregateCreationRelationModel> = emptyList(),
)

data class AggregateCreationGraphModel(
    val rootEntity: CanonicalTypeIdentity,
    val factoryPayload: SemanticValueDefinition,
    val rootConstructorFieldNames: List<String>,
    val ownedNodes: List<AggregateCreationNodeModel>,
    val relations: List<AggregateCreationRelationModel>,
)

