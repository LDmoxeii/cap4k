@file:Suppress("DEPRECATION")
@file:OptIn(
    org.jetbrains.kotlin.DeprecatedForRemovalCompilerApi::class,
    org.jetbrains.kotlin.ir.symbols.UnsafeDuringIrConstructionAPI::class
)

package com.only4.cap4k.plugin.codeanalysis.compiler

import com.only4.cap4k.plugin.codeanalysis.core.model.DesignArtifact
import com.only4.cap4k.plugin.codeanalysis.core.model.DesignElement
import com.only4.cap4k.plugin.codeanalysis.core.model.DesignField
import org.jetbrains.kotlin.descriptors.DescriptorVisibilities
import org.jetbrains.kotlin.ir.IrElement
import org.jetbrains.kotlin.ir.declarations.IrClass
import org.jetbrains.kotlin.ir.declarations.IrDeclarationParent
import org.jetbrains.kotlin.ir.declarations.IrDeclarationWithVisibility
import org.jetbrains.kotlin.ir.declarations.IrField
import org.jetbrains.kotlin.ir.declarations.IrModuleFragment
import org.jetbrains.kotlin.ir.declarations.IrProperty
import org.jetbrains.kotlin.ir.declarations.IrSimpleFunction
import org.jetbrains.kotlin.ir.declarations.IrValueParameter
import org.jetbrains.kotlin.ir.expressions.IrCall
import org.jetbrains.kotlin.ir.expressions.IrConst
import org.jetbrains.kotlin.ir.expressions.IrConstructorCall
import org.jetbrains.kotlin.ir.expressions.IrExpression
import org.jetbrains.kotlin.ir.expressions.IrGetEnumValue
import org.jetbrains.kotlin.ir.expressions.IrGetField
import org.jetbrains.kotlin.ir.expressions.IrGetObjectValue
import org.jetbrains.kotlin.ir.expressions.IrTypeOperatorCall
import org.jetbrains.kotlin.ir.expressions.IrVararg
import org.jetbrains.kotlin.ir.types.IrSimpleType
import org.jetbrains.kotlin.ir.types.IrType
import org.jetbrains.kotlin.ir.types.IrTypeProjection
import org.jetbrains.kotlin.ir.types.isNullable
import org.jetbrains.kotlin.ir.util.fqNameWhenAvailable
import org.jetbrains.kotlin.ir.util.parentAsClass
import org.jetbrains.kotlin.ir.util.primaryConstructor
import org.jetbrains.kotlin.ir.visitors.IrVisitorVoid
import org.jetbrains.kotlin.ir.visitors.acceptChildrenVoid
import org.jetbrains.kotlin.ir.visitors.acceptVoid
import org.jetbrains.kotlin.name.FqName

