package com.only4.cap4k.gradle.codegen

import com.only4.cap4k.gradle.codegen.misc.splitWithTrim
import com.only4.cap4k.gradle.codegen.misc.toLowerCamelCase
import com.only4.cap4k.gradle.codegen.misc.toUpperCamelCase
import com.only4.cap4k.gradle.codegen.template.PathNode
import com.only4.cap4k.gradle.codegen.template.TemplateNode
import org.gradle.api.tasks.TaskAction
import java.io.File

/**
 * 生成设计元素任务
 */
open class GenDesignTask : GenArchTask() {

    @TaskAction
    override fun generate() {
        renderFileSwitch = false
        super.generate()
    }

    // 预构建别名映射（全部小写）
    private companion object {
        val DESIGN_ALIAS: Map<String, String> = buildMap {
            // command
            put("commands", "command")
            put("command", "command")
            put("cmd", "command")
            // saga
            put("saga", "saga")
            // query
            put("queries", "query")
            put("query", "query")
            put("qry", "query")
            // client
            put("clients", "client")
            put("client", "client")
            put("cli", "client")
            // integration event
            putAll(
                listOf(
                    "integration_events", "integration_event", "events", "event",
                    "evt", "i_e", "ie"
                ).associateWith { "integration_event" }
            )
            // integration event handler / subscriber
            putAll(
                listOf(
                    "integration_event_handlers", "integration_event_handler",
                    "event_handlers", "event_handler", "evt_hdl", "i_e_h", "ieh",
                    "integration_event_subscribers", "integration_event_subscriber",
                    "event_subscribers", "event_subscriber", "evt_sub", "i_e_s", "ies"
                ).associateWith { "integration_event_handler" }
            )
            // repository
            putAll(
                listOf("repositories", "repository", "repos", "repo").associateWith { "repository" }
            )
            // factory
            putAll(
                listOf("factories", "factory", "fac").associateWith { "factory" }
            )
            // specification
            putAll(
                listOf("specifications", "specification", "specs", "spec", "spe").associateWith { "specification" }
            )
            // domain event
            putAll(
                listOf("domain_events", "domain_event", "d_e", "de").associateWith { "domain_event" }
            )
            // domain event handler / subscriber
            putAll(
                listOf(
                    "domain_event_handlers", "domain_event_handler", "d_e_h", "deh",
                    "domain_event_subscribers", "domain_event_subscriber", "d_e_s", "des"
                ).associateWith { "domain_event_handler" }
            )
            // domain service
            putAll(
                listOf("domain_service", "service", "svc").associateWith { "domain_service" }
            )
        }
    }

    /**
     * 设计元素别名映射
     */
    fun alias4Design(name: String): String = DESIGN_ALIAS[name.lowercase()] ?: name

    /**
     * 解析字面量设计配置
     */
    fun resolveLiteralDesign(design: String): Map<String, Set<String>> {
        if (design.isBlank()) return emptyMap()

        return escape(design)
            .replace("\\r\\n|\\r|\\n".toRegex(), ";")
            .split(PATTERN_SPLITTER.toRegex())
            .map { it.splitWithTrim(PATTERN_DESIGN_PARAMS_SPLITTER, 2) }
            .filter { it.size == 2 }
            .groupBy({ alias4Design(it[0]) }, { it[1].trim() })
            .mapValues { it.value.toSet() }
    }

    // 缓存编译后的 regex
    private val regexCache = mutableMapOf<String, Regex>()
    private fun TemplateNode.compiledRegex(): Regex? =
        pattern.takeIf { it.isNotBlank() }?.let { p -> regexCache.getOrPut(p) { p.toRegex() } }

    private inline fun forEachDesign(
        designMap: Map<String, Set<String>>,
        key: String,
        templateNode: TemplateNode,
        crossinline action: (String) -> Unit
    ) {
        designMap[key]?.forEach { literal ->
            if (patternMatches(templateNode, literal)) action(literal)
        }
    }

    private fun patternMatches(templateNode: TemplateNode, literal: String): Boolean =
        templateNode.compiledRegex()?.matches(literal) ?: true

