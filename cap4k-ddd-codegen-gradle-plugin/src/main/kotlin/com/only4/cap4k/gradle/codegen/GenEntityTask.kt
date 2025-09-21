package com.only4.cap4k.gradle.codegen

import com.only4.cap4k.gradle.codegen.misc.NamingUtils
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

    private val tableMap = mutableMapOf<String, Map<String, Any?>>()
    private val tableModuleMap = mutableMapOf<String, String>()
    private val tableAggregateMap = mutableMapOf<String, String>()
    private val columnsMap = mutableMapOf<String, List<Map<String, Any?>>>()
    private val enumConfigMap = mutableMapOf<String, Map<Int, Array<String>>>()
    private val enumPackageMap = mutableMapOf<String, String>()
    private val enumTableNameMap = mutableMapOf<String, String>()
    private val entityJavaTypeMap = mutableMapOf<String, String>()
    private val templateNodeMap = mutableMapOf<String, MutableList<TemplateNode>>()

    private var dbType = "mysql"
    private var aggregatesPath = ""
    private var schemaPath = ""
    private var subscriberPath = ""

    @TaskAction
    fun generate() {
        logger.info("生成实体类...")

        val ext = getExtension()

        // 设置数据库类型和方言
        val (url, username, password) = getDatabaseConfig()
        dbType = SqlSchemaUtils.recognizeDbType(url)
        SqlSchemaUtils.processSqlDialect(dbType)

        logger.info("数据库连接：$url")
        logger.info("数据库账号：$username")
        logger.info("数据库密码：$password")
        logger.info("数据库名称：${ext.database.schema.get()}")
        logger.info("包含表：${ext.database.tables.get()}")
        logger.info("忽略表：${ext.database.ignoreTables.get()}")
        logger.info("乐观锁字段：${ext.generation.versionField.get()}")

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
            val comment = SqlSchemaUtils.getTableComment(table)

            logger.info("处理表: $tableName - $comment")

            // 缓存表信息
            tableMap[tableName] = table

            // 解析表注解
            val annotations = SqlSchemaUtils.parseAnnotations(comment)
            val module = annotations["Module"] ?: ""
            val aggregate = annotations["Aggregate"] ?: ""

            if (module.isNotEmpty()) {
                tableModuleMap[tableName] = module
            }
            if (aggregate.isNotEmpty()) {
                tableAggregateMap[tableName] = aggregate
            }

            // 获取表的列信息
            val columns = allColumns.filter { column ->
                SqlSchemaUtils.isColumnInTable(column, table)
            }.sortedBy { SqlSchemaUtils.getOrdinalPosition(it) }

            columnsMap[tableName] = columns

            // 解析枚举配置
            parseEnumConfigs(tableName, columns)

            // 生成实体Java类型映射
            entityJavaTypeMap[tableName] = NamingUtils.toUpperCamelCase(tableName) ?: tableName
        }

        logger.info("数据库结构解析完成，共处理 ${tables.size} 张表")
    }

    /**
     * 解析枚举配置
     */
    private fun parseEnumConfigs(tableName: String, columns: List<Map<String, Any?>>) {
        for (column in columns) {
            if (SqlSchemaUtils.hasEnum(column)) {
                val columnName = SqlSchemaUtils.getColumnName(column)
                val enumConfig = SqlSchemaUtils.getEnum(column)
                val enumKey = "${tableName}_${columnName}"

                enumConfigMap[enumKey] = enumConfig
                enumPackageMap[enumKey] = DEFAULT_ENUM_PACKAGE
                enumTableNameMap[enumKey] = tableName

                logger.debug("发现枚举配置: $enumKey -> $enumConfig")
            }
        }
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

                // 如果配置了生成聚合
                if (ext.generation.generateAggregate.get()) {
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
        val packageName = buildPackageName(tableName, "entities")
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

        return when {
            module?.isNotEmpty() == true && aggregate?.isNotEmpty() == true ->
                "$basePackage.domain.$module.$aggregate.$subPackage"

            aggregate?.isNotEmpty() == true ->
                "$basePackage.domain.$aggregate.$subPackage"

            module?.isNotEmpty() == true ->
                "$basePackage.domain.$module.$subPackage"

            else ->
                "$basePackage.domain.$subPackage"
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
        val aggregateName = annotations["Aggregate"] ?: NamingUtils.toLowerCamelCase(tableName) ?: tableName
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
            val javaType = SqlSchemaUtils.getColumnJavaType(column)
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

        return """
            package $packageName

            import com.only4.cap4k.ddd.core.domain.aggregate.annotation.Aggregate

            /**
             * $comment 聚合
             */
            @Aggregate(aggregate = "${NamingUtils.toLowerCamelCase(tableName)}", type = "aggregate")
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
