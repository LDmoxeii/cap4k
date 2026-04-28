@file:Suppress("DEPRECATION")
@file:OptIn(
    org.jetbrains.kotlin.DeprecatedForRemovalCompilerApi::class,
    org.jetbrains.kotlin.ir.symbols.UnsafeDuringIrConstructionAPI::class
)

package com.only4.cap4k.plugin.codeanalysis.compiler

import com.only4.cap4k.plugin.codeanalysis.core.model.DesignElement
import com.only4.cap4k.plugin.codeanalysis.core.model.DesignField
import com.only4.cap4k.plugin.codeanalysis.core.model.DesignParameter
import org.jetbrains.kotlin.ir.IrElement
import org.jetbrains.kotlin.ir.declarations.IrClass
import org.jetbrains.kotlin.ir.declarations.IrModuleFragment
import org.jetbrains.kotlin.ir.declarations.IrValueParameter
import org.jetbrains.kotlin.ir.expressions.IrConst
import org.jetbrains.kotlin.ir.expressions.IrConstructorCall
import org.jetbrains.kotlin.ir.expressions.IrGetEnumValue
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
        val responseClass = findNestedClass(declaration, "Response") ?: findNestedClass(declaration, "Item")
        val requestFields = requestClass?.let { collectFields(it, nestedTypes) }.orEmpty()
        val responseFields = responseClass?.let { collectFields(it, nestedTypes) }.orEmpty()
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
        val responseClass = findNestedClass(declaration, "Item")
        val requestFields = requestClass?.let { collectFields(it, nestedTypes) }.orEmpty()
        val responseFields = responseClass?.let { collectFields(it, nestedTypes) }.orEmpty()
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
        val requestFields = collectFields(declaration, nestedTypes)
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
        val parameters = collectValidatorParameters(declaration) ?: return
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

    private fun collectFields(rootClass: IrClass, nestedTypes: Map<String, IrClass>): List<DesignField> {
        val visited = mutableSetOf<String>()
        return collectFieldsRecursive(rootClass, nestedTypes, null, visited)
    }

    private fun collectFieldsRecursive(
        rootClass: IrClass,
        nestedTypes: Map<String, IrClass>,
        prefix: String?,
        visited: MutableSet<String>
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
            val defaultValue = resolveDefaultValue(param)
            fields.add(DesignField(fieldPath, typeFormatter.format(type), nullable, defaultValue))

            val nestedInfo = resolveNestedType(type, nestedTypes)
            if (nestedInfo != null) {
                val nestedPrefix = if (nestedInfo.isCollection) "$fieldPath[]" else fieldPath
                fields.addAll(
                    collectFieldsRecursive(
                        nestedInfo.nestedClass,
                        nestedTypes,
                        nestedPrefix,
                        visited
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

    private fun collectValidatorParameters(annotationClass: IrClass): List<DesignParameter>? {
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
                defaultValue = resolveDefaultValue(parameter),
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
            ?.let { resolveDefaultValue(it) }
    }

    private fun resolveDefaultValue(param: IrValueParameter): String? {
        val expr = param.defaultValue?.expression ?: return null
        val const = expr as? IrConst ?: return null
        val value = const.value ?: return null
        return value.toString()
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
private val SupportedValidatorTargets = setOf("CLASS", "FIELD", "VALUE_PARAMETER")
private val ValidatorTargetOrder = mapOf("CLASS" to 0, "FIELD" to 1, "VALUE_PARAMETER" to 2)
private val SupportedValidatorValueTypes = setOf("Any", "String", "Long", "Int", "Boolean")
private val SupportedValidatorParameterTypes = setOf("String", "Int", "Long", "Boolean")
private val StandardValidatorParameterNames = setOf("message", "groups", "payload")