    override fun renderTemplate(templateNodes: List<TemplateNode>, parentPath: String) {
        val ext = extension.get()

        // 收集设计字面量
        val designLiteral = buildString {
            if (!ext.designFiles.isEmpty) {
                ext.designFiles.files.forEach { file ->
                    if (file.exists()) {
                        if (isNotEmpty()) append(";")
                        append(file.readText(charset(ext.archTemplateEncoding.get())))
                    }
                }
            }
        }

        val designMap = resolveLiteralDesign(designLiteral)

        templateNodes.forEach { templateNode ->
            when (alias4Design(templateNode.tag.orEmpty())) {
                "command" -> forEachDesign(designMap, "command", templateNode) {
                    renderAppLayerCommand(it, parentPath, templateNode)
                }

                "saga" -> forEachDesign(designMap, "saga", templateNode) {
                    renderAppLayerSaga(it, parentPath, templateNode)
                }

                "query", "query_handler" -> forEachDesign(designMap, "query", templateNode) {
                    renderAppLayerQuery(it, parentPath, templateNode)
                }

                "client", "client_handler" -> forEachDesign(designMap, "client", templateNode) {
                    renderAppLayerClient(it, parentPath, templateNode)
                }

                "integration_event" -> {
                    // 事件本体
                    forEachDesign(designMap, "integration_event", templateNode) {
                        renderAppLayerIntegrationEvent(true, "integration_event", it, parentPath, templateNode)
                    }
                    // 事件(发送/订阅)处理
                    forEachDesign(designMap, "integration_event_handler", templateNode) {
                        renderAppLayerIntegrationEvent(false, "integration_event", it, parentPath, templateNode)
                    }
                }

                "integration_event_handler" -> forEachDesign(designMap, "integration_event_handler", templateNode) {
                    renderAppLayerIntegrationEvent(false, "integration_event_handler", it, parentPath, templateNode)
                }

                "domain_event" -> forEachDesign(designMap, "domain_event", templateNode) {
                    renderDomainLayerDomainEvent(it, parentPath, templateNode)
                }

                "domain_event_handler" -> {
                    // 领域事件声明
                    forEachDesign(designMap, "domain_event", templateNode) {
                        renderDomainLayerDomainEvent(it, parentPath, templateNode)
                    }
                    // 领域事件订阅
                    forEachDesign(designMap, "domain_event_handler", templateNode) {
                        renderDomainLayerDomainEvent(it, parentPath, templateNode)
                    }
                }

                "specification" -> forEachDesign(designMap, "specification", templateNode) {
                    renderDomainLayerSpecification(it, parentPath, templateNode)
                }

                "factory" -> forEachDesign(designMap, "factory", templateNode) {
                    renderDomainLayerAggregateFactory(it, parentPath, templateNode)
                }

                "domain_service" -> forEachDesign(designMap, "domain_service", templateNode) {
                    renderDomainLayerDomainService(it, parentPath, templateNode)
                }

                else -> {
                    val tag = templateNode.tag.orEmpty()
                    forEachDesign(designMap, tag, templateNode) {
                        renderGenericDesign(it, parentPath, templateNode)
                    }
                }
            }
        }
    }

    private fun renderAppLayerCommand(
        literalCommandDeclaration: String,
        parentPath: String,
        templateNode: TemplateNode,
    ) {
        logger.info("解析命令设计：$literalCommandDeclaration")
        val path = internalRenderGenericDesign(literalCommandDeclaration, parentPath, templateNode) { context ->
            var name = context["Name"].orEmpty()
            if (!name.endsWith("Cmd") && !name.endsWith("Command")) {
                name += "Cmd"
            }
            val tag = templateNode.tag.orEmpty()
            putContext(tag, "Name", name, context)
            putContext(tag, "Command", context["Name"].orEmpty(), context)
            putContext(tag, "Request", "${context["Command"]}Request", context)
            putContext(tag, "Response", "${context["Command"]}Response", context)

            val comment = context["Val1"] ?: "todo: 命令描述"
            putContext(tag, "Comment", comment, context)
            putContext(
                tag,
                "CommentEscaped",
                comment.replace(PATTERN_LINE_BREAK.toRegex(), " "),
                context
            )
            context
        }
        logger.info("生成命令代码：$path")
    }

