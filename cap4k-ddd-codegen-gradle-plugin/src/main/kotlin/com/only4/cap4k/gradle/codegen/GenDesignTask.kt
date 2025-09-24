package com.only4.cap4k.gradle.codegen

import com.only4.cap4k.gradle.codegen.misc.NamingUtils
import com.only4.cap4k.gradle.codegen.misc.TextUtils
import com.only4.cap4k.gradle.codegen.template.PathNode
import com.only4.cap4k.gradle.codegen.template.TemplateNode
import org.gradle.api.provider.Property
import org.gradle.api.tasks.Input
import org.gradle.api.tasks.TaskAction
import java.io.File
import java.util.regex.Pattern

/**
 * 生成设计元素任务
 */
open class GenDesignTask : GenArchTask() {

    @get:Input
    override val extension: Property<Cap4kCodegenExtension> =
        project.objects.property(Cap4kCodegenExtension::class.java)

    @get:Input
    override val projectName: Property<String> = project.objects.property(String::class.java)

    @get:Input
    override val projectGroup: Property<String> = project.objects.property(String::class.java)

    @get:Input
    override val projectVersion: Property<String> = project.objects.property(String::class.java)

    @get:Input
    override val projectDir: Property<String> = project.objects.property(String::class.java)

    @TaskAction
    override fun generate() {
        renderFileSwitch = false
        super.generate()
    }

    private var renderFileSwitch = true

    /**
     * 设计元素别名映射
     */
    fun alias4Design(name: String): String {
        return when (name.lowercase()) {
            "commands", "command", "cmd" -> "command"
            "saga" -> "saga"
            "queries", "query", "qry" -> "query"
            "clients", "client", "cli" -> "client"
            "integration_events", "integration_event", "events", "event", "evt", "i_e", "ie" -> "integration_event"
            "integration_event_handlers", "integration_event_handler", "event_handlers", "event_handler",
            "evt_hdl", "i_e_h", "ieh", "integration_event_subscribers", "integration_event_subscriber",
            "event_subscribers", "event_subscriber", "evt_sub", "i_e_s", "ies",
                -> "integration_event_handler"

            "repositories", "repository", "repos", "repo" -> "repository"
            "factories", "factory", "fac" -> "factory"
            "specifications", "specification", "specs", "spec", "spe" -> "specification"
            "domain_events", "domain_event", "d_e", "de" -> "domain_event"
            "domain_event_handlers", "domain_event_handler", "d_e_h", "deh",
            "domain_event_subscribers", "domain_event_subscriber", "d_e_s", "des",
                -> "domain_event_handler"

            "domain_service", "service", "svc" -> "domain_service"
            else -> name
        }
    }

    /**
     * 解析字面量设计配置
     */
    fun resolveLiteralDesign(design: String): Map<String, Set<String>> {
        if (design.isBlank()) {
            return emptyMap()
        }

        return escape(design).replace(Regex("\\r\\n|\\r|\\n"), ";")
            .split(Regex(PATTERN_SPLITTER))
            .map { TextUtils.splitWithTrim(it, PATTERN_DESIGN_PARAMS_SPLITTER, 2) }
            .filter { it.size == 2 }
            .groupBy(
                { alias4Design(it[0]) },
                { it[1].trim() }
            )
            .mapValues { it.value.toSet() }
    }

