package com.only4.cap4k.gradle.codegen.misc

/**
 * 文本工具类
 *
 * @author cap4k-codegen
 * @date 2024/12/21
 */
object TextUtils {

    /**
     * 使用指定分隔符分割字符串并去除空白
     */
    fun splitWithTrim(text: String, delimiter: String): Array<String> {
        return text.split(delimiter)
            .map { it.trim() }
            .filter { it.isNotEmpty() }
            .toTypedArray()
    }

    /**
     * 转义HTML字符
     */
    fun escapeHtml(text: String): String {
        return text
            .replace("&", "&amp;")
            .replace("<", "&lt;")
            .replace(">", "&gt;")
            .replace("\"", "&quot;")
            .replace("'", "&#39;")
    }

    /**
     * 检查字符串是否为空或仅包含空白字符
     */
    fun isBlank(text: String?): Boolean {
        return text.isNullOrBlank()
    }

    /**
     * 检查字符串是否不为空且不仅包含空白字符
     */
    fun isNotBlank(text: String?): Boolean {
        return !text.isNullOrBlank()
    }

    /**
     * 首字母大写
     */
    fun capitalize(text: String): String {
        return if (text.isEmpty()) {
            text
        } else {
            text.substring(0, 1).uppercase() + text.substring(1)
        }
    }

    /**
     * 首字母小写
     */
    fun uncapitalize(text: String): String {
        return if (text.isEmpty()) {
            text
        } else {
            text.substring(0, 1).lowercase() + text.substring(1)
        }
    }

    /**
     * 移除字符串前后的指定字符
     */
    fun strip(text: String, chars: String = " \t\n\r"): String {
        return text.trim { it in chars }
    }

    /**
     * 重复字符串
     */
    fun repeat(text: String, times: Int): String {
        return text.repeat(times)
    }

    /**
     * 左填充字符串到指定长度
     */
    fun leftPad(text: String, length: Int, padChar: Char = ' '): String {
        return if (text.length >= length) {
            text
        } else {
            padChar.toString().repeat(length - text.length) + text
        }
    }

    /**
     * 右填充字符串到指定长度
     */
    fun rightPad(text: String, length: Int, padChar: Char = ' '): String {
        return if (text.length >= length) {
            text
        } else {
            text + padChar.toString().repeat(length - text.length)
        }
    }
}