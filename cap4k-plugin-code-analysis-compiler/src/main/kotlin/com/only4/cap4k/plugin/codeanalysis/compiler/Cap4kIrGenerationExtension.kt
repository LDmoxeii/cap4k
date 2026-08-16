@file:OptIn(org.jetbrains.kotlin.ir.symbols.UnsafeDuringIrConstructionAPI::class)

package com.only4.cap4k.plugin.codeanalysis.compiler

import com.only4.cap4k.plugin.codeanalysis.core.io.MetadataSink
import com.only4.cap4k.plugin.codeanalysis.core.model.Node
import com.only4.cap4k.plugin.codeanalysis.core.model.NodeType
import com.only4.cap4k.plugin.codeanalysis.core.model.Relationship
import com.only4.cap4k.plugin.codeanalysis.core.model.RelationshipType
import org.jetbrains.kotlin.backend.common.IrElementTransformerVoidWithContext
import org.jetbrains.kotlin.backend.common.extensions.IrGenerationExtension
import org.jetbrains.kotlin.backend.common.extensions.IrPluginContext
import org.jetbrains.kotlin.ir.IrElement
import org.jetbrains.kotlin.ir.IrFileEntry
import org.jetbrains.kotlin.ir.IrStatement
import org.jetbrains.kotlin.ir.PsiIrFileEntry
import org.jetbrains.kotlin.ir.declarations.*
import org.jetbrains.kotlin.ir.declarations.IrParameterKind
import org.jetbrains.kotlin.ir.expressions.*
import org.jetbrains.kotlin.ir.types.IrSimpleType
import org.jetbrains.kotlin.ir.types.IrType
import org.jetbrains.kotlin.ir.util.fqNameWhenAvailable
import org.jetbrains.kotlin.ir.util.hasAnnotation
import org.jetbrains.kotlin.ir.util.parentAsClass
import org.jetbrains.kotlin.ir.visitors.IrVisitorVoid
import org.jetbrains.kotlin.ir.visitors.acceptChildrenVoid
import org.jetbrains.kotlin.ir.visitors.acceptVoid
import org.jetbrains.kotlin.name.FqName
import org.jetbrains.kotlin.ir.symbols.UnsafeDuringIrConstructionAPI
import java.nio.file.Path
import kotlin.io.path.createDirectories
import kotlin.io.path.div
import kotlin.io.path.exists
import kotlin.io.path.writeText

class Cap4kIrGenerationExtension : IrGenerationExtension {
    override fun generate(moduleFragment: IrModuleFragment, pluginContext: IrPluginContext) {
        val options = Cap4kOptions.fromSystemProperties()
        val index = ClassIndexBuilder(options).apply {
            moduleFragment.files.forEach { it.acceptVoid(this) }
        }.build()

        val controllerRoots = ControllerCallGraphBuilder(options).apply {
            moduleFragment.files.forEach { it.acceptVoid(this) }
        }.buildRootsByMethod()
        val endpointHandlerMethods = EndpointHandlerCallGraphBuilder().apply {
            moduleFragment.files.forEach { it.acceptVoid(this) }
        }.buildReachableMethods()

        val collector = GraphCollector(options, index, controllerRoots, endpointHandlerMethods)
        moduleFragment.files.forEach { file ->
            file.accept(collector, null)
        }
        collector.completeEndpointHttpEvidence()

        val fallback = options.outputDir
        val filePaths = moduleFragment.files.map { it.fileEntry.name }
        val outDir = resolveOutputDir(filePaths, fallback)
        JsonFileMetadataSink(outDir.toString()).write(collector.nodesAsSequence(), collector.relsAsSequence())
        val designElements = DesignElementCollector(options).collect(moduleFragment)
        (outDir / "design-elements.json").writeText(DesignElementJsonWriter().write(designElements))
        (outDir / "aggregate-elements.json").writeText(AggregateElementJsonWriter().write(index.aggregateElements))
    }
}

private fun resolveOutputDir(filePaths: Iterable<String>, fallback: Path): Path {
    val moduleRoot = findModuleRootBySrc(filePaths) ?: findModuleRootByGradle(filePaths)
    return (moduleRoot ?: fallback).resolve("build").resolve("cap4k-code-analysis").createDirectories()
}

private fun findModuleRootBySrc(filePaths: Iterable<String>): Path? {
    for (pathStr in filePaths) {
        val path = runCatching { kotlin.io.path.Path(pathStr) }.getOrNull() ?: continue
        var cur = path.parent
        while (cur != null) {
            if (cur.fileName?.toString() == "src") return cur.parent
            cur = cur.parent
        }
    }
    return null
}

private fun findModuleRootByGradle(filePaths: Iterable<String>): Path? {
    for (pathStr in filePaths) {
        val path = runCatching { kotlin.io.path.Path(pathStr) }.getOrNull() ?: continue
        var cur = path.parent
        while (cur != null) {
            if (cur.resolve("build.gradle.kts").exists() || cur.resolve("build.gradle").exists()) {
                return cur
            }
            cur = cur.parent
        }
    }
    return null
}

private data class AggregateInfo(
    val aggregateName: String,
    val name: String,
    val packageName: String,
    val description: String,
    val type: String,
    val root: Boolean,
)

private data class EntityMethodRef(
    val aggregateRootFq: String,
    val methodId: String,
    val displayName: String,
)

private data class EndpointHttpBindingEvidence(
    val operationName: String,
    val operationOwnerFq: String,
    val requestFq: String,
    val method: String,
    val path: String,
) {
    val nodeId: String get() = "endpoint-http:$operationName"
}

private data class EndpointHandlerInvocation(
    val kind: ApplicationCallKind,
    val targetFq: String,
)

private data class ClassIndex(
    val aggregateInfoByClass: Map<String, AggregateInfo>,
    val aggregateElements: List<AggregateElementRecord>,
    val aggregateRootsByName: Map<String, String>,
    val entityMethodNamesByClass: Map<String, Set<String>>,
    val domainEventClasses: Set<String>,
    val integrationEventClasses: Set<String>,
)

private class ControllerCallGraphBuilder(
    private val options: Cap4kOptions,
) : IrVisitorVoid() {
    private val controllerMethodsByClass = mutableMapOf<String, MutableSet<String>>()
    private val methodCalls = mutableMapOf<String, MutableSet<String>>()
    private val controllerClasses = mutableSetOf<String>()

    private val restController = FqName("org.springframework.web.bind.annotation.RestController")
    private val requestMappings = setOf(
        "org.springframework.web.bind.annotation.RequestMapping",
        "org.springframework.web.bind.annotation.GetMapping",
        "org.springframework.web.bind.annotation.PostMapping",
        "org.springframework.web.bind.annotation.PutMapping",
        "org.springframework.web.bind.annotation.DeleteMapping",
        "org.springframework.web.bind.annotation.PatchMapping"
    ).map(::FqName).toSet()

    override fun visitElement(element: IrElement) {
        element.acceptChildrenVoid(this)
    }

    override fun visitClass(declaration: IrClass) {
        if (!options.scanSpring) return super.visitClass(declaration)
        val fqcn = declaration.fqNameWhenAvailable?.asString() ?: return super.visitClass(declaration)
        if (declaration.hasAnnotation(restController)) {
            controllerClasses.add(fqcn)
        }
        super.visitClass(declaration)
    }

    override fun visitFunction(declaration: IrFunction) {
        if (!options.scanSpring) return super.visitFunction(declaration)
        val parentClass = declaration.parent as? IrClass ?: return super.visitFunction(declaration)
        val parentFqcn = parentClass.fqNameWhenAvailable?.asString() ?: return super.visitFunction(declaration)
        if (!controllerClasses.contains(parentFqcn)) return super.visitFunction(declaration)

        val methodName = declaration.name.asString()
        val methodId = "$parentFqcn::$methodName"
        val isControllerMethod = declaration.annotations.any { ann ->
            val annFq = ann.symbol.owner.parentAsClass.fqNameWhenAvailable
            annFq != null && requestMappings.contains(annFq)
        }
        if (isControllerMethod) {
            controllerMethodsByClass.getOrPut(parentFqcn) { mutableSetOf() }.add(methodId)
        }

        declaration.body?.acceptVoid(object : IrVisitorVoid() {
            override fun visitElement(element: IrElement) {
                element.acceptChildrenVoid(this)
            }

            override fun visitCall(expression: IrCall) {
                val targetClass = expression.symbol.owner.parent as? IrClass
                val targetFq = targetClass?.fqNameWhenAvailable?.asString()
                if (targetFq == parentFqcn) {
                    val targetId = "$targetFq::${expression.symbol.owner.name.asString()}"
                    methodCalls.getOrPut(methodId) { mutableSetOf() }.add(targetId)
                }
                super.visitCall(expression)
            }
        })

        super.visitFunction(declaration)
    }

    fun buildRootsByMethod(): Map<String, Set<String>> {
        val rootsByMethod = mutableMapOf<String, MutableSet<String>>()
        controllerMethodsByClass.values.flatten().forEach { root ->
            val stack = ArrayDeque<String>()
            val visited = mutableSetOf<String>()
            stack.add(root)
            while (stack.isNotEmpty()) {
                val current = stack.removeLast()
                if (!visited.add(current)) continue
                rootsByMethod.getOrPut(current) { mutableSetOf() }.add(root)
                methodCalls[current].orEmpty().forEach { callee ->
                    stack.add(callee)
                }
            }
        }
        return rootsByMethod.mapValues { it.value.toSet() }
    }
}

