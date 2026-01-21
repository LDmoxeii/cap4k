package com.only4.cap4k.plugin.codegen.generators.unit

import com.only4.cap4k.plugin.codegen.context.aggregate.AggregateContext
import com.only4.cap4k.plugin.codegen.gradle.AbstractCodegenTask
import com.only4.cap4k.plugin.codegen.imports.EntityImportManager
import com.only4.cap4k.plugin.codegen.misc.Inflector
import com.only4.cap4k.plugin.codegen.misc.SqlSchemaUtils
import com.only4.cap4k.plugin.codegen.misc.SqlSchemaUtils.LEFT_QUOTES_4_ID_ALIAS
import com.only4.cap4k.plugin.codegen.misc.SqlSchemaUtils.RIGHT_QUOTES_4_ID_ALIAS
import com.only4.cap4k.plugin.codegen.misc.addIfNone
import com.only4.cap4k.plugin.codegen.misc.removeText
import com.only4.cap4k.plugin.codegen.misc.refPackage
import com.only4.cap4k.plugin.codegen.misc.toLowerCamelCase
import com.only4.cap4k.plugin.codegen.misc.toUpperCamelCase
import com.only4.cap4k.plugin.codegen.template.TemplateNode
import java.io.File

class EntityUnitGenerator : AggregateUnitGenerator {
    override val tag: String = "entity"
    override val order: Int = 20

    private fun defaultTemplateNodes(): List<TemplateNode> {
        return listOf(
            TemplateNode().apply {
                type = "file"
                tag = this@EntityUnitGenerator.tag
                name = "{{ Entity }}.kt"
                format = "resource"
                data = "templates/entity.kt.peb"
                conflict = "overwrite"
            }
        )
    }

    context(ctx: AggregateContext)
    override fun collect(): List<GenerationUnit> {
        val units = mutableListOf<GenerationUnit>()

        ctx.tableMap.values.forEach { table ->
            if (SqlSchemaUtils.isIgnore(table)) return@forEach
            if (SqlSchemaUtils.hasRelation(table)) return@forEach

            val tableName = SqlSchemaUtils.getTableName(table)
            val entityTypeRaw = ctx.entityTypeMap[tableName] ?: return@forEach
            val columns = ctx.columnsMap[tableName] ?: return@forEach
            val ids = resolveIdColumns(columns)
            if (ids.isEmpty()) return@forEach

            val entityType = AggregateNaming.entityName(entityTypeRaw)
            if (ctx.typeMapping.containsKey(entityType)) return@forEach

            val tableContext = buildContext(table, entityType, columns, ids)
            val aggregate = ctx.resolveAggregateWithModule(tableName)
            val fullName = entityFullName(ctx, aggregate, entityType)
            val qType = AggregateNaming.querydslName(entityType)
            val fullQName = qEntityFullName(ctx, aggregate, qType)

            units.add(
                GenerationUnit(
                    id = "entity:${aggregate}:${entityType}",
                    tag = tag,
                    name = entityType,
                    order = order,
                    templateNodes = defaultTemplateNodes(),
                    context = tableContext,
                    exportTypes = mapOf(
                        entityType to fullName,
                        qType to fullQName,
                    ),
                )
            )
        }

        return units
    }

