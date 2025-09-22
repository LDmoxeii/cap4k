package com.only4.cap4k.gradle.codegen

import com.alibaba.fastjson.JSON
import com.only4.cap4k.gradle.codegen.misc.NamingUtils
import com.only4.cap4k.gradle.codegen.misc.SourceFileUtils
import com.only4.cap4k.gradle.codegen.misc.SqlSchemaUtils
import com.only4.cap4k.gradle.codegen.template.PathNode
import com.only4.cap4k.gradle.codegen.template.Template
import com.only4.cap4k.gradle.codegen.template.TemplateNode
import org.gradle.api.DefaultTask
import org.gradle.api.provider.Property
import org.gradle.api.tasks.Input
import org.gradle.api.tasks.Internal
import java.io.File
import java.time.LocalDateTime
import java.time.format.DateTimeFormatter

/**
 * 代码生成任务抽象基类
 *
 * @author cap4k-codegen
 * @date 2024/12/21
 */
abstract class AbstractCodegenTask : DefaultTask() {

    companion object {
        const val FLAG_DO_NOT_OVERWRITE = "[cap4k-ddd-codegen-gradle-plugin:do-not-overwrite]"
        const val PATTERN_SPLITTER = "[\\,\\;]"
        const val PATTERN_DESIGN_PARAMS_SPLITTER = "[\\:]"
        const val PATTERN_LINE_BREAK = "\\r\\n|[\\r\\n]"
        const val AGGREGATE_REPOSITORY_PACKAGE = "adapter.domain.repositories"
        const val AGGREGATE_PACKAGE = "domain.aggregates"
        const val DOMAIN_EVENT_SUBSCRIBER_PACKAGE = "application.subscribers.domain"
        const val INTEGRATION_EVENT_SUBSCRIBER_PACKAGE = "application.subscribers.integration"
        const val DEFAULT_MUL_PRI_KEY_NAME = "Key"
    }

    @get:Input
    abstract val extension: Property<Cap4kCodegenExtension>

    @get:Input
    abstract val projectName: Property<String>

    @get:Input
    abstract val projectGroup: Property<String>

    @get:Input
    abstract val projectVersion: Property<String>

    @get:Input
    abstract val projectDir: Property<String>

    @Internal
    protected var template: Template? = null

    init {
        group = "cap4k codegen"
    }

    /**
     * 获取插件扩展配置
     */
    protected fun getExtension(): Cap4kCodegenExtension {
        return extension.get()
    }

    /**
     * 获取项目目录
     */
    protected fun getProjectDir(): String {
        return projectDir.get()
    }

    /**
     * 获取适配层模块路径
     */
    @Internal
    protected fun getAdapterModulePath(): String {
        val ext = getExtension()
        return if (ext.multiModule.get()) {
            val baseDir = getProjectDir()
            val moduleName = "${projectName.get()}${ext.moduleNameSuffix4Adapter}"
            "$baseDir${File.separator}$moduleName"
        } else {
            getProjectDir()
        }
    }

    /**
     * 获取应用层模块路径
     */
    @Internal
    protected fun getApplicationModulePath(): String {
        val ext = getExtension()
        return if (ext.multiModule.get()) {
            val baseDir = getProjectDir()
            val moduleName = "${projectName.get()}${ext.moduleNameSuffix4Application}"
            "$baseDir${File.separator}$moduleName"
        } else {
            getProjectDir()
        }
    }

    /**
     * 获取领域层模块路径
     */
    @Internal
    protected fun getDomainModulePath(): String {
        val ext = getExtension()
        return if (ext.multiModule.get()) {
            val baseDir = getProjectDir()
            val moduleName = "${projectName.get()}${ext.moduleNameSuffix4Domain}"
            "$baseDir${File.separator}$moduleName"
        } else {
            getProjectDir()
        }
    }