private class EndpointHandlerCallGraphBuilder : IrVisitorVoid() {
    private val endpointHandlerFq = FqName("com.only4.cap4k.ddd.core.application.endpoint.EndpointHandler")
    private val handlerClasses = mutableSetOf<String>()
    private val handleRoots = mutableSetOf<IrFunction>()
    private val methodCalls = mutableMapOf<IrFunction, MutableSet<IrFunction>>()

    override fun visitElement(element: IrElement) {
        element.acceptChildrenVoid(this)
    }

    override fun visitClass(declaration: IrClass) {
        val fqcn = declaration.fqNameWhenAvailable?.asString()
        if (fqcn != null && declaration.isOrImplements(endpointHandlerFq)) handlerClasses.add(fqcn)
        super.visitClass(declaration)
    }

    override fun visitFunction(declaration: IrFunction) {
        val parentClass = declaration.parent as? IrClass ?: return super.visitFunction(declaration)
        val parentFqcn = parentClass.fqNameWhenAvailable?.asString() ?: return super.visitFunction(declaration)
        if (parentFqcn !in handlerClasses) return super.visitFunction(declaration)

        val implementsHandle = declaration is IrSimpleFunction &&
            declaration.name.asString() == "handle" && declaration.overriddenSymbols.any { symbol ->
            val ownerClass = symbol.owner.parent as? IrClass
            ownerClass?.isOrImplements(endpointHandlerFq) == true
        }
        if (implementsHandle) handleRoots.add(declaration)

        declaration.body?.acceptVoid(object : IrVisitorVoid() {
            override fun visitElement(element: IrElement) {
                element.acceptChildrenVoid(this)
            }

            override fun visitCall(expression: IrCall) {
                val targetClass = expression.symbol.owner.parent as? IrClass
                val targetFq = targetClass?.fqNameWhenAvailable?.asString()
                if (targetFq == parentFqcn) {
                    methodCalls.getOrPut(declaration) { mutableSetOf() }
                        .add(expression.symbol.owner)
                }
                super.visitCall(expression)
            }
        })
        super.visitFunction(declaration)
    }

    fun buildReachableMethods(): Set<IrFunction> {
        val reachable = linkedSetOf<IrFunction>()
        val stack = ArrayDeque<IrFunction>()
        handleRoots.forEach(stack::addLast)
        while (stack.isNotEmpty()) {
            val current = stack.removeLast()
            if (!reachable.add(current)) continue
            methodCalls[current].orEmpty().forEach(stack::addLast)
        }
        return reachable
    }
}

@OptIn(UnsafeDuringIrConstructionAPI::class)
private class ClassIndexBuilder(
    private val options: Cap4kOptions,
) : IrVisitorVoid() {
    private val aggregateInfoByClass = mutableMapOf<String, AggregateInfo>()
    private val aggregateRootsByName = mutableMapOf<String, String>()
    private val entityMethodNamesByClass = mutableMapOf<String, MutableSet<String>>()
    private val domainEventClasses = mutableSetOf<String>()
    private val integrationEventClasses = mutableSetOf<String>()

    private val designBlockMetadataAnn = FqName(DESIGN_BLOCK_METADATA_ANNOTATION_FQ)
    private val aggregateElementMetadataAnn = FqName(AGGREGATE_ELEMENT_METADATA_ANNOTATION_FQ)
    private val domainEventAnn = FqName(options.domainEventAnnFq)
    private val integrationEventAnn = FqName(options.integrationEventAnnFq)

    override fun visitElement(element: IrElement) {
        element.acceptChildrenVoid(this)
    }

    override fun visitClass(declaration: IrClass) {
        val fqcn = declaration.fqNameWhenAvailable?.asString() ?: return

        if (declaration.hasAnnotation(domainEventAnn)) {
            domainEventClasses.add(fqcn)
        }
        if (declaration.hasAnnotation(integrationEventAnn)) {
            integrationEventClasses.add(fqcn)
        }

        val aggInfo = declaration.readAggregateElementInfo(aggregateElementMetadataAnn)
        if (aggInfo != null) {
            aggregateInfoByClass[fqcn] = aggInfo
            when (aggInfo.type) {
                AGG_TYPE_ENTITY -> {
                    if (aggInfo.root) {
                        aggregateRootsByName[aggInfo.aggregateName] = fqcn
                    }
                    val names = entityMethodNamesByClass.getOrPut(fqcn) { mutableSetOf() }
                    declaration.declarations.filterIsInstance<IrFunction>()
                        .map { it.name.asString() }
                        .forEach { names.add(it) }
                }
            }
        }

        super.visitClass(declaration)
    }

    fun build(): ClassIndex = ClassIndex(
        aggregateInfoByClass = aggregateInfoByClass.toMap(),
        aggregateElements = aggregateInfoByClass.entries
            .sortedBy { (carrierQualifiedName, _) -> carrierQualifiedName }
            .map { (carrierQualifiedName, info) ->
                AggregateElementRecord(
                    carrierQualifiedName = carrierQualifiedName,
                    aggregate = info.aggregateName,
                    name = info.name,
                    packageName = info.packageName,
                    description = info.description,
                    type = info.type,
                    root = info.root,
                )
            },
        aggregateRootsByName = aggregateRootsByName.toMap(),
        entityMethodNamesByClass = entityMethodNamesByClass.mapValues { it.value.toSet() },
        domainEventClasses = domainEventClasses.toSet(),
        integrationEventClasses = integrationEventClasses.toSet(),
    )
}

