package com.only4.cap4k.gradle.codegen

import com.alibaba.fastjson.JSON
import com.only4.cap4k.gradle.codegen.misc.Inflector
import com.only4.cap4k.gradle.codegen.misc.NamingUtils
import com.only4.cap4k.gradle.codegen.misc.SourceFileUtils
import com.only4.cap4k.gradle.codegen.misc.SqlSchemaUtils
import com.only4.cap4k.gradle.codegen.misc.SqlSchemaUtils.LEFT_QUOTES_4_ID_ALIAS
import com.only4.cap4k.gradle.codegen.misc.SqlSchemaUtils.RIGHT_QUOTES_4_ID_ALIAS
import com.only4.cap4k.gradle.codegen.misc.SqlSchemaUtils.hasColumn
import com.only4.cap4k.gradle.codegen.template.TemplateNode
import org.gradle.api.provider.Property
import org.gradle.api.tasks.Input
import org.gradle.api.tasks.Internal
import org.gradle.api.tasks.TaskAction
import java.io.BufferedWriter
import java.io.File
import java.io.StringWriter
import java.time.LocalDateTime
import java.time.format.DateTimeFormatter
import java.util.*
import java.util.stream.Collectors

/**
 * 生成实体类任务
 */
open class GenEntityTask : GenArchTask() {

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

    companion object {
        const val DEFAULT_SCHEMA_PACKAGE = "meta"
        const val DEFAULT_SPEC_PACKAGE = "specs"
        const val DEFAULT_FAC_PACKAGE = "factory"
        const val DEFAULT_ENUM_PACKAGE = "enums"
        const val DEFAULT_DOMAIN_EVENT_PACKAGE = "events"
        const val DEFAULT_SCHEMA_BASE_CLASS_NAME = "Schema"
    }

    @Internal
    val tableMap = mutableMapOf<String, Map<String, Any?>>()

    @Internal
    val tableModuleMap = mutableMapOf<String, String>()

    @Internal
    val tableAggregateMap = mutableMapOf<String, String>()

    @Internal
    val columnsMap = mutableMapOf<String, List<Map<String, Any?>>>()

    @Internal
    val enumConfigMap = mutableMapOf<String, Map<Int, Array<String>>>()

    @Internal
    val enumPackageMap = mutableMapOf<String, String>()

    @Internal
    val enumTableNameMap = mutableMapOf<String, String>()

    @Internal
    val entityTypeMap = mutableMapOf<String, String>()

    @Internal
    val annotationsCache = mutableMapOf<String, Map<String, String>>()

    @Internal
    var dbType = "mysql"

    @Internal
    var aggregatesPath = ""

    @Internal
    var schemaPath = ""

    @Internal
    var subscriberPath = ""

    @Internal
    val templateNodeMap = mutableMapOf<String, MutableList<TemplateNode>>()