    /**
     * 获取上下文变量
     */
    @Internal
    protected fun getEscapeContext(): Map<String, String> {
        val ext = getExtension()
        val context = mutableMapOf<String, String>()

        // 项目相关配置
        context["artifactId"] = projectName.get()
        context["groupId"] = projectGroup.get()
        context["version"] = projectVersion.get()

        // 基础配置
        context["archTemplate"] = ext.archTemplate.get()
        context["archTemplateEncoding"] = ext.archTemplateEncoding.get()
        context["outputEncoding"] = ext.outputEncoding.get()
        context["designFile"] = ext.designFile.get()
        context["basePackage"] = ext.basePackage.get()
        context["basePackage__as_path"] = ext.basePackage.get().replace(".", File.separator)
        context["multiModule"] = ext.multiModule.get().toString()

        // 模块路径
        context["adapterModulePath"] = getAdapterModulePath()
        context["applicationModulePath"] = getApplicationModulePath()
        context["domainModulePath"] = getDomainModulePath()

        // 数据库配置
        context["dbUrl"] = ext.database.url.get()
        context["dbUsername"] = ext.database.username.get()
        context["dbPassword"] = ext.database.password.get()
        context["dbSchema"] = ext.database.schema.get()
        context["dbTables"] = ext.database.tables.get()
        context["dbIgnoreTables"] = ext.database.ignoreTables.get()

        // 生成配置
        context["versionField"] = ext.generation.versionField.get()
        context["deletedField"] = ext.generation.deletedField.get()
        context["readonlyFields"] = ext.generation.readonlyFields.get()
        context["ignoreFields"] = ext.generation.ignoreFields.get()
        context["entityBaseClass"] = ext.generation.entityBaseClass.get()
        context["rootEntityBaseClass"] = ext.generation.rootEntityBaseClass.get()
        context["entityClassExtraImports"] = ext.generation.entityClassExtraImports.get()
        context["entitySchemaOutputPackage"] = ext.generation.entitySchemaOutputPackage.get()
        context["entitySchemaOutputMode"] = ext.generation.entitySchemaOutputMode.get()
        context["entitySchemaNameTemplate"] = ext.generation.entitySchemaNameTemplate.get()
        context["aggregateNameTemplate"] = ext.generation.aggregateNameTemplate.get()
        context["idGenerator"] = ext.generation.idGenerator.get()
        context["idGenerator4ValueObject"] = ext.generation.idGenerator4ValueObject.get()
        context["hashMethod4ValueObject"] = ext.generation.hashMethod4ValueObject.get()
        context["fetchType"] = ext.generation.fetchType.get()
        context["enumValueField"] = ext.generation.enumValueField.get()

        context["enumNameField"] = ext.generation.enumNameField.get()

        context["enumUnmatchedThrowException"] = ext.generation.enumUnmatchedThrowException.get().toString()
        context["datePackage"] = ext.generation.datePackage.get()
        context["typeRemapping"] = stringfyTypeRemapping()
        context["generateDefault"] = ext.generation.generateDefault.get().toString()
        context["generateDbType"] = ext.generation.generateDbType.get().toString()
        context["generateSchema"] = ext.generation.generateSchema.get().toString()
        context["generateAggregate"] = ext.generation.generateAggregate.get().toString()
        context["generateParent"] = ext.generation.generateParent.get().toString()
        context["aggregateRootAnnotation"] = getAggregateRootAnnotation()
        context["aggregateNameTemplate"] = ext.generation.aggregateNameTemplate.get()
        context["repositoryNameTemplate"] = ext.generation.repositoryNameTemplate.get()
        context["repositorySupportQuerydsl"] = ext.generation.repositorySupportQuerydsl.get().toString()
        context["date"] = LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyy/MM/dd"))
        context["SEPARATOR"] = File.separator
        context["separator"] = File.separator


        // 包名相关
//        context["lastPackageName"] = getLastPackageName(ext.basePackage.get())
//        context["parentPackageName"] = getParentPackageName(ext.basePackage.get())

        return context
    }

//    /**
//     * 获取最后一段包名
//     */
//    private fun getLastPackageName(packageName: String): String {
//        return NamingUtils.getLastPackageName(packageName)
//    }
//
//    /**
//     * 获取父包名
//     */
//    private fun getParentPackageName(packageName: String): String {
//        return NamingUtils.parentPackageName(packageName)
//    }

    fun stringfyTypeRemapping(): String {
        val typeRemapping = getExtension().generation.typeRemapping.get()
        var result = ""
        typeRemapping.forEach { (k, v) ->
            result += "<$k>$v</$k>"
        }
        return result
    }

