package com.only4.cap4k.gradle.codegen

import com.only4.cap4k.gradle.codegen.misc.loadFiles
import com.only4.cap4k.gradle.codegen.misc.resolvePackage
import com.only4.cap4k.gradle.codegen.misc.resolvePackageDirectory
import com.only4.cap4k.gradle.codegen.misc.toUpperCamelCase
import com.only4.cap4k.gradle.codegen.template.TemplateNode
import org.gradle.api.tasks.TaskAction
import java.io.File

/**
 * 生成仓储类任务
 */
open class GenRepositoryTask : GenArchTask() {

    /**
     * SUGGEST_RENAME: aggregateRoot2AggregateNameMap -> aggregateRootToAggregateName
     * key = 完整类名(FQN), value = @Aggregate(aggregate="xxx") 中的 aggregate 名称
     */
    private val aggregateRoot2AggregateNameMap = mutableMapOf<String, String>()

    // 预编译正则
    private val AGGREGATE_ROOT_ANNOTATION = Regex("@Aggregate\\s*\\(.*root\\s*=\\s*true.*\\)")
    private val AGGREGATE_NAME_CAPTURE = Regex("aggregate\\s*=\\s*\"([^\"]+)\"")
    private val ID_ANNOTATION_REGEX = Regex("^\\s*@Id(\\(\\s*\\))?\\s*$")
    private val FIELD_DECL_REGEX =
        Regex("^\\s*(var|val)\\s+([_A-Za-z][_A-Za-z0-9]*)\\s*:\\s*([_A-Za-z][_A-Za-z0-9.<>,?]*)(\\s*=.*)?\\s*(,)?\\s*$")

    @TaskAction
    override fun generate() {
        renderFileSwitch = false
        super.generate()
        generateRepositories()
    }

    private fun generateRepositories() {
        if (template == null) {
            logger.warn("模板尚未加载，跳过仓储生成")
            return
        }
        val repositoriesDir = resolveRepositoriesDirectory()
        val repositoryTemplateNodes = getRepositoryTemplateNodes()
        if (repositoryTemplateNodes.isEmpty()) {
            logger.warn("未找到 repository 模板节点，跳过仓储生成")
            return
        }
        logger.info("开始生成仓储代码到目录: $repositoriesDir")
        renderTemplate(repositoryTemplateNodes, repositoriesDir)
        logger.info("仓储代码生成完成")
    }

    private fun resolveRepositoriesDirectory(): String {
        val ext = extension.get()
        return resolvePackageDirectory(
            getAdapterModulePath(),
            "${ext.basePackage.get()}.$AGGREGATE_REPOSITORY_PACKAGE"
        )
    }

    private fun getRepositoryTemplateNodes(): List<TemplateNode> =
        template?.select("repository").takeIf { !it.isNullOrEmpty() }
            ?: listOf(getDefaultRepositoryTemplate())

    /**
     * 构建默认仓储模板
     * 按需拼接 Querydsl 相关内容（避免未启用时仍引用导致编译失败）
     */
    private fun getDefaultRepositoryTemplate(): TemplateNode {
        val ext = extension.get()
        val repositorySupportQuerydsl = ext.generation.repositorySupportQuerydsl.get()
        val repositoryNameTemplate = ext.generation.repositoryNameTemplate.get()

        val extendsParts = mutableListOf(
            "JpaRepository<\${Entity}, \${IdentityType}>",
            "JpaSpecificationExecutor<\${Entity}>"
        )
        if (repositorySupportQuerydsl) extendsParts += "QuerydslPredicateExecutor<\${Entity}>"

        val adapters = buildString {
            append(
                """
                @Component
                @Aggregate(aggregate = "${'$'}{Aggregate}", name = "${'$'}{Entity}Repo", type = Aggregate.TYPE_REPOSITORY, description = "")
                class ${'$'}{Entity}JpaRepositoryAdapter(
                    jpaSpecificationExecutor: JpaSpecificationExecutor<${'$'}{Entity}>,
                    jpaRepository: JpaRepository<${'$'}{Entity}, ${'$'}{IdentityType}>
                ) : AbstractJpaRepository<${'$'}{Entity}, ${'$'}{IdentityType}>(
                    jpaSpecificationExecutor,
                    jpaRepository
                )
                """.trimIndent()
            )
            if (repositorySupportQuerydsl) {
                append("\n\n")
                append(
                    """
                    @Component
                    @Aggregate(aggregate = "${'$'}{Aggregate}", name = "${'$'}{Entity}QuerydslRepo", type = Aggregate.TYPE_REPOSITORY, description = "")
                    class ${'$'}{Entity}QuerydslRepositoryAdapter(
                        querydslPredicateExecutor: QuerydslPredicateExecutor<${'$'}{Entity}>
                    ) : AbstractQuerydslRepository<${'$'}{Entity}>(
                        querydslPredicateExecutor
                    )
                    """.trimIndent()
                )
            }
        }

        val extraImports = if (repositorySupportQuerydsl)
            """
            import com.only4.cap4k.ddd.domain.repo.querydsl.AbstractQuerydslRepository
            import org.springframework.data.querydsl.QuerydslPredicateExecutor
            """.trimIndent()
        else ""

        val template = """
            package ${'$'}{basePackage}.${AGGREGATE_REPOSITORY_PACKAGE}

            import ${'$'}{EntityPackage}.${'$'}{Entity}
            import com.only4.cap4k.ddd.core.domain.aggregate.annotation.Aggregate
            import com.only4.cap4k.ddd.domain.repo.AbstractJpaRepository
            import org.springframework.data.jpa.repository.JpaRepository
            import org.springframework.data.jpa.repository.JpaSpecificationExecutor
            $extraImports
            import org.springframework.stereotype.Component

            /**
             * 本文件由[cap4k-ddd-codegen-gradle-plugin]生成
             * @date ${'$'}{date}
             */
            interface ${'$'}{Entity}Repository : ${extendsParts.joinToString(", ")} {

                $adapters
            }
        """.trimIndent()

        return TemplateNode().apply {
            type = "file"
            tag = "repository"
            name = "$repositoryNameTemplate.kt"
            format = "raw"
            data = template
            conflict = "skip"
        }
    }

