package com.only4.cap4k.plugin.codegen.misc

import java.io.BufferedWriter
import java.io.File
import java.net.URL
import java.nio.file.Files
import java.nio.file.Paths

private const val PACKAGE_SPLITTER = "."
private val cache = mutableMapOf<String, List<File>>()
private const val SRC_MAIN_KOTLIN = "src.main.kotlin."
private const val SRC_TEST_KOTLIN = "src.test.kotlin."

private val WINDOWS_ABSOLUTE_REGEX = Regex("^[A-Za-z]:[\\\\/].*")
private val HTTP_REGEX = Regex("^https?://", RegexOption.IGNORE_CASE)

/**
 * 获取所有文件清单，含子目录中的文件
 */
fun loadFiles(baseDir: String): List<File> =
    cache.getOrPut(baseDir) {
        File(baseDir).also { require(it.exists()) { "文件夹不存在: $baseDir" } }
            .let { root ->
                root.takeIf { it.isDirectory }
                    ?.walkTopDown()
                    ?.filter { it.isFile }
                    ?.toList()
                    ?: listOf(root)
            }
    }

/**
 * 加载文件内容(支持FilePath&URL)
 */
fun loadFileContent(location: String, charsetName: String): String {
    val loc = location.trim()
    return if (isHttpUri(loc)) {
        URL(loc).openStream().bufferedReader(charset(charsetName)).use { it.readText() }
    } else {
        File(loc).readText(charset(charsetName))
    }
}

/**
 * 判断是否是HTTP URI
 */
fun isHttpUri(location: String): Boolean = HTTP_REGEX.containsMatchIn(location.trimStart())

/**
 * 判断是否绝对路径(支持FilePath&URL)
 */
fun isAbsolutePathOrHttpUri(location: String): Boolean =
    location.trim().let { loc ->
        when {
            isHttpUri(loc) -> true
            File.separatorChar == '/' -> loc.startsWith('/')          // Unix / Linux / Mac
            loc.startsWith("\\\\") -> true                            // Windows UNC 路径 \\server\share
            WINDOWS_ABSOLUTE_REGEX.matches(loc) -> true               // Windows 盘符路径
            else -> false
        }
    }

/**
 * 拼接路径(支持FilePath&URL)
 */
fun concatPathOrHttpUri(path1: String, path2: String): String =
    when {
        path2.isBlank() -> path1
        isHttpUri(path1) -> buildString {
            append(path1.trimEnd('/'))
            append('/')
            append(path2.trimStart('/'))
        }

        else -> {
            val sep = File.separatorChar
            val base = path1.trimEnd(sep)
            val seg = path2.trimStart('/', '\\')
            if (seg.isEmpty()) base else "$base$sep${seg.normalizeOsSeparator()}"
        }
    }

private fun String.normalizeOsSeparator(): String =
    if (File.separatorChar == '\\') replace('/', '\\') else replace('\\', '/')

private fun String.toHttpDirectory(): String {
    if (endsWith("/")) return this
    val idx = lastIndexOf('/')
    return if (idx >= 0) substring(0, idx + 1) else ""
}

private fun String.ensureTrailingSeparator(): String =
    if (endsWith(File.separator)) this else this + File.separator

/**
 * 解析目录路径
 */
fun resolveDirectory(location: String, baseDir: String = ""): String =
    location.trim().let { loc ->
        require(loc.isNotEmpty()) { "location 不能为空" }
        if (isHttpUri(loc)) return loc.toHttpDirectory()

        val path = if (isAbsolutePathOrHttpUri(loc)) Paths.get(loc)
        else Paths.get(baseDir, loc)

        require(Files.exists(path)) { "路径不存在：$loc" }

        val dir = if (Files.isDirectory(path)) path else path.parent
        dir.toAbsolutePath().toString().ensureTrailingSeparator()
    }

/**
 * 解析Kotlin包在文件系统中��文件夹路径
 */
fun resolvePackageDirectory(baseDir: String, packageName: String): String =
    runCatching {
        require(packageName.isNotBlank()) { "packageName 不能为空" }
        val root = File(baseDir).canonicalFile
        val rel = (SRC_MAIN_KOTLIN + packageName).replace(PACKAGE_SPLITTER, File.separator)
        File(root, rel).path
    }.getOrElse { throw IllegalStateException("解析目录失败: ${it.message}", it) }

/**
 * 解析源文件路径
 */
fun resolveSourceFile(baseDir: String, packageName: String, className: String): String =
    Paths.get(
        resolvePackageDirectory(baseDir, packageName),
        "$className.kt"
    ).toString()