    /**
     * 渲染模板并生成文件
     */
    @Throws(Exception::class)
    protected fun render(pathNode: PathNode, parentPath: String): String {
        var path = parentPath
        when (pathNode.type) {
            "root" -> {
                pathNode.children?.forEach { child ->
                    render(child, parentPath)
                }
            }

            "dir" -> {
                path = renderDir(pathNode, parentPath)
                pathNode.children?.forEach { child ->
                    render(child, path)
                }
            }

            "file" -> {
                path = renderFile(pathNode, parentPath)
            }
        }
        return path
    }

    fun renderDir(pathNode: PathNode, parentPath: String): String {
        require("dir".equals(pathNode.type, ignoreCase = true)) { "pathNode must be a directory type" }
        if (pathNode.name?.isBlank() == true) return parentPath

        val path = "$parentPath${File.separator}${pathNode.name}"

        if (File(path).exists()) {
            when (pathNode.conflict) {
                "skip" -> {
                    logger.info("目录已存在，跳过: $path")
                }

                "warn" -> {
                    logger.warn("目录已存在，继续: $path")
                }

                "overwrite" -> {
                    logger.info("目录覆盖: $path")
                    File(path).delete()
                    File(path).mkdirs()
                }
            }
        } else {
            File(path).mkdirs()
            logger.info("创建目录: $path")
        }

        if (pathNode.tag?.isBlank() == true) return path

        pathNode.tag!!.split(PATTERN_SPLITTER)
            .forEach { tag ->
                renderTemplate(template!!.select(tag), path)
            }

        return path
    }

    fun renderFile(pathNode: PathNode, parentPath: String): String {
        require("file".equals(pathNode.type, ignoreCase = true)) { "pathNode must be a directory type" }
        require(!(pathNode.name.isNullOrBlank())) { "pathNode name must not be blank" }

        val path = "$parentPath${File.separator}${pathNode.name}"

        val content = pathNode.data!!
        val encoding = pathNode.encoding ?: getExtension().outputEncoding.get()

        val file = File(path)
        if (file.exists()) {
            when (pathNode.conflict) {
                "skip" -> {
                    logger.info("文件已存在，跳过: $path")
                }

                "warn" -> {
                    logger.warn("文件已存在，继续: $path")
                }

                "overwrite" -> {
                    if (file.reader(charset(encoding)).readText().contains(FLAG_DO_NOT_OVERWRITE)) {
                        logger.warn("文件已存在且包含保护标记，跳过: $path")
                    } else {
                        logger.info("文件覆盖: $path")
                        file.delete()
                        file.writeText(content, charset(encoding))
                    }
                }
            }
        } else {
            file.mkdirs()
            file.writeText(content, charset(encoding))
            logger.info("创建目录: $path")
        }

        return path
    }

    /**
     * 生成领域事件名称
     */
    fun generateDomainEventName(eventName: String): String {
        var domainEventClassName = NamingUtils.toUpperCamelCase(eventName) ?: eventName
        if (!domainEventClassName.endsWith("Event") && !domainEventClassName.endsWith("Evt")) {
            domainEventClassName += "DomainEvent"
        }
        return domainEventClassName
    }

    fun generateDomainServiceName(svcName: String): String {
        var serviceName = svcName
        if (!serviceName.endsWith("Svc") && !serviceName.endsWith("Service")) {
            serviceName += "DomainService"
        }
        return serviceName
    }

