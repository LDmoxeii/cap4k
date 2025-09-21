package com.only4.cap4k.gradle.codegen.misc

/**
 * 命名工具类
 *
 * @author cap4k-codegen
 * @date 2024/12/21
 */
object NamingUtils {

    /**
     * 下划线转小驼峰
     * user_name  ---->  userName
     * userName   --->  userName
     *
     * @param someCase 带有下划线的字符串
     * @return 驼峰字符串
     */
    fun toLowerCamelCase(someCase: String?): String? {
        if (someCase == null) {
            return null
        }
        val camel = toUpperCamelCase(someCase)
        return camel?.let {
            it.substring(0, 1).lowercase() + it.substring(1)
        }
    }

    /**
     * 下划线转大驼峰
     * user_name  ---->  UserName
     * userName   --->  UserName
     *
     * @param someCase 带有下划线的字符串
     * @return 驼峰字符串
     */
    fun toUpperCamelCase(someCase: String?): String? {
        if (someCase == null) {
            return null
        }
        return someCase.split(Regex("(?=[A-Z])|[^a-zA-Z0-9]"))
            .map { it.replace(Regex("[^a-zA-Z0-9]"), "") }
            .filter { it.isNotBlank() }
            .joinToString("") {
                it.substring(0, 1).uppercase() + it.substring(1).lowercase()
            }
    }

    /**
     * 转蛇形风格命名(snake_case)
     *
     * @param someCase
     * @return
     */
    fun toSnakeCase(someCase: String?): String? {
        if (someCase == null) {
            return null
        }
        return someCase.split(Regex("(?=[A-Z])|[^a-zA-Z0-9_]"))
            .filter { it.isNotBlank() }
            .joinToString("_") { it.lowercase() }
    }

    /**
     * 转土耳其烤肉风格命名(kebab-case)
     *
     * @param someCase
     * @return
     */
    fun toKebabCase(someCase: String?): String? {
        if (someCase == null) {
            return someCase
        }
        return someCase.split(Regex("(?=[A-Z])|[^a-zA-Z0-9\\-]"))
            .filter { it.isNotBlank() }
            .joinToString("-") { it.lowercase() }
    }

    /**
     * 获取末位包名
     *
     * @param packageName
     * @return
     */
    fun getLastPackageName(packageName: String?): String {
        if (packageName.isNullOrEmpty()) {
            return ""
        }
        if (!packageName.contains(".")) {
            return packageName
        }
        return packageName.substring(packageName.lastIndexOf(".") + 1)
    }

    /**
     * 获取父包名
     *
     * @param packageName
     * @return
     */
    fun parentPackageName(packageName: String?): String {
        if (packageName.isNullOrEmpty()) {
            return ""
        }
        val lastPackageName = getLastPackageName(packageName)
        if (packageName.length == lastPackageName.length) {
            return ""
        }
        return packageName.substring(0, packageName.length - lastPackageName.length - 1)
    }
}