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
            val moduleName = getLastPackageName(ext.basePackage.get()) + ext.moduleNameSuffix4Adapter.get()
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
            val moduleName = getLastPackageName(ext.basePackage.get()) + ext.moduleNameSuffix4Application.get()
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
            val moduleName = getLastPackageName(ext.basePackage.get()) + ext.moduleNameSuffix4Domain.get()
            "$baseDir${File.separator}$moduleName"
        } else {
            getProjectDir()
        }
    }

    /**
     * 获取上下文变量
     */
    @Internal
    protected fun getEscapeContext(): Map<String, String?> {
        val ext = getExtension()
        val context = mutableMapOf<String, String?>()

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
        context["dbSchema"] = ext.database.schema.orNull
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
        context["lastPackageName"] = getLastPackageName(ext.basePackage.get())
        context["parentPackageName"] = getParentPackageName(ext.basePackage.get())

        return context
    }

    /**
     * 获取最后一段包名
     */
    private fun getLastPackageName(packageName: String): String {
        return NamingUtils.getLastPackageName(packageName)
    }

    /**
     * 获取父包名
     */
    private fun getParentPackageName(packageName: String): String {
        return NamingUtils.parentPackageName(packageName)
    }

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

    fun renderTemplate(
        templateNodes: List<TemplateNode>,
        parentPath: String,
    ) {
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

    fun getAggregateRootAnnotation(): String {
        if (getExtension().generation.aggregateRootAnnotation.get().isNotEmpty()) {
            getExtension().generation.aggregateRootAnnotation.set(
                getExtension().generation.aggregateRootAnnotation.get().trim()
            )
            if (!getExtension().generation.aggregateRootAnnotation.get().startsWith("@")) {
                getExtension().generation.aggregateRootAnnotation.set("@${getExtension().generation.aggregateRootAnnotation.get()}")
            }
        }
        return getExtension().generation.aggregateRootAnnotation.get()
    }
}
