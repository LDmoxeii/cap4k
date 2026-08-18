package com.only4.cap4k.plugin.pipeline.source.enummanifest

import com.fasterxml.jackson.core.JsonParser
import com.fasterxml.jackson.databind.JsonNode
import com.fasterxml.jackson.databind.node.ArrayNode
import com.fasterxml.jackson.databind.node.ObjectNode
import com.only4.cap4k.plugin.pipeline.api.EnumDeclarationSnapshot
import com.only4.cap4k.plugin.pipeline.api.EnumItemModel
import com.only4.cap4k.plugin.pipeline.api.SharedEnumDefinition
import com.only4.cap4k.plugin.pipeline.api.EnumFieldSnapshot
import com.only4.cap4k.plugin.pipeline.api.EnumItemSnapshot
import com.only4.cap4k.plugin.pipeline.api.EnumLiteralSnapshot
import com.only4.cap4k.plugin.pipeline.api.EnumManifestSnapshot
import com.only4.cap4k.plugin.pipeline.api.PipelineBoundaryAuthorities
import com.only4.cap4k.plugin.pipeline.api.PipelineBoundaryKind
import com.only4.cap4k.plugin.pipeline.api.PipelineCapabilityBoundary
import com.only4.cap4k.plugin.pipeline.api.PipelineCapabilityDescriptor
import com.only4.cap4k.plugin.pipeline.api.PipelineCapabilityKind
import com.only4.cap4k.plugin.pipeline.api.PipelineExecutionLane
import com.only4.cap4k.plugin.pipeline.api.PipelineInputRequirement
import com.only4.cap4k.plugin.pipeline.api.PipelineInputRequirementMatch
import com.only4.cap4k.plugin.pipeline.api.PipelinePublicTasks
import com.only4.cap4k.plugin.pipeline.api.ProjectConfig
import com.only4.cap4k.plugin.pipeline.api.SourceProvider
import com.only4.cap4k.plugin.pipeline.json.PipelineJson
import java.io.File

class EnumManifestSourceProvider : SourceProvider {
    private val objectMapper = PipelineJson.newMapper().apply {
        factory.enable(JsonParser.Feature.STRICT_DUPLICATE_DETECTION)
    }
    override val id: String = "enum-manifest"
    override val descriptor: PipelineCapabilityDescriptor = PipelineCapabilityDescriptor.builtIn(
        providerId = id,
        displayName = "Enum Manifest Source",
        kind = PipelineCapabilityKind.SOURCE,
        module = "cap4k-plugin-pipeline-source-enum-manifest",
        tacticalCarriers = listOf("Enum"),
        executionLanes = listOf(PipelineExecutionLane.AUTHORING),
        tasks = listOf(PipelinePublicTasks.PLAN, PipelinePublicTasks.GENERATE),
        inputRequirements = listOf(
            PipelineInputRequirement(
                id = "enum-manifest-files",
                configurationPaths = listOf("sources.enum-manifest.files", "types.enumManifest.files"),
                match = PipelineInputRequirementMatch.ANY,
            ),
        ),
        boundaries = listOf(
            PipelineCapabilityBoundary(PipelineBoundaryKind.INPUT, PipelineBoundaryAuthorities.PROJECT_INPUT),
            PipelineCapabilityBoundary(PipelineBoundaryKind.GENERATION, PipelineBoundaryAuthorities.PIPELINE_SOURCE),
        ),
    )

    override fun collect(config: ProjectConfig): EnumManifestSnapshot {
        val options = config.sources[id]?.options ?: emptyMap()
        val declarations = resolveFiles(options).flatMap(::parseFile)
        validateDuplicateNames(declarations)
        return EnumManifestSnapshot(
            declarations = declarations,
            definitions = declarations
                .filter { declaration -> declaration.fields.isEmpty() }
                .map { declaration ->
                    SharedEnumDefinition(
                        typeName = declaration.typeName,
                        packageName = declaration.packageName,
                        aggregates = declaration.aggregates,
                        items = declaration.items.map { item -> EnumItemModel(item.value, item.name, item.description) },
                    )
                },
        )
    }

    override fun localInputPaths(config: ProjectConfig): List<String> =
        resolveFiles(config.sources[id]?.options.orEmpty()).map(File::getAbsolutePath)

    private fun validateDuplicateNames(declarations: List<EnumDeclarationSnapshot>) {
        declarations.filter { it.aggregates.isEmpty() }.groupingBy { it.typeName }.eachCount()
            .entries.firstOrNull { it.value > 1 }?.key?.let {
                throw IllegalArgumentException("duplicate shared enum definition: $it")
            }
        declarations.mapNotNull { declaration ->
            declaration.aggregates.singleOrNull()?.let { owner -> owner to declaration.typeName }
        }.groupingBy { it }.eachCount().entries.firstOrNull { it.value > 1 }?.key?.let {
            throw IllegalArgumentException("duplicate aggregate enum definition: ${it.second} in ${it.first}")
        }
    }

