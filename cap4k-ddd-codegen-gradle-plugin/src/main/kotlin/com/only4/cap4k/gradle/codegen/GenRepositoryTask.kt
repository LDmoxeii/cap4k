package com.only4.cap4k.gradle.codegen

import com.only4.cap4k.gradle.codegen.misc.NamingUtils
import com.only4.cap4k.gradle.codegen.misc.SourceFileUtils
import com.only4.cap4k.gradle.codegen.template.TemplateNode
import org.gradle.api.provider.Property
import org.gradle.api.tasks.Input
import org.gradle.api.tasks.TaskAction
import java.io.File

/**
 * 生成仓储类任务
 */
open class GenRepositoryTask : GenArchTask() {

    @get:Input
    override val projectName: Property<String> = project.objects.property(String::class.java)

    @get:Input
    override val projectGroup: Property<String> = project.objects.property(String::class.java)

    @get:Input
    override val projectVersion: Property<String> = project.objects.property(String::class.java)

    @get:Input
    override val projectDir: Property<String> = project.objects.property(String::class.java)

    private val aggregateRoot2AggregateNameMap = mutableMapOf<String, String>()

    @TaskAction
    override fun generate() {
        super.generate()
        generateRepositories()
    }

    private fun generateRepositories() {
        val repositoriesDir = resolveRepositoriesDirectory()
        val repositoryTemplateNodes = getRepositoryTemplateNodes()

        logger.info("开始生成仓储代码到目录: $repositoriesDir")
        renderTemplate(repositoryTemplateNodes, repositoriesDir)
        logger.info("仓储代码生成完成")
    }

    private fun resolveRepositoriesDirectory(): String {
        val ext = getExtension()
        return SourceFileUtils.resolvePackageDirectory(
            getAdapterModulePath(),
            "${ext.basePackage.get()}.$AGGREGATE_REPOSITORY_PACKAGE"
        )
    }

    private fun getRepositoryTemplateNodes(): List<TemplateNode> {
        return template?.select("repository")?.takeIf { it.isNotEmpty() }
            ?: listOf(getDefaultRepositoryTemplate())
    }