    context(ctx: AggregateContext)
    private fun buildContext(
        table: Map<String, Any?>,
        entityType: String,
        columns: List<Map<String, Any?>>,
        ids: List<Map<String, Any?>>,
    ): Map<String, Any?> {
        val tableName = SqlSchemaUtils.getTableName(table)
        val aggregate = ctx.resolveAggregateWithModule(tableName)
        val fullEntityPackage = ctx.tablePackageMap[tableName]!!

        val importManager = EntityImportManager()
        importManager.addBaseImports()

        val identityType = if (ids.size != 1) "Long" else SqlSchemaUtils.getColumnType(ids[0])
        if (ctx.typeMapping.containsKey(identityType)) {
            importManager.add(ctx.typeMapping[identityType]!!)
        }

        var baseClass: String? = null
        when {
            SqlSchemaUtils.isAggregateRoot(table) && ctx.getString("rootEntityBaseClass").isNotBlank() -> {
                baseClass = ctx.getString("rootEntityBaseClass")
            }

            ctx.getString("entityBaseClass").isNotBlank() -> {
                baseClass = ctx.getString("entityBaseClass")
            }
        }

        baseClass?.let {
            var resolved = it
                .replace("\${Entity}", entityType)
                .replace("\${IdentityType}", identityType)

            val head = resolved
                .substringBefore("<")
                .substringBefore("(")
                .trim()

            val simpleName = head.substringAfterLast('.')
            val fullName = when {
                head.contains('.') -> head
                ctx.typeMapping.containsKey(simpleName) -> ctx.typeMapping[simpleName]
                else -> null
            }

            if (!fullName.isNullOrBlank()) {
                importManager.add(fullName)
                resolved = resolved.replaceFirst(head, simpleName)
            }

            baseClass = resolved
        }

        val extendsClause = if (baseClass?.isNotBlank() == true) " : $baseClass()" else ""
        val implementsClause = if (SqlSchemaUtils.isValueObject(table)) ", ValueObject<$identityType>" else ""

        val existingImportLines = mutableListOf<String>()
        val annotationLines = mutableListOf<String>()
        val customerLines = mutableListOf<String>()

        val filePath = resolveSourceFile(
            ctx.getString("domainModulePath"),
            fullEntityPackage,
            entityType,
        )
        processEntityCustomerSourceFile(filePath, existingImportLines, annotationLines, customerLines)
        processAnnotationLines(table, columns, annotationLines, ids)

        existingImportLines.forEach { line ->
            importManager.add(line)
        }

        val columnDataList = columns.map { column ->
            prepareColumnData(
                table, column,
                ids, importManager
            )
        }

        val relationDataList = prepareRelationData(table, importManager)

        val deletedField = ctx.getString("deletedField")
        val hasSoftDelete = deletedField.isNotBlank() && SqlSchemaUtils.hasColumn(deletedField, columns)
        importManager.addIfNeeded(
            hasSoftDelete,
            "org.hibernate.annotations.SQLDelete",
            "org.hibernate.annotations.Where"
        )

        val needsIdGenerator = ids.size == 1 &&
                !SqlSchemaUtils.isValueObject(table) &&
                resolveEntityIdGenerator(table).isNotEmpty()
        importManager.addIfNeeded(
            needsIdGenerator,
            "org.hibernate.annotations.GenericGenerator"
        )

        val hasCollectionRelation = ctx.relationsMap[tableName]?.values?.any {
            val relationType = it.split(";")[0]
            relationType in listOf("OneToMany", "ManyToMany", "*OneToMany", "*ManyToMany")
        } == true
        importManager.addIfNeeded(
            hasCollectionRelation,
            "org.hibernate.annotations.Fetch",
            "org.hibernate.annotations.FetchMode"
        )

        importManager.addIfNeeded(
            SqlSchemaUtils.isValueObject(table),
            "com.only4.cap4k.ddd.core.domain.aggregate.ValueObject"
        )

        val finalImports = importManager.toImportLines()

        val resultContext = ctx.baseMap.toMutableMap()
        with(ctx) {
            resultContext.putContext(tag, "modulePath", ctx.domainPath)
            resultContext.putContext(tag, "templatePackage", refPackage(templatePackage[tag] ?: ""))
            resultContext.putContext(tag, "package", refPackage(aggregate))

            resultContext.putContext(tag, "Entity", entityType)

            resultContext.putContext(tag, "entityType", entityType)
            resultContext.putContext(tag, "extendsClause", extendsClause)
            resultContext.putContext(tag, "implementsClause", implementsClause)
            resultContext.putContext(tag, "columns", columnDataList)
            resultContext.putContext(tag, "relations", relationDataList)
            resultContext.putContext(tag, "annotationLines", annotationLines)
            resultContext.putContext(tag, "customerLines", customerLines)
            resultContext.putContext(tag, "imports", finalImports)
            resultContext.putContext(tag, "Comment", SqlSchemaUtils.getComment(table))
        }

        return resultContext
    }

    private fun entityFullName(
        ctx: AggregateContext,
        aggregate: String,
        entityType: String,
    ): String {
        val basePackage = ctx.getString("basePackage")
        val templatePackage = refPackage(ctx.templatePackage[tag] ?: "")
        val pkg = refPackage(aggregate)
        return "$basePackage${templatePackage}${pkg}${refPackage(entityType)}"
    }

    private fun qEntityFullName(
        ctx: AggregateContext,
        aggregate: String,
        qType: String,
    ): String {
        val basePackage = ctx.getString("basePackage")
        val templatePackage = refPackage(ctx.templatePackage[tag] ?: "")
        val pkg = refPackage(aggregate)
        return "$basePackage${templatePackage}${pkg}${refPackage(qType)}"
    }

    private fun resolveIdColumns(columns: List<Map<String, Any?>>): List<Map<String, Any?>> {
        return columns.filter { SqlSchemaUtils.isColumnPrimaryKey(it) }
    }

    private fun resolveSourceFile(
        baseDir: String,
        packageName: String,
        className: String,
    ): String {
        val packagePath = packageName.replace(".", File.separator)
        return "$baseDir${File.separator}src${File.separator}main${File.separator}kotlin${File.separator}$packagePath${File.separator}$className.kt"
    }

    context(ctx: AggregateContext)
    private fun resolveEntityIdGenerator(table: Map<String, Any?>): String {
        return when {
            SqlSchemaUtils.hasIdGenerator(table) -> {
                SqlSchemaUtils.getIdGenerator(table)
            }

            SqlSchemaUtils.isValueObject(table) -> {
                ctx.getString("idGenerator4ValueObject").ifBlank {
                    "com.only4.cap4k.ddd.domain.repo.Md5HashIdentifierGenerator"
                }
            }

            else -> {
                ctx.getString("idGenerator")
            }
        }
    }

    context(ctx: AggregateContext)
    private fun isColumnNeedGenerate(
        table: Map<String, Any?>,
        column: Map<String, Any?>,
        relations: Map<String, Map<String, String>>,
    ): Boolean {
        val tableName = SqlSchemaUtils.getTableName(table)
        val columnName = SqlSchemaUtils.getColumnName(column)

        if (SqlSchemaUtils.isIgnore(column)) return false

        val ignoreFields = ctx.getString("ignoreFields")
        if (ignoreFields.isNotBlank() && ignoreFields.lowercase().split(Regex("[,;]")).any {
            columnName.matches(Regex(it.replace("%", ".*", true)))
        }) return false

        if (!SqlSchemaUtils.isAggregateRoot(table)) {
            val parent = SqlSchemaUtils.getParent(table)
            val refMatchesParent = SqlSchemaUtils.hasReference(column) &&
                    parent.equals(SqlSchemaUtils.getReference(column), ignoreCase = true)
            val fkNameMatches = columnName.equals("${parent}_id", ignoreCase = true)
            if (refMatchesParent || fkNameMatches) return false
        }

        if (relations.containsKey(tableName)) {
            for (entry in relations[tableName]!!.entries) {
                val refInfos = entry.value.split(";")
                when (refInfos[0]) {
                    "ManyToOne", "OneToOne" -> if (columnName.equals(refInfos[1], ignoreCase = true)) {
                        return false
                    }
                }
            }
        }
        return true
    }

    context(ctx: AggregateContext)
    private fun isReadOnlyColumn(column: Map<String, Any?>): Boolean {
        if (SqlSchemaUtils.hasReadOnly(column)) return true

        val columnName = SqlSchemaUtils.getColumnName(column).lowercase()
        val readonlyFields = ctx.getString("readonlyFields")

        return readonlyFields.isNotBlank() && readonlyFields
            .lowercase()
            .split(Regex(AbstractCodegenTask.PATTERN_SPLITTER))
            .any { pattern -> columnName.matches(pattern.replace("%", ".*").toRegex()) }
    }

    context(ctx: AggregateContext)
    private fun isVersionColumn(column: Map<String, Any?>) =
        SqlSchemaUtils.getColumnName(column) == ctx.getString("versionField")

    private fun isIdColumn(column: Map<String, Any?>) = SqlSchemaUtils.isColumnPrimaryKey(column)