    private fun resolveFiles(options: Map<String, Any?>): List<File> {
        val filePaths = options["files"] as? List<*> ?: emptyList<Any?>()
        return filePaths.map { File(it.toString()) }
    }

    private fun parseFile(file: File): List<EnumDeclarationSnapshot> {
        val root = try {
            file.reader(Charsets.UTF_8).use(objectMapper::readTree)
        } catch (error: Exception) {
            throw IllegalArgumentException(
                "enum manifest ${file.path}: invalid JSON: ${error.message.orEmpty().lineSequence().firstOrNull().orEmpty()}",
                error,
            )
        }
        require(root is ArrayNode) { "enum manifest ${file.path}: root must be a JSON array" }
        return root.mapIndexed { enumIndex, element ->
            val enumPath = "${file.path}[$enumIndex]"
            val json = element as? ObjectNode
                ?: throw IllegalArgumentException("enum manifest $enumPath: enum entry must be a JSON object")
            json.requireAllowedKeys(ENUM_KEYS, "enum manifest $enumPath")
            require(!json.has(REMOVED_TRANSLATION_FLAG)) {
                "enum manifest field $REMOVED_TRANSLATION_FLAG is removed; install an enum translation addon instead."
            }
            val typeName = json.requiredString("name", enumPath)
            requireKotlinIdentifier(typeName, "$enumPath.name", "enum type")
            val fields = json.optionalArray("fields", enumPath).mapIndexed { fieldIndex, fieldNode ->
                val fieldPath = "$enumPath.fields[$fieldIndex]"
                val field = fieldNode as? ObjectNode
                    ?: throw IllegalArgumentException("enum manifest $fieldPath: field must be a JSON object")
                field.requireAllowedKeys(FIELD_KEYS, "enum manifest $fieldPath")
                val name = field.requiredString("name", fieldPath)
                requireKotlinIdentifier(name, "$fieldPath.name", "enum property")
                require(name !in RESERVED_PROPERTY_NAMES) {
                    "enum manifest $fieldPath: enum $typeName property $name is reserved"
                }
                EnumFieldSnapshot(name, field.requiredString("type", fieldPath), fieldPath)
            }
            fields.groupBy { it.name }.filterValues { it.size > 1 }.keys.firstOrNull()?.let { duplicate ->
                throw IllegalArgumentException("enum manifest $enumPath: enum $typeName duplicate property $duplicate")
            }
            val fieldNames = fields.map { it.name }.toSet()
            val items = json.requiredArray("items", enumPath).mapIndexed { itemIndex, itemNode ->
                val itemPath = "$enumPath.items[$itemIndex]"
                val item = itemNode as? ObjectNode
                    ?: throw IllegalArgumentException("enum manifest $itemPath: item must be a JSON object")
                item.requireAllowedKeys(ITEM_KEYS + fieldNames, "enum manifest $itemPath")
                val constant = item.requiredString("name", itemPath)
                requireKotlinIdentifier(constant, "$itemPath.name", "enum constant")
                require(constant !in RESERVED_CONSTANT_NAMES) {
                    "enum manifest $itemPath: enum $typeName item $constant conflicts with generated member $constant"
                }
                val missing = fieldNames.filterNot(item::has)
                require(missing.isEmpty()) {
                    "enum manifest $itemPath: enum $typeName item $constant missing properties ${missing.joinToString()}"
                }
                EnumItemSnapshot(
                    value = item.requiredPersistedInt("value", itemPath, typeName, constant),
                    name = constant,
                    description = item.requiredString("desc", itemPath),
                    propertyValues = fields.associate { field ->
                        field.name to item.requiredLiteral(field.name, "$itemPath.${field.name}")
                    },
                    sourcePath = itemPath,
                )
            }
            items.groupBy { it.name }.filterValues { it.size > 1 }.keys.firstOrNull()?.let { duplicate ->
                throw IllegalArgumentException("enum manifest $enumPath: enum $typeName duplicate constant $duplicate")
            }
            items.groupBy { it.value }.filterValues { it.size > 1 }.keys.firstOrNull()?.let { duplicate ->
                throw IllegalArgumentException("enum manifest $enumPath: enum $typeName duplicate persisted value $duplicate")
            }
            val aggregates = json.optionalStringArray("aggregates", enumPath)
            require(aggregates.size <= 1) { "enum $typeName may declare at most one aggregate" }
            EnumDeclarationSnapshot(
                typeName = typeName,
                packageName = json.requiredString("package", enumPath),
                aggregates = aggregates,
                fields = fields,
                items = items,
                sourcePath = enumPath,
            )
        }
    }
}


