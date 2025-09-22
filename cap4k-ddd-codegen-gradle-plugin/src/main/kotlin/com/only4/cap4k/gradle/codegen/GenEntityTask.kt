package com.only4.cap4k.gradle.codegen

import com.only4.cap4k.gradle.codegen.misc.NamingUtils
import com.only4.cap4k.gradle.codegen.misc.SourceFileUtils
import com.only4.cap4k.gradle.codegen.misc.SqlSchemaUtils
import com.only4.cap4k.gradle.codegen.template.TemplateNode
import org.gradle.api.provider.Property
import org.gradle.api.tasks.Input
import org.gradle.api.tasks.TaskAction
import java.io.BufferedWriter
import java.io.File
import java.util.*
import java.util.stream.Collectors

/**
 * 生成实体类任务
 */
open class GenEntityTask : AbstractCodegenTask() {

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

    val tableMap = mutableMapOf<String, Map<String, Any?>>()
    val tableModuleMap = mutableMapOf<String, String>()
    val tableAggregateMap = mutableMapOf<String, String>()
    val columnsMap = mutableMapOf<String, List<Map<String, Any?>>>()
    val enumConfigMap = mutableMapOf<String, Map<Int, Array<String>>>()
    val enumPackageMap = mutableMapOf<String, String>()
    val enumTableNameMap = mutableMapOf<String, String>()
    val entityTypeMap = mutableMapOf<String, String>()

    val annotationsCache = mutableMapOf<String, Map<String, String>>()

    var dbType = "mysql"

    var aggregatesPath = ""
    var schemaPath = ""
    var subscriberPath = ""

    val templateNodeMap = mutableMapOf<String, MutableList<TemplateNode>>()


