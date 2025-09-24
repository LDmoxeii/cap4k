package com.only4.cap4k.gradle.codegen

import com.only4.cap4k.gradle.codegen.misc.NamingUtils
import com.only4.cap4k.gradle.codegen.misc.SourceFileUtils
import com.only4.cap4k.gradle.codegen.template.PathNode
import com.only4.cap4k.gradle.codegen.template.TemplateNode
import org.gradle.api.provider.Property
import org.gradle.api.tasks.Input
import org.gradle.api.tasks.TaskAction
import java.io.IOException
import java.util.regex.Pattern

/**
 * 生成仓储类任务
 */
open class GenRepositoryTask : GenArchTask() {

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

    private val aggregateRoot2AggregateNameMap = mutableMapOf<String, String>()
    private var hasRepositoryTemplate = false

    @TaskAction
    override fun generate() {
        renderFileSwitch = false
        super.generate()

        if (!hasRepositoryTemplate) {
            val ext = getExtension()
            val repositoriesDir = SourceFileUtils.resolvePackageDirectory(
                getAdapterModulePath(),
                "${ext.basePackage.get()}.$AGGREGATE_REPOSITORY_PACKAGE"
            )

            var repositoryTemplateNodes = template?.select("repository")
            if (repositoryTemplateNodes.isNullOrEmpty()) {
                repositoryTemplateNodes = listOf(getDefaultRepositoryTemplate())
            }

            try {
                renderTemplate(repositoryTemplateNodes, repositoriesDir)
            } catch (e: IOException) {
                logger.error("模板文件写入失败！", e)
            }
        }
    }

    private fun getDefaultRepositoryTemplate(): TemplateNode {
        val ext = getExtension()
        val repositorySupportQuerydsl = ext.generation.repositorySupportQuerydsl.get()
        val repositoryNameTemplate = ext.generation.repositoryNameTemplate.get()

        val template = "package \${basePackage}.$AGGREGATE_REPOSITORY_PACKAGE\n" +
                "\n" +
                "import \${EntityPackage}.\${Entity}\n" +
                "import com.only4.cap4k.ddd.core.domain.aggregate.annotation.Aggregate\n" +
                "import com.only4.cap4k.ddd.core.domain.repo.AbstractJpaRepository\n" +
                "import com.only4.cap4k.ddd.core.domain.repo.AggregateRepository\n" +
                (if (!repositorySupportQuerydsl) "" else
                    "import com.only4.cap4k.ddd.core.domain.repo.querydsl.AbstractQuerydslRepository\n") +
                "import org.springframework.data.jpa.repository.JpaRepository\n" +
                "import org.springframework.data.jpa.repository.JpaSpecificationExecutor\n" +
                (if (!repositorySupportQuerydsl) "" else
                    "import org.springframework.data.querydsl.QuerydslPredicateExecutor\n") +
                "import org.springframework.stereotype.Component\n" +
                "\n" +
                "/**\n" +
                " * 本文件由[cap4k-ddd-codegen-gradle-plugin]生成\n" +
                " * @author cap4k-ddd-codegen\n" +
                " * @date \${date}\n" +
                " */\n" +
                "interface $repositoryNameTemplate : AggregateRepository<\${Entity}, \${IdentityType}>, JpaSpecificationExecutor<\${Entity}>, JpaRepository<\${Entity}, \${IdentityType}>" +
                (if (!repositorySupportQuerydsl) "" else ", QuerydslPredicateExecutor<\${Entity}>") + " {\n" +
                "\n" +
                "    @Component\n" +
                "    @Aggregate(aggregate = \"\${Aggregate}\", name = \"\${Entity}Repo\", type = Aggregate.TYPE_REPOSITORY, description = \"\")\n" +
                "    class \${Entity}JpaRepositoryAdapter(private val jpaSpecificationExecutor: JpaSpecificationExecutor<\${Entity}>, private val jpaRepository: JpaRepository<\${Entity}, \${IdentityType}>) : AbstractJpaRepository<\${Entity}, \${IdentityType}>(jpaSpecificationExecutor, jpaRepository)\n" +
                (if (!repositorySupportQuerydsl) "" else ("\n" +
                        "    @Component\n" +
                        "    @Aggregate(aggregate = \"\${Aggregate}\", name = \"\${Entity}QuerydslRepo\", type = Aggregate.TYPE_REPOSITORY, description = \"\")\n" +
                        "    class \${Entity}QuerydslRepositoryAdapter(private val querydslPredicateExecutor: QuerydslPredicateExecutor<\${Entity}>) : AbstractQuerydslRepository<\${Entity}>(querydslPredicateExecutor)\n")) +
                "\n" +
                "}\n"

        val templateNode = TemplateNode()
        templateNode.type = "file"
        templateNode.tag = "repository"
        templateNode.name = "$repositoryNameTemplate.kt"
        templateNode.format = "raw"
        templateNode.data = template
        templateNode.conflict = "skip"
        return templateNode
    }

