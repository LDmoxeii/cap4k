package com.only4.cap4k.plugin.pipeline.generator.design

import com.only4.cap4k.plugin.pipeline.api.ProjectConfig
import java.nio.file.InvalidPathException
import java.nio.file.Path

internal fun requireRelativeModuleRoot(config: ProjectConfig, role: String): String {
    val moduleRoot = config.modules[role] ?: error("$role module is required")
    if (moduleRoot.isBlank()) {
        throw IllegalArgumentException("$role module must be a valid relative filesystem path: $moduleRoot")
    }
    if (moduleRoot.startsWith(":")) {
        throw IllegalArgumentException("$role module must be a valid relative filesystem path: $moduleRoot")
    }

    val path = try {
        Path.of(moduleRoot)
    } catch (ex: InvalidPathException) {
        throw IllegalArgumentException("$role module must be a valid relative filesystem path: $moduleRoot", ex)
    }

    if (path.isAbsolute || path.root != null) {
        throw IllegalArgumentException("$role module must be a valid relative filesystem path: $moduleRoot")
    }

    val normalized = path.normalize()
    if (normalized.nameCount > 0 && normalized.getName(0).toString() == "..") {
        throw IllegalArgumentException("$role module must be a valid relative filesystem path: $moduleRoot")
    }

    return moduleRoot
}
