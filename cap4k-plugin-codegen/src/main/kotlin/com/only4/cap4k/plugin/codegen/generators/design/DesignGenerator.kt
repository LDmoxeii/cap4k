package com.only4.cap4k.plugin.codegen.generators.design

import com.only4.cap4k.plugin.codegen.context.design.DesignContext
import com.only4.cap4k.plugin.codegen.template.TemplateNode

/**
 * 设计模板生成器接口
 *
 * 所有设计元素生成器必须实现此接口
 */
interface DesignGenerator {
    /**
     * 生成器标签 (用于匹配模板节点)
     */
    val tag: String

    /**
     * 生成顺序 (数字越小越先执行)
     */
    val order: Int

    /**
     * 判断是否应该为此设计生成代码
     */
    context(ctx: DesignContext)
    fun shouldGenerate(design: Any): Boolean

    /**
     * 构建模板上下文
     *
     * @param design 设计对象 (CommandDesign, QueryDesign, ...)
     * @param context 设计上下文
     * @return 模板变量 Map
     */
    context(ctx: DesignContext)
    fun buildContext(design: Any): Map<String, Any?>

    /**
     * 获取生成器完全限定名
     * 例如: "com.example.application.commands.category.CreateCategoryCmd"
     */
    fun generatorFullName(): String

    /**
     * 获取生成器简单名称
     * 例如: "CreateCategoryCmd"
     */
    fun generatorName(): String

    /**
     * 获取默认模板节点列表
     *
     * 一个设计可能需要生成多个文件，例如：
     * - Command 可能需要生成 Cmd.kt + CmdRequest.kt + CmdResponse.kt
     * - Entity 可能需要生成 Entity.kt + EntityRepository.kt + EntityMapper.kt
     *
     * 使用方可以通过 TemplateNode.pattern 属性来过滤需要生成的模板
     *
     * @return 模板节点列表
     */
    fun getDefaultTemplateNodes(): List<TemplateNode>

    /**
     * 生成完成后的回调
     * 可用于缓存生成的类型到 typeMapping
     */
    context(ctx: DesignContext)
    fun onGenerated(design: Any) {}
}
