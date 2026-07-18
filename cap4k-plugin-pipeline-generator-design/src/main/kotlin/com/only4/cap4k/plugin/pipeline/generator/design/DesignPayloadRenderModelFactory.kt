package com.only4.cap4k.plugin.pipeline.generator.design

import com.only4.cap4k.plugin.pipeline.api.DesignBlockModel
import com.only4.cap4k.plugin.pipeline.api.EntityModel
import com.only4.cap4k.plugin.pipeline.api.FieldModel
import com.only4.cap4k.plugin.pipeline.generator.design.types.AGGREGATE_SOURCE
import com.only4.cap4k.plugin.pipeline.generator.design.types.DesignSymbolRegistry
import com.only4.cap4k.plugin.pipeline.generator.design.types.EXPLICIT_FQCN_SOURCE
import com.only4.cap4k.plugin.pipeline.generator.design.types.ImportResolver
import com.only4.cap4k.plugin.pipeline.generator.design.types.ImportResolver.UnknownShortTypeFailure
import com.only4.cap4k.plugin.pipeline.generator.design.types.SymbolIdentity
import java.util.ArrayDeque

internal object DesignPayloadRenderModelFactory {
    private val builtInTypeNames = setOf(
        "Any",
        "Array",
        "Boolean",
        "Byte",
        "Char",
        "Collection",
        "Double",
        "Float",
        "Int",
        "Iterable",
        "List",
        "Long",
        "Map",
        "MutableCollection",
        "MutableIterable",
        "MutableList",
        "MutableMap",
        "MutableSet",
        "Nothing",
        "Number",
        "Sequence",
        "Pair",
        "Triple",
        "Set",
        "Short",
        "String",
        "Unit",
    )

    private val collectionWrapperTypes = setOf(
        "Collection",
        "Iterable",
        "List",
        "MutableCollection",
        "MutableList",
        "MutableSet",
        "Set",
    )

    fun createForCommandBlock(
        packageName: String,
        block: DesignBlockModel,
        symbolRegistry: DesignSymbolRegistry = DesignSymbolRegistry(),
        siblingTypeNames: Set<String> = emptySet(),
    ): DesignRenderModel = createForBlock(
        packageName = packageName,
        typeName = block.commandTypeName(),
        description = block.description,
        fields = block.fields,
        resultFields = block.resultFields,
        symbolRegistry = symbolRegistry,
        aggregateContext = block.aggregates,
        siblingRequestTypeNames = siblingTypeNames,
    )

    fun createForQueryBlock(
        packageName: String,
        block: DesignBlockModel,
        pageRequest: Boolean,
        symbolRegistry: DesignSymbolRegistry = DesignSymbolRegistry(),
        siblingTypeNames: Set<String> = emptySet(),
    ): DesignRenderModel = createForBlock(
        packageName = packageName,
        typeName = block.queryTypeName(),
        description = block.description,
        fields = block.fields,
        resultFields = block.resultFields,
        symbolRegistry = symbolRegistry,
        aggregateContext = block.aggregates,
        siblingRequestTypeNames = siblingTypeNames,
        pageRequest = pageRequest,
    )

    fun createForClientBlock(
        packageName: String,
        block: DesignBlockModel,
        symbolRegistry: DesignSymbolRegistry = DesignSymbolRegistry(),
        siblingTypeNames: Set<String> = emptySet(),
    ): DesignRenderModel = createForBlock(
        packageName = packageName,
        typeName = block.clientTypeName(),
        description = block.description,
        fields = block.fields,
        resultFields = block.resultFields,
        symbolRegistry = symbolRegistry,
        aggregateContext = block.aggregates,
        siblingRequestTypeNames = siblingTypeNames,
    )

    fun createForApiPayloadBlock(
        packageName: String,
        block: DesignBlockModel,
        pageRequest: Boolean,
        symbolRegistry: DesignSymbolRegistry = DesignSymbolRegistry(),
    ): DesignRenderModel = createForBlock(
        packageName = packageName,
        typeName = block.apiPayloadTypeName(),
        description = block.description,
        fields = block.fields,
        resultFields = block.resultFields,
        symbolRegistry = symbolRegistry,
        aggregateContext = block.aggregates,
        pageRequest = pageRequest,
    )

