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
     * key = 完整类名(FQN), value = @Aggregate(aggregate="xxx") 中的 aggregate 名称
     */
    private val aggregateRootToAggregateName = mutableMapOf<String, String>()

    // 预编译正则
    private companion object {
        val AGGREGATE_ROOT_ANNOTATION = "@Aggregate\\s*\\(.*root\\s*=\\s*true.*\\)".toRegex()
        val AGGREGATE_NAME_CAPTURE = "aggregate\\s*=\\s*\"([^\"]+)\"".toRegex()
        val ID_ANNOTATION_REGEX = "^\\s*@Id(\\(\\s*\\))?\\s*$".toRegex()
        val FIELD_DECL_REGEX =
            "^\\s*(var|val)\\s+([_A-Za-z][_A-Za-z0-9]*)\\s*:\\s*([_A-Za-z][_A-Za-z0-9.<>,?]*)(\\s*=.*)?\\s*(,)?\\s*$".toRegex()
    }

    @TaskAction
    override fun generate() {
        renderFileSwitch = false
        super.generate()
        generateRepositories()
    }

    private fun generateRepositories() {
        template ?: run {
            logger.warn("模板尚未加载，跳过仓储生成")
            return
        }

        val repositoriesDir = resolveRepositoriesDirectory()
        val repositoryTemplateNodes = resolveRepositoryTemplateNodes()

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

    private fun resolveRepositoryTemplateNodes(): List<TemplateNode> =
        template?.select("repository")?.takeIf { it.isNotEmpty() }
            ?: listOf(resolveDefaultRepositoryTemplate())

    private fun resolveDefaultRepositoryTemplate(): TemplateNode {
        val ext = extension.get()
        val repositoryNameTemplate = ext.generation.repositoryNameTemplate.get()

        return TemplateNode().apply {
            type = "file"
            tag = "repository"
            name = "$repositoryNameTemplate.kt"
            format = "velocity"
            data = "vm/repository/Repository.kt.vm"
            conflict = "skip"
        }
    }

    override fun renderTemplate(templateNodes: List<TemplateNode>, parentPath: String) {
        templateNodes.asSequence()
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
        val content = runCatching {
            file.readText(charset(extension.get().outputEncoding.get()))
        }.getOrElse {
            logger.warn("读取文件失败，跳过: ${file.absolutePath}", it)
            return
        }

        if (!isAggregateRoot(content, fullClassName)) return

        val simpleClassName = file.nameWithoutExtension
        val identityClass = getIdentityType(content, simpleClassName)
        val aggregate = aggregateRootToAggregateName[fullClassName]
            ?: toUpperCamelCase(simpleClassName) ?: simpleClassName

        logger.info("聚合根: $fullClassName, ID=$identityClass, Aggregate=$aggregate")

        val pattern = templateNode.pattern
        val shouldGenerate = pattern.isBlank() || pattern.toRegex().matches(fullClassName)
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
    ): MutableMap<String, String> = getEscapeContext().toMutableMap().apply {
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
        return content.lineSequence()
            .filter { it.trimStart().startsWith("@") }
            .any { line ->
                if (AGGREGATE_ROOT_ANNOTATION.containsMatchIn(line)) {
                    AGGREGATE_NAME_CAPTURE.find(line)
                        ?.groupValues
                        ?.getOrNull(1)
                        ?.takeIf { it.isNotBlank() }
                        ?.let { aggregateRootToAggregateName[className] = it }
                    true
                } else false
            }
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
            idx.takeIf { ID_ANNOTATION_REGEX.matches(line) }
        }

        return when {
            idIndices.size > 1 -> "$simpleClassName.$DEFAULT_MUL_PRI_KEY_NAME"
            idIndices.isEmpty() -> "Long"
            else -> {
                val idLineIndex = idIndices.first()
                (idLineIndex + 1 until lines.size)
                    .firstNotNullOfOrNull { i ->
                        FIELD_DECL_REGEX.matchEntire(lines[i])
                            ?.groupValues
                            ?.getOrNull(3)
                            ?.trim()
                            ?.takeIf { it.isNotBlank() }
                    } ?: "Long"
            }
        }
    }
}

