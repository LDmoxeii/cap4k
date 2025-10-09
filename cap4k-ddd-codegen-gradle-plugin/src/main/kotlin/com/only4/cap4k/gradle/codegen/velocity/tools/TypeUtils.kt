package com.only4.cap4k.gradle.codegen.velocity.tools

/**
 * 类型判断工具类(供 Velocity 模板使用)
 *
 * @author cap4k-codegen
 * @date 2024/12/21
 */
object TypeUtils {

    private val STRING_TYPES = setOf("String", "string", "VARCHAR", "TEXT", "CHAR")
    private val NUMBER_TYPES =
        setOf("Integer", "Long", "Double", "Float", "BigDecimal", "int", "long", "double", "float")
    private val DATE_TYPES = setOf("Date", "LocalDate", "LocalDateTime", "Timestamp", "TIMESTAMP", "DATETIME")
    private val BOOLEAN_TYPES = setOf("Boolean", "boolean", "BIT", "TINYINT")

    @JvmStatic
    fun isString(type: String): Boolean = type in STRING_TYPES

    @JvmStatic
    fun isNumber(type: String): Boolean = type in NUMBER_TYPES

    @JvmStatic
    fun isDate(type: String): Boolean = type in DATE_TYPES

    @JvmStatic
    fun isBoolean(type: String): Boolean = type in BOOLEAN_TYPES
}