    context(ctx: AggregateContext)
    private fun generateFieldComment(column: Map<String, Any?>): List<String> {
        val fieldName = SqlSchemaUtils.getColumnName(column)
        val fieldType = SqlSchemaUtils.getColumnType(column)

        return buildList {
            add("/**")

            SqlSchemaUtils.getComment(column)
                .split(Regex(AbstractCodegenTask.PATTERN_LINE_BREAK))
                .filter { it.isNotEmpty() }
                .forEach { add(" * $it") }

            if (SqlSchemaUtils.hasEnum(column)) {
                val enumMap = ctx.enumConfigMap[fieldType] ?: ctx.enumConfigMap[SqlSchemaUtils.getType(column)]
                enumMap?.entries?.forEach { (key, value) ->
                    add(" * $key:${value[0]}:${value[1]}")
                }
            }

            if (fieldName == ctx.getString("versionField")) {
                add(" * 数据版本（支持乐观锁）")
            }

            if (ctx.getBoolean("generateDbType")) {
                add(" * ${SqlSchemaUtils.getColumnDbType(column)}")
            }

            add(" */")
        }
    }

    context(ctx: AggregateContext)
    private fun processEntityCustomerSourceFile(
        filePath: String,
        importLines: MutableList<String>,
        annotationLines: MutableList<String>,
        customerLines: MutableList<String>,
    ): Boolean {
        val file = File(filePath)
        if (file.exists()) {
            val content = file.readText(charset(ctx.getString("outputEncoding")))
            val lines = content.replace("\r\n", "\n").split("\n")

            var startMapperLine = 0
            var endMapperLine = 0
            var startClassLine = 0
            var inAnnotationBlock = false

            for (i in 1 until lines.size) {
                val line = lines[i]
                val trimmedLine = line.trim()

                when {
                    !inAnnotationBlock && startClassLine == 0 && trimmedLine.startsWith("import ") -> {
                        importLines.add(trimmedLine.removePrefix("import").trim())
                    }

                    !inAnnotationBlock && startClassLine == 0 && trimmedLine.startsWith("@") -> {
                        inAnnotationBlock = true
                        annotationLines.add(line)
                    }

                    inAnnotationBlock && startClassLine == 0 && trimmedLine.startsWith("@") -> {
                        annotationLines.add(line)
                    }

                    trimmedLine.startsWith("class") && startClassLine == 0 -> {
                        startClassLine = i
                        inAnnotationBlock = false
                    }

                    line.contains("【字段映射开始】") -> {
                        startMapperLine = i
                    }

                    line.contains("【字段映射结束】") -> {
                        endMapperLine = i
                    }

                    endMapperLine > 0 -> {
                        customerLines.add(line)
                    }
                }
            }

            for (i in customerLines.size - 1 downTo 0) {
                val line = customerLines[i]
                if (line.contains("}")) {
                    customerLines.removeAt(i)
                    if (!line.equals("}", ignoreCase = true)) {
                        customerLines.add(i, line.take(line.lastIndexOf("}")))
                    }
                    break
                }
                customerLines.removeAt(i)
            }

            if (startMapperLine == 0 || endMapperLine == 0) {
                return false
            }

            file.delete()
        }
        return true
    }