    fun createForDomainEventBlock(
        packageName: String,
        block: DesignBlockModel,
        aggregate: EntityModel,
        symbolRegistry: DesignSymbolRegistry = DesignSymbolRegistry(),
    ): DesignRenderModel = createForBlock(
        packageName = packageName,
        typeName = block.domainEventTypeName(),
        description = block.description,
        aggregateName = aggregate.name,
        aggregatePackageName = aggregate.packageName,
        fields = block.fields,
        resultFields = emptyList(),
        symbolRegistry = symbolRegistry,
        aggregateContext = block.aggregates,
    )

    fun createForIntegrationEventBlock(
        packageName: String,
        block: DesignBlockModel,
        symbolRegistry: DesignSymbolRegistry = DesignSymbolRegistry(),
    ): DesignRenderModel = createForBlock(
        packageName = packageName,
        typeName = block.integrationEventTypeName(),
        description = block.description,
        fields = block.fields,
        resultFields = emptyList(),
        symbolRegistry = symbolRegistry,
        aggregateContext = block.aggregates,
    )

    fun createForSagaBlock(
        packageName: String,
        block: DesignBlockModel,
        symbolRegistry: DesignSymbolRegistry = DesignSymbolRegistry(),
    ): DesignRenderModel = createForBlock(
        packageName = packageName,
        typeName = block.name,
        description = block.description,
        fields = block.fields,
        resultFields = block.resultFields,
        symbolRegistry = symbolRegistry,
        aggregateContext = block.aggregates,
    )

    private fun createForBlock(
        packageName: String,
        typeName: String,
        description: String,
        fields: List<FieldModel>,
        resultFields: List<FieldModel>,
        symbolRegistry: DesignSymbolRegistry,
        aggregateContext: List<String>,
        siblingRequestTypeNames: Set<String> = emptySet(),
        pageRequest: Boolean = false,
        aggregateName: String? = null,
        aggregatePackageName: String? = null,
    ): DesignRenderModel {
        val requestNamespace = buildNamespace(fields, "request")
        val responseNamespace = buildNamespace(resultFields, "response")
        return createRenderModel(
            packageName = packageName,
            typeName = typeName,
            description = description,
            aggregateName = aggregateName,
            aggregatePackageName = aggregatePackageName,
            requestNamespace = requestNamespace,
            responseNamespace = responseNamespace,
            symbolRegistry = symbolRegistry,
            aggregateContext = aggregateContext,
            siblingRequestTypeNames = siblingRequestTypeNames,
            pageRequest = pageRequest,
        )
    }

    private fun createRenderModel(
        packageName: String,
        typeName: String,
        description: String,
        aggregateName: String?,
        aggregatePackageName: String?,
        requestNamespace: NamespaceModel,
        responseNamespace: NamespaceModel,
        symbolRegistry: DesignSymbolRegistry,
        aggregateContext: List<String>,
        siblingRequestTypeNames: Set<String> = emptySet(),
        pageRequest: Boolean = false,
    ): DesignRenderModel {
        val resolvedSymbolRegistry = buildSymbolRegistry(
            aggregateName = aggregateName,
            aggregatePackageName = aggregatePackageName,
            requestNamespace = requestNamespace,
            responseNamespace = responseNamespace,
            symbolRegistry = symbolRegistry,
        )
        validateNamespaceTypes(
            "request",
            requestNamespace,
            resolvedSymbolRegistry,
            requestNamespace.nestedTypeNames,
            aggregateContext,
            siblingRequestTypeNames,
        )
        validateNamespaceTypes(
            "response",
            responseNamespace,
            resolvedSymbolRegistry,
            responseNamespace.nestedTypeNames,
            aggregateContext,
            siblingRequestTypeNames,
        )
        val requestImportPlan = DesignImportPlanner.plan(
            types = requestNamespace.resolvedTypes,
            innerTypeNames = requestNamespace.nestedTypeNames,
            symbolRegistry = resolvedSymbolRegistry,
            aggregateContext = aggregateContext,
        )
        val responseImportPlan = DesignImportPlanner.plan(
            types = responseNamespace.resolvedTypes,
            innerTypeNames = responseNamespace.nestedTypeNames,
            symbolRegistry = resolvedSymbolRegistry,
            aggregateContext = aggregateContext,
        )
        val requestRenderedTypes = ArrayDeque(requestImportPlan.renderedTypes)
        val responseRenderedTypes = ArrayDeque(responseImportPlan.renderedTypes)
        val fields = renderFields(requestNamespace.fields, requestRenderedTypes)
        val nestedTypes = renderNestedTypes(requestNamespace.nestedTypes, requestRenderedTypes)
        val resultFields = renderFields(responseNamespace.fields, responseRenderedTypes)
        val resultNestedTypes = renderNestedTypes(responseNamespace.nestedTypes, responseRenderedTypes)

        return DesignRenderModel(
            packageName = packageName,
            typeName = typeName,
            description = description,
            descriptionText = description,
            descriptionCommentText = description.toKDocCommentText(),
            descriptionKotlinStringLiteral = description.toKotlinStringLiteral(),
            aggregateName = aggregateName,
            imports = (requestImportPlan.imports + responseImportPlan.imports).distinct().sorted(),
            fields = fields,
            resultFields = resultFields,
            nestedTypes = nestedTypes,
            resultNestedTypes = resultNestedTypes,
            pageRequest = pageRequest,
        )
    }