    override fun renderTemplate(templateNodes: List<TemplateNode>, parentPath: String) {
        var design = ""
        val ext = getExtension()

        // 从extension获取设计配置
        val designValue = ext.design.orNull
        if (!designValue.isNullOrBlank()) {
            design += designValue
        }

        // 从设计文件读取配置
        val designFile = ext.designFile.orNull
        if (!designFile.isNullOrBlank() && File(designFile).exists()) {
            design += (";" + File(designFile).readText(charset(ext.archTemplateEncoding.get())))
        }

        val designMap = resolveLiteralDesign(design)

        templateNodes.forEach { templateNode ->
            when (alias4Design(templateNode.tag ?: "")) {
                "command" -> {
                    designMap["command"]?.forEach { literalDesign ->
                        if (templateNode.pattern.isNullOrBlank() || Pattern.compile(templateNode.pattern).asPredicate()
                                .test(literalDesign)
                        ) {
                            renderAppLayerCommand(literalDesign, parentPath, templateNode)
                        }
                    }
                }

                "saga" -> {
                    designMap["saga"]?.forEach { literalDesign ->
                        if (templateNode.pattern.isNullOrBlank() || Pattern.compile(templateNode.pattern).asPredicate()
                                .test(literalDesign)
                        ) {
                            renderAppLayerSaga(literalDesign, parentPath, templateNode)
                        }
                    }
                }

                "query", "query_handler" -> {
                    designMap["query"]?.forEach { literalDesign ->
                        if (templateNode.pattern.isNullOrBlank() || Pattern.compile(templateNode.pattern).asPredicate()
                                .test(literalDesign)
                        ) {
                            renderAppLayerQuery(literalDesign, parentPath, templateNode)
                        }
                    }
                }

                "client", "client_handler" -> {
                    designMap["client"]?.forEach { literalDesign ->
                        if (templateNode.pattern.isNullOrBlank() || Pattern.compile(templateNode.pattern).asPredicate()
                                .test(literalDesign)
                        ) {
                            renderAppLayerClient(literalDesign, parentPath, templateNode)
                        }
                    }
                }

                "integration_event" -> {
                    designMap["integration_event"]?.forEach { literalDesign ->
                        if (templateNode.pattern.isNullOrBlank() || Pattern.compile(templateNode.pattern).asPredicate()
                                .test(literalDesign)
                        ) {
                            renderAppLayerIntegrationEvent(
                                true,
                                "integration_event",
                                literalDesign,
                                parentPath,
                                templateNode
                            )
                        }
                    }
                    designMap["integration_event_handler"]?.forEach { literalDesign ->
                        if (templateNode.pattern.isNullOrBlank() || Pattern.compile(templateNode.pattern).asPredicate()
                                .test(literalDesign)
                        ) {
                            renderAppLayerIntegrationEvent(
                                false,
                                "integration_event",
                                literalDesign,
                                parentPath,
                                templateNode
                            )
                        }
                    }
                }

                "integration_event_handler" -> {
                    designMap["integration_event_handler"]?.forEach { literalDesign ->
                        if (templateNode.pattern.isNullOrBlank() || Pattern.compile(templateNode.pattern).asPredicate()
                                .test(literalDesign)
                        ) {
                            renderAppLayerIntegrationEvent(
                                false,
                                "integration_event_handler",
                                literalDesign,
                                parentPath,
                                templateNode
                            )
                        }
                    }
                }

                "domain_event" -> {
                    designMap["domain_event"]?.forEach { literalDesign ->
                        if (templateNode.pattern.isNullOrBlank() || Pattern.compile(templateNode.pattern).asPredicate()
                                .test(literalDesign)
                        ) {
                            renderDomainLayerDomainEvent(literalDesign, parentPath, templateNode)
                        }
                    }
                }

                "domain_event_handler" -> {
                    designMap["domain_event"]?.forEach { literalDesign ->
                        if (templateNode.pattern.isNullOrBlank() || Pattern.compile(templateNode.pattern).asPredicate()
                                .test(literalDesign)
                        ) {
                            renderDomainLayerDomainEvent(literalDesign, parentPath, templateNode)
                        }
                    }
                    designMap["domain_event_handler"]?.forEach { literalDesign ->
                        if (templateNode.pattern.isNullOrBlank() || Pattern.compile(templateNode.pattern).asPredicate()
                                .test(literalDesign)
                        ) {
                            renderDomainLayerDomainEvent(literalDesign, parentPath, templateNode)
                        }
                    }
                }

                "specification" -> {
                    designMap["specification"]?.forEach { literalDesign ->
                        if (templateNode.pattern.isNullOrBlank() || Pattern.compile(templateNode.pattern).asPredicate()
                                .test(literalDesign)
                        ) {
                            renderDomainLayerSpecification(literalDesign, parentPath, templateNode)
                        }
                    }
                }

                "factory" -> {
                    designMap["factory"]?.forEach { literalDesign ->
                        if (templateNode.pattern.isNullOrBlank() || Pattern.compile(templateNode.pattern).asPredicate()
                                .test(literalDesign)
                        ) {
                            renderDomainLayerAggregateFactory(literalDesign, parentPath, templateNode)
                        }
                    }
                }

                "domain_service" -> {
                    designMap["domain_service"]?.forEach { literalDesign ->
                        if (templateNode.pattern.isNullOrBlank() || Pattern.compile(templateNode.pattern).asPredicate()
                                .test(literalDesign)
                        ) {
                            renderDomainLayerDomainService(literalDesign, parentPath, templateNode)
                        }
                    }
                }

                else -> {
                    val tag = templateNode.tag ?: ""
                    designMap[tag]?.forEach { literalDesign ->
                        if (templateNode.pattern.isNullOrBlank() || Pattern.compile(templateNode.pattern).asPredicate()
                                .test(literalDesign)
                        ) {
                            renderGenericDesign(literalDesign, parentPath, templateNode)
                        }
                    }
                }
            }
        }
    }

