package com.only4.cap4k.gradle.codegen.misc

// HTML 转义映射
private val HTML_ESCAPE_MAP = mapOf(
    '&' to "&amp;",
    '<' to "&lt;",
    '>' to "&gt;",
    '"' to "&quot;",
    '\'' to "&#39;"
)

fun String.splitWithTrim(
    delimiter: String,
    limit: Int? = null,
    filterEmpty: Boolean = limit == null
): Array<String> {
    val regex = Regex(delimiter)
    val parts = if (limit != null) this.split(regex, limit) else this.split(regex)
    return parts.asSequence()
        .map { it.trim() }
        .let { seq -> if (filterEmpty) seq.filter { it.isNotEmpty() } else seq }
        .toList()
        .toTypedArray()
}

/**
 * 转义HTML字符（高效单次遍历）
 */
fun escapeHtml(text: String): String {
    if (text.none { it in HTML_ESCAPE_MAP }) return text
    val sb = StringBuilder(text.length + 16)
    for (c in text) {
        sb.append(HTML_ESCAPE_MAP[c] ?: c)
    }
    return sb.toString()
}
