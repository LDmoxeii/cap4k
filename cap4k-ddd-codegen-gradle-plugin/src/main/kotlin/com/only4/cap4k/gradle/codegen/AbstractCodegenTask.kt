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
import org.gradle.api.tasks.Optional
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

        // 基础配置
        context["basePackage"] = ext.basePackage.get()
        context["basePackage__as_path"] = ext.basePackage.get().replace(".", File.separator)
        context["multiModule"] = ext.multiModule.get().toString()
        context["archTemplateEncoding"] = ext.archTemplateEncoding.get()
        context["outputEncoding"] = ext.outputEncoding.get()

        // 项目相关配置
        context["artifactId"] = projectName.get()
        context["groupId"] = projectGroup.get()
        context["version"] = projectVersion.get()
        context["projectName"] = projectName.get()

        // 调试输出
        logger.info("Context variables:")
        logger.info("- artifactId: ${context["artifactId"]}")
        logger.info("- groupId: ${context["groupId"]}")
        logger.info("- version: ${context["version"]}")
        logger.info("- basePackage: ${context["basePackage"]}")

        // 模块路径
        context["adapterModulePath"] = getAdapterModulePath()
        context["applicationModulePath"] = getApplicationModulePath()
        context["domainModulePath"] = getDomainModulePath()

        // 包名相关
        context["lastPackageName"] = getLastPackageName(ext.basePackage.get())
        context["parentPackageName"] = getParentPackageName(ext.basePackage.get())

        // 时间相关
        val now = LocalDateTime.now()
        context["currentDate"] = now.format(DateTimeFormatter.ofPattern("yyyy/MM/dd"))
        context["currentTime"] = now.format(DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss"))
        context["currentYear"] = now.year.toString()

        // 数据库配置
        context["dbUrl"] = ext.database.url.orNull
        context["dbUsername"] = ext.database.username.orNull
        context["dbPassword"] = ext.database.password.orNull
        context["dbSchema"] = ext.database.schema.orNull

        // 生成配置
        context["generateSchema"] = ext.generation.generateSchema.get().toString()
        context["generateAggregate"] = ext.generation.generateAggregate.get().toString()
        context["entityBaseClass"] = ext.generation.entityBaseClass.get()
        context["rootEntityBaseClass"] = ext.generation.rootEntityBaseClass.get()

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

    /**
     * 渲染模板并生成文件
     */
    @Throws(Exception::class)
    protected fun render(pathNode: PathNode, outputDir: String) {
        when (pathNode.type) {
            "root" -> {
                pathNode.children?.forEach { child ->
                    render(child, outputDir)
                }
            }

            "dir" -> {
                val dirPath = "$outputDir${File.separator}${pathNode.name}"
                val dir = File(dirPath)
                if (!dir.exists()) {
                    dir.mkdirs()
                    logger.info("创建目录: $dirPath")
                }
                pathNode.children?.forEach { child ->
                    render(child, dirPath)
                }
            }

            "file" -> {
                val filePath = "$outputDir${File.separator}${pathNode.name}"
                val file = File(filePath)

                // 检查冲突处理策略
                if (file.exists()) {
                    when (pathNode.conflict) {
                        "skip" -> {
                            logger.info("文件已存在，跳过: $filePath")
                            return
                        }

                        "warn" -> {
                            logger.warn("文件已存在，覆盖: $filePath")
                        }

                        "overwrite" -> {
                            logger.info("覆盖文件: $filePath")
                        }
                    }
                }

                // 检查是否包含不覆盖标记
                if (file.exists()) {
                    val content = file.readText()
                    if (content.contains(FLAG_DO_NOT_OVERWRITE)) {
                        logger.info("文件包含不覆盖标记，跳过: $filePath")
                        return
                    }
                }

                // 创建父目录
                file.parentFile?.mkdirs()

                // 写入文件
                val encoding = pathNode.encoding ?: getExtension().outputEncoding.get()
                file.writeText(pathNode.data ?: "", charset(encoding))
                logger.info("生成文件: $filePath")
            }

            "segment" -> {
                // segment类型节点用于代码片段，不直接生成文件
                logger.debug("处理代码片段: ${pathNode.name}")
            }
        }
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
     * 获取指定表的列信息
     */
    protected fun getColumnsForTable(tableName: String, allColumns: List<Map<String, Any?>>): List<Map<String, Any?>> {
        return allColumns.filter { column ->
            val columnTableName = SqlSchemaUtils.getColumnName(column)
            columnTableName.equals(tableName, ignoreCase = true)
        }.sortedBy { SqlSchemaUtils.getOrdinalPosition(it) }
    }
}
