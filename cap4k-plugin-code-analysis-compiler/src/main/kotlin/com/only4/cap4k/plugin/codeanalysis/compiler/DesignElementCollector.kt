@file:Suppress("DEPRECATION")
@file:OptIn(
    org.jetbrains.kotlin.DeprecatedForRemovalCompilerApi::class,
    org.jetbrains.kotlin.ir.symbols.UnsafeDuringIrConstructionAPI::class
)

package com.only4.cap4k.plugin.codeanalysis.compiler

import com.only4.cap4k.plugin.codeanalysis.core.model.DesignElement
import com.only4.cap4k.plugin.codeanalysis.core.model.DesignField
import com.only4.cap4k.plugin.codeanalysis.core.model.DesignParameter
import org.jetbrains.kotlin.descriptors.DescriptorVisibilities
import org.jetbrains.kotlin.ir.IrElement
import org.jetbrains.kotlin.ir.declarations.IrClass
import org.jetbrains.kotlin.ir.declarations.IrDeclarationParent
import org.jetbrains.kotlin.ir.declarations.IrDeclarationWithVisibility
import org.jetbrains.kotlin.ir.declarations.IrField
import org.jetbrains.kotlin.ir.declarations.IrModuleFragment
import org.jetbrains.kotlin.ir.declarations.IrProperty
import org.jetbrains.kotlin.ir.declarations.IrValueParameter
import org.jetbrains.kotlin.ir.declarations.IrSimpleFunction
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
import org.jetbrains.kotlin.ir.util.hasAnnotation
import org.jetbrains.kotlin.ir.util.parentAsClass
import org.jetbrains.kotlin.ir.util.primaryConstructor
import org.jetbrains.kotlin.ir.visitors.IrVisitorVoid
import org.jetbrains.kotlin.ir.visitors.acceptChildrenVoid
import org.jetbrains.kotlin.ir.visitors.acceptVoid
import org.jetbrains.kotlin.name.FqName
import org.jetbrains.kotlin.descriptors.ClassKind