    override fun renderTemplate(templateNodes: List<TemplateNode>, parentPath: String) {
        templateNodes
            .asSequence()
            .filter { it.tag == "repository" }
            .forEach { templateNode ->
                logger.info("开始生成仓储代码")
                val kotlinFiles = loadFiles(getDomainModulePath())
                    .asSequence()
                    .filter { it.isFile && it.extension.equals("kt", ignoreCase = true) }

                kotlinFiles.forEach { file ->
                    processKotlinFile(file, templateNode, parentPath)
                }

                logger.info("结束生成仓储代码")
            }
    }

    private fun processKotlinFile(file: File, templateNode: TemplateNode, parentPath: String) {
        val fullClassName = resolvePackage(file.absolutePath)
        val content = runCatching { file.readText(charset(extension.get().outputEncoding.get())) }
            .getOrElse {
                logger.warn("读取文件失败，跳过: ${file.absolutePath}", it)
                return
            }

        if (!isAggregateRoot(content, fullClassName)) return

        val simpleClassName = file.nameWithoutExtension
        val identityClass = getIdentityType(content, simpleClassName)
        val aggregate = aggregateRoot2AggregateNameMap[fullClassName]
            ?: toUpperCamelCase(simpleClassName) ?: simpleClassName

        logger.info("聚合根: $fullClassName, ID=$identityClass, Aggregate=$aggregate")

        val pattern = templateNode.pattern
        val shouldGenerate = pattern.isBlank() || Regex(pattern).matches(fullClassName)
        if (!shouldGenerate) return

        val context = buildRepositoryContext(
            file = file,
            simpleClassName = simpleClassName,
            identityClass = identityClass,
            aggregate = aggregate
        )
        val pathNode = templateNode.deepCopy().resolve(context)
        forceRender(pathNode, parentPath)
    }

    private fun buildRepositoryContext(
        file: File,
        simpleClassName: String,
        identityClass: String,
        aggregate: String,
    ): MutableMap<String, String> =
        getEscapeContext().toMutableMap().apply {
            val entityPackage = resolvePackage(file.absolutePath)
            putAll(
                mapOf(
                    "EntityPackage" to entityPackage,
                    "EntityType" to simpleClassName,
                    "Entity" to simpleClassName,
                    "IdentityClass" to identityClass,
                    "IdentityType" to identityClass,
                    "Identity" to identityClass,
                    "Aggregate" to aggregate
                )
            )
        }

    /**
     * 是否聚合根：检测 @Aggregate(root = true)
     * 同时缓存 aggregate 名称
     */
    private fun isAggregateRoot(content: String, className: String): Boolean {
        content.lineSequence()
            .filter { it.trimStart().startsWith("@") }
            .forEach { line ->
                if (AGGREGATE_ROOT_ANNOTATION.containsMatchIn(line)) {
                    AGGREGATE_NAME_CAPTURE.find(line)
                        ?.groupValues
                        ?.getOrNull(1)
                        ?.takeIf { it.isNotBlank() }
                        ?.let { aggregateRoot2AggregateNameMap[className] = it }
                    return true
                }
            }
        return false
    }

    /**
     * 主键类型推断：
     * 1. 多个 @Id -> 复合主键
     * 2. 找到唯一一个 @Id -> 向后寻找第一个字段声明的类型
     * 3. 未找到 -> 默认 Long
     */
    private fun getIdentityType(content: String, simpleClassName: String): String {
        val lines = content.lines()
        val idIndices = lines.mapIndexedNotNull { idx, line ->
            if (ID_ANNOTATION_REGEX.matches(line)) idx else null
        }

        if (idIndices.size > 1) return "$simpleClassName.$DEFAULT_MUL_PRI_KEY_NAME"
        if (idIndices.isEmpty()) return "Long"

        val idLineIndex = idIndices.first()
        for (i in idLineIndex + 1 until lines.size) {
            val line = lines[i]
            val match = FIELD_DECL_REGEX.matchEntire(line) ?: continue
            val type = match.groupValues.getOrNull(3)?.trim()
            if (!type.isNullOrBlank()) return type
        }
        return "Long"
    }
}