    private fun renderAppLayerSaga(literalSagaDeclaration: String, parentPath: String, templateNode: TemplateNode) {
        logger.info("解析Saga设计：$literalSagaDeclaration")
        val path = internalRenderGenericDesign(literalSagaDeclaration, parentPath, templateNode) { context ->
            var name = context["Name"].orEmpty()
            if (!name.endsWith("Saga")) {
                name += "Saga"
            }
            val tag = templateNode.tag.orEmpty()
            putContext(tag, "Name", name, context)
            putContext(tag, "Saga", context["Name"].orEmpty(), context)
            putContext(tag, "Request", "${context["Saga"]}Request", context)
            putContext(tag, "Response", "${context["Saga"]}Response", context)

            val comment = context["Val1"] ?: "todo: Saga描述"
            putContext(tag, "Comment", comment, context)
            putContext(
                tag,
                "CommentEscaped",
                comment.replace(PATTERN_LINE_BREAK.toRegex(), " "),
                context
            )
            context
        }
        logger.info("生成Saga代码：$path")
    }

    private fun renderAppLayerQuery(literalQueryDeclaration: String, parentPath: String, templateNode: TemplateNode) {
        logger.info("解析查询设计：$literalQueryDeclaration")
        val path = internalRenderGenericDesign(literalQueryDeclaration, parentPath, templateNode) { context ->
            var name = context["Name"].orEmpty()
            if (!name.endsWith("Qry") && !name.endsWith("Query")) {
                name += "Qry"
            }
            val tag = templateNode.tag.orEmpty()
            putContext(tag, "Name", name, context)
            putContext(tag, "Query", context["Name"].orEmpty(), context)
            putContext(tag, "Request", "${context["Query"]}Request", context)
            putContext(tag, "Response", "${context["Query"]}Response", context)

            val comment = context["Val1"] ?: "todo: 查询描述"
            putContext(tag, "Comment", comment, context)
            putContext(
                tag,
                "CommentEscaped",
                comment.replace(PATTERN_LINE_BREAK.toRegex(), " "),
                context
            )
            context
        }
        logger.info("生成查询代码：$path")
    }

    private fun renderAppLayerClient(literalClientDeclaration: String, parentPath: String, templateNode: TemplateNode) {
        logger.info("解析防腐端设计：$literalClientDeclaration")
        val path = internalRenderGenericDesign(literalClientDeclaration, parentPath, templateNode) { context ->
            var name = context["Name"].orEmpty()
            if (!name.endsWith("Cli") && !name.endsWith("Client")) {
                name += "Cli"
            }
            val tag = templateNode.tag.orEmpty()
            putContext(tag, "Name", name, context)
            putContext(tag, "Client", context["Name"].orEmpty(), context)
            putContext(tag, "Request", "${context["Name"]}Request", context)
            putContext(tag, "Response", "${context["Name"]}Response", context)

            val comment = context["Val1"] ?: "todo: 防腐端描述"
            putContext(tag, "Comment", comment, context)
            putContext(
                tag,
                "CommentEscaped",
                comment.replace(PATTERN_LINE_BREAK.toRegex(), " "),
                context
            )
            context
        }
        logger.info("生成防腐端代码：$path")
    }

    private fun renderAppLayerIntegrationEvent(
        internal: Boolean,
        designType: String,
        literalIntegrationEventDeclaration: String,
        parentPath: String,
        templateNode: TemplateNode,
    ) {
        logger.info("解析集成事件设计：$literalIntegrationEventDeclaration")
        val finalParentPath = if (designType == "integration_event") {
            parentPath + File.separator + (if (internal) "" else "external")
        } else {
            parentPath
        }

        val path =
            internalRenderGenericDesign(literalIntegrationEventDeclaration, finalParentPath, templateNode) { context ->
                val tag = templateNode.tag.orEmpty()
                putContext(tag, "subPackage", if (internal) "" else ".external", context)
                var name = context["Name"].orEmpty()
                if (!name.endsWith("Evt") && !name.endsWith("Event")) {
                    name += "IntegrationEvent"
                }
                putContext(tag, "Name", name, context)
                putContext(tag, "IntegrationEvent", context["Name"].orEmpty(), context)

                val mqTopic = context["Val1"]?.takeIf { it.isNotBlank() }
                    ?: context["Val0"] ?: ""
                putContext(tag, "MQ_TOPIC", "\"$mqTopic\"", context)

                if (internal) {
                    putContext(tag, "MQ_CONSUMER", "IntegrationEvent.NONE_SUBSCRIBER", context)
                    val comment = context["Val2"] ?: "todo: 集成事件描述"
                    putContext(tag, "Comment", comment, context)
                } else {
                    val mqConsumer = context["Val2"]?.takeIf { it.isNotBlank() } ?: "\$spring.application.name"
                    putContext(tag, "MQ_CONSUMER", "\"$mqConsumer\"", context)
                    val comment = context["Val3"] ?: "todo: 集成事件描述"
                    putContext(tag, "Comment", comment, context)
                }

                putContext(
                    tag,
                    "CommentEscaped",
                    context["Comment"].orEmpty().replace(PATTERN_LINE_BREAK.toRegex(), " "),
                    context
                )
                context
            }
        logger.info("生成集成事件代码：$path")
    }