    /**
     * 生成应用层命令
     */
    private fun renderAppLayerCommand(
        literalCommandDeclaration: String,
        parentPath: String,
        templateNode: TemplateNode,
    ) {
        logger.info("解析命令设计：$literalCommandDeclaration")
        val path = internalRenderGenericDesign(literalCommandDeclaration, parentPath, templateNode) { context ->
            var name = context["Name"] ?: ""
            if (!name.endsWith("Cmd") && !name.endsWith("Command")) {
                name += "Cmd"
            }
            putContext(templateNode.tag ?: "", "Name", name, context)
            putContext(templateNode.tag ?: "", "Command", context["Name"] ?: "", context)
            putContext(templateNode.tag ?: "", "Request", "${context["Command"]}Request", context)
            putContext(templateNode.tag ?: "", "Response", "${context["Command"]}Response", context)

            val comment = if (context.containsKey("Val1")) context["Val1"] ?: "" else "todo: 命令描述"
            putContext(templateNode.tag ?: "", "Comment", comment, context)
            putContext(
                templateNode.tag ?: "",
                "CommentEscaped",
                comment.replace(Regex(PATTERN_LINE_BREAK), " "),
                context
            )
            context
        }
        logger.info("生成命令代码：$path")
    }

    /**
     * 生成应用层Saga
     */
    private fun renderAppLayerSaga(literalSagaDeclaration: String, parentPath: String, templateNode: TemplateNode) {
        logger.info("解析Saga设计：$literalSagaDeclaration")
        val path = internalRenderGenericDesign(literalSagaDeclaration, parentPath, templateNode) { context ->
            var name = context["Name"] ?: ""
            if (!name.endsWith("Saga")) {
                name += "Saga"
            }
            putContext(templateNode.tag ?: "", "Name", name, context)
            putContext(templateNode.tag ?: "", "Saga", context["Name"] ?: "", context)
            putContext(templateNode.tag ?: "", "Request", "${context["Saga"]}Request", context)
            putContext(templateNode.tag ?: "", "Response", "${context["Saga"]}Response", context)

            val comment = if (context.containsKey("Val1")) context["Val1"] ?: "" else "todo: Saga描述"
            putContext(templateNode.tag ?: "", "Comment", comment, context)
            putContext(
                templateNode.tag ?: "",
                "CommentEscaped",
                comment.replace(Regex(PATTERN_LINE_BREAK), " "),
                context
            )
            context
        }
        logger.info("生成Saga代码：$path")
    }

    /**
     * 生成应用层查询
     */
    private fun renderAppLayerQuery(literalQueryDeclaration: String, parentPath: String, templateNode: TemplateNode) {
        logger.info("解析查询设计：$literalQueryDeclaration")
        val path = internalRenderGenericDesign(literalQueryDeclaration, parentPath, templateNode) { context ->
            var name = context["Name"] ?: ""
            if (!name.endsWith("Qry") && !name.endsWith("Query")) {
                name += "Qry"
            }
            putContext(templateNode.tag ?: "", "Name", name, context)
            putContext(templateNode.tag ?: "", "Query", context["Name"] ?: "", context)
            putContext(templateNode.tag ?: "", "Request", "${context["Query"]}Request", context)
            putContext(templateNode.tag ?: "", "Response", "${context["Query"]}Response", context)

            val comment = if (context.containsKey("Val1")) context["Val1"] ?: "" else "todo: 查询描述"
            putContext(templateNode.tag ?: "", "Comment", comment, context)
            putContext(
                templateNode.tag ?: "",
                "CommentEscaped",
                comment.replace(Regex(PATTERN_LINE_BREAK), " "),
                context
            )
            context
        }
        logger.info("生成查询代码：$path")
    }