    override fun renderTemplate(templateNodes: List<TemplateNode>, parentPath: String) {
        templateNodes.forEach { templateNode ->
            if ("repository" == templateNode.tag) {
                hasRepositoryTemplate = true
                logger.info("开始生成仓储代码")
                logger.info("聚合根标注注解：${getAggregateRootAnnotation()}")
                logger.info("")

                val files = SourceFileUtils.loadFiles(getDomainModulePath())
                    .filter { "kt".equals(it.extension, ignoreCase = true) }

                files.forEach { file ->
                    logger.debug("发现Kotlin文件: ${SourceFileUtils.resolvePackage(file.absolutePath)}")
                }
                logger.info("发现kotlin文件数量：${files.size}")

                files.forEach { file ->
                    val fullClassName = SourceFileUtils.resolvePackage(file.absolutePath)
                    logger.debug("解析Kotlin文件: $fullClassName")
                    val content = file.readText(charset(getExtension().outputEncoding.get()))

                    val isAggregateRoot = isAggregateRoot(content, fullClassName)
                    if (isAggregateRoot) {
                        logger.info("发现聚合根: $fullClassName")

                        val simpleClassName = file.nameWithoutExtension
                        val identityClass = getIdentityType(content, simpleClassName)
                        val aggregate = aggregateRoot2AggregateNameMap[fullClassName]
                            ?: NamingUtils.toUpperCamelCase(simpleClassName) ?: simpleClassName
                        logger.info("聚合根ID类型: $identityClass")

                        val pattern = templateNode.pattern
                        if (pattern.isNullOrBlank() || Pattern.compile(pattern).asPredicate().test(fullClassName)) {
                            try {
                                val context = getEscapeContext().toMutableMap()
                                context["EntityPackage"] = SourceFileUtils.resolvePackage(file.absolutePath)
                                context["EntityType"] = simpleClassName
                                context["Entity"] = simpleClassName
                                context["IdentityClass"] = identityClass
                                context["IdentityType"] = identityClass
                                context["Identity"] = identityClass
                                context["Aggregate"] = aggregate
                                val pathNode = templateNode.cloneTemplateNode().resolve(context) as PathNode
                                forceRender(pathNode, parentPath)
                            } catch (e: IOException) {
                                logger.error("生成仓储文件失败", e)
                            }
                        }
                    }
                }
                logger.info("结束生成仓储代码")
            }
        }
    }

    private var renderFileSwitch = true

    private fun isAggregateRoot(content: String, className: String): Boolean {
        return content.split(Regex("(\\r)|(\\n)|(\\r\\n)"))
            .filter { line -> line.trim().startsWith("@") || line.replace("\\s".toRegex(), "") == "/*@AggregateRoot*/" }
            .any { line ->
                var hasAggregateRoot = false
                if (getAggregateRootAnnotation().isBlank()) {
                    // 注解风格 @AggregateRoot()
                    val oldAggregateRoot1 = line.matches(Regex("@AggregateRoot(\\(.*\\))?"))
                    // 注释风格 /* @AggregateRoot */
                    val oldAggregateRoot2 = line.matches(Regex("^\\s*/\\*\\s*@AggregateRoot\\s*\\*\\/\\s*$"))
                    hasAggregateRoot = oldAggregateRoot1 || oldAggregateRoot2
                } else {
                    // 注解风格 @AggregateRoot()
                    val isAggregateRootAnnotation = line.matches(Regex("@AggregateRoot(\\(.*\\))?"))
                    // 注解风格 配置
                    val isAggregateRootAnnotationFullName =
                        line.matches(Regex("${getAggregateRootAnnotation()}(\\(.*\\))?"))
                    hasAggregateRoot = isAggregateRootAnnotationFullName || isAggregateRootAnnotation
                }
                if (hasAggregateRoot) {
                    return@any hasAggregateRoot
                }

                val aggregateRoot = line.matches(Regex("@Aggregate\\s*\\(.*root\\s*=\\s*true.*\\)"))
                if (aggregateRoot) {
                    val aggregatePattern = Pattern.compile("\\s*aggregate\\s*=\\s*\\\"([^\\\"]*)\\\"")
                    val matcher = aggregatePattern.matcher(line)
                    if (matcher.find() && matcher.groupCount() == 1) {
                        aggregateRoot2AggregateNameMap[className] = matcher.group(1)
                    }
                }

                logger.debug("annotationline: $line")
                logger.debug("hasAggregateRoot=$hasAggregateRoot")
                logger.debug("aggregateRoot=$aggregateRoot")
                return@any aggregateRoot
            }
    }

    private fun getIdentityType(content: String, simpleClassName: String): String {
        val defaultIdentityType = "Long"
        val idAnnotationPattern = Pattern.compile("^\\s*@Id(\\(\\s*\\))?\\s*$")
        val fieldPattern =
            Pattern.compile("^\\s*(private|protected|public)?\\s*([_A-Za-z][_A-Za-z0-9]*)\\s*([_A-Za-z][_A-Za-z0-9]*)\\s*$")

        val idAnnotationCount = content.split(Regex("(\\r)|(\\n)|(\\r\\n)"))
            .count { idAnnotationPattern.asPredicate().test(it) }

        return when {
            idAnnotationCount > 1 -> "$simpleClassName.$DEFAULT_MUL_PRI_KEY_NAME"
            idAnnotationCount == 0 -> defaultIdentityType
            else -> {
                var idAnnotationReaded = false
                for (line in content.split(Regex("(\\r)|(\\n)|(\\r\\n)"))) {
                    if (!idAnnotationReaded) {
                        if (idAnnotationPattern.asPredicate().test(line)) {
                            idAnnotationReaded = true
                        }
                    } else {
                        val matcher = fieldPattern.matcher(line)
                        if (matcher.find() && matcher.groupCount() > 2) {
                            return matcher.group(matcher.groupCount() - 1) ?: defaultIdentityType
                        }
                    }
                }
                defaultIdentityType
            }
        }
    }
}

