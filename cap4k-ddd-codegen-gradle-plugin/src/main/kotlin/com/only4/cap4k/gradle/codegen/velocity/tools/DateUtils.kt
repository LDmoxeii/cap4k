package com.only4.cap4k.gradle.codegen.velocity.tools

import java.time.LocalDateTime
import java.time.format.DateTimeFormatter

/**
 * 日期工具类(供 Velocity 模板使用)
 *
 * @author cap4k-codegen
 * @date 2024/12/21
 */
object DateUtils {

    /**
     * 当前日期 (yyyy-MM-dd)
     */
    @JvmStatic
    fun now(): String {
        return LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyy-MM-dd"))
    }

    /**
     * 当前日期时间 (yyyy-MM-dd HH:mm:ss)
     */
    @JvmStatic
    fun datetime(): String {
        return LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss"))
    }

    /**
     * 当前年份
     */
    @JvmStatic
    fun year(): String {
        return LocalDateTime.now().year.toString()
    }
}