private val REMOVED_TRANSLATION_FLAG = "generate" + "Translation"
private val ENUM_KEYS = setOf("name", "package", "aggregates", "fields", "items", REMOVED_TRANSLATION_FLAG)
private val FIELD_KEYS = setOf("name", "type")
private val ITEM_KEYS = setOf("value", "name", "desc")
private val RESERVED_PROPERTY_NAMES = setOf(
    "value", "name", "description", "desc", "Converter", "valueOfOrNull", "entries", "values", "valueOf"
)
private val RESERVED_CONSTANT_NAMES = setOf("Converter", "valueOfOrNull", "entries", "values", "valueOf")
private val IDENTIFIER = Regex("[A-Za-z_][A-Za-z0-9_]*")
private val KOTLIN_KEYWORDS = setOf("as", "break", "class", "continue", "do", "else", "false", "for", "fun", "if", "in", "interface", "is", "null", "object", "package", "return", "super", "this", "throw", "true", "try", "typealias", "typeof", "val", "var", "when", "while")

private fun requireKotlinIdentifier(value: String, path: String, identity: String) {
    require(IDENTIFIER.matches(value) && value !in KOTLIN_KEYWORDS) {
        "enum manifest $path: invalid Kotlin $identity identity '$value'"
    }
}

private fun ObjectNode.requireAllowedKeys(allowed: Set<String>, path: String) {
    val unknown = fieldNames().asSequence().filterNot { it in allowed }.toList()
    require(unknown.isEmpty()) { "$path: unknown fields ${unknown.joinToString()}" }
}

private fun ObjectNode.requiredString(field: String, path: String): String {
    val node = get(field) ?: throw IllegalArgumentException("enum manifest $path: missing field $field")
    require(node.isTextual) { "enum manifest $path.$field: expected JSON string" }
    return node.textValue().also { require(it.isNotBlank()) { "enum manifest $path.$field: must not be blank" } }
}

private fun ObjectNode.requiredArray(field: String, path: String): ArrayNode =
    (get(field) ?: throw IllegalArgumentException("enum manifest $path: missing field $field")) as? ArrayNode
        ?: throw IllegalArgumentException("enum manifest $path.$field: expected JSON array")

private fun ObjectNode.optionalArray(field: String, path: String): ArrayNode = when {
    !has(field) -> PipelineJson.newMapper().createArrayNode()
    get(field) is ArrayNode -> get(field) as ArrayNode
    else -> throw IllegalArgumentException("enum manifest $path.$field: expected JSON array")
}

private fun ObjectNode.optionalStringArray(field: String, path: String): List<String> =
    if (!has(field) || get(field).isNull) emptyList() else requiredArray(field, path).mapIndexed { index, node ->
        require(node.isTextual && node.textValue().isNotBlank()) {
            "enum manifest $path.$field[$index]: expected non-blank JSON string"
        }
        node.textValue()
    }

private fun ObjectNode.requiredPersistedInt(field: String, path: String, enumName: String, itemName: String): Int {
    val node = get(field) ?: throw IllegalArgumentException("enum manifest $path: missing field $field")
    require(node.isIntegralNumber) {
        "enum manifest $path.$field: enum $enumName item $itemName persisted value must be a JSON integral number"
    }
    return try {
        node.bigIntegerValue().intValueExact()
    } catch (_: ArithmeticException) {
        throw IllegalArgumentException("enum manifest $path.$field: enum $enumName item $itemName persisted value is outside Int range")
    }
}

private fun ObjectNode.requiredLiteral(field: String, sourcePath: String): EnumLiteralSnapshot {
    val node = get(field) ?: throw IllegalArgumentException("enum manifest $sourcePath: missing property $field")
    return when {
        node.isNull -> EnumLiteralSnapshot.Null(sourcePath)
        node.isTextual -> EnumLiteralSnapshot.StringValue(node.textValue(), sourcePath)
        node.isBoolean -> EnumLiteralSnapshot.BooleanValue(node.booleanValue(), sourcePath)
        node.isIntegralNumber -> EnumLiteralSnapshot.IntegerValue(node.bigIntegerValue(), sourcePath)
        node.isFloatingPointNumber -> EnumLiteralSnapshot.DecimalValue(node.decimalValue(), sourcePath)
        else -> throw IllegalArgumentException("enum manifest $sourcePath: property literal must be null, string, boolean, or number")
    }
}