class DesignElementCollector(
    private val options: Cap4kOptions,
    private val requestAggregates: Map<String, List<String>>
) : IrVisitorVoid() {
    private val elements = LinkedHashMap<String, DesignElement>()
    private val typeFormatter = IrTypeFormatter()

    private val requestParamFq = FqName(options.requestParamFq)
    private val pageRequestFq = FqName(options.pageRequestFq)
    private val domainEventAnnFq = FqName(options.domainEventAnnFq)
    private val aggregateAnnFq = FqName(options.aggregateAnnFq)
    private val constraintAnnFq = FqName(options.constraintAnnFq)
    private val constraintValidatorFq = FqName(options.constraintValidatorFq)

    fun collect(moduleFragment: IrModuleFragment): List<DesignElement> {
        moduleFragment.files.forEach { it.acceptVoid(this) }
        return elements.values.toList()
    }

    override fun visitElement(element: IrElement) {
        element.acceptChildrenVoid(this)
    }

    override fun visitClass(declaration: IrClass) {
        val fqcn = declaration.fqNameWhenAvailable?.asString()
        if (fqcn != null) {
            when {
                declaration.isOrImplements(requestParamFq) -> collectRequestElementForRequest(declaration, fqcn)
                declaration.kind == ClassKind.OBJECT &&
                    fqcn.contains(".adapter.portal.api.payload.") &&
                    declaration.parent !is IrClass ->
                    collectPayloadElement(declaration, fqcn)
                declaration.hasAnnotation(domainEventAnnFq) || declaration.readAggregateInfo(aggregateAnnFq)?.type == AGG_TYPE_DOMAIN_EVENT ->
                    collectDomainEventElement(declaration, fqcn)
                declaration.kind == ClassKind.ANNOTATION_CLASS && declaration.hasAnnotation(constraintAnnFq) ->
                    collectValidatorElement(declaration, fqcn)
            }
        }
        super.visitClass(declaration)
    }

    private fun collectRequestElementForRequest(declaration: IrClass, requestFqcn: String) {
        val parent = declaration.parent as? IrClass
        val container = if (parent != null && declaration.name.asString() == "Request") parent else declaration
        val containerFqcn = container.fqNameWhenAvailable?.asString() ?: return
        val requestClass = if (container == declaration) findNestedClass(container, "Request") else declaration
        collectRequestElement(container, containerFqcn, requestClass, requestFqcn)
    }

    private fun collectRequestElement(
        declaration: IrClass,
        fqcn: String,
        requestClass: IrClass?,
        requestFqcn: String,
    ) {
        val kind = classifyRequestKind(declaration, fqcn)
        val name = stripSuffix(declaration.name.asString(), kind)
        val pkg = extractPackage(fqcn, kind.packageMarker)
        val nestedTypes = collectNestedTypes(declaration)
        val responseClass = findNestedClass(declaration, "Response")
        rejectLegacyItemResponse(declaration, responseClass, kind.tag, name)
        val requestFields = requestClass?.let {
            collectFields(it, nestedTypes, DefaultValueContext("${kind.tag} $name request field"))
        }.orEmpty()
        val responseFields = responseClass?.let {
            collectFields(it, nestedTypes, DefaultValueContext("${kind.tag} $name response field"))
        }.orEmpty()
        val aggregates = requestAggregates[requestFqcn].orEmpty()
        addElement(
            DesignElement(
                tag = kind.tag,
                `package` = pkg,
                name = name,
                desc = "",
                aggregates = aggregates,
                entity = null,
                persist = null,
                traits = requestTraitsFor(kind.tag, requestClass),
                requestFields = requestFields,
                responseFields = responseFields
            )
        )
    }

    private fun collectPayloadElement(declaration: IrClass, fqcn: String) {
        val name = lowerCamel(declaration.name.asString())
        val pkg = extractPackage(fqcn, ".adapter.portal.api.payload")
        val nestedTypes = collectNestedTypes(declaration)
        val requestClass = findNestedClass(declaration, "Request")
        val responseClass = findNestedClass(declaration, "Response")
        rejectLegacyItemResponse(declaration, responseClass, "api_payload", name)
        val requestFields = requestClass?.let {
            collectFields(it, nestedTypes, DefaultValueContext("api_payload $name request field"))
        }.orEmpty()
        val responseFields = responseClass?.let {
            collectFields(it, nestedTypes, DefaultValueContext("api_payload $name response field"))
        }.orEmpty()
        val aggInfo = declaration.readAggregateInfo(aggregateAnnFq)
        val aggregates = if (aggInfo?.type == AGG_TYPE_FACTORY_PAYLOAD) listOf(aggInfo.aggregateName) else emptyList()
        addElement(
            DesignElement(
                tag = "api_payload",
                `package` = pkg,
                name = name,
                desc = "",
                aggregates = aggregates,
                entity = null,
                persist = null,
                traits = requestTraitsFor("api_payload", requestClass),
                requestFields = requestFields,
                responseFields = responseFields
            )
        )
    }

    private fun collectDomainEventElement(declaration: IrClass, fqcn: String) {
        val pkg = extractPackage(fqcn, listOf(".domain.aggregates", ".domain.event"), ".events")
        val aggInfo = declaration.readAggregateInfo(aggregateAnnFq)
        val entity = aggInfo?.aggregateName
        val aggregates = entity?.let { listOf(it) }.orEmpty()
        val persist = declaration.readDomainEventPersist(domainEventAnnFq)
        val nestedTypes = collectNestedTypes(declaration)
        val requestFields = collectFields(
            declaration,
            nestedTypes,
            DefaultValueContext("domain_event ${declaration.name.asString()} request field"),
        )
        addElement(
            DesignElement(
                tag = "domain_event",
                `package` = pkg,
                name = declaration.name.asString(),
                desc = "",
                aggregates = aggregates,
                entity = entity,
                persist = persist,
                requestFields = requestFields,
                responseFields = emptyList()
            )
        )
    }

    private fun collectValidatorElement(declaration: IrClass, fqcn: String) {
        if (isAggregateUniqueValidator(fqcn)) {
            return
        }
        val valueType = resolveConstraintValidatorValueType(declaration) ?: return
        if (valueType !in SupportedValidatorValueTypes) {
            return
        }
        val rawTargets = readAnnotationTargets(declaration)
        if (rawTargets.isEmpty() || rawTargets.any { it !in SupportedValidatorTargets }) {
            return
        }
        val targets = rawTargets
            .distinct()
            .sortedBy { target -> ValidatorTargetOrder[target] ?: Int.MAX_VALUE }
        if ("CLASS" in targets && valueType != "Any") {
            return
        }
        val parameters = collectValidatorParameters(declaration, declaration.name.asString()) ?: return
        addElement(
            DesignElement(
                tag = "validator",
                `package` = extractPackage(fqcn, ".application.validators"),
                name = declaration.name.asString(),
                desc = "",
                message = readAnnotationConstructorDefault(declaration, "message") ?: "校验未通过",
                targets = targets,
                valueType = valueType,
                parameters = parameters,
            )
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

    private fun requestTraitsFor(tag: String, requestClass: IrClass?): List<String> {
        if (tag !in RequestTraitTags || requestClass == null) {
            return emptyList()
        }
        return if (requestClass.isOrImplements(pageRequestFq)) {
            listOf("page")
        } else {
            emptyList()
        }
    }

    private fun rejectLegacyItemResponse(
        declaration: IrClass,
        responseClass: IrClass?,
        tag: String,
        name: String,
    ) {
        if (responseClass == null && findNestedClass(declaration, "Item") != null) {
            throw IllegalArgumentException(
                "$tag $name must define nested Response; legacy nested Item is not supported by analysis projection.",
            )
        }
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

    private fun resolveConstraintValidatorValueType(annotationClass: IrClass): String? {
        val nestedValidator = findNestedClass(annotationClass, "Validator") ?: return null
        val annotationName = annotationClass.name.asString()
        val matchingSuperType = nestedValidator.superTypes
            .mapNotNull { it as? IrSimpleType }
            .firstOrNull { type ->
                val owner = type.classifier?.owner as? IrClass ?: return@firstOrNull false
                owner.fqNameWhenAvailable == constraintValidatorFq
            } ?: return null
        val annotationType = matchingSuperType.arguments.getOrNull(0)?.typeOrNull ?: return null
        val valueType = matchingSuperType.arguments.getOrNull(1)?.typeOrNull ?: return null
        if (typeFormatter.format(annotationType) != annotationName) {
            return null
        }
        return typeFormatter.format(valueType).removeSuffix("?")
    }

    private fun collectValidatorParameters(annotationClass: IrClass, annotationName: String): List<DesignParameter>? {
        val ctor = annotationClass.primaryConstructor ?: return emptyList()
        val parameters = mutableListOf<DesignParameter>()
        ctor.valueParameters.forEach { parameter ->
            val name = parameter.name.asString()
            if (name in StandardValidatorParameterNames) {
                return@forEach
            }
            val type = typeFormatter.format(parameter.type).removeSuffix("?")
            if (type !in SupportedValidatorParameterTypes) {
                return null
            }
            parameters += DesignParameter(
                name = name,
                type = type,
                nullable = parameter.type.isNullable(),
                defaultValue = resolveDefaultValue(
                    parameter,
                    "validator $annotationName parameter $name",
                    DefaultValueRenderStyle.LEGACY_RAW_STRING_LITERAL,
                ),
            )
        }
        return parameters
    }

    private fun readAnnotationTargets(annotationClass: IrClass): List<String> {
        val targetAnnotation = annotationClass.annotations.firstOrNull {
            it.symbol.owner.parentAsClass.fqNameWhenAvailable?.asString() == "kotlin.annotation.Target"
        } ?: return emptyList()
        return targetAnnotation.getEnumVarargArg("allowedTargets")
    }

    private fun readAnnotationConstructorDefault(annotationClass: IrClass, parameterName: String): String? {
        val ctor = annotationClass.primaryConstructor ?: return null
        return ctor.valueParameters
            .firstOrNull { it.name.asString() == parameterName }
            ?.let {
                resolveDefaultValue(
                    it,
                    "validator ${annotationClass.name.asString()} parameter $parameterName",
                    DefaultValueRenderStyle.LEGACY_RAW_STRING_LITERAL,
                )
            }
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
        return hasStableConstantInitializer() || isStatic
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

    private fun classifyRequestKind(declaration: IrClass, fqcn: String): RequestKind {
        val simpleName = declaration.name.asString()
        return when {
            simpleName.endsWith("Qry") || fqcn.contains(".queries.") -> RequestKind.QUERY
            simpleName.endsWith("Cli") || fqcn.contains(".distributed.clients.") -> RequestKind.CLI
            simpleName.endsWith("Cmd") || fqcn.contains(".commands.") -> RequestKind.COMMAND
            else -> RequestKind.COMMAND
        }
    }

    private fun stripSuffix(name: String, kind: RequestKind): String {
        return when (kind) {
            RequestKind.COMMAND -> name.removeSuffix("Cmd")
            RequestKind.QUERY -> name.removeSuffix("Qry")
            RequestKind.CLI -> name.removeSuffix("Cli")
        }
    }

    private fun extractPackage(fqcn: String, marker: String): String {
        return extractPackage(fqcn, listOf(marker), null)
    }

    private fun extractPackage(fqcn: String, markers: List<String>, trimSuffix: String?): String {
        val pkg = fqcn.substringBeforeLast(".", "")
        val marker = markers.firstOrNull { pkg.contains(it) } ?: return pkg
        val idx = pkg.indexOf(marker)
        var start = idx + marker.length
        if (start < pkg.length && pkg[start] == '.') start++
        var result = if (start <= pkg.length) pkg.substring(start) else ""
        if (trimSuffix != null && result.endsWith(trimSuffix)) {
            result = result.removeSuffix(trimSuffix)
        }
        return result
    }

    private fun lowerCamel(value: String): String {
        if (value.isEmpty()) return value
        return value.replaceFirstChar { it.lowercase() }
    }

    private fun addElement(element: DesignElement) {
        val key = "${element.tag}|${element.`package`}|${element.name}"
        elements.putIfAbsent(key, element)
    }

    private fun isAggregateUniqueValidator(fqcn: String): Boolean {
        val packageName = fqcn.substringBeforeLast(".", "")
        return packageName.contains(".application.validators.") &&
            (packageName.endsWith(".unique") || packageName.contains(".unique."))
    }

    private fun IrClass.isOrImplements(fqName: FqName, visited: MutableSet<IrClass> = mutableSetOf()): Boolean {
        val currentFq = fqNameWhenAvailable
        if (currentFq == fqName) return true
        if (!visited.add(this)) return false
        return superTypes.any { t ->
            val st = t as? IrSimpleType ?: return@any false
            val owner = st.classifier?.owner as? IrClass ?: return@any false
            owner.isOrImplements(fqName, visited)
        }
    }

    private fun IrClass.readAggregateInfo(aggregateAnn: FqName): AggregateInfo? {
        val ann = annotations.firstOrNull { it.symbol.owner.parentAsClass.fqNameWhenAvailable == aggregateAnn }
            ?: return null
        val aggregateName = ann.getStringArg("aggregate") ?: ""
        val type = ann.getStringArg("type") ?: ""
        val resolvedName = if (aggregateName.isNotEmpty()) aggregateName else name.asString()
        return AggregateInfo(resolvedName, type)
    }

    private fun IrClass.readDomainEventPersist(domainEventAnn: FqName): Boolean? {
        val ann = annotations.firstOrNull { it.symbol.owner.parentAsClass.fqNameWhenAvailable == domainEventAnn }
            ?: return null
        return ann.getBooleanArg("persist") ?: false
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

    private fun IrConstructorCall.getEnumVarargArg(name: String): List<String> {
        val idx = symbol.owner.valueParameterIndex(name)
        if (idx < 0) return emptyList()
        val arg = getValueArgument(idx) ?: return emptyList()
        return when (arg) {
            is IrVararg -> arg.elements.mapNotNull { element ->
                (element as? IrGetEnumValue)?.symbol?.owner?.name?.asString()
            }
            is IrGetEnumValue -> listOf(arg.symbol.owner.name.asString())
            else -> emptyList()
        }
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

    private data class AggregateInfo(
        val aggregateName: String,
        val type: String
    )

    private enum class RequestKind(
        val tag: String,
        val packageMarker: String
    ) {
        COMMAND("command", ".commands"),
        QUERY("query", ".queries"),
        CLI("client", ".distributed.clients")
    }

    private val org.jetbrains.kotlin.ir.types.IrTypeArgument.typeOrNull: IrType?
        get() = (this as? IrTypeProjection)?.type
}

private const val AGG_TYPE_FACTORY_PAYLOAD = "factory-payload"
private const val AGG_TYPE_DOMAIN_EVENT = "domain-event"
private val RequestTraitTags = setOf("query", "api_payload")
private val SupportedValidatorTargets = setOf("CLASS", "FIELD", "VALUE_PARAMETER")
private val ValidatorTargetOrder = mapOf("CLASS" to 0, "FIELD" to 1, "VALUE_PARAMETER" to 2)
private val SupportedValidatorValueTypes = setOf("Any", "String", "Long", "Int", "Boolean")
private val SupportedValidatorParameterTypes = setOf("String", "Int", "Long", "Boolean")
private val StandardValidatorParameterNames = setOf("message", "groups", "payload")