    fun alias4Template(tag: String, `var`: String): List<String> {
        val key = "$tag.${`var`}"
        return when (key) {
            "schema.Comment", "enum.Comment", "domain_event.Comment", "domain_event_handler.Comment",
            "specification.Comment", "factory.Comment", "domain_service.Comment", "integration_event.Comment",
            "integration_event_handler.Comment", "client.Comment", "query.Comment", "command.Comment",
            "client_handler.Comment", "query_handler.Comment", "command_handler.Comment", "saga.Comment" ->
                listOf(`var`, "comment", "COMMENT")

            "schema.CommentEscaped", "enum.CommentEscaped", "domain_event.CommentEscaped", "domain_event_handler.CommentEscaped",
            "specification.CommentEscaped", "factory.CommentEscaped", "domain_service.CommentEscaped", "integration_event.CommentEscaped",
            "integration_event_handler.CommentEscaped", "client.CommentEscaped", "query.CommentEscaped", "command.CommentEscaped",
            "client_handler.CommentEscaped", "query_handler.CommentEscaped", "command_handler.CommentEscaped", "saga.CommentEscaped" ->
                listOf(`var`, "commentEscaped", "COMMENT_ESCAPED", "Comment_Escaped")

            "schema.Aggregate", "enum.Aggregate", "domain_event.Aggregate", "domain_event_handler.Aggregate",
            "specification.Aggregate", "factory.Aggregate" ->
                listOf(`var`, "aggregate", "AGGREGATE")

            "schema.entityPackage", "enum.entityPackage", "domain_event.entityPackage", "domain_event_handler.entityPackage",
            "specification.entityPackage", "factory.entityPackage" ->
                listOf(`var`, "EntityPackage", "ENTITY_PACKAGE", "entity_package", "Entity_Package")

            "schema.templatePackage", "enum.templatePackage", "domain_event.templatePackage", "domain_event_handler.templatePackage",
            "specification.templatePackage", "factory.templatePackage" ->
                listOf(`var`, "TemplatePackage", "TEMPLATE_PACKAGE", "template_package", "Template_Package")

            "schema.Entity", "enum.Entity", "domain_event.Entity", "domain_event_handler.Entity",
            "specification.Entity", "factory.Entity" ->
                listOf(
                    `var`,
                    "entity",
                    "ENTITY",
                    "entityType",
                    "EntityType",
                    "ENTITY_TYPE",
                    "Entity_Type",
                    "entity_type"
                )

            "schema.EntityVar", "enum.EntityVar", "domain_event.EntityVar", "domain_event_handler.EntityVar",
            "specification.EntityVar", "factory.EntityVar" ->
                listOf(`var`, "entityVar", "ENTITY_VAR", "entity_var", "Entity_Var")

            "schema_base.SchemaBase", "schema.SchemaBase" ->
                listOf(`var`, "schema_base", "SCHEMA_BASE")

            "schema.IdField" ->
                listOf(`var`, "idField", "ID_FIELD", "id_field", "Id_Field")

            "schema.FIELD_ITEMS" ->
                listOf(`var`, "fieldItems", "field_items", "Field_Items")

            "schema.JOIN_ITEMS" ->
                listOf(`var`, "joinItems", "join_items", "Join_Items")

            "schema_field.fieldType" ->
                listOf(`var`, "FIELD_TYPE", "field_type", "Field_Type")

            "schema_field.fieldName" ->
                listOf(`var`, "FIELD_NAME", "field_name", "Field_Name")

            "schema_field.fieldComment" ->
                listOf(`var`, "FIELD_COMMENT", "field_comment", "Field_Comment")

            "enum.Enum" ->
                listOf(`var`, "enum", "ENUM", "EnumType", "enumType", "ENUM_TYPE", "enum_type", "Enum_Type")

            "enum.EnumValueField" ->
                listOf(`var`, "enumValueField", "ENUM_VALUE_FIELD", "enum_value_field", "Enum_Value_Field")

            "enum.EnumNameField" ->
                listOf(`var`, "enumNameField", "ENUM_NAME_FIELD", "enum_name_field", "Enum_Name_Field")

            "enum.ENUM_ITEMS" ->
                listOf(`var`, "enumItems", "enum_items", "Enum_Items")

            "enum_item.itemName" ->
                listOf(`var`, "ItemName", "ITEM_NAME", "item_name", "Item_Name")

            "enum_item.itemValue" ->
                listOf(`var`, "ItemValue", "ITEM_VALUE", "item_value", "Item_Value")

            "enum_item.itemDesc" ->
                listOf(`var`, "ItemDesc", "ITEM_DESC", "item_desc", "Item_Desc")

            "domain_event.DomainEvent", "domain_event_handler.DomainEvent" ->
                listOf(
                    `var`,
                    "domainEvent",
                    "DOMAIN_EVENT",
                    "domain_event",
                    "Domain_Event",
                    "Event",
                    "EVENT",
                    "event",
                    "DE",
                    "D_E",
                    "de",
                    "d_e"
                )

            "domain_event.persist", "domain_event_handler.persist" ->
                listOf(`var`, "Persist", "PERSIST")

            "domain_service.DomainService" ->
                listOf(
                    `var`,
                    "domainService",
                    "DOMAIN_SERVICE",
                    "domain_service",
                    "Domain_Service",
                    "Service",
                    "SERVICE",
                    "service",
                    "Svc",
                    "SVC",
                    "svc",
                    "DS",
                    "D_S",
                    "ds",
                    "d_s"
                )

            "specification.Specification" ->
                listOf(`var`, "specification", "SPECIFICATION", "Spec", "SPEC", "spec")

            "factory.Factory" ->
                listOf(`var`, "factory", "FACTORY", "Fac", "FAC", "fac")

            "integration_event.IntegrationEvent", "integration_event_handler.IntegrationEvent" ->
                listOf(
                    `var`,
                    "integrationEvent",
                    "integration_event",
                    "INTEGRATION_EVENT",
                    "Integration_Event",
                    "Event",
                    "EVENT",
                    "event",
                    "IE",
                    "I_E",
                    "ie",
                    "i_e"
                )

            "specification.AggregateRoot", "factory.AggregateRoot", "domain_event.AggregateRoot", "domain_event_handler.AggregateRoot" ->
                listOf(
                    `var`,
                    "aggregateRoot",
                    "aggregate_root",
                    "AGGREGATE_ROOT",
                    "Aggregate_Root",
                    "Root",
                    "ROOT",
                    "root",
                    "AR",
                    "A_R",
                    "ar",
                    "a_r"
                )

            "client.Client", "client_handler.Client" ->
                listOf(`var`, "client", "CLIENT", "Cli", "CLI", "cli")

            "query.Query", "query_handler.Query" ->
                listOf(`var`, "query", "QUERY", "Qry", "QRY", "qry")

            "command.Command", "command_handler.Command" ->
                listOf(`var`, "command", "COMMAND", "Cmd", "CMD", "cmd")

            "client.Request", "client_handler.Request", "query.Request", "query_handler.Request", "command.Request", "command_handler.Request" ->
                listOf(`var`, "request", "REQUEST", "Req", "REQ", "req", "Param", "PARAM", "param")

            "client.Response", "client_handler.Response", "query.Response", "query_handler.Response", "command.Response", "command_handler.Response", "saga.Response", "saga_handler.Response" ->
                listOf(
                    `var`,
                    "response",
                    "RESPONSE",
                    "Res",
                    "RES",
                    "res",
                    "ReturnType",
                    "returnType",
                    "RETURN_TYPE",
                    "return_type",
                    "Return_Type",
                    "Return",
                    "RETURN",
                    "return"
                )

            else -> listOf(`var`)
        }
    }