    /**
     * 生成应用层客户端
     */
    private fun renderAppLayerClient(literalClientDeclaration: String, parentPath: String, templateNode: TemplateNode) {
        logger.info("解析防腐端设计：$literalClientDeclaration")
        val path = internalRenderGenericDesign(literalClientDeclaration, parentPath, templateNode) { context ->
            var name = context["Name"] ?: ""
            if (!name.endsWith("Cli") && !name.endsWith("Client")) {
                name += "Cli"
            }
            putContext(templateNode.tag ?: "", "Name", name, context)
            putContext(templateNode.tag ?: "", "Client", context["Name"] ?: "", context)
            putContext(templateNode.tag ?: "", "Request", "${context["Name"]}Request", context)
            putContext(templateNode.tag ?: "", "Response", "${context["Name"]}Response", context)

            val comment = if (context.containsKey("Val1")) context["Val1"] ?: "" else "todo: 防腐端描述"
            putContext(templateNode.tag ?: "", "Comment", comment, context)
            putContext(
                templateNode.tag ?: "",
                "CommentEscaped",
                comment.replace(Regex(PATTERN_LINE_BREAK), " "),
                context
            )
            context
        }
        logger.info("生成防腐端代码：$path")
    }

    /**
     * 生成应用层集成事件
     */
    private fun renderAppLayerIntegrationEvent(
        internal: Boolean,
        designType: String,
        literalIntegrationEventDeclaration: String,
        parentPath: String,
        templateNode: TemplateNode,
    ) {
        logger.info("解析集成事件设计：$literalIntegrationEventDeclaration")
        var finalParentPath = parentPath
        if (designType == "integration_event") {
            finalParentPath += File.separator + (if (internal) "" else "external")
        }

        val path =
            internalRenderGenericDesign(literalIntegrationEventDeclaration, finalParentPath, templateNode) { context ->
                putContext(templateNode.tag ?: "", "subPackage", if (internal) "" else ".external", context)
                var name = context["Name"] ?: ""
                if (!name.endsWith("Evt") && !name.endsWith("Event")) {
                    name += "IntegrationEvent"
                }
                putContext(templateNode.tag ?: "", "Name", name, context)
                putContext(templateNode.tag ?: "", "IntegrationEvent", context["Name"] ?: "", context)

                val mqTopic = if (context.containsKey("Val1")) {
                    val val1 = context["Val1"] ?: ""
                    if (val1.isBlank()) "\"${context["Val0"]}\"" else "\"$val1\""
                } else {
                    "\"${context["Val0"]}\""
                }
                putContext(templateNode.tag ?: "", "MQ_TOPIC", mqTopic, context)

                if (internal) {
                    putContext(templateNode.tag ?: "", "MQ_CONSUMER", "IntegrationEvent.NONE_SUBSCRIBER", context)
                    val comment = if (context.containsKey("Val2")) context["Val2"] ?: "" else "todo: 集成事件描述"
                    putContext(templateNode.tag ?: "", "Comment", comment, context)
                } else {
                    val mqConsumer = if (context.containsKey("Val2")) {
                        val val2 = context["Val2"] ?: ""
                        if (val2.isBlank()) "\"\${spring.application.name}\"" else "\"$val2\""
                    } else {
                        "\"\${spring.application.name}\""
                    }
                    putContext(templateNode.tag ?: "", "MQ_CONSUMER", mqConsumer, context)
                    val comment = if (context.containsKey("Val3")) context["Val3"] ?: "" else "todo: 集成事件描述"
                    putContext(templateNode.tag ?: "", "Comment", comment, context)
                }

                putContext(
                    templateNode.tag ?: "",
                    "CommentEscaped",
                    (context["Comment"] ?: "").replace(Regex(PATTERN_LINE_BREAK), " "),
                    context
                )
                context
            }
        logger.info("生成集成事件代码：$path")
    }

