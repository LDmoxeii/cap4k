package com.only4.cap4k.plugin.codegen.gradle

import com.only4.cap4k.plugin.codegen.context.design.DesignContext
import com.only4.cap4k.plugin.codegen.context.design.MutableDesignContext
import com.only4.cap4k.plugin.codegen.context.design.builders.DesignContextBuilder
import com.only4.cap4k.plugin.codegen.context.design.builders.KspMetadataContextBuilder
import com.only4.cap4k.plugin.codegen.context.design.builders.TypeMappingBuilder
import com.only4.cap4k.plugin.codegen.context.design.builders.UnifiedDesignBuilder
import com.only4.cap4k.plugin.codegen.context.design.models.AggregateInfo
import com.only4.cap4k.plugin.codegen.context.design.models.BaseDesign
import com.only4.cap4k.plugin.codegen.context.design.models.DesignElement
import com.only4.cap4k.plugin.codegen.core.TagAliasResolver
import com.only4.cap4k.plugin.codegen.generators.design.*
import com.only4.cap4k.plugin.codegen.misc.concatPackage
import com.only4.cap4k.plugin.codegen.misc.resolvePackageDirectory
import com.only4.cap4k.plugin.codegen.template.TemplateNode
import org.gradle.api.tasks.Internal
import org.gradle.api.tasks.TaskAction
import java.io.File

open class GenDesignTask : GenArchTask(), MutableDesignContext {

    @Internal
    override val designElementMap = mutableMapOf<String, MutableList<DesignElement>>()

    @Internal
    override val aggregateMap: MutableMap<String, AggregateInfo> = mutableMapOf()

    @Internal
    override val designMap = mutableMapOf<String, MutableList<BaseDesign>>()

    override fun renderTemplate(templateNodes: List<TemplateNode>, parentPath: String) {
        super.renderTemplate(templateNodes, parentPath)
        templateNodes.forEach { templateNode ->
            val tag = templateNode.tag?.let { TagAliasResolver.normalizeDesignTag(it) } ?: return@forEach
            templateNodeMap.computeIfAbsent(tag) { mutableListOf() }.add(templateNode)
        }
    }

    @TaskAction
    override fun generate() {
        renderFileSwitch = false
        super.generate()

        genDesign()
    }

    private fun genDesign() {
        val metadataPath = resolveMetadataPath()

        if (!metadataPath.exists()) {
            return
        }

        val context = buildDesignContext(metadataPath.absolutePath)

        val totalDesigns = context.designMap.values.sumOf { it.size }

        if (totalDesigns == 0) {
            return
        }

        generateDesignFiles(context)
    }

    private fun resolveMetadataPath(): File {
        val domainModulePath = File(getString("domainModulePath"))
        val domainKspPath = File(domainModulePath, "build/generated/ksp/main/resources/metadata")
        return domainKspPath
    }

    private fun buildDesignContext(metadataPath: String): DesignContext {
        val builders = listOf(
            DesignContextBuilder(),                  // order=10  - 读取 JSON 设计文件
            KspMetadataContextBuilder(metadataPath), // order=15  - 读取 KSP 聚合元信息
            TypeMappingBuilder(),                    // order=18  - 构建类型映射 typeMapping
            UnifiedDesignBuilder()                   // order=20  - 统一设计元素分发
        )

        builders.sortedBy { it.order }.forEach { builder ->
            builder.build(this)
        }

        return this
    }

    private fun generateDesignFiles(context: DesignContext) {
        val generators = listOf(
            CommandGenerator(),             // order=10 - 生成命令
            QueryGenerator(),               // order=10 - 生成查询
            ClientGenerator(),              // order=10 - 生成分布式客户端
            DomainEventGenerator(),         // order=10 - 生成领域事件
            ValidatorGenerator(),           // order=10 - 生成校验器
            ApiPayloadGenerator(),          // order=10 - 生成 API 请求负载
            DomainEventHandlerGenerator(),  // order=20 - 生成领域事件处理器
            QueryHandlerGenerator(),        // order=20 - 生成查询处理器
            ClientHandlerGenerator(),       // order=20 - 生成客户端处理器
        )

        generators.sortedBy { it.order }.forEach { generator ->
            generateForDesigns(generator, context)
        }
    }

    private fun generateForDesigns(
        generator: DesignTemplateGenerator,
        context: DesignContext
    ) {
        val designs = context.designMap[generator.tag]?.toMutableList() ?: mutableListOf()

        with(context) {
            while (designs.isNotEmpty()) {
                val design = designs.first()

                if (!generator.shouldGenerate(design)) {
                    designs.removeFirst()
                    continue
                }

                val templateContext = generator.buildContext(design).toMutableMap()

                // 合并模板节点（上下文与默认合并多份，再按 pattern 选择）
                // - 同一 dir/file 类型节点可共存；每个唯一 (name+pattern) 保留一个模板节点
                // - context 优先级高于 defaults，子目录与文件名层级都按优先级覆盖
                val genName = generator.generatorName(design)

                val ctxTop = context.templateNodeMap.getOrDefault(generator.tag, emptyList())
                val defTop = generator.getDefaultTemplateNodes()

                val selected = TemplateNode.mergeAndSelect(ctxTop, defTop, genName)

                selected.forEach { templateNode ->
                    val pathNode = templateNode.resolve(templateContext)
                    forceRender(
                        pathNode, resolvePackageDirectory(
                            templateContext["modulePath"].toString(),
                            concatPackage(
                                getString("basePackage"),
                                templateContext["templatePackage"].toString(),
                                templateContext["package"].toString()
                            )
                        )
                    )
                }

                generator.onGenerated(design)
            }
        }
    }
}