    private fun buildNamespace(
        fields: List<FieldModel>,
        namespace: String,
    ): NamespaceModel {
        val root = PayloadPathNode(name = "__root__", path = emptyList())

        fields.forEach { field ->
            val segments = parseFieldPath(field.name)
            var current = root
            segments.forEach { segment ->
                val child = current.children.getOrPut(segment.name) {
                    PayloadPathNode(name = segment.name, path = current.path + segment.name)
                }
                if (segment.list) {
                    child.list = true
                }
                current = child
            }

            if (isCollectionType(field.type)) {
                current.list = true
            }
            current.explicitDeclarations += field
        }

        val nestedTypeNames = linkedSetOf<String>()
        val nestedTypeNodes = mutableListOf<PayloadPathNode>()
        validateDuplicateDeclarations(root, namespace)
        collectNestedTypeNodes(root, namespace, nestedTypeNames, nestedTypeNodes)

        return NamespaceModel(
            fields = root.children.values.map { node ->
                node.toPreparedField(namespace, nestedTypeNames)
            },
            nestedTypes = nestedTypeNodes.map { node ->
                PreparedNestedTypeModel(
                    name = requireNotNull(node.nestedTypeName),
                    fields = node.children.values.map { child ->
                        child.toPreparedField(namespace, nestedTypeNames)
                    },
                )
            },
            nestedTypeNames = nestedTypeNames,
        )
    }

    private fun renderFields(
        fields: List<PreparedFieldModel>,
        renderedTypes: ArrayDeque<DesignRenderedTypeModel>,
    ): List<DesignRenderFieldModel> {
        return fields.map { field ->
            field.toRenderField(renderedType = renderedTypes.removeFirst().renderedText.withNullability(field.nullable))
        }
    }

    private fun buildSymbolRegistry(
        aggregateName: String?,
        aggregatePackageName: String?,
        requestNamespace: NamespaceModel,
        responseNamespace: NamespaceModel,
        symbolRegistry: DesignSymbolRegistry,
    ): DesignSymbolRegistry {
        val registry = DesignSymbolRegistry(symbolRegistry.allSymbols())
        val resolvedAggregateName = aggregateName?.takeIf { it.isNotBlank() }
        val resolvedAggregatePackageName = aggregatePackageName?.takeIf { it.isNotBlank() }
        if (resolvedAggregateName != null && resolvedAggregatePackageName != null) {
            registry.register(
                SymbolIdentity(
                    packageName = resolvedAggregatePackageName,
                    typeName = resolvedAggregateName,
                    moduleRole = "domain",
                    source = AGGREGATE_SOURCE,
                ),
            )
        }

        (requestNamespace.resolvedTypes + responseNamespace.resolvedTypes)
            .flatMap(::collectExplicitSymbols)
            .forEach(registry::register)

        return registry
    }