    context(ctx: AggregateContext)
    private fun processAnnotationLines(
        table: Map<String, Any?>,
        columns: List<Map<String, Any?>>,
        annotationLines: MutableList<String>,
        ids: List<Map<String, Any?>>,
    ) {
        val tableName = SqlSchemaUtils.getTableName(table)
        val entityTypeRaw = ctx.entityTypeMap[tableName] ?: ""
        val entityType = AggregateNaming.entityName(entityTypeRaw)

        removeText(annotationLines, """@Aggregate\(.*\)""")

        val cleanedComment = SqlSchemaUtils.getComment(table)
            .replace(Regex(AbstractCodegenTask.PATTERN_LINE_BREAK), "\\\\n")
            .replace("\"", "\\\"")
            .replace(";", "，")

        addIfNone(
            annotationLines,
            """@Aggregate\(.*\)""",
            """@Aggregate(aggregate = "${toUpperCamelCase(ctx.resolveAggregateWithModule(tableName))}", name = "$entityType", root = ${
                SqlSchemaUtils.isAggregateRoot(table)
            }, type = ${if (SqlSchemaUtils.isValueObject(table)) "Aggregate.TYPE_VALUE_OBJECT" else "Aggregate.TYPE_ENTITY"}, description = "$cleanedComment")"""
        ) { _, _ -> 0 }

        addIfNone(annotationLines, """@Entity(\(.*\))?""", "@Entity")

        if (ids.size > 1) {
            addIfNone(
                annotationLines,
                """@IdClass\(.*\)""",
                "@IdClass(${entityType}.${AbstractCodegenTask.DEFAULT_MUL_PRI_KEY_NAME}::class)"
            )
        }

        addIfNone(
            annotationLines,
            """@Table\(.*\)?""",
            "@Table(name = \"$LEFT_QUOTES_4_ID_ALIAS$tableName$RIGHT_QUOTES_4_ID_ALIAS\")"
        )

        addIfNone(annotationLines, """@DynamicInsert(\(.*\))?""", "@DynamicInsert")
        addIfNone(annotationLines, """@DynamicUpdate(\(.*\))?""", "@DynamicUpdate")

        val deletedField = ctx.getString("deletedField")
        val versionField = ctx.getString("versionField")

        if (deletedField.isNotBlank() && SqlSchemaUtils.hasColumn(deletedField, columns)) {
            if (ids.isEmpty()) {
                throw RuntimeException("实体缺失【主键】：$tableName")
            }

            val idFieldName = if (ids.size == 1) {
                toLowerCamelCase(SqlSchemaUtils.getColumnName(ids[0])) ?: SqlSchemaUtils.getColumnName(ids[0])
            } else {
                "(${
                    ids.joinToString(", ") {
                        toLowerCamelCase(SqlSchemaUtils.getColumnName(it)) ?: SqlSchemaUtils.getColumnName(
                            it
                        )
                    }
                })"
            }

            val idFieldValue = if (ids.size == 1) "?" else "(${ids.joinToString(", ") { "?" }})"

            if (SqlSchemaUtils.hasColumn(versionField, columns)) {
                addIfNone(
                    annotationLines,
                    """@SQLDelete\(.*\)?""",
                    """@SQLDelete(sql = "update $LEFT_QUOTES_4_ID_ALIAS$tableName$RIGHT_QUOTES_4_ID_ALIAS set $LEFT_QUOTES_4_ID_ALIAS$deletedField$RIGHT_QUOTES_4_ID_ALIAS = $LEFT_QUOTES_4_ID_ALIAS$idFieldName$RIGHT_QUOTES_4_ID_ALIAS where $LEFT_QUOTES_4_ID_ALIAS$idFieldName$RIGHT_QUOTES_4_ID_ALIAS = $idFieldValue and $LEFT_QUOTES_4_ID_ALIAS$versionField$RIGHT_QUOTES_4_ID_ALIAS = ?")"""
                )
            } else {
                addIfNone(
                    annotationLines,
                    """@SQLDelete\(.*\)?""",
                    """@SQLDelete(sql = "update $LEFT_QUOTES_4_ID_ALIAS$tableName$RIGHT_QUOTES_4_ID_ALIAS set $LEFT_QUOTES_4_ID_ALIAS$deletedField$RIGHT_QUOTES_4_ID_ALIAS = $LEFT_QUOTES_4_ID_ALIAS$idFieldName$RIGHT_QUOTES_4_ID_ALIAS where $LEFT_QUOTES_4_ID_ALIAS$idFieldName$RIGHT_QUOTES_4_ID_ALIAS = $idFieldValue")"""
                )
            }

