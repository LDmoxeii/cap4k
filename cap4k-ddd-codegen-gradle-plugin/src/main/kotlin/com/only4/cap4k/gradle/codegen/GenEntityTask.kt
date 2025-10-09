package com.only4.cap4k.gradle.codegen

import com.alibaba.fastjson.JSON
import com.only4.cap4k.gradle.codegen.misc.*
import com.only4.cap4k.gradle.codegen.misc.SqlSchemaUtils.LEFT_QUOTES_4_ID_ALIAS
import com.only4.cap4k.gradle.codegen.misc.SqlSchemaUtils.RIGHT_QUOTES_4_ID_ALIAS
import com.only4.cap4k.gradle.codegen.misc.SqlSchemaUtils.hasColumn
import com.only4.cap4k.gradle.codegen.template.TemplateNode
import org.gradle.api.tasks.Internal
import org.gradle.api.tasks.TaskAction
import java.io.BufferedWriter
import java.io.File
import java.io.StringWriter
import java.time.LocalDateTime
import java.time.format.DateTimeFormatter

/**
 * 生成实体类任务
 */
open class GenEntityTask : GenArchTask() {

    companion object {
        private const val DEFAULT_SCHEMA_PACKAGE = "meta"
        private const val DEFAULT_SPEC_PACKAGE = "specs"
        private const val DEFAULT_FAC_PACKAGE = "factory"
        private const val DEFAULT_ENUM_PACKAGE = "enums"
        private const val DEFAULT_DOMAIN_EVENT_PACKAGE = "events"
        private const val DEFAULT_SCHEMA_BASE_CLASS_NAME = "Schema"
    }

    @Internal
    val tableMap = mutableMapOf<String, Map<String, Any?>>()

    @Internal
    val columnsMap = mutableMapOf<String, List<Map<String, Any?>>>()

    @Internal
    val relations = mutableMapOf<String, Map<String, String>>()

    @Internal
    val tablePackageMap = mutableMapOf<String, String>()

    @Internal
    val tableModuleMap = mutableMapOf<String, String>()

    @Internal
    val tableAggregateMap = mutableMapOf<String, String>()

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