@OptIn(UnsafeDuringIrConstructionAPI::class)
private class GraphCollector(
    private val options: Cap4kOptions,
    private val index: ClassIndex,
    private val controllerRootsByMethod: Map<String, Set<String>>,
    private val endpointHandlerReachableMethods: Set<IrFunction>,
) : IrElementTransformerVoidWithContext() {
    private val nodes = LinkedHashMap<String, Node>()
    private val rels = LinkedHashSet<Relationship>()
    private val missingMetadataByNodeId = LinkedHashMap<String, LinkedHashSet<String>>()
    private val metadataOwnerByNodeId = LinkedHashMap<String, String>()
    private val handlerToCommand: MutableMap<String, String> = mutableMapOf()
    private val handlerContext: ArrayDeque<String> = ArrayDeque()
    private val functionContext: ArrayDeque<FunctionCtx> = ArrayDeque()
    private val aggregateInfoCache: MutableMap<String, AggregateInfo?> = mutableMapOf()
    private val aggregateRootsByName: MutableMap<String, String> = index.aggregateRootsByName.toMutableMap()
    private val domainEventCache: MutableMap<String, Boolean> = mutableMapOf()
    private val integrationEventCache: MutableMap<String, Boolean> = mutableMapOf()
    private val endpointHttpBindings = linkedMapOf<String, EndpointHttpBindingEvidence>()
    private val endpointRequestByHandlerClass = mutableMapOf<String, String>()
    private val endpointHandlerInvocationsByRequest = mutableMapOf<String, MutableList<EndpointHandlerInvocation>>()

    private val restController = FqName("org.springframework.web.bind.annotation.RestController")
    private val scheduled = FqName("org.springframework.scheduling.annotation.Scheduled")
    private val requestMappings = setOf(
        "org.springframework.web.bind.annotation.RequestMapping",
        "org.springframework.web.bind.annotation.GetMapping",
        "org.springframework.web.bind.annotation.PostMapping",
        "org.springframework.web.bind.annotation.PutMapping",
        "org.springframework.web.bind.annotation.DeleteMapping",
        "org.springframework.web.bind.annotation.PatchMapping"
    ).map(::FqName).toSet()

    private val designBlockMetadataAnn = FqName(DESIGN_BLOCK_METADATA_ANNOTATION_FQ)
    private val aggregateElementMetadataAnn = FqName(AGGREGATE_ELEMENT_METADATA_ANNOTATION_FQ)
    private val domainEventAnn = FqName(options.domainEventAnnFq)
    private val integrationEventAnn = FqName(options.integrationEventAnnFq)
    private val eventListenerAnn = FqName(options.eventListenerAnnFq)
    private val commandInterfaceFq = FqName("com.only4.cap4k.ddd.core.application.command.Command")
    private val queryInterfaceFq = FqName("com.only4.cap4k.ddd.core.application.query.Query")
    private val capabilityCallFq = FqName("com.only4.cap4k.ddd.core.application.capability.CapabilityCall")
    private val commandHandlerFq = FqName("com.only4.cap4k.ddd.core.application.command.CommandHandler")
    private val queryHandlerFq = FqName("com.only4.cap4k.ddd.core.application.query.QueryHandler")
    private val capabilityHandlerFq = FqName("com.only4.cap4k.ddd.core.application.capability.CapabilityHandler")
    private val endpointHandlerFq = FqName("com.only4.cap4k.ddd.core.application.endpoint.EndpointHandler")
    private val endpointRequestFq = FqName("com.only4.cap4k.contract.EndpointRequest")
    private val endpointMvcBindingFq = "com.only4.cap4k.ddd.endpoint.http.EndpointMvcBinding"
    private val commandSupervisorFq = FqName("com.only4.cap4k.ddd.core.application.command.CommandSupervisor")
    private val querySupervisorFq = FqName("com.only4.cap4k.ddd.core.application.query.QuerySupervisor")
    private val capabilitySupervisorFq = FqName("com.only4.cap4k.ddd.core.application.capability.CapabilitySupervisor")
    private val repositorySupervisorFq = FqName(options.repositorySupervisorFq)
    private val repositoryFq = FqName("com.only4.cap4k.ddd.core.domain.repo.Repository")
    private val aggregateFactorySupervisorFq = FqName(options.aggregateFactorySupervisorFq)
    private val constraintValidatorFq = FqName("jakarta.validation.ConstraintValidator")
    private val constraintValidatorJavaxFq = FqName("javax.validation.ConstraintValidator")
    private val predicateFq = FqName("com.only4.cap4k.ddd.core.domain.repo.Predicate")
    private val domainServiceAnn = FqName("com.only4.cap4k.ddd.core.domain.service.annotation.DomainService")
    private val springRepositoryAnn = FqName("org.springframework.stereotype.Repository")
    private val jakartaEntityAnn = FqName("jakarta.persistence.Entity")
    private val javaxEntityAnn = FqName("javax.persistence.Entity")

    override fun visitClassNew(declaration: IrClass): IrStatement {
        val fqcn = declaration.fqNameWhenAvailable?.asString() ?: return super.visitClassNew(declaration)
        val classDisplayName = declaration.nestedSimpleName()
        if (index.domainEventClasses.contains(fqcn) || declaration.hasAnnotation(domainEventAnn)) {
            addNode(Node(id = fqcn, name = classDisplayName, fullName = fqcn, type = NodeType.domainevent))
            requireDesignBlockMetadata(fqcn, declaration)
        }
        if (index.integrationEventClasses.contains(fqcn) || declaration.hasAnnotation(integrationEventAnn)) {
            addNode(Node(id = fqcn, name = classDisplayName, fullName = fqcn, type = NodeType.integrationevent))
            requireDesignBlockMetadata(fqcn, declaration)
        }

        val aggInfo = index.aggregateInfoByClass[fqcn]
        if (aggInfo != null) {
            if (aggInfo.type == AGG_TYPE_ENTITY && aggInfo.root) {
                addNode(Node(id = fqcn, name = classDisplayName, fullName = fqcn, type = NodeType.aggregate))
            }
        } else if (declaration.hasAnnotation(jakartaEntityAnn) || declaration.hasAnnotation(javaxEntityAnn)) {
            addNode(Node(id = fqcn, name = classDisplayName, fullName = fqcn, type = NodeType.aggregate))
            requireAggregateElementMetadata(fqcn, declaration)
        }

        val isRepositoryMetadataCarrier = declaration.hasAnnotation(springRepositoryAnn) &&
            declaration.isOrImplements(repositoryFq)
        if (isRepositoryMetadataCarrier && !declaration.hasAnnotation(aggregateElementMetadataAnn)) {
            addNode(Node(id = fqcn, name = classDisplayName, fullName = fqcn, type = NodeType.repository))
            requireAggregateElementMetadata(fqcn, declaration)
        }

        if (declaration.hasAnnotation(domainServiceAnn)) {
            addNode(Node(id = fqcn, name = classDisplayName, fullName = fqcn, type = NodeType.domainservice))
            requireDesignBlockMetadata(fqcn, declaration)
        }

        if (options.scanSpring && declaration.hasAnnotation(restController)) {
            addNode(Node(id = fqcn, name = classDisplayName, fullName = fqcn, type = NodeType.controller))
        }

        val applicationContractNodeType = when {
            fqcn != commandInterfaceFq.asString() && declaration.isOrImplements(commandInterfaceFq) -> NodeType.command
            fqcn != queryInterfaceFq.asString() && declaration.isOrImplements(queryInterfaceFq) -> NodeType.query
            fqcn != capabilityCallFq.asString() && declaration.isOrImplements(capabilityCallFq) -> NodeType.capability
            else -> null
        }
        if (applicationContractNodeType != null) {
            addNode(Node(id = fqcn, name = classDisplayName, fullName = fqcn, type = applicationContractNodeType))
            requireDesignBlockMetadata(fqcn, declaration)
        }

        if (declaration.isOrImplements(endpointHandlerFq)) {
            val endpointRequestClass = resolveRequestClassFromHandlerInterface(declaration, endpointHandlerFq)
            val endpointRequestFq = endpointRequestClass?.fqNameWhenAvailable?.asString()
            if (endpointRequestFq != null) {
                endpointRequestByHandlerClass[fqcn] = endpointRequestFq
            }
        }

        val implementsCommandHandler = declaration.isOrImplements(commandHandlerFq)
        val implementsQueryHandler = declaration.isOrImplements(queryHandlerFq)
        val implementsCapabilityHandler = declaration.isOrImplements(capabilityHandlerFq)
        if (implementsCommandHandler) {
            addNode(Node(id = fqcn, name = classDisplayName, fullName = fqcn, type = NodeType.commandhandler))
            requireDesignBlockMetadata(fqcn, declaration)
            val cmdReqClass = resolveRequestClassFromHandlerInterface(declaration, commandHandlerFq)
            val cmdReqFq = cmdReqClass?.fqNameWhenAvailable?.asString()
            if (cmdReqClass != null && cmdReqFq != null) {
                addNode(Node(id = cmdReqFq, name = cmdReqClass.nestedSimpleName(), fullName = cmdReqFq, type = NodeType.command))
                requireDesignBlockMetadata(cmdReqFq, cmdReqClass)
                handlerToCommand[fqcn] = cmdReqFq
                addRel(Relationship(fromId = cmdReqFq, toId = fqcn, type = RelationshipType.CommandToCommandHandler))
            }
        } else if (implementsQueryHandler) {
            addNode(Node(id = fqcn, name = classDisplayName, fullName = fqcn, type = NodeType.queryhandler))
            requireDesignBlockMetadata(fqcn, declaration)
            val qryReqClass = resolveRequestClassFromHandlerInterface(declaration, queryHandlerFq)
            val qryReqFq = qryReqClass?.fqNameWhenAvailable?.asString()
            if (qryReqClass != null && qryReqFq != null) {
                addNode(Node(id = qryReqFq, name = qryReqClass.nestedSimpleName(), fullName = qryReqFq, type = NodeType.query))
                requireDesignBlockMetadata(qryReqFq, qryReqClass)
                addRel(Relationship(fromId = qryReqFq, toId = fqcn, type = RelationshipType.QueryToQueryHandler))
            }
        } else if (implementsCapabilityHandler) {
            addNode(Node(id = fqcn, name = classDisplayName, fullName = fqcn, type = NodeType.capabilityhandler))
            requireDesignBlockMetadata(fqcn, declaration)
            val capabilityCallClass = resolveRequestClassFromHandlerInterface(declaration, capabilityHandlerFq)
            val capabilityCallFq = capabilityCallClass?.fqNameWhenAvailable?.asString()
            if (capabilityCallClass != null && capabilityCallFq != null) {
                addNode(Node(id = capabilityCallFq, name = capabilityCallClass.nestedSimpleName(), fullName = capabilityCallFq, type = NodeType.capability))
                requireDesignBlockMetadata(capabilityCallFq, capabilityCallClass)
                addRel(Relationship(fromId = capabilityCallFq, toId = fqcn, type = RelationshipType.CapabilityToCapabilityHandler))
            }
        }

        return super.visitClassNew(declaration)
    }

    override fun visitFunctionNew(declaration: IrFunction): IrStatement {
        val parentClass = declaration.parent as? IrClass
        val methodName = declaration.name.asString()
        val parentFqcn = parentClass?.fqNameWhenAvailable?.asString()
        val methodId = if (parentFqcn != null) "$parentFqcn::$methodName" else methodName
        val isReachableEndpointHandlerMethod = declaration in endpointHandlerReachableMethods
        val methodDisplayName = buildMethodDisplayName(parentClass, methodName)
        val entityMethodRef = resolveEntityMethodRef(declaration)

        val isControllerMethod = options.scanSpring &&
            parentClass != null &&
            parentClass.hasAnnotation(restController) &&
            declaration.annotations.any { ann ->
                val annFq = ann.symbol.owner.parentAsClass.fqNameWhenAvailable
                annFq != null && requestMappings.contains(annFq)
            }
        val isTemporalTriggerMethod = options.scanSpring && declaration.hasAnnotation(scheduled)

        if (isControllerMethod) {
            addNode(Node(id = methodId, name = methodDisplayName, fullName = methodId, type = NodeType.controllermethod))
        }

        val eventClass = resolveEventListenerEventClass(declaration)
        val eventTypeFq = eventClass?.fqNameWhenAvailable?.asString()
        val isDomainEventHandler = eventClass != null && isDomainEventClass(eventClass)
        val isIntegrationEventHandler = eventClass != null && isIntegrationEventClass(eventClass)
        val isValidatorMethod = parentClass != null && (
            parentClass.isOrImplements(constraintValidatorFq) ||
                parentClass.isOrImplements(constraintValidatorJavaxFq)
            )
        if ((isDomainEventHandler || isIntegrationEventHandler) && eventTypeFq != null) {
            val handlerType = if (isDomainEventHandler) NodeType.domaineventhandler else NodeType.integrationeventhandler
            addNode(Node(id = methodId, name = methodDisplayName, fullName = methodId, type = handlerType))
            parentClass?.let { requireDesignBlockMetadata(methodId, it) }
            val eventType = eventTypeFq!!
            val eventNodeType = if (isDomainEventHandler) NodeType.domainevent else NodeType.integrationevent
            val eventDisplayName = eventClass?.nestedSimpleName() ?: typeDisplayNameForFqcn(eventType)
            addNode(Node(id = eventType, name = eventDisplayName, fullName = eventType, type = eventNodeType))
            eventClass?.let { requireDesignBlockMetadata(eventType, it) }
            val relType = if (isDomainEventHandler) RelationshipType.DomainEventToHandler else RelationshipType.IntegrationEventToHandler
            addRel(Relationship(fromId = eventType, toId = methodId, type = relType))
        }
        if (isTemporalTriggerMethod && !isControllerMethod && !isDomainEventHandler && !isIntegrationEventHandler) {
            addNode(Node(id = methodId, name = methodDisplayName, fullName = methodId, type = NodeType.temporaltriggermethod))
        }

        if (entityMethodRef != null) {
            addNode(
                Node(
                    id = entityMethodRef.methodId,
                    name = entityMethodRef.displayName,
                    fullName = entityMethodRef.methodId,
                    type = NodeType.entitymethod
                )
            )
        }

        val ctx = when {
            isControllerMethod -> FunctionCtx.CONTROLLER_METHOD
            isDomainEventHandler -> FunctionCtx.DOMAIN_EVENT_HANDLER
            isIntegrationEventHandler -> FunctionCtx.INTEGRATION_EVENT_HANDLER
            isTemporalTriggerMethod -> FunctionCtx.TEMPORAL_TRIGGER_METHOD
            isValidatorMethod -> FunctionCtx.VALIDATOR
            else -> FunctionCtx.OTHER
        }
        functionContext.addLast(ctx)

        val handlerIdForFunction = parentFqcn?.takeIf { handlerToCommand.containsKey(it) }
        if (handlerIdForFunction != null) {
            handlerContext.addLast(handlerIdForFunction)
        }

        val createdAggregates = mutableSetOf<String>()
        val removedAggregates = mutableSetOf<String>()
        var senderMethodAdded = false
        var validatorNodeAdded = false

        declaration.body?.acceptVoid(object : IrVisitorVoid() {
            override fun visitElement(element: IrElement) {
                element.acceptChildrenVoid(this)
            }

            override fun visitCall(expression: IrCall) {
                collectEndpointHttpBinding(expression)
                val calleeName = expression.symbol.owner.name.asString()
                val ownerClass = expression.symbol.owner.parent as? IrClass
                val receiverClass = expression.dispatchReceiverClass()
                val applicationCallKind = when {
                    calleeName == "send" && (
                        receiverClass?.isOrImplements(commandSupervisorFq) == true ||
                            ownerClass?.isOrImplements(commandSupervisorFq) == true
                        ) -> ApplicationCallKind.COMMAND
                    calleeName == "ask" && (
                        receiverClass?.isOrImplements(querySupervisorFq) == true ||
                            ownerClass?.isOrImplements(querySupervisorFq) == true
                        ) -> ApplicationCallKind.QUERY
                    calleeName == "call" && (
                        receiverClass?.isOrImplements(capabilitySupervisorFq) == true ||
                            ownerClass?.isOrImplements(capabilitySupervisorFq) == true
                        ) -> ApplicationCallKind.CAPABILITY
                    else -> null
                }
                val isAggregateFactorySupervisor = receiverClass?.isOrImplements(aggregateFactorySupervisorFq) == true ||
                    ownerClass?.isOrImplements(aggregateFactorySupervisorFq) == true
                val isRepositorySupervisor = receiverClass?.isOrImplements(repositorySupervisorFq) == true ||
                    ownerClass?.isOrImplements(repositorySupervisorFq) == true

                if (applicationCallKind != null) {
                    val markerFq = when (applicationCallKind) {
                        ApplicationCallKind.COMMAND -> commandInterfaceFq
                        ApplicationCallKind.QUERY -> queryInterfaceFq
                        ApplicationCallKind.CAPABILITY -> capabilityCallFq
                    }
                    val requestClass = resolveRequestClassFromExpression(expression.valueArgumentOrNull(0), markerFq)
                    val requestFq = requestClass?.fqNameWhenAvailable?.asString()
                    if (requestClass != null && requestFq != null) {
                        val nodeType = when (applicationCallKind) {
                            ApplicationCallKind.COMMAND -> NodeType.command
                            ApplicationCallKind.QUERY -> NodeType.query
                            ApplicationCallKind.CAPABILITY -> NodeType.capability
                        }
                        val requestDisplayName = requestClass.nestedSimpleName()
                        addNode(Node(id = requestFq, name = requestDisplayName, fullName = requestFq, type = nodeType))
                        requireDesignBlockMetadata(requestFq, requestClass)

                        val endpointRequestFq = parentFqcn?.let(endpointRequestByHandlerClass::get)
                        if (
                            endpointRequestFq != null &&
                            isReachableEndpointHandlerMethod &&
                            applicationCallKind != ApplicationCallKind.CAPABILITY
                        ) {
                            endpointHandlerInvocationsByRequest
                                .getOrPut(endpointRequestFq) { mutableListOf() }
                                .add(EndpointHandlerInvocation(applicationCallKind, requestFq))
                        }

                        val ctx = functionContext.lastOrNull()
                        val senderId = methodId
                        val controllerRoots = if (ctx == FunctionCtx.OTHER) controllerRootsByMethod[senderId].orEmpty() else emptySet()
                        if (controllerRoots.isNotEmpty()) {
                            val relType = requireNotNull(
                                relationshipTypeForSend(applicationCallKind, FunctionCtx.CONTROLLER_METHOD)
                            )
                            controllerRoots.forEach { rootId ->
                                addRel(Relationship(fromId = rootId, toId = requestFq, type = relType))
                            }
                            super.visitCall(expression)
                            return
                        }

                        val relType = relationshipTypeForSend(applicationCallKind, ctx)
                        if (ctx == FunctionCtx.VALIDATOR && !validatorNodeAdded) {
                            addNode(Node(id = senderId, name = methodDisplayName, fullName = senderId, type = NodeType.validator))
                            validatorNodeAdded = true
                        }
                        if (relType != null) {
                            if (relType.isSenderMethodRel() && !senderMethodAdded && ctx == FunctionCtx.OTHER) {
                                val senderType = senderNodeTypeForRel(relType)
                                addNode(Node(id = senderId, name = methodDisplayName, fullName = senderId, type = senderType))
                                senderMethodAdded = true
                            }
                            addRel(Relationship(fromId = senderId, toId = requestFq, type = relType))
                        }
                    }
                }

                if (calleeName == "create" && isAggregateFactorySupervisor) {
                    val payloadClass = resolvePayloadClassFromExpression(expression.valueArgumentOrNull(0))
                    val aggRootFq = resolveAggregateRootFromType(expression.type)
                        ?: payloadClass?.let { resolveAggregateRootFromPayload(it) }
                    val handlerId = handlerContext.lastOrNull()
                    if (aggRootFq != null && handlerId != null) {
                        addNode(Node(id = aggRootFq, name = typeDisplayNameForFqcn(aggRootFq), fullName = aggRootFq, type = NodeType.aggregate))
                        addRel(Relationship(fromId = handlerId, toId = aggRootFq, type = RelationshipType.CommandHandlerToAggregate))
                        createdAggregates.add(aggRootFq)
                    }
                }

                if (calleeName == "remove" && isRepositorySupervisor) {
                    val arg = expression.valueArgumentOrNull(0)
                    val aggRootFq = arg?.type?.let { resolveAggregateFromPredicateType(it) }
                    if (aggRootFq != null) removedAggregates.add(aggRootFq)
                }

                if (calleeName in setOf("findOne", "get", "findById", "find") && isRepositorySupervisor) {
                    val aggRootFq = resolveAggregateRootFromType(expression.type)
                    val handlerId = handlerContext.lastOrNull()
                    if (aggRootFq != null && handlerId != null) {
                        addNode(Node(id = aggRootFq, name = typeDisplayNameForFqcn(aggRootFq), fullName = aggRootFq, type = NodeType.aggregate))
                        addRel(Relationship(fromId = handlerId, toId = aggRootFq, type = RelationshipType.CommandHandlerToAggregate))
                    }
                }

                if (handlerContext.isNotEmpty()) {
                    val handlerId = handlerContext.lastOrNull()
                    val targetMethod = resolveEntityMethodRef(expression.symbol.owner)
                    if (targetMethod != null) {
                        addNode(
                            Node(
                                id = targetMethod.methodId,
                                name = targetMethod.displayName,
                                fullName = targetMethod.methodId,
                                type = NodeType.entitymethod
                            )
                        )
                        if (handlerId != null) {
                            addRel(
                                Relationship(
                                    fromId = handlerId,
                                    toId = targetMethod.methodId,
                                    type = RelationshipType.CommandHandlerToEntityMethod
                                )
                            )
                        }
                        addNode(
                            Node(
                                id = targetMethod.aggregateRootFq,
                                name = typeDisplayNameForFqcn(targetMethod.aggregateRootFq),
                                fullName = targetMethod.aggregateRootFq,
                                type = NodeType.aggregate
                            )
                        )
                        addRel(
                            Relationship(
                                fromId = targetMethod.aggregateRootFq,
                                toId = targetMethod.methodId,
                                type = RelationshipType.AggregateToEntityMethod
                            )
                        )
                    }
                }

                super.visitCall(expression)
            }

            override fun visitConstructorCall(expression: IrConstructorCall) {
                val evtClass = expression.symbol.owner.parentAsClass
                val evtFq = evtClass.fqNameWhenAvailable?.asString()
                if (evtFq != null && isDomainEventClass(evtClass)) {
                    addNode(Node(id = evtFq, name = evtClass.nestedSimpleName(), fullName = evtFq, type = NodeType.domainevent))
                    requireDesignBlockMetadata(evtFq, evtClass)
                    val eventSourceId = entityMethodRef?.methodId ?: methodId
                    addRel(Relationship(fromId = eventSourceId, toId = evtFq, type = RelationshipType.EntityMethodToDomainEvent))
                }
                super.visitConstructorCall(expression)
            }
        })

        if (handlerIdForFunction != null) {
            createdAggregates.forEach { aggFq ->
                addOptionalLifecycleRelationship(handlerIdForFunction, aggFq, "onCreate")
            }
            removedAggregates.forEach { aggFq ->
                addOptionalLifecycleRelationship(handlerIdForFunction, aggFq, "onDeleted")
            }
        }

        if (handlerIdForFunction != null && handlerContext.isNotEmpty()) {
            handlerContext.removeLast()
        }
        functionContext.removeLastOrNull()

        return super.visitFunctionNew(declaration)
    }

    private fun collectEndpointHttpBinding(expression: IrCall) {
        val callee = expression.symbol.owner
        if (callee.name.asString() !in setOf("json", "special")) return
        val ownerClass = callee.parent as? IrClass ?: return
        if (!ownerClass.isNestedWithin(endpointMvcBindingFq)) return

        val requestClass = expression.valueArgumentOrNull(1).classReferenceClass() ?: return
        val responseClass = expression.valueArgumentOrNull(2).classReferenceClass() ?: return
        val requestFq = requestClass.fqNameWhenAvailable?.asString() ?: return
        val operationOwner = requestClass.parent as? IrClass ?: return
        val operationOwnerFq = operationOwner.fqNameWhenAvailable?.asString() ?: return
        if ((responseClass.parent as? IrClass)?.fqNameWhenAvailable?.asString() != operationOwnerFq) return
        if (requestClass.name.asString() != "Request" || responseClass.name.asString() != "Response") return

        val metadata = operationOwner.annotations.firstOrNull {
            it.symbol.owner.parentAsClass.fqNameWhenAvailable == designBlockMetadataAnn
        }
        if (metadata != null) {
            val endpointResponseType = resolveTypeArgumentInHierarchyFromClass(requestClass, endpointRequestFq, 0)
                as? IrSimpleType ?: return
            val endpointResponseClass = endpointResponseType.classifier?.owner as? IrClass ?: return
            if (endpointResponseClass != responseClass) return
        }

        val operationArgument = expression.valueArgumentOrNull(0)
        val operationName = metadata?.getStringArg("operationName")?.trim()?.takeIf(String::isNotEmpty)
            ?: operationArgument.stringConstant()?.trim()?.takeIf(String::isNotEmpty)
            ?: return
        if (metadata != null && metadata.getStringArg("tag")?.trim() != "endpoint") return
        if (!operationArgument.referencesOperationNameOn(operationOwner, operationName, currentFile?.fileEntry, expression)) return

        val method = expression.valueArgumentOrNull(3).enumEntryName() ?: return
        val path = expression.valueArgumentOrNull(4).stringConstant() ?: return
        if (!path.startsWith('/')) return

        endpointHttpBindings.putIfAbsent(
            operationName,
            EndpointHttpBindingEvidence(operationName, operationOwnerFq, requestFq, method.uppercase(), path),
        )
    }

    fun completeEndpointHttpEvidence() {
        endpointHttpBindings.values.sortedBy(EndpointHttpBindingEvidence::operationName).forEach { binding ->
            val displayName = "${binding.operationName} [${binding.method} ${binding.path}]"
            addNode(
                Node(
                    binding.nodeId,
                    displayName,
                    binding.nodeId,
                    NodeType.endpointhttpbinding,
                    metadataOwner = binding.operationOwnerFq,
                )
            )
            endpointHandlerInvocationsByRequest[binding.requestFq].orEmpty()
                .distinct()
                .forEach { invocation ->
                    val relationshipType = when (invocation.kind) {
                        ApplicationCallKind.COMMAND -> RelationshipType.EndpointHttpBindingToCommand
                        ApplicationCallKind.QUERY -> RelationshipType.EndpointHttpBindingToQuery
                        ApplicationCallKind.CAPABILITY -> return@forEach
                    }
                    addRel(Relationship(binding.nodeId, invocation.targetFq, relationshipType))
                }
        }
    }

    private fun resolveRequestClassFromHandlerInterface(declaration: IrClass, handlerFq: FqName): IrClass? {
        val requestType = resolveTypeArgumentInHierarchyFromClass(declaration, handlerFq, 0) ?: return null
        val simple = requestType as? IrSimpleType ?: return null
        return simple.classifier?.owner as? IrClass
    }

    private fun resolveRequestClassFromExpression(expression: IrExpression?, markerFq: FqName): IrClass? {
        val unwrapped = expression?.unwrapExpression() ?: return null
        val ctorClass = (unwrapped as? IrConstructorCall)?.symbol?.owner?.parentAsClass
        if (ctorClass != null && ctorClass.isOrImplements(markerFq)) return ctorClass
        val type = unwrapped.type as? IrSimpleType ?: return null
        val cls = type.classifier?.owner as? IrClass ?: return null
        return if (cls.isOrImplements(markerFq)) cls else null
    }

    private fun resolvePayloadClassFromExpression(expression: IrExpression?): IrClass? {
        val unwrapped = expression?.unwrapExpression() ?: return null
        val ctorClass = (unwrapped as? IrConstructorCall)?.symbol?.owner?.parentAsClass
        if (ctorClass != null) return ctorClass
        val type = unwrapped.type as? IrSimpleType ?: return null
        return type.classifier?.owner as? IrClass
    }

    private fun resolveAggregateRootFromPayload(payloadClass: IrClass): String? {
        return null
    }

    private fun resolveAggregateRootFromExpression(expression: IrExpression?): String? {
        val unwrapped = expression?.unwrapExpression() ?: return null
        val type = unwrapped.type
        return resolveAggregateRootFromType(type)
    }

    private fun resolveAggregateRootFromType(type: IrType): String? {
        val simple = type as? IrSimpleType ?: return null
        val cls = simple.classifier?.owner as? IrClass ?: return null
        val fq = cls.fqNameWhenAvailable?.asString() ?: return null
        val info = cls.aggregateInfo() ?: return null
        if (info.type != AGG_TYPE_ENTITY) return null
        return if (info.root) {
            aggregateRootsByName.putIfAbsent(info.aggregateName, fq)
            fq
        } else {
            aggregateRootsByName[info.aggregateName]
                ?: inferGeneratedAggregateRootFq(fq, info.aggregateName)
                ?: fq
        }
    }

    private fun resolveEntityMethodRef(function: IrFunction): EntityMethodRef? {
        val methodName = function.name.asString()
        val parentClass = function.parent as? IrClass
        if (parentClass != null) {
            val parentFq = parentClass.fqNameWhenAvailable?.asString() ?: return null
            val aggInfo = parentClass.aggregateInfo() ?: return null
            if (aggInfo.type != AGG_TYPE_ENTITY) return null
            val aggregateRootFq = if (aggInfo.root) {
                parentFq
            } else {
                aggInfo.aggregateName
                    .takeIf { it.isNotEmpty() }
                    ?.let { aggregateRootsByName[it] ?: inferGeneratedAggregateRootFq(parentFq, it) }
                    ?: parentFq
            }
            val methodId = "$parentFq::$methodName"
            return EntityMethodRef(
                aggregateRootFq = aggregateRootFq,
                methodId = methodId,
                displayName = buildMethodDisplayName(parentClass, methodName)
            )
        }

        val parent = function.parent
        if (parent !is IrFile && parent !is IrPackageFragment) return null

        val receiverType = function.parameters
            .firstOrNull { it.kind == IrParameterKind.ExtensionReceiver }
            ?.type
            ?: return null
        val receiverSimpleType = receiverType as? IrSimpleType ?: return null
        val receiverClass = receiverSimpleType.classifier?.owner as? IrClass ?: return null
        val receiverFq = receiverClass.fqNameWhenAvailable?.asString() ?: return null
        val receiverInfo = receiverClass.aggregateInfo() ?: return null
        if (receiverInfo.type != AGG_TYPE_ENTITY) return null

        val aggregateRootFq = if (receiverInfo.root) {
            receiverFq
        } else {
            receiverInfo.aggregateName
                .takeIf { it.isNotEmpty() }
                ?.let { aggregateRootsByName[it] ?: inferGeneratedAggregateRootFq(receiverFq, it) }
                ?: receiverFq
        }
        val methodId = "$receiverFq::$methodName"
        return EntityMethodRef(
            aggregateRootFq = aggregateRootFq,
            methodId = methodId,
            displayName = buildMethodDisplayNameFromFqcn(receiverFq, methodName)
        )
    }

    private fun resolveAggregateFromPredicateType(type: IrType): String? {
        val simple = type as? IrSimpleType ?: return null
        val cls = simple.classifier?.owner as? IrClass ?: return null
        val fq = cls.fqNameWhenAvailable?.asString()
        return when {
            fq == predicateFq.asString() -> {
                val arg = simple.arguments.getOrNull(0) as? org.jetbrains.kotlin.ir.types.IrTypeProjection
                arg?.type?.let { resolveAggregateRootFromType(it) }
            }
            cls.isOrImplements(predicateFq) -> {
                val directArg = (simple.arguments.getOrNull(0) as? org.jetbrains.kotlin.ir.types.IrTypeProjection)?.type
                val superArg = cls.findSuperTypeArgument(predicateFq, 0)
                (directArg ?: superArg)?.let { resolveAggregateRootFromType(it) }
            }
            else -> null
        }
    }

    private fun resolveEventListenerEventClass(declaration: IrFunction): IrClass? {
        val ann = declaration.annotations.firstOrNull { it.symbol.owner.parentAsClass.fqNameWhenAvailable == eventListenerAnn }
            ?: return null
        val eventClass = ann.getClassArgClass("value")
            ?: ann.getClassArgClass("classes")
        if (eventClass != null) return eventClass
        val param = (declaration as? IrSimpleFunction)
            ?.parameters
            ?.firstOrNull { it.kind == IrParameterKind.Regular || it.kind == IrParameterKind.Context }
        val paramType = param?.type as? IrSimpleType ?: return null
        return paramType.classifier?.owner as? IrClass
    }

    private fun addOptionalLifecycleRelationship(handlerId: String, aggregateFq: String, methodName: String) {
        if (methodName !in index.entityMethodNamesByClass[aggregateFq].orEmpty()) return

        val methodId = "$aggregateFq::$methodName"
        val displayName = buildMethodDisplayNameFromFqcn(aggregateFq, methodName)
        addNode(Node(id = methodId, name = displayName, fullName = methodId, type = NodeType.entitymethod))
        addRel(Relationship(fromId = handlerId, toId = methodId, type = RelationshipType.CommandHandlerToEntityMethod))
        addRel(Relationship(fromId = aggregateFq, toId = methodId, type = RelationshipType.AggregateToEntityMethod))
    }

    private fun isDomainEventClass(irClass: IrClass): Boolean {
        val fq = irClass.fqNameWhenAvailable?.asString() ?: return false
        return domainEventCache.getOrPut(fq) {
            index.domainEventClasses.contains(fq) || irClass.hasAnnotation(domainEventAnn)
        }
    }

    private fun isIntegrationEventClass(irClass: IrClass): Boolean {
        val fq = irClass.fqNameWhenAvailable?.asString() ?: return false
        return integrationEventCache.getOrPut(fq) {
            index.integrationEventClasses.contains(fq) || irClass.hasAnnotation(integrationEventAnn)
        }
    }

    private fun addNode(node: Node) {
        nodes.putIfAbsent(node.id, node)
    }

    private fun requireDesignBlockMetadata(nodeId: String, declaration: IrClass) {
        if (declaration.findEnclosingAnnotation(designBlockMetadataAnn) == null) {
            missingMetadataByNodeId
                .getOrPut(nodeId) { linkedSetOf() }
                .add(designBlockMetadataAnn.asString())
            metadataOwnerByNodeId.putIfAbsent(nodeId, declaration.analysisMetadataOwnerSymbol())
        }
    }

    private fun requireAggregateElementMetadata(nodeId: String, declaration: IrClass) {
        if (!declaration.hasAnnotation(aggregateElementMetadataAnn)) {
            missingMetadataByNodeId
                .getOrPut(nodeId) { linkedSetOf() }
                .add(aggregateElementMetadataAnn.asString())
            metadataOwnerByNodeId.putIfAbsent(nodeId, declaration.analysisMetadataOwnerSymbol())
        }
    }

    private fun IrClass.analysisMetadataOwnerSymbol(): String {
        var current = this
        while (current.parent is IrClass) {
            current = current.parent as IrClass
        }
        return current.fqNameWhenAvailable?.asString()
            ?: fqNameWhenAvailable?.asString()
            ?: name.asString()
    }

    private fun IrClass.findEnclosingAnnotation(annotation: FqName): IrClass? {
        var current: IrClass? = this
        while (current != null) {
            if (current.hasAnnotation(annotation)) {
                return current
            }
            current = current.parent as? IrClass
        }
        return null
    }

    private fun addRel(rel: Relationship) {
        rels.add(rel)
    }

    fun nodesAsSequence(): Sequence<Node> = nodes.values.asSequence().map { node ->
        node.copy(
            missingMetadata = (node.missingMetadata + missingMetadataByNodeId[node.id].orEmpty()).distinct(),
            metadataOwner = metadataOwnerByNodeId[node.id] ?: node.metadataOwner,
        )
    }
    fun relsAsSequence(): Sequence<Relationship> = rels.asSequence()

    private fun IrClass.aggregateInfo(): AggregateInfo? {
        val fq = fqNameWhenAvailable?.asString() ?: return null
        return aggregateInfoCache.getOrPut(fq) {
            index.aggregateInfoByClass[fq]
                ?: readAggregateElementInfo(aggregateElementMetadataAnn)
        }
    }
}