    private fun collectExplicitSymbols(type: DesignResolvedTypeModel): List<SymbolIdentity> {
        val own = if (type.kind == DesignResolvedTypeKind.EXPLICIT_FQCN) {
            listOf(
                SymbolIdentity(
                    packageName = type.rawText.substringBeforeLast('.', missingDelimiterValue = ""),
                    typeName = type.simpleName,
                    source = EXPLICIT_FQCN_SOURCE,
                ),
            )
        } else {
            emptyList()
        }

        return own + type.arguments.flatMap(::collectExplicitSymbols)
    }

    private fun validateNamespaceTypes(
        namespace: String,
        model: NamespaceModel,
        symbolRegistry: DesignSymbolRegistry,
        innerTypeNames: Set<String>,
        aggregateContext: List<String>,
        siblingRequestTypeNames: Set<String> = emptySet(),
    ) {
        model.fields.forEach { validateFieldType(it, symbolRegistry, innerTypeNames, aggregateContext, siblingRequestTypeNames) }
        model.nestedTypes.forEach { nestedType ->
            nestedType.fields.forEach { validateFieldType(it, symbolRegistry, innerTypeNames, aggregateContext, siblingRequestTypeNames) }
        }
    }

    private fun validateFieldType(
        field: PreparedFieldModel,
        symbolRegistry: DesignSymbolRegistry,
        innerTypeNames: Set<String>,
        aggregateContext: List<String>,
        siblingRequestTypeNames: Set<String>,
    ) {
        try {
            ImportResolver.resolve(
                type = field.resolvedType,
                innerTypeNames = innerTypeNames,
                symbolRegistry = symbolRegistry,
                aggregateContext = aggregateContext,
            )
        } catch (ex: IllegalArgumentException) {
            val advisory = if (ex is UnknownShortTypeFailure) {
                val shortTypeName = ex.shortType
                val siblingAdvisory = if (shortTypeName in siblingRequestTypeNames) {
                    "; sibling design-entry references are not supported"
                } else {
                    ""
                }
                "; use a fully qualified name, declare cap4k-owned enum/value-object types in " +
                    "types.enumManifest or types.valueObjectManifest, or register external handwritten types in " +
                    "types.registryFile$siblingAdvisory"
            } else {
                ""
            }
            throw IllegalArgumentException(
                "failed to resolve type for field ${field.sourceName}: ${field.resolvedType.rawText} (${ex.message}$advisory)",
                ex,
            )
        }
    }

    private fun renderNestedTypes(
        nestedTypes: List<PreparedNestedTypeModel>,
        renderedTypes: ArrayDeque<DesignRenderedTypeModel>,
    ): List<DesignRenderNestedTypeModel> {
        return nestedTypes.map { nestedType ->
            DesignRenderNestedTypeModel(
                name = nestedType.name,
                fields = renderFields(nestedType.fields, renderedTypes),
            )
        }
    }

    private fun parseFieldPath(path: String): List<FieldPathSegment> {
        val parts = path.trim().split('.')
        require(parts.isNotEmpty() && parts.none { it.isBlank() }) {
            "blank or malformed nested field path: $path"
        }

        return parts.map { rawPart ->
            val list = rawPart.endsWith("[]")
            val name = rawPart.removeSuffix("[]")
            require(name.isNotBlank() && !name.contains('[') && !name.contains(']')) {
                "blank or malformed nested field path: $path"
            }
            FieldPathSegment(name = name, list = list)
        }
    }

    private fun collectNestedTypeNodes(
        node: PayloadPathNode,
        namespace: String,
        nestedTypeNames: MutableSet<String>,
        nestedTypeNodes: MutableList<PayloadPathNode>,
    ) {
        node.children.values.forEach { child ->
            if (isPageDataType(child.explicitDeclarations.singleOrNull()?.type)) {
                val pageEnvelopeItemTypeName = validatePageEnvelopeItemTypeName(child, namespace)
                collectPageEnvelopeItemNode(
                    pageNode = child,
                    itemTypeName = pageEnvelopeItemTypeName,
                    namespace = namespace,
                    nestedTypeNames = nestedTypeNames,
                    nestedTypeNodes = nestedTypeNodes,
                )
                return@forEach
            }

            if (child.children.isNotEmpty()) {
                val nestedTypeName = validateNestedContainer(child, namespace)
                if (!nestedTypeNames.add(nestedTypeName)) {
                    throw IllegalArgumentException("duplicate nested type name in $namespace namespace: $nestedTypeName")
                }
                child.nestedTypeName = nestedTypeName
                nestedTypeNodes += child
                collectNestedTypeNodes(child, namespace, nestedTypeNames, nestedTypeNodes)
            }
        }
    }

