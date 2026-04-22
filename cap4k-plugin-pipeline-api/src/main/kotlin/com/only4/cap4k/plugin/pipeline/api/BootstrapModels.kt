package com.only4.cap4k.plugin.pipeline.api

enum class BootstrapMode {
    IN_PLACE,
    PREVIEW_SUBTREE,
}

data class BootstrapConfig(
    val preset: String,
    val projectName: String,
    val basePackage: String,
    val projectDir: String = ".",
    val modules: BootstrapModulesConfig,
    val templates: BootstrapTemplateConfig,
    val slots: List<BootstrapSlotBinding>,
    val conflictPolicy: ConflictPolicy,
    val mode: BootstrapMode,
    val previewDir: String?,
)

data class BootstrapModulesConfig(
    val domainModuleName: String,
    val applicationModuleName: String,
    val adapterModuleName: String,
    val startModuleName: String,
)

data class BootstrapTemplateConfig(
    val preset: String,
    val overrideDirs: List<String>,
)

enum class BootstrapSlotKind {
    ROOT,
    BUILD_LOGIC,
    MODULE_ROOT,
    MODULE_PACKAGE,
    MODULE_RESOURCES,
}

data class BootstrapSlotBinding(
    val kind: BootstrapSlotKind,
    val role: String? = null,
    val sourceDir: String,
) {
    val slotId: String =
        when (kind) {
            BootstrapSlotKind.ROOT -> "root"
            BootstrapSlotKind.BUILD_LOGIC -> "build-logic"
            BootstrapSlotKind.MODULE_ROOT -> "module-root:${requireNotNull(role)}"
            BootstrapSlotKind.MODULE_PACKAGE -> "module-package:${requireNotNull(role)}"
            BootstrapSlotKind.MODULE_RESOURCES -> "module-resources:${requireNotNull(role)}"
        }
}

data class BootstrapPlanItem(
    val presetId: String,
    val outputPath: String,
    val conflictPolicy: ConflictPolicy,
    val templateId: String? = null,
    val sourcePath: String? = null,
    val slotId: String? = null,
    val context: Map<String, Any?> = emptyMap(),
) {
    init {
        require(!templateId.isNullOrBlank() || !sourcePath.isNullOrBlank()) {
            "BootstrapPlanItem requires templateId or sourcePath."
        }
    }
}

data class BootstrapPlanReport(
    val items: List<BootstrapPlanItem>,
)