private enum class FunctionCtx {
    CONTROLLER_METHOD,
    DOMAIN_EVENT_HANDLER,
    INTEGRATION_EVENT_HANDLER,
    TEMPORAL_TRIGGER_METHOD,
    VALIDATOR,
    OTHER
}

private enum class ApplicationCallKind {
    COMMAND,
    QUERY,
    CAPABILITY
}

private fun relationshipTypeForSend(kind: ApplicationCallKind, ctx: FunctionCtx?): RelationshipType? {
    return when (kind) {
        ApplicationCallKind.COMMAND -> when (ctx) {
            FunctionCtx.CONTROLLER_METHOD -> RelationshipType.ControllerMethodToCommand
            FunctionCtx.DOMAIN_EVENT_HANDLER -> RelationshipType.DomainEventHandlerToCommand
            FunctionCtx.INTEGRATION_EVENT_HANDLER -> RelationshipType.IntegrationEventHandlerToCommand
            FunctionCtx.TEMPORAL_TRIGGER_METHOD -> RelationshipType.TemporalTriggerMethodToCommand
            else -> null
        }
        ApplicationCallKind.QUERY -> when (ctx) {
            FunctionCtx.CONTROLLER_METHOD -> RelationshipType.ControllerMethodToQuery
            FunctionCtx.DOMAIN_EVENT_HANDLER -> RelationshipType.DomainEventHandlerToQuery
            FunctionCtx.INTEGRATION_EVENT_HANDLER -> RelationshipType.IntegrationEventHandlerToQuery
            FunctionCtx.VALIDATOR -> RelationshipType.ValidatorToQuery
            else -> RelationshipType.QuerySenderMethodToQuery
        }
        ApplicationCallKind.CAPABILITY -> when (ctx) {
            FunctionCtx.CONTROLLER_METHOD -> RelationshipType.ControllerMethodToCapability
            FunctionCtx.DOMAIN_EVENT_HANDLER -> RelationshipType.DomainEventHandlerToCapability
            FunctionCtx.INTEGRATION_EVENT_HANDLER -> RelationshipType.IntegrationEventHandlerToCapability
            else -> RelationshipType.CapabilitySenderMethodToCapability
        }
    }
}