    @TaskAction
    fun generate() {
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
        if (ext.generation.entityBaseClass.get().isBlank()) {
            ext.generation.entityBaseClass.set(SourceFileUtils.resolveDefaultBasePackage(getDomainModulePath()))
        }
        logger.info("实体类基类：${ext.generation.entityBaseClass.get()}")
        logger.info("聚合根标注注解: ${getAggregateRootAnnotation()}")
        logger.info("主键ID生成器: ${ext.generation.idGenerator.get()}")
        logger.info("日期类型映射: ${ext.generation.datePackage.get()}")
        logger.info("枚举值字段名称: ${ext.generation.enumValueField.get()}")
        logger.info("枚举名字段名称: ${ext.generation.enumNameField.get()}")
        logger.info("枚举不匹配是否抛出异常: ${ext.generation.enumUnmatchedThrowException.get()}")
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
                                "${ext.basePackage.get()}.${getEntityPackage(tableName)}$enumPackage"
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
    fun getAggregatesPath(): String {
        if (aggregatesPath.isNotBlank()) return aggregatesPath

        return SourceFileUtils.resolveDirectory(
            getDomainModulePath(),
            "${extension.get().basePackage.get()}.${AGGREGATE_PACKAGE}"
        )
    }

    /**
     * 获取聚合根包名，不包含basePackage
     */
    fun getAggregatesPackage(): String {
        return SourceFileUtils.resolvePackage(
            "${getAggregatesPath()}${File.separator}X.kt"
        ).substring(extension.get().basePackage.get().length + 1)
    }

    /**
     * 获取实体schema文件目录
     *
     */
    fun getSchemaPath(): String? {
        if (schemaPath.isNotBlank()) {
            return schemaPath
        }
        return SourceFileUtils.resolveDirectory(
            getDomainModulePath(),
            "${extension.get().basePackage.get()}.${getEntitySchemaOutputPackage()}"
        )
    }

    /**
     * 获取schema包名，不包含basePackage
     *
     */
    fun getSchemaPackage(): String {
        return SourceFileUtils.resolvePackage(
            "${getSchemaPath()}${File.separator}X.kt"
        ).substring(
            if (extension.get().basePackage.get().isBlank()) 0 else (extension.get().basePackage.get().length + 1)
        )
    }

    /**
     * 获取领域事件订阅者文件目录
     *
     */
    fun getSubscriberPath(): String? {
        if (subscriberPath.isNotBlank()) {
            return subscriberPath
        }
        return SourceFileUtils.resolveDirectory(
            getApplicationModulePath(),
            "${extension.get().basePackage.get()}.${DOMAIN_EVENT_SUBSCRIBER_PACKAGE}"
        )
    }

    /**
     * 获取领域事件订阅者包名，不包含basePackage
     *
     */
    fun getSubscriberPackage(): String {
        return SourceFileUtils.resolvePackage(
            "${getSubscriberPath()}${File.separator}X.kt"
        ).substring(extension.get().basePackage.get().length + 1)
    }

    /**
     * 获取模块
     */
    private fun getModule(tableName: String): String {
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
    private fun getAggregate(tableName: String): String {
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
                aggregate = NamingUtils.toSnakeCase(getEntityType(aggregateRootTableName)) ?: ""
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
    fun getAggregateWithModule(tableName: String): String {
        val module = getModule(tableName)
        if (module.isNotBlank()) {
            return SourceFileUtils.concatPackage(
                module,
                getAggregate(tableName)
            )
        } else {
            return getAggregate(tableName)
        }
    }

    /**
     * 获取实体类 Class.SimpleName
     */
    private fun getEntityType(tableName: String): String {
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
     * 解析实体类全路径包名，含basePackage
     */
    private fun resolveEntityFullPackage(table: Map<String, Any?>, basePackage: String, baseDir: String): String {
        val tableName = SqlSchemaUtils.getTableName(table)
        val packageName = SourceFileUtils.concatPackage(basePackage, getEntityPackage(tableName))
        return packageName
    }

    /**
     * 获取实体类所在包，不包含basePackage
     */
    private fun getEntityPackage(tableName: String): String {
        val module = getModule(tableName)
        val aggregate = getAggregate(tableName)
        return SourceFileUtils.concatPackage(
            getAggregatesPackage(),
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
        relations: MutableMap<String?, MutableMap<String?, String?>?>
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
    private fun getIdColumns(columns: List<Map<String, Any?>>): List<Map<String, Any?>> {
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
    private fun getColumn(
        columns: List<Map<String, Any?>>,
        columnName: String?
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
     *
     * @param table
     * @param basePackage
     * @param baseDir
     * @return
     */
    fun resolveEntityFullPackage(table: Map<String, Any?>, basePackage: String?, baseDir: String?): String {
        val tableName: String = SqlSchemaUtils.getTableName(table)
        val simpleClassName: String = getEntityType(tableName)
        val packageName: String = SourceFileUtils.concatPackage(
            basePackage,
            getEntityPackage(tableName)
        )
        return packageName
    }

    private fun resolveRelationTable(
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
        importLines: List<String>,
        annotationLinens: List<String>,
        customerLinens: List<String>
    ): Boolean {
        TODO()
    }

    fun processImportLines(table: Map<String, Any?>, importLines: List<String>, content: String) {
        TODO()
    }

    fun processAnnotationLines(
        table: Map<String, Any?>,
        columns: List<Map<String, Any?>>,
        annotationLines: List<String>
    ) {
        TODO()
    }

    fun buildEntitySourceFile(
        table: Map<String, Any?>,
        columns: List<Map<String, Any?>>,
        tablePackageMap: Map<String, Any?>,
        relations: Map<String, Map<String, Any?>>,
        basePackage: String,
        baseDir: String
    ) {
        TODO()
    }

    fun writeEntityClass(
        table: Map<String, Any?>,
        columns: List<Map<String, Any?>>,
        tablePackageMap: Map<String, Any?>,
        relations: Map<String, Map<String, Any?>>,
        enums: List<String>,
        annotationLines: List<String>,
        customerLinens: List<String>
    ) {
        TODO()
    }

    fun getEntityIdGenerator(table: Map<String, Any?>): String {
        TODO()
    }

    fun writeColumnProperty(
        out: BufferedWriter,
        table: Map<String, Any?>,
        column: Map<String, Any?>,
        ids: List<Map<String, Any?>>,
        relations: Map<String, Map<String, Any?>>,
        enums: List<String>,
    ) {
        TODO()
    }

    fun writeFieldComment(
        out: BufferedWriter,
        column: Map<String, Any?>,
    ) {
        TODO()
    }

    fun writeRelationProperty(
        out: BufferedWriter,
        table: Map<String, Any?>,
        relations: Map<String, Map<String, String>>,
        tablePackageMap: Map<String, String>,
    ) {
        TODO()
    }

    fun writeAggregateSourceFile(
        table: Map<String, Any?>,
        columns: List<Map<String, Any?>>,
        tablePackageMap: Map<String, String>,
        baseDir: String
    ) {
        TODO()
    }

    fun writeFactorySourceFile(
        table: Map<String, Any?>,
        tablePackageMap: Map<String, String>,
        baseDir: String
    ) {
        TODO()
    }

    fun writeSpecificationSourceFile(
        table: Map<String, Any?>,
        tablePackageMap: Map<String, String>,
        baseDir: String
    ) {
        TODO()
    }

    private fun writeDomainEventSourceFile(
        table: Map<String, Any?>,
        tablePackageMap: Map<String, String>,
        domainEventClassName: String,
        domainEventDescription: String,
        baseDir: String
    ) {
        TODO()
    }

    private fun writeEnumSourceFile(
        enumConfig: Map<Int, Array<String>>,
        enumClassName: String,
        enumValueField: String,
        enumNameField: String,
        tablePackageMap: Map<String, String>,
        baseDir: String
    ) {
        val tableName = enumTableNameMap[enumClassName] ?: return
        val packageName = enumPackageMap[enumClassName] ?: return

//        val enumCode = buildEnumCode(enumClassName, enumConfig, packageName)
        val outputDir = SourceFileUtils.resolveDirectory(baseDir, packageName)
        val filePath = "$outputDir${File.separator}$enumClassName.kt"

//        writeCodeToFile(filePath, enumCode)
        logger.info("生成枚举文件：$filePath")
    }

    private fun writeSchemaSourceFile(
        table: Map<String, Any?>,
        columns: List<Map<String, Any?>>,
        tablePackageMap: Map<String, String>,
        relations: Map<String, Map<String, String>>,
        basePackage: String,
        baseDir: String
    ) {
        // TODO: 实现Schema源文件生成
        logger.info("Schema源文件生成功能待实现")
    }

    private fun writeSchemaBaseSourceFile(baseDir: String) {
        val ext = getExtension()
        val packageName = SourceFileUtils.concatPackage(ext.basePackage.get(), getSchemaPackage())
        val schemaBaseCode = buildSchemaBaseCode(packageName)
        val outputDir = SourceFileUtils.resolveDirectory(baseDir, packageName)
        val filePath = "$outputDir${File.separator}${DEFAULT_SCHEMA_BASE_CLASS_NAME}.kt"

//        writeCodeToFile(filePath, schemaBaseCode)
        logger.info("生成Schema基类文件：$filePath")
    }

    /**
     * 生成领域事件名称
     */
    private fun generateDomainEventName(eventName: String): String {
        return NamingUtils.toUpperCamelCase(eventName) ?: eventName
    }

    /**
     * 构建Schema基类代码
     */
    private fun buildSchemaBaseCode(packageName: String): String {
        return """
            package $packageName

            /**
             * 实体结构基类
             *
             * @author cap4k-ddd-codegen
             */
            abstract class ${DEFAULT_SCHEMA_BASE_CLASS_NAME} {
                // TODO: 实现Schema基类逻辑
            }
        """.trimIndent()
    }

//    /**
//     * 构建实体源文件
//     */
//    private fun buildEntitySourceFile(
//        table: Map<String, Any?>,
//        columns: List<Map<String, Any?>>,
//        tablePackageMap: Map<String, String>,
//        relations: Map<String, Map<String, String>>,
//        basePackage: String,
//        baseDir: String
//    ) {
//        val tableName = SqlSchemaUtils.getTableName(table)
//        if (isIgnoreTable(table)) {
//            logger.info("跳过忽略表：$tableName")
//            return
//        }
//
//        if (SqlSchemaUtils.hasRelation(table)) {
//            logger.info("跳过关系表：$tableName")
//            return
//        }
//
//        val ids = getIdColumns(columns)
//        if (ids.isEmpty()) {
//            logger.error("跳过问题表：$tableName 缺失主键")
//            return
//        }
//
//        val entityType = getEntityType(tableName)
//        val entityFullPackage = tablePackageMap[tableName] ?: return
//
//        val outputDir = SourceFileUtils.resolveDirectory(baseDir, entityFullPackage)
//        val filePath = "$outputDir${File.separator}$entityType.kt"
//
//        val entityCode = buildEntityCode(entityType, tableName, table, columns, entityFullPackage)
//        writeCodeToFile(filePath, entityCode)
//
//        logger.info("生成实体文件：$filePath")
//
//        // 生成相关文件
//        val ext = getExtension()
//        if (ext.generation.generateSchema.get()) {
//            writeSchemaSourceFile(table, columns, tablePackageMap, relations, basePackage, baseDir)
//        }
//
//        if (SqlSchemaUtils.isAggregateRoot(table)) {
//            if (ext.generation.generateAggregate.get()) {
//                writeAggregateSourceFile(table, columns, tablePackageMap, baseDir)
//            }
//            if (SqlSchemaUtils.hasFactory(table) || ext.generation.generateAggregate.get()) {
//                writeFactorySourceFile(table, tablePackageMap, baseDir)
//            }
//            if (SqlSchemaUtils.hasSpecification(table) || ext.generation.generateAggregate.get()) {
//                writeSpecificationSourceFile(table, tablePackageMap, baseDir)
//            }
//            if (SqlSchemaUtils.hasDomainEvent(table)) {
//                val domainEvents = SqlSchemaUtils.getDomainEvent(table)
//                for (domainEvent in domainEvents) {
//                    if (domainEvent.isNotBlank()) {
//                        val segments = domainEvent.split(":")
//                        val domainEventClassName = generateDomainEventName(segments[0])
//                        val domainEventDescription = if (segments.size > 1) segments[1] else "todo: 领域事件说明"
//                        writeDomainEventSourceFile(
//                            table,
//                            tablePackageMap,
//                            domainEventClassName,
//                            domainEventDescription,
//                            baseDir
//                        )
//                    }
//                }
//            }
//        }
//    }
//
//    private fun generateEntityClass(tableName: String, table: Map<String, Any?>) {
//        val ext = getExtension()
//        val entityName = NamingUtils.toUpperCamelCase(tableName) ?: tableName
//        val columns = columnsMap[tableName] ?: return
//
//        // 确定输出路径
//        val packageName = buildPackageName(tableName, "")
//        val outputDir = getDomainModulePath()
//        val filePath = "${outputDir}/src/main/kotlin/${packageName.replace('.', File.separatorChar)}/${entityName}.kt"
//
//        // 生成实体代码
//        val entityCode = buildEntityCode(entityName, tableName, table, columns, packageName)
//
//        // 写入文件
//        writeCodeToFile(filePath, entityCode)
//
//        logger.info("生成实体类: $filePath")
//    }
//
//    /**
//     * 生成聚合类
//     */
//    private fun generateAggregateClass(tableName: String, table: Map<String, Any?>) {
//        val aggregateName = getExtension().generation.aggregateNameTemplate.get()
//            .replace("\${Entity}", NamingUtils.toUpperCamelCase(tableName) ?: tableName)
//
//        val packageName = buildPackageName(tableName, "aggregates")
//        val outputDir = getDomainModulePath()
//        val filePath =
//            "${outputDir}/src/main/kotlin/${packageName.replace('.', File.separatorChar)}/${aggregateName}.kt"
//
//        val aggregateCode = buildAggregateCode(aggregateName, tableName, table, packageName)
//        writeCodeToFile(filePath, aggregateCode)
//
//        logger.info("生成聚合类: $filePath")
//    }
//
//    /**
//     * 生成Schema类
//     */
//    private fun generateSchemaClass(tableName: String, table: Map<String, Any?>) {
//        val schemaName = "${NamingUtils.toUpperCamelCase(tableName)}Schema"
//        val columns = columnsMap[tableName] ?: return
//
//        val packageName = buildPackageName(tableName, DEFAULT_SCHEMA_PACKAGE)
//        val outputDir = getDomainModulePath()
//        val filePath = "${outputDir}/src/main/kotlin/${packageName.replace('.', File.separatorChar)}/${schemaName}.kt"
//
//        val schemaCode = buildSchemaCode(schemaName, tableName, columns, packageName)
//        writeCodeToFile(filePath, schemaCode)
//
//        logger.info("生成Schema类: $filePath")
//    }
//
//    /**
//     * 生成枚举类
//     */
//    private fun generateEnumClasses(tableName: String) {
//        val enumsForTable = enumConfigMap.filterKeys { it.startsWith("${tableName}_") }
//
//        for ((enumKey, enumConfig) in enumsForTable) {
//            val columnName = enumKey.substringAfter("${tableName}_")
//            val enumName = "${NamingUtils.toUpperCamelCase(tableName)}${NamingUtils.toUpperCamelCase(columnName)}"
//
//            val packageName = buildPackageName(tableName, DEFAULT_ENUM_PACKAGE)
//            val outputDir = getDomainModulePath()
//            val filePath = "${outputDir}/src/main/kotlin/${packageName.replace('.', File.separatorChar)}/${enumName}.kt"
//
//            val enumCode = buildEnumCode(enumName, enumConfig, packageName)
//            writeCodeToFile(filePath, enumCode)
//
//            logger.info("生成枚举类: $filePath")
//        }
//    }
//
//    /**
//     * 构建包名
//     */
//    private fun buildPackageName(tableName: String, subPackage: String): String {
//        val ext = getExtension()
//        val basePackage = ext.basePackage.get()
//
//        // 检查是否有模块配置
//        val module = tableModuleMap[tableName]
//        val aggregate = tableAggregateMap[tableName]
//
//        val suffix = if (subPackage.isNotEmpty()) ".$subPackage" else ""
//
//        return when {
//            module?.isNotEmpty() == true && aggregate?.isNotEmpty() == true ->
//                "$basePackage.${AGGREGATE_PACKAGE}.$module.${aggregate.lowercase()}$suffix"
//
//            aggregate?.isNotEmpty() == true ->
//                "$basePackage.${AGGREGATE_PACKAGE}.${aggregate.lowercase()}$suffix"
//
//            module?.isNotEmpty() == true ->
//                "$basePackage.${AGGREGATE_PACKAGE}.$module$suffix"
//
//            else ->
//                "$basePackage.${AGGREGATE_PACKAGE}$suffix"
//        }
//    }
//
//    /**
//     * 构建实体代码
//     */
//    private fun buildEntityCode(
//        entityName: String,
//        tableName: String,
//        table: Map<String, Any?>,
//        columns: List<Map<String, Any?>>,
//        packageName: String,
//    ): String {
//        val ext = getExtension()
//        val comment = SqlSchemaUtils.getTableComment(table)
//        val annotations = SqlSchemaUtils.parseAnnotations(comment)
//
//        val imports = mutableSetOf<String>()
//        imports.add("com.only4.cap4k.ddd.core.domain.aggregate.annotation.Aggregate")
//        imports.add("javax.persistence.*")
//
//        val isRoot = SqlSchemaUtils.isAggregateRoot(table)
//        val isValueObject = SqlSchemaUtils.isValueObject(table)
//
//        val codeBuilder = StringBuilder()
//
//        // Package declaration
//        codeBuilder.appendLine("package $packageName")
//        codeBuilder.appendLine()
//
//        // Imports
//        imports.sorted().forEach { import ->
//            codeBuilder.appendLine("import $import")
//        }
//        codeBuilder.appendLine()
//
//        // Class comment
//        if (comment.isNotEmpty()) {
//            codeBuilder.appendLine("/**")
//            codeBuilder.appendLine(" * $comment")
//            codeBuilder.appendLine(" */")
//        }
//
//        // Annotations
//        val resolvedAggregate = getAggregate(tableName)
//        val aggregateName =
//            annotations["Aggregate"] ?: resolvedAggregate ?: NamingUtils.toLowerCamelCase(tableName) ?: tableName
//        val type = when {
//            isRoot -> "entity"
//            isValueObject -> "value-object"
//            else -> "entity"
//        }
//
//        codeBuilder.appendLine("@Aggregate(aggregate = \"$aggregateName\", type = \"$type\", root = $isRoot)")
//        codeBuilder.appendLine("@Entity")
//        codeBuilder.appendLine("@Table(name = \"$tableName\")")
//
//        // Class declaration
//        val baseClass = if (isRoot) {
//            ext.generation.rootEntityBaseClass.get().ifEmpty { ext.generation.entityBaseClass.get() }
//        } else {
//            ext.generation.entityBaseClass.get()
//        }
//
//        val classDeclaration = if (baseClass.isNotEmpty()) {
//            "data class $entityName : $baseClass()"
//        } else {
//            "data class $entityName"
//        }
//
//        codeBuilder.appendLine("$classDeclaration {")
//
//        // Properties
//        generateEntityProperties(columns, codeBuilder, imports)
//
//        codeBuilder.appendLine("}")
//
//        return codeBuilder.toString()
//    }
//
//    /**
//     * 生成实体属性
//     */
//    private fun generateEntityProperties(
//        columns: List<Map<String, Any?>>,
//        codeBuilder: StringBuilder,
//        imports: MutableSet<String>,
//    ) {
//        for (column in columns) {
//            val columnName = SqlSchemaUtils.getColumnName(column)
//            val propertyName = NamingUtils.toLowerCamelCase(columnName) ?: columnName
//            val javaType = SqlSchemaUtils.getColumnType(column)
//            val isPrimaryKey = SqlSchemaUtils.isColumnPrimaryKey(column)
//            val isNullable = SqlSchemaUtils.isColumnNullable(column)
//            val comment = SqlSchemaUtils.getColumnComment(column)
//
//            codeBuilder.appendLine()
//
//            // Property comment
//            if (comment.isNotEmpty()) {
//                codeBuilder.appendLine("    /**")
//                codeBuilder.appendLine("     * $comment")
//                codeBuilder.appendLine("     */")
//            }
//
//            // Annotations
//            if (isPrimaryKey) {
//                codeBuilder.appendLine("    @Id")
//                val idGenerator = getExtension().generation.idGenerator.get()
//                if (idGenerator.isNotEmpty()) {
//                    codeBuilder.appendLine("    @GeneratedValue(strategy = GenerationType.$idGenerator)")
//                }
//            }
//
//            codeBuilder.appendLine("    @Column(name = \"$columnName\")")
//
//            // Property declaration
//            val kotlinType = convertToKotlinType(javaType, isNullable, imports)
//            val defaultValue = if (isNullable) " = null" else ""
//
//            codeBuilder.appendLine("    var $propertyName: $kotlinType$defaultValue")
//        }
//    }
//
//    /**
//     * 转换为Kotlin类型
//     */
//    private fun convertToKotlinType(javaType: String, isNullable: Boolean, imports: MutableSet<String>): String {
//        val kotlinType = when (javaType) {
//            "Int" -> "Int"
//            "Long" -> "Long"
//            "Float" -> "Float"
//            "Double" -> "Double"
//            "Boolean" -> "Boolean"
//            "String" -> "String"
//            "ByteArray" -> "ByteArray"
//            "java.math.BigDecimal" -> {
//                imports.add("java.math.BigDecimal")
//                "BigDecimal"
//            }
//
//            "java.time.LocalDate" -> {
//                imports.add("java.time.LocalDate")
//                "LocalDate"
//            }
//
//            "java.time.LocalDateTime" -> {
//                imports.add("java.time.LocalDateTime")
//                "LocalDateTime"
//            }
//
//            "java.time.LocalTime" -> {
//                imports.add("java.time.LocalTime")
//                "LocalTime"
//            }
//
//            else -> "String"
//        }
//
//        return if (isNullable) "$kotlinType?" else kotlinType
//    }
//
//    /**
//     * 构建聚合代码 (简化版本)
//     */
//    private fun buildAggregateCode(
//        aggregateName: String,
//        tableName: String,
//        table: Map<String, Any?>,
//        packageName: String,
//    ): String {
//        val entityName = NamingUtils.toUpperCamelCase(tableName) ?: tableName
//        val comment = SqlSchemaUtils.getTableComment(table)
//        val resolvedAggregate = getAggregate(tableName)
//
//        return """
//            package $packageName
//
//            import com.only4.cap4k.ddd.core.domain.aggregate.annotation.Aggregate
//
//            /**
//             * $comment 聚合
//             */
//            @Aggregate(aggregate = "$resolvedAggregate", type = "aggregate")
//            class $aggregateName {
//                // TODO: 实现聚合逻辑
//            }
//        """.trimIndent()
//    }
//
//    /**
//     * 构建Schema代码 (简化版本)
//     */
//    private fun buildSchemaCode(
//        schemaName: String,
//        tableName: String,
//        columns: List<Map<String, Any?>>,
//        packageName: String,
//    ): String {
//        return """
//            package $packageName
//
//            /**
//             * $tableName 表结构定义
//             */
//            object $schemaName {
//                const val TABLE_NAME = "$tableName"
//
//                ${
//            columns.joinToString("\n                ") {
//                val columnName = SqlSchemaUtils.getColumnName(it)
//                "const val ${columnName.uppercase()} = \"$columnName\""
//            }
//        }
//            }
//        """.trimIndent()
//    }
//
//    /**
//     * 构建枚举代码
//     */
//    private fun buildEnumCode(
//        enumName: String,
//        enumConfig: Map<Int, Array<String>>,
//        packageName: String,
//    ): String {
//        val enumItems = enumConfig.entries.joinToString(",\n    ") { (value, names) ->
//            "${names[0].uppercase()}($value, \"${names[0]}\", \"${names.getOrNull(1) ?: names[0]}\")"
//        }
//
//        return """
//            package $packageName
//
//            /**
//             * $enumName 枚举
//             */
//            enum class $enumName(
//                val value: Int,
//                val code: String,
//                val description: String
//            ) {
//                $enumItems;
//
//                companion object {
//                    fun fromValue(value: Int): $enumName? {
//                        return values().find { it.value == value }
//                    }
//
//                    fun fromCode(code: String): $enumName? {
//                        return values().find { it.code == code }
//                    }
//                }
//            }
//        """.trimIndent()
//    }
//
//    /**
//     * 写入代码到文件
//     */
//    private fun writeCodeToFile(filePath: String, code: String) {
//        val file = File(filePath)
//
//        // 检查文件是否存在且包含不覆盖标记
//        if (file.exists()) {
//            val existingContent = file.readText()
//            if (existingContent.contains(FLAG_DO_NOT_OVERWRITE)) {
//                logger.info("文件包含不覆盖标记，跳过: $filePath")
//                return
//            }
//        }
//
//        // 创建父目录
//        file.parentFile?.mkdirs()
//
//        // 写入文件
//        file.writeText(code, charset(getExtension().outputEncoding.get()))
//    }
}
