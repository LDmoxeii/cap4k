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
import org.jetbrains.kotlin.ir.IrStatement
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

        val collector = GraphCollector(options, index, controllerRoots)
        moduleFragment.files.forEach { file ->
            file.accept(collector, null)
        }

        val fallback = options.outputDir
        val filePaths = moduleFragment.files.map { it.fileEntry.name }
        val outDir = resolveOutputDir(filePaths, fallback)
        JsonFileMetadataSink(outDir.toString()).write(collector.nodesAsSequence(), collector.relsAsSequence())
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

private data class AggregateInfo(val aggregateName: String, val type: String, val root: Boolean)

private data class ClassIndex(
    val aggregateInfoByClass: Map<String, AggregateInfo>,
    val aggregateRootsByName: Map<String, String>,
    val payloadToAggregateName: Map<String, String>,
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

@OptIn(UnsafeDuringIrConstructionAPI::class)
private class ClassIndexBuilder(
    private val options: Cap4kOptions,
) : IrVisitorVoid() {
    private val aggregateInfoByClass = mutableMapOf<String, AggregateInfo>()
    private val aggregateRootsByName = mutableMapOf<String, String>()
    private val payloadToAggregateName = mutableMapOf<String, String>()
    private val entityMethodNamesByClass = mutableMapOf<String, MutableSet<String>>()
    private val domainEventClasses = mutableSetOf<String>()
    private val integrationEventClasses = mutableSetOf<String>()

    private val aggregateAnn = FqName(options.aggregateAnnFq)
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

        val aggInfo = declaration.readAggregateInfo(aggregateAnn)
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
                AGG_TYPE_FACTORY_PAYLOAD -> payloadToAggregateName[fqcn] = aggInfo.aggregateName
                AGG_TYPE_DOMAIN_EVENT -> domainEventClasses.add(fqcn)
            }
        }

        super.visitClass(declaration)
    }

    fun build(): ClassIndex = ClassIndex(
        aggregateInfoByClass = aggregateInfoByClass.toMap(),
        aggregateRootsByName = aggregateRootsByName.toMap(),
        payloadToAggregateName = payloadToAggregateName.toMap(),
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
) : IrElementTransformerVoidWithContext() {
    private val nodes = LinkedHashMap<String, Node>()
    private val rels = LinkedHashSet<Relationship>()
    private val handlerToCommand: MutableMap<String, String> = mutableMapOf()
    private val requestKindByFq: MutableMap<String, RequestKind> = mutableMapOf()
    private val handlerContext: ArrayDeque<String> = ArrayDeque()
    private val functionContext: ArrayDeque<FunctionCtx> = ArrayDeque()
    private val aggregateInfoCache: MutableMap<String, AggregateInfo?> = mutableMapOf()
    private val aggregateRootsByName: MutableMap<String, String> = index.aggregateRootsByName.toMutableMap()
    private val domainEventCache: MutableMap<String, Boolean> = mutableMapOf()
    private val integrationEventCache: MutableMap<String, Boolean> = mutableMapOf()

    private val restController = FqName("org.springframework.web.bind.annotation.RestController")
    private val requestMappings = setOf(
        "org.springframework.web.bind.annotation.RequestMapping",
        "org.springframework.web.bind.annotation.GetMapping",
        "org.springframework.web.bind.annotation.PostMapping",
        "org.springframework.web.bind.annotation.PutMapping",
        "org.springframework.web.bind.annotation.DeleteMapping",
        "org.springframework.web.bind.annotation.PatchMapping"
    ).map(::FqName).toSet()

    private val aggregateAnn = FqName(options.aggregateAnnFq)
    private val domainEventAnn = FqName(options.domainEventAnnFq)
    private val integrationEventAnn = FqName(options.integrationEventAnnFq)
    private val eventListenerAnn = FqName(options.eventListenerAnnFq)

    private val commandInterfaceFq = FqName("com.only4.cap4k.ddd.core.application.command.Command")
    private val queryInterfaceFq = FqName("com.only4.cap4k.ddd.core.application.query.Query")
    private val requestHandlerFq = FqName("com.only4.cap4k.ddd.core.application.RequestHandler")
    private val requestSupervisorFq = FqName(options.requestSupervisorFq)
    private val unitOfWorkFq = FqName(options.unitOfWorkFq)
    private val repositorySupervisorFq = FqName(options.repositorySupervisorFq)
    private val aggregateFactorySupervisorFq = FqName(options.aggregateFactorySupervisorFq)
    private val requestParamFq = FqName(options.requestParamFq)
    private val constraintValidatorFq = FqName("jakarta.validation.ConstraintValidator")
    private val constraintValidatorJavaxFq = FqName("javax.validation.ConstraintValidator")
    private val predicateFq = FqName("com.only4.cap4k.ddd.core.domain.repo.Predicate")
    private val aggregatePredicateFq = FqName("com.only4.cap4k.ddd.core.domain.aggregate.AggregatePredicate")

    override fun visitClassNew(declaration: IrClass): IrStatement {
        val fqcn = declaration.fqNameWhenAvailable?.asString() ?: return super.visitClassNew(declaration)
        val classDisplayName = declaration.nestedSimpleName()

        if (index.domainEventClasses.contains(fqcn) || declaration.hasAnnotation(domainEventAnn)) {
            addNode(Node(id = fqcn, name = classDisplayName, fullName = fqcn, type = NodeType.domainevent))
        }
        if (index.integrationEventClasses.contains(fqcn) || declaration.hasAnnotation(integrationEventAnn)) {
            addNode(Node(id = fqcn, name = classDisplayName, fullName = fqcn, type = NodeType.integrationevent))
        }

        val aggInfo = index.aggregateInfoByClass[fqcn]
        if (aggInfo != null && aggInfo.type == AGG_TYPE_ENTITY && aggInfo.root) {
            addNode(Node(id = fqcn, name = classDisplayName, fullName = fqcn, type = NodeType.aggregate))
        }

        if (options.scanSpring && declaration.hasAnnotation(restController)) {
            addNode(Node(id = fqcn, name = classDisplayName, fullName = fqcn, type = NodeType.controller))
        }

        val implementsCommand = declaration.isOrImplements(commandInterfaceFq)
        val implementsQuery = declaration.isOrImplements(queryInterfaceFq)
        val implementsRequestHandler = !implementsCommand &&
            !implementsQuery &&
            declaration.isOrImplements(requestHandlerFq)
        if (implementsCommand) {
            addNode(Node(id = fqcn, name = classDisplayName, fullName = fqcn, type = NodeType.commandhandler))
            val cmdReqClass = resolveRequestClassFromHandlerInterface(declaration, commandInterfaceFq)
            val cmdReqFq = cmdReqClass?.fqNameWhenAvailable?.asString()
            if (cmdReqClass != null && cmdReqFq != null) {
                addNode(Node(id = cmdReqFq, name = cmdReqClass.nestedSimpleName(), fullName = cmdReqFq, type = NodeType.command))
                handlerToCommand[fqcn] = cmdReqFq
                requestKindByFq[cmdReqFq] = RequestKind.COMMAND
                addRel(Relationship(fromId = cmdReqFq, toId = fqcn, type = RelationshipType.CommandToCommandHandler))
            }
        } else if (implementsQuery) {
            addNode(Node(id = fqcn, name = classDisplayName, fullName = fqcn, type = NodeType.queryhandler))
            val qryReqClass = resolveRequestClassFromHandlerInterface(declaration, queryInterfaceFq)
            val qryReqFq = qryReqClass?.fqNameWhenAvailable?.asString()
            if (qryReqClass != null && qryReqFq != null) {
                addNode(Node(id = qryReqFq, name = qryReqClass.nestedSimpleName(), fullName = qryReqFq, type = NodeType.query))
                requestKindByFq[qryReqFq] = RequestKind.QUERY
                addRel(Relationship(fromId = qryReqFq, toId = fqcn, type = RelationshipType.QueryToQueryHandler))
            }
        } else if (implementsRequestHandler) {
            addNode(Node(id = fqcn, name = classDisplayName, fullName = fqcn, type = NodeType.clihandler))
            val cliReqClass = resolveRequestClassFromHandlerInterface(declaration, requestHandlerFq)
            val cliReqFq = cliReqClass?.fqNameWhenAvailable?.asString()
            if (cliReqClass != null && cliReqFq != null) {
                addNode(Node(id = cliReqFq, name = cliReqClass.nestedSimpleName(), fullName = cliReqFq, type = NodeType.cli))
                requestKindByFq[cliReqFq] = RequestKind.CLI
                addRel(Relationship(fromId = cliReqFq, toId = fqcn, type = RelationshipType.CliToCliHandler))
            }
        }

        return super.visitClassNew(declaration)
    }

    override fun visitFunctionNew(declaration: IrFunction): IrStatement {
        val parentClass = declaration.parent as? IrClass
        val methodName = declaration.name.asString()
        val parentFqcn = parentClass?.fqNameWhenAvailable?.asString()
        val methodId = if (parentFqcn != null) "$parentFqcn::$methodName" else methodName
        val methodDisplayName = buildMethodDisplayName(parentClass, methodName)

        val isControllerMethod = options.scanSpring &&
            parentClass != null &&
            parentClass.hasAnnotation(restController) &&
            declaration.annotations.any { ann ->
                val annFq = ann.symbol.owner.parentAsClass.fqNameWhenAvailable
                annFq != null && requestMappings.contains(annFq)
            }

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
            val eventType = eventTypeFq!!
            val eventNodeType = if (isDomainEventHandler) NodeType.domainevent else NodeType.integrationevent
            val eventDisplayName = eventClass?.nestedSimpleName() ?: typeDisplayNameForFqcn(eventType)
            addNode(Node(id = eventType, name = eventDisplayName, fullName = eventType, type = eventNodeType))
            val relType = if (isDomainEventHandler) RelationshipType.DomainEventToHandler else RelationshipType.IntegrationEventToHandler
            addRel(Relationship(fromId = eventType, toId = methodId, type = relType))
        }

        val aggInfo = parentFqcn?.let { index.aggregateInfoByClass[it] }
        if (aggInfo != null && aggInfo.type == AGG_TYPE_ENTITY) {
            addNode(Node(id = methodId, name = methodDisplayName, fullName = methodId, type = NodeType.entitymethod))
        }

        val ctx = when {
            isControllerMethod -> FunctionCtx.CONTROLLER_METHOD
            isDomainEventHandler -> FunctionCtx.DOMAIN_EVENT_HANDLER
            isIntegrationEventHandler -> FunctionCtx.INTEGRATION_EVENT_HANDLER
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
        var saveCalled = false
        var senderMethodAdded = false
        var validatorNodeAdded = false

        declaration.body?.acceptVoid(object : IrVisitorVoid() {
            override fun visitElement(element: IrElement) {
                element.acceptChildrenVoid(this)
            }

            override fun visitCall(expression: IrCall) {
                val calleeName = expression.symbol.owner.name.asString()
                val ownerClass = expression.symbol.owner.parent as? IrClass
                val receiverClass = expression.dispatchReceiverClass()
                val isRequestSupervisor = receiverClass?.isOrImplements(requestSupervisorFq) == true ||
                    ownerClass?.isOrImplements(requestSupervisorFq) == true
                val isAggregateFactorySupervisor = receiverClass?.isOrImplements(aggregateFactorySupervisorFq) == true ||
                    ownerClass?.isOrImplements(aggregateFactorySupervisorFq) == true
                val isUnitOfWork = receiverClass?.isOrImplements(unitOfWorkFq) == true ||
                    ownerClass?.isOrImplements(unitOfWorkFq) == true ||
                    isUnitOfWorkDefaultCall(expression, unitOfWorkFq)
                val isRepositorySupervisor = receiverClass?.isOrImplements(repositorySupervisorFq) == true ||
                    ownerClass?.isOrImplements(repositorySupervisorFq) == true

                if (calleeName == "send" && isRequestSupervisor) {
                    val requestClass = resolveRequestClassFromExpression(expression.valueArgumentOrNull(0))
                    val requestFq = requestClass?.fqNameWhenAvailable?.asString()
                    if (requestClass != null && requestFq != null) {
                        val requestKind = classifyRequestKind(requestClass)
                        val nodeType = when (requestKind) {
                            RequestKind.COMMAND -> NodeType.command
                            RequestKind.QUERY -> NodeType.query
                            RequestKind.CLI -> NodeType.cli
                        }
                        val requestDisplayName = requestClass.nestedSimpleName()
                        addNode(Node(id = requestFq, name = requestDisplayName, fullName = requestFq, type = nodeType))

                        val ctx = functionContext.lastOrNull()
                        val senderId = methodId
                        val controllerRoots = if (ctx == FunctionCtx.OTHER) controllerRootsByMethod[senderId].orEmpty() else emptySet()
                        if (controllerRoots.isNotEmpty()) {
                            val relType = relationshipTypeForSend(requestKind, FunctionCtx.CONTROLLER_METHOD)
                            controllerRoots.forEach { rootId ->
                                addRel(Relationship(fromId = rootId, toId = requestFq, type = relType))
                            }
                            super.visitCall(expression)
                            return
                        }

                        val relType = relationshipTypeForSend(requestKind, ctx)
                        if (ctx == FunctionCtx.VALIDATOR && !validatorNodeAdded) {
                            addNode(Node(id = senderId, name = methodDisplayName, fullName = senderId, type = NodeType.validator))
                            validatorNodeAdded = true
                        }
                        if (relType.isSenderMethodRel() && !senderMethodAdded && ctx == FunctionCtx.OTHER) {
                            val senderType = senderNodeTypeForRel(relType)
                            addNode(Node(id = senderId, name = methodDisplayName, fullName = senderId, type = senderType))
                            senderMethodAdded = true
                        }
                        addRel(Relationship(fromId = senderId, toId = requestFq, type = relType))
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

                if ((calleeName == "save" || calleeName == "save\$default") && isUnitOfWork) {
                    saveCalled = true
                }

                if (options.includeRepoUow && calleeName == "remove" && isUnitOfWork) {
                    val aggRootFq = resolveAggregateRootFromExpression(expression.valueArgumentOrNull(0))
                    if (aggRootFq != null) removedAggregates.add(aggRootFq)
                }

                if (options.includeRepoUow && calleeName == "remove" && isRepositorySupervisor) {
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
                    val targetClass = expression.symbol.owner.parent as? IrClass
                    if (targetClass != null) {
                        val targetFq = targetClass.fqNameWhenAvailable?.asString()
                        val targetAggInfo = targetClass.aggregateInfo()
                        if (targetAggInfo != null && targetAggInfo.type == AGG_TYPE_ENTITY) {
                            val aggRootFq = if (targetAggInfo.root) {
                                targetFq
                            } else {
                                targetAggInfo.aggregateName
                                    .takeIf { it.isNotEmpty() }
                                    ?.let { aggregateRootsByName[it] }
                                    ?: targetFq
                            }
                            val calleeId = "$targetFq::${expression.symbol.owner.name.asString()}"
                            val calleeName = buildMethodDisplayName(targetClass, expression.symbol.owner.name.asString())
                            addNode(Node(id = calleeId, name = calleeName, fullName = calleeId, type = NodeType.entitymethod))
                            if (handlerId != null) {
                                addRel(Relationship(fromId = handlerId, toId = calleeId, type = RelationshipType.CommandHandlerToEntityMethod))
                            }
                            if (aggRootFq != null) {
                                addNode(Node(id = aggRootFq, name = typeDisplayNameForFqcn(aggRootFq), fullName = aggRootFq, type = NodeType.aggregate))
                                addRel(Relationship(fromId = aggRootFq, toId = calleeId, type = RelationshipType.AggregateToEntityMethod))
                            }
                        }
                    }
                }

                super.visitCall(expression)
            }

            override fun visitConstructorCall(expression: IrConstructorCall) {
                val evtClass = expression.symbol.owner.parentAsClass
                val evtFq = evtClass.fqNameWhenAvailable?.asString()
                if (evtFq != null && isDomainEventClass(evtClass)) {
                    addNode(Node(id = evtFq, name = evtClass.nestedSimpleName(), fullName = evtFq, type = NodeType.domainevent))
                    addRel(Relationship(fromId = methodId, toId = evtFq, type = RelationshipType.EntityMethodToDomainEvent))
                }
                super.visitConstructorCall(expression)
            }
        })

        if (saveCalled && handlerIdForFunction != null) {
            createdAggregates.forEach { aggFq ->
                val methodName = pickLifecycleMethod(aggFq, "onCreate", "onCreate")
                val methodId = "$aggFq::$methodName"
                val displayName = buildMethodDisplayNameFromFqcn(aggFq, methodName)
                addNode(Node(id = methodId, name = displayName, fullName = methodId, type = NodeType.entitymethod))
                addRel(Relationship(fromId = handlerIdForFunction, toId = methodId, type = RelationshipType.CommandHandlerToEntityMethod))
                addRel(Relationship(fromId = aggFq, toId = methodId, type = RelationshipType.AggregateToEntityMethod))
            }
            removedAggregates.forEach { aggFq ->
                val methodName = pickLifecycleMethod(aggFq, "onDelete", "onRemove")
                val methodId = "$aggFq::$methodName"
                val displayName = buildMethodDisplayNameFromFqcn(aggFq, methodName)
                addNode(Node(id = methodId, name = displayName, fullName = methodId, type = NodeType.entitymethod))
                addRel(Relationship(fromId = handlerIdForFunction, toId = methodId, type = RelationshipType.CommandHandlerToEntityMethod))
                addRel(Relationship(fromId = aggFq, toId = methodId, type = RelationshipType.AggregateToEntityMethod))
            }
        }

        if (handlerIdForFunction != null && handlerContext.isNotEmpty()) {
            handlerContext.removeLast()
        }
        functionContext.removeLastOrNull()

        return super.visitFunctionNew(declaration)
    }

    private fun resolveRequestClassFromHandlerInterface(declaration: IrClass, handlerFq: FqName): IrClass? {
        val requestType = resolveTypeArgumentInHierarchyFromClass(declaration, handlerFq, 0) ?: return null
        val simple = requestType as? IrSimpleType ?: return null
        return simple.classifier?.owner as? IrClass
    }

    private fun resolveRequestClassFromExpression(expression: IrExpression?): IrClass? {
        val unwrapped = expression?.unwrapExpression() ?: return null
        val ctorClass = (unwrapped as? IrConstructorCall)?.symbol?.owner?.parentAsClass
        if (ctorClass != null && ctorClass.isOrImplements(requestParamFq)) return ctorClass
        val type = unwrapped.type as? IrSimpleType ?: return null
        val cls = type.classifier?.owner as? IrClass ?: return null
        return if (cls.isOrImplements(requestParamFq)) cls else null
    }

    private fun classifyRequestKind(requestClass: IrClass): RequestKind {
        val fq = requestClass.fqNameWhenAvailable?.asString() ?: return RequestKind.COMMAND
        requestKindByFq[fq]?.let { return it }
        val parentClass = requestClass.parent as? IrClass
        val parentName = parentClass?.name?.asString().orEmpty()
        val parentFq = parentClass?.fqNameWhenAvailable?.asString().orEmpty()
        val kind = when {
            parentName.endsWith("Qry") || parentFq.contains(".queries.") -> RequestKind.QUERY
            parentName.endsWith("Cli") || parentFq.contains(".distributed.clients.") -> RequestKind.CLI
            parentName.endsWith("Cmd") || parentFq.contains(".commands.") -> RequestKind.COMMAND
            else -> RequestKind.COMMAND
        }
        requestKindByFq[fq] = kind
        return kind
    }

    private fun resolvePayloadClassFromExpression(expression: IrExpression?): IrClass? {
        val unwrapped = expression?.unwrapExpression() ?: return null
        val ctorClass = (unwrapped as? IrConstructorCall)?.symbol?.owner?.parentAsClass
        if (ctorClass != null) return ctorClass
        val type = unwrapped.type as? IrSimpleType ?: return null
        return type.classifier?.owner as? IrClass
    }

    private fun resolveAggregateRootFromPayload(payloadClass: IrClass): String? {
        val payloadFq = payloadClass.fqNameWhenAvailable?.asString() ?: return null
        val aggName = index.payloadToAggregateName[payloadFq]
            ?: payloadClass.readAggregateInfo(aggregateAnn)
                ?.takeIf { it.type == AGG_TYPE_FACTORY_PAYLOAD }
                ?.aggregateName
            ?: return null
        return aggregateRootsByName[aggName]
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
            aggregateRootsByName[info.aggregateName] ?: fq
        }
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
            fq == aggregatePredicateFq.asString() -> {
                val arg = simple.arguments.getOrNull(1) as? org.jetbrains.kotlin.ir.types.IrTypeProjection
                arg?.type?.let { resolveAggregateRootFromType(it) }
            }
            cls.isOrImplements(predicateFq) -> {
                val directArg = (simple.arguments.getOrNull(0) as? org.jetbrains.kotlin.ir.types.IrTypeProjection)?.type
                val superArg = cls.findSuperTypeArgument(predicateFq, 0)
                (directArg ?: superArg)?.let { resolveAggregateRootFromType(it) }
            }
            cls.isOrImplements(aggregatePredicateFq) -> {
                val directArg = (simple.arguments.getOrNull(1) as? org.jetbrains.kotlin.ir.types.IrTypeProjection)?.type
                val superArg = cls.findSuperTypeArgument(aggregatePredicateFq, 1)
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

    private fun pickLifecycleMethod(aggFq: String, primary: String, fallback: String): String {
        val methods = index.entityMethodNamesByClass[aggFq].orEmpty()
        return when {
            methods.contains(primary) -> primary
            methods.contains(fallback) -> fallback
            else -> primary
        }
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

    private fun addRel(rel: Relationship) {
        rels.add(rel)
    }

    fun nodesAsSequence(): Sequence<Node> = nodes.values.asSequence()
    fun relsAsSequence(): Sequence<Relationship> = rels.asSequence()

    private fun IrClass.aggregateInfo(): AggregateInfo? {
        val fq = fqNameWhenAvailable?.asString() ?: return null
        return aggregateInfoCache.getOrPut(fq) {
            index.aggregateInfoByClass[fq] ?: readAggregateInfo(aggregateAnn)
        }
    }
}

private enum class FunctionCtx {
    CONTROLLER_METHOD,
    DOMAIN_EVENT_HANDLER,
    INTEGRATION_EVENT_HANDLER,
    VALIDATOR,
    OTHER
}

private enum class RequestKind {
    COMMAND,
    QUERY,
    CLI
}

private fun relationshipTypeForSend(kind: RequestKind, ctx: FunctionCtx?): RelationshipType {
    return when (kind) {
        RequestKind.COMMAND -> when (ctx) {
            FunctionCtx.CONTROLLER_METHOD -> RelationshipType.ControllerMethodToCommand
            FunctionCtx.DOMAIN_EVENT_HANDLER -> RelationshipType.DomainEventHandlerToCommand
            FunctionCtx.INTEGRATION_EVENT_HANDLER -> RelationshipType.IntegrationEventHandlerToCommand
            else -> RelationshipType.CommandSenderMethodToCommand
        }
        RequestKind.QUERY -> when (ctx) {
            FunctionCtx.CONTROLLER_METHOD -> RelationshipType.ControllerMethodToQuery
            FunctionCtx.DOMAIN_EVENT_HANDLER -> RelationshipType.DomainEventHandlerToQuery
            FunctionCtx.INTEGRATION_EVENT_HANDLER -> RelationshipType.IntegrationEventHandlerToQuery
            FunctionCtx.VALIDATOR -> RelationshipType.ValidatorToQuery
            else -> RelationshipType.QuerySenderMethodToQuery
        }
        RequestKind.CLI -> when (ctx) {
            FunctionCtx.CONTROLLER_METHOD -> RelationshipType.ControllerMethodToCli
            FunctionCtx.DOMAIN_EVENT_HANDLER -> RelationshipType.DomainEventHandlerToCli
            FunctionCtx.INTEGRATION_EVENT_HANDLER -> RelationshipType.IntegrationEventHandlerToCli
            else -> RelationshipType.CliSenderMethodToCli
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
        RelationshipType.CommandSenderMethodToCommand -> NodeType.commandsendermethod
        RelationshipType.QuerySenderMethodToQuery -> NodeType.querysendermethod
        RelationshipType.CliSenderMethodToCli -> NodeType.clisendermethod
        else -> NodeType.commandsendermethod
    }
}

private fun RelationshipType.isSenderMethodRel(): Boolean {
    return this == RelationshipType.CommandSenderMethodToCommand ||
        this == RelationshipType.QuerySenderMethodToQuery ||
        this == RelationshipType.CliSenderMethodToCli
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

private fun isUnitOfWorkDefaultCall(expression: IrCall, unitOfWorkFq: FqName): Boolean {
    if (expression.symbol.owner.name.asString() != "save\$default") return false
    val receiverArg = expression.valueArgumentOrNull(0) ?: return false
    val type = receiverArg.type as? IrSimpleType ?: return false
    val cls = type.classifier?.owner as? IrClass ?: return false
    return cls.isOrImplements(unitOfWorkFq)
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

private fun IrClass.readAggregateInfo(aggregateAnn: FqName): AggregateInfo? {
    val ann = annotations.firstOrNull { it.symbol.owner.parentAsClass.fqNameWhenAvailable == aggregateAnn }
        ?: return null
    val aggregateName = ann.getStringArg("aggregate") ?: ""
    val type = ann.getStringArg("type") ?: ""
    val root = ann.getBooleanArg("root") ?: false
    val resolvedName = if (aggregateName.isNotEmpty()) aggregateName else name.asString()
    return AggregateInfo(resolvedName, type, root)
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
private const val AGG_TYPE_FACTORY_PAYLOAD = "factory-payload"
private const val AGG_TYPE_DOMAIN_EVENT = "domain-event"

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
            append("\"type\":\"").append(n.type.name).append("\"}")
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