private fun IrClass.nestedSimpleName(): String {
    val names = mutableListOf<String>()
    var current: IrClass? = this
    while (current != null) {
        names.add(current.name.asString())
        current = current.parent as? IrClass
    }
    return names.asReversed().joinToString(".")
}

private fun buildMethodDisplayName(parentClass: IrClass?, methodName: String): String {
    return if (parentClass != null) "${parentClass.nestedSimpleName()}::$methodName" else methodName
}

private fun buildMethodDisplayNameFromFqcn(fqcn: String, methodName: String): String {
    return "${typeDisplayNameForFqcn(fqcn)}::$methodName"
}

private fun typeDisplayNameForFqcn(fqcn: String): String {
    val normalized = fqcn.replace('$', '.')
    val parts = normalized.split('.')
    if (parts.isEmpty()) return normalized
    val firstClassIndex = parts.indexOfFirst { part ->
        part.firstOrNull()?.isUpperCase() == true
    }
    return if (firstClassIndex == -1) parts.last() else parts.drop(firstClassIndex).joinToString(".")
}

private fun senderNodeTypeForRel(relType: RelationshipType): NodeType {
    return when (relType) {
        RelationshipType.QuerySenderMethodToQuery -> NodeType.querysendermethod
        RelationshipType.CapabilitySenderMethodToCapability -> NodeType.capabilitysendermethod
        else -> error("Relationship $relType does not use a generic sender method node")
    }
}