    fun alias4Design(name: String): String = when (name.lowercase()) {
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

    override fun renderTemplate(
        templateNodes: List<TemplateNode>,
        parentPath: String,
    ) {
        templateNodes.forEach { templateNode ->
            val alias = alias4Design(templateNode.tag!!)
            when (alias) {
                "aggregate" -> aggregatesPath = parentPath
                "schema_base" -> schemaPath = parentPath
                "domain_event_handler" -> subscriberPath = parentPath
            }

            templateNodeMap.computeIfAbsent(alias) { mutableListOf() }.add(templateNode)
        }
    }

    @TaskAction
    override fun generate() {
        renderFileSwitch = false
        super.generate()
        genEntity()
    }

    fun resolveDatabaseConfig(): Triple<String, String, String> {
        val ext = extension.get()
        return Triple(
            ext.database.url.get(),
            ext.database.username.get(),
            ext.database.password.get()
        )
    }

    fun resolveTables(): List<Map<String, Any?>> {
        SqlSchemaUtils.loadLogger(logger)
        val (url, username, password) = resolveDatabaseConfig()
        return SqlSchemaUtils.resolveTables(url, username, password)
    }

    fun resolveColumns(): List<Map<String, Any?>> {
        SqlSchemaUtils.loadLogger(logger)
        val (url, username, password) = resolveDatabaseConfig()
        return SqlSchemaUtils.resolveColumns(url, username, password)
    }

    fun logSystemInfo() {
        val ext = extension.get()
        val (url, username, password) = resolveDatabaseConfig()

        with(ext) {
            logger.info("数据库连接：$url")
            logger.info("数据库账号：$username")
            logger.info("数据库密码：$password")
            logger.info("数据库名称：${database.schema.get()}")
            logger.info("包含表：${database.tables.get()}")
            logger.info("忽略表：${database.ignoreTables.get()}")
            logger.info("乐观锁字段：${generation.versionField.get()}")
            logger.info("逻辑删除字段：${generation.deletedField.get()}")
            logger.info("只读字段：${generation.readonlyFields.get()}")
            logger.info("忽略字段：${generation.ignoreFields.get()}")
        }

        logger.info("")
        logProjectStructureInfo()
        logger.info("")
        logger.info("")
    }

    fun logProjectStructureInfo() {
        val ext = extension.get()

        if (ext.basePackage.get().isBlank()) {
            ext.basePackage.set(resolveDefaultBasePackage(getDomainModulePath()))
        }

        with(ext.generation) {
            logger.info("实体类基类：${entityBaseClass.get()}")
            logger.info("主键ID生成器: ${idGenerator.get()}")
            logger.info("日期类型映射: ${datePackage.get()}")
            logger.info("枚举值字段名称: ${enumValueField.get()}")
            logger.info("枚举名字段名称: ${enumNameField.get()}")
            logger.info("枚举不匹配是否抛出异常: ${enumUnmatchedThrowException.get()}")
            logger.info("类型强制映射规则: ")
            typeRemapping.get().forEach { (key, value) ->
                logger.info("  $key <-> $value")
            }
            logger.info("生成Schema：${if (generateSchema.get()) "是" else "否"}")
            if (generateSchema.get()) {
                logger.info("  输出模式：${getEntitySchemaOutputMode()}")
                logger.info("  输出路径：${getEntitySchemaOutputPackage()}")
            }
            logger.info("生成聚合封装类：${if (generateAggregate.get()) "是" else "否"}")
        }
    }

    fun processTablesAndColumns(tables: List<Map<String, Any?>>, allColumns: List<Map<String, Any?>>) {
        logger.info("----------------解析数据库表----------------")
        logger.info("")
        logger.info("数据库类型：$dbType")

        val maxTableNameLength = tables.maxOfOrNull { SqlSchemaUtils.getTableName(it).length } ?: 20

        // 缓存表和列信息
        tables.forEach { table ->
            val tableName = SqlSchemaUtils.getTableName(table)
            val tableColumns = allColumns.filter { column ->
                SqlSchemaUtils.isColumnInTable(column, table)
            }.sortedBy { SqlSchemaUtils.getOrdinalPosition(it) }

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
                )
            )
        }
        logger.info("")
        logger.info("")
    }

    fun processTableRelations() {
        logger.info("----------------开始字段扫描----------------")
        logger.info("")

        // 解析表关系
        tableMap.values.forEach { table ->
            val tableName = SqlSchemaUtils.getTableName(table)
            val tableColumns = columnsMap[tableName]!!

            logger.info("开始解析表关系:$tableName")
            val relationTable = resolveRelationTable(table, tableColumns)

            relationTable.forEach { (key, value) ->
                relations.merge(key, value) { existing, new ->
                    existing.toMutableMap().apply { putAll(new) }
                }
            }

            tablePackageMap[tableName] = resolveEntityFullPackage(
                table,
                extension.get().basePackage.get(),
                getDomainModulePath()
            )
            logger.info("结束解析表关系:$tableName")
            logger.info("")
        }

        logger.info("----------------完成字段扫描----------------")
        logger.info("")
        logger.info("")
    }

    fun processEnumConfigurations() {
        // 解析枚举配置
        tableMap.values.forEach { table ->
            if (isIgnoreTable(table)) return@forEach

            val tableName = SqlSchemaUtils.getTableName(table)
            val tableColumns = columnsMap[tableName]!!

            tableColumns.forEach { column ->
                if (SqlSchemaUtils.hasEnum(column) && !isIgnoreColumn(column)) {
                    val enumConfig = SqlSchemaUtils.getEnum(column)
                    if (enumConfig.isNotEmpty()) {
                        val enumType = SqlSchemaUtils.getType(column)
                        enumConfigMap[enumType] = enumConfig

                        val enumPackage = buildString {
                            val packageName = templateNodeMap["enum"]
                                ?.takeIf { it.isNotEmpty() }
                                ?.get(0)?.name
                                ?.takeIf { it.isNotBlank() }
                                ?: DEFAULT_ENUM_PACKAGE

                            if (packageName.isNotBlank()) {
                                append(".$packageName")
                            }
                        }

                        enumPackageMap[enumType] =
                            "${extension.get().basePackage.get()}.${resolveEntityPackage(tableName)}$enumPackage"
                        enumTableNameMap[enumType] = tableName
                    }
                }
            }
        }
    }

    fun processEntityType(tableName: String): String =
        entityTypeMap.computeIfAbsent(tableName) {
            val table = tableMap[tableName]!!
            val type = SqlSchemaUtils.getType(table).takeIf { it.isNotBlank() }
                ?: toUpperCamelCase(tableName)
                ?: throw RuntimeException("实体类名未生成")

            logger.info("解析实体类名: ${SqlSchemaUtils.getTableName(table)} --> $type")
            type
        }

    fun genEntity() {
        logger.info("生成实体类...")

        // 设置 SqlSchemaUtils 的任务引用
        SqlSchemaUtils.task = this

        logSystemInfo()

        runCatching {
            // 数据库解析
            val (url, _, _) = resolveDatabaseConfig()
            dbType = SqlSchemaUtils.recognizeDbType(url)
            SqlSchemaUtils.processSqlDialect(dbType)

            val tables = resolveTables()
            if (tables.isEmpty()) {
                logger.warn("没有找到匹配的表")
                return
            }

            processTablesAndColumns(tables, resolveColumns())
            processTableRelations()
            processEnumConfigurations()
            generateEnums(tablePackageMap)
            generateEntities(relations, tablePackageMap)

        }.onFailure { error ->
            logger.error("生成实体类失败", error)
            throw error
        }
    }

    fun generateEnums(tablePackageMap: Map<String, String>) {
        logger.info("----------------开始生成枚举----------------")
        logger.info("")

        enumConfigMap.forEach { (enumType, enumConfig) ->
            runCatching {
                writeEnumSourceFile(
                    enumConfig, enumType,
                    extension.get().generation.enumValueField.get(),
                    extension.get().generation.enumNameField.get(),
                    tablePackageMap, getDomainModulePath()
                )
            }.onFailure { e ->
                logger.error("生成枚举失败: $enumType", e)
            }
        }

        logger.info("----------------完成生成枚举----------------")
        logger.info("")
        logger.info("")
    }

    fun generateEntities(relations: Map<String, Map<String, String>>, tablePackageMap: Map<String, String>) {
        logger.info("----------------开始生成实体----------------")
        logger.info("")

        val ext = extension.get()
        if (ext.generation.generateSchema.get()) {
            runCatching {
                writeSchemaBaseSourceFile(getDomainModulePath())
            }.onFailure { e ->
                logger.error("生成Schema基类失败", e)
            }
        }

        tableMap.values.forEach { table ->
            val tableName = SqlSchemaUtils.getTableName(table)
            val tableColumns = columnsMap[tableName]!!

            runCatching {
                buildEntitySourceFile(
                    table, tableColumns, tablePackageMap, relations,
                    ext.basePackage.get(), getDomainModulePath()
                )
            }.onFailure { e ->
                logger.error("生成实体失败: $tableName", e)
            }
        }

        logger.info("----------------完成生成实体----------------")
        logger.info("")
    }

    fun generateFieldComment(column: Map<String, Any?>): List<String> {
        val fieldName = SqlSchemaUtils.getColumnName(column)
        val fieldType = SqlSchemaUtils.getColumnType(column)

        return buildList {
            add("/**")

            SqlSchemaUtils.getComment(column)
                .split(PATTERN_LINE_BREAK.toRegex())
                .filter { it.isNotEmpty() }
                .forEach { add(" * $it") }

            if (SqlSchemaUtils.hasEnum(column)) {
                logger.info("获取枚举 java类型：$fieldName -> $fieldType")
                val enumMap = enumConfigMap[fieldType] ?: enumConfigMap[SqlSchemaUtils.getType(column)]
                enumMap?.entries?.forEach { (key, value) ->
                    add(" * $key:${value[0]}:${value[1]}")
                }
            }

            if (fieldName == extension.get().generation.versionField.get()) {
                add(" * 数据版本（支持乐观锁）")
            }

            if (extension.get().generation.generateDbType.get()) {
                add(" * ${SqlSchemaUtils.getColumnDbType(column)}")
            }

            add(" */")
        }
    }

    fun resolveEntityFullPackage(table: Map<String, Any?>, basePackage: String, baseDir: String): String {
        val tableName = SqlSchemaUtils.getTableName(table)
        val packageName = concatPackage(basePackage, resolveEntityPackage(tableName))
        return packageName
    }

    fun resolveEntityPackage(tableName: String): String {
        val module = resolveModule(tableName)
        val aggregate = resolveAggregate(tableName)
        return concatPackage(resolveAggregatesPackage(), module, aggregate.lowercase())
    }

    fun resolveAggregatesPath(): String {
        if (aggregatesPath.isNotBlank()) return aggregatesPath

        return resolvePackageDirectory(
            getDomainModulePath(),
            "${extension.get().basePackage.get()}.${AGGREGATE_PACKAGE}"
        )
    }

    fun resolveSchemaPath(): String {
        if (schemaPath.isNotBlank()) return schemaPath

        return resolvePackageDirectory(
            getDomainModulePath(),
            "${extension.get().basePackage.get()}.${getEntitySchemaOutputPackage()}"
        )
    }

    fun resolveSubscriberPath(): String {
        if (subscriberPath.isNotBlank()) return subscriberPath

        return resolvePackageDirectory(
            getApplicationModulePath(),
            "${extension.get().basePackage.get()}.${DOMAIN_EVENT_SUBSCRIBER_PACKAGE}"
        )
    }

    fun resolveAggregatesPackage(): String {
        return resolvePackage(
            "${resolveAggregatesPath()}${File.separator}X.kt"
        ).substring(extension.get().basePackage.get().length + 1)
    }

    fun resolveSchemaPackage(): String {
        return resolvePackage(
            "${resolveSchemaPath()}${File.separator}X.kt"
        ).substring(
            if (extension.get().basePackage.get().isBlank()) 0 else (extension.get().basePackage.get().length + 1)
        )
    }

    fun resolveSubscriberPackage(): String {
        return resolvePackage(
            "${resolveSubscriberPath()}${File.separator}X.kt"
        ).substring(extension.get().basePackage.get().length + 1)
    }

    fun resolveModule(tableName: String): String =
        tableModuleMap.computeIfAbsent(tableName) {
            var result = ""
            generateSequence(tableMap[tableName]) { currentTable ->
                val parent = SqlSchemaUtils.getParent(currentTable)
                if (parent.isBlank()) null else tableMap[parent]
            }.forEach { table ->
                val module = SqlSchemaUtils.getModule(table)
                val tableNameStr = SqlSchemaUtils.getTableName(table)
                logger.info("尝试${if (table == tableMap[tableName]) "" else "父表"}模块: $tableNameStr ${module.ifBlank { "[缺失]" }}")

                if (SqlSchemaUtils.isAggregateRoot(table) || module.isNotBlank()) {
                    result = module
                    return@forEach
                }
            }
            logger.info("模块解析结果: $tableName ${result.ifBlank { "[无]" }}")
            result
        }

    fun resolveAggregate(tableName: String): String =
        tableAggregateMap.computeIfAbsent(tableName) {
            var aggregateRootTableName = tableName
            var result = ""

            generateSequence(tableMap[tableName]) { currentTable ->
                val parent = SqlSchemaUtils.getParent(currentTable)
                if (parent.isBlank()) null else {
                    tableMap[parent]?.also {
                        aggregateRootTableName = SqlSchemaUtils.getTableName(it)
                    }
                }
            }.forEach { table ->
                val aggregate = SqlSchemaUtils.getAggregate(table)
                val tableNameStr = SqlSchemaUtils.getTableName(table)
                logger.info("尝试${if (table == tableMap[tableName]) "" else "父表"}聚合: $tableNameStr ${aggregate.ifBlank { "[缺失]" }}")

                if (SqlSchemaUtils.isAggregateRoot(table) || aggregate.isNotBlank()) {
                    result = aggregate.takeIf { it.isNotBlank() }
                        ?: (toSnakeCase(processEntityType(aggregateRootTableName)) ?: "")
                    return@forEach
                }
            }

            if (result.isBlank()) {
                result = toSnakeCase(processEntityType(aggregateRootTableName)) ?: ""
            }

            logger.info("聚合解析结果: $tableName ${result.ifBlank { "[缺失]" }}")
            result
        }

    fun resolveAggregateWithModule(tableName: String): String {
        val module = resolveModule(tableName)
        return if (module.isNotBlank()) {
            concatPackage(module, resolveAggregate(tableName))
        } else {
            resolveAggregate(tableName)
        }
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
                            "LAZY".equals(extension.get().generation.fetchType.get(), ignoreCase = true)
                        )
                        val columnName = SqlSchemaUtils.getColumnName(column)

                        // 在父表中记录子表的OneToMany关系
                        result[parent]!!.putIfAbsent(
                            tableName,
                            "OneToMany;$columnName${if (lazy) ";LAZY" else ""}"
                        )

                        // 处理子表对父表的引用
                        result.putIfAbsent(tableName, mutableMapOf())
                        val parentRelation = if (extension.get().generation.generateParent.get()) {
                            "*ManyToOne;$columnName${if (lazy) ";LAZY" else ""}"
                        } else {
                            "PLACEHOLDER;$columnName" // 使用占位符，防止聚合间关系误判
                        }
                        result[tableName]!!.putIfAbsent(parent, parentRelation)

                        rewrited = true
                    }
                }
            }
            if (!rewrited) {
                val column = columns.firstOrNull {
                    SqlSchemaUtils.getColumnName(it).equals("${parent}_id", ignoreCase = true)
                }
                if (column != null) {
                    val lazy = SqlSchemaUtils.isLazy(
                        column,
                        "LAZY".equals(extension.get().generation.fetchType.get(), ignoreCase = true)
                    )
                    val columnName = SqlSchemaUtils.getColumnName(column)

                    // 在父表中记录子表的OneToMany关系
                    result[parent]!!.putIfAbsent(
                        tableName,
                        "OneToMany;$columnName${if (lazy) ";LAZY" else ""}"
                    )

                    // 处理子表对父表的引用
                    result.putIfAbsent(tableName, mutableMapOf())
                    val parentRelation = if (extension.get().generation.generateParent.get()) {
                        "*ManyToOne;$columnName${if (lazy) ";LAZY" else ""}"
                    } else {
                        "PLACEHOLDER;$columnName" // 使用占位符，防止聚合间关系误判
                    }
                    result[tableName]!!.putIfAbsent(parent, parentRelation)
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
                        "LAZY".equals(extension.get().generation.fetchType.get(), ignoreCase = true)
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
                "LAZY".equals(extension.get().generation.fetchType.get(), ignoreCase = true)
            )

            if (colRel.isNotBlank() || SqlSchemaUtils.hasReference(column)) {
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

    fun isIgnoreTable(table: Map<String, Any?>): Boolean = SqlSchemaUtils.isIgnore(table)

    fun isIgnoreColumn(column: Map<String, Any?>): Boolean {
        if (SqlSchemaUtils.isIgnore(column)) return true

        val columnName = SqlSchemaUtils.getColumnName(column).lowercase()
        return extension.get().generation.ignoreFields.get()
            .split(PATTERN_SPLITTER.toRegex())
            .any { pattern -> columnName.matches(pattern.replace("%", ".*").toRegex()) }
    }

    fun isReservedColumn(column: Map<String, Any?>): Boolean =
        SqlSchemaUtils.getColumnName(column).equals(extension.get().generation.versionField.get(), ignoreCase = true)

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
            val parent = SqlSchemaUtils.getParent(table)
            val refMatchesParent = SqlSchemaUtils.hasReference(column) &&
                    parent.equals(SqlSchemaUtils.getReference(column), ignoreCase = true)
            val fkNameMatches = columnName.equals("${parent}_id", ignoreCase = true)
            if (refMatchesParent || fkNameMatches) return false
        }

        if (relations.containsKey(tableName)) {
            for (entry in relations[tableName]!!.entries) {
                val refInfos = entry.value!!.split(";".toRegex()).dropLastWhile { it.isEmpty() }.toTypedArray()
                when (refInfos[0]) {
                    "ManyToOne", "OneToOne" -> if (columnName.equals(refInfos[1], ignoreCase = true)) {
                        return false
                    }

                    "PLACEHOLDER" -> if (columnName.equals(refInfos[1], ignoreCase = true)) {
                        return false // PLACEHOLDER 关系的字段不生成
                    }

                    else -> {}
                }
            }
        }
        return true
    }

    fun isIdColumn(column: Map<String, Any?>): Boolean {
        return SqlSchemaUtils.isColumnPrimaryKey(column)
    }

    fun isReadOnlyColumn(column: Map<String, Any?>): Boolean {
        if (SqlSchemaUtils.hasReadOnly(column)) return true

        val columnName = SqlSchemaUtils.getColumnName(column).lowercase()
        val readonlyFields = extension.get().generation.readonlyFields.get()

        return readonlyFields.isNotBlank() && readonlyFields
            .lowercase()
            .split(PATTERN_SPLITTER.toRegex())
            .any { pattern -> columnName.matches(pattern.replace("%", ".*").toRegex()) }
    }

    fun isVersionColumn(column: Map<String, Any?>): Boolean {
        return SqlSchemaUtils.getColumnName(column) == extension.get().generation.versionField.get()
    }

    fun resolveColumn(
        columns: List<Map<String, Any?>>,
        columnName: String?,
    ): Map<String, Any?>? {
        return columns.firstOrNull {
            columnName == SqlSchemaUtils.getColumnName(it)
        }
    }

    fun resolveEntityIdGenerator(table: Map<String, Any?>): String {
        return when {
            SqlSchemaUtils.hasIdGenerator(table) -> {
                SqlSchemaUtils.getIdGenerator(table)
            }

            SqlSchemaUtils.isValueObject(table) -> {
                extension.get().generation.idGenerator4ValueObject.get().ifBlank {
                    // ValueObject 值对象 默认使用MD5
                    "com.only4.cap4k.ddd.domain.repo.Md5HashIdentifierGenerator"
                }
            }

            else -> {
                extension.get().generation.idGenerator.get().ifBlank {
                    ""
                }
            }
        }
    }

    fun resolveIdColumns(columns: List<Map<String, Any?>>): List<Map<String, Any?>> {
        return columns.filter { SqlSchemaUtils.isColumnPrimaryKey(it) }
    }

    fun processEntityCustomerSourceFile(
        filePath: String,
        importLines: MutableList<String>,
        annotationLines: MutableList<String>,
        customerLines: MutableList<String>,
    ): Boolean {
        val file = File(filePath)
        if (file.exists()) {
            val content = file.readText(charset(extension.get().outputEncoding.get()))
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
                addIfNone(
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
                val hibernateAnnotation = "\\b${importLine.substring(importLine.lastIndexOf(".") + 1).trim()}\\b"
                if (!content.contains(hibernateAnnotation.toRegex())) {
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

        // 移除并重新添加 @Aggregate 注解
        removeText(annotationLines, """@Aggregate\(.*\)""")
        addIfNone(
            annotationLines,
            """@Aggregate\(.*\)""",
            """@Aggregate(aggregate = "${toUpperCamelCase(resolveAggregateWithModule(tableName))}", name = "${
                processEntityType(
                    tableName
                )
            }", root = ${SqlSchemaUtils.isAggregateRoot(table)}, type = ${if (SqlSchemaUtils.isValueObject(table)) "Aggregate.TYPE_VALUE_OBJECT" else "Aggregate.TYPE_ENTITY"}, description = "${
                SqlSchemaUtils.getComment(
                    table
                ).replace(Regex(PATTERN_LINE_BREAK), "\\\\n")
            }")"""
        ) { _, _ -> 0 }

        // 添加 JPA 基本注解
        addIfNone(annotationLines, """@Entity(\(.*\))?""", "@Entity")

        val ids = resolveIdColumns(columns)
        if (ids.size > 1) {
            addIfNone(
                annotationLines,
                """@IdClass(\(.*\))""",
                "@IdClass(${processEntityType(tableName)}.${DEFAULT_MUL_PRI_KEY_NAME}::class)"
            )
        }

        addIfNone(
            annotationLines,
            """@Table(\(.*\))?""",
            "@Table(name = \"$LEFT_QUOTES_4_ID_ALIAS$tableName$RIGHT_QUOTES_4_ID_ALIAS\")"
        )

        addIfNone(annotationLines, """@DynamicInsert(\(.*\))?""", "@DynamicInsert")
        addIfNone(annotationLines, """@DynamicUpdate(\(.*\))?""", "@DynamicUpdate")

        // 处理软删除相关注解
        val ext = extension.get()
        val deletedField = ext.generation.deletedField.get()
        val versionField = ext.generation.versionField.get()

        if (deletedField.isNotBlank() && hasColumn(deletedField, columns)) {
            if (ids.isEmpty()) {
                throw RuntimeException("实体缺失【主键】：$tableName")
            }

            val idFieldName = if (ids.size == 1) {
                toLowerCamelCase(SqlSchemaUtils.getColumnName(ids[0]))
                    ?: SqlSchemaUtils.getColumnName(ids[0])
            } else {
                "(${
                    ids.joinToString(", ") {
                        toLowerCamelCase(SqlSchemaUtils.getColumnName(it)) ?: SqlSchemaUtils.getColumnName(
                            it
                        )
                    }
                })"
            }

            val idFieldValue = if (ids.size == 1) "?" else "(" + ids.joinToString(", ") { "?" } + ")"

            if (hasColumn(versionField, columns)) {
                addIfNone(
                    annotationLines,
                    """@SQLDelete(\(.*\))?""",
                    """@SQLDelete(sql = "update $LEFT_QUOTES_4_ID_ALIAS$tableName$RIGHT_QUOTES_4_ID_ALIAS set $LEFT_QUOTES_4_ID_ALIAS$deletedField$RIGHT_QUOTES_4_ID_ALIAS = $LEFT_QUOTES_4_ID_ALIAS$idFieldName$RIGHT_QUOTES_4_ID_ALIAS where $LEFT_QUOTES_4_ID_ALIAS$idFieldName$RIGHT_QUOTES_4_ID_ALIAS = $idFieldValue and $LEFT_QUOTES_4_ID_ALIAS$versionField$RIGHT_QUOTES_4_ID_ALIAS = ?")"""
                )
            } else {
                addIfNone(
                    annotationLines,
                    """@SQLDelete(\(.*\))?""",
                    """@SQLDelete(sql = "update $LEFT_QUOTES_4_ID_ALIAS$tableName$RIGHT_QUOTES_4_ID_ALIAS set $LEFT_QUOTES_4_ID_ALIAS$deletedField$RIGHT_QUOTES_4_ID_ALIAS = $LEFT_QUOTES_4_ID_ALIAS$idFieldName$RIGHT_QUOTES_4_ID_ALIAS where $LEFT_QUOTES_4_ID_ALIAS$idFieldName$RIGHT_QUOTES_4_ID_ALIAS = $idFieldValue")"""
                )
            }

//            if (hasColumn(versionField, columns) && !hasLine(
//                    annotationLines,
//                    "@SQLDelete(\\(.*$versionField.*\\))"
//                )
//            ) {
//                replaceText(
//                    annotationLines,
//                    "@SQLDelete(\\(.*\\))?",
//                    """
//                    "@SQLDelete(sql = "update $LEFT_QUOTES_4_ID_ALIAS$tableName$RIGHT_QUOTES_4_ID_ALIAS set $LEFT_QUOTES_4_ID_ALIAS$deletedField$RIGHT_QUOTES_4_ID_ALIAS = $LEFT_QUOTES_4_ID_ALIAS$idFieldName$RIGHT_QUOTES_4_ID_ALIAS where $LEFT_QUOTES_4_ID_ALIAS$idFieldName$RIGHT_QUOTES_4_ID_ALIAS = $idFieldValue)
//                    """.trimIndent()
//                )
//            }

            addIfNone(
                annotationLines,
                """@Where(\(.*\))?""",
                """@Where(clause = "$LEFT_QUOTES_4_ID_ALIAS$deletedField$RIGHT_QUOTES_4_ID_ALIAS = 0")"""
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

        val entityType = processEntityType(tableName)
        val entityFullPackage = tablePackageMap[tableName] ?: return

        // 创建输出目录
        File(resolvePackageDirectory(baseDir, entityFullPackage)).mkdirs()

        val filePath = resolveSourceFile(baseDir, entityFullPackage, entityType)

        val enums = mutableListOf<String>()
        val importLines = mutableListOf<String>()
        val annotationLines = mutableListOf<String>()
        val customerLines = mutableListOf<String>()

        if (!processEntityCustomerSourceFile(filePath, importLines, annotationLines, customerLines)) {
            logger.warn("文件被改动，无法自动更新！$filePath")
            return
        }

        processAnnotationLines(table, columns, annotationLines)
        val mainSource =
            writeEntityClass(table, columns, tablePackageMap, relations, enums, annotationLines, customerLines)
        processImportLines(table, importLines, mainSource)

        logger.info("开始生成实体文件：$filePath")

        val file = File(filePath)
        if (!file.exists() || !file.readText(charset(extension.get().outputEncoding.get()))
                .contains(FLAG_DO_NOT_OVERWRITE)
        ) {
            file.writeText(
                """package $entityFullPackage
                |${importLines.joinToString("\n")}
                |$mainSource""".trimMargin(),
                charset(extension.get().outputEncoding.get())
            )
        }

        val ext = extension.get()
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
        val entityType = processEntityType(tableName)
        val ids = resolveIdColumns(columns)

        if (ids.isEmpty()) {
            throw RuntimeException("实体缺失【主键】：$tableName")
        }

        val identityType = if (ids.size != 1) "Long" else SqlSchemaUtils.getColumnType(ids[0])

        val stringWriter = StringWriter()
        val out = BufferedWriter(stringWriter)

        // Write annotation lines
        annotationLines.forEach { line -> writeLine(out, line) }

        // Determine base class
        var baseClass: String? = null
        when {
            SqlSchemaUtils.isAggregateRoot(table) && extension.get().generation.rootEntityBaseClass.get()
                .isNotBlank() -> {
                baseClass = extension.get().generation.rootEntityBaseClass.get()
            }

            extension.get().generation.entityBaseClass.get().isNotBlank() -> {
                baseClass = extension.get().generation.entityBaseClass.get()
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

        writeLine(out, "class $entityType$extendsClause$implementsClause (")

        writeLine(
            out,
            "    // 【字段映射开始】本段落由[cap4k-ddd-codegen-gradle-plugin]维护，请不要手工改动"
        )

        // Write column properties
        for (column in columns) {
            writeColumnProperty(out, table, column, ids, relations, enums)
        }

        writeLine(out, ") {")

        // Write relation properties
        writeRelationProperty(out, table, relations, tablePackageMap)

        writeLine(out, "")
        writeLine(
            out,
            "    // 【字段映射结束】本段落由[cap4k-ddd-codegen-gradle-plugin]维护，请不要手工改动"
        )

//         Write customer lines or default behavior methods
        if (customerLines.isNotEmpty()) {
            customerLines.forEach { line -> writeLine(out, line) }
        } else {
            writeLine(out, "")
            writeLine(out, "    // 【行为方法开始】")
            writeLine(out, "")
            writeLine(out, "")
            writeLine(out, "")
            writeLine(out, "    // 【行为方法结束】")
            writeLine(out, "")
        }

        writeLine(out, "}")
        writeLine(out, "")

        // Value object implementation
//        if (SqlSchemaUtils.isValueObject(table)) {
//            val idFieldName =
//                if (ids.size != 1) "" else toLowerCamelCase(SqlSchemaUtils.getColumnName(ids[0]))
//                    ?: SqlSchemaUtils.getColumnName(ids[0])
//            val idTypeName = if (ids.size != 1) "Long" else SqlSchemaUtils.getColumnType(ids[0])
//
//            val hashTemplate = when {
//                extension.get().generation.hashMethod4ValueObject.get().isNotBlank() -> {
//                    "    " + extension.get().generation.hashMethod4ValueObject.get().trim()
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
//            writeLine(out, "")
//            writeLine(
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
//            writeLine(out, "")
//            writeLine(
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
//            writeLine(out, "")
//        }

        // Multiple primary keys (adapted for Kotlin data class)
//        if (ids.size > 1) {
//            writeLine(out, "")
//            writeLine(out, "    data class $DEFAULT_MUL_PRI_KEY_NAME(")
//            ids.forEachIndexed { index, id ->
//                val columnName = SqlSchemaUtils.getColumnName(id)
//                val leftQuote = LEFT_QUOTES_4_ID_ALIAS.replace("\"", "\\\"")
//                val rightQuote = RIGHT_QUOTES_4_ID_ALIAS.replace("\"", "\\\"")
//                val type = SqlSchemaUtils.getColumnType(id)
//                val fieldName = toLowerCamelCase(columnName) ?: columnName
//
//                writeLine(out, "        @Column(name = \"$leftQuote$columnName$rightQuote\")")
//                val suffix = if (index == ids.size - 1) "" else ","
//                writeLine(out, "        val $fieldName: $type$suffix")
//            }
//            writeLine(out, "    ) : java.io.Serializable")
//        }

        out.flush()
        out.close()
        return stringWriter.toString()
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
            ) && columnName != extension.get().generation.versionField.get()
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

        writeLine(out, "")
        writeFieldComment(out, column)

        // ID annotation
        if (isIdColumn(column)) {
            writeLine(out, "    @Id")
            if (ids.size == 1) {
                val entityIdGenerator = resolveEntityIdGenerator(table)
                when {
                    SqlSchemaUtils.isValueObject(table) -> {
                        // 不使用ID生成器
                    }

                    entityIdGenerator.isNotEmpty() -> {
                        writeLine(out, "    @GeneratedValue(generator = \"$entityIdGenerator\")")
                        writeLine(
                            out,
                            "    @GenericGenerator(name = \"$entityIdGenerator\", strategy = \"$entityIdGenerator\")"
                        )
                    }

                    else -> {
                        // 无ID生成器 使用数据库自增
                        writeLine(out, "    @GeneratedValue(strategy = GenerationType.IDENTITY)")
                    }
                }
            }
        }

        // Version annotation
        if (isVersionColumn(column)) {
            writeLine(out, "    @Version")
        }

        // Enum converter annotation
        if (SqlSchemaUtils.hasEnum(column)) {
            enums.add(columnType)
            writeLine(out, "    @Convert(converter = $columnType.Converter::class)")
        }

        // Column annotation
        val leftQuote = LEFT_QUOTES_4_ID_ALIAS.replace("\"", "\\\"")
        val rightQuote = RIGHT_QUOTES_4_ID_ALIAS.replace("\"", "\\\"")

        if (!updatable || !insertable) {
            writeLine(
                out,
                "    @Column(name = \"$leftQuote$columnName$rightQuote\", insertable = $insertable, updatable = $updatable)"
            )
        } else {
            writeLine(out, "    @Column(name = \"$leftQuote$columnName$rightQuote\")")
        }

        // Property declaration with default value if needed
        val fieldName = toLowerCamelCase(columnName) ?: columnName
        val defaultJavaLiteral = SqlSchemaUtils.getColumnDefaultLiteral(column)
        val defaultValue = " = $defaultJavaLiteral"
        writeLine(out, "    var $fieldName: $columnType$defaultValue,")
    }

    fun writeFieldComment(
        out: BufferedWriter,
        column: Map<String, Any?>,
    ) {
        val comments = generateFieldComment(column)
        for (line in comments) {
            writeLine(out, "    $line")
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

            // 跳过占位符关系
            if (refInfos[0] == "PLACEHOLDER") {
                continue
            }

            val fetchType = when {
                relationInfo.endsWith(";LAZY") -> "LAZY"
                SqlSchemaUtils.hasLazy(navTable) -> if (SqlSchemaUtils.isLazy(navTable, false)) "LAZY" else "EAGER"
                else -> "EAGER"
            }

            val relation = refInfos[0]
            val joinColumn = refInfos[1]
            val fetchAnnotation = "" // For Kotlin, we might not need Hibernate fetch annotations

            writeLine(out, "")

            when (relation) {
                "OneToMany" -> {
                    // 专属聚合内关系
                    writeLine(
                        out,
                        "    @${relation}(cascade = [CascadeType.ALL], fetch = FetchType.$fetchType, orphanRemoval = true)$fetchAnnotation"
                    )
                    writeLine(out, "    @Fetch(FetchMode.SUBSELECT)")
                    val leftQuote = LEFT_QUOTES_4_ID_ALIAS.replace("\"", "\\\"")
                    val rightQuote = RIGHT_QUOTES_4_ID_ALIAS.replace("\"", "\\\"")
                    writeLine(
                        out,
                        "    @JoinColumn(name = \"$leftQuote$joinColumn$rightQuote\", nullable = false)"
                    )

                    val countIsOne = SqlSchemaUtils.countIsOne(navTable)

                    val fieldName = Inflector.pluralize(
                        toLowerCamelCase(processEntityType(refTableName)) ?: processEntityType(refTableName)
                    )
                    val entityPackage = tablePackageMap[refTableName] ?: ""
                    val entityType = processEntityType(refTableName)
                    writeLine(
                        out,
                        "    var $fieldName: MutableList<$entityPackage.$entityType> = mutableListOf()"
                    )

                    if (countIsOne) {
                        writeLine(out, "")
                        writeLine(out, "    fun load$entityType(): $entityPackage.$entityType? {")
                        writeLine(
                            out,
                            "        return if ($fieldName.isEmpty()) null else $fieldName[0]"
                        )
                        writeLine(out, "    }")
                    }
                }

                "*ManyToOne" -> {
                    writeLine(
                        out,
                        "    @${relation.replace("*", "")}(cascade = [], fetch = FetchType.$fetchType)$fetchAnnotation"
                    )
                    val leftQuote = LEFT_QUOTES_4_ID_ALIAS.replace("\"", "\\\"")
                    val rightQuote = RIGHT_QUOTES_4_ID_ALIAS.replace("\"", "\\\"")
                    writeLine(
                        out,
                        "    @JoinColumn(name = \"$leftQuote$joinColumn$rightQuote\", nullable = false, insertable = false, updatable = false)"
                    )
                    val entityPackage = tablePackageMap[refTableName] ?: ""
                    val entityType = processEntityType(refTableName)
                    val fieldName = toLowerCamelCase(entityType) ?: entityType
                    writeLine(out, "    var $fieldName: $entityPackage.$entityType? = null")
                }

                "ManyToOne" -> {
                    writeLine(
                        out,
                        "    @${relation}(cascade = [], fetch = FetchType.$fetchType)$fetchAnnotation"
                    )
                    val leftQuote = LEFT_QUOTES_4_ID_ALIAS.replace("\"", "\\\"")
                    val rightQuote = RIGHT_QUOTES_4_ID_ALIAS.replace("\"", "\\\"")
                    writeLine(
                        out,
                        "    @JoinColumn(name = \"$leftQuote$joinColumn$rightQuote\", nullable = false)"
                    )
                    val entityPackage = tablePackageMap[refTableName] ?: ""
                    val entityType = processEntityType(refTableName)
                    val fieldName = toLowerCamelCase(entityType) ?: entityType
                    writeLine(out, "    var $fieldName: $entityPackage.$entityType? = null")
                }

                "*OneToMany" -> {
                    // 当前不会用到，无法控制集合数量规模
                    val entityTypeName = processEntityType(tableName)
                    val fieldNameFromTable = toLowerCamelCase(entityTypeName) ?: entityTypeName
                    writeLine(
                        out,
                        "    @${
                            relation.replace(
                                "*",
                                ""
                            )
                        }(mappedBy = \"$fieldNameFromTable\", cascade = [], fetch = FetchType.$fetchType)$fetchAnnotation"
                    )
                    writeLine(out, "    @Fetch(FetchMode.SUBSELECT)")
                    val entityPackage = tablePackageMap[refTableName] ?: ""
                    val entityType = processEntityType(refTableName)
                    val fieldName = Inflector.pluralize(toLowerCamelCase(entityType) ?: entityType)
                    writeLine(
                        out,
                        "    var $fieldName: MutableList<$entityPackage.$entityType> = mutableListOf()"
                    )
                }

                "OneToOne" -> {
                    writeLine(
                        out,
                        "    @${relation}(cascade = [], fetch = FetchType.$fetchType)$fetchAnnotation"
                    )
                    val leftQuote = LEFT_QUOTES_4_ID_ALIAS.replace("\"", "\\\"")
                    val rightQuote = RIGHT_QUOTES_4_ID_ALIAS.replace("\"", "\\\"")
                    writeLine(
                        out,
                        "    @JoinColumn(name = \"$leftQuote$joinColumn$rightQuote\", nullable = false)"
                    )
                    val entityPackage = tablePackageMap[refTableName] ?: ""
                    val entityType = processEntityType(refTableName)
                    val fieldName = toLowerCamelCase(entityType) ?: entityType
                    writeLine(out, "    var $fieldName: $entityPackage.$entityType? = null")
                }

                "*OneToOne" -> {
                    val entityTypeName = processEntityType(tableName)
                    val fieldNameFromTable = toLowerCamelCase(entityTypeName) ?: entityTypeName
                    writeLine(
                        out,
                        "    @${
                            relation.replace(
                                "*",
                                ""
                            )
                        }(mappedBy = \"$fieldNameFromTable\", cascade = [], fetch = FetchType.$fetchType)$fetchAnnotation"
                    )
                    val entityPackage = tablePackageMap[refTableName] ?: ""
                    val entityType = processEntityType(refTableName)
                    val fieldName = toLowerCamelCase(entityType) ?: entityType
                    writeLine(out, "    var $fieldName: $entityPackage.$entityType? = null")
                }

                "ManyToMany" -> {
                    writeLine(
                        out,
                        "    @${relation}(cascade = [], fetch = FetchType.$fetchType)$fetchAnnotation"
                    )
                    writeLine(out, "    @Fetch(FetchMode.SUBSELECT)")
                    val leftQuote = LEFT_QUOTES_4_ID_ALIAS.replace("\"", "\\\"")
                    val rightQuote = RIGHT_QUOTES_4_ID_ALIAS.replace("\"", "\\\"")
                    val joinTableName = refInfos[3]
                    val inverseJoinColumn = refInfos[2]
                    writeLine(
                        out, "    @JoinTable(name = \"$leftQuote$joinTableName$rightQuote\", " +
                                "joinColumns = [JoinColumn(name = \"$leftQuote$joinColumn$rightQuote\", nullable = false)], " +
                                "inverseJoinColumns = [JoinColumn(name = \"$leftQuote$inverseJoinColumn$rightQuote\", nullable = false)])"
                    )
                    val entityPackage = tablePackageMap[refTableName] ?: ""
                    val entityType = processEntityType(refTableName)
                    val fieldName = Inflector.pluralize(toLowerCamelCase(entityType) ?: entityType)
                    writeLine(
                        out,
                        "    var $fieldName: MutableList<$entityPackage.$entityType> = mutableListOf()"
                    )
                }

                "*ManyToMany" -> {
                    val entityTypeName = processEntityType(tableName)
                    val fieldNameFromTable =
                        Inflector.pluralize(toLowerCamelCase(entityTypeName) ?: entityTypeName)
                    writeLine(
                        out,
                        "    @${
                            relation.replace(
                                "*",
                                ""
                            )
                        }(mappedBy = \"$fieldNameFromTable\", cascade = [], fetch = FetchType.$fetchType)$fetchAnnotation"
                    )
                    writeLine(out, "    @Fetch(FetchMode.SUBSELECT)")
                    val entityPackage = tablePackageMap[refTableName] ?: ""
                    val entityType = processEntityType(refTableName)
                    val fieldName = Inflector.pluralize(toLowerCamelCase(entityType) ?: entityType)
                    writeLine(
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
        val entityType = processEntityType(tableName)
        val entityVar = toLowerCamelCase(entityType) ?: entityType

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
        putContext(tag, "templatePackage", refPackage(resolveAggregatesPackage()), context)
        putContext(tag, "package", refPackage(aggregate), context)
        putContext(tag, "path", aggregate.replace(".", File.separator), context)
        putContext(tag, "Aggregate", toUpperCamelCase(aggregate) ?: aggregate, context)
        putContext(tag, "Comment", comment, context)
        putContext(tag, "CommentEscaped", comment.replace(Regex(PATTERN_LINE_BREAK), "  "), context)
        putContext(
            tag,
            "entityPackage",
            refPackage(entityFullPackage, extension.get().basePackage.get()),
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
                val pathNode = templateNode.deepCopy().resolve(context)
                val path = forceRender(
                    pathNode,
                    resolvePackageDirectory(
                        baseDir,
                        concatPackage(
                            extension.get().basePackage.get(),
                            context["templatePackage"] ?: ""
                        )
                    )
                )
                logger.info(
                    resolvePackageDirectory(
                        baseDir,
                        concatPackage(
                            extension.get().basePackage.get(),
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
        val entityType = processEntityType(tableName)
        val entityVar = toLowerCamelCase(entityType) ?: entityType

        val context = getEscapeContext().toMutableMap()
        putContext(tag, "Name", "${entityType}Factory", context)
        putContext(tag, "Factory", context["Name"] ?: "", context)
        putContext(tag, "templatePackage", refPackage(resolveAggregatesPackage()), context)
        putContext(tag, "package", refPackage(aggregate), context)
        putContext(tag, "path", aggregate.replace(".", File.separator), context)
        putContext(tag, "Aggregate", toUpperCamelCase(aggregate) ?: aggregate, context)
        putContext(tag, "Comment", "", context)
        putContext(tag, "CommentEscaped", "", context)
        putContext(
            tag,
            "entityPackage",
            refPackage(entityFullPackage, extension.get().basePackage.get()),
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
                val pathNode = templateNode.deepCopy().resolve(context)
                val path = forceRender(
                    pathNode,
                    resolvePackageDirectory(
                        baseDir,
                        concatPackage(
                            extension.get().basePackage.get(),
                            context["templatePackage"] ?: ""
                        )
                    )
                )
                logger.info(
                    resolvePackageDirectory(
                        baseDir,
                        concatPackage(
                            extension.get().basePackage.get(),
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
        val entityType = processEntityType(tableName)
        val entityVar = toLowerCamelCase(entityType) ?: entityType

        val context = getEscapeContext().toMutableMap()
        putContext(tag, "Name", "${entityType}Specification", context)
        putContext(tag, "Specification", context["Name"] ?: "", context)
        putContext(tag, "templatePackage", refPackage(resolveAggregatesPackage()), context)
        putContext(tag, "package", refPackage(aggregate), context)
        putContext(tag, "path", aggregate.replace(".", File.separator), context)
        putContext(tag, "Aggregate", toUpperCamelCase(aggregate) ?: aggregate, context)
        putContext(tag, "Comment", "", context)
        putContext(tag, "CommentEscaped", "", context)
        putContext(
            tag,
            "entityPackage",
            refPackage(entityFullPackage, extension.get().basePackage.get()),
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
                val pathNode = templateNode.deepCopy().resolve(context)
                val path = forceRender(
                    pathNode,
                    resolvePackageDirectory(
                        baseDir,
                        concatPackage(
                            extension.get().basePackage.get(),
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
        val entityType = processEntityType(tableName)
        val entityVar = toLowerCamelCase(entityType) ?: entityType

        val domainEventDescEscaped = domainEventDescription.replace(Regex(PATTERN_LINE_BREAK), "\\n")

        val context = getEscapeContext().toMutableMap()
        putContext(tag, "Name", domainEventClassName, context)
        putContext(tag, "DomainEvent", context["Name"] ?: "", context)
        putContext(tag, "domainEventPackage", refPackage(resolveAggregatesPackage()), context)
        putContext(tag, "domainEventHandlerPackage", refPackage(resolveSubscriberPackage()), context)
        putContext(tag, "package", refPackage(aggregate), context)
        putContext(tag, "path", aggregate.replace(".", File.separator), context)
        putContext(tag, "persist", "false", context)
        putContext(tag, "Aggregate", toUpperCamelCase(aggregate) ?: aggregate, context)
        putContext(tag, "Comment", domainEventDescEscaped, context)
        putContext(tag, "CommentEscaped", domainEventDescEscaped, context)
        putContext(
            tag,
            "entityPackage",
            refPackage(entityFullPackage, extension.get().basePackage.get()),
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
                val pathNode = templateNode.deepCopy().resolve(context)
                val path = forceRender(
                    pathNode,
                    resolvePackageDirectory(
                        baseDir,
                        concatPackage(
                            extension.get().basePackage.get(),
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
                val pathNode = templateNode.deepCopy().resolve(context)
                val path = forceRender(
                    pathNode,
                    resolvePackageDirectory(
                        getApplicationModulePath(),
                        concatPackage(
                            extension.get().basePackage.get(),
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
        val entityType = processEntityType(tableName)
        val entityVar = toLowerCamelCase(entityType) ?: entityType

        val context = getEscapeContext().toMutableMap()
        putContext(tag, "templatePackage", refPackage(resolveAggregatesPackage()), context)
        putContext(tag, "package", refPackage(aggregate), context)
        putContext(tag, "path", aggregate.replace(".", File.separator), context)
        putContext(tag, "Aggregate", toUpperCamelCase(aggregate) ?: aggregate, context)
        putContext(tag, "Comment", "", context)
        putContext(tag, "CommentEscaped", "", context)
        putContext(
            tag,
            "entityPackage",
            refPackage(entityFullPackage, extension.get().basePackage.get()),
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
                }).deepCopy().resolve(itemContext)
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
                val pathNode = templateNode.deepCopy().resolve(context)
                val path = forceRender(
                    pathNode,
                    resolvePackageDirectory(
                        baseDir,
                        concatPackage(
                            extension.get().basePackage.get(),
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
        val entityType = processEntityType(tableName)
        val entityVar = toLowerCamelCase(entityType) ?: entityType

        val comment = SqlSchemaUtils.getComment(table).replace(Regex(PATTERN_LINE_BREAK), " ")

        val schemaBaseFullPackage = if (schemaPath.isNotBlank()) {
            resolvePackage(schemaPath)
        } else {
            concatPackage(basePackage, getEntitySchemaOutputPackage())
        }

        val context = getEscapeContext().toMutableMap()

        putContext(tag, "templatePackage", refPackage(schemaPackage), context)
        putContext(tag, "package", refPackage(aggregate), context)
        putContext(tag, "path", aggregate.replace(".", File.separator), context)
        putContext(tag, "Aggregate", toUpperCamelCase(aggregate) ?: aggregate, context)
        putContext(tag, "isAggregateRoot", SqlSchemaUtils.isAggregateRoot(table).toString(), context)
        putContext(tag, "Comment", comment, context)
        putContext(tag, "CommentEscaped", comment.replace(Regex(PATTERN_LINE_BREAK), " "), context)
        putContext(
            tag,
            "entityPackage",
            refPackage(entityFullPackage, basePackage),
            context
        )
        putContext(tag, "Entity", entityType, context)
        putContext(tag, "EntityVar", entityVar, context)
        putContext(
            tag,
            "schemaBasePackage",
            refPackage(schemaBaseFullPackage, basePackage),
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
                toLowerCamelCase(SqlSchemaUtils.getColumnName(column)) ?: SqlSchemaUtils.getColumnName(
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
            }).deepCopy().resolve(itemContext).data ?: ""

            propertyNameItems += (if (templateNodeMap.containsKey(propertyNameTag) && templateNodeMap[propertyNameTag]!!.isNotEmpty()) {
                templateNodeMap[propertyNameTag]!![templateNodeMap[propertyNameTag]!!.size - 1]
            } else {
                resolveDefaultSchemaPropertyNameTemplateNode()
            }).deepCopy().resolve(itemContext).data ?: ""
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
                        putContext(joinTag, "joinEntityType", processEntityType(key), joinContext)
                        putContext(
                            joinTag,
                            "joinEntityVars",
                            Inflector.pluralize(
                                toLowerCamelCase(processEntityType(key)) ?: processEntityType(key)
                            ),
                            joinContext
                        )
                        if (!("abs".equals(getEntitySchemaOutputMode(), ignoreCase = true))) {
                            putContext(
                                joinTag,
                                "joinEntitySchemaPackage",
                                "${concatPackage(tablePackageMap[key] ?: "", DEFAULT_SCHEMA_PACKAGE)}.",
                                joinContext
                            )
                        } else {
                            putContext(joinTag, "joinEntitySchemaPackage", "", joinContext)
                        }
                        joinItems += (if (templateNodeMap.containsKey(joinTag) && templateNodeMap[joinTag]!!.isNotEmpty()) {
                            templateNodeMap[joinTag]!![templateNodeMap[joinTag]!!.size - 1]
                        } else {
                            resolveDefaultSchemaJoinTemplateNode()
                        }).deepCopy().resolve(joinContext).data ?: ""

                        fieldType = "${tablePackageMap[key]}.${processEntityType(key)}"
                        fieldName =
                            Inflector.pluralize(
                                toLowerCamelCase(processEntityType(key)) ?: processEntityType(key)
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
                        }).deepCopy().resolve(itemContext).data ?: ""
                        propertyNameItems += (if (templateNodeMap.containsKey(propertyNameTag) && templateNodeMap[propertyNameTag]!!.isNotEmpty()) {
                            templateNodeMap[propertyNameTag]!![templateNodeMap[propertyNameTag]!!.size - 1]
                        } else {
                            resolveDefaultSchemaPropertyNameTemplateNode()
                        }).deepCopy().resolve(itemContext).data ?: ""
                    }

                    "OneToOne", "ManyToOne" -> {
                        putContext(joinTag, "joinEntityPackage", tablePackageMap[key] ?: "", joinContext)
                        putContext(joinTag, "joinEntityType", processEntityType(key), joinContext)
                        putContext(
                            joinTag,
                            "joinEntityVars",
                            toLowerCamelCase(processEntityType(key)) ?: processEntityType(key),
                            joinContext
                        )
                        if (!("abs".equals(getEntitySchemaOutputMode(), ignoreCase = true))) {
                            putContext(
                                joinTag,
                                "joinEntitySchemaPackage",
                                "${concatPackage(tablePackageMap[key] ?: "", DEFAULT_SCHEMA_PACKAGE)}.",
                                joinContext
                            )
                        } else {
                            putContext(joinTag, "joinEntitySchemaPackage", "", joinContext)
                        }
                        joinItems += (if (templateNodeMap.containsKey(joinTag) && templateNodeMap[joinTag]!!.isNotEmpty()) {
                            templateNodeMap[joinTag]!![templateNodeMap[joinTag]!!.size - 1]
                        } else {
                            resolveDefaultSchemaJoinTemplateNode()
                        }).deepCopy().resolve(joinContext).data ?: ""

                        fieldType = "${tablePackageMap[key]}.${processEntityType(key)}"
                        fieldName = toLowerCamelCase(processEntityType(key)) ?: processEntityType(key)
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
                        }).deepCopy().resolve(itemContext).data ?: ""
                        propertyNameItems += (if (templateNodeMap.containsKey(propertyNameTag) && templateNodeMap[propertyNameTag]!!.isNotEmpty()) {
                            templateNodeMap[propertyNameTag]!![templateNodeMap[propertyNameTag]!!.size - 1]
                        } else {
                            resolveDefaultSchemaPropertyNameTemplateNode()
                        }).deepCopy().resolve(itemContext).data ?: ""
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
                    listOf(resolveDefaultRootSchemaExtraExtensionTemplateNode(extension.get().generation.generateAggregate.get()))
                }
                for (templateNode in extraExtensionTemplateNodes) {
                    extraExtension += templateNode.deepCopy().resolve(context).data ?: ""
                }
            }
        } catch (e: Exception) {
            logger.error("SchemaExtraExtension模板文件生成失败！", e)
        }
        putContext(tag, "EXTRA_EXTENSION", extraExtension, context)

        val schemaTemplateNodes = if (templateNodeMap.containsKey(tag)) {
            templateNodeMap[tag]!!
        } else {
            listOf(
                resolveDefaultSchemaTemplateNode(
                    SqlSchemaUtils.isAggregateRoot(table),
                    extension.get().generation.generateAggregate.get()
                )
            )
        }

        try {
            for (templateNode in schemaTemplateNodes) {
                val pathNode = templateNode.deepCopy().resolve(context)
                val path = forceRender(
                    pathNode,
                    resolvePackageDirectory(
                        baseDir,
                        concatPackage(
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
        val schemaFullPackage = concatPackage(extension.get().basePackage.get(), resolveSchemaPackage())

        val schemaBaseTemplateNodes = if (templateNodeMap.containsKey(tag)) {
            templateNodeMap[tag]!!
        } else {
            listOf(resolveDefaultSchemaBaseTemplateNode())
        }

        val context = getEscapeContext().toMutableMap()
        putContext(
            tag,
            "templatePackage",
            refPackage(schemaFullPackage, extension.get().basePackage.get()),
            context
        )
        putContext(tag, "SchemaBase", DEFAULT_SCHEMA_BASE_CLASS_NAME, context)

        try {
            for (templateNode in schemaBaseTemplateNodes) {
                val pathNode = templateNode.deepCopy().resolve(context)
                forceRender(
                    pathNode,
                    resolvePackageDirectory(
                        baseDir,
                        concatPackage(
                            extension.get().basePackage.get(),
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
        return TemplateNode().apply {
            type = "file"
            tag = "aggregate"
            name = "${'$'}{path}${'$'}{SEPARATOR}${extension.get().generation.aggregateNameTemplate.get()}.kt"
            format = "velocity"
            data = "vm/aggregate/Aggregate.kt.vm"
            conflict = "skip"
        }
    }

    fun resolveDefaultFactoryTemplateNode(): TemplateNode {
        return TemplateNode().apply {
            type = "file"
            tag = "factory"
            name = "${'$'}{path}${'$'}{SEPARATOR}${DEFAULT_FAC_PACKAGE}${'$'}{SEPARATOR}${'$'}{Entity}Factory.kt"
            format = "velocity"
            data = "vm/factory/Factory.kt.vm"
            conflict = "skip"
        }
    }

    fun resolveDefaultFactoryPayloadTemplateNode(): TemplateNode {
        return TemplateNode().apply {
            type = "file"
            tag = "factory"
            name = "${'$'}{path}${'$'}{SEPARATOR}${DEFAULT_FAC_PACKAGE}${'$'}{SEPARATOR}${'$'}{Entity}Payload.kt"
            format = "velocity"
            data = "vm/factory/Payload.kt.vm"
            conflict = "skip"
        }
    }

    fun resolveDefaultSpecificationTemplateNode(): TemplateNode {
        return TemplateNode().apply {
            type = "file"
            tag = "specification"
            name = "${'$'}{path}${'$'}{SEPARATOR}${DEFAULT_SPEC_PACKAGE}${'$'}{SEPARATOR}${'$'}{Entity}Specification.kt"
            format = "velocity"
            data = "vm/specification/Specification.kt.vm"
            conflict = "skip"
        }
    }

    fun resolveDefaultDomainEventHandlerTemplateNode(): TemplateNode {
        return TemplateNode().apply {
            type = "file"
            tag = "domain_event_handler"
            name = "${'$'}{DomainEvent}Subscriber.kt"
            format = "velocity"
            data = "vm/domain-event/DomainEventSubscriber.kt.vm"
            conflict = "skip"
        }
    }

    fun resolveDefaultDomainEventTemplateNode(): TemplateNode {
        return TemplateNode().apply {
            type = "file"
            tag = "domain_event"
            name = "${'$'}{path}${'$'}{SEPARATOR}${DEFAULT_DOMAIN_EVENT_PACKAGE}${'$'}{SEPARATOR}${'$'}{DomainEvent}.kt"
            format = "velocity"
            data = "vm/domain-event/DomainEvent.kt.vm"
            conflict = "skip"
        }
    }

    fun resolveDefaultEnumTemplateNode(): TemplateNode {
        return TemplateNode().apply {
            type = "file"
            tag = "enum"
            name = "${'$'}{path}${'$'}{SEPARATOR}${DEFAULT_ENUM_PACKAGE}${'$'}{SEPARATOR}${'$'}{Enum}.kt"
            format = "velocity"
            data = "vm/enum/Enum.kt.vm"
            conflict = "skip"
        }
    }

    fun resolveDefaultEnumItemTemplateNode(): TemplateNode {
        return TemplateNode().apply {
            type = "segment"
            tag = "enum_item"
            name = ""
            format = "velocity"
            data = "vm/enum/EnumItem.vm"
            conflict = "skip"
        }
    }

    fun resolveDefaultSchemaFieldTemplateNode(): TemplateNode {
        return TemplateNode().apply {
            type = "segment"
            tag = "schema_field"
            name = ""
            format = "velocity"
            data = "vm/schema/SchemaField.vm"
            conflict = "skip"
        }
    }

    fun resolveDefaultSchemaPropertyNameTemplateNode(): TemplateNode {
        return TemplateNode().apply {
            type = "segment"
            tag = "schema_property_name"
            name = ""
            format = "velocity"
            data = "vm/schema/SchemaPropertyName.vm"
            conflict = "skip"
        }
    }

    fun resolveDefaultSchemaJoinTemplateNode(): TemplateNode {
        return TemplateNode().apply {
            type = "segment"
            tag = "schema_join"
            name = ""
            format = "velocity"
            data = "vm/schema/SchemaJoin.vm"
            conflict = "skip"
        }
    }

    fun resolveDefaultSchemaTemplateNode(isAggregateRoot: Boolean, generateAggregate: Boolean): TemplateNode {
        return TemplateNode().apply {
            type = "file"
            tag = "schema"
            name =
                "${'$'}{path}${'$'}{SEPARATOR}${DEFAULT_SCHEMA_PACKAGE}${'$'}{SEPARATOR}${extension.get().generation.entitySchemaNameTemplate.get()}.kt"
            format = "velocity"
            data = "vm/schema/Schema.kt.vm"
            conflict = "overwrite"
        }
    }

    fun resolveDefaultRootSchemaExtraExtensionTemplateNode(generateAggregate: Boolean): TemplateNode {
        return TemplateNode().apply {
            type = "segment"
            tag = "root_schema_extra_extension"
            name = ""
            format = "velocity"
            data = "vm/schema/SchemaExtraExtension.vm"
            conflict = "skip"
        }
    }

    fun resolveDefaultSchemaBaseTemplateNode(): TemplateNode {
        return TemplateNode().apply {
            type = "file"
            tag = "schema_base"
            name = "${'$'}{SchemaBase}.kt"
            format = "velocity"
            data = "vm/schema/SchemaBase.kt.vm"
            conflict = "overwrite"
        }
    }
}
