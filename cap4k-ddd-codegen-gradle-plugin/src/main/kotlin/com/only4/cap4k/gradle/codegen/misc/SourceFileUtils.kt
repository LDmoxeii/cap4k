package com.only4.cap4k.gradle.codegen.misc

import java.io.BufferedWriter
import java.io.File
import java.io.IOException
import java.net.URL
import java.nio.file.Files
import java.nio.file.Paths

/**
 * 源文件工具类
 *
 * @author cap4k-codegen
 * @date 2024/12/21
 */
object SourceFileUtils {
    private const val PACKAGE_SPLITTER = "."
    private val cache = mutableMapOf<String, List<File>>()
    private const val SRC_MAIN_KOTLIN = "src/main/kotlin/"
    private const val SRC_TEST_KOTLIN = "src/test/kotlin/"

    /**
     * 获取所有文件清单，含子目录中的文件
     */
    fun loadFiles(baseDir: String): List<File> {
        if (cache.containsKey(baseDir)) {
            return cache[baseDir]!!
        }

        val file = File(baseDir)
        val list = mutableListOf<File>()
        val result = mutableListOf<File>()

        if (file.exists()) {
            val files = file.listFiles()
            if (files == null) {
                result.add(file)
                return result
            }

            list.addAll(files)
            while (list.isNotEmpty()) {
                val currentFile = list.removeAt(0)
                val subFiles = currentFile.listFiles()
                if (subFiles == null) {
                    result.add(currentFile)
                    continue
                }

                for (f in subFiles) {
                    if (f.isDirectory) {
                        list.add(f)
                    } else {
                        result.add(f)
                    }
                }
            }
        } else {
            throw RuntimeException("文件夹不存在！")
        }

        cache[baseDir] = result
        return result
    }

    /**
     * 加载文件内容(支持FilePath&URL)
     */
    fun loadFileContent(location: String, charsetName: String, baseDir: String? = null): String {
        return if (isHttpUri(location)) {
            val url = URL(location)
            url.openStream().bufferedReader(charset(charsetName)).use { it.readText() }
        } else {
            val path = if (isAbsolutePathOrHttpUri(location)) {
                location
            } else {
                // 如果是相对路径，基于baseDir解析
                val base = baseDir ?: System.getProperty("user.dir")
                concatPathOrHttpUri(base, location)
            }
            File(path).readText(charset(charsetName))
        }
    }

    /**
     * 加载资源文件内容
     */
    fun loadResourceFileContent(path: String, charsetName: String): String {
        val inputStream = SourceFileUtils::class.java.classLoader.getResourceAsStream(path)
            ?: throw RuntimeException("资源文件不存在: $path")
        return inputStream.bufferedReader(charset(charsetName)).use { it.readText() }
    }

    /**
     * 判断是否是HTTP URI
     */
    fun isHttpUri(location: String?): Boolean {
        if (location == null) return false
        val lowerCaseLocation = location.lowercase()
        return lowerCaseLocation.startsWith("http://") || lowerCaseLocation.startsWith("https://")
    }

    /**
     * 判断是否绝对路径(支持FilePath&URL)
     */
    fun isAbsolutePathOrHttpUri(location: String): Boolean {
        if (isHttpUri(location)) {
            return true
        }
        if (File.separator == "/") {
            return location.startsWith("/")
        } else {
            return location.length > 3 && location[1] == ':' && location[2] == '\\'
        }
    }

    /**
     * 拼接路径(支持FilePath&URL)
     */
    fun concatPathOrHttpUri(path1: String, path2: String): String {
        return if (isHttpUri(path1)) {
            path1 + (if (path1.endsWith("/")) "" else "/") + path2
        } else if (File.separator == "\\") {
            path1 + (if (path1.endsWith(File.separator)) "" else File.separator) + path2.replace("/", "\\")
        } else {
            path1 + (if (path1.endsWith(File.separator)) "" else File.separator) + path2
        }
    }

    /**
     * 解析目录路径
     */
    fun resolveDirectory(location: String, baseDir: String? = null): String {
        return if (isHttpUri(location)) {
            if (location.endsWith("/")) {
                location
            } else {
                location.substring(0, location.lastIndexOf("/") + 1)
            }
        } else {
            val path = if (isAbsolutePathOrHttpUri(location)) {
                Paths.get(location)
            } else {
                // 如果是相对路径，基于baseDir解析
                val base = baseDir ?: System.getProperty("user.dir")
                Paths.get(base, location)
            }

            if (!Files.exists(path)) {
                throw RuntimeException("路径不存在：$location")
            }
            if (Files.isDirectory(path)) {
                path.toAbsolutePath().toString() + File.separator
            } else {
                path.parent.toAbsolutePath().toString() + File.separator
            }
        }
    }

