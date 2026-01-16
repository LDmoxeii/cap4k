package com.only4.cap4k.plugin.codegen.context

import com.only4.cap4k.plugin.codegen.template.TemplateNode

interface BaseContext {

    // === 基础配置 Map（包含所有项目、数据库、生成配置等） ===
    val baseMap: Map<String, Any?>

    // === 模块路径信息 ===
    val adapterPath: String
    val domainPath: String
    val applicationPath: String

    // === 类型映射 ===
    val typeMapping: MutableMap<String, String>

    // === 模板路径信息 ===
    val templateParentPath: MutableMap<String, String>

    // === 模板包名信息 ===
    val templatePackage: MutableMap<String, String>

    // === 模板信息 ===
    val templateNodeMap: MutableMap<String, MutableList<TemplateNode>>

    // === baseMap 辅助访问方法 ===
    fun getString(key: String, default: String = ""): String = baseMap[key]?.toString() ?: default
    fun getBoolean(key: String, default: Boolean = false): Boolean =
        baseMap[key]?.toString()?.toBoolean() ?: default

    fun getInt(key: String, default: Int = 0): Int =
        baseMap[key]?.toString()?.toIntOrNull() ?: default

    /**
     * 将值放入上下文 Map，支持模板别名映射
     *
     * 例如: putContext("entity", "Entity", "User")
     * 会根据 templateAliasMap 中的映射，将 "User" 同时放入多个别名 key 中
     */
    fun MutableMap<String, Any?>.putContext(tag: String, variable: String, value: Any)
}

