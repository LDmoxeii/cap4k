package com.only4.cap4k.gradle.codegen.misc


// 分词正则：
// 1) (?<=[a-z0-9])(?=[A-Z]) 处理 userName -> user | Name
// 2) [^A-Za-z0-9]+ 处理下划线、横线等分隔符
private val SPLIT_REGEX = Regex("(?<=[a-z0-9])(?=[A-Z])|[^A-Za-z0-9]+")

private fun tokenize(value: String): List<String> =
    value.trim()
        .split(SPLIT_REGEX)
        .filter { it.isNotEmpty() }

/**
 * 下划线 / 混合命名 转 小驼峰
 * user_name -> userName, userName -> userName
 */
fun toLowerCamelCase(someCase: String?): String? =
    someCase?.let {
        val parts = tokenize(it)
        if (parts.isEmpty()) return null
        val head = parts.first().lowercase()
        val tail = parts.drop(1).joinToString("") { p -> p.lowercase().replaceFirstChar { c -> c.titlecase() } }
        head + tail
    }

/**
 * 下划线 / 混合命名 转 大驼峰
 * user_name -> UserName, userName -> UserName
 */
fun toUpperCamelCase(someCase: String?): String? =
    someCase?.let {
        val parts = tokenize(it)
        if (parts.isEmpty()) return null
        parts.joinToString("") { p -> p.lowercase().replaceFirstChar { c -> c.titlecase() } }
    }

/**
 * 转 snake_case
 */
fun toSnakeCase(someCase: String?): String? =
    someCase?.let {
        val parts = tokenize(it)
        if (parts.isEmpty()) return null
        parts.joinToString("_") { p -> p.lowercase() }
    }

/**
 * 转 kebab-case
 */
fun toKebabCase(someCase: String?): String? =
    someCase?.let {
        val parts = tokenize(it)
        if (parts.isEmpty()) return null
        parts.joinToString("-") { p -> p.lowercase() }
    }