            addIfNone(
                annotationLines,
                """@Where\(.*\)?""",
                """@Where(clause = "$LEFT_QUOTES_4_ID_ALIAS$deletedField$RIGHT_QUOTES_4_ID_ALIAS = 0")"""
            )
        }
    }

    context(ctx: AggregateContext)
    private fun prepareColumnData(
        table: Map<String, Any?>,
        column: Map<String, Any?>,
        ids: List<Map<String, Any?>>,
        importManager: EntityImportManager,
    ): Map<String, Any?> {
        val columnName = SqlSchemaUtils.getColumnName(column)
        val columnType = SqlSchemaUtils.getColumnType(column)

        val needGenerate = isColumnNeedGenerate(table, column, ctx.relationsMap) ||
                columnName == ctx.getString("versionField")

        if (!needGenerate) {
            return mapOf("needGenerate" to false)
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

        val comments = generateFieldComment(column)
        val comment = comments.joinToString("\n") { "    $it" }

        val annotations = mutableListOf<String>()

        if (isIdColumn(column)) {
            annotations.add("@Id")
            if (ids.size == 1) {
                val entityIdGenerator = resolveEntityIdGenerator(table)
                when {
                    SqlSchemaUtils.isValueObject(table) -> {
                        // no-op
                    }

                    entityIdGenerator.isNotEmpty() -> {
                        annotations.add("@GeneratedValue(generator = \"$entityIdGenerator\")")
                        annotations.add("@GenericGenerator(name = \"$entityIdGenerator\", strategy = \"$entityIdGenerator\")")
                    }

                    else -> {
                        annotations.add("@GeneratedValue(strategy = GenerationType.IDENTITY)")
                    }
                }
            }
        }

        if (isVersionColumn(column)) {
            annotations.add("@Version")
        }

        if (SqlSchemaUtils.hasType(column)) {
            val customType = SqlSchemaUtils.getType(column)
            val simpleType = customType.removeSuffix("?")
            importManager.add(ctx.typeMapping[simpleType]!!)
            annotations.add("@Convert(converter = $simpleType.Converter::class)")
        }

        val leftQuote = LEFT_QUOTES_4_ID_ALIAS.replace("\"", "\\\"")
        val rightQuote = RIGHT_QUOTES_4_ID_ALIAS.replace("\"", "\\\"")

        if (!updatable || !insertable) {
            annotations.add("@Column(name = \"$leftQuote$columnName$rightQuote\", insertable = $insertable, updatable = $updatable)")
        } else {
            annotations.add("@Column(name = \"$leftQuote$columnName$rightQuote\")")
        }

        val fieldName = toLowerCamelCase(columnName) ?: columnName
        val defaultJavaLiteral = SqlSchemaUtils.getColumnDefaultLiteral(column)
        val defaultValue = if (defaultJavaLiteral.isBlank()) "" else " = $defaultJavaLiteral"

        return mapOf(
            "needGenerate" to true,
            "columnName" to columnName,
            "fieldName" to fieldName,
            "fieldType" to columnType,
            "defaultValue" to defaultValue,
            "comment" to comment,
            "annotations" to annotations
        )
    }

    context(context: AggregateContext)
    private fun prepareRelationData(
        table: Map<String, Any?>,
        importManager: EntityImportManager,
    ): List<Map<String, Any?>> {
        return with(context) {
            val tableName = SqlSchemaUtils.getTableName(table)
            val result = mutableListOf<Map<String, Any?>>()

            if (!relationsMap.containsKey(tableName)) {
                return result
            }

            for ((refTableName, relationInfo) in relationsMap[tableName]!!) {
                val refInfos = relationInfo.split(";")
                val navTable = context.tableMap[refTableName]!!

                val fetchType = when {
                    relationInfo.endsWith(";LAZY") -> "LAZY"
                    SqlSchemaUtils.hasLazy(navTable) -> if (SqlSchemaUtils.isLazy(navTable, false)) "LAZY" else "EAGER"
                    else -> "EAGER"
                }

                val relation = refInfos[0]
                val joinColumn = refInfos[1]
                val leftQuote = LEFT_QUOTES_4_ID_ALIAS.replace("\"", "\\\"")
                val rightQuote = RIGHT_QUOTES_4_ID_ALIAS.replace("\"", "\\\"")

                val annotations = mutableListOf<String>()
                var fieldName = ""
                var fieldType = ""
                var defaultValue = ""
                var hasLoadMethod = false
                val refEntityType = context.entityTypeMap[refTableName] ?: ""
                val refEntityPackage = tablePackageMap[refTableName]!!
                val fullRefEntityType = "$refEntityPackage${refPackage(refEntityType)}"

                val entityPackage = tablePackageMap[tableName]!!

                importManager.addIfNeeded(entityPackage != refEntityPackage, fullRefEntityType)

                when (relation) {
                    "OneToMany" -> {
                        annotations.add("@${relation}(cascade = [CascadeType.ALL], fetch = FetchType.$fetchType, orphanRemoval = true)")
                        annotations.add("@Fetch(FetchMode.SUBSELECT)")
                        annotations.add("@JoinColumn(name = \"$leftQuote$joinColumn$rightQuote\", nullable = false)")

                        val countIsOne = SqlSchemaUtils.countIsOne(navTable)

                        fieldName = Inflector.pluralize(toLowerCamelCase(refEntityType) ?: refEntityType)
                        fieldType = "MutableList<$refEntityType>"
                        defaultValue = " = mutableListOf()"
                        hasLoadMethod = countIsOne
                    }

                    "*ManyToOne" -> {
                        annotations.add("@${relation.replace("*", "")}(cascade = [], fetch = FetchType.$fetchType)")
                        annotations.add("@JoinColumn(name = \"$leftQuote$joinColumn$rightQuote\", nullable = false, insertable = false, updatable = false)")

                        fieldName = toLowerCamelCase(refEntityType) ?: refEntityType
                        fieldType = "$refEntityType?"
                        defaultValue = " = null"
                    }

                    "ManyToOne" -> {
                        annotations.add("@${relation}(cascade = [], fetch = FetchType.$fetchType)")
                        annotations.add("@JoinColumn(name = \"$leftQuote$joinColumn$rightQuote\", nullable = false)")

                        fieldName = toLowerCamelCase(refEntityType) ?: refEntityType
                        fieldType = "$refEntityType?"
                        defaultValue = " = null"
                    }

                    "OneToOne" -> {
                        annotations.add("@${relation}(cascade = [], fetch = FetchType.$fetchType)")
                        annotations.add("@JoinColumn(name = \"$leftQuote$joinColumn$rightQuote\", nullable = false)")

                        fieldName = toLowerCamelCase(refEntityType) ?: refEntityType
                        fieldType = "$refEntityType?"
                        defaultValue = " = null"
                    }

                    "ManyToMany" -> {
                        annotations.add("@${relation}(cascade = [], fetch = FetchType.$fetchType)")
                        annotations.add("@Fetch(FetchMode.SUBSELECT)")
                        val joinTableName = refInfos[3]
                        val inverseJoinColumn = refInfos[2]
                        annotations.add(
                            "@JoinTable(name = \"$leftQuote$joinTableName$rightQuote\", " +
                                    "joinColumns = [JoinColumn(name = \"$leftQuote$joinColumn$rightQuote\", nullable = false)], " +
                                    "inverseJoinColumns = [JoinColumn(name = \"$leftQuote$inverseJoinColumn$rightQuote\", nullable = false)])"
                        )

                        fieldName = Inflector.pluralize(toLowerCamelCase(refEntityType) ?: refEntityType)
                        fieldType = "MutableList<$refEntityType>"
                        defaultValue = " = mutableListOf()"
                    }

                    "*ManyToMany" -> {
                        val entityTypeName = context.entityTypeMap[tableName] ?: ""
                        val fieldNameFromTable = Inflector.pluralize(toLowerCamelCase(entityTypeName) ?: entityTypeName)
                        annotations.add(
                            "@${
                                relation.replace(
                                    "*",
                                    ""
                                )
                            }(mappedBy = \"$fieldNameFromTable\", cascade = [], fetch = FetchType.$fetchType)"
                        )
                        annotations.add("@Fetch(FetchMode.SUBSELECT)")

                        fieldName = Inflector.pluralize(toLowerCamelCase(refEntityType) ?: refEntityType)
                        fieldType = "MutableList<$refEntityType>"
                        defaultValue = " = mutableListOf()"
                    }
                }

                result.add(
                    mapOf(
                        "relation" to relation,
                        "fieldName" to fieldName,
                        "fieldType" to fieldType,
                        "defaultValue" to defaultValue,
                        "annotations" to annotations,
                        "hasLoadMethod" to hasLoadMethod,
                        "entityType" to refEntityType,
                        "fullEntityType" to fullRefEntityType
                    )
                )
            }

            result
        }
    }
}
