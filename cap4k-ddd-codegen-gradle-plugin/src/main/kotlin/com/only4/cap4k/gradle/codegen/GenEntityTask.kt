package com.only4.cap4k.gradle.codegen

import com.only4.cap4k.gradle.codegen.misc.NamingUtils
import com.only4.cap4k.gradle.codegen.misc.SourceFileUtils
import com.only4.cap4k.gradle.codegen.misc.SqlSchemaUtils
import com.only4.cap4k.gradle.codegen.template.TemplateNode
import org.gradle.api.provider.Property
import org.gradle.api.tasks.Input
import org.gradle.api.tasks.TaskAction
import java.io.File

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
        // 设置数据库类型和方言
        val (url, username, password) = getDatabaseConfig()
        dbType = SqlSchemaUtils.recognizeDbType(url)
        SqlSchemaUtils.processSqlDialect(dbType)
        SqlSchemaUtils.task = this

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
        if (ext.generation.entityBaseClass.get().isBlank()) {
            ext.generation.entityBaseClass.set(SourceFileUtils.resolveDefaultBasePackage(getDomainModulePath()))
        }
        logger.info("实体类基类：${ext.generation.entityBaseClass.get()}")
        logger.info("聚合根标注注解: ${getAggregateRootAnnotation()}")
        logger.info("主键ID生成器: ${ext.generation.idGenerator}")
        logger.info("日期类型映射: ${ext.generation.datePackage.get()}")
        logger.info("枚举值字段名称: ${ext.generation.enumValueField.get()}")
        logger.info("枚举名字段名称: ${ext.generation.enumNameField.get()}")
        logger.info("枚举不匹配是否抛出异常: ${ext.generation.enumUnmatchedThrowException.get()}")

        try {
            // 获取数据库信息
            val tables = filterTables(getTables())
            val allColumns = getColumns()

            if (tables.isEmpty()) {
                logger.warn("没有找到匹配的表")
                return
            }

            // 解析表和列信息
            parseDatabaseSchema(tables, allColumns)

            // 生成代码
            generateEntities()

        } catch (e: Exception) {
            logger.error("生成实体类失败", e)
            throw e
        }
    }

    /**
     * 解析数据库结构
     */
    private fun parseDatabaseSchema(tables: List<Map<String, Any?>>, allColumns: List<Map<String, Any?>>) {
        logger.info("解析数据库结构...")

        for (table in tables) {
            val tableName = SqlSchemaUtils.getTableName(table)

            // 缓存表信息
            tableMap[tableName] = table

            // 获取表的列信息
            val columns = allColumns.filter { column ->
                SqlSchemaUtils.isColumnInTable(column, table)
            }.sortedBy { SqlSchemaUtils.getOridinalPosition(it) }

            columnsMap[tableName] = columns
        }

        for (table in tableMap.values) {
            val tableColumns = columnsMap[SqlSchemaUtils.getTableName(table)]!!
            val relationTable = resolveRelationTable(table, tableColumns)
        }

        // 延迟解析模块和聚合信息，确保所有表都已缓存
        parseModuleAndAggregateInfo(tables)

        logger.info("数据库结构解析完成，共处理 ${tables.size} 张表")
    }

    private fun resolveRelationTable(
        table: Map<String, Any?>,
        columns: List<Map<String, Any?>>,
    ): Map<String, Map<String, String>> {
        val result = mutableMapOf<String, MutableMap<String, String>>()
        val tableName = SqlSchemaUtils.getTableName(table)

        if (isIgnoreTable(table)) return result

        if (!SqlSchemaUtils.isAggregateRoot(table)) {
            val parent = SqlSchemaUtils.getParent(table)

            result.putIfAbsent(parent, mutableMapOf())

            var rewrited = false
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
                    }
                }
            }
            if (!rewrited) {
                val column = columns.firstOrNull { SqlSchemaUtils.getColumnName(it).equals("${parent}_id") }
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

        if (SqlSchemaUtils.hasRelation(table)) {

        }
    }

    private fun isIgnoreTable(table: Map<String, Any?>): Boolean = SqlSchemaUtils.isIgnore(table)

    /**
     * 解析模块和聚合信息
     */
    private fun parseModuleAndAggregateInfo(tables: List<Map<String, Any?>>) {
        logger.info("解析模块和聚合信息...")

        for (table in tables) {
            val tableName = SqlSchemaUtils.getTableName(table)

            // 解析模块信息
            getModule(tableName)

            // 解析聚合信息
            getAggregate(tableName)
        }
    }

    /**
     * 获取模块（类似Maven版本）
     */
    private fun getModule(tableName: String): String {
        return tableModuleMap.computeIfAbsent(tableName) {
            var currentTable = tableMap[tableName]
            var module = currentTable?.let { SqlSchemaUtils.getModule(it) } ?: ""

            logger.info("尝试解析模块: $tableName ${if (module.isBlank()) "[缺失]" else module}")

            while (currentTable != null && !SqlSchemaUtils.isAggregateRoot(currentTable) && module.isBlank()) {
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
     * 获取聚合（类似Maven版本）
     */
    private fun getAggregate(tableName: String): String {
        return tableAggregateMap.computeIfAbsent(tableName) {
            var currentTable = tableMap[tableName]
            var aggregate = currentTable?.let { SqlSchemaUtils.getAggregate(it) } ?: ""
            var aggregateRootTableName = tableName

            logger.info("尝试解析聚合: $tableName ${if (aggregate.isBlank()) "[缺失]" else aggregate}")

            while (currentTable != null && !SqlSchemaUtils.isAggregateRoot(currentTable) && aggregate.isBlank()) {
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
                aggregate = NamingUtils.toSnakeCase(getEntityJavaType(aggregateRootTableName)) ?: ""
            }

            logger.info("聚合解析结果: $tableName ${if (aggregate.isBlank()) "[缺失]" else aggregate}")
            aggregate
        }
    }

    /**
     * 获取实体类 Class.SimpleName
     */
    private fun getEntityJavaType(tableName: String): String {
        return entityTypeMap.computeIfAbsent(tableName) {
            val table = tableMap[tableName]
            var type = table?.let { SqlSchemaUtils.getType(it) } ?: ""
            if (type.isBlank()) {
                type = NamingUtils.toUpperCamelCase(tableName) ?: ""
            }
            if (type.isNotBlank()) {
                if (table != null) {
                    logger.info("解析实体类名: ${SqlSchemaUtils.getTableName(table)} --> $type")
                }
                type
            } else {
                throw RuntimeException("实体类名未生成")
            }
        }
    }

    /**
     * 解析枚举配置
     */
    private fun parseEnumConfigs(tableName: String, columns: List<Map<String, Any?>>) {
        for (column in columns) {
            if (SqlSchemaUtils.hasEnum(column) && !isIgnoreColumn(column)) {
                val enumConfig = SqlSchemaUtils.getEnum(column)
                if (enumConfig.isEmpty()) {
                    continue
                }
                val enumKey = SqlSchemaUtils.getType(column)

                enumConfigMap[enumKey] = enumConfig
                var enumPackage = if (templateNodeMap.containsKey("enum") && templateNodeMap["enum"]!!.isNotEmpty()) {
                    templateNodeMap["enum"]!![0].name!!
                } else {
                    DEFAULT_ENUM_PACKAGE
                }

                if (enumPackage.isNotBlank()) {
                    enumPackage = ".$enumPackage"
                }
                enumPackageMap[enumKey] =
                    "${getExtension()}.${getEntityPackage(SqlSchemaUtils.getTableName())}$enumPackage"
                enumTableNameMap[enumKey] = tableName

                logger.debug("发现枚举配置: $enumKey -> $enumConfig")
            }
        }
    }

    private fun isIgnoreColumn(column: Map<String, Any?>): Boolean {
        if (SqlSchemaUtils.isIgnore(column)) {
            return true
        }
        val columnName = SqlSchemaUtils.getColumnName(column).lowercase()
        return getExtension().generation.ignoreFields.get().map { it.lowercase().split(PATTERN_SPLITTER) }.flatten()
            .any { columnName.matches(it.replace("%", ".*").toRegex()) }
    }

    /**
     * 生成实体类
     */
    private fun generateEntities() {
        logger.info("开始生成实体类...")

        val ext = getExtension()

        for ((tableName, table) in tableMap) {
            logger.info("生成实体: $tableName")

            try {
                generateEntityClass(tableName, table)

                // 如果配置了生成聚合，且当前表是聚合根
                if (ext.generation.generateAggregate.get() && SqlSchemaUtils.isAggregateRoot(table)) {
                    generateAggregateClass(tableName, table)
                }

                // 如果配置了生成Schema
                if (ext.generation.generateSchema.get()) {
                    generateSchemaClass(tableName, table)
                }

                // 生成枚举类
                generateEnumClasses(tableName)

            } catch (e: Exception) {
                logger.error("生成实体 $tableName 失败", e)
                throw e
            }
        }

        logger.info("实体类生成完成")
    }

    /**
     * 生成实体类
     */
    private fun generateEntityClass(tableName: String, table: Map<String, Any?>) {
        val ext = getExtension()
        val entityName = NamingUtils.toUpperCamelCase(tableName) ?: tableName
        val columns = columnsMap[tableName] ?: return

        // 确定输出路径
        val packageName = buildPackageName(tableName, "")
        val outputDir = getDomainModulePath()
        val filePath = "${outputDir}/src/main/kotlin/${packageName.replace('.', File.separatorChar)}/${entityName}.kt"

        // 生成实体代码
        val entityCode = buildEntityCode(entityName, tableName, table, columns, packageName)

        // 写入文件
        writeCodeToFile(filePath, entityCode)

        logger.info("生成实体类: $filePath")
    }

    /**
     * 生成聚合类
     */
    private fun generateAggregateClass(tableName: String, table: Map<String, Any?>) {
        val aggregateName = getExtension().generation.aggregateNameTemplate.get()
            .replace("\${Entity}", NamingUtils.toUpperCamelCase(tableName) ?: tableName)

        val packageName = buildPackageName(tableName, "aggregates")
        val outputDir = getDomainModulePath()
        val filePath =
            "${outputDir}/src/main/kotlin/${packageName.replace('.', File.separatorChar)}/${aggregateName}.kt"

        val aggregateCode = buildAggregateCode(aggregateName, tableName, table, packageName)
        writeCodeToFile(filePath, aggregateCode)

        logger.info("生成聚合类: $filePath")
    }

    /**
     * 生成Schema类
     */
    private fun generateSchemaClass(tableName: String, table: Map<String, Any?>) {
        val schemaName = "${NamingUtils.toUpperCamelCase(tableName)}Schema"
        val columns = columnsMap[tableName] ?: return

        val packageName = buildPackageName(tableName, DEFAULT_SCHEMA_PACKAGE)
        val outputDir = getDomainModulePath()
        val filePath = "${outputDir}/src/main/kotlin/${packageName.replace('.', File.separatorChar)}/${schemaName}.kt"

        val schemaCode = buildSchemaCode(schemaName, tableName, columns, packageName)
        writeCodeToFile(filePath, schemaCode)

        logger.info("生成Schema类: $filePath")
    }

    /**
     * 生成枚举类
     */
    private fun generateEnumClasses(tableName: String) {
        val enumsForTable = enumConfigMap.filterKeys { it.startsWith("${tableName}_") }

        for ((enumKey, enumConfig) in enumsForTable) {
            val columnName = enumKey.substringAfter("${tableName}_")
            val enumName = "${NamingUtils.toUpperCamelCase(tableName)}${NamingUtils.toUpperCamelCase(columnName)}"

            val packageName = buildPackageName(tableName, DEFAULT_ENUM_PACKAGE)
            val outputDir = getDomainModulePath()
            val filePath = "${outputDir}/src/main/kotlin/${packageName.replace('.', File.separatorChar)}/${enumName}.kt"

            val enumCode = buildEnumCode(enumName, enumConfig, packageName)
            writeCodeToFile(filePath, enumCode)

            logger.info("生成枚举类: $filePath")
        }
    }

    /**
     * 构建包名
     */
    private fun buildPackageName(tableName: String, subPackage: String): String {
        val ext = getExtension()
        val basePackage = ext.basePackage.get()

        // 检查是否有模块配置
        val module = tableModuleMap[tableName]
        val aggregate = tableAggregateMap[tableName]

        val suffix = if (subPackage.isNotEmpty()) ".$subPackage" else ""

        return when {
            module?.isNotEmpty() == true && aggregate?.isNotEmpty() == true ->
                "$basePackage.${AGGREGATE_PACKAGE}.$module.${aggregate.lowercase()}$suffix"

            aggregate?.isNotEmpty() == true ->
                "$basePackage.${AGGREGATE_PACKAGE}.${aggregate.lowercase()}$suffix"

            module?.isNotEmpty() == true ->
                "$basePackage.${AGGREGATE_PACKAGE}.$module$suffix"

            else ->
                "$basePackage.${AGGREGATE_PACKAGE}$suffix"
        }
    }

    /**
     * 构建实体代码
     */
    private fun buildEntityCode(
        entityName: String,
        tableName: String,
        table: Map<String, Any?>,
        columns: List<Map<String, Any?>>,
        packageName: String,
    ): String {
        val ext = getExtension()
        val comment = SqlSchemaUtils.getTableComment(table)
        val annotations = SqlSchemaUtils.parseAnnotations(comment)

        val imports = mutableSetOf<String>()
        imports.add("com.only4.cap4k.ddd.core.domain.aggregate.annotation.Aggregate")
        imports.add("javax.persistence.*")

        val isRoot = SqlSchemaUtils.isAggregateRoot(table)
        val isValueObject = SqlSchemaUtils.isValueObject(table)

        val codeBuilder = StringBuilder()

        // Package declaration
        codeBuilder.appendLine("package $packageName")
        codeBuilder.appendLine()

        // Imports
        imports.sorted().forEach { import ->
            codeBuilder.appendLine("import $import")
        }
        codeBuilder.appendLine()

        // Class comment
        if (comment.isNotEmpty()) {
            codeBuilder.appendLine("/**")
            codeBuilder.appendLine(" * $comment")
            codeBuilder.appendLine(" */")
        }

        // Annotations
        val resolvedAggregate = getAggregate(tableName)
        val aggregateName =
            annotations["Aggregate"] ?: resolvedAggregate ?: NamingUtils.toLowerCamelCase(tableName) ?: tableName
        val type = when {
            isRoot -> "entity"
            isValueObject -> "value-object"
            else -> "entity"
        }

        codeBuilder.appendLine("@Aggregate(aggregate = \"$aggregateName\", type = \"$type\", root = $isRoot)")
        codeBuilder.appendLine("@Entity")
        codeBuilder.appendLine("@Table(name = \"$tableName\")")

        // Class declaration
        val baseClass = if (isRoot) {
            ext.generation.rootEntityBaseClass.get().ifEmpty { ext.generation.entityBaseClass.get() }
        } else {
            ext.generation.entityBaseClass.get()
        }

        val classDeclaration = if (baseClass.isNotEmpty()) {
            "data class $entityName : $baseClass()"
        } else {
            "data class $entityName"
        }

        codeBuilder.appendLine("$classDeclaration {")

        // Properties
        generateEntityProperties(columns, codeBuilder, imports)

        codeBuilder.appendLine("}")

        return codeBuilder.toString()
    }

    /**
     * 生成实体属性
     */
    private fun generateEntityProperties(
        columns: List<Map<String, Any?>>,
        codeBuilder: StringBuilder,
        imports: MutableSet<String>,
    ) {
        for (column in columns) {
            val columnName = SqlSchemaUtils.getColumnName(column)
            val propertyName = NamingUtils.toLowerCamelCase(columnName) ?: columnName
            val javaType = SqlSchemaUtils.getColumnType(column)
            val isPrimaryKey = SqlSchemaUtils.isColumnPrimaryKey(column)
            val isNullable = SqlSchemaUtils.isColumnNullable(column)
            val comment = SqlSchemaUtils.getColumnComment(column)

            codeBuilder.appendLine()

            // Property comment
            if (comment.isNotEmpty()) {
                codeBuilder.appendLine("    /**")
                codeBuilder.appendLine("     * $comment")
                codeBuilder.appendLine("     */")
            }

            // Annotations
            if (isPrimaryKey) {
                codeBuilder.appendLine("    @Id")
                val idGenerator = getExtension().generation.idGenerator.get()
                if (idGenerator.isNotEmpty()) {
                    codeBuilder.appendLine("    @GeneratedValue(strategy = GenerationType.$idGenerator)")
                }
            }

            codeBuilder.appendLine("    @Column(name = \"$columnName\")")

            // Property declaration
            val kotlinType = convertToKotlinType(javaType, isNullable, imports)
            val defaultValue = if (isNullable) " = null" else ""

            codeBuilder.appendLine("    var $propertyName: $kotlinType$defaultValue")
        }
    }

    /**
     * 转换为Kotlin类型
     */
    private fun convertToKotlinType(javaType: String, isNullable: Boolean, imports: MutableSet<String>): String {
        val kotlinType = when (javaType) {
            "Int" -> "Int"
            "Long" -> "Long"
            "Float" -> "Float"
            "Double" -> "Double"
            "Boolean" -> "Boolean"
            "String" -> "String"
            "ByteArray" -> "ByteArray"
            "java.math.BigDecimal" -> {
                imports.add("java.math.BigDecimal")
                "BigDecimal"
            }

            "java.time.LocalDate" -> {
                imports.add("java.time.LocalDate")
                "LocalDate"
            }

            "java.time.LocalDateTime" -> {
                imports.add("java.time.LocalDateTime")
                "LocalDateTime"
            }

            "java.time.LocalTime" -> {
                imports.add("java.time.LocalTime")
                "LocalTime"
            }

            else -> "String"
        }

        return if (isNullable) "$kotlinType?" else kotlinType
    }

    /**
     * 构建聚合代码 (简化版本)
     */
    private fun buildAggregateCode(
        aggregateName: String,
        tableName: String,
        table: Map<String, Any?>,
        packageName: String,
    ): String {
        val entityName = NamingUtils.toUpperCamelCase(tableName) ?: tableName
        val comment = SqlSchemaUtils.getTableComment(table)
        val resolvedAggregate = getAggregate(tableName)

        return """
            package $packageName

            import com.only4.cap4k.ddd.core.domain.aggregate.annotation.Aggregate

            /**
             * $comment 聚合
             */
            @Aggregate(aggregate = "$resolvedAggregate", type = "aggregate")
            class $aggregateName {
                // TODO: 实现聚合逻辑
            }
        """.trimIndent()
    }

    /**
     * 构建Schema代码 (简化版本)
     */
    private fun buildSchemaCode(
        schemaName: String,
        tableName: String,
        columns: List<Map<String, Any?>>,
        packageName: String,
    ): String {
        return """
            package $packageName

            /**
             * $tableName 表结构定义
             */
            object $schemaName {
                const val TABLE_NAME = "$tableName"

                ${
            columns.joinToString("\n                ") {
                val columnName = SqlSchemaUtils.getColumnName(it)
                "const val ${columnName.uppercase()} = \"$columnName\""
            }
        }
            }
        """.trimIndent()
    }

    /**
     * 构建枚举代码
     */
    private fun buildEnumCode(
        enumName: String,
        enumConfig: Map<Int, Array<String>>,
        packageName: String,
    ): String {
        val enumItems = enumConfig.entries.joinToString(",\n    ") { (value, names) ->
            "${names[0].uppercase()}($value, \"${names[0]}\", \"${names.getOrNull(1) ?: names[0]}\")"
        }

        return """
            package $packageName

            /**
             * $enumName 枚举
             */
            enum class $enumName(
                val value: Int,
                val code: String,
                val description: String
            ) {
                $enumItems;

                companion object {
                    fun fromValue(value: Int): $enumName? {
                        return values().find { it.value == value }
                    }

                    fun fromCode(code: String): $enumName? {
                        return values().find { it.code == code }
                    }
                }
            }
        """.trimIndent()
    }

    /**
     * 写入代码到文件
     */
    private fun writeCodeToFile(filePath: String, code: String) {
        val file = File(filePath)

        // 检查文件是否存在且包含不覆盖标记
        if (file.exists()) {
            val existingContent = file.readText()
            if (existingContent.contains(FLAG_DO_NOT_OVERWRITE)) {
                logger.info("文件包含不覆盖标记，跳过: $filePath")
                return
            }
        }

        // 创建父目录
        file.parentFile?.mkdirs()

        // 写入文件
        file.writeText(code, charset(getExtension().outputEncoding.get()))
    }
}