private fun RelationshipType.isSenderMethodRel(): Boolean {
    return this == RelationshipType.QuerySenderMethodToQuery ||
        this == RelationshipType.CapabilitySenderMethodToCapability
}

private fun resolveTypeArgumentInHierarchyFromClass(
    clazz: IrClass,
    targetFq: FqName,
    index: Int,
): IrType? {
    clazz.superTypes.forEach { st ->
        val simple = st as? IrSimpleType ?: return@forEach
        val resolved = resolveTypeArgumentInHierarchy(simple, targetFq, index, emptyMap(), mutableSetOf())
        if (resolved != null) return resolved
    }
    return null
}

private fun resolveTypeArgumentInHierarchy(
    type: IrSimpleType,
    targetFq: FqName,
    index: Int,
    inheritedMapping: Map<org.jetbrains.kotlin.ir.symbols.IrTypeParameterSymbol, IrType>,
    visited: MutableSet<IrClass>,
): IrType? {
    val cls = type.classifier?.owner as? IrClass ?: return null
    if (!visited.add(cls)) return null

    val mapping = buildTypeParameterMapping(cls, type, inheritedMapping)
    val fq = cls.fqNameWhenAvailable
    if (fq == targetFq) {
        val arg = type.arguments.getOrNull(index) as? org.jetbrains.kotlin.ir.types.IrTypeProjection ?: return null
        return resolveTypeParameter(arg.type, mapping)
    }

    cls.superTypes.forEach { st ->
        val simple = st as? IrSimpleType ?: return@forEach
        val resolved = resolveTypeArgumentInHierarchy(simple, targetFq, index, mapping, visited)
        if (resolved != null) return resolved
    }
    return null
}