class DesignElementCollector(
    private val options: Cap4kOptions,
) : IrVisitorVoid() {
    private val blocks = LinkedHashMap<BlockKey, MutableDesignBlock>()
    private val typeFormatter = IrTypeFormatter()

    private val buildingBlockAnnFq = FqName(options.buildingBlockAnnFq)
    private val domainEventAnnFq = FqName(options.domainEventAnnFq)
    private val integrationEventAnnFq = FqName(options.integrationEventAnnFq)

    fun collect(moduleFragment: IrModuleFragment): List<DesignElement> {
        moduleFragment.files.forEach { it.acceptVoid(this) }
        return blocks.values.map { it.toDesignElement() }
    }

    override fun visitElement(element: IrElement) {
        element.acceptChildrenVoid(this)
    }

    override fun visitClass(declaration: IrClass) {
        declaration.findAnnotation(buildingBlockAnnFq)?.let { ann ->
            collectBuildingBlock(declaration, ann)
        }
        super.visitClass(declaration)
    }

    private fun collectBuildingBlock(declaration: IrClass, ann: IrConstructorCall) {
        val className = declaration.fqNameWhenAvailable?.asString() ?: declaration.name.asString()
        val tag = ann.getStringArg("tag").orEmpty().trim()
        val name = ann.getStringArg("name").orEmpty().trim()
        val packageName = ann.getStringArg("packageName").orEmpty().trim()
        val family = ann.getStringArg("family").orEmpty().trim()
        require(tag.isNotEmpty()) { "BuildingBlock annotation on $className must declare non-blank tag" }
        require(name.isNotEmpty()) { "BuildingBlock annotation on $className must declare non-blank name" }
        require(family.isNotEmpty()) { "BuildingBlock annotation on $className must declare non-blank family" }

        val nestedTypes = collectNestedTypes(declaration)
        val fields = primaryFieldCarrier(declaration, family)?.let { fieldsRoot ->
            collectFields(
                fieldsRoot,
                nestedTypes,
                DefaultValueContext("$tag $name field"),
            ).filterRecoveredFields(family)
        }.orEmpty()
        val resultFields = if (family.hasResultFields()) {
            findNestedClass(declaration, "Response")?.let {
                collectFields(it, nestedTypes, DefaultValueContext("$tag $name result field"))
            }.orEmpty()
        } else {
            emptyList()
        }
        val artifact = family.takeIf { it.isNotEmpty() }?.let {
            DesignArtifact(family = it, variant = ann.getStringArg("variant").orEmpty().trim())
        }
        val eventName = ann.getStringArg("eventName").orEmpty().trim()
            .ifBlank { declaration.readIntegrationEventName(integrationEventAnnFq).orEmpty() }
        val persist = declaration.readDomainEventPersist(domainEventAnnFq)

        mergeBlock(
            DesignElement(
                tag = tag,
                `package` = packageName,
                name = name,
                description = ann.getStringArg("description").orEmpty(),
                aggregates = ann.getStringListArg("aggregates"),
                eventName = eventName,
                persist = persist,
                artifacts = listOfNotNull(artifact),
                fields = fields,
                resultFields = resultFields,
            )
        )
    }

    private fun primaryFieldCarrier(declaration: IrClass, family: String): IrClass? =
        when (family) {
            "command",
            "query",
            "client",
            "api-payload" -> findNestedClass(declaration, "Request") ?: declaration
            "domain-event",
            "integration-event" -> declaration
            else -> null
        }

    private fun String.hasResultFields(): Boolean =
        this == "command" || this == "query" || this == "client" || this == "api-payload"

    private fun List<DesignField>.filterRecoveredFields(family: String): List<DesignField> =
        when (family) {
            "domain-event" -> filterNot { field -> field.name == "entity" || field.name.startsWith("entity.") }
            else -> this
        }

    private fun mergeBlock(element: DesignElement) {
        val key = BlockKey(element.tag, element.`package`, element.name)
        val existing = blocks[key]
        if (existing == null) {
            blocks[key] = MutableDesignBlock.from(element)
            return
        }

        existing.mergeShared(element)
        existing.mergeArtifacts(element.artifacts)
        existing.fields = mergeFields(existing.fields, element.fields, key, "fields")
        existing.resultFields = mergeFields(existing.resultFields, element.resultFields, key, "resultFields")
    }

    private fun MutableDesignBlock.mergeShared(element: DesignElement) {
        mergeString("description", description, element.description) { description = it }
        mergeString("eventName", eventName, element.eventName) { eventName = it }
        mergeStringList("aggregates", aggregates, element.aggregates) { aggregates = it }
        mergeBoolean("persist", persist, element.persist) { persist = it }
    }

    private fun MutableDesignBlock.mergeString(
        field: String,
        existing: String,
        incoming: String,
        update: (String) -> Unit,
    ) {
        if (incoming.isBlank()) {
            return
        }
        if (existing.isBlank()) {
            update(incoming)
            return
        }
        if (existing != incoming) {
            throw conflict(field)
        }
    }

    private fun MutableDesignBlock.mergeStringList(
        field: String,
        existing: List<String>,
        incoming: List<String>,
        update: (List<String>) -> Unit,
    ) {
        if (incoming.isEmpty()) {
            return
        }
        if (existing.isEmpty()) {
            update(incoming)
            return
        }
        if (existing != incoming) {
            throw conflict(field)
        }
    }

    private fun MutableDesignBlock.mergeBoolean(
        field: String,
        existing: Boolean?,
        incoming: Boolean?,
        update: (Boolean?) -> Unit,
    ) {
        if (incoming == null) {
            return
        }
        if (existing == null) {
            update(incoming)
            return
        }
        if (existing != incoming) {
            throw conflict(field)
        }
    }

    private fun MutableDesignBlock.conflict(field: String): IllegalArgumentException =
        IllegalArgumentException("conflicting BuildingBlock metadata for $tag $packageName $name: $field")

    private fun MutableDesignBlock.mergeArtifacts(incoming: List<DesignArtifact>) {
        incoming.forEach { artifact ->
            if (artifacts.none { it.family == artifact.family && it.variant == artifact.variant }) {
                artifacts += artifact
            }
        }
    }

    private fun mergeFields(
        existing: List<DesignField>,
        incoming: List<DesignField>,
        key: BlockKey,
        fieldName: String,
    ): List<DesignField> {
        if (incoming.isEmpty()) {
            return existing
        }
        if (existing.isEmpty() || existing == incoming) {
            return if (existing.isEmpty()) incoming else existing
        }
        throw IllegalArgumentException(
            "conflicting BuildingBlock metadata for ${key.tag} ${key.packageName} ${key.name}: $fieldName",
        )
    }

    private fun collectFields(
        rootClass: IrClass,
        nestedTypes: Map<String, IrClass>,
        defaultValueContext: DefaultValueContext,
    ): List<DesignField> {
        val visited = mutableSetOf<String>()
        return collectFieldsRecursive(rootClass, nestedTypes, null, visited, defaultValueContext)
    }

    private fun collectFieldsRecursive(
        rootClass: IrClass,
        nestedTypes: Map<String, IrClass>,
        prefix: String?,
        visited: MutableSet<String>,
        defaultValueContext: DefaultValueContext,
    ): List<DesignField> {
        val fqcn = rootClass.fqNameWhenAvailable?.asString()
        if (fqcn != null && !visited.add(fqcn)) return emptyList()
        val ctor = rootClass.primaryConstructor ?: return emptyList()
        val fields = mutableListOf<DesignField>()
        ctor.valueParameters.forEach { param ->
            val name = param.name.asString()
            val fieldPath = if (prefix.isNullOrEmpty()) name else "$prefix.$name"
            val type = param.type
            val nullable = type.isNullable()
            val defaultValue = resolveDefaultValue(param, defaultValueContext.describe(fieldPath), defaultValueContext.renderStyle)
            fields.add(DesignField(fieldPath, typeFormatter.format(type), nullable, defaultValue))

            val nestedInfo = resolveNestedType(type, nestedTypes)
            if (nestedInfo != null) {
                val nestedPrefix = if (nestedInfo.isCollection) "$fieldPath[]" else fieldPath
                fields.addAll(
                    collectFieldsRecursive(
                        nestedInfo.nestedClass,
                        nestedTypes,
                        nestedPrefix,
                        visited,
                        defaultValueContext,
                    )
                )
            }
        }
        if (fqcn != null) {
            visited.remove(fqcn)
        }
        return fields
    }

    private fun resolveNestedType(type: IrType, nestedTypes: Map<String, IrClass>): NestedType? {
        val elementType = typeFormatter.collectionElementType(type)
        val targetType = elementType ?: type
        val simple = targetType as? IrSimpleType ?: return null
        val klass = simple.classifier?.owner as? IrClass ?: return null
        val fqcn = klass.fqNameWhenAvailable?.asString() ?: return null
        val nestedClass = nestedTypes[fqcn] ?: return null
        return NestedType(nestedClass, elementType != null)
    }

    private fun resolveDefaultValue(
        param: IrValueParameter,
        context: String,
        renderStyle: DefaultValueRenderStyle,
    ): String? {
        val expr = param.defaultValue?.expression ?: return null
        val rendered = renderDefaultValueExpression(expr.unwrapDefaultValueExpression(), renderStyle)
        if (rendered != null) {
            return rendered
        }
        throw IllegalArgumentException("unsupported defaultValue expression for $context")
    }

    private fun renderDefaultValueExpression(
        expression: IrExpression,
        renderStyle: DefaultValueRenderStyle,
    ): String? {
        return when (expression) {
            is IrConst -> renderConstDefaultValue(expression, renderStyle)
            is IrCall -> renderEmptyCollectionCall(expression) ?: renderStablePropertyGetterCall(expression, renderStyle)
            is IrGetEnumValue -> renderStableEnumValue(expression)
            is IrGetObjectValue -> renderStableObjectValue(expression)
            is IrGetField -> renderStableFieldReference(expression, renderStyle)
            else -> null
        }
    }

    private fun renderConstDefaultValue(
        expression: IrConst,
        renderStyle: DefaultValueRenderStyle,
    ): String? {
        val value = expression.value
        return when (value) {
            null -> "null"
            is String -> when (renderStyle) {
                DefaultValueRenderStyle.KOTLIN_READY -> renderStringLiteral(value)
                DefaultValueRenderStyle.LEGACY_RAW_STRING_LITERAL -> value
            }
            is Boolean,
            is Byte,
            is Short,
            is Int -> value.toString()
            is Long -> "${value}L"
            is Float -> "${value}F"
            is Double -> value.toString()
            else -> null
        }
    }

    private fun renderEmptyCollectionCall(expression: IrCall): String? {
        if (expression.dispatchReceiver != null || expression.extensionReceiver != null) {
            return null
        }
        if (expression.symbol.owner.valueParameters.isNotEmpty()) {
            return null
        }
        return when (expression.symbol.owner.fqNameWhenAvailable?.asString()) {
            "kotlin.collections.emptyList" -> "emptyList()"
            "kotlin.collections.emptySet" -> "emptySet()"
            "kotlin.collections.emptyMap" -> "emptyMap()"
            else -> null
        }
    }

    private fun renderStablePropertyGetterCall(
        expression: IrCall,
        renderStyle: DefaultValueRenderStyle,
    ): String? {
        val function = expression.symbol.owner as? IrSimpleFunction ?: return null
        val property = function.correspondingPropertySymbol?.owner ?: return null
        if (property.isVar || function.valueParameters.isNotEmpty()) {
            return null
        }
        if (!property.isExternallyUsableStableProperty(function)) {
            return null
        }
        if (!isSupportedStablePropertyReceiver(expression.dispatchReceiver, expression.extensionReceiver)) {
            return null
        }
        val backingField = property.backingField ?: return null
        if (!backingField.hasStableConstantInitializer()) {
            return null
        }
        return property.fqNameWhenAvailable?.asString()
    }

    private fun renderStableFieldReference(
        expression: IrGetField,
        renderStyle: DefaultValueRenderStyle,
    ): String? {
        val owner = expression.symbol.owner
        if (!owner.isStableConstantField()) {
            return null
        }
        if (!owner.isExternallyUsableReference()) {
            return null
        }
        if (!isSupportedStableFieldReceiver(expression.receiver)) {
            return null
        }
        return renderQualifiedFieldReference(owner, expression.receiver, renderStyle)
    }

    private fun IrField.isStableConstantField(): Boolean {
        if (!isFinal) {
            return false
        }
        val property = correspondingPropertySymbol?.owner
        if (property?.isVar == true) {
            return false
        }
        return hasStableConstantInitializer()
    }

    private fun IrField.hasStableConstantInitializer(): Boolean {
        val initializer = initializer?.expression?.unwrapDefaultValueExpression() ?: return false
        return renderStableConstantInitializer(initializer)
    }

    private fun renderStableConstantInitializer(expression: IrExpression): Boolean {
        return when (expression) {
            is IrConst -> renderConstDefaultValue(expression, DefaultValueRenderStyle.KOTLIN_READY) != null
            is IrGetEnumValue -> true
            is IrGetObjectValue -> true
            is IrCall -> renderEmptyCollectionCall(expression) != null
            else -> false
        }
    }

    private fun renderStableEnumValue(expression: IrGetEnumValue): String? {
        val ownerClass = expression.symbol.owner.parentAsClass
        if (!ownerClass.isExternallyUsableReference()) {
            return null
        }
        return "${renderClassReference(ownerClass)}.${expression.symbol.owner.name.asString()}"
    }

    private fun renderStableObjectValue(expression: IrGetObjectValue): String? {
        val ownerClass = expression.symbol.owner
        if (!ownerClass.isExternallyUsableReference()) {
            return null
        }
        return renderClassReference(ownerClass)
    }

    private fun renderQualifiedFieldReference(
        owner: IrField,
        receiver: IrExpression?,
        renderStyle: DefaultValueRenderStyle,
    ): String? {
        owner.fqNameWhenAvailable?.asString()?.let { return it }
        val renderedReceiver = receiver?.let {
            renderDefaultValueExpression(it.unwrapDefaultValueExpression(), renderStyle)
        }
        if (renderedReceiver != null) {
            return "$renderedReceiver.${owner.name.asString()}"
        }
        val parentClass = owner.parent as? IrClass ?: return null
        return "${renderClassReference(parentClass)}.${owner.name.asString()}"
    }

    private fun IrProperty.isExternallyUsableStableProperty(getter: IrSimpleFunction): Boolean {
        if (!isExternallyUsableReference()) {
            return false
        }
        return getter.isExternallyUsableReference()
    }

    private fun isSupportedStablePropertyReceiver(
        dispatchReceiver: IrExpression?,
        extensionReceiver: IrExpression?,
    ): Boolean {
        if (extensionReceiver != null) {
            return false
        }
        return isSupportedSingletonReferenceReceiver(dispatchReceiver)
    }

    private fun isSupportedStableFieldReceiver(receiver: IrExpression?): Boolean {
        return isSupportedSingletonReferenceReceiver(receiver)
    }

    private fun isSupportedSingletonReferenceReceiver(receiver: IrExpression?): Boolean {
        if (receiver == null) {
            return true
        }
        return receiver.unwrapDefaultValueExpression() is IrGetObjectValue
    }

    private fun IrDeclarationWithVisibility.isExternallyUsableReference(): Boolean {
        if (visibility != DescriptorVisibilities.PUBLIC) {
            return false
        }
        return parentChainIsExternallyUsable(parent)
    }

    private fun parentChainIsExternallyUsable(parent: IrDeclarationParent?): Boolean {
        var current = parent
        while (current is IrClass) {
            if (current.visibility != DescriptorVisibilities.PUBLIC) {
                return false
            }
            current = current.parent
        }
        return true
    }

    private fun renderClassReference(irClass: IrClass?): String? {
        if (irClass == null) {
            return null
        }
        return irClass.fqNameWhenAvailable?.asString()
    }

    private fun renderStringLiteral(value: String): String = buildString {
        append('"')
        value.forEach { ch ->
            when (ch) {
                '\\' -> append("\\\\")
                '"' -> append("\\\"")
                '\n' -> append("\\n")
                '\r' -> append("\\r")
                '\t' -> append("\\t")
                '\b' -> append("\\b")
                '\u000C' -> append("\\f")
                '$' -> append("\\$")
                else -> {
                    if (ch.code < 0x20) {
                        append("\\u").append(ch.code.toString(16).padStart(4, '0'))
                    } else {
                        append(ch)
                    }
                }
            }
        }
        append('"')
    }

    private fun IrExpression.unwrapDefaultValueExpression(): IrExpression {
        var current: IrExpression = this
        while (true) {
            current = when (current) {
                is IrTypeOperatorCall -> current.argument
                else -> return current
            }
        }
    }

    private fun findNestedClass(container: IrClass, name: String): IrClass? {
        return container.declarations
            .filterIsInstance<IrClass>()
            .firstOrNull { it.name.asString() == name }
    }

    private fun collectNestedTypes(container: IrClass): Map<String, IrClass> {
        val nestedTypes = LinkedHashMap<String, IrClass>()
        fun visit(node: IrClass) {
            node.declarations.filterIsInstance<IrClass>().forEach { nested ->
                val fqcn = nested.fqNameWhenAvailable?.asString() ?: return@forEach
                nestedTypes[fqcn] = nested
                visit(nested)
            }
        }
        visit(container)
        return nestedTypes
    }

    private fun IrClass.readDomainEventPersist(domainEventAnn: FqName): Boolean? {
        val ann = findAnnotation(domainEventAnn)
            ?: return null
        return ann.getBooleanArg("persist") ?: false
    }

    private fun IrClass.readIntegrationEventName(integrationEventAnn: FqName): String? {
        val ann = findAnnotation(integrationEventAnn)
            ?: return null
        return ann.getStringArg("value")?.takeIf { it.isNotBlank() }
    }

    private fun IrClass.findAnnotation(fqName: FqName): IrConstructorCall? {
        return annotations.firstOrNull { it.symbol.owner.parentAsClass.fqNameWhenAvailable == fqName }
    }

    private fun IrConstructorCall.getStringArg(name: String): String? {
        val idx = symbol.owner.valueParameterIndex(name)
        if (idx < 0) return null
        val arg = getValueArgument(idx) as? IrConst ?: return null
        return arg.value as? String
    }

    private fun IrConstructorCall.getBooleanArg(name: String): Boolean? {
        val idx = symbol.owner.valueParameterIndex(name)
        if (idx < 0) return null
        val arg = getValueArgument(idx) as? IrConst ?: return null
        return arg.value as? Boolean
    }

    private fun IrConstructorCall.getStringListArg(name: String): List<String> {
        val idx = symbol.owner.valueParameterIndex(name)
        if (idx < 0) return emptyList()
        val arg = getValueArgument(idx) ?: return emptyList()
        return when (arg) {
            is IrVararg -> arg.elements.mapNotNull { element ->
                (element as? IrConst)?.value as? String
            }
            is IrConst -> listOfNotNull(arg.value as? String)
            else -> emptyList()
        }.map { it.trim() }.filter { it.isNotEmpty() }
    }

    private fun org.jetbrains.kotlin.ir.declarations.IrFunction.valueParameterIndex(name: String): Int {
        var idx = 0
        for (param in valueParameters) {
            if (param.name.asString() == name) return idx
            idx++
        }
        return -1
    }

    private data class NestedType(
        val nestedClass: IrClass,
        val isCollection: Boolean
    )

    private data class DefaultValueContext(
        val owner: String,
        val renderStyle: DefaultValueRenderStyle = DefaultValueRenderStyle.KOTLIN_READY,
    ) {
        fun describe(name: String): String = "$owner $name"
    }

    private enum class DefaultValueRenderStyle {
        KOTLIN_READY,
        LEGACY_RAW_STRING_LITERAL,
    }

    private data class BlockKey(
        val tag: String,
        val packageName: String,
        val name: String,
    )

    private data class MutableDesignBlock(
        val tag: String,
        val packageName: String,
        val name: String,
        var description: String,
        var aggregates: List<String>,
        var eventName: String,
        var persist: Boolean?,
        val artifacts: MutableList<DesignArtifact>,
        var fields: List<DesignField>,
        var resultFields: List<DesignField>,
    ) {
        fun toDesignElement(): DesignElement =
            DesignElement(
                tag = tag,
                `package` = packageName,
                name = name,
                description = description,
                aggregates = aggregates,
                eventName = eventName,
                persist = persist,
                artifacts = artifacts.toList(),
                fields = fields,
                resultFields = resultFields,
            )

        companion object {
            fun from(element: DesignElement): MutableDesignBlock =
                MutableDesignBlock(
                    tag = element.tag,
                    packageName = element.`package`,
                    name = element.name,
                    description = element.description,
                    aggregates = element.aggregates,
                    eventName = element.eventName,
                    persist = element.persist,
                    artifacts = element.artifacts.toMutableList(),
                    fields = element.fields,
                    resultFields = element.resultFields,
                )
        }
    }

    private val org.jetbrains.kotlin.ir.types.IrTypeArgument.typeOrNull: IrType?
        get() = (this as? IrTypeProjection)?.type
}