    fun alias4Design(name: String): String {
        return when (name.lowercase()) {
            "entity", "aggregate", "entities", "aggregates" -> "aggregate"
            "schema", "schemas" -> "schema"
            "enum", "enums" -> "enum"
            "enumitem", "enum_item" -> "enum_item"
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

    override fun renderTemplate(
        templateNodes: List<TemplateNode>,
        parentPath: String,
    ) {
        for (templateNode in templateNodes) {
            val alias = alias4Design(templateNode.tag!!)
            when (alias) {
                "aggregate" -> aggregatesPath = parentPath
                "schema_base" -> schemaPath = parentPath
                "domain_event_handler" -> subscriberPath = parentPath
            }

            if (!templateNodeMap.containsKey(alias)) {
                templateNodeMap[alias] = mutableListOf()
            }
            templateNodeMap[alias]!!.add(templateNode)
        }
    }

    @TaskAction
    override fun generate() {
        super.generate()
        genEntity()
    }

    fun genEntity() {
        logger.info("生成实体类...")

        val ext = getExtension()

        // 设置 SqlSchemaUtils 的任务引用
        SqlSchemaUtils.task = this

        // 数据库连接信息
        val (url, username, password) = getDatabaseConfig()
        logger.info("数据库连接：$url")
        logger.info("数据库账号：$username")
        logger.info("数据库密码：$password")
        logger.info("数据库名称：${ext.database.schema.get()}")
        logger.info("包含表：${ext.database.tables.get()}")
        logger.info("忽略表：${ext.database.ignoreTables.get()}")
        logger.info("乐观锁字段：${ext.generation.versionField.get()}")
        logger.info("逻辑删除字段：${ext.generation.deletedField.get()}")
        logger.info("只读字段：${ext.generation.readonlyFields.get()}")
        logger.info("忽略字段：${ext.generation.ignoreFields.get()}")
        logger.info("")

        // 项目结构解析
        if (ext.basePackage.get().isBlank()) {
            ext.basePackage.set(SourceFileUtils.resolveDefaultBasePackage(getDomainModulePath()))
        }
        logger.info("实体类基类：${ext.generation.entityBaseClass.get()}")
        logger.info("聚合根标注注解: ${getAggregateRootAnnotation()}")
        logger.info("主键ID生成器: ${ext.generation.idGenerator.get()}")
        logger.info("日期类型映射: ${ext.generation.datePackage.get()}")
        logger.info("枚举值字段名称: ${ext.generation.enumValueField.get()}")
        logger.info("枚举名字段名称: ${ext.generation.enumNameField.get()}")
        logger.info("枚举不匹配是否抛出异常: ${ext.generation.enumUnmatchedThrowException.get()}")
        logger.info("类型强制映射规则: ")
        ext.generation.typeRemapping.get().forEach { (key, value) ->
            logger.info("  $key <-> $value")
        }
        logger.info("生成Schema：${if (ext.generation.generateSchema.get()) "是" else "否"}")
        if (ext.generation.generateSchema.get()) {
            logger.info("  输出模式：${getEntitySchemaOutputMode()}")
            logger.info("  输出路径：${getEntitySchemaOutputPackage()}")
        }
        logger.info("生成聚合封装类：${if (ext.generation.generateAggregate.get()) "是" else "否"}")
        logger.info("")
        logger.info("")

        try {
            // 数据库解析
            dbType = SqlSchemaUtils.recognizeDbType(url)
            SqlSchemaUtils.processSqlDialect(dbType)

            val tables = filterTables(getTables())
            val allColumns = getColumns()
            val relations = mutableMapOf<String, Map<String, String>>()
            val tablePackageMap = mutableMapOf<String, String>()

            if (tables.isEmpty()) {
                logger.warn("没有找到匹配的表")
                return
            }

            logger.info("----------------解析数据库表----------------")
            logger.info("")
            logger.info("数据库类型：$dbType")

            val maxTableNameLength = tables.maxOfOrNull { SqlSchemaUtils.getTableName(it).length } ?: 20

            // 缓存表和列信息
            for (table in tables) {
                val tableName = SqlSchemaUtils.getTableName(table)
                val tableColumns = allColumns.filter { column ->
                    SqlSchemaUtils.isColumnInTable(column, table)
                }.sortedBy { SqlSchemaUtils.getOridinalPosition(it) }

                tableMap[tableName] = table
                columnsMap[tableName] = tableColumns

                logger.info(String.format("%" + maxTableNameLength + "s   %s", "", SqlSchemaUtils.getComment(table)))
                logger.info(
                    String.format(
                        "%" + maxTableNameLength + "s : (%s)",
                        tableName,
                        tableColumns.joinToString(", ") { column ->
                            "${SqlSchemaUtils.getColumnDbDataType(column)} ${SqlSchemaUtils.getColumnName(column)}"
                        }
                    ))
            }
            logger.info("")
            logger.info("")

            logger.info("----------------开始字段扫描----------------")
            logger.info("")

            // 解析表关系
            for (table in tableMap.values) {
                val tableName = SqlSchemaUtils.getTableName(table)
                val tableColumns = columnsMap[tableName]!!

                logger.info("开始解析表关系:$tableName")
                val relationTable = resolveRelationTable(table, tableColumns)

                for ((key, value) in relationTable) {
                    if (!relations.containsKey(key)) {
                        relations[key] = value
                    } else {
                        val existingRelations = relations[key]!!.toMutableMap()
                        existingRelations.putAll(value)
                        relations[key] = existingRelations
                    }
                }

                tablePackageMap[tableName] =
                    resolveEntityFullPackage(table, ext.basePackage.get(), getDomainModulePath())
                logger.info("结束解析表关系:$tableName")
                logger.info("")
            }

            // 解析枚举配置
            for (table in tableMap.values) {
                if (isIgnoreTable(table)) {
                    continue
                }
                val tableName = SqlSchemaUtils.getTableName(table)
                val tableColumns = columnsMap[tableName]!!

                for (column in tableColumns) {
                    if (SqlSchemaUtils.hasEnum(column) && !isIgnoreColumn(column)) {
                        val enumConfig = SqlSchemaUtils.getEnum(column)
                        if (enumConfig.isNotEmpty()) {
                            val enumType = SqlSchemaUtils.getType(column)
                            enumConfigMap[enumType] = enumConfig

                            var enumPackage =
                                if (templateNodeMap.containsKey("enum") && templateNodeMap["enum"]!!.isNotEmpty()) {
                                    templateNodeMap["enum"]!![0].name!!
                                } else {
                                    DEFAULT_ENUM_PACKAGE
                                }

                            if (enumPackage.isNotBlank()) {
                                enumPackage = ".$enumPackage"
                            }

                            enumPackageMap[enumType] =
                                "${ext.basePackage.get()}.${resolveEntityPackage(tableName)}$enumPackage"
                            enumTableNameMap[enumType] = tableName
                        }
                    }
                }
            }
            logger.info("----------------完成字段扫描----------------")
            logger.info("")
            logger.info("")

            // 生成枚举
            logger.info("----------------开始生成枚举----------------")
            logger.info("")
            for ((enumType, enumConfig) in enumConfigMap) {
                try {
                    writeEnumSourceFile(
                        enumConfig, enumType, ext.generation.enumValueField.get(),
                        ext.generation.enumNameField.get(), tablePackageMap, getDomainModulePath()
                    )
                } catch (e: Exception) {
                    logger.error("生成枚举失败: $enumType", e)
                }
            }
            logger.info("----------------完成生成枚举----------------")
            logger.info("")
            logger.info("")

            // 生成实体
            logger.info("----------------开始生成实体----------------")
            logger.info("")

            if (ext.generation.generateSchema.get()) {
                try {
                    writeSchemaBaseSourceFile(getDomainModulePath())
                } catch (e: Exception) {
                    logger.error("生成Schema基类失败", e)
                }
            }

            for (table in tableMap.values) {
                val tableName = SqlSchemaUtils.getTableName(table)
                val tableColumns = columnsMap[tableName]!!

                try {
                    buildEntitySourceFile(
                        table,
                        tableColumns,
                        tablePackageMap,
                        relations,
                        ext.basePackage.get(),
                        getDomainModulePath()
                    )
                } catch (e: Exception) {
                    logger.error("生成实体失败: $tableName", e)
                }
            }
            logger.info("----------------完成生成实体----------------")
            logger.info("")

        } catch (e: Exception) {
            logger.error("生成实体类失败", e)
            throw e
        }
    }

    /**
     * 获取聚合根文件目录
     *
     */
    fun resolveAggregatesPath(): String {
        if (aggregatesPath.isNotBlank()) return aggregatesPath

        return SourceFileUtils.resolvePackageDirectory(
            getDomainModulePath(),
            "${extension.get().basePackage.get()}.${AGGREGATE_PACKAGE}"
        )
    }

    /**
     * 获取聚合根包名，不包含basePackage
     */
    fun resolveAggregatesPackage(): String {
        return SourceFileUtils.resolvePackage(
            "${resolveAggregatesPath()}${File.separator}X.kt"
        ).substring(extension.get().basePackage.get().length + 1)
    }

    /**
     * 获取实体schema文件目录
     *
     */
    fun resolveSchemaPath(): String? {
        if (schemaPath.isNotBlank()) {
            return schemaPath
        }
        return SourceFileUtils.resolvePackageDirectory(
            getDomainModulePath(),
            "${extension.get().basePackage.get()}.${getEntitySchemaOutputPackage()}"
        )
    }

    /**
     * 获取schema包名，不包含basePackage
     *
     */
    fun resolveSchemaPackage(): String {
        return SourceFileUtils.resolvePackage(
            "${resolveSchemaPath()}${File.separator}X.kt"
        ).substring(
            if (extension.get().basePackage.get().isBlank()) 0 else (extension.get().basePackage.get().length + 1)
        )
    }

    /**
     * 获取领域事件订阅者文件目录
     *
     */
    fun resolveSubscriberPath(): String? {
        if (subscriberPath.isNotBlank()) {
            return subscriberPath
        }
        return SourceFileUtils.resolvePackageDirectory(
            getApplicationModulePath(),
            "${extension.get().basePackage.get()}.${DOMAIN_EVENT_SUBSCRIBER_PACKAGE}"
        )
    }

    /**
     * 获取领域事件订阅者包名，不包含basePackage
     *
     */
    fun resolveSubscriberPackage(): String {
        return SourceFileUtils.resolvePackage(
            "${resolveSubscriberPath()}${File.separator}X.kt"
        ).substring(extension.get().basePackage.get().length + 1)
    }

    /**
     * 获取模块
     */
    private fun resolveModule(tableName: String): String {
        return tableModuleMap.computeIfAbsent(tableName) {
            var currentTable: Map<String, Any?>? = tableMap[tableName]!!

            var module = SqlSchemaUtils.getModule(currentTable!!)

            logger.info("尝试解析模块: $tableName ${if (module.isBlank()) "[缺失]" else module}")

            while (!SqlSchemaUtils.isAggregateRoot(currentTable!!) && module.isBlank()) {
                val parent = SqlSchemaUtils.getParent(currentTable)
                if (parent.isBlank()) {
                    break
                }
                currentTable = tableMap[parent]
                if (currentTable == null) {
                    logger.error("表 $tableName @Parent 注解值填写错误，不存在表名为 $parent 的表")
                    break
                }
                module = SqlSchemaUtils.getModule(currentTable)
                logger.info("尝试父表模块: ${SqlSchemaUtils.getTableName(currentTable)} ${if (module.isBlank()) "[缺失]" else module}")
            }

            logger.info("模块解析结果: $tableName ${if (module.isBlank()) "[无]" else module}")
            module
        }
    }

    /**
     * 获取聚合
     * 格式: 模块.聚合
     */
    private fun resolveAggregate(tableName: String): String {
        return tableAggregateMap.computeIfAbsent(tableName) {
            var currentTable: Map<String, Any?>? = tableMap[tableName]!!
            var aggregate = SqlSchemaUtils.getAggregate(currentTable!!)
            var aggregateRootTableName = tableName

            logger.info("尝试解析聚合: $tableName ${if (aggregate.isBlank()) "[缺失]" else aggregate}")

            while (!SqlSchemaUtils.isAggregateRoot(currentTable!!) && aggregate.isBlank()) {
                val parent = SqlSchemaUtils.getParent(currentTable)
                if (parent.isBlank()) {
                    break
                }
                currentTable = tableMap[parent]
                if (currentTable == null) {
                    logger.error("表 $tableName @Parent 注解值填写错误，不存在表名为 $parent 的表")
                    break
                }
                aggregateRootTableName = SqlSchemaUtils.getTableName(currentTable)
                aggregate = SqlSchemaUtils.getAggregate(currentTable)
                logger.info("尝试父表聚合: ${aggregateRootTableName} ${if (aggregate.isBlank()) "[缺失]" else aggregate}")
            }

            if (aggregate.isBlank()) {
                aggregate = NamingUtils.toSnakeCase(resolveEntityType(aggregateRootTableName)) ?: ""
            }

            logger.info("聚合解析结果: $tableName ${if (aggregate.isBlank()) "[缺失]" else aggregate}")
            aggregate
        }
    }

    /**
     * 获取聚合名称
     * 格式: 模块.聚合
     *
     * @param tableName
     * @return
     */
    fun resolveAggregateWithModule(tableName: String): String {
        val module = resolveModule(tableName)
        if (module.isNotBlank()) {
            return SourceFileUtils.concatPackage(
                module,
                resolveAggregate(tableName)
            )
        } else {
            return resolveAggregate(tableName)
        }
    }

    /**
     * 获取实体类 Class.SimpleName
     */
    fun resolveEntityType(tableName: String): String {
        return entityTypeMap.computeIfAbsent(tableName) {
            val table = tableMap[tableName]!!
            var type = SqlSchemaUtils.getType(table)
            if (type.isBlank()) {
                type = NamingUtils.toUpperCamelCase(tableName) ?: ""
            }
            if (type.isNotBlank()) {
                logger.info("解析实体类名: ${SqlSchemaUtils.getTableName(table)} --> $type")
                type
            } else {
                throw RuntimeException("实体类名未生成")
            }
        }
    }

    /**
     * 获取实体类所在包，不包含basePackage
     */
    fun resolveEntityPackage(tableName: String): String {
        val module = resolveModule(tableName)
        val aggregate = resolveAggregate(tableName)
        return SourceFileUtils.concatPackage(
            resolveAggregatesPackage(),
            module,
            aggregate.lowercase()
        )
    }

    fun isReservedColumn(column: Map<String, Any?>): Boolean {
        val columnName: String =
            SqlSchemaUtils.getColumnName(column).lowercase(Locale.getDefault())
        val isReserved = getExtension().generation.versionField.get().equals(columnName, ignoreCase = true)
        return isReserved
    }

    fun isReadOnlyColumn(column: Map<String, Any?>): Boolean {
        if (SqlSchemaUtils.hasReadOnly(column)) {
            return true
        }
        val columnName: String =
            SqlSchemaUtils.getColumnName(column).lowercase(Locale.getDefault())
        if (getExtension().generation.readonlyFields.get().isNotBlank()
            && Arrays.stream<String>(
                getExtension().generation.readonlyFields.get().lowercase(Locale.getDefault())
                    .split(PATTERN_SPLITTER.toRegex())
                    .dropLastWhile { it.isEmpty() }.toTypedArray()
            )
                .anyMatch { c: String? -> columnName.matches(c!!.replace("%", ".*").toRegex()) }
        ) {
            return true
        }

        return false
    }

    fun isIgnoreTable(table: Map<String, Any?>): Boolean = SqlSchemaUtils.isIgnore(table)

    fun isIgnoreColumn(column: Map<String, Any?>): Boolean {
        if (SqlSchemaUtils.isIgnore(column)) {
            return true
        }
        val columnName: String =
            SqlSchemaUtils.getColumnName(column).lowercase(Locale.getDefault())
        return getExtension().generation.ignoreFields.get().map { it.lowercase().split(PATTERN_SPLITTER) }.flatten()
            .any { columnName.matches(it.replace("%", ".*").toRegex()) }
    }

    /**
     * 判断是否需要生成实体字段
     *
     * @param table
     * @param column
     * @param relations
     * @return
     */
    fun isColumnNeedGenerate(
        table: Map<String, Any?>,
        column: Map<String, Any?>,
        relations: Map<String, Map<String, String?>?>,
    ): Boolean {
        val tableName: String = SqlSchemaUtils.getTableName(table)
        val columnName: String = SqlSchemaUtils.getColumnName(column)
        if (isIgnoreColumn(column)) {
            return false
        }
        if (isReservedColumn(column)) {
            return false
        }

        if (!SqlSchemaUtils.isAggregateRoot(table)) {
            if (columnName.equals(
                    SqlSchemaUtils.getParent(table) + "_id",
                    ignoreCase = true
                )
            ) {
                return false
            }
        }

        if (relations.containsKey(tableName)) {
            for (entry in relations.get(tableName)!!.entries) {
                val refInfos = entry.value!!.split(";".toRegex()).dropLastWhile { it.isEmpty() }.toTypedArray()
                when (refInfos[0]) {
                    "ManyToOne", "OneToOne" -> if (columnName.equals(refInfos[1], ignoreCase = true)) {
                        return false
                    }

                    else -> {}
                }
            }
        }
        return true
    }

    /**
     * 获取ID列
     */
    private fun resolveIdColumns(columns: List<Map<String, Any?>>): List<Map<String, Any?>> {
        return columns.filter { SqlSchemaUtils.isColumnPrimaryKey(it) }
    }

    /**
     * 是否Id列
     *
     * @param column
     * @return
     */
    private fun isIdColumn(column: Map<String, Any?>): Boolean {
        return SqlSchemaUtils.isColumnPrimaryKey(column)
    }

    /**
     * 是否Version列
     *
     * @param column
     * @return
     */
    private fun isVersionColumn(column: Map<String, Any?>): Boolean {
        return SqlSchemaUtils.getColumnName(column) == getExtension().generation.versionField.get()
    }

    /**
     * 获取指定列
     *
     * @param columns
     * @param columnName
     * @return
     */
    private fun resolveColumn(
        columns: List<Map<String, Any?>>,
        columnName: String?,
    ): Map<String, Any?>? {
        return columns.firstOrNull() {
            columnName == SqlSchemaUtils.getColumnName(it)
        }
    }

    /**
     * 生成字段注释
     *
     * @param column
     * @return
     */
    fun generateFieldComment(column: Map<String, Any?>): MutableList<String> {
        val comments: MutableList<String> = ArrayList<String>()
        val fieldName: String = SqlSchemaUtils.getColumnName(column)
        val fieldType: String? = SqlSchemaUtils.getColumnType(column)
        comments.add("/**")
        for (comment in SqlSchemaUtils.getComment(column)
            .split(PATTERN_LINE_BREAK.toRegex()).dropLastWhile { it.isEmpty() }.toTypedArray()) {
            if (comment.isEmpty()) {
                continue
            }
            comments.add(" * " + comment)
            if (SqlSchemaUtils.hasEnum(column)) {
                logger.info("获取枚举java类型：" + fieldName + " -> " + fieldType)
                var enumMap = enumConfigMap.get(fieldType)
                if (enumMap == null) {
                    enumMap = enumConfigMap.get(SqlSchemaUtils.getType(column))
                }
                if (enumMap != null) {
                    comments.addAll(
                        enumMap.entries.stream()
                            .map { " * " + it!!.key + ":" + it.value[0] + ":" + it.value[1] }
                            .collect(Collectors.toList())
                    )
                }
            }
        }
        if (fieldName == getExtension().generation.versionField.get()) {
            comments.add(" * 数据版本（支持乐观锁）")
        }
        if (getExtension().generation.generateDbType.get()) {
            comments.add(" * " + SqlSchemaUtils.getColumnDbType(column))
        }
        comments.add(" */")
        return comments
    }

    /**
     * 解析实体类全路径包名，含basePackage
     */
    fun resolveEntityFullPackage(table: Map<String, Any?>, basePackage: String, baseDir: String): String {
        val tableName = SqlSchemaUtils.getTableName(table)
        val packageName = SourceFileUtils.concatPackage(basePackage, resolveEntityPackage(tableName))
        return packageName
    }

    fun resolveRelationTable(
        table: Map<String, Any?>,
        columns: List<Map<String, Any?>>,
    ): Map<String, Map<String, String>> {
        val result = mutableMapOf<String, MutableMap<String, String>>()
        val tableName = SqlSchemaUtils.getTableName(table)

        if (isIgnoreTable(table)) return result

        // 聚合内部关系 OneToMany
        // OneToOne关系也用OneToMany实现，避免持久化存储结构变更
        if (!SqlSchemaUtils.isAggregateRoot(table)) {
            val parent = SqlSchemaUtils.getParent(table)
            result.putIfAbsent(parent, mutableMapOf())

            var rewrited = false // 是否显式声明引用字段
            columns.forEach { column ->
                if (SqlSchemaUtils.hasReference(column)) {
                    if (parent.equals(SqlSchemaUtils.getReference(column), ignoreCase = true)) {
                        val lazy = SqlSchemaUtils.isLazy(
                            column,
                            "LAZY".equals(getExtension().generation.fetchType.get(), ignoreCase = true)
                        )
                        result[parent]!!.putIfAbsent(
                            tableName,
                            "OneToMany;${SqlSchemaUtils.getColumnName(column)}${if (lazy) ";LAZY" else ""}"
                        )
                        if (getExtension().generation.generateParent.get()) {
                            result.putIfAbsent(tableName, mutableMapOf())
                            result[tableName]!!.putIfAbsent(
                                parent,
                                "*ManyToOne;${SqlSchemaUtils.getColumnName(column)}${if (lazy) ";LAZY" else ""}"
                            )
                        }
                        rewrited = true
                    }
                }
            }
            if (!rewrited) {
                val column =
                    columns.firstOrNull { SqlSchemaUtils.getColumnName(it).equals("${parent}_id", ignoreCase = true) }
                if (column != null) {
                    val lazy = SqlSchemaUtils.isLazy(
                        column,
                        "LAZY".equals(getExtension().generation.fetchType.get(), ignoreCase = true)
                    )
                    result[parent]!!.putIfAbsent(
                        tableName,
                        "OneToMany;${SqlSchemaUtils.getColumnName(column)}${if (lazy) ";LAZY" else ""}"
                    )
                    if (getExtension().generation.generateParent.get()) {
                        result.putIfAbsent(tableName, mutableMapOf())
                        result[tableName]!!.putIfAbsent(
                            parent,
                            "*ManyToOne;${SqlSchemaUtils.getColumnName(column)}${if (lazy) ";LAZY" else ""}"
                        )
                    }
                }
            }
        }

        // 聚合之间关系
        if (SqlSchemaUtils.hasRelation(table)) {
            // ManyToMany
            var owner = ""
            var beowned = ""
            var joinCol = ""
            var inverseJoinColumn = ""
            var ownerLazy = false

            columns.forEach { column ->
                if (SqlSchemaUtils.hasReference(column)) {
                    val refTableName = SqlSchemaUtils.getReference(column)
                    result.putIfAbsent(refTableName, mutableMapOf())
                    val lazy = SqlSchemaUtils.isLazy(
                        column,
                        "LAZY".equals(getExtension().generation.fetchType.get(), ignoreCase = true)
                    )
                    if (owner.isEmpty()) {
                        ownerLazy = lazy
                        owner = refTableName
                        joinCol = SqlSchemaUtils.getColumnName(column)
                    } else {
                        beowned = refTableName
                        inverseJoinColumn = SqlSchemaUtils.getColumnName(column)
                        result[beowned]!!.putIfAbsent(
                            owner,
                            "*ManyToMany;$inverseJoinColumn${if (lazy) ";LAZY" else ""}"
                        )
                    }
                }
            }
            if (owner.isNotEmpty() && beowned.isNotEmpty()) {
                result[owner]!!.putIfAbsent(
                    beowned,
                    "ManyToMany;$joinCol;$inverseJoinColumn;$tableName${if (ownerLazy) ";LAZY" else ""}"
                )
            }
        }

        // 处理显式关系配置
        columns.forEach { column ->
            val colRel = SqlSchemaUtils.getRelation(column)
            val colName = SqlSchemaUtils.getColumnName(column)
            var refTableName: String? = null
            val lazy = SqlSchemaUtils.isLazy(
                column,
                "LAZY".equals(getExtension().generation.fetchType.get(), ignoreCase = true)
            )

            if (colRel.isNotEmpty() || SqlSchemaUtils.hasReference(column)) {
                when (colRel) {
                    "OneToOne", "1:1" -> {
                        refTableName = SqlSchemaUtils.getReference(column)
                        result.putIfAbsent(tableName, mutableMapOf())
                        result[tableName]!!.putIfAbsent(
                            refTableName,
                            "OneToOne;$colName${if (lazy) ";LAZY" else ""}"
                        )
                    }

                    "ManyToOne", "*:1" -> {
                        refTableName = SqlSchemaUtils.getReference(column)
                        result.putIfAbsent(tableName, mutableMapOf())
                        result[tableName]!!.putIfAbsent(
                            refTableName,
                            "ManyToOne;$colName${if (lazy) ";LAZY" else ""}"
                        )
                    }

                    else -> {
                        // 默认处理为 ManyToOne
                        if (SqlSchemaUtils.hasReference(column)) {
                            refTableName = SqlSchemaUtils.getReference(column)
                            result.putIfAbsent(tableName, mutableMapOf())
                            result[tableName]!!.putIfAbsent(
                                refTableName,
                                "ManyToOne;$colName${if (lazy) ";LAZY" else ""}"
                            )
                        }
                    }
                }
            }
        }

        return result
    }

    fun readEntityCustomerSourceFile(
        filePath: String,
        importLines: MutableList<String>,
        annotationLines: MutableList<String>,
        customerLines: MutableList<String>,
    ): Boolean {
        val file = File(filePath)
        if (file.exists()) {
            val content = file.readText(charset(getExtension().outputEncoding.get()))
            val lines = content.replace("\r\n", "\n").split("\n")

            var startMapperLine = 0
            var endMapperLine = 0
            var startClassLine = 0

            for (i in 1 until lines.size) {
                val line = lines[i]
                when {
                    line.contains("【字段映射开始】") -> {
                        startMapperLine = i
                    }

                    line.contains("【字段映射结束】") -> {
                        endMapperLine = i
                    }

                    line.trim().startsWith("class") && startClassLine == 0 -> {
                        startClassLine = i
                    }

                    (line.trim().startsWith("@") || annotationLines.isNotEmpty()) && startClassLine == 0 -> {
                        annotationLines.add(line)
                        logger.debug("[annotation] $line")
                    }

                    annotationLines.isEmpty() && startClassLine == 0 -> {
                        importLines.add(line)
                        logger.debug("[import] $line")
                    }

                    startMapperLine == 0 || endMapperLine > 0 -> {
                        customerLines.add(line)
                    }
                }
            }

            // 处理customerLines，移除末尾的大括号
            for (i in customerLines.size - 1 downTo 0) {
                val line = customerLines[i]
                if (line.contains("}")) {
                    customerLines.removeAt(i)
                    if (!line.equals("}", ignoreCase = true)) {
                        customerLines.add(i, line.substring(0, line.lastIndexOf("}")))
                    }
                    break
                }
                customerLines.removeAt(i)
            }

            customerLines.forEach { line ->
                logger.debug("[customer] $line")
            }

            if (startMapperLine == 0 || endMapperLine == 0) {
                return false
            }

            file.delete()
        }
        return true
    }

    fun processImportLines(table: Map<String, Any?>, importLines: MutableList<String>, content: String) {
        val importEmpty = importLines.isEmpty()
        if (importEmpty) {
            importLines.add("")
        }

        val entityClassExtraImports = getEntityClassExtraImports().toMutableList()
        if (SqlSchemaUtils.isValueObject(table)) {
            val idx = entityClassExtraImports.indexOf("com.only4.cap4k.ddd.core.domain.aggregate.annotation.Aggregate")
            if (idx > 0) {
                entityClassExtraImports.add(idx, "com.only4.cap4k.ddd.core.domain.aggregate.ValueObject")
            } else {
                entityClassExtraImports.add("com.only4.cap4k.ddd.core.domain.aggregate.ValueObject")
            }
        }

        if (importEmpty) {
            var breakLine = false
            for (entityClassExtraImport in entityClassExtraImports) {
                if (entityClassExtraImport.startsWith("jakarta") && !breakLine) {
                    breakLine = true
                    importLines.add("")
                }
                importLines.add("import $entityClassExtraImport")
            }
            importLines.add("")
            importLines.add("/**")

            val tableComment = SqlSchemaUtils.getComment(table)
            for (comment in tableComment.split(Regex(PATTERN_LINE_BREAK))) {
                if (comment.isEmpty()) {
                    continue
                }
                importLines.add(" * $comment")
            }
            importLines.add(" *")
            importLines.add(" * 本文件由[cap4k-ddd-codegen-gradle-plugin]生成")
            importLines.add(" * 警告：请勿手工修改该文件的字段声明，重新生成会覆盖字段声明")
            importLines.add(" * @author cap4k-ddd-codegen")
            importLines.add(" * @date ${LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyy/MM/dd"))}")
            importLines.add(" */")
        } else {
            for (entityClassExtraImport in entityClassExtraImports) {
                SourceFileUtils.addIfNone(
                    importLines,
                    """\s*import\s+${entityClassExtraImport}\s*""",
                    "import $entityClassExtraImport"
                ) { list, line ->
                    val firstLargeLine = list.firstOrNull { l -> l.isNotEmpty() && l > line }
                    if (firstLargeLine != null) {
                        list.indexOf(firstLargeLine)
                    } else {
                        val imports =
                            list.filter { l -> l.isNotEmpty() && !l.contains(" java") && l.startsWith("import") }
                        if (imports.isNotEmpty()) {
                            list.indexOf(imports.last()) + 1
                        } else {
                            list.size
                        }
                    }
                }
            }
        }

        // 移除未使用的 Hibernate 注解导入
        var i = 0
        while (i < importLines.size) {
            val importLine = importLines[i]
            if (importLine.contains(" org.hibernate.annotations.") && !importLine.contains("*")) {
                val hibernateAnnotation = importLine.substring(importLine.lastIndexOf(".") + 1).replace(";", "").trim()
                if (!content.contains(hibernateAnnotation)) {
                    importLines.removeAt(i)
                    continue
                }
            }
            i++
        }
    }