    private fun renderDomainLayerDomainEvent(
        literalDomainEventDeclaration: String,
        parentPath: String,
        templateNode: TemplateNode,
    ) {
        logger.info("解析领域事件设计：$literalDomainEventDeclaration")
        val path = internalRenderGenericDesign(literalDomainEventDeclaration, parentPath, templateNode) { context ->
            val tag = templateNode.tag.orEmpty()
            val relativePath = context["Val0"].orEmpty().substringBeforeLast(".")
                .replace(".", File.separator)
            if (relativePath.isNotBlank()) {
                putContext(tag, "path", relativePath, context)
                putContext(
                    tag,
                    "package",
                    if (relativePath.isEmpty()) "" else ".${relativePath.replace(File.separator, ".")}",
                    context
                )
            }

            if (!context.containsKey("Val1")) {
                throw RuntimeException("缺失领域事件名称，领域事件设计格式：AggregateRootEntityName:DomainEventName")
            }

            var name = toUpperCamelCase(context["Val1"].orEmpty()).orEmpty()
            if (!name.endsWith("Evt") && !name.endsWith("Event")) {
                name += "DomainEvent"
            }

            val entity = toUpperCamelCase(
                context["Val0"].orEmpty().substringAfter(".")
            ).orEmpty()

            val persist = context["val2"] in listOf("true", "persist", "1")

            putContext(tag, "Name", name, context)
            putContext(tag, "DomainEvent", context["Name"].orEmpty(), context)
            putContext(tag, "persist", persist.toString(), context)
            putContext(tag, "Aggregate", entity, context)
            putContext(tag, "Entity", entity, context)
            putContext(tag, "EntityVar", toLowerCamelCase(entity).orEmpty(), context)
            putContext(tag, "AggregateRoot", context["Entity"].orEmpty(), context)

            val comment = if (alias4Design(tag) == "domain_event_handler") {
                context["Val2"] ?: "todo: 领域事件订阅描述"
            } else {
                context["Val2"] ?: "todo: 领域事件描述"
            }
            putContext(tag, "Comment", comment, context)
            putContext(
                tag,
                "CommentEscaped",
                comment.replace(PATTERN_LINE_BREAK.toRegex(), " "),
                context
            )
            context
        }
        logger.info("生成领域事件代码：$path")
    }

    private fun renderDomainLayerAggregateFactory(
        literalAggregateFactoryDeclaration: String,
        parentPath: String,
        templateNode: TemplateNode,
    ) {
        logger.info("解析聚合工厂设计：$literalAggregateFactoryDeclaration")
        val path =
            internalRenderGenericDesign(literalAggregateFactoryDeclaration, parentPath, templateNode) { context ->
                val entity = context["Name"].orEmpty()
                val name = "${entity}Factory"
                val tag = templateNode.tag.orEmpty()

                putContext(tag, "Name", name, context)
                putContext(tag, "Factory", context["Name"].orEmpty(), context)
                putContext(tag, "Aggregate", entity, context)
                putContext(tag, "Entity", entity, context)
                putContext(tag, "EntityVar", toLowerCamelCase(entity).orEmpty(), context)
                putContext(tag, "AggregateRoot", context["Entity"].orEmpty(), context)

                val comment = context["Val1"] ?: "todo: 聚合工厂描述"
                putContext(tag, "Comment", comment, context)
                putContext(
                    tag,
                    "CommentEscaped",
                    comment.replace(PATTERN_LINE_BREAK.toRegex(), " "),
                    context
                )
                context
            }
        logger.info("生成聚合工厂代码：$path")
    }