    private fun getDefaultRepositoryTemplate(): TemplateNode {
        val ext = getExtension()
        val repositorySupportQuerydsl = ext.generation.repositorySupportQuerydsl.get()
        val repositoryNameTemplate = ext.generation.repositoryNameTemplate.get()

        val template = """
            package ${'$'}{basePackage}.${AGGREGATE_REPOSITORY_PACKAGE}

            import ${'$'}{EntityPackage}.${'$'}{Entity}
            import com.only4.cap4k.ddd.core.domain.aggregate.annotation.Aggregate
            import com.only4.cap4k.ddd.domain.repo.AbstractJpaRepository
            ${if (repositorySupportQuerydsl) "import com.only4.cap4k.ddd.domain.repo.querydsl.AbstractQuerydslRepository" else ""}
            import org.springframework.data.jpa.repository.JpaRepository
            import org.springframework.data.jpa.repository.JpaSpecificationExecutor
            ${if (repositorySupportQuerydsl) "import org.springframework.data.querydsl.QuerydslPredicateExecutor" else ""}
            import org.springframework.stereotype.Component

            /**
             * 本文件由[cap4k-ddd-codegen-gradle-plugin]生成
             * @author cap4k-ddd-codegen
             * @date ${'$'}{date}
             */
            interface ${'$'}{Entity}Repository : JpaRepository<${'$'}{Entity}, ${'$'}{IdentityType}>, JpaSpecificationExecutor<${'$'}{Entity}>, QuerydslPredicateExecutor<${'$'}{Entity}>  {

                @Component
                @Aggregate(aggregate = "${'$'}{Aggregate}", name = "${'$'}{Entity}Repo", type = Aggregate.TYPE_REPOSITORY, description = "")
                class ${'$'}{Entity}JpaRepositoryAdapter(
                    jpaSpecificationExecutor: JpaSpecificationExecutor<${'$'}{Entity}>,
                    jpaRepository: JpaRepository<${'$'}{Entity}, ${'$'}{IdentityType}>
                ) : AbstractJpaRepository<${'$'}{Entity}, ${'$'}{IdentityType}>(
                    jpaSpecificationExecutor, 
                    jpaRepository
                )

                @Component
                @Aggregate(aggregate = "${'$'}{Aggregate}", name = "${'$'}{Entity}QuerydslRepo", type = Aggregate.TYPE_REPOSITORY, description = "")
                class ${'$'}{Entity}QuerydslRepositoryAdapter(
                    querydslPredicateExecutor: QuerydslPredicateExecutor<${'$'}{Entity}>
                ) : AbstractQuerydslRepository<${'$'}{Entity}>(
                    querydslPredicateExecutor
                )
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
            .filter { it.tag == "repository" }
            .forEach { templateNode ->
                logger.info("开始生成仓储代码")
                logger.info("")

                val kotlinFiles = SourceFileUtils.loadFiles(getDomainModulePath())
                    .asSequence()
                    .filter { it.extension.equals("kt", ignoreCase = true) }

                kotlinFiles.forEach { file ->
                    processKotlinFile(file, templateNode, parentPath)
                }

                logger.info("结束生成仓储代码")
            }
    }

    private fun processKotlinFile(file: File, templateNode: TemplateNode, parentPath: String) {
        val fullClassName = SourceFileUtils.resolvePackage(file.absolutePath)
        val content = file.readText(charset(getExtension().outputEncoding.get()))

        if (!isAggregateRoot(content, fullClassName)) return

        logger.info("发现聚合根: $fullClassName")

        val simpleClassName = file.nameWithoutExtension
        val identityClass = getIdentityType(content, simpleClassName)
        val aggregate = aggregateRoot2AggregateNameMap[fullClassName]
            ?: NamingUtils.toUpperCamelCase(simpleClassName) ?: simpleClassName

        logger.info("聚合根ID类型: $identityClass")

        // 检查模式匹配
        val pattern = templateNode.pattern
        val shouldGenerate = pattern.isNullOrBlank() ||
                Regex(pattern).matches(fullClassName)

        if (shouldGenerate) {
            generateRepositoryForAggregate(
                templateNode = templateNode,
                parentPath = parentPath,
                file = file,
                simpleClassName = simpleClassName,
                identityClass = identityClass,
                aggregate = aggregate
            )
        }
    }

    private fun generateRepositoryForAggregate(
        templateNode: TemplateNode,
        parentPath: String,
        file: File,
        simpleClassName: String,
        identityClass: String,
        aggregate: String,
    ) {
        val context = buildRepositoryContext(
            file = file,
            simpleClassName = simpleClassName,
            identityClass = identityClass,
            aggregate = aggregate
        )

        val pathNode = templateNode.cloneTemplateNode().resolve(context)
        forceRender(pathNode, parentPath)
    }

    private fun buildRepositoryContext(
        file: File,
        simpleClassName: String,
        identityClass: String,
        aggregate: String,
    ): MutableMap<String, String> {
        return getEscapeContext().toMutableMap().apply {
            val entityPackage = SourceFileUtils.resolvePackage(file.absolutePath)

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
    }

    private fun isAggregateRoot(content: String, className: String): Boolean {
        return content.lines()
            .asSequence()
            .filter { line -> line.trim().startsWith("@") }
            .any { line ->
                // 检查@Aggregate(root = true)注解
                val aggregateRootRegex = Regex("@Aggregate\\s*\\(.*root\\s*=\\s*true.*\\)")
                if (aggregateRootRegex.matches(line)) {
                    // 提取aggregate名称并缓存
                    Regex("\\s*aggregate\\s*=\\s*\"([^\"]*)\"")
                        .find(line)
                        ?.groupValues
                        ?.getOrNull(1)
                        ?.let { aggregateName ->
                            aggregateRoot2AggregateNameMap[className] = aggregateName
                        }
                    true
                } else false
            }
    }

    private fun getIdentityType(content: String, simpleClassName: String): String {
        val defaultIdentityType = "Long"
        val idAnnotationRegex = Regex("^\\s*@Id(\\(\\s*\\))?\\s*$")
        val fieldRegex =
            Regex("^\\s*(var|val)\\s+([_A-Za-z][_A-Za-z0-9]*)\\s*:\\s*([_A-Za-z][_A-Za-z0-9.<>?]*)(\\s*=.*)?\\s*,?\\s*$")

        val lines = content.lines()
        val idAnnotationCount = lines.count(idAnnotationRegex::matches)

        return when {
            idAnnotationCount > 1 -> "$simpleClassName.$DEFAULT_MUL_PRI_KEY_NAME"
            idAnnotationCount == 0 -> defaultIdentityType
            else -> {
                // 使用windowed找到@Id注解后的第一个字段声明
                lines.asSequence()
                    .windowed(size = 10, partialWindows = true) // 查看@Id注解后最多10行
                    .firstOrNull { window -> idAnnotationRegex.matches(window.first()) }
                    ?.drop(1) // 跳过@Id注解行
                    ?.firstNotNullOfOrNull { line ->
                        fieldRegex.matchEntire(line)?.groupValues?.getOrNull(3)
                    }
                    ?: defaultIdentityType
            }
        }
    }
}