    private fun collectPageEnvelopeItemNode(
        pageNode: PayloadPathNode,
        itemTypeName: String,
        namespace: String,
        nestedTypeNames: MutableSet<String>,
        nestedTypeNodes: MutableList<PayloadPathNode>,
    ) {
        val listNode = pageNode.children["list"] ?: return
        if (listNode.children.isEmpty()) {
            return
        }
        if (!nestedTypeNames.add(itemTypeName)) {
            throw IllegalArgumentException("duplicate nested type name in $namespace namespace: $itemTypeName")
        }
        listNode.nestedTypeName = itemTypeName
        nestedTypeNodes += listNode
        collectNestedTypeNodes(listNode, namespace, nestedTypeNames, nestedTypeNodes)
    }

    private fun validatePageEnvelopeItemTypeName(
        node: PayloadPathNode,
        namespace: String,
    ): String {
        if (namespace != "response") {
            throw IllegalArgumentException(
                "PageData envelope in $namespace namespace is only supported in response fields",
            )
        }
        if (node.path.size != 1 || node.name != "page") {
            throw IllegalArgumentException(
                "PageData envelope in $namespace namespace is only supported for root field page",
            )
        }
        if (node.children.keys != setOf("list")) {
            throw IllegalArgumentException(
                "PageData field page in $namespace namespace must declare nested item fields only under list[]",
            )
        }
        val listNode = node.children["list"]
        if (listNode == null || !listNode.list || listNode.children.isEmpty()) {
            throw IllegalArgumentException(
                "PageData field page in $namespace namespace must declare nested item fields only under list[]",
            )
        }

        val directFieldType = node.explicitDeclarations.singleOrNull()?.type?.trim().orEmpty()
        val genericStart = directFieldType.indexOf('<')
        val genericEnd = directFieldType.lastIndexOf('>')
        require(genericStart >= 0 && genericEnd > genericStart) {
            "PageData field page in $namespace namespace must declare nested item fields only under list[]"
        }
        val arguments = splitGenericArguments(directFieldType.substring(genericStart + 1, genericEnd))
        return arguments.singleOrNull()?.let(::simpleNestedTypeCandidate)
            ?: throw IllegalArgumentException(
                "PageData field page in $namespace namespace must declare nested item fields only under list[]",
            )
    }

    private fun isPageDataType(type: String?): Boolean {
        val trimmed = type?.trim().orEmpty()
        val rootType = trimmed.substringBefore('<')
        return simpleTypeName(rootType) == "PageData"
    }

    private fun validateDuplicateDeclarations(
        node: PayloadPathNode,
        namespace: String,
    ) {
        node.children.values.forEach { child ->
            if (child.explicitDeclarations.size > 1) {
                throw duplicateDirectContainerError(child, namespace)
            }
            validateDuplicateDeclarations(child, namespace)
        }
    }

    private fun validateNestedContainer(
        node: PayloadPathNode,
        namespace: String,
    ): String {
        val fallbackTypeName = toNestedTypeName(node.name)
        val directField = when (node.explicitDeclarations.size) {
            0 -> throw missingDirectContainerError(node, namespace, fallbackTypeName)
            1 -> node.explicitDeclarations.single()
            else -> throw duplicateDirectContainerError(node, namespace)
        }

        val nestedTypeName = nestedTypeNameFor(node)
        if (!isCompatibleRootField(directField.type, nestedTypeName)) {
            throw incompatibleDirectContainerError(node, namespace, nestedTypeName)
        }

        return nestedTypeName
    }

    private fun missingDirectContainerError(
        node: PayloadPathNode,
        namespace: String,
        nestedTypeName: String,
    ): IllegalArgumentException {
        return if (node.path.size == 1) {
            IllegalArgumentException(
                "missing compatible direct root field for nested type $nestedTypeName in $namespace namespace",
            )
        } else {
            IllegalArgumentException(
                "missing compatible direct field for nested type $nestedTypeName at ${node.pathText} " +
                    "in $namespace namespace",
            )
        }
    }