    fun putContext(tag: String, `var`: String, `val`: String, context: MutableMap<String, String>) {
        val aliases = alias4Template(tag, `var`)
        for (alias in aliases) {
            context[alias] = `val`
        }
    }

    fun escape(content: String): String {
        return content
            .replace("\\\\", "\${symbol_escape}")
            .replace("\\:", "\${symbol_colon}")
            .replace("\\,", "\${symbol_comma}")
            .replace("\\;", "\${symbol_semicolon}")
    }

    fun unescape(content: String): String {
        return content
            .replace("\${symbol_escape}", "\\")
            .replace("\${symbol_colon}", ":")
            .replace("\${symbol_comma}", ",")
            .replace("\${symbol_semicolon}", ";")
    }

    open fun renderTemplate(
        templateNodes: List<TemplateNode>,
        parentPath: String,
    ) {
    }

    /**
     * 强制渲染路径节点（忽略renderFileSwitch设置）
     */
    fun forceRender(pathNode: PathNode, parentPath: String): String {
        return render(pathNode, parentPath)
    }

    /**
     * 解析模板配置并设置上下文
     */
    protected fun loadTemplate(templatePath: String): Template {
        val ext = getExtension()
        val templateContent =
            SourceFileUtils.loadFileContent(templatePath, ext.archTemplateEncoding.get(), projectDir.get())
        logger.debug("模板内容: $templateContent")

        PathNode.setDirectory(SourceFileUtils.resolveDirectory(templatePath, projectDir.get()))
        val template = JSON.parseObject(templateContent, Template::class.java)
        template.resolve(getEscapeContext())

        return template
    }