private fun buildTypeParameterMapping(
    cls: IrClass,
    type: IrSimpleType,
    inheritedMapping: Map<org.jetbrains.kotlin.ir.symbols.IrTypeParameterSymbol, IrType>,
): Map<org.jetbrains.kotlin.ir.symbols.IrTypeParameterSymbol, IrType> {
    if (cls.typeParameters.isEmpty()) return inheritedMapping
    val mapping = LinkedHashMap<org.jetbrains.kotlin.ir.symbols.IrTypeParameterSymbol, IrType>(inheritedMapping)
    cls.typeParameters.forEachIndexed { idx, param ->
        val arg = type.arguments.getOrNull(idx) as? org.jetbrains.kotlin.ir.types.IrTypeProjection ?: return@forEachIndexed
        val argType = resolveTypeParameter(arg.type, inheritedMapping)
        mapping[param.symbol] = argType
    }
    return mapping
}

private fun resolveTypeParameter(
    type: IrType,
    mapping: Map<org.jetbrains.kotlin.ir.symbols.IrTypeParameterSymbol, IrType>,
): IrType {
    val simple = type as? IrSimpleType ?: return type
    val classifier = simple.classifier
    if (classifier is org.jetbrains.kotlin.ir.symbols.IrTypeParameterSymbol) {
        val mapped = mapping[classifier] ?: return type
        return if (mapped == type) mapped else resolveTypeParameter(mapped, mapping)
    }
    return type
}