    private fun duplicateDirectContainerError(
        node: PayloadPathNode,
        namespace: String,
    ): IllegalArgumentException {
        return if (node.path.size == 1) {
            IllegalArgumentException("duplicate direct root declarations for ${node.name} in $namespace namespace")
        } else {
            IllegalArgumentException("duplicate direct declarations for ${node.pathText} in $namespace namespace")
        }
    }

    private fun incompatibleDirectContainerError(
        node: PayloadPathNode,
        namespace: String,
        nestedTypeName: String,
    ): IllegalArgumentException {
        return if (node.path.size == 1) {
            IllegalArgumentException(
                "direct root field ${node.name} in $namespace namespace must point to nested type $nestedTypeName",
            )
        } else {
            IllegalArgumentException(
                "direct field ${node.pathText} in $namespace namespace must point to nested type $nestedTypeName",
            )
        }
    }

    private fun nestedTypeNameFor(node: PayloadPathNode): String {
        val explicitType = node.explicitDeclarations.firstOrNull()?.type?.trim().orEmpty()
        return nestedTypeCandidate(explicitType) ?: toNestedTypeName(node.name)
    }

    private fun nestedTypeCandidate(type: String): String? {
        if (type.isBlank()) {
            return null
        }

        val genericStart = type.indexOf('<')
        if (genericStart < 0) {
            return simpleNestedTypeCandidate(type)
        }

        val genericEnd = type.lastIndexOf('>')
        if (genericEnd <= genericStart) {
            return null
        }

        val rootType = simpleTypeName(type.substring(0, genericStart))
        if (rootType !in collectionWrapperTypes) {
            return simpleNestedTypeCandidate(type)
        }

        val arguments = splitGenericArguments(type.substring(genericStart + 1, genericEnd))
        return arguments.singleOrNull()?.let(::simpleNestedTypeCandidate)
    }

    private fun simpleNestedTypeCandidate(type: String): String? {
        val simpleName = simpleTypeName(type)
        return simpleName.takeIf { it.isNotBlank() && it !in builtInTypeNames && it != "self" }
    }

    private fun isCollectionType(type: String): Boolean {
        val trimmed = type.trim()
        val genericStart = trimmed.indexOf('<')
        if (genericStart < 0) {
            return false
        }

        return simpleTypeName(trimmed.substring(0, genericStart)) in collectionWrapperTypes
    }

    private fun toNestedTypeName(rawName: String): String {
        return rawName
            .split(Regex("[^A-Za-z0-9]+"))
            .filter { it.isNotBlank() }
            .joinToString("") { part ->
                part.replaceFirstChar { it.uppercaseChar() }
            }
    }

    private fun isCompatibleRootField(type: String, nestedTypeName: String): Boolean {
        val trimmed = type.trim()
        if (trimmed.isBlank()) {
            return false
        }

        val genericStart = trimmed.indexOf('<')
        if (genericStart < 0) {
            return simpleTypeName(trimmed) == nestedTypeName
        }

        val genericEnd = trimmed.lastIndexOf('>')
        if (genericEnd <= genericStart) {
            return false
        }

        val rootType = simpleTypeName(trimmed.substring(0, genericStart))
        if (rootType !in collectionWrapperTypes) {
            return false
        }

        val arguments = splitGenericArguments(trimmed.substring(genericStart + 1, genericEnd))
        return arguments.size == 1 && simpleTypeName(arguments.single()) == nestedTypeName
    }

    private fun splitGenericArguments(text: String): List<String> {
        if (text.isBlank()) {
            return emptyList()
        }

        val result = mutableListOf<String>()
        var depth = 0
        var start = 0
        text.forEachIndexed { index, char ->
            when (char) {
                '<' -> depth++
                '>' -> depth--
                ',' -> if (depth == 0) {
                    result += text.substring(start, index).trim()
                    start = index + 1
                }
            }
        }

        result += text.substring(start).trim()
        return result.filter { it.isNotEmpty() }
    }