    private fun renderDomainLayerSpecification(
        literalSpecificationDeclaration: String,
        parentPath: String,
        templateNode: TemplateNode,
    ) {
        logger.info("解析实体规约设计：$literalSpecificationDeclaration")
        val path = internalRenderGenericDesign(literalSpecificationDeclaration, parentPath, templateNode) { context ->
            val entity = context["Name"].orEmpty()
            val name = "${entity}Specification"
            val tag = templateNode.tag.orEmpty()

            putContext(tag, "Name", name, context)
            putContext(tag, "Specification", context["Name"].orEmpty(), context)
            putContext(tag, "Aggregate", entity, context)
            putContext(tag, "Entity", entity, context)
            putContext(tag, "EntityVar", toLowerCamelCase(entity).orEmpty(), context)
            putContext(tag, "AggregateRoot", context["Entity"].orEmpty(), context)

            val comment = context["Val1"] ?: "todo: 实体规约描述"
            putContext(tag, "Comment", comment, context)
            putContext(
                tag,
                "CommentEscaped",
                comment.replace(PATTERN_LINE_BREAK.toRegex(), " "),
                context
            )
            context
        }
        logger.info("生成实体规约代码：$path")
    }

    private fun renderDomainLayerDomainService(
        literalDomainServiceDeclaration: String,
        parentPath: String,
        templateNode: TemplateNode,
    ) {
        logger.info("解析领域服务设计：$literalDomainServiceDeclaration")
        val path = internalRenderGenericDesign(literalDomainServiceDeclaration, parentPath, templateNode) { context ->
            val name = generateDomainServiceName(context["Name"].orEmpty())
            val tag = templateNode.tag.orEmpty()

            putContext(tag, "Name", name, context)
            putContext(tag, "DomainService", context["Name"].orEmpty(), context)

            val comment = context["Val1"] ?: "todo: 领域服务描述"
            putContext(tag, "Comment", comment, context)
            putContext(
                tag,
                "CommentEscaped",
                comment.replace(PATTERN_LINE_BREAK.toRegex(), " "),
                context
            )
            context
        }
        logger.info("生成领域服务代码：$path")
    }

    private fun renderGenericDesign(literalGenericDeclaration: String, parentPath: String, templateNode: TemplateNode) {
        logger.info("解析自定义元素设计：$literalGenericDeclaration")
        val path = internalRenderGenericDesign(literalGenericDeclaration, parentPath, templateNode, null)
        logger.info("生成自定义元素代码：$path")
    }

    /**
     * 通用设计元素渲染逻辑
     */
    private fun internalRenderGenericDesign(
        literalGenericDeclaration: String,
        parentPath: String,
        templateNode: TemplateNode,
        contextBuilder: ((MutableMap<String, String>) -> MutableMap<String, String>)?,
    ): String {
        val segments = escape(literalGenericDeclaration)
            .splitWithTrim(PATTERN_DESIGN_PARAMS_SPLITTER)
            .map { unescape(it) }

        val context = getEscapeContext().toMutableMap()
        val tag = templateNode.tag.orEmpty()

        segments.forEachIndexed { i, segment ->
            putContext(tag, "Val$i", segment, context)
            putContext(tag, "val$i", segment.lowercase(), context)
        }

        val name = segments[0].lowercase()
        val Name = toUpperCamelCase(segments[0].substringAfter(".")).orEmpty()
        val path = segments[0].substringBeforeLast(".").replace(".", File.separator)

        putContext(tag, "Name", Name, context)
        putContext(tag, "name", name, context)
        putContext(tag, "path", path, context)
        putContext(
            tag,
            "package",
            if (path.isEmpty()) "" else ".${path.replace(File.separator, ".")}",
            context
        )

        val finalContext = contextBuilder?.invoke(context) ?: context
        val pathNode = templateNode.deepCopy().resolve(finalContext) as PathNode
        return forceRender(pathNode, parentPath)
    }
}