    fun processAnnotationLines(
        table: Map<String, Any?>,
        columns: List<Map<String, Any?>>,
        annotationLines: MutableList<String>,
    ) {
        val tableName = SqlSchemaUtils.getTableName(table)
        val annotationEmpty = annotationLines.isEmpty()

        // 移除并重新添加 @Aggregate 注解
        SourceFileUtils.removeText(annotationLines, """@Aggregate\(.*\)""")
        SourceFileUtils.addIfNone(
            annotationLines,
            """@Aggregate\(.*\)""",
            """@Aggregate(aggregate = "${NamingUtils.toUpperCamelCase(resolveAggregateWithModule(tableName))}", name = "${
                resolveEntityType(
                    tableName
                )
            }", root = ${SqlSchemaUtils.isAggregateRoot(table)}, type = ${if (SqlSchemaUtils.isValueObject(table)) "Aggregate.TYPE_VALUE_OBJECT" else "Aggregate.TYPE_ENTITY"}, description = "${
                SqlSchemaUtils.getComment(
                    table
                ).replace(Regex(PATTERN_LINE_BREAK), "\\\\n")
            }")"""
        ) { _, _ -> 0 }

        // 处理聚合根注解
        val aggregateRootAnnotation = getAggregateRootAnnotation()
        if (aggregateRootAnnotation.isNotBlank()) {
            if (SqlSchemaUtils.isAggregateRoot(table)) {
                SourceFileUtils.addIfNone(
                    annotationLines,
                    """$aggregateRootAnnotation(\(.*\))?""",
                    aggregateRootAnnotation
                )
            } else {
                SourceFileUtils.removeText(annotationLines, """$aggregateRootAnnotation(\(.*\))?""")
                SourceFileUtils.removeText(annotationLines, """@AggregateRoot(\(.*\))?""")
            }
        }

        // 添加 JPA 基本注解
        SourceFileUtils.addIfNone(annotationLines, """@Entity(\(.*\))?""", "@Entity")

        val ids = resolveIdColumns(columns)
        if (ids.size > 1) {
            SourceFileUtils.addIfNone(
                annotationLines,
                """@IdClass(\(.*\))""",
                "@IdClass(${resolveEntityType(tableName)}.${DEFAULT_MUL_PRI_KEY_NAME}::class)"
            )
        }

        SourceFileUtils.addIfNone(
            annotationLines,
            """@Table(\(.*\))?""",
            "@Table(name = \"$LEFT_QUOTES_4_ID_ALIAS$tableName$RIGHT_QUOTES_4_ID_ALIAS\")"
        )

        SourceFileUtils.addIfNone(annotationLines, """@DynamicInsert(\(.*\))?""", "@DynamicInsert")
        SourceFileUtils.addIfNone(annotationLines, """@DynamicUpdate(\(.*\))?""", "@DynamicUpdate")

        // 处理软删除相关注解
        val ext = getExtension()
        val deletedField = ext.generation.deletedField.get()
        val versionField = ext.generation.versionField.get()

        if (deletedField.isNotBlank() && hasColumn(deletedField, columns)) {
            if (ids.isEmpty()) {
                throw RuntimeException("实体缺失【主键】：$tableName")
            }

            val idFieldName = if (ids.size == 1) {
                NamingUtils.toLowerCamelCase(SqlSchemaUtils.getColumnName(ids[0]))
                    ?: SqlSchemaUtils.getColumnName(ids[0])
            } else {
                "(${
                    ids.joinToString(", ") {
                        NamingUtils.toLowerCamelCase(SqlSchemaUtils.getColumnName(it)) ?: SqlSchemaUtils.getColumnName(
                            it
                        )
                    }
                })"
            }

            val idFieldValue = if (ids.size == 1) "?" else "(" + ids.joinToString(", ") { "?" } + ")"

            if (hasColumn(versionField, columns)) {
                SourceFileUtils.addIfNone(
                    annotationLines,
                    """@SQLDelete(\(.*\))?""",
                    """@SQLDelete(sql = "update $LEFT_QUOTES_4_ID_ALIAS$tableName$RIGHT_QUOTES_4_ID_ALIAS set $LEFT_QUOTES_4_ID_ALIAS$deletedField$RIGHT_QUOTES_4_ID_ALIAS = $LEFT_QUOTES_4_ID_ALIAS$idFieldName$RIGHT_QUOTES_4_ID_ALIAS where $LEFT_QUOTES_4_ID_ALIAS$idFieldName$RIGHT_QUOTES_4_ID_ALIAS = $idFieldValue and $LEFT_QUOTES_4_ID_ALIAS$versionField$RIGHT_QUOTES_4_ID_ALIAS = ?)"""
                )
            } else {
                SourceFileUtils.addIfNone(
                    annotationLines,
                    """@SQLDelete(\(.*\))?""",
                    """@SQLDelete(sql = "update $LEFT_QUOTES_4_ID_ALIAS$tableName$RIGHT_QUOTES_4_ID_ALIAS set $LEFT_QUOTES_4_ID_ALIAS$deletedField$RIGHT_QUOTES_4_ID_ALIAS = $LEFT_QUOTES_4_ID_ALIAS$idFieldName$RIGHT_QUOTES_4_ID_ALIAS where $LEFT_QUOTES_4_ID_ALIAS$idFieldName$RIGHT_QUOTES_4_ID_ALIAS = $idFieldValue)"""
                )
            }

//            if (hasColumn(versionField, columns) && !SourceFileUtils.hasLine(
//                    annotationLines,
//                    "@SQLDelete(\\(.*$versionField.*\\))"
//                )
//            ) {
//                SourceFileUtils.replaceText(
//                    annotationLines,
//                    "@SQLDelete(\\(.*\\))?",
//                    """
//                    "@SQLDelete(sql = "update $LEFT_QUOTES_4_ID_ALIAS$tableName$RIGHT_QUOTES_4_ID_ALIAS set $LEFT_QUOTES_4_ID_ALIAS$deletedField$RIGHT_QUOTES_4_ID_ALIAS = $LEFT_QUOTES_4_ID_ALIAS$idFieldName$RIGHT_QUOTES_4_ID_ALIAS where $LEFT_QUOTES_4_ID_ALIAS$idFieldName$RIGHT_QUOTES_4_ID_ALIAS = $idFieldValue)
//                    """.trimIndent()
//                )
//            }