/**
 * 拼接包名
 */
fun concatPackage(vararg packages: String): String =
    packages.asSequence()
        .map { it.trim('.') }
        .filter { it.isNotBlank() }
        .joinToString(PACKAGE_SPLITTER)

/**
 * 解析包名
 */
fun resolvePackage(filePath: String): String {
    val className = resolveClassName(filePath)
    require(className.contains(PACKAGE_SPLITTER)) { "无法从路径解析包名: $filePath" }
    return className.substringBeforeLast(PACKAGE_SPLITTER)
}

/**
 * 从完整类名中获取包名
 *
 */
fun getPackageFromClassName(fullClassName: String): String {
    val trimmed = fullClassName.trim()
    return if (trimmed.contains(PACKAGE_SPLITTER)) {
        trimmed.substringBeforeLast(PACKAGE_SPLITTER)
    } else {
        ""
    }
}

private fun resolveClassName(filePath: String): String {
    require(filePath.endsWith(".kt")) { "文件不是Kotlin源文件" }
    val canonical = filePath.replace(File.separator, PACKAGE_SPLITTER)
        .removeSuffix(".kt")
    val idx = when {
        canonical.contains(SRC_MAIN_KOTLIN) -> canonical.lastIndexOf(SRC_MAIN_KOTLIN) + SRC_MAIN_KOTLIN.length
        canonical.contains(SRC_TEST_KOTLIN) -> canonical.lastIndexOf(SRC_TEST_KOTLIN) + SRC_TEST_KOTLIN.length
        else -> return ""
    }
    return canonical.substring(idx)
}

/**
 * 相对包名引用
 */
fun refPackage(fullPackage: String, basePackage: String): String {
    require(fullPackage.startsWith(basePackage)) { "无法计算相对包路径" }
    return fullPackage.removePrefix(basePackage)
}

/**
 * 标准化相对包名 以 '.' 开头
 *
 */
fun refPackage(refPackage: String): String =
    refPackage.takeIf { it.isNotBlank() }
        ?.let { if (it.startsWith(PACKAGE_SPLITTER)) it else "$PACKAGE_SPLITTER$it" }
        ?: ""

/**
 * 检查是否包含指定行
 */
fun hasLine(lines: List<String>, pattern: String): Boolean {
    val regex = pattern.toRegex()
    return lines.any { it.trim().matches(regex) }
}

/**
 * 添加行（如果不存在）
 */
fun addIfNone(
    lines: MutableList<String>,
    pattern: String,
    lineToAdd: String,
    indexProvider: ((List<String>, String) -> Int)? = null,
) {
    val regex = pattern.toRegex()
    if (lines.none { it.trim().matches(regex) }) {
        val index = indexProvider?.invoke(lines, lineToAdd) ?: lines.size
        lines.add(index.coerceIn(0, lines.size), lineToAdd)
    }
}

/**
 * 移除匹配的文本
 */
fun removeText(lines: MutableList<String>, pattern: String) {
    val regex = pattern.toRegex()
    lines.removeAll { it.trim().matches(regex) }
}

/**
 * 替换匹配的文本
 */
fun replaceText(lines: MutableList<String>, pattern: String, replacement: String) {
    val regex = pattern.toRegex()
    lines.indices.forEach { i ->
        if (lines[i].trim().matches(regex)) lines[i] = replacement
    }
}

/**
 * 去重文本（保持首次出现）
 */
fun distinctText(lines: MutableList<String>, pattern: String) {
    val regex = pattern.toRegex()
    val seen = mutableSetOf<String>()
    val it = lines.listIterator()
    while (it.hasNext()) {
        val v = it.next()
        if (v.trim().matches(regex)) {
            if (!seen.add(v)) it.remove()
        }
    }
}

fun resolveDefaultBasePackage(baseDir: String): String {
    val root = File(baseDir).canonicalPath + File.separator +
            SRC_MAIN_KOTLIN.replace(PACKAGE_SPLITTER, File.separator)
    val file = loadFiles(root).firstOrNull { it.isFile && it.extension == "kotlin" }
        ?: throw RuntimeException("解析默认basePackage失败")
    val packageName = resolvePackage(file.canonicalPath)
    val parts = packageName.split('.').filter { it.isNotBlank() }
    require(parts.isNotEmpty()) { "解析默认basePackage失败" }
    return parts.take(3).joinToString(PACKAGE_SPLITTER)
}

/**
 * 写入一行到缓冲写入器
 */
fun writeLine(out: BufferedWriter, line: String) {
    out.write(line)
    out.newLine()
}