    /**
     * 获取数据库连接配置
     */
    @Internal
    protected fun getDatabaseConfig(): Triple<String, String, String> {
        val ext = getExtension()
        val url = ext.database.url.get()
        val username = ext.database.username.get()
        val password = ext.database.password.get()
        return Triple(url, username, password)
    }

    /**
     * 获取数据库表信息
     */
    @Internal
    protected fun getTables(): List<Map<String, Any?>> {
        SqlSchemaUtils.setLogger(logger)
        val (url, username, password) = getDatabaseConfig()
        return SqlSchemaUtils.resolveTables(url, username, password)
    }

    /**
     * 获取数据库列信息
     */
    @Internal
    protected fun getColumns(): List<Map<String, Any?>> {
        SqlSchemaUtils.setLogger(logger)
        val (url, username, password) = getDatabaseConfig()
        return SqlSchemaUtils.resolveColumns(url, username, password)
    }

    /**
     * 根据表名过滤表
     */
    protected fun filterTables(tables: List<Map<String, Any?>>): List<Map<String, Any?>> {
        val ext = getExtension()
        val tablesPattern = ext.database.tables.get()
        val ignoreTablesPattern = ext.database.ignoreTables.get()

        var filtered = tables

        // 包含模式过滤
        if (tablesPattern.isNotEmpty()) {
            val patterns = tablesPattern.split(Regex(PATTERN_SPLITTER))
                .map { it.trim() }
                .filter { it.isNotEmpty() }

            filtered = filtered.filter { table ->
                val tableName = SqlSchemaUtils.getTableName(table)
                patterns.any { pattern ->
                    tableName.matches(Regex(pattern.replace("*", ".*")))
                }
            }
        }

        // 排除模式过滤
        if (ignoreTablesPattern.isNotEmpty()) {
            val patterns = ignoreTablesPattern.split(Regex(PATTERN_SPLITTER))
                .map { it.trim() }
                .filter { it.isNotEmpty() }

            filtered = filtered.filter { table ->
                val tableName = SqlSchemaUtils.getTableName(table)
                patterns.none { pattern ->
                    tableName.matches(Regex(pattern.replace("*", ".*")))
                }
            }
        }

        return filtered
    }

    /**
     * 获取实体类额外导入包
     */
    @Internal
    protected fun getEntityClassExtraImports(): List<String> {
        val ext = getExtension()
        val defaultImportList = listOf(
            "com.only4.cap4k.ddd.core.domain.aggregate.annotation.Aggregate",
            "javax.persistence.*"
        )

        val imports = mutableListOf<String>()
        imports.addAll(defaultImportList)

        val extraImports = ext.generation.entityClassExtraImports.get()
        if (extraImports.isNotEmpty()) {
            imports.addAll(
                extraImports.split(";")
                    .map { it.trim().replace(Regex(PATTERN_LINE_BREAK), "") }
                    .map { if (it.startsWith("import ")) it.substring(6).trim() else it }
                    .filter { it.isNotBlank() }
            )
        }

        return imports.distinct()
    }

    /**
     * 获取实体Schema输出模式
     */
    @Internal
    protected fun getEntitySchemaOutputMode(): String {
        val ext = getExtension()
        val mode = ext.generation.entitySchemaOutputMode.get()
        return mode.ifBlank { "ref" }
    }

    /**
     * 获取实体Schema输出包
     */
    @Internal
    protected fun getEntitySchemaOutputPackage(): String {
        val ext = getExtension()
        val packageName = ext.generation.entitySchemaOutputPackage.get()
        return packageName.ifBlank { "domain._share.meta" }
    }

    /**
     * 获取聚合根注解
     */
    @Internal
    protected fun getAggregateRootAnnotation(): String {
        val ext = getExtension()
        var annotation = ext.generation.aggregateRootAnnotation.get()

        if (annotation.isNotEmpty()) {
            annotation = annotation.trim()
            if (!annotation.startsWith("@")) {
                annotation = "@$annotation"
            }
        }

        return annotation
    }
}