    /**
     * 生成领域层领域事件
     */
    private fun renderDomainLayerDomainEvent(
        literalDomainEventDeclaration: String,
        parentPath: String,
        templateNode: TemplateNode,
    ) {
        logger.info("解析领域事件设计：$literalDomainEventDeclaration")
        val path = internalRenderGenericDesign(literalDomainEventDeclaration, parentPath, templateNode) { context ->
            val relativePath = NamingUtils.parentPackageName(context["Val0"] ?: "")
                .replace(".", File.separator)
            if (relativePath.isNotBlank()) {
                putContext(templateNode.tag ?: "", "path", relativePath, context)
                putContext(
                    templateNode.tag ?: "",
                    "package",
                    if (relativePath.isEmpty()) "" else ".${relativePath.replace(File.separator, ".")}",
                    context
                )
            }

            if (!context.containsKey("Val1")) {
                throw RuntimeException("缺失领域事件名称，领域事件设计格式：AggregateRootEntityName:DomainEventName")
            }

            var name = NamingUtils.toUpperCamelCase(context["Val1"] ?: "") ?: ""
            if (!name.endsWith("Evt") && !name.endsWith("Event")) {
                name += "DomainEvent"
            }

            val entity = NamingUtils.toUpperCamelCase(
                NamingUtils.getLastPackageName(context["Val0"] ?: "")
            ) ?: ""

            val persist = context.containsKey("val2") &&
                    listOf("true", "persist", "1").contains(context["val2"])

            putContext(templateNode.tag ?: "", "Name", name, context)
            putContext(templateNode.tag ?: "", "DomainEvent", context["Name"] ?: "", context)
            putContext(templateNode.tag ?: "", "persist", if (persist) "true" else "false", context)
            putContext(templateNode.tag ?: "", "Aggregate", entity, context)
            putContext(templateNode.tag ?: "", "Entity", entity, context)
            putContext(templateNode.tag ?: "", "EntityVar", NamingUtils.toLowerCamelCase(entity) ?: "", context)
            putContext(templateNode.tag ?: "", "AggregateRoot", context["Entity"] ?: "", context)

            val comment = if (alias4Design(templateNode.tag ?: "") == "domain_event_handler") {
                if (context.containsKey("Val2")) context["Val2"] ?: "" else "todo: 领域事件订阅描述"
            } else {
                if (context.containsKey("Val2")) context["Val2"] ?: "" else "todo: 领域事件描述"
            }
            putContext(templateNode.tag ?: "", "Comment", comment, context)
            putContext(
                templateNode.tag ?: "",
                "CommentEscaped",
                comment.replace(Regex(PATTERN_LINE_BREAK), " "),
                context
            )
            context
        }
        logger.info("生成领域事件代码：$path")
    }

    /**
     * 生成领域层聚合工厂
     */
    private fun renderDomainLayerAggregateFactory(
        literalAggregateFactoryDeclaration: String,
        parentPath: String,
        templateNode: TemplateNode,
    ) {
        logger.info("解析聚合工厂设计：$literalAggregateFactoryDeclaration")
        val path =
            internalRenderGenericDesign(literalAggregateFactoryDeclaration, parentPath, templateNode) { context ->
                val entity = context["Name"] ?: ""
                val name = "${entity}Factory"
                putContext(templateNode.tag ?: "", "Name", name, context)
                putContext(templateNode.tag ?: "", "Factory", context["Name"] ?: "", context)
                putContext(templateNode.tag ?: "", "Aggregate", entity, context)
                putContext(templateNode.tag ?: "", "Entity", entity, context)
                putContext(templateNode.tag ?: "", "EntityVar", NamingUtils.toLowerCamelCase(entity) ?: "", context)
                putContext(templateNode.tag ?: "", "AggregateRoot", context["Entity"] ?: "", context)

                val comment = if (context.containsKey("Val1")) context["Val1"] ?: "" else "todo: 聚合工厂描述"
                putContext(templateNode.tag ?: "", "Comment", comment, context)
                putContext(
                    templateNode.tag ?: "",
                    "CommentEscaped",
                    comment.replace(Regex(PATTERN_LINE_BREAK), " "),
                    context
                )
                context
            }
        logger.info("生成聚合工厂代码：$path")
    }

