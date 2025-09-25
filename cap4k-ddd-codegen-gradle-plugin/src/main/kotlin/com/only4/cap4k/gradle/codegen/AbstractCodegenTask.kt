package com.only4.cap4k.gradle.codegen

import com.only4.cap4k.gradle.codegen.misc.toUpperCamelCase
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
        const val PATTERN_SPLITTER = "[,;]"
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

    @Internal
    protected var renderFileSwitch = true

    init {
        group = "cap4k codegen"
    }

    protected fun getExtension(): Cap4kCodegenExtension = extension.get()
    protected fun getProjectDir(): String = projectDir.get()

    @Internal
    protected fun getEntityClassExtraImports(): List<String> {
        val ext = getExtension()
        val defaultImportList = listOf(
            "com.only4.cap4k.ddd.core.domain.aggregate.annotation.Aggregate",
            "jakarta.persistence.*",
            "org.hibernate.annotations.DynamicInsert",
            "org.hibernate.annotations.DynamicUpdate",
            "org.hibernate.annotations.Fetch",
            "org.hibernate.annotations.FetchMode",
            "org.hibernate.annotations.GenericGenerator"
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

    private fun modulePath(suffix: String): String =
        getExtension().let { ext ->
            if (ext.multiModule.get())
                "${getProjectDir()}${File.separator}${projectName.get()}$suffix"
            else getProjectDir()
        }

    @Internal
    protected fun getAdapterModulePath(): String =
        modulePath(getExtension().moduleNameSuffix4Adapter.get())

    @Internal
    protected fun getApplicationModulePath(): String =
        modulePath(getExtension().moduleNameSuffix4Application.get())

    @Internal
    protected fun getDomainModulePath(): String =
        modulePath(getExtension().moduleNameSuffix4Domain.get())

    fun forceRender(pathNode: PathNode, parentPath: String): String {
        val temp = renderFileSwitch
        renderFileSwitch = true
        val path = render(pathNode, parentPath)
        renderFileSwitch = temp
        return path
    }

    protected fun render(pathNode: PathNode, parentPath: String): String =
        when (pathNode.type?.lowercase()) {
            "root" -> {
                pathNode.children?.forEach { render(it, parentPath) }
                parentPath
            }

            "dir" -> {
                val dirPath = renderDir(pathNode, parentPath)
                pathNode.children?.forEach { render(it, dirPath) }
                dirPath
            }

            "file" -> renderFile(pathNode, parentPath)
            else -> parentPath
        }


    protected open fun renderTemplate(
        templateNodes: List<TemplateNode>,
        parentPath: String,
    ) = Unit

    fun renderDir(pathNode: PathNode, parentPath: String): String {
        require("dir".equals(pathNode.type, true)) { "pathNode must be a directory type" }
        val name = pathNode.name?.takeIf { it.isNotBlank() } ?: return parentPath
        val path = "$parentPath${File.separator}$name"
        val dirFile = File(path)

        if (dirFile.exists()) {
            when (pathNode.conflict.lowercase()) {
                "skip" -> logger.info("目录已存在，跳过: $path")
                "warn" -> logger.warn("目录已存在，继续: $path")
                "overwrite" -> {
                    logger.info("目录覆盖: $path")
                    dirFile.deleteRecursively()
                    dirFile.mkdirs()
                }
            }
        } else {
            dirFile.mkdirs()
            logger.info("创建目录: $path")
        }

        if (pathNode.tag.isNullOrBlank()) return path
        pathNode.tag!!
            .split(Regex(PATTERN_SPLITTER))
            .filter { it.isNotBlank() }
            .forEach { renderTemplate(template!!.select(it), path) }

        return path
    }

    fun renderFile(pathNode: PathNode, parentPath: String): String {
        require("file".equals(pathNode.type, true)) { "pathNode must be a file type" }
        val name = pathNode.name?.takeIf { it.isNotBlank() }
            ?: error("pathNode name must not be blank")

        val path = "$parentPath${File.separator}$name"
        if (!renderFileSwitch) return path
        val file = File(path)
        val content = pathNode.data ?: ""
        val encoding = pathNode.encoding ?: getExtension().outputEncoding.get()

        if (file.exists()) {
            when (pathNode.conflict.lowercase()) {
                "skip" -> logger.info("文件已存在，跳过: $path")
                "warn" -> logger.warn("文件已存在，继续: $path")
                "overwrite" -> {
                    if (file.readText(charset(encoding)).contains(FLAG_DO_NOT_OVERWRITE)) {
                        logger.warn("文件已存在且包含保护标记，跳过: $path")
                    } else {
                        logger.info("文件覆盖: $path")
                        file.writeText(content, charset(encoding))
                    }
                }
            }
        } else {
            file.parentFile?.mkdirs()
            file.writeText(content, charset(encoding))
            logger.info("创建文件: $path")
        }
        return path
    }

    fun generateDomainEventName(eventName: String): String {
        val base = toUpperCamelCase(eventName) ?: eventName
        return if (base.endsWith("Event") || base.endsWith("Evt")) base else "${base}DomainEvent"
    }

    fun generateDomainServiceName(svcName: String): String {
        val base = if (svcName.endsWith("Svc") || svcName.endsWith("Service")) svcName else "${svcName}DomainService"
        return base
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

    @Internal
    protected fun getEscapeContext(): Map<String, String> = buildMap {
        val ext = getExtension()
        // 项目
        put("artifactId", projectName.get())
        put("groupId", projectGroup.get())
        put("version", projectVersion.get())
        // 基础
        put("archTemplate", ext.archTemplate.get())
        put("archTemplateEncoding", ext.archTemplateEncoding.get())
        put("outputEncoding", ext.outputEncoding.get())
        put("designFile", ext.designFile.get())
        put("basePackage", ext.basePackage.get())
        put("basePackage__as_path", ext.basePackage.get().replace(".", File.separator))
        put("multiModule", ext.multiModule.get().toString())
        // 模块路径
        put("adapterModulePath", getAdapterModulePath())
        put("applicationModulePath", getApplicationModulePath())
        put("domainModulePath", getDomainModulePath())
        // 数据库
        with(ext.database) {
            put("dbUrl", url.get())
            put("dbUsername", username.get())
            put("dbPassword", password.get())
            put("dbSchema", schema.get())
            put("dbTables", tables.get())
            put("dbIgnoreTables", ignoreTables.get())
        }
        // 生成配置
        with(ext.generation) {
            put("versionField", versionField.get())
            put("deletedField", deletedField.get())
            put("readonlyFields", readonlyFields.get())
            put("ignoreFields", ignoreFields.get())
            put("entityBaseClass", entityBaseClass.get())
            put("rootEntityBaseClass", rootEntityBaseClass.get())
            put("entityClassExtraImports", entityClassExtraImports.get())
            put("entitySchemaOutputPackage", entitySchemaOutputPackage.get())
            put("entitySchemaOutputMode", entitySchemaOutputMode.get())
            put("entitySchemaNameTemplate", entitySchemaNameTemplate.get())
            put("aggregateNameTemplate", aggregateNameTemplate.get())
            put("idGenerator", idGenerator.get())
            put("idGenerator4ValueObject", idGenerator4ValueObject.get())
            put("hashMethod4ValueObject", hashMethod4ValueObject.get())
            put("fetchType", fetchType.get())
            put("enumValueField", enumValueField.get())
            put("enumNameField", enumNameField.get())
            put("enumUnmatchedThrowException", enumUnmatchedThrowException.get().toString())
            put("datePackage", datePackage.get())
            put("typeRemapping", stringfyTypeRemapping())
            put("generateDbType", generateDbType.get().toString())
            put("generateSchema", generateSchema.get().toString())
            put("generateAggregate", generateAggregate.get().toString())
            put("generateParent", generateParent.get().toString())
            put("aggregateNameTemplate", aggregateNameTemplate.get())
            put("repositoryNameTemplate", repositoryNameTemplate.get())
            put("repositorySupportQuerydsl", repositorySupportQuerydsl.get().toString())
        }
        // 其它
        put("date", LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyy/MM/dd")))
        put("SEPARATOR", File.separator)
        put("separator", File.separator)
    }

    fun stringfyTypeRemapping(): String =
        getExtension().generation.typeRemapping.get()
            .entries.joinToString("") { (k, v) -> "<$k>$v</$k>" }
}