    /**
     * 解析Java/Kotlin包在文件系统中的文件夹路径
     */
    fun resolvePackageDirectory(baseDir: String, packageName: String): String {
        return try {
            File(baseDir).canonicalPath + File.separator +
                    (SRC_MAIN_KOTLIN + packageName).replace(PACKAGE_SPLITTER, File.separator)
        } catch (e: IOException) {
            throw RuntimeException("解析目录失败", e)
        }
    }

    /**
     * 解析源文件路径
     */
    fun resolveSourceFile(baseDir: String, packageName: String, className: String): String {
        val packageDir = resolvePackageDirectory(baseDir, packageName)
        return "$packageDir${File.separator}$className.kt"
    }

    /**
     * 拼接包名
     */
    fun concatPackage(vararg packages: String?): String {
        return packages
            .filterNotNull()
            .filter { it.isNotBlank() }
            .joinToString(PACKAGE_SPLITTER)
    }

    /**
     * 解析包名
     */
    fun resolvePackage(filePath: String): String {
        val file = File(filePath)
        val content = file.readText()
        val packageRegex = Regex("^\\s*package\\s+([\\w\\.]+)", RegexOption.MULTILINE)
        val match = packageRegex.find(content)
        return match?.groupValues?.get(1) ?: ""
    }

    /**
     * 相对包名引用
     */
    fun refPackage(packageName: String, basePackage: String? = null): String {
        if (basePackage.isNullOrBlank()) return packageName
        if (packageName.startsWith(basePackage)) {
            val relativePart = packageName.substring(basePackage.length)
            return if (relativePart.startsWith(".")) relativePart.substring(1) else relativePart
        }
        return packageName
    }

    /**
     * 检查是否包含指定行
     */
    fun hasLine(lines: List<String>, pattern: String): Boolean {
        val regex = Regex(pattern)
        return lines.any { regex.matches(it.trim()) }
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
        if (!hasLine(lines, pattern)) {
            val index = indexProvider?.invoke(lines, lineToAdd) ?: lines.size
            lines.add(index, lineToAdd)
        }
    }

    /**
     * 移除匹配的文本
     */
    fun removeText(lines: MutableList<String>, pattern: String) {
        val regex = Regex(pattern)
        lines.removeAll { regex.matches(it.trim()) }
    }

    /**
     * 替换匹配的文本
     */
    fun replaceText(lines: MutableList<String>, pattern: String, replacement: String) {
        val regex = Regex(pattern)
        for (i in lines.indices) {
            if (regex.matches(lines[i].trim())) {
                lines[i] = replacement
            }
        }
    }

    /**
     * 去重文本
     */
    fun distinctText(lines: MutableList<String>, pattern: String) {
        val regex = Regex(pattern)
        val seen = mutableSetOf<String>()
        val iterator = lines.iterator()

        while (iterator.hasNext()) {
            val line = iterator.next()
            if (regex.matches(line.trim())) {
                if (line in seen) {
                    iterator.remove()
                } else {
                    seen.add(line)
                }
            }
        }
    }

    fun resolveDefaultBasePackage(baseDir: String): String {
        val file = loadFiles(
            File(baseDir).canonicalPath + File.separator + SRC_MAIN_KOTLIN.replace(
                PACKAGE_SPLITTER,
                File.separator
            )
        ).firstOrNull { it.isFile && it.extension == "kt" }

        file ?: throw RuntimeException("解析默认basePackage失败")

        val packageName = resolvePackage(file.canonicalPath)
        val packages = packageName.split("\\.")
        return when (packages.size) {
            0 -> throw RuntimeException("解析默认basePackage失败")
            1 -> packages[0]
            2 -> packages[0] + PACKAGE_SPLITTER + packages[1]
            else -> {
                packages[0] + PACKAGE_SPLITTER + packages[1] + PACKAGE_SPLITTER + packages[2]
            }
        }

    }

    /**
     * 写入一行到缓冲写入器
     */
    fun writeLine(out: BufferedWriter, line: String) {
        try {
            out.write(line)
            out.newLine()
        } catch (e: IOException) {
            e.printStackTrace()
        }
    }
}