            SourceFileUtils.addIfNone(
                annotationLines,
                """@Where(\(.*\))?""",
                """@Where(clause = "$LEFT_QUOTES_4_ID_ALIAS$deletedField$RIGHT_QUOTES_4_ID_ALIAS = 0"""
            )
        }
    }

    fun buildEntitySourceFile(
        table: Map<String, Any?>,
        columns: List<Map<String, Any?>>,
        tablePackageMap: Map<String, String>,
        relations: Map<String, Map<String, String>>,
        basePackage: String,
        baseDir: String,
    ) {
        val tableName = SqlSchemaUtils.getTableName(table)

        if (isIgnoreTable(table)) {
            logger.info("跳过忽略表：$tableName")
            return
        }

        if (SqlSchemaUtils.hasRelation(table)) {
            logger.info("跳过关系表：$tableName")
            return
        }

        val ids = resolveIdColumns(columns)
        if (ids.isEmpty()) {
            logger.error("跳过问题表：${tableName}缺失主键")
            return
        }

        val entityType = resolveEntityType(tableName)
        val entityFullPackage = tablePackageMap[tableName] ?: return

        // 创建输出目录
        File(SourceFileUtils.resolvePackageDirectory(baseDir, entityFullPackage)).mkdirs()

        val filePath = SourceFileUtils.resolveSourceFile(baseDir, entityFullPackage, entityType)

        val enums = mutableListOf<String>()
        val importLines = mutableListOf<String>()
        val annotationLines = mutableListOf<String>()
        val customerLines = mutableListOf<String>()

        if (!readEntityCustomerSourceFile(filePath, importLines, annotationLines, customerLines)) {
            logger.warn("文件被改动，无法自动更新！$filePath")
            return
        }

        processAnnotationLines(table, columns, annotationLines)
        val mainSource =
            writeEntityClass(table, columns, tablePackageMap, relations, enums, annotationLines, customerLines)
        processImportLines(table, importLines, mainSource)

        logger.info("开始生成实体文件：$filePath")

        val file = File(filePath)
        if (!file.exists() || !file.readText(charset(getExtension().outputEncoding.get()))
                .contains(FLAG_DO_NOT_OVERWRITE)
        ) {
            file.writeText(
                """package $entityFullPackage
                |${importLines.joinToString("\n")}
                |$mainSource""".trimMargin(),
                charset(getExtension().outputEncoding.get())
            )
        }

        val ext = getExtension()
        if (ext.generation.generateSchema.get()) {
            writeSchemaSourceFile(table, columns, tablePackageMap, relations, basePackage, baseDir)
        }

        if (SqlSchemaUtils.isAggregateRoot(table)) {
            if (ext.generation.generateAggregate.get()) {
                writeAggregateSourceFile(table, columns, tablePackageMap, baseDir)
            }
            if (SqlSchemaUtils.hasFactory(table) || ext.generation.generateAggregate.get()) {
                writeFactorySourceFile(table, tablePackageMap, baseDir)
            }
            if (SqlSchemaUtils.hasSpecification(table) || ext.generation.generateAggregate.get()) {
                writeSpecificationSourceFile(table, tablePackageMap, baseDir)
            }
            if (SqlSchemaUtils.hasDomainEvent(table)) {
                val domainEvents = SqlSchemaUtils.getDomainEvents(table)
                for (domainEvent in domainEvents) {
                    if (domainEvent.isBlank()) {
                        continue
                    }
                    val segments = domainEvent.split(":")
                    val domainEventClassName = generateDomainEventName(segments[0])
                    val domainEventDescription = if (segments.size > 1) segments[1] else "todo: 领域事件说明"
                    writeDomainEventSourceFile(
                        table,
                        tablePackageMap,
                        domainEventClassName,
                        domainEventDescription,
                        baseDir
                    )
                }
            }
        }
    }

    fun writeEntityClass(
        table: Map<String, Any?>,
        columns: List<Map<String, Any?>>,
        tablePackageMap: Map<String, String>,
        relations: Map<String, Map<String, String>>,
        enums: MutableList<String>,
        annotationLines: List<String>,
        customerLines: List<String>,
    ): String {
        val tableName = SqlSchemaUtils.getTableName(table)
        val entityType = resolveEntityType(tableName)
        val ids = resolveIdColumns(columns)

        if (ids.isEmpty()) {
            throw RuntimeException("实体缺失【主键】：$tableName")
        }

        val identityType = if (ids.size != 1) "Long" else SqlSchemaUtils.getColumnType(ids[0])

        val stringWriter = StringWriter()
        val out = BufferedWriter(stringWriter)

        // Write annotation lines
        annotationLines.forEach { line -> SourceFileUtils.writeLine(out, line) }

        // Determine base class
        var baseClass: String? = null
        when {
            SqlSchemaUtils.isAggregateRoot(table) && getExtension().generation.rootEntityBaseClass.get()
                .isNotBlank() -> {
                baseClass = getExtension().generation.rootEntityBaseClass.get()
            }

            getExtension().generation.entityBaseClass.get().isNotBlank() -> {
                baseClass = getExtension().generation.entityBaseClass.get()
            }
        }

        baseClass?.let {
            baseClass = it
                .replace("\${Entity}", entityType)
                .replace("\${IdentityType}", identityType)
        }

        // Write class declaration (adapted for Kotlin)
        val extendsClause = if (baseClass?.isNotBlank() == true) " : $baseClass()" else ""
        val implementsClause = if (SqlSchemaUtils.isValueObject(table)) ", ValueObject<$identityType>" else ""

        SourceFileUtils.writeLine(out, "class $entityType$extendsClause$implementsClause (")

        SourceFileUtils.writeLine(
            out,
            "    // 【字段映射开始】本段落由[cap4k-ddd-codegen-gradle-plugin]维护，请不要手工改动"
        )

        // Write column properties
        for (column in columns) {
            writeColumnProperty(out, table, column, ids, relations, enums)
        }

        SourceFileUtils.writeLine(out, ") {")

        // Write relation properties
        writeRelationProperty(out, table, relations, tablePackageMap)

        SourceFileUtils.writeLine(out, "")
        SourceFileUtils.writeLine(
            out,
            "    // 【字段映射结束】本段落由[cap4k-ddd-codegen-gradle-plugin]维护，请不要手工改动"
        )

//         Write customer lines or default behavior methods
        if (customerLines.isNotEmpty()) {
            customerLines.forEach { line -> SourceFileUtils.writeLine(out, line) }
        } else {
            SourceFileUtils.writeLine(out, "")
            SourceFileUtils.writeLine(out, "    // 【行为方法开始】")
            SourceFileUtils.writeLine(out, "")
            SourceFileUtils.writeLine(out, "")
            SourceFileUtils.writeLine(out, "")
            SourceFileUtils.writeLine(out, "    // 【行为方法结束】")
            SourceFileUtils.writeLine(out, "")
        }

        SourceFileUtils.writeLine(out, "}")
        SourceFileUtils.writeLine(out, "")

        // Value object implementation
//        if (SqlSchemaUtils.isValueObject(table)) {
//            val idFieldName =
//                if (ids.size != 1) "" else NamingUtils.toLowerCamelCase(SqlSchemaUtils.getColumnName(ids[0]))
//                    ?: SqlSchemaUtils.getColumnName(ids[0])
//            val idTypeName = if (ids.size != 1) "Long" else SqlSchemaUtils.getColumnType(ids[0])
//
//            val hashTemplate = when {
//                getExtension().generation.hashMethod4ValueObject.get().isNotBlank() -> {
//                    "    " + getExtension().generation.hashMethod4ValueObject.get().trim()
//                }
//
//                ids.size != 1 -> {
//                    """
//                    override fun hash(): Long {
//                        return ${resolveEntityIdGenerator(table)}.hash(this, "") as Long
//                    }""".trimIndent()
//                }
//
//                else -> {
//                    """
//                    override fun hash(): $idTypeName {
//                        if ($idFieldName == null) {
//                            $idFieldName = ${resolveEntityIdGenerator(table)}.hash(this, "$idFieldName") as $idTypeName
//                        }
//                        return $idFieldName!!
//                    }
//                    """.trimIndent()
//                }
//            }
//
//            SourceFileUtils.writeLine(out, "")
//            SourceFileUtils.writeLine(
//                out, hashTemplate
//                    .replace("\${idField}", idFieldName)
//                    .replace("\${IdField}", idFieldName)
//                    .replace("\${ID_FIELD}", idFieldName)
//                    .replace("\${id_field}", idFieldName)
//                    .replace("\${idTypeName}", idTypeName)
//                    .replace("\${IdType}", idTypeName)
//                    .replace("\${ID_TYPE}", idTypeName)
//                    .replace("\${id_type}", idTypeName)
//            )
//
//            SourceFileUtils.writeLine(out, "")
//            SourceFileUtils.writeLine(
//                out,
//                """
//                override fun equals(other: Any?): Boolean {
//                    if (other == null) {
//                        return false
//                    }
//                    if (other !is $entityType) {
//                        return false
//                    }
//                    return hashCode() == other.hashCode()
//                }
//
//                override fun hashCode(): Int {
//                    return hash().hashCode()
//                }
//                """.trimIndent()
//            )
//            SourceFileUtils.writeLine(out, "")
//        }

        // Multiple primary keys (adapted for Kotlin data class)
//        if (ids.size > 1) {
//            SourceFileUtils.writeLine(out, "")
//            SourceFileUtils.writeLine(out, "    data class $DEFAULT_MUL_PRI_KEY_NAME(")
//            ids.forEachIndexed { index, id ->
//                val columnName = SqlSchemaUtils.getColumnName(id)
//                val leftQuote = LEFT_QUOTES_4_ID_ALIAS.replace("\"", "\\\"")
//                val rightQuote = RIGHT_QUOTES_4_ID_ALIAS.replace("\"", "\\\"")
//                val type = SqlSchemaUtils.getColumnType(id)
//                val fieldName = NamingUtils.toLowerCamelCase(columnName) ?: columnName
//
//                SourceFileUtils.writeLine(out, "        @Column(name = \"$leftQuote$columnName$rightQuote\")")
//                val suffix = if (index == ids.size - 1) "" else ","
//                SourceFileUtils.writeLine(out, "        val $fieldName: $type$suffix")
//            }
//            SourceFileUtils.writeLine(out, "    ) : java.io.Serializable")
//        }

        out.flush()
        out.close()
        return stringWriter.toString()
    }

    fun resolveEntityIdGenerator(table: Map<String, Any?>): String {
        return when {
            SqlSchemaUtils.hasIdGenerator(table) -> {
                SqlSchemaUtils.getIdGenerator(table)
            }

            SqlSchemaUtils.isValueObject(table) -> {
                getExtension().generation.idGenerator4ValueObject.get().ifBlank {
                    // ValueObject 值对象 默认使用MD5
                    "com.only4.cap4k.ddd.domain.repo.Md5HashIdentifierGenerator"
                }
            }

            else -> {
                getExtension().generation.idGenerator.get().ifBlank {
                    ""
                }
            }
        }
    }

    fun writeColumnProperty(
        out: BufferedWriter,
        table: Map<String, Any?>,
        column: Map<String, Any?>,
        ids: List<Map<String, Any?>>,
        relations: Map<String, Map<String, String>>,
        enums: MutableList<String>,
    ) {
        val columnName = SqlSchemaUtils.getColumnName(column)
        val columnType = SqlSchemaUtils.getColumnType(column)

        if (!isColumnNeedGenerate(
                table,
                column,
                relations
            ) && columnName != getExtension().generation.versionField.get()
        ) {
            return
        }

        var updatable = true
        var insertable = true

        if (SqlSchemaUtils.getColumnType(column).contains("Date")) {
            updatable = !SqlSchemaUtils.isAutoUpdateDateColumn(column)
            insertable = !SqlSchemaUtils.isAutoInsertDateColumn(column)
        }

        if (isReadOnlyColumn(column)) {
            insertable = false
            updatable = false
        }

        if (SqlSchemaUtils.hasIgnoreInsert(column)) {
            insertable = false
        }

        if (SqlSchemaUtils.hasIgnoreUpdate(column)) {
            updatable = false
        }

        SourceFileUtils.writeLine(out, "")
        writeFieldComment(out, column)

        // ID annotation
        if (isIdColumn(column)) {
            SourceFileUtils.writeLine(out, "    @Id")
            if (ids.size == 1) {
                val entityIdGenerator = resolveEntityIdGenerator(table)
                when {
                    SqlSchemaUtils.isValueObject(table) -> {
                        // 不使用ID生成器
                    }

                    entityIdGenerator.isNotEmpty() -> {
                        SourceFileUtils.writeLine(out, "    @GeneratedValue(generator = \"$entityIdGenerator\")")
                        SourceFileUtils.writeLine(
                            out,
                            "    @GenericGenerator(name = \"$entityIdGenerator\", strategy = \"$entityIdGenerator\")"
                        )
                    }

                    else -> {
                        // 无ID生成器 使用数据库自增
                        SourceFileUtils.writeLine(out, "    @GeneratedValue(strategy = GenerationType.IDENTITY)")
                    }
                }
            }
        }

        // Version annotation
        if (isVersionColumn(column)) {
            SourceFileUtils.writeLine(out, "    @Version")
        }

        // Enum converter annotation
        if (SqlSchemaUtils.hasEnum(column)) {
            enums.add(columnType)
            SourceFileUtils.writeLine(out, "    @Convert(converter = $columnType.Converter::class)")
        }

        // Column annotation
        val leftQuote = LEFT_QUOTES_4_ID_ALIAS.replace("\"", "\\\"")
        val rightQuote = RIGHT_QUOTES_4_ID_ALIAS.replace("\"", "\\\"")

        if (!updatable || !insertable) {
            SourceFileUtils.writeLine(
                out,
                "    @Column(name = \"$leftQuote$columnName$rightQuote\", insertable = $insertable, updatable = $updatable)"
            )
        } else {
            SourceFileUtils.writeLine(out, "    @Column(name = \"$leftQuote$columnName$rightQuote\")")
        }

        // Property declaration with default value if needed
        val fieldName = NamingUtils.toLowerCamelCase(columnName) ?: columnName
        val defaultJavaLiteral = SqlSchemaUtils.getColumnDefaultLiteral(column)
        val defaultValue = " = $defaultJavaLiteral"
        SourceFileUtils.writeLine(out, "    var $fieldName: $columnType$defaultValue,")
    }

    fun writeFieldComment(
        out: BufferedWriter,
        column: Map<String, Any?>,
    ) {
        val comments = generateFieldComment(column)
        for (line in comments) {
            SourceFileUtils.writeLine(out, "    $line")
        }
    }

    fun writeRelationProperty(
        out: BufferedWriter,
        table: Map<String, Any?>,
        relations: Map<String, Map<String, String>>,
        tablePackageMap: Map<String, String>,
    ) {
        val tableName = SqlSchemaUtils.getTableName(table)

        if (!relations.containsKey(tableName)) {
            return
        }

        for ((refTableName, relationInfo) in relations[tableName]!!) {
            val refInfos = relationInfo.split(";")
            val navTable = tableMap[refTableName] ?: continue

            val fetchType = when {
                relationInfo.endsWith(";LAZY") -> "LAZY"
                SqlSchemaUtils.hasLazy(navTable) -> if (SqlSchemaUtils.isLazy(navTable, false)) "LAZY" else "EAGER"
                else -> "EAGER"
            }

            val relation = refInfos[0]
            val joinColumn = refInfos[1]
            val fetchAnnotation = "" // For Kotlin, we might not need Hibernate fetch annotations

            SourceFileUtils.writeLine(out, "")

            when (relation) {
                "OneToMany" -> {
                    // 专属聚合内关系
                    SourceFileUtils.writeLine(
                        out,
                        "    @${relation}(cascade = [CascadeType.ALL], fetch = FetchType.$fetchType, orphanRemoval = true)$fetchAnnotation"
                    )
                    SourceFileUtils.writeLine(out, "    @Fetch(FetchMode.SUBSELECT)")
                    val leftQuote = LEFT_QUOTES_4_ID_ALIAS.replace("\"", "\\\"")
                    val rightQuote = RIGHT_QUOTES_4_ID_ALIAS.replace("\"", "\\\"")
                    SourceFileUtils.writeLine(
                        out,
                        "    @JoinColumn(name = \"$leftQuote$joinColumn$rightQuote\", nullable = false)"
                    )

                    val countIsOne = SqlSchemaUtils.countIsOne(navTable)

                    val fieldName = Inflector.pluralize(
                        NamingUtils.toLowerCamelCase(resolveEntityType(refTableName)) ?: resolveEntityType(refTableName)
                    )
                    val entityPackage = tablePackageMap[refTableName] ?: ""
                    val entityType = resolveEntityType(refTableName)
                    SourceFileUtils.writeLine(
                        out,
                        "    var $fieldName: MutableList<$entityPackage.$entityType> = mutableListOf()"
                    )

                    if (countIsOne) {
                        SourceFileUtils.writeLine(out, "")
                        SourceFileUtils.writeLine(out, "    fun load$entityType(): $entityPackage.$entityType? {")
                        SourceFileUtils.writeLine(
                            out,
                            "        return if ($fieldName.isEmpty()) null else $fieldName[0]"
                        )
                        SourceFileUtils.writeLine(out, "    }")
                    }
                }

                "*ManyToOne" -> {
                    SourceFileUtils.writeLine(
                        out,
                        "    @${relation.replace("*", "")}(cascade = [], fetch = FetchType.$fetchType)$fetchAnnotation"
                    )
                    val leftQuote = LEFT_QUOTES_4_ID_ALIAS.replace("\"", "\\\"")
                    val rightQuote = RIGHT_QUOTES_4_ID_ALIAS.replace("\"", "\\\"")
                    SourceFileUtils.writeLine(
                        out,
                        "    @JoinColumn(name = \"$leftQuote$joinColumn$rightQuote\", nullable = false, insertable = false, updatable = false)"
                    )
                    val entityPackage = tablePackageMap[refTableName] ?: ""
                    val entityType = resolveEntityType(refTableName)
                    val fieldName = NamingUtils.toLowerCamelCase(entityType) ?: entityType
                    SourceFileUtils.writeLine(out, "    var $fieldName: $entityPackage.$entityType? = null")
                }

                "ManyToOne" -> {
                    SourceFileUtils.writeLine(
                        out,
                        "    @${relation}(cascade = [], fetch = FetchType.$fetchType)$fetchAnnotation"
                    )
                    val leftQuote = LEFT_QUOTES_4_ID_ALIAS.replace("\"", "\\\"")
                    val rightQuote = RIGHT_QUOTES_4_ID_ALIAS.replace("\"", "\\\"")
                    SourceFileUtils.writeLine(
                        out,
                        "    @JoinColumn(name = \"$leftQuote$joinColumn$rightQuote\", nullable = false)"
                    )
                    val entityPackage = tablePackageMap[refTableName] ?: ""
                    val entityType = resolveEntityType(refTableName)
                    val fieldName = NamingUtils.toLowerCamelCase(entityType) ?: entityType
                    SourceFileUtils.writeLine(out, "    var $fieldName: $entityPackage.$entityType? = null")
                }

                "*OneToMany" -> {
                    // 当前不会用到，无法控制集合数量规模
                    val entityTypeName = resolveEntityType(tableName)
                    val fieldNameFromTable = NamingUtils.toLowerCamelCase(entityTypeName) ?: entityTypeName
                    SourceFileUtils.writeLine(
                        out,
                        "    @${
                            relation.replace(
                                "*",
                                ""
                            )
                        }(mappedBy = \"$fieldNameFromTable\", cascade = [], fetch = FetchType.$fetchType)$fetchAnnotation"
                    )
                    SourceFileUtils.writeLine(out, "    @Fetch(FetchMode.SUBSELECT)")
                    val entityPackage = tablePackageMap[refTableName] ?: ""
                    val entityType = resolveEntityType(refTableName)
                    val fieldName = Inflector.pluralize(NamingUtils.toLowerCamelCase(entityType) ?: entityType)
                    SourceFileUtils.writeLine(
                        out,
                        "    var $fieldName: MutableList<$entityPackage.$entityType> = mutableListOf()"
                    )
                }

                "OneToOne" -> {
                    SourceFileUtils.writeLine(
                        out,
                        "    @${relation}(cascade = [], fetch = FetchType.$fetchType)$fetchAnnotation"
                    )
                    val leftQuote = LEFT_QUOTES_4_ID_ALIAS.replace("\"", "\\\"")
                    val rightQuote = RIGHT_QUOTES_4_ID_ALIAS.replace("\"", "\\\"")
                    SourceFileUtils.writeLine(
                        out,
                        "    @JoinColumn(name = \"$leftQuote$joinColumn$rightQuote\", nullable = false)"
                    )
                    val entityPackage = tablePackageMap[refTableName] ?: ""
                    val entityType = resolveEntityType(refTableName)
                    val fieldName = NamingUtils.toLowerCamelCase(entityType) ?: entityType
                    SourceFileUtils.writeLine(out, "    var $fieldName: $entityPackage.$entityType? = null")
                }

                "*OneToOne" -> {
                    val entityTypeName = resolveEntityType(tableName)
                    val fieldNameFromTable = NamingUtils.toLowerCamelCase(entityTypeName) ?: entityTypeName
                    SourceFileUtils.writeLine(
                        out,
                        "    @${
                            relation.replace(
                                "*",
                                ""
                            )
                        }(mappedBy = \"$fieldNameFromTable\", cascade = [], fetch = FetchType.$fetchType)$fetchAnnotation"
                    )
                    val entityPackage = tablePackageMap[refTableName] ?: ""
                    val entityType = resolveEntityType(refTableName)
                    val fieldName = NamingUtils.toLowerCamelCase(entityType) ?: entityType
                    SourceFileUtils.writeLine(out, "    var $fieldName: $entityPackage.$entityType? = null")
                }

                "ManyToMany" -> {
                    SourceFileUtils.writeLine(
                        out,
                        "    @${relation}(cascade = [], fetch = FetchType.$fetchType)$fetchAnnotation"
                    )
                    SourceFileUtils.writeLine(out, "    @Fetch(FetchMode.SUBSELECT)")
                    val leftQuote = LEFT_QUOTES_4_ID_ALIAS.replace("\"", "\\\"")
                    val rightQuote = RIGHT_QUOTES_4_ID_ALIAS.replace("\"", "\\\"")
                    val joinTableName = refInfos[3]
                    val inverseJoinColumn = refInfos[2]
                    SourceFileUtils.writeLine(
                        out, "    @JoinTable(name = \"$leftQuote$joinTableName$rightQuote\", " +
                                "joinColumns = [JoinColumn(name = \"$leftQuote$joinColumn$rightQuote\", nullable = false)], " +
                                "inverseJoinColumns = [JoinColumn(name = \"$leftQuote$inverseJoinColumn$rightQuote\", nullable = false)])"
                    )
                    val entityPackage = tablePackageMap[refTableName] ?: ""
                    val entityType = resolveEntityType(refTableName)
                    val fieldName = Inflector.pluralize(NamingUtils.toLowerCamelCase(entityType) ?: entityType)
                    SourceFileUtils.writeLine(
                        out,
                        "    var $fieldName: MutableList<$entityPackage.$entityType> = mutableListOf()"
                    )
                }

                "*ManyToMany" -> {
                    val entityTypeName = resolveEntityType(tableName)
                    val fieldNameFromTable =
                        Inflector.pluralize(NamingUtils.toLowerCamelCase(entityTypeName) ?: entityTypeName)
                    SourceFileUtils.writeLine(
                        out,
                        "    @${
                            relation.replace(
                                "*",
                                ""
                            )
                        }(mappedBy = \"$fieldNameFromTable\", cascade = [], fetch = FetchType.$fetchType)$fetchAnnotation"
                    )
                    SourceFileUtils.writeLine(out, "    @Fetch(FetchMode.SUBSELECT)")
                    val entityPackage = tablePackageMap[refTableName] ?: ""
                    val entityType = resolveEntityType(refTableName)
                    val fieldName = Inflector.pluralize(NamingUtils.toLowerCamelCase(entityType) ?: entityType)
                    SourceFileUtils.writeLine(
                        out,
                        "    var $fieldName: MutableList<$entityPackage.$entityType> = mutableListOf()"
                    )
                }
            }
        }
    }

    fun writeAggregateSourceFile(
        table: Map<String, Any?>,
        columns: List<Map<String, Any?>>,
        tablePackageMap: Map<String, String>,
        baseDir: String,
    ) {
        val tag = "aggregate"
        val tableName = SqlSchemaUtils.getTableName(table)
        val aggregate = resolveAggregateWithModule(tableName)

        val entityFullPackage = tablePackageMap[tableName] ?: return
        val entityType = resolveEntityType(tableName)
        val entityVar = NamingUtils.toLowerCamelCase(entityType) ?: entityType

        val ids = resolveIdColumns(columns)
        if (ids.isEmpty()) {
            throw RuntimeException("实体缺失【主键】：$tableName")
        }
        val identityType = if (ids.size != 1) "Long" else SqlSchemaUtils.getColumnType(ids[0])
        val comment = SqlSchemaUtils.getComment(table).replace(Regex(PATTERN_LINE_BREAK), " ")

        val context = getEscapeContext().toMutableMap()
        putContext(tag, "Name", entityType, context)
        putContext(tag, "Entity", entityType, context)
        putContext(tag, "AggregateRoot", context["Entity"] ?: "", context)
        putContext(tag, "templatePackage", SourceFileUtils.refPackage(resolveAggregatesPackage()), context)
        putContext(tag, "package", SourceFileUtils.refPackage(aggregate), context)
        putContext(tag, "path", aggregate.replace(".", File.separator), context)
        putContext(tag, "Aggregate", NamingUtils.toUpperCamelCase(aggregate) ?: aggregate, context)
        putContext(tag, "Comment", comment, context)
        putContext(tag, "CommentEscaped", comment.replace(Regex(PATTERN_LINE_BREAK), "  "), context)
        putContext(
            tag,
            "entityPackage",
            SourceFileUtils.refPackage(entityFullPackage, getExtension().basePackage.get()),
            context
        )
        putContext(tag, "EntityVar", entityVar, context)
        putContext(tag, "IdentityType", identityType, context)

        val aggregateTemplateNodes = if (templateNodeMap.containsKey(tag)) {
            templateNodeMap[tag]!!
        } else {
            listOf(resolveDefaultAggregateTemplateNode())
        }

        try {
            for (templateNode in aggregateTemplateNodes) {
                val pathNode = templateNode.cloneTemplateNode().resolve(context)
                val path = forceRender(
                    pathNode,
                    SourceFileUtils.resolvePackageDirectory(
                        baseDir,
                        SourceFileUtils.concatPackage(
                            getExtension().basePackage.get(),
                            context["templatePackage"] ?: ""
                        )
                    )
                )
                logger.info(
                    SourceFileUtils.resolvePackageDirectory(
                        baseDir,
                        SourceFileUtils.concatPackage(
                            getExtension().basePackage.get(),
                            context["templatePackage"] ?: ""
                        )
                    )
                )
                logger.info("开始生成聚合封装类：$path")
            }
        } catch (e: Exception) {
            logger.error("聚合封装模板文件写入失败！", e)
        }
    }

    fun writeFactorySourceFile(
        table: Map<String, Any?>,
        tablePackageMap: Map<String, String>,
        baseDir: String,
    ) {
        val tag = "factory"
        val tableName = SqlSchemaUtils.getTableName(table)
        val aggregate = resolveAggregateWithModule(tableName)

        val entityFullPackage = tablePackageMap[tableName] ?: return
        val entityType = resolveEntityType(tableName)
        val entityVar = NamingUtils.toLowerCamelCase(entityType) ?: entityType

        val context = getEscapeContext().toMutableMap()
        putContext(tag, "Name", "${entityType}Factory", context)
        putContext(tag, "Factory", context["Name"] ?: "", context)
        putContext(tag, "templatePackage", SourceFileUtils.refPackage(resolveAggregatesPackage()), context)
        putContext(tag, "package", SourceFileUtils.refPackage(aggregate), context)
        putContext(tag, "path", aggregate.replace(".", File.separator), context)
        putContext(tag, "Aggregate", NamingUtils.toUpperCamelCase(aggregate) ?: aggregate, context)
        putContext(tag, "Comment", "", context)
        putContext(tag, "CommentEscaped", "", context)
        putContext(
            tag,
            "entityPackage",
            SourceFileUtils.refPackage(entityFullPackage, getExtension().basePackage.get()),
            context
        )
        putContext(tag, "Entity", entityType, context)
        putContext(tag, "AggregateRoot", context["Entity"] ?: "", context)
        putContext(tag, "EntityVar", entityVar, context)

        val factoryTemplateNodes = if (templateNodeMap.containsKey(tag)) {
            templateNodeMap[tag]!!
        } else {
            listOf(resolveDefaultFactoryPayloadTemplateNode(), resolveDefaultFactoryTemplateNode())
        }

        try {
            for (templateNode in factoryTemplateNodes) {
                val pathNode = templateNode.cloneTemplateNode().resolve(context)
                val path = forceRender(
                    pathNode,
                    SourceFileUtils.resolvePackageDirectory(
                        baseDir,
                        SourceFileUtils.concatPackage(
                            getExtension().basePackage.get(),
                            context["templatePackage"] ?: ""
                        )
                    )
                )
                logger.info(
                    SourceFileUtils.resolvePackageDirectory(
                        baseDir,
                        SourceFileUtils.concatPackage(
                            getExtension().basePackage.get(),
                            context["templatePackage"] ?: ""
                        )
                    )
                )
                logger.info("开始生成聚合工厂：$path")
            }
        } catch (e: Exception) {
            logger.error("聚合工厂模板文件写入失败！", e)
        }
    }

    fun writeSpecificationSourceFile(
        table: Map<String, Any?>,
        tablePackageMap: Map<String, String>,
        baseDir: String,
    ) {
        val tag = "specification"
        val tableName = SqlSchemaUtils.getTableName(table)
        val aggregate = resolveAggregateWithModule(tableName)

        val entityFullPackage = tablePackageMap[tableName] ?: return
        val entityType = resolveEntityType(tableName)
        val entityVar = NamingUtils.toLowerCamelCase(entityType) ?: entityType

        val context = getEscapeContext().toMutableMap()
        putContext(tag, "Name", "${entityType}Specification", context)
        putContext(tag, "Specification", context["Name"] ?: "", context)
        putContext(tag, "templatePackage", SourceFileUtils.refPackage(resolveAggregatesPackage()), context)
        putContext(tag, "package", SourceFileUtils.refPackage(aggregate), context)
        putContext(tag, "path", aggregate.replace(".", File.separator), context)
        putContext(tag, "Aggregate", NamingUtils.toUpperCamelCase(aggregate) ?: aggregate, context)
        putContext(tag, "Comment", "", context)
        putContext(tag, "CommentEscaped", "", context)
        putContext(
            tag,
            "entityPackage",
            SourceFileUtils.refPackage(entityFullPackage, getExtension().basePackage.get()),
            context
        )
        putContext(tag, "Entity", entityType, context)
        putContext(tag, "AggregateRoot", context["Entity"] ?: "", context)
        putContext(tag, "EntityVar", entityVar, context)

        val specificationTemplateNodes = if (templateNodeMap.containsKey(tag)) {
            templateNodeMap[tag]!!
        } else {
            listOf(resolveDefaultSpecificationTemplateNode())
        }

        try {
            for (templateNode in specificationTemplateNodes) {
                val pathNode = templateNode.cloneTemplateNode().resolve(context)
                val path = forceRender(
                    pathNode,
                    SourceFileUtils.resolvePackageDirectory(
                        baseDir,
                        SourceFileUtils.concatPackage(
                            getExtension().basePackage.get(),
                            context["templatePackage"] ?: ""
                        )
                    )
                )
                logger.info("开始生成实体规约：$path")
            }
        } catch (e: Exception) {
            logger.error("实体规约模板文件写入失败！", e)
        }
    }

    fun writeDomainEventSourceFile(
        table: Map<String, Any?>,
        tablePackageMap: Map<String, String>,
        domainEventClassName: String,
        domainEventDescription: String,
        baseDir: String,
    ) {
        val tag = "domain_event"
        val handlerTag = "domain_event_handler"
        val tableName = SqlSchemaUtils.getTableName(table)
        val aggregate = resolveAggregateWithModule(tableName)

        val entityFullPackage = tablePackageMap[tableName] ?: return
        val entityType = resolveEntityType(tableName)
        val entityVar = NamingUtils.toLowerCamelCase(entityType) ?: entityType

        val domainEventDescEscaped = domainEventDescription.replace(Regex(PATTERN_LINE_BREAK), "\\n")

        val context = getEscapeContext().toMutableMap()
        putContext(tag, "Name", domainEventClassName, context)
        putContext(tag, "DomainEvent", context["Name"] ?: "", context)
        putContext(tag, "domainEventPackage", SourceFileUtils.refPackage(resolveAggregatesPackage()), context)
        putContext(tag, "domainEventHandlerPackage", SourceFileUtils.refPackage(resolveSubscriberPackage()), context)
        putContext(tag, "package", SourceFileUtils.refPackage(aggregate), context)
        putContext(tag, "path", aggregate.replace(".", File.separator), context)
        putContext(tag, "persist", "false", context)
        putContext(tag, "Aggregate", NamingUtils.toUpperCamelCase(aggregate) ?: aggregate, context)
        putContext(tag, "Comment", domainEventDescEscaped, context)
        putContext(tag, "CommentEscaped", domainEventDescEscaped, context)
        putContext(
            tag,
            "entityPackage",
            SourceFileUtils.refPackage(entityFullPackage, getExtension().basePackage.get()),
            context
        )
        putContext(tag, "Entity", entityType, context)
        putContext(tag, "AggregateRoot", context["Entity"] ?: "", context)
        putContext(tag, "EntityVar", entityVar, context)

        putContext(tag, "templatePackage", context["domainEventPackage"] ?: "", context)
        val domainEventTemplateNodes = if (templateNodeMap.containsKey(tag)) {
            templateNodeMap[tag]!!
        } else {
            listOf(resolveDefaultDomainEventTemplateNode())
        }

        try {
            for (templateNode in domainEventTemplateNodes) {
                val pathNode = templateNode.cloneTemplateNode().resolve(context)
                val path = forceRender(
                    pathNode,
                    SourceFileUtils.resolvePackageDirectory(
                        baseDir,
                        SourceFileUtils.concatPackage(
                            getExtension().basePackage.get(),
                            context["templatePackage"] ?: ""
                        )
                    )
                )
                logger.info("开始生成领域事件文件：$path")
            }
        } catch (e: Exception) {
            logger.error("领域事件模板文件写入失败！", e)
        }

        putContext(tag, "templatePackage", context["domainEventHandlerPackage"] ?: "", context)
        val domainEventHandlerTemplateNodes = if (templateNodeMap.containsKey(handlerTag)) {
            templateNodeMap[handlerTag]!!
        } else {
            listOf(resolveDefaultDomainEventHandlerTemplateNode())
        }

        try {
            for (templateNode in domainEventHandlerTemplateNodes) {
                val pathNode = templateNode.cloneTemplateNode().resolve(context)
                val path = forceRender(
                    pathNode,
                    SourceFileUtils.resolvePackageDirectory(
                        getApplicationModulePath(),
                        SourceFileUtils.concatPackage(
                            getExtension().basePackage.get(),
                            context["templatePackage"] ?: ""
                        )
                    )
                )
                logger.info("开始生成领域事件处理器文件：$path")
            }
        } catch (e: Exception) {
            logger.error("领域事件处理器模板文件写入失败！", e)
        }
    }

    fun writeEnumSourceFile(
        enumConfig: Map<Int, Array<String>>,
        enumClassName: String,
        enumValueField: String,
        enumNameField: String,
        tablePackageMap: Map<String, String>,
        baseDir: String,
    ) {
        val tag = "enum"
        val itemTag = "enum_item"
        val tableName = enumTableNameMap[enumClassName] ?: return
        val aggregate = resolveAggregateWithModule(tableName)

        val entityFullPackage = tablePackageMap[tableName] ?: return
        val entityType = resolveEntityType(tableName)
        val entityVar = NamingUtils.toLowerCamelCase(entityType) ?: entityType

        val context = getEscapeContext().toMutableMap()
        putContext(tag, "templatePackage", SourceFileUtils.refPackage(resolveAggregatesPackage()), context)
        putContext(tag, "package", SourceFileUtils.refPackage(aggregate), context)
        putContext(tag, "path", aggregate.replace(".", File.separator), context)
        putContext(tag, "Aggregate", NamingUtils.toUpperCamelCase(aggregate) ?: aggregate, context)
        putContext(tag, "Comment", "", context)
        putContext(tag, "CommentEscaped", "", context)
        putContext(
            tag,
            "entityPackage",
            SourceFileUtils.refPackage(entityFullPackage, getExtension().basePackage.get()),
            context
        )
        putContext(tag, "Entity", entityType, context)
        putContext(tag, "AggregateRoot", context["Entity"] ?: "", context)
        putContext(tag, "EntityVar", entityVar, context)
        putContext(tag, "Enum", enumClassName, context)
        putContext(tag, "EnumValueField", enumValueField, context)
        putContext(tag, "EnumNameField", enumNameField, context)

        var enumItems = ""
        for ((key, value) in enumConfig) {
            val itemValue = key.toString()
            val itemName = value[0]
            val itemDesc = value[1]
            logger.info("  $itemDesc : $itemName = $key")

            val itemContext = context.toMutableMap()
            putContext(itemTag, "itemName", itemName, itemContext)
            putContext(itemTag, "itemValue", itemValue, itemContext)
            putContext(itemTag, "itemDesc", itemDesc, itemContext)

            val enumItemsPathNode =
                (if (templateNodeMap.containsKey(itemTag) && templateNodeMap[itemTag]!!.isNotEmpty()) {
                    templateNodeMap[itemTag]!![templateNodeMap[itemTag]!!.size - 1]
                } else {
                    resolveDefaultEnumItemTemplateNode()
                }).cloneTemplateNode().resolve(itemContext)
            enumItems += enumItemsPathNode.data
        }
        putContext(tag, "ENUM_ITEMS", enumItems, context)

        val enumTemplateNodes = if (templateNodeMap.containsKey(tag)) {
            templateNodeMap[tag]!!
        } else {
            listOf(resolveDefaultEnumTemplateNode())
        }

        try {
            for (templateNode in enumTemplateNodes) {
                val pathNode = templateNode.cloneTemplateNode().resolve(context)
                val path = forceRender(
                    pathNode,
                    SourceFileUtils.resolvePackageDirectory(
                        baseDir,
                        SourceFileUtils.concatPackage(
                            getExtension().basePackage.get(),
                            context["templatePackage"] ?: ""
                        )
                    )
                )
                logger.info(JSON.toJSONString(context))
                logger.info("开始生成枚举文件：$path")
            }
        } catch (e: Exception) {
            logger.error("枚举模板文件写入失败！", e)
        }
    }

    fun writeSchemaSourceFile(
        table: Map<String, Any?>,
        columns: List<Map<String, Any?>>,
        tablePackageMap: Map<String, String>,
        relations: Map<String, Map<String, String>>,
        basePackage: String,
        baseDir: String,
    ) {
        val tag = "schema"
        val fieldTag = "schema_field"
        val joinTag = "schema_join"
        val propertyNameTag = "schema_property_name"
        val rootExtraExtensionTag = "root_schema_extra_extension"
        val tableName = SqlSchemaUtils.getTableName(table)
        val aggregate = resolveAggregateWithModule(tableName)

        val schemaPackage = if ("abs".equals(getEntitySchemaOutputMode(), ignoreCase = true)) {
            resolveSchemaPackage()
        } else {
            resolveAggregatesPackage()
        }

        val entityFullPackage = tablePackageMap[tableName] ?: return
        val entityType = resolveEntityType(tableName)
        val entityVar = NamingUtils.toLowerCamelCase(entityType) ?: entityType

        val comment = SqlSchemaUtils.getComment(table).replace(Regex(PATTERN_LINE_BREAK), " ")

        val schemaBaseFullPackage = if (schemaPath.isNotBlank()) {
            SourceFileUtils.resolvePackage(schemaPath)
        } else {
            SourceFileUtils.concatPackage(basePackage, getEntitySchemaOutputPackage())
        }

        val context = getEscapeContext().toMutableMap()

        putContext(tag, "templatePackage", SourceFileUtils.refPackage(schemaPackage), context)
        putContext(tag, "package", SourceFileUtils.refPackage(aggregate), context)
        putContext(tag, "path", aggregate.replace(".", File.separator), context)
        putContext(tag, "Aggregate", NamingUtils.toUpperCamelCase(aggregate) ?: aggregate, context)
        putContext(tag, "isAggregateRoot", SqlSchemaUtils.isAggregateRoot(table).toString(), context)
        putContext(tag, "Comment", comment, context)
        putContext(tag, "CommentEscaped", comment.replace(Regex(PATTERN_LINE_BREAK), " "), context)
        putContext(
            tag,
            "entityPackage",
            SourceFileUtils.refPackage(entityFullPackage, basePackage),
            context
        )
        putContext(tag, "Entity", entityType, context)
        putContext(tag, "EntityVar", entityVar, context)
        putContext(
            tag,
            "schemaBasePackage",
            SourceFileUtils.refPackage(schemaBaseFullPackage, basePackage),
            context
        )
        putContext(tag, "SchemaBase", DEFAULT_SCHEMA_BASE_CLASS_NAME, context)

        var fieldItems = ""
        var propertyNameItems = ""
        for (column in columns) {
            if (!isColumnNeedGenerate(table, column, relations)) {
                continue
            }
            val fieldType = SqlSchemaUtils.getColumnType(column)
            val fieldName =
                NamingUtils.toLowerCamelCase(SqlSchemaUtils.getColumnName(column)) ?: SqlSchemaUtils.getColumnName(
                    column
                )
            val fieldComment = generateFieldComment(column).joinToString("\n    ")
            val fieldDescription = SqlSchemaUtils.getComment(column).replace(Regex(PATTERN_LINE_BREAK), "")

            val itemContext = context.toMutableMap()
            putContext(fieldTag, "fieldType", fieldType, itemContext)
            putContext(fieldTag, "fieldName", fieldName, itemContext)
            putContext(fieldTag, "fieldComment", fieldComment, itemContext)
            putContext(fieldTag, "fieldDescription", fieldDescription, itemContext)

            fieldItems += (if (templateNodeMap.containsKey(fieldTag) && templateNodeMap[fieldTag]!!.isNotEmpty()) {
                templateNodeMap[fieldTag]!![templateNodeMap[fieldTag]!!.size - 1]
            } else {
                resolveDefaultSchemaFieldTemplateNode()
            }).cloneTemplateNode().resolve(itemContext).data ?: ""

            propertyNameItems += (if (templateNodeMap.containsKey(propertyNameTag) && templateNodeMap[propertyNameTag]!!.isNotEmpty()) {
                templateNodeMap[propertyNameTag]!![templateNodeMap[propertyNameTag]!!.size - 1]
            } else {
                resolveDefaultSchemaPropertyNameTemplateNode()
            }).cloneTemplateNode().resolve(itemContext).data ?: ""
        }

        var joinItems = ""
        if (relations.containsKey(tableName)) {
            for ((key, value) in relations[tableName]!!) {
                val refInfos = value.split(";")
                val joinContext = context.toMutableMap()
                val itemContext = context.toMutableMap()
                var fieldType: String
                var fieldName: String
                var fieldComment: String
                var fieldDescription: String

                when (refInfos[0]) {
                    "OneToMany", "*OneToMany" -> {
                        putContext(joinTag, "joinEntityPackage", tablePackageMap[key] ?: "", joinContext)
                        putContext(joinTag, "joinEntityType", resolveEntityType(key), joinContext)
                        putContext(
                            joinTag,
                            "joinEntityVars",
                            Inflector.pluralize(
                                NamingUtils.toLowerCamelCase(resolveEntityType(key)) ?: resolveEntityType(key)
                            ),
                            joinContext
                        )
                        if (!("abs".equals(getEntitySchemaOutputMode(), ignoreCase = true))) {
                            putContext(
                                joinTag,
                                "joinEntitySchemaPackage",
                                "${SourceFileUtils.concatPackage(tablePackageMap[key] ?: "", DEFAULT_SCHEMA_PACKAGE)}.",
                                joinContext
                            )
                        } else {
                            putContext(joinTag, "joinEntitySchemaPackage", "", joinContext)
                        }
                        joinItems += (if (templateNodeMap.containsKey(joinTag) && templateNodeMap[joinTag]!!.isNotEmpty()) {
                            templateNodeMap[joinTag]!![templateNodeMap[joinTag]!!.size - 1]
                        } else {
                            resolveDefaultSchemaJoinTemplateNode()
                        }).cloneTemplateNode().resolve(joinContext).data ?: ""

                        fieldType = "${tablePackageMap[key]}.${resolveEntityType(key)}"
                        fieldName =
                            Inflector.pluralize(
                                NamingUtils.toLowerCamelCase(resolveEntityType(key)) ?: resolveEntityType(key)
                            )
                        fieldComment = ""
                        fieldDescription = ""
                        putContext(fieldTag, "fieldType", "java.util.List<$fieldType>", itemContext)
                        putContext(fieldTag, "fieldName", fieldName, itemContext)
                        putContext(fieldTag, "fieldComment", fieldComment, itemContext)
                        putContext(fieldTag, "fieldDescription", fieldDescription, itemContext)
                        fieldItems += (if (templateNodeMap.containsKey(fieldTag) && templateNodeMap[fieldTag]!!.isNotEmpty()) {
                            templateNodeMap[fieldTag]!![templateNodeMap[fieldTag]!!.size - 1]
                        } else {
                            resolveDefaultSchemaFieldTemplateNode()
                        }).cloneTemplateNode().resolve(itemContext).data ?: ""
                        propertyNameItems += (if (templateNodeMap.containsKey(propertyNameTag) && templateNodeMap[propertyNameTag]!!.isNotEmpty()) {
                            templateNodeMap[propertyNameTag]!![templateNodeMap[propertyNameTag]!!.size - 1]
                        } else {
                            resolveDefaultSchemaPropertyNameTemplateNode()
                        }).cloneTemplateNode().resolve(itemContext).data ?: ""
                    }

                    "OneToOne", "ManyToOne" -> {
                        putContext(joinTag, "joinEntityPackage", tablePackageMap[key] ?: "", joinContext)
                        putContext(joinTag, "joinEntityType", resolveEntityType(key), joinContext)
                        putContext(
                            joinTag,
                            "joinEntityVars",
                            NamingUtils.toLowerCamelCase(resolveEntityType(key)) ?: resolveEntityType(key),
                            joinContext
                        )
                        if (!("abs".equals(getEntitySchemaOutputMode(), ignoreCase = true))) {
                            putContext(
                                joinTag,
                                "joinEntitySchemaPackage",
                                "${SourceFileUtils.concatPackage(tablePackageMap[key] ?: "", DEFAULT_SCHEMA_PACKAGE)}.",
                                joinContext
                            )
                        }
                        joinItems += (if (templateNodeMap.containsKey(joinTag) && templateNodeMap[joinTag]!!.isNotEmpty()) {
                            templateNodeMap[joinTag]!![templateNodeMap[joinTag]!!.size - 1]
                        } else {
                            resolveDefaultSchemaJoinTemplateNode()
                        }).cloneTemplateNode().resolve(joinContext).data ?: ""

                        fieldType = "${tablePackageMap[key]}.${resolveEntityType(key)}"
                        fieldName = NamingUtils.toLowerCamelCase(resolveEntityType(key)) ?: resolveEntityType(key)
                        val refColumn = resolveColumn(columns, refInfos[1])
                        fieldComment =
                            if (refColumn != null) generateFieldComment(refColumn).joinToString("\n    ") else ""
                        fieldDescription = if (refColumn != null) SqlSchemaUtils.getComment(refColumn)
                            .replace(Regex(PATTERN_LINE_BREAK), "") else ""
                        putContext(fieldTag, "fieldType", fieldType, itemContext)
                        putContext(fieldTag, "fieldName", fieldName, itemContext)
                        putContext(fieldTag, "fieldComment", fieldComment, itemContext)
                        putContext(fieldTag, "fieldDescription", fieldDescription, itemContext)
                        fieldItems += (if (templateNodeMap.containsKey(fieldTag) && templateNodeMap[fieldTag]!!.isNotEmpty()) {
                            templateNodeMap[fieldTag]!![templateNodeMap[fieldTag]!!.size - 1]
                        } else {
                            resolveDefaultSchemaFieldTemplateNode()
                        }).cloneTemplateNode().resolve(itemContext).data ?: ""
                        propertyNameItems += (if (templateNodeMap.containsKey(propertyNameTag) && templateNodeMap[propertyNameTag]!!.isNotEmpty()) {
                            templateNodeMap[propertyNameTag]!![templateNodeMap[propertyNameTag]!!.size - 1]
                        } else {
                            resolveDefaultSchemaPropertyNameTemplateNode()
                        }).cloneTemplateNode().resolve(itemContext).data ?: ""
                    }

                    else -> {
                        // 暂不支持
                    }
                }
            }
        }

        putContext(tag, "PROPERTY_NAMES", propertyNameItems, context)
        putContext(tag, "FIELD_ITEMS", fieldItems, context)
        putContext(tag, "JOIN_ITEMS", joinItems, context)

        var extraExtension = ""
        try {
            if (SqlSchemaUtils.isAggregateRoot(table)) {
                val extraExtensionTemplateNodes = if (templateNodeMap.containsKey(rootExtraExtensionTag)) {
                    templateNodeMap[rootExtraExtensionTag]!!
                } else {
                    listOf(resolveDefaultRootSchemaExtraExtensionTemplateNode(getExtension().generation.generateAggregate.get()))
                }
                for (templateNode in extraExtensionTemplateNodes) {
                    extraExtension += templateNode.cloneTemplateNode().resolve(context).data ?: ""
                }
            }
        } catch (e: Exception) {
            logger.error("SchemaExtraExtension模板文件生成失败！", e)
        }
        putContext(tag, "EXTRA_EXTENSION", extraExtension, context)

        val schemaTemplateNodes = if (templateNodeMap.containsKey(tag)) {
            templateNodeMap[tag]!!
        } else {
            listOf(resolveDefaultSchemaTemplateNode(SqlSchemaUtils.isAggregateRoot(table)))
        }

        try {
            for (templateNode in schemaTemplateNodes) {
                val pathNode = templateNode.cloneTemplateNode().resolve(context)
                val path = forceRender(
                    pathNode,
                    SourceFileUtils.resolvePackageDirectory(
                        baseDir,
                        SourceFileUtils.concatPackage(
                            basePackage,
                            context["templatePackage"] ?: ""
                        )
                    )
                )
                logger.info("开始生成Schema文件：$path")
            }
        } catch (e: Exception) {
            logger.error("Schema模板文件写入失败！", e)
        }
    }

    fun writeSchemaBaseSourceFile(baseDir: String) {
        val tag = "schema_base"
        val schemaFullPackage = SourceFileUtils.concatPackage(getExtension().basePackage.get(), resolveSchemaPackage())

        val schemaBaseTemplateNodes = if (templateNodeMap.containsKey(tag)) {
            templateNodeMap[tag]!!
        } else {
            listOf(resolveDefaultSchemaBaseTemplateNode())
        }

        val context = getEscapeContext().toMutableMap()
        putContext(
            tag,
            "templatePackage",
            SourceFileUtils.refPackage(schemaFullPackage, getExtension().basePackage.get()),
            context
        )
        putContext(tag, "SchemaBase", DEFAULT_SCHEMA_BASE_CLASS_NAME, context)

        try {
            for (templateNode in schemaBaseTemplateNodes) {
                val pathNode = templateNode.cloneTemplateNode().resolve(context)
                forceRender(
                    pathNode,
                    SourceFileUtils.resolvePackageDirectory(
                        baseDir,
                        SourceFileUtils.concatPackage(
                            getExtension().basePackage.get(),
                            context["templatePackage"] ?: ""
                        )
                    )
                )
            }
        } catch (e: Exception) {
            logger.error("模板文件写入失败！", e)
        }
    }

    fun resolveDefaultAggregateTemplateNode(): TemplateNode {
        val template = """
            package ${'$'}{basePackage}.${'$'}{templatePackage}.${'$'}{package}
                        
            import com.only4.cap4k.ddd.core.domain.aggregate.Aggregate
            import ${'$'}{basePackage}.${'$'}{templatePackage}.${'$'}{package}.${DEFAULT_FAC_PACKAGE}.${'$'}{Entity}Factory
                        
            /**
             * ${'$'}{Entity}聚合封装
             * ${'$'}{CommentEscaped}
             *
             * @author cap4k-ddd-codegen
             * @date ${'$'}{date}
             */
            class ${'$'}{aggregateNameTemplate}(
            payload: ${'$'}{Entity}Factory.Payload? = null,
            ) : Aggregate.Default<${'$'}{Entity}>(payload) {
                        
            val id by lazy { root.id }
                            
            class Id(key: ${'$'}{IdentityType}) : com.only4.cap4k.ddd.core.domain.aggregate.Id.Default<${'$'}{aggregateNameTemplate}, ${'$'}{IdentityType}>(key)
                            
            }
        """.trimIndent()
        return TemplateNode().apply {
            type = "file"
            tag = "aggregate"
            name = "${'$'}{path}${'$'}{SEPARATOR}${getExtension().generation.aggregateNameTemplate.get()}.kt"
            format = "raw"
            data = template
            conflict = "skip"
        }
    }

    fun resolveDefaultFactoryTemplateNode(): TemplateNode {
        val template = """
            package ${'$'}{basePackage}.domain.aggregates.${'$'}{package}.${DEFAULT_FAC_PACKAGE}

            import ${'$'}{basePackage}.domain.aggregates.${'$'}{package}.${'$'}{Entity}
            import com.only4.cap4k.ddd.core.domain.aggregate.AggregateFactory
            import com.only4.cap4k.ddd.core.domain.aggregate.AggregatePayload
            import com.only4.cap4k.ddd.core.domain.aggregate.annotation.Aggregate
            import org.springframework.stereotype.Service

            @Service
            @Aggregate(aggregate = "${'$'}{Entity}", name = "${'$'}{Entity}Factory", type = Aggregate.TYPE_FACTORY, description = "")
            class ${'$'}{Entity}Factory : AggregateFactory<${'$'}{Entity}Factory.Payload, ${'$'}{Entity}> {
                override fun create(payload: Payload): ${'$'}{Entity} {
                    return ${'$'}{Entity}(

                    )
                }
            }
        """.trimIndent()

        return TemplateNode().apply {
            type = "file"
            tag = "factory"
            name = "${'$'}{path}${'$'}{SEPARATOR}${DEFAULT_FAC_PACKAGE}${'$'}{SEPARATOR}${'$'}{Entity}Factory.kt"
            format = "raw"
            data = template
            conflict = "skip"
        }
    }

    fun resolveDefaultFactoryPayloadTemplateNode(): TemplateNode {
        val template = """
            package ${'$'}{basePackage}.domain.aggregates.${'$'}{package}.${DEFAULT_FAC_PACKAGE}

            import ${'$'}{basePackage}.domain.aggregates.${'$'}{package}.${'$'}{Entity}
            import com.only4.cap4k.ddd.core.domain.aggregate.AggregateFactory
            import com.only4.cap4k.ddd.core.domain.aggregate.AggregatePayload
            import com.only4.cap4k.ddd.core.domain.aggregate.annotation.Aggregate
            @Aggregate(aggregate = "${'$'}{Entity}", name = "${'$'}{Entity}Payload", type = Aggregate.TYPE_FACTORY_PAYLOAD, description = "")
                class Payload(

                ) : AggregatePayload<${'$'}{Entity}>
        """.trimIndent()

        return TemplateNode().apply {
            type = "file"
            tag = "factory"
            name = "${'$'}{path}${'$'}{SEPARATOR}${DEFAULT_FAC_PACKAGE}${'$'}{SEPARATOR}${'$'}{Entity}Payload.kt"
            format = "raw"
            data = template
            conflict = "skip"
        }
    }

    fun resolveDefaultSpecificationTemplateNode(): TemplateNode {

        val template = """
            package ${'$'}{basePackage}.domain.aggregates.${'$'}{package}.specs

            import ${'$'}{basePackage}.domain.aggregates.${'$'}{package}.${'$'}{Entity}
            import com.only4.cap4k.ddd.core.domain.aggregate.Specification
            import com.only4.cap4k.ddd.core.domain.aggregate.Specification.Result
            import com.only4.cap4k.ddd.core.domain.aggregate.annotation.Aggregate
            import org.springframework.stereotype.Service

            /**
             * ${'$'}{CommentEscaped}
             *
             * @author cap4k-ddd-codegen
             * @date ${'$'}{date}
             */
            @Service
            @Aggregate(aggregate = "${'$'}{Entity}", name = "${'$'}{Entity}Specification", type = Aggregate.TYPE_SPECIFICATION, description = "")
            class ${'$'}{Entity}Specification : Specification<${'$'}{Entity}> {

                override fun specify(entity: ${'$'}{Entity}): Result {
                    return Result.pass()
                }

            }
        """.trimIndent()

        return TemplateNode().apply {
            type = "file"
            tag = "specification"
            name = "${'$'}{path}${'$'}{SEPARATOR}${DEFAULT_SPEC_PACKAGE}${'$'}{SEPARATOR}${'$'}{Entity}Specification.kt"
            format = "raw"
            data = template
            conflict = "skip"
        }
    }

    fun resolveDefaultDomainEventHandlerTemplateNode(): TemplateNode {
        val template = """
            package ${'$'}{basePackage}.application.subscribers.domain

            import ${'$'}{basePackage}.domain.aggregates.${'$'}{package}.events.${'$'}{DomainEvent};
            import org.springframework.context.event.EventListener
            import org.springframework.stereotype.Service

            @Service
            class ${'$'}{DomainEvent}Subscriber {

                @EventListener(${'$'}{DomainEvent}::class)
                fun on(event: ${'$'}{DomainEvent}) {

                }
            }
        """.trimIndent()

        return TemplateNode().apply {
            type = "file"
            tag = "domain_event_handler"
            name = "${'$'}{DomainEvent}Subscriber.kt"
            format = "raw"
            data = template
            conflict = "skip"
        }
    }

    fun resolveDefaultDomainEventTemplateNode(): TemplateNode {

        val template = """
            package ${'$'}{basePackage}.domain.aggregates.${'$'}{package}.events;

            import ${'$'}{basePackage}.domain.aggregates.${'$'}{package}.${'$'}{Entity}
            import com.only4.cap4k.ddd.core.domain.aggregate.annotation.Aggregate
            import com.only4.cap4k.ddd.core.domain.event.annotation.DomainEvent

            @DomainEvent(persist = ${'$'}{persist})
            @Aggregate(aggregate = "${'$'}{Aggregate}", name = "${'$'}{DomainEvent}", type = Aggregate.TYPE_DOMAIN_EVENT, description = "")
            class ${'$'}{DomainEvent}(val entity: ${'$'}{Entity})

        """.trimIndent()

        return TemplateNode().apply {
            type = "file"
            tag = "domain_event"
            name = "${'$'}{path}${'$'}{SEPARATOR}${DEFAULT_DOMAIN_EVENT_PACKAGE}${'$'}{SEPARATOR}${'$'}{DomainEvent}.kt"
            format = "raw"
            data = template
            conflict = "skip"
        }
    }

    fun resolveDefaultEnumTemplateNode(): TemplateNode {
        val template = """
            package ${'$'}{basePackage}.${'$'}{templatePackage}.${'$'}{package}.${DEFAULT_ENUM_PACKAGE}

            import com.only4.cap4k.ddd.core.domain.aggregate.annotation.Aggregate
            import jakarta.persistence.AttributeConverter

            /**
             * 本文件由[cap4k-ddd-codegen-gradle-plugin]生成
             * 警告：请勿手工修改该文件，重新生成会覆盖该文件
             * @author cap4k-ddd-codegen
             * @date ${'$'}{date}
             */
            @Aggregate(aggregate = "${'$'}{Aggregate}", name = "${'$'}{Enum}", type = "enum", description = "${'$'}{CommentEscaped}")
            enum class ${'$'}{Enum}(
                val ${'$'}{EnumValueField}: Int,
                val ${'$'}{EnumNameField}: String
            ) {
                
                ${'$'}{ENUM_ITEMS}
                ;

                companion object {
                    
                    private val enumMap: Map<Int, ${'$'}{Enum}> by lazy {
                        entries.associateBy { it.${'$'}{EnumValueField} }
                    }

                    fun valueOf(value: Int): ${'$'}{Enum} {
                        return enumMap[value] ?: throw IllegalArgumentException("枚举类型${'$'}{Enum}枚举值转换异常，不存在的值: ${'$'}value")
                    }

                    fun valueOfOrNull(value: Int?): ${'$'}{Enum}? {
                        return if (value == null) null else valueOf(value)
                    }
                }

                /**
                 * JPA转换器
                 */
                class Converter : AttributeConverter<${'$'}{Enum}, Int> {
                    override fun convertToDatabaseColumn(attribute: ${'$'}{Enum}): Int {
                        return attribute.${'$'}{EnumValueField}
                    }
                    
                    ${
            if (getExtension().generation.enumUnmatchedThrowException.get()) """
                        override fun convertToEntityAttribute(dbData: Int): ${'$'}{Enum} {
                        return valueOf(dbData)
                    }   
                    """.trim() else """
                        override fun convertToEntityAttribute(dbData: Int): ${'$'}{Enum}? {
                        return valueOfOrNull(dbData)
                    }
                    """.trim()
        }
                }
            }
        """.trimIndent()

        return TemplateNode().apply {
            type = "file"
            tag = "enum"
            name = "${'$'}{path}${'$'}{SEPARATOR}${DEFAULT_ENUM_PACKAGE}${'$'}{SEPARATOR}${'$'}{Enum}.kt"
            format = "raw"
            data = template
            conflict = "skip"
        }
    }

    fun resolveDefaultEnumItemTemplateNode(): TemplateNode {
        val template = """
            |
            |    /**
            |     * ${'$'}{itemDesc}
            |     */
            |    ${'$'}{itemName}(${'$'}{itemValue}, "${'$'}{itemDesc}"),
            |    
        """.trimMargin()
        return TemplateNode().apply {
            type = "segment"
            tag = "enum_item"
            name = ""
            format = "raw"
            data = template
            conflict = "skip"
        }
    }

    fun resolveDefaultSchemaFieldTemplateNode(): TemplateNode {
        val template = """
        |
        |    ${'$'}{fieldComment}
        |    fun ${'$'}{fieldName}(): ${'$'}{SchemaBase}.Field<${'$'}{fieldType}> {
        |        return ${'$'}{SchemaBase}.Field(root.get(props.${'$'}{fieldName}), this.criteriaBuilder)
        |    }
        """.trimMargin()

        return TemplateNode().apply {
            type = "segment"
            tag = "schema_field"
            name = ""
            format = "raw"
            data = template
            conflict = "skip"
        }
    }

    fun resolveDefaultSchemaPropertyNameTemplateNode(): TemplateNode {
        val template = """
        |    
        |        /**
        |         * ${'$'}{fieldDescription}
        |         */
        |        val ${'$'}{fieldName} = "${'$'}{fieldName}"
        |       
        """.trimMargin()

        return TemplateNode().apply {
            type = "segment"
            tag = "schema_property_name"
            name = ""
            format = "raw"
            data = template
            conflict = "skip"
        }
    }

    fun resolveDefaultSchemaJoinTemplateNode(): TemplateNode {
        val joinEntitySchema =
            getExtension().generation.entitySchemaNameTemplate.get().replace("\${Entity}", "\${joinEntityType}")
        val template = """/**
            |     * ${'$'}{joinEntityType} 关联查询条件定义
            |     *
            |     * @param joinType
            |     * @return
            |     */
            |    fun join${'$'}{joinEntityType}(joinType: ${'$'}{SchemaBase}.JoinType): ${'$'}{joinEntitySchemaPackage}$joinEntitySchema {
            |        val type = joinType.toJpaJoinType()
            |        val join = (this.root as Root<${'$'}{Entity}>).join<${'$'}{Entity}, ${'$'}{joinEntityPackage}.${'$'}{joinEntityType}>("${'$'}{joinEntityVars}", type)
            |        val schema = ${'$'}{joinEntitySchemaPackage}$joinEntitySchema(join, this.criteriaBuilder)
            |        return schema
            |    }
        """.trimMargin()

        return TemplateNode().apply {
            type = "segment"
            tag = "schema_join"
            name = ""
            format = "raw"
            data = template
            conflict = "skip"
        }
    }

    fun resolveDefaultSchemaTemplateNode(isAggregateRoot: Boolean): TemplateNode {
        val ext = getExtension()
        val entitySchemaNameTemplate = ext.generation.entitySchemaNameTemplate.get()
        val supportQuerydsl = ext.generation.repositorySupportQuerydsl.get()

        val template = """
            package ${'$'}{basePackage}.${'$'}{templatePackage}.${'$'}{package}.${DEFAULT_SCHEMA_PACKAGE}

            ${if (supportQuerydsl) "import com.querydsl.core.types.OrderSpecifier" else ""}
            import com.only4.cap4k.ddd.domain.repo.JpaPredicate
            ${if (supportQuerydsl) "import com.only4.cap4k.ddd.domain.repo.querydsl.QuerydslPredicate" else ""}
            import ${'$'}{basePackage}.${'$'}{schemaBasePackage}.${'$'}{SchemaBase}
            ${if (isAggregateRoot && supportQuerydsl) "import ${'$'}{basePackage}.${'$'}{entityPackage}.${getExtension().generation.aggregateNameTemplate.get()}" else ""}
            import ${'$'}{basePackage}.${'$'}{entityPackage}.${'$'}{Entity}
            ${if (supportQuerydsl) "import ${'$'}{basePackage}.${'$'}{entityPackage}.Q${'$'}{Entity}" else ""}
            ${if (supportQuerydsl) "import com.only4.cap4k.ddd.core.domain.aggregate.AggregatePredicate" else ""}
            import org.springframework.data.jpa.domain.Specification

            import jakarta.persistence.criteria.*
            
            /**
             * ${'$'}{Comment}
             * 本文件由[cap4k-ddd-codegen-gradle-plugin]生成
             * 警告：请勿手工修改该文件，重新生成会覆盖该文件
             * @author cap4k-ddd-codegen
             * @date ${'$'}{date}
             */
            class $entitySchemaNameTemplate(
                private val root: Path<${'$'}{Entity}>,
                private val criteriaBuilder: CriteriaBuilder,
            ) {
                class PROPERTY_NAMES {
                    ${'$'}{PROPERTY_NAMES}
                }
                
                companion object {
                
                    val props = PROPERTY_NAMES()
                    
                    /**
                     * 构建查询条件
                     *
                     * @param builder where条件构造器
                     * @return
                     */
                    @JvmStatic
                    fun specify(builder: ${'$'}{SchemaBase}.PredicateBuilder<$entitySchemaNameTemplate>): Specification<${'$'}{Entity}> {
                        return specify(builder, false, emptyList())
                    }
            
                    /**
                     * 构建查询条件
                     *
                     * @param builder  where条件构造器
                     * @param distinct 是否去重
                     * @return
                     */
                    @JvmStatic
                    fun specify(builder: ${'$'}{SchemaBase}.PredicateBuilder<$entitySchemaNameTemplate>, distinct: Boolean): Specification<${'$'}{Entity}> {
                        return specify(builder, distinct, emptyList())
                    }
            
                    /**
                     * 构建查询条件
                     *
                     * @param builder       where条件构造器
                     * @param orderBuilders 排序条件构造器
                     * @return
                     */
                    @JvmStatic
                    fun specify(
                        builder: ${'$'}{SchemaBase}.PredicateBuilder<$entitySchemaNameTemplate>,
                        vararg orderBuilders: ${'$'}{SchemaBase}.OrderBuilder<$entitySchemaNameTemplate>,
                    ): Specification<${'$'}{Entity}> {
                        return specify(builder, orderBuilders.toList())
                    }
            
                    /**
                     * 构建查询条件
                     *
                     * @param builder       where条件构造器
                     * @param orderBuilders 排序条件构造器
                     * @return
                     */
                    @JvmStatic
                    fun specify(
                        builder: ${'$'}{SchemaBase}.PredicateBuilder<$entitySchemaNameTemplate>,
                        orderBuilders: List<${'$'}{SchemaBase}.OrderBuilder<$entitySchemaNameTemplate>>,
                    ): Specification<${'$'}{Entity}> {
                        return specify(builder, false, orderBuilders)
                    }
            
                    /**
                    * 构建查询条件
                    *
                    * @param builder       where条件构造器
                    * @param distinct      是否去重
                    * @param orderBuilders 排序条件构造器
                    * @return
                    */
                    @JvmStatic
                    fun specify(
                        builder: ${'$'}{SchemaBase}.PredicateBuilder<$entitySchemaNameTemplate>,
                        distinct: Boolean,
                        vararg orderBuilders: ${'$'}{SchemaBase}.OrderBuilder<$entitySchemaNameTemplate>,
                    ): Specification<${'$'}{Entity}> {
                        return specify(builder, distinct, orderBuilders.toList())
                    }
            
                    /**
                    * 构建查询条件
                    *
                    * @param builder       where条件构造器
                    * @param distinct      是否去重
                    * @param orderBuilders 排序条件构造器
                    * @return
                    */
                    @JvmStatic
                    fun specify(
                        builder: ${'$'}{SchemaBase}.PredicateBuilder<$entitySchemaNameTemplate>,
                        distinct: Boolean,
                        orderBuilders: List<${'$'}{SchemaBase}.OrderBuilder<$entitySchemaNameTemplate>>,
                    ): Specification<${'$'}{Entity}> {
                        return specify { schema, criteriaQuery, criteriaBuilder ->
                            criteriaQuery.where(builder.build(schema))
                            criteriaQuery.distinct(distinct)
                            if (orderBuilders.isNotEmpty()) {
                                criteriaQuery.orderBy(orderBuilders.map { it.build(schema) })
                            }
                            null
                        }
                    }
            
                    /**
                     * 构建查询条件
                     *
                     * @param specifier 查询条件构造器
                     * @return
                     */
                    @JvmStatic
                    fun specify(specifier: ${'$'}{SchemaBase}.Specification<${'$'}{Entity}, $entitySchemaNameTemplate>): Specification<${'$'}{Entity}> {
                        return Specification { root, criteriaQuery, criteriaBuilder ->
                            val schema = $entitySchemaNameTemplate(root, criteriaBuilder)
                            specifier.toPredicate(schema, criteriaQuery, criteriaBuilder)
                        }
                    }
            
                    /**
                    * 构建子查询
                    *
                    * @param resultClass      返回结果类型
                    * @param selectBuilder    select条件构造器
                    * @param predicateBuilder where条件构造器
                    * @param criteriaBuilder
                    * @param criteriaQuery
                    * @param <E>
                    * @return
                    */
                    @JvmStatic
                    fun <E> subquery(
                        resultClass: Class<E>,
                        selectBuilder: ${'$'}{SchemaBase}.ExpressionBuilder<$entitySchemaNameTemplate, E>,
                        predicateBuilder: ${'$'}{SchemaBase}.PredicateBuilder<$entitySchemaNameTemplate>,
                        criteriaBuilder: CriteriaBuilder,
                        criteriaQuery: CriteriaQuery<*>,
                    ): Subquery<E> {
                        return subquery(resultClass, { sq, schema ->
                            sq.select(selectBuilder.build(schema))
                            sq.where(predicateBuilder.build(schema))
                        }, criteriaBuilder, criteriaQuery)
                    }
            
                    /**
                     * 构建子查询
                     *
                     * @param resultClass       返回结果类型
                     * @param subqueryConfigure 子查询配置
                     * @param criteriaBuilder
                     * @param criteriaQuery
                     * @param <E>
                     * @return
                     */
                    @JvmStatic
                    fun <E> subquery(
                        resultClass: Class<E>,
                        subqueryConfigure: ${'$'}{SchemaBase}.SubqueryConfigure<E, $entitySchemaNameTemplate>,
                        criteriaBuilder: CriteriaBuilder,
                        criteriaQuery: CriteriaQuery<*>,
                    ): Subquery<E> {
                        val sq = criteriaQuery.subquery(resultClass)
                        val root = sq.from(${'$'}{Entity}::class.java)
                        val schema = $entitySchemaNameTemplate(root, criteriaBuilder)
                        subqueryConfigure.configure(sq, schema)
                        return sq
                    }
                    
                    ${'$'}{EXTRA_EXTENSION}
                }
                
                fun _criteriaBuilder(): CriteriaBuilder = criteriaBuilder

                fun _root(): Path<${'$'}{Entity}> = root
            
                ${'$'}{FIELD_ITEMS}
            
                /**
                 * 满足所有条件
                 * @param restrictions
                 * @return
                 */
                fun all(vararg restrictions: Predicate): Predicate {
                    return criteriaBuilder.and(*restrictions)
                }
            
                /**
                 * 满足任一条件
                 * @param restrictions
                 * @return
                 */
                fun any(vararg restrictions: Predicate): Predicate {
                    return criteriaBuilder.or(*restrictions)
                }
            
                /**
                 * 指定条件
                 * @param builder
                 * @return
                 */
                fun spec(builder: ${'$'}{SchemaBase}.PredicateBuilder<$entitySchemaNameTemplate>): Predicate {
                    return builder.build(this)
                }
                
                ${'$'}{JOIN_ITEMS}
            }
        """.trimIndent()

        return TemplateNode().apply {
            type = "file"
            tag = "schema"
            name = "${'$'}{path}${'$'}{SEPARATOR}${DEFAULT_SCHEMA_PACKAGE}${'$'}{SEPARATOR}$entitySchemaNameTemplate.kt"
            format = "raw"
            data = template
            conflict = "overwrite"
        }
    }

    fun resolveDefaultRootSchemaExtraExtensionTemplateNode(generateAggregate: Boolean): TemplateNode {
        val ext = getExtension()
        val entitySchemaNameTemplate = ext.generation.entitySchemaNameTemplate.get()
        val supportQuerydsl = ext.generation.repositorySupportQuerydsl.get()
        var template = ""

        template += if (generateAggregate)
            """/**
                |         * 构建查询条件
                |         *
                |         * @param id 主键
                |         * @return
                |         */
                |        @JvmStatic
                |        fun predicateById(id: Any): AggregatePredicate<${getExtension().generation.aggregateNameTemplate.get()}, ${'$'}{Entity}> {
                |            return JpaPredicate.byId(${'$'}{Entity}::class.java, id).toAggregatePredicate(${getExtension().generation.aggregateNameTemplate.get()}::class.java)
                |        }
                |    
                |        /**
                |        * 构建查询条件
                |        *
                |        * @param ids 主键
                |        * @return
                |        */
                |        @JvmStatic
                |        fun predicateByIds(ids: Iterable<*>): AggregatePredicate<${getExtension().generation.aggregateNameTemplate.get()}, ${'$'}{Entity}> {
                |            @Suppress("UNCHECKED_CAST")
                |            return JpaPredicate.byIds(${'$'}{Entity}::class.java, ids as Iterable<Any>).toAggregatePredicate(${getExtension().generation.aggregateNameTemplate.get()}::class.java)
                |        }
                |    
                |        /**
                |         * 构建查询条件
                |         *
                |         * @param ids 主键
                |         * @return
                |         */
                |        @JvmStatic
                |        fun predicateByIds(vararg ids: Any): AggregatePredicate<${getExtension().generation.aggregateNameTemplate.get()}, ${'$'}{Entity}> {
                |            return JpaPredicate.byIds(${'$'}{Entity}::class.java, ids.toList()).toAggregatePredicate(${getExtension().generation.aggregateNameTemplate.get()}::class.java)
                |        }
                |    
                |        /**
                |         * 构建查询条件
                |         *
                |         * @param builder 查询条件构造器
                |         * @return
                |         */
                |        @JvmStatic
                |        fun predicate(builder: ${'$'}{SchemaBase}.PredicateBuilder<$entitySchemaNameTemplate>): AggregatePredicate<${getExtension().generation.aggregateNameTemplate.get()}, ${'$'}{Entity}> {
                |            return JpaPredicate.bySpecification(${'$'}{Entity}::class.java, specify(builder)).toAggregatePredicate(${getExtension().generation.aggregateNameTemplate.get()}::class.java)
                |        }
                |    
                |        /**
                |         * 构建查询条件
                |         *
                |         * @param builder  查询条件构造器
                |         * @param distinct 是否去重
                |         * @return
                |         */
                |        @JvmStatic
                |        fun predicate(builder: ${'$'}{SchemaBase}.PredicateBuilder<$entitySchemaNameTemplate>, distinct: Boolean): AggregatePredicate<${getExtension().generation.aggregateNameTemplate.get()}, ${'$'}{Entity}> {
                |            return JpaPredicate.bySpecification(${'$'}{Entity}::class.java, specify(builder, distinct)).toAggregatePredicate(${getExtension().generation.aggregateNameTemplate.get()}::class.java)
                |        }
                |    
                |        /**
                |         * 构建查询条件
                |         *
                |         * @param builder       查询条件构造器
                |         * @param orderBuilders 排序构造器
                |         * @return
                |         */
                |        @JvmStatic
                |        fun predicate(
                |            builder: ${'$'}{SchemaBase}.PredicateBuilder<$entitySchemaNameTemplate>,
                |            orderBuilders: List<${'$'}{SchemaBase}.OrderBuilder<$entitySchemaNameTemplate>>,
                |        ): AggregatePredicate<${getExtension().generation.aggregateNameTemplate.get()}, ${'$'}{Entity}> {
                |            return JpaPredicate.bySpecification(${'$'}{Entity}::class.java, specify(builder, false, orderBuilders)).toAggregatePredicate(${getExtension().generation.aggregateNameTemplate.get()}::class.java)
                |        }
                |    
                |        /**
                |         * 构建查询条件
                |         *
                |         * @param builder       查询条件构造器
                |         * @param orderBuilders 排序构造器
                |         * @return
                |         */
                |        @JvmStatic
                |        fun predicate(
                |            builder: ${'$'}{SchemaBase}.PredicateBuilder<$entitySchemaNameTemplate>,
                |            vararg orderBuilders: ${'$'}{SchemaBase}.OrderBuilder<$entitySchemaNameTemplate>,
                |        ): AggregatePredicate<${getExtension().generation.aggregateNameTemplate.get()}, ${'$'}{Entity}> {
                |            return JpaPredicate.bySpecification(${'$'}{Entity}::class.java, specify(builder, false, *orderBuilders)).toAggregatePredicate(${getExtension().generation.aggregateNameTemplate.get()}::class.java)
                |        }
                |    
                |        /**
                |         * 构建查询条件
                |         *
                |         * @param builder       查询条件构造器
                |         * @param distinct      是否去重
                |         * @param orderBuilders 排序构造器
                |         * @return
                |         */
                |        @JvmStatic
                |        fun predicate(
                |            builder: ${'$'}{SchemaBase}.PredicateBuilder<$entitySchemaNameTemplate>,
                |            distinct: Boolean,
                |            orderBuilders: List<${'$'}{SchemaBase}.OrderBuilder<$entitySchemaNameTemplate>>,
                |        ): AggregatePredicate<${getExtension().generation.aggregateNameTemplate.get()}, ${'$'}{Entity}> {
                |            return JpaPredicate.bySpecification(${'$'}{Entity}::class.java, specify(builder, distinct, orderBuilders)).toAggregatePredicate(${getExtension().generation.aggregateNameTemplate.get()}::class.java)
                |        }
                |    
                |        /**
                |         * 构建查询条件
                |         *
                |         * @param builder       查询条件构造器
                |         * @param distinct      是否去重
                |         * @param orderBuilders 排序构造器
                |         * @return
                |         */
                |        @JvmStatic
                |        fun predicate(
                |            builder: ${'$'}{SchemaBase}.PredicateBuilder<$entitySchemaNameTemplate>,
                |            distinct: Boolean,
                |            vararg orderBuilders: ${'$'}{SchemaBase}.OrderBuilder<$entitySchemaNameTemplate>,
                |        ): AggregatePredicate<${getExtension().generation.aggregateNameTemplate.get()}, ${'$'}{Entity}> {
                |            return JpaPredicate.bySpecification(${'$'}{Entity}::class.java, specify(builder, distinct, *orderBuilders)).toAggregatePredicate(${getExtension().generation.aggregateNameTemplate.get()}::class.java)
                |        }
                |    
                |        /**
                |         * 构建查询条件
                |         *
                |         * @param specifier 查询条件构造器
                |         * @return
                |         */
                |        @JvmStatic
                |        fun predicate(specifier: ${'$'}{SchemaBase}.Specification<${'$'}{Entity}, $entitySchemaNameTemplate>): AggregatePredicate<${getExtension().generation.aggregateNameTemplate.get()}, ${'$'}{Entity}> {
                |            return JpaPredicate.bySpecification(${'$'}{Entity}::class.java, specify(specifier)).toAggregatePredicate(${getExtension().generation.aggregateNameTemplate.get()}::class.java)
                |        }
            """.trimMargin()
        else """/**
                    |        * 构建查询条件
                    |        *
                    |        * @param id 主键
                    |        * @return
                    |        */
                    |       @JvmStatic
                    |       fun predicateById(id: Any): JpaPredicate<${'$'}{Entity}> {
                    |           return JpaPredicate.byId(${'$'}{Entity}::class.java, id)
                    |       }
                    |       
                    |       /**
                    |       * 构建查询条件
                    |       *
                    |       * @param ids 主键
                    |       * @return
                    |       */
                    |       @JvmStatic
                    |       fun predicateByIds(ids: Iterable<*>): JpaPredicate<${'$'}{Entity}> {
                    |           @Suppress("UNCHECKED_CAST")
                    |           return JpaPredicate.byIds(${'$'}{Entity}::class.java, ids as Iterable<Any>)
                    |       }
                    |       
                    |       /**
                    |        * 构建查询条件
                    |        *
                    |        * @param ids 主键
                    |        * @return
                    |        */
                    |       @JvmStatic
                    |       fun predicateByIds(vararg ids: Any): JpaPredicate<${'$'}{Entity}> {
                    |           return JpaPredicate.byIds(${'$'}{Entity}::class.java, ids.toList())
                    |       }
                    |       
                    |       /**
                    |        * 构建查询条件
                    |        *
                    |        * @param builder 查询条件构造器
                    |        * @return
                    |        */
                    |       @JvmStatic
                    |       fun predicate(builder: ${'$'}{SchemaBase}.PredicateBuilder<$entitySchemaNameTemplate>): JpaPredicate<${'$'}{Entity}> {
                    |           return JpaPredicate.bySpecification(${'$'}{Entity}::class.java, specify(builder))
                    |       }
                    |       
                    |       /**
                    |        * 构建查询条件
                    |        *
                    |        * @param builder  查询条件构造器
                    |        * @param distinct 是否去重
                    |        * @return
                    |        */
                    |       @JvmStatic
                    |       fun predicate(builder: ${'$'}{SchemaBase}.PredicateBuilder<$entitySchemaNameTemplate>, distinct: Boolean): JpaPredicate<${'$'}{Entity}> {
                    |           return JpaPredicate.bySpecification(${'$'}{Entity}::class.java, specify(builder, distinct))
                    |       }
                    |       
                    |       /**
                    |        * 构建查询条件
                    |        *
                    |        * @param builder       查询条件构造器
                    |        * @param orderBuilders 排序构造器
                    |        * @return
                    |        */
                    |       @JvmStatic
                    |       fun predicate(
                    |           builder: ${'$'}{SchemaBase}.PredicateBuilder<$entitySchemaNameTemplate>,
                    |           orderBuilders: List<${'$'}{SchemaBase}.OrderBuilder<$entitySchemaNameTemplate>>,
                    |       ): JpaPredicate<${'$'}{Entity}> {
                    |           return JpaPredicate.bySpecification(${'$'}{Entity}::class.java, specify(builder, false, orderBuilders))
                    |       }
                    |       
                    |       /**
                    |        * 构建查询条件
                    |        *
                    |        * @param builder       查询条件构造器
                    |        * @param orderBuilders 排序构造器
                    |        * @return
                    |        */
                    |       @JvmStatic
                    |       fun predicate(
                    |           builder: ${'$'}{SchemaBase}.PredicateBuilder<$entitySchemaNameTemplate>,
                    |           vararg orderBuilders: ${'$'}{SchemaBase}.OrderBuilder<$entitySchemaNameTemplate>,
                    |       ): JpaPredicate<${'$'}{Entity}> {
                    |           return JpaPredicate.bySpecification(${'$'}{Entity}::class.java, specify(builder, false, *orderBuilders))
                    |       }
                    |       
                    |       /**
                    |        * 构建查询条件
                    |        *
                    |        * @param builder       查询条件构造器
                    |        * @param distinct      是否去重
                    |        * @param orderBuilders 排序构造器
                    |        * @return
                    |        */
                    |       @JvmStatic
                    |       fun predicate(
                    |           builder: ${'$'}{SchemaBase}.PredicateBuilder<$entitySchemaNameTemplate>,
                    |           distinct: Boolean,
                    |           orderBuilders: List<${'$'}{SchemaBase}.OrderBuilder<$entitySchemaNameTemplate>>,
                    |       ): JpaPredicate<${'$'}{Entity}> {
                    |           return JpaPredicate.bySpecification(${'$'}{Entity}::class.java, specify(builder, distinct, orderBuilders))
                    |       }
                    |       
                    |       /**
                    |        * 构建查询条件
                    |        *
                    |        * @param builder       查询条件构造器
                    |        * @param distinct      是否去重
                    |        * @param orderBuilders 排序构造器
                    |        * @return
                    |        */
                    |       @JvmStatic
                    |       fun predicate(
                    |           builder: ${'$'}{SchemaBase}.PredicateBuilder<$entitySchemaNameTemplate>,
                    |           distinct: Boolean,
                    |           vararg orderBuilders: ${'$'}{SchemaBase}.OrderBuilder<$entitySchemaNameTemplate>,
                    |       ): JpaPredicate<${'$'}{Entity}> {
                    |           return JpaPredicate.bySpecification(${'$'}{Entity}::class.java, specify(builder, distinct, *orderBuilders))
                    |       }
                    |       
                    |       /**
                    |        * 构建查询条件
                    |        *
                    |        * @param specifier 查询条件构造器
                    |        * @return
                    |        */
                    |       @JvmStatic
                    |       fun predicate(specifier: ${'$'}{SchemaBase}.Specification<${'$'}{Entity}, $entitySchemaNameTemplate>): JpaPredicate<${'$'}{Entity}> {
                    |           return JpaPredicate.bySpecification(${'$'}{Entity}::class.java, specify(specifier))
                    |       }
                """.trimMargin()

        if (supportQuerydsl) {
            template += if (generateAggregate) """
                    |
                    |       /**
                    |         * 构建querydsl查询条件
                    |         *
                    |         * @param filterBuilder          查询条件构造器
                    |         * @param orderSpecifierBuilders 排序构造器
                    |         * @return
                    |         */
                    |        @JvmStatic
                    |        fun querydsl(
                    |            filterBuilder: java.util.function.Function<Q${'$'}{Entity}, com.querydsl.core.types.Predicate>,
                    |            vararg orderSpecifierBuilders: java.util.function.Function<Q${'$'}{Entity}, OrderSpecifier<*>>,
                    |        ): AggregatePredicate<${getExtension().generation.aggregateNameTemplate.get()}, ${'$'}{Entity}> {
                    |            return QuerydslPredicate.of(${'$'}{Entity}::class.java)
                    |                .where(filterBuilder.apply(Q${'$'}{Entity}.${'$'}{EntityVar}))
                    |                .orderBy(*orderSpecifierBuilders.map { it.apply(Q${'$'}{Entity}.${'$'}{EntityVar}) }.toTypedArray())
                    |                .toAggregatePredicate(${getExtension().generation.aggregateNameTemplate.get()}::class.java)
                    |        }
                    |        
                    |        /**
                    |         * 构建querydsl查询条件
                    |         *
                    |         * @param filter          查询条件构造器
                    |         * @param orderSpecifiers 排序构造器
                    |         * @return
                    |         */
                    |        @JvmStatic
                    |        fun querydsl(
                    |            filter: com.querydsl.core.types.Predicate,
                    |            vararg orderSpecifiers: OrderSpecifier<*>,
                    |        ): AggregatePredicate<${getExtension().generation.aggregateNameTemplate.get()}, ${'$'}{Entity}> {
                    |            return QuerydslPredicate.of(${'$'}{Entity}::class.java)
                    |                .where(filter)
                    |                .orderBy(*orderSpecifiers)
                    |                .toAggregatePredicate(${getExtension().generation.aggregateNameTemplate.get()}::class.java)
                    |        }  
                """.trimMargin()
            else """
                    |
                    |       /**
                    |         * 构建querydsl查询条件
                    |         *
                    |         * @param filterBuilder          查询条件构造器
                    |         * @param orderSpecifierBuilders 排序构造器
                    |         * @return
                    |         */
                    |        @JvmStatic
                    |        fun querydsl(
                    |            filterBuilder: java.util.function.Function<Q${'$'}{Entity}, com.querydsl.core.types.Predicate>,
                    |            vararg orderSpecifierBuilders: java.util.function.Function<Q${'$'}{Entity}, OrderSpecifier<*>>,
                    |        ): QuerydslPredicate<${'$'}{Entity}> {
                    |            return QuerydslPredicate.of(${'$'}{Entity}::class.java)
                    |                .where(filterBuilder.apply(Q${'$'}{Entity}.${'$'}{EntityVar}))
                    |                .orderBy(*orderSpecifierBuilders.map { it.apply(Q${'$'}{Entity}.${'$'}{EntityVar}) }.toTypedArray())
                    |        }
                    |        
                    |        /**
                    |         * 构建querydsl查询条件
                    |         *
                    |         * @param filter          查询条件构造器
                    |         * @param orderSpecifiers 排序构造器
                    |         * @return
                    |         */
                    |        @JvmStatic
                    |        fun querydsl(
                    |            filter: com.querydsl.core.types.Predicate,
                    |            vararg orderSpecifiers: OrderSpecifier<*>,
                    |        ): QuerydslPredicate<${'$'}{Entity}> {
                    |            return QuerydslPredicate.of(${'$'}{Entity}::class.java)
                    |                .where(filter)
                    |                .orderBy(*orderSpecifiers)
                    |        }                    
                """.trimMargin()
        }

        return TemplateNode().apply {
            type = "segment"
            tag = "root_schema_extra_extension"
            name = ""
            format = "raw"
            data = template
            conflict = "skip"
        }
    }

    fun resolveDefaultSchemaBaseTemplateNode(): TemplateNode {
        val template = """
            package ${'$'}{basePackage}.${'$'}{templatePackage}

            import jakarta.persistence.criteria.*
            import org.hibernate.query.sqm.SortOrder
            import org.hibernate.query.sqm.tree.domain.SqmBasicValuedSimplePath
            import org.hibernate.query.sqm.tree.select.SqmSortSpecification

            /**
             * 实体结构基类
             *
             * @author cap4k-ddd-codegen
             */
            object ${'$'}{SchemaBase} {
                        
                fun interface Specification<E, S> {
                    fun toPredicate(schema: S, criteriaQuery: CriteriaQuery<*>, criteriaBuilder: CriteriaBuilder): Predicate?
                }
                        
                fun interface SubqueryConfigure<E, S> {
                    fun configure(subquery: Subquery<E>, schema: S)
                }
                        
                /**
                 * 表达式构建器
                 */
                fun interface ExpressionBuilder<S, T> {
                    fun build(schema: S): Expression<T>
                }
                        
                /**
                 * 断言构建器
                 */
                fun interface PredicateBuilder<S> {
                    fun build(schema: S): Predicate
                }
                        
                /**
                 * 排序构建器
                 */
                fun interface OrderBuilder<S> {
                    fun build(schema: S): Order
                }
                        
                enum class JoinType {
                    INNER,
                    LEFT,
                    RIGHT;
                        
                    fun toJpaJoinType(): jakarta.persistence.criteria.JoinType {
                        return when (this) {
                            INNER -> jakarta.persistence.criteria.JoinType.INNER
                            LEFT -> jakarta.persistence.criteria.JoinType.LEFT
                            RIGHT -> jakarta.persistence.criteria.JoinType.RIGHT
                        }
                    }
                }
                        
                /**
                 * 字段
                 *
                 * @param <T>
                 */
                @Suppress("UNCHECKED_CAST")
                class Field<T> {
                    private val path: Path<T>?
                    private val criteriaBuilder: CriteriaBuilder?
                    private val name: String?
                        
                    constructor(path: Path<T>, criteriaBuilder: CriteriaBuilder) {
                        this.path = path
                        this.criteriaBuilder = criteriaBuilder
                        this.name = if (path is SqmBasicValuedSimplePath<*>) {
                            path.navigablePath.localName
                        } else null
                    }
                        
                    constructor(name: String) {
                        this.name = name
                        this.path = null
                        this.criteriaBuilder = null
                    }
                        
                    protected fun _criteriaBuilder(): CriteriaBuilder? = criteriaBuilder
                        
                    fun path(): Path<T>? = path
                        
                    override fun toString(): String = name ?: ""
                        
                    fun asc(): Order = SqmSortSpecification(path as SqmBasicValuedSimplePath<T>, SortOrder.ASCENDING)
                        
                    fun desc(): Order = SqmSortSpecification(path as SqmBasicValuedSimplePath<T>, SortOrder.DESCENDING)
                        
                    fun isTrue(): Predicate = criteriaBuilder!!.isTrue(path as Expression<Boolean>)
                        
                    fun isFalse(): Predicate = criteriaBuilder!!.isFalse(path as Expression<Boolean>)
                        
                    fun isEmpty(): Predicate = criteriaBuilder!!.isEmpty(path as Expression<Collection<*>>)
                        
                    fun isNotEmpty(): Predicate = criteriaBuilder!!.isNotEmpty(path as Expression<Collection<*>>)
                        
                    fun equal(value: Any?): Predicate = criteriaBuilder!!.equal(path, value)
                        
                    fun equal(value: Expression<*>): Predicate = criteriaBuilder!!.equal(path, value)
                        
                    fun notEqual(value: Any?): Predicate = criteriaBuilder!!.notEqual(path, value)
                        
                    fun notEqual(value: Expression<*>): Predicate = criteriaBuilder!!.notEqual(path, value)
                        
                    fun isNull(): Predicate = criteriaBuilder!!.isNull(path)
                        
                    fun isNotNull(): Predicate = criteriaBuilder!!.isNotNull(path)
                        
                    fun <Y : Comparable<Y>> greaterThan(value: Y): Predicate =
                        criteriaBuilder!!.greaterThan(path as Expression<Y>, value)
                        
                    fun <Y : Comparable<Y>> greaterThan(value: Expression<out Y>): Predicate =
                        criteriaBuilder!!.greaterThan(path as Expression<Y>, value)
                        
                    fun <Y : Comparable<Y>> greaterThanOrEqualTo(value: Y): Predicate =
                        criteriaBuilder!!.greaterThanOrEqualTo(path as Expression<Y>, value)
                        
                    fun <Y : Comparable<Y>> greaterThanOrEqualTo(value: Expression<out Y>): Predicate =
                        criteriaBuilder!!.greaterThanOrEqualTo(path as Expression<Y>, value)
                        
                    fun <Y : Comparable<Y>> lessThan(value: Y): Predicate =
                        criteriaBuilder!!.lessThan(path as Expression<Y>, value)
                        
                    fun <Y : Comparable<Y>> lessThan(value: Expression<out Y>): Predicate =
                        criteriaBuilder!!.lessThan(path as Expression<Y>, value)
                        
                    fun <Y : Comparable<Y>> lessThanOrEqualTo(value: Y): Predicate =
                        criteriaBuilder!!.lessThanOrEqualTo(path as Expression<Y>, value)
                        
                    fun <Y : Comparable<Y>> lessThanOrEqualTo(value: Expression<out Y>): Predicate =
                        criteriaBuilder!!.lessThanOrEqualTo(path as Expression<Y>, value)
                        
                    fun <Y : Comparable<Y>> between(value1: Y, value2: Y): Predicate =
                        criteriaBuilder!!.between(path as Expression<Y>, value1, value2)
                        
                    fun <Y : Comparable<Y>> between(value1: Expression<out Y>, value2: Expression<out Y>): Predicate =
                        criteriaBuilder!!.between(path as Expression<Y>, value1, value2)
                        
                    fun `in`(vararg values: Any?): Predicate = `in`(listOf(*values))
                        
                    fun `in`(values: Collection<*>): Predicate {
                        val predicate = criteriaBuilder!!.`in`(path)
                        values.forEach { value ->
                            @Suppress("UNCHECKED_CAST")
                            predicate.value(value as T)
                        }
                        return predicate
                    }
                        
                    fun `in`(vararg expressions: Expression<*>): Predicate {
                        val predicate = criteriaBuilder!!.`in`(path)
                        expressions.forEach { expression ->
                            @Suppress("UNCHECKED_CAST")
                            predicate.value(expression as Expression<out T>)
                        }
                        return predicate
                    }
                        
                    fun notIn(vararg values: Any?): Predicate = notIn(listOf(*values))
                        
                    fun notIn(values: Collection<*>): Predicate = criteriaBuilder!!.not(`in`(values))
                        
                    fun notIn(vararg expressions: Expression<*>): Predicate = criteriaBuilder!!.not(`in`(*expressions))
                        
                    fun like(value: String): Predicate = criteriaBuilder!!.like(path as Expression<String>, value)
                        
                    fun like(value: Expression<String>): Predicate = criteriaBuilder!!.like(path as Expression<String>, value)
                        
                    fun notLike(value: String): Predicate = criteriaBuilder!!.notLike(path as Expression<String>, value)
                        
                    fun notLike(value: Expression<String>): Predicate = criteriaBuilder!!.notLike(path as Expression<String>, value)
                        
                    // 简化方法名
                    fun eq(value: Any?): Predicate = equal(value)
                        
                    fun eq(value: Expression<*>): Predicate = equal(value)
                        
                    fun neq(value: Any?): Predicate = notEqual(value)
                        
                    fun neq(value: Expression<*>): Predicate = notEqual(value)
                        
                    fun <Y : Comparable<Y>> gt(value: Y): Predicate = greaterThan(value)
                        
                    fun <Y : Comparable<Y>> gt(value: Expression<out Y>): Predicate = greaterThan(value)
                        
                    fun <Y : Comparable<Y>> ge(value: Y): Predicate = greaterThanOrEqualTo(value)
                        
                    fun <Y : Comparable<Y>> ge(value: Expression<out Y>): Predicate = greaterThanOrEqualTo(value)
                        
                    fun <Y : Comparable<Y>> lt(value: Y): Predicate = lessThan(value)
                        
                    fun <Y : Comparable<Y>> lt(value: Expression<out Y>): Predicate = lessThan(value)
                        
                    fun <Y : Comparable<Y>> le(value: Y): Predicate = lessThanOrEqualTo(value)
                        
                    fun <Y : Comparable<Y>> le(value: Expression<out Y>): Predicate = lessThanOrEqualTo(value)
                }
            }
            """.trimIndent()

        return TemplateNode().apply {
            type = "file"
            tag = "schema_base"
            name = "${'$'}{SchemaBase}.kt"
            format = "raw"
            data = template
            conflict = "overwrite"
        }
    }
}
