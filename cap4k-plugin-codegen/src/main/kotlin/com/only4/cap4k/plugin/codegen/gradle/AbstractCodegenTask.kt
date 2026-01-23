package com.only4.cap4k.plugin.codegen.gradle

import com.only4.cap4k.plugin.codegen.context.BaseContext
import com.only4.cap4k.plugin.codegen.core.*
import com.only4.cap4k.plugin.codegen.gradle.extension.CodegenExtension
import com.only4.cap4k.plugin.codegen.misc.resolvePackage
import com.only4.cap4k.plugin.codegen.template.PathNode
import com.only4.cap4k.plugin.codegen.template.Template
import com.only4.cap4k.plugin.codegen.template.TemplateNode
import org.gradle.api.DefaultTask
import org.gradle.api.provider.Property
import org.gradle.api.tasks.Input
import org.gradle.api.tasks.Internal
import java.io.File
import java.nio.charset.Charset
import java.time.LocalDateTime
import java.time.format.DateTimeFormatter

/**
 * 代码生成任务抽象基类
 *
 * @author cap4k-codegen
 * @date 2024/12/21
 */
abstract class AbstractCodegenTask : DefaultTask(), BaseContext {

    init {
        group = "cap4k"
    }

    companion object {
        const val FLAG_DO_NOT_OVERWRITE = "[cap4k-ddd-codegen-gradle-plugin:do-not-overwrite]"
        const val PATTERN_SPLITTER = "[,;]"
        const val PATTERN_LINE_BREAK = "\\r\\n|[\\r\\n]"
        const val DEFAULT_MUL_PRI_KEY_NAME = "Key"
    }

    @get:Input
    abstract val extension: Property<CodegenExtension>

    @get:Input
    abstract val projectName: Property<String>

    @get:Input
    abstract val projectGroup: Property<String>

    @get:Input
    abstract val projectVersion: Property<String>

    @get:Input
    abstract val projectDir: Property<String>

    @Internal
    protected var template: Template? = null

    @Internal
    protected var renderFileSwitch = true

    @Internal
    protected open val aliasRegistry = AliasRegistry()

    @get:Internal
    protected open val logAdapter: LoggerAdapter by lazy { GradleLoggerAdapter(logger) }

    @get:Internal
    protected open val fileWriter: FileWriter by lazy { DefaultFileWriter(logAdapter) }

    private val CodegenExtension.adapterPath: String
        get() = modulePath(moduleNameSuffix4Adapter.get())

    private val CodegenExtension.applicationPath: String
        get() = modulePath(moduleNameSuffix4Application.get())

    private val CodegenExtension.domainPath: String
        get() = modulePath(moduleNameSuffix4Domain.get())

    private fun CodegenExtension.modulePath(suffix: String): String =
        if (multiModule.get()) {
            "${projectDir.get()}${File.separator}${projectName.get()}$suffix"
        } else {
            projectDir.get()
        }

