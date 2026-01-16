package com.only4.cap4k.plugin.codegen.core

import org.gradle.api.logging.Logger
import java.io.File
import java.nio.charset.Charset

interface LoggerAdapter {
    fun info(message: String)
    fun warn(message: String)
}

class GradleLoggerAdapter(private val logger: Logger) : LoggerAdapter {
    override fun info(message: String) = logger.info(message)
    override fun warn(message: String) = logger.warn(message)
}

interface FileWriter {
    fun ensureDirectory(path: String, conflict: String)
    fun writeFile(path: String, content: String, charset: Charset, conflict: String, protectFlag: String? = null)
}

class DefaultFileWriter(
    private val log: LoggerAdapter,
) : FileWriter {

    override fun ensureDirectory(path: String, conflict: String) {
        val dirFile = File(path)
        when {
            !dirFile.exists() -> {
                dirFile.mkdirs()
                log.info("创建目录: $path")
            }

            else -> when (conflict.lowercase()) {
                "skip" -> log.info("目录已存在，跳过: $path")
                "warn" -> log.warn("目录已存在，继续: $path")
                "overwrite" -> {
                    log.info("目录覆盖: $path")
                    dirFile.deleteRecursively()
                    dirFile.mkdirs()
                }
            }
        }
    }

    override fun writeFile(path: String, content: String, charset: Charset, conflict: String, protectFlag: String?) {
        val file = File(path)
        when {
            !file.exists() -> {
                file.parentFile?.mkdirs()
                file.writeText(content, charset)
                log.info("创建文件: $path")
            }

            else -> when (conflict.lowercase()) {
                "skip" -> log.info("文件已存在，跳过: $path")
                "warn" -> log.warn("文件已存在，继续: $path")
                "overwrite" -> {
                    if (!protectFlag.isNullOrBlank() && file.readText(charset).contains(protectFlag)) {
                        log.warn("文件已存在且包含保护标记，跳过: $path")
                    } else {
                        log.info("文件覆盖: $path")
                        file.writeText(content, charset)
                    }
                }
            }
        }
    }
}