private fun IrCall.dispatchReceiverClass(): IrClass? {
    val receiverParam = symbol.owner.parameters.firstOrNull { it.kind == IrParameterKind.DispatchReceiver }
        ?: return null
    val receiver = arguments.get(receiverParam) ?: return null
    val type = receiver.type as? IrSimpleType ?: return null
    return type.classifier?.owner as? IrClass
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

private fun IrClass.findSuperTypeArgument(fqName: FqName, index: Int): IrType? {
    return superTypes.firstNotNullOfOrNull { t ->
        val st = t as? IrSimpleType ?: return@firstNotNullOfOrNull null
        val owner = st.classifier?.owner as? IrClass ?: return@firstNotNullOfOrNull null
        if (owner.fqNameWhenAvailable != fqName) return@firstNotNullOfOrNull null
        val arg = st.arguments.getOrNull(index) as? org.jetbrains.kotlin.ir.types.IrTypeProjection
        arg?.type
    }
}

private fun IrClass.readAggregateElementInfo(aggregateElementMetadataAnn: FqName): AggregateInfo? {
    val ann = annotations.firstOrNull { it.symbol.owner.parentAsClass.fqNameWhenAvailable == aggregateElementMetadataAnn }
        ?: return null
    val className = fqNameWhenAvailable?.asString() ?: name.asString()
    val aggregateName = ann.getStringArg("aggregate").orEmpty().trim()
    val name = ann.getStringArg("name").orEmpty().trim()
    val packageName = ann.getStringArg("packageName").orEmpty().trim()
    val description = ann.getStringArg("description").orEmpty().trim()
    val type = ann.getStringArg("type").orEmpty().trim()
    require(aggregateName.isNotEmpty()) {
        "AggregateElementMetadata annotation on $className must declare non-blank aggregate"
    }
    require(type.isNotEmpty()) {
        "AggregateElementMetadata annotation on $className must declare non-blank type"
    }
    require(type in SUPPORTED_AGGREGATE_ELEMENT_TYPES) {
        "AggregateElementMetadata annotation on $className has unsupported type: $type"
    }
    val root = ann.getBooleanArg("root") ?: false
    return AggregateInfo(
        aggregateName = aggregateName,
        name = name,
        packageName = packageName,
        description = description,
        type = type,
        root = root,
    )
}

private fun inferGeneratedAggregateRootFq(entityFq: String, aggregateName: String): String? {
    val packageName = entityFq.substringBeforeLast('.', missingDelimiterValue = "")
    val aggregateToken = packageName.substringAfter(".domain.aggregates.", missingDelimiterValue = "")
    if (aggregateToken.isBlank() || aggregateToken.contains('.')) return null
    val inferredAggregateName = aggregateToken.toUpperCamelCase()
    if (inferredAggregateName != aggregateName) return null
    return "$packageName.$aggregateName"
}

private fun String.toUpperCamelCase(): String {
    return split('_', '-', '.')
        .filter { it.isNotBlank() }
        .joinToString("") { part ->
            part.lowercase().replaceFirstChar { ch -> ch.titlecase() }
        }
}

private fun IrConstructorCall.getStringArg(name: String): String? {
    val idx = symbol.owner.valueParameterIndex(name)
    if (idx < 0) return null
    val arg = valueArgumentOrNull(idx) as? IrConst ?: return null
    return arg.value as? String
}

private fun IrConstructorCall.getBooleanArg(name: String): Boolean? {
    val idx = symbol.owner.valueParameterIndex(name)
    if (idx < 0) return null
    val arg = valueArgumentOrNull(idx) as? IrConst ?: return null
    return arg.value as? Boolean
}

private fun IrConstructorCall.getClassArgClass(name: String): IrClass? {
    val idx = symbol.owner.valueParameterIndex(name)
    if (idx < 0) return null
    val arg = valueArgumentOrNull(idx)
    return when (arg) {
        is IrClassReference -> {
            (arg.classType as? IrSimpleType)?.classifier?.owner as? IrClass
        }
        is IrVararg -> {
            val first = arg.elements.firstOrNull() as? IrClassReference
            (first?.classType as? IrSimpleType)?.classifier?.owner as? IrClass
        }
        else -> null
    }
}

private fun IrMemberAccessExpression<*>.valueArgumentOrNull(index: Int): IrExpression? {
    val owner = symbol.owner as? IrFunction ?: return null
    val valueParams = owner.parameters.filter { param ->
        param.kind == IrParameterKind.Regular || param.kind == IrParameterKind.Context
    }
    val param = valueParams.getOrNull(index) ?: return null
    return arguments.get(param)
}

private fun IrFunction.valueParameterIndex(name: String): Int {
    var idx = 0
    for (param in parameters) {
        if (param.kind == IrParameterKind.Regular || param.kind == IrParameterKind.Context) {
            if (param.name.asString() == name) return idx
            idx++
        }
    }
    return -1
}

private fun IrClass.isNestedWithin(fqcn: String): Boolean {
    var current: IrClass? = this
    while (current != null) {
        if (current.fqNameWhenAvailable?.asString() == fqcn) return true
        current = current.parent as? IrClass
    }
    return false
}

private fun IrExpression?.classReferenceClass(): IrClass? {
    val reference = this?.unwrapExpression() as? IrClassReference ?: return null
    return (reference.classType as? IrSimpleType)?.classifier?.owner as? IrClass
}

private fun IrExpression?.stringConstant(): String? =
    (this?.unwrapExpression() as? IrConst)?.value as? String

private fun IrExpression?.enumEntryName(): String? {
    val expression = this?.unwrapExpression() ?: return null
    return when (expression) {
        is IrGetEnumValue -> expression.symbol.owner.name.asString()
        is IrGetField -> expression.symbol.owner.name.asString()
        is IrCall -> expression.symbol.owner.name.asString().removePrefix("<get-").removeSuffix(">")
        else -> null
    }
}

private fun IrExpression?.referencesOperationNameOn(
    operationOwner: IrClass,
    expectedOperationName: String,
    fileEntry: IrFileEntry?,
    callExpression: IrCall,
): Boolean {
    val expression = this?.unwrapExpression() ?: return false
    if (expression is IrConst) {
        if (expression.value != expectedOperationName) return false
        val source = fileEntry.sourceContents() ?: return false
        val ownerName = operationOwner.fqNameWhenAvailable?.asString()?.substringAfterLast('.')
            ?: operationOwner.name.asString()
        if (expression.hasGeneratedOperationNameSourceReference(source, ownerName)) return true
        return callExpression.hasGeneratedOperationNameArgument(source, ownerName)
    }
    val declaration = when (expression) {
        is IrCall -> expression.symbol.owner
        is IrGetField -> expression.symbol.owner
        else -> return false
    }
    if (declaration.name.asString() !in setOf("OPERATION_NAME", "<get-OPERATION_NAME>", "getOPERATION_NAME")) return false
    var owner = declaration.parent as? IrClass
    while (owner != null) {
        if (owner == operationOwner) return true
        owner = owner.parent as? IrClass
    }
    return false
}

private fun IrExpression.hasGeneratedOperationNameSourceReference(source: String, ownerName: String): Boolean {
    if (startOffset < 0 || endOffset <= startOffset || endOffset > source.length) return false
    val sourceReference = source.substring(startOffset, endOffset).trim()
    if (sourceReference == "$ownerName.OPERATION_NAME" || sourceReference.endsWith(".$ownerName.OPERATION_NAME")) {
        return true
    }
    if (sourceReference != "OPERATION_NAME") return false
    return source.substring(0, startOffset).trimEnd().endsWith("$ownerName.")
}

private fun IrCall.hasGeneratedOperationNameArgument(source: String, ownerName: String): Boolean {
    if (startOffset < 0 || endOffset <= startOffset || endOffset > source.length) return false
    val callSource = source.substring(startOffset, endOffset)
    val qualifiedOwner = "(?:[A-Za-z_][A-Za-z0-9_]*\\.)*${Regex.escape(ownerName)}"
    val operationReference = "$qualifiedOwner\\.OPERATION_NAME"
    val namedArgument = Regex("""\boperationName\s*=\s*$operationReference\b""")
    if (namedArgument.containsMatchIn(callSource)) return true
    val positionalArgument = Regex("""\(\s*$operationReference\s*[,)]""")
    return positionalArgument.containsMatchIn(callSource)
}

private fun IrFileEntry?.sourceContents(): String? {
    val entry = this ?: return null
    return when (entry) {
        is PsiIrFileEntry -> entry.psiFile.text
        else -> entry.name.takeIf(String::isNotBlank)?.let { sourceFileName ->
            runCatching { java.nio.file.Files.readString(kotlin.io.path.Path(sourceFileName)) }.getOrNull()
        }
    }
}

private fun IrExpression.unwrapExpression(): IrExpression {
    var current: IrExpression = this
    while (true) {
        current = when (current) {
            is IrTypeOperatorCall -> current.argument
            is IrBlock -> current.statements.lastOrNull() as? IrExpression ?: return current
            is IrComposite -> current.statements.lastOrNull() as? IrExpression ?: return current
            else -> return current
        }
    }
}

private const val AGG_TYPE_ENTITY = "entity"
private val SUPPORTED_AGGREGATE_ELEMENT_TYPES = setOf(
    "schema",
    AGG_TYPE_ENTITY,
    "repository",
    "factory",
    "strong-id",
    "projection",
)

private class JsonFileMetadataSink(private val outputDir: String) : MetadataSink {
    override fun write(nodes: Sequence<Node>, relationships: Sequence<Relationship>) {
        val dir = kotlin.io.path.Path(outputDir).createDirectories()
        (dir / "nodes.json").writeText(serializeNodes(nodes))
        (dir / "rels.json").writeText(serializeRels(relationships))
    }

    private fun serializeNodes(nodes: Sequence<Node>): String = buildString {
        append('[')
        var first = true
        nodes.forEach { n ->
            if (!first) append(',') else first = false
            append("{\"id\":\"").append(escape(n.id)).append("\",")
            append("\"name\":\"").append(escape(n.name)).append("\",")
            append("\"fullName\":\"").append(escape(n.fullName)).append("\",")
            append("\"type\":\"").append(n.type.name).append('\"')
            if (n.missingMetadata.isNotEmpty()) {
                append(",\"missingMetadata\":[")
                n.missingMetadata.forEachIndexed { index, metadataFq ->
                    if (index > 0) append(',')
                    append('\"').append(escape(metadataFq)).append('\"')
                }
                append(']')
            }
            n.metadataOwner?.takeIf { owner -> owner.isNotBlank() }?.let { owner ->
                append(",\"metadataOwner\":\"").append(escape(owner)).append('\"')
            }
            append('}')
        }
        append(']')
    }

    private fun serializeRels(rels: Sequence<Relationship>): String = buildString {
        append('[')
        var first = true
        rels.forEach { r ->
            if (!first) append(',') else first = false
            append("{\"fromId\":\"").append(escape(r.fromId)).append("\",")
            append("\"toId\":\"").append(escape(r.toId)).append("\",")
            append("\"type\":\"").append(r.type.name).append("\"")
            val lbl = r.label
            if (lbl != null) append(",\"label\":\"").append(escape(lbl)).append("\"")
            append('}')
        }
        append(']')
    }

    private fun escape(s: String): String = s
        .replace("\\", "\\\\")
        .replace("\"", "\\\"")
        .replace("\n", " ")
}