    @get:Internal
    override val baseMap: Map<String, Any?> by lazy {
        buildMap {
            val ext = extension.get()

            // 项目信息
            put("artifactId", projectName.get())
            put("groupId", projectGroup.get())
            put("version", projectVersion.get())

            // 基础配置
            put("archTemplate", ext.archTemplate.get())
            put("archTemplateEncoding", ext.archTemplateEncoding.get())
            put("outputEncoding", ext.outputEncoding.get())
            put("designFiles", ext.designFiles.files)
            put("basePackage", ext.basePackage.get())
            put("basePackage__as_path", ext.basePackage.get().replace(".", File.separator))
            put("multiModule", ext.multiModule.get())

            // 模块路径
            put("adapterModulePath", ext.adapterPath)
            put("applicationModulePath", ext.applicationPath)
            put("domainModulePath", ext.domainPath)

            // 数据库配置
            with(ext.database) {
                put("dbUrl", url.get())
                put("dbUsername", username.get())
                put("dbPassword", password.get())
                put("dbSchema", schema.get())
                put("dbTables", tables.get())
                put("dbIgnoreTables", ignoreTables.get())
            }

            // 生成配置
            with(ext.generation) {
                put("versionField", versionField.get())
                put("deletedField", deletedField.get())
                put("readonlyFields", readonlyFields.get())
                put("ignoreFields", ignoreFields.get())
                put("entityBaseClass", entityBaseClass.get())
                put("rootEntityBaseClass", rootEntityBaseClass.get())
                put("entityClassExtraImports", entityClassExtraImports.get())
                put("entitySchemaOutputPackage", entitySchemaOutputPackage.get())
                put("entitySchemaOutputMode", entitySchemaOutputMode.get())
                put("entitySchemaNameTemplate", entitySchemaNameTemplate.get())
                put("aggregateTypeTemplate", aggregateTypeTemplate.get())
                put("repositoryNameTemplate", repositoryNameTemplate.get())
                put("idGenerator", idGenerator.get())
                put("idGenerator4ValueObject", idGenerator4ValueObject.get())
                put("hashMethod4ValueObject", hashMethod4ValueObject.get())
                put("fetchType", fetchType.get())
                put("enumValueField", enumValueField.get())
                put("enumNameField", enumNameField.get())
                put("enumUnmatchedThrowException", enumUnmatchedThrowException.get())
                put("datePackage", datePackage.get())
                put("generateDbType", generateDbType.get())
                put("generateSchema", generateSchema.get())
                put("generateAggregate", generateAggregate.get())
                put("generateParent", generateParent.get())
                put("repositorySupportQuerydsl", repositorySupportQuerydsl.get())
            }

            // 其他配置
            put("date", LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyy/MM/dd")))
            put("SEPARATOR", File.separator)
            put("separator", File.separator)

        }.toMap()
    }

    @get:Internal
    override val adapterPath: String by lazy { extension.get().adapterPath }

    @get:Internal
    override val applicationPath: String by lazy { extension.get().applicationPath }

    @get:Internal
    override val domainPath: String by lazy { extension.get().domainPath }

    @get:Internal
    override val typeMapping: MutableMap<String, String> by lazy {
        extension.get().generation.typeMapping.get().toMutableMap()
    }

    @Internal
    override val templateParentPath: MutableMap<String, String> = mutableMapOf()

    @Internal
    override val templatePackage: MutableMap<String, String> = mutableMapOf()

    @Internal
    override val templateNodeMap = mutableMapOf<String, MutableList<TemplateNode>>()


    override fun MutableMap<String, Any?>.putContext(tag: String, variable: String, value: Any) =
        aliasRegistry.resolve(tag, variable).forEach { alias ->
            this[alias] = value
        }

    protected fun forceRender(pathNode: PathNode, parentPath: String): String =
        renderFileSwitch.let { originalValue ->
            renderFileSwitch = true
            try {
                render(pathNode, parentPath)
            } finally {
                renderFileSwitch = originalValue
            }
        }

    protected fun render(pathNode: PathNode, parentPath: String): String =
        when (pathNode.type?.lowercase()) {
            "root" -> {
                pathNode.children?.forEach { render(it, parentPath) }
                parentPath
            }

            "dir" -> {
                val dirPath = renderDir(pathNode, parentPath)
                pathNode.children?.forEach { render(it, dirPath) }
                dirPath
            }

            "file" -> renderFile(pathNode, parentPath)
            else -> parentPath
        }

    protected open fun renderTemplate(
        templateNodes: List<TemplateNode>,
        parentPath: String,
    ) {
        templateNodes.forEach { templateNode ->
            templateParentPath[templateNode.tag!!] = parentPath
        }
    }

    private fun renderDir(pathNode: PathNode, parentPath: String): String {
        require(pathNode.type.equals("dir", ignoreCase = true)) { "pathNode must be a directory type" }

        val name = pathNode.name?.takeIf { it.isNotBlank() } ?: return parentPath
        val path = "$parentPath${File.separator}$name"
        fileWriter.ensureDirectory(path, pathNode.conflict)

        pathNode.tag?.takeIf { it.isNotBlank() }?.let { tag ->
            tag.split(Regex(PATTERN_SPLITTER))
                .filter { it.isNotBlank() }
                .forEach { renderTemplate(template!!.select(it), path) }
        }

        return path
    }

    protected fun renderFile(pathNode: PathNode, parentPath: String): String {
        require(pathNode.type.equals("file", ignoreCase = true)) { "pathNode must be a file type" }

        val name = pathNode.name?.takeIf { it.isNotBlank() }
            ?: error("pathNode name must not be blank")

        val path = "$parentPath${File.separator}$name"
        if (!renderFileSwitch) return path

        val content = pathNode.data.orEmpty()
        val encoding = pathNode.encoding ?: extension.get().outputEncoding.get()
        val charset = Charset.forName(encoding)

        fileWriter.writeFile(path, content, charset, pathNode.conflict, FLAG_DO_NOT_OVERWRITE)
        return path
    }

}
