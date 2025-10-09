package com.only4.cap4k.gradle.codegen.velocity.tools

/**
 * 字符串工具类(供 Velocity 模板使用)
 *
 * @author cap4k-codegen
 * @date 2024/12/21
 */
object StringUtils {

    /**
     * 首字母大写
     */
    @JvmStatic
    fun capitalize(str: String): String {
        if (str.isEmpty()) return str
        return str.replaceFirstChar { it.uppercase() }
    }

    /**
     * 首字母小写
     */
    @JvmStatic
    fun uncapitalize(str: String): String {
        if (str.isEmpty()) return str
        return str.replaceFirstChar { it.lowercase() }
    }

    /**
     * 驼峰转下划线
     * userName → user_name
     */
    @JvmStatic
    fun camelToSnake(str: String): String {
        return str.replace(Regex("([a-z])([A-Z])"), "$1_$2").lowercase()
    }

    /**
     * 下划线转驼峰
     * user_name → userName
     */
    @JvmStatic
    fun snakeToCamel(str: String): String {
        return str.split('_')
            .mapIndexed { index, part ->
                if (index == 0) part else capitalize(part)
            }
            .joinToString("")
    }
}