    /**
     * 生成领域层规约
     */
    private fun renderDomainLayerSpecification(
        literalSpecificationDeclaration: String,
        parentPath: String,
        templateNode: TemplateNode,
    ) {
        logger.info("解析实体规约设计：$literalSpecificationDeclaration")
        val path = internalRenderGenericDesign(literalSpecificationDeclaration, parentPath, templateNode) { context ->
            val entity = context["Name"] ?: ""
            val name = "${entity}Specification"
            putContext(templateNode.tag ?: "", "Name", name, context)
            putContext(templateNode.tag ?: "", "Specification", context["Name"] ?: "", context)
            putContext(templateNode.tag ?: "", "Aggregate", entity, context)
            putContext(templateNode.tag ?: "", "Entity", entity, context)
            putContext(templateNode.tag ?: "", "EntityVar", NamingUtils.toLowerCamelCase(entity) ?: "", context)
            putContext(templateNode.tag ?: "", "AggregateRoot", context["Entity"] ?: "", context)

            val comment = if (context.containsKey("Val1")) context["Val1"] ?: "" else "todo: 实体规约描述"
            putContext(templateNode.tag ?: "", "Comment", comment, context)
            putContext(
                templateNode.tag ?: "",
                "CommentEscaped",
                comment.replace(Regex(PATTERN_LINE_BREAK), " "),
                context
            )
            context
        }
        logger.info("生成实体规约代码：$path")
    }

    /**
     * 生成领域层领域服务
     */
    private fun renderDomainLayerDomainService(
        literalDomainServiceDeclaration: String,
        parentPath: String,
        templateNode: TemplateNode,
    ) {
        logger.info("解析领域服务设计：$literalDomainServiceDeclaration")
        val path = internalRenderGenericDesign(literalDomainServiceDeclaration, parentPath, templateNode) { context ->
            val name = generateDomainServiceName(context["Name"] ?: "")
            putContext(templateNode.tag ?: "", "Name", name, context)
            putContext(templateNode.tag ?: "", "DomainService", context["Name"] ?: "", context)

            val comment = if (context.containsKey("Val1")) context["Val1"] ?: "" else "todo: 领域服务描述"
            putContext(templateNode.tag ?: "", "Comment", comment, context)
            putContext(
                templateNode.tag ?: "",
                "CommentEscaped",
                comment.replace(Regex(PATTERN_LINE_BREAK), " "),
                context
            )
            context
        }
        logger.info("生成领域服务代码：$path")
    }

    /**
     * 生成通用设计元素
     */
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
        val segments = TextUtils.splitWithTrim(escape(literalGenericDeclaration), PATTERN_DESIGN_PARAMS_SPLITTER)
        for (i in segments.indices) {
            segments[i] = unescape(segments[i])
        }

        val context = getEscapeContext().toMutableMap()
        segments.forEachIndexed { i, segment ->
            putContext(templateNode.tag ?: "", "Val$i", segment, context)
            putContext(templateNode.tag ?: "", "val$i", segment.lowercase(), context)
        }

        val name = segments[0].lowercase()
        val Name = NamingUtils.toUpperCamelCase(NamingUtils.getLastPackageName(segments[0])) ?: ""
        val path = NamingUtils.parentPackageName(segments[0]).replace(".", File.separator)

        putContext(templateNode.tag ?: "", "Name", Name, context)
        putContext(templateNode.tag ?: "", "name", name, context)
        putContext(templateNode.tag ?: "", "path", path, context)
        putContext(
            templateNode.tag ?: "",
            "package",
            if (path.isEmpty()) "" else ".${path.replace(File.separator, ".")}",
            context
        )

        val finalContext = contextBuilder?.invoke(context) ?: context
        val pathNode = templateNode.cloneTemplateNode().resolve(finalContext) as PathNode
        return forceRender(pathNode, parentPath)
    }
}