    private fun simpleTypeName(type: String): String {
        return type
            .trim()
            .removePrefix("out ")
            .removePrefix("in ")
            .removeSuffix("?")
            .trim()
            .substringAfterLast('.')
    }

    private fun PayloadPathNode.toPreparedField(
        namespace: String,
        innerTypeNames: Set<String>,
    ): PreparedFieldModel {
        val field = explicitDeclarations.firstOrNull()
            ?: throw missingDirectContainerError(this, namespace, toNestedTypeName(name))
        return PreparedFieldModel(
            name = name,
            sourceName = field.name,
            nullable = field.nullable,
            defaultValue = field.defaultValue,
            resolvedType = resolveDesignType(
                type = DesignTypeParser.parse(field.type),
                innerTypeNames = innerTypeNames,
                namespace = namespace,
            ),
        )
    }

    private fun resolveDesignType(
        type: DesignTypeModel,
        innerTypeNames: Set<String>,
        namespace: String,
    ): DesignResolvedTypeModel {
        val parsedRawText = type.tokenText
        val resolvesToInner = parsedRawText in innerTypeNames
        val rawText = if (resolvesToInner) {
            parsedRawText.substringAfterLast('.')
        } else {
            parsedRawText
        }
        val simpleName = rawText.substringAfterLast('.')
        val kind = when {
            resolvesToInner -> DesignResolvedTypeKind.INNER
            rawText in builtInTypeNames -> DesignResolvedTypeKind.BUILTIN
            rawText.contains('.') -> DesignResolvedTypeKind.EXPLICIT_FQCN
            else -> DesignResolvedTypeKind.UNRESOLVED
        }

        return DesignResolvedTypeModel(
            kind = kind,
            rawText = rawText,
            simpleName = simpleName,
            nullable = type.nullable,
            arguments = type.arguments.map { argument ->
                resolveDesignType(
                    type = argument,
                    innerTypeNames = innerTypeNames,
                    namespace = namespace,
                )
            },
            importCandidates = when (kind) {
                DesignResolvedTypeKind.EXPLICIT_FQCN -> setOf(rawText)
                else -> emptySet()
            },
        )
    }

    private fun PreparedFieldModel.toRenderField(renderedType: String): DesignRenderFieldModel {
        val rawDefaultValue = if (defaultValue != null &&
            defaultValue.isEmpty() &&
            renderedType.removeSuffix("?").trim() == "String"
        ) {
            "\"\""
        } else {
            defaultValue
        }
        return DesignRenderFieldModel(
            name = name,
            renderedType = renderedType,
            nullable = nullable,
            defaultValue = DefaultValueFormatter.format(
                rawDefaultValue = rawDefaultValue,
                renderedType = renderedType,
                nullable = nullable,
                fieldName = sourceName,
            ),
        )
    }

    private fun String.withNullability(nullable: Boolean): String {
        if (!nullable || endsWith("?")) {
            return this
        }
        return "$this?"
    }

    private data class NamespaceModel(
        val fields: List<PreparedFieldModel>,
        val nestedTypes: List<PreparedNestedTypeModel>,
        val nestedTypeNames: Set<String>,
    ) {
        val resolvedTypes: List<DesignResolvedTypeModel>
            get() = fields.map { it.resolvedType } + nestedTypes.flatMap { nestedType ->
                nestedType.fields.map { it.resolvedType }
            }
    }

    private data class PreparedFieldModel(
        val name: String,
        val sourceName: String,
        val nullable: Boolean,
        val defaultValue: String?,
        val resolvedType: DesignResolvedTypeModel,
    )

    private data class PreparedNestedTypeModel(
        val name: String,
        val fields: List<PreparedFieldModel>,
    )

    private data class FieldPathSegment(
        val name: String,
        val list: Boolean,
    )

    private data class PayloadPathNode(
        val name: String,
        val path: List<String>,
        val children: LinkedHashMap<String, PayloadPathNode> = linkedMapOf(),
        val explicitDeclarations: MutableList<FieldModel> = mutableListOf(),
        var list: Boolean = false,
        var nestedTypeName: String? = null,
    ) {
        val pathText: String
            get() = path.joinToString(".")
    }
}
