package com.only4.cap4k.plugin.pipeline.api

import java.nio.file.InvalidPathException
import java.nio.file.Path

class ArtifactLayoutResolver(
    private val basePackage: String,
    private val artifactLayout: ArtifactLayoutConfig = ArtifactLayoutConfig(),
) {

    init {
        validatePackageLayouts()
        validateOutputRoots()
    }

    fun aggregateEntityPackage(entityPackage: String): String =
        packageFromLayout(artifactLayout.aggregate, entityPackage)

    fun aggregateSchemaPackage(entityPackage: String): String =
        packageFromLayout(artifactLayout.aggregateSchema, entityPackage)

    fun aggregateSchemaBasePackage(): String =
        joinPackage(basePackage, artifactLayout.aggregateSchema.packageRoot)

    fun aggregateRepositoryPackage(): String =
        packageFromLayout(artifactLayout.aggregateRepository, "")

    fun aggregateSharedEnumPackage(entityPackage: String): String =
        packageFromLayout(artifactLayout.aggregateSharedEnum, entityPackage)

    fun aggregateEnumTranslationPackage(entityPackage: String): String =
        packageFromLayout(artifactLayout.aggregateEnumTranslation, entityPackage)

    fun aggregateUniqueQueryPackage(entityPackage: String): String =
        packageFromLayout(artifactLayout.aggregateUniqueQuery, entityPackage)

    fun aggregateUniqueQueryHandlerPackage(entityPackage: String): String =
        packageFromLayout(artifactLayout.aggregateUniqueQueryHandler, entityPackage)

    fun aggregateUniqueValidatorPackage(entityPackage: String): String =
        packageFromLayout(artifactLayout.aggregateUniqueValidator, entityPackage)

    fun aggregateWrapperPackage(entityPackage: String): String =
        entityPackage

    fun aggregateFactoryPackage(entityPackage: String): String =
        joinPackage(entityPackage, "factory")

    fun aggregateSpecificationPackage(entityPackage: String): String =
        joinPackage(entityPackage, "specification")

    fun aggregateLocalEnumPackage(entityPackage: String): String =
        joinPackage(entityPackage, "enums")

    fun designCommandPackage(designPackage: String): String =
        packageFromLayout(artifactLayout.designCommand, designPackage)

    fun designQueryPackage(designPackage: String): String =
        packageFromLayout(artifactLayout.designQuery, designPackage)

    fun designClientPackage(designPackage: String): String =
        packageFromLayout(artifactLayout.designClient, designPackage)

    fun designQueryHandlerPackage(designPackage: String): String =
        packageFromLayout(artifactLayout.designQueryHandler, designPackage)

    fun designClientHandlerPackage(designPackage: String): String =
        packageFromLayout(artifactLayout.designClientHandler, designPackage)

    fun designValidatorPackage(designPackage: String): String =
        packageFromLayout(artifactLayout.designValidator, designPackage)

    fun designApiPayloadPackage(designPackage: String): String =
        packageFromLayout(artifactLayout.designApiPayload, designPackage)

    fun designDomainEventPackage(designPackage: String): String =
        packageFromLayout(artifactLayout.designDomainEvent, designPackage)

    fun designDomainEventHandlerPackage(designPackage: String): String =
        packageFromLayout(artifactLayout.designDomainEventHandler, designPackage)

    fun flowOutputRoot(): String =
        normalizeOutputRoot(artifactLayout.flow.outputRoot, "flow")

    fun drawingBoardOutputRoot(): String =
        normalizeOutputRoot(artifactLayout.drawingBoard.outputRoot, "drawing-board")

    fun kotlinSourcePath(moduleRoot: String, packageName: String, typeName: String): String =
        joinPath(
            moduleRoot,
            "src/main/kotlin",
            packageName.replace('.', '/'),
            "$typeName.kt",
        )

    fun projectResourcePath(outputRoot: String, relativeFileName: String): String =
        joinPath(outputRoot, relativeFileName)

    private fun packageFromLayout(layout: PackageLayout, packageName: String): String =
        joinPackage(
            basePackage,
            layout.packageRoot,
            packageName.ifBlank { layout.defaultPackage },
            layout.packageSuffix,
        )

    private fun validatePackageLayouts() {
        validatePackageFragment(basePackage, "basePackage", allowBlank = false)
        packageLayouts().forEach { (name, layout) ->
            validatePackageFragment(layout.packageRoot, "layout.$name.packageRoot")
            validatePackageFragment(layout.packageSuffix, "layout.$name.packageSuffix")
            validatePackageFragment(layout.defaultPackage, "layout.$name.defaultPackage")
        }
    }

    private fun validateOutputRoots() {
        outputRootLayouts().forEach { (name, layout) ->
            normalizeOutputRoot(layout.outputRoot, name)
        }
    }

    private fun packageLayouts(): List<Pair<String, PackageLayout>> = listOf(
        "aggregate" to artifactLayout.aggregate,
        "aggregateSchema" to artifactLayout.aggregateSchema,
        "aggregateRepository" to artifactLayout.aggregateRepository,
        "aggregateSharedEnum" to artifactLayout.aggregateSharedEnum,
        "aggregateEnumTranslation" to artifactLayout.aggregateEnumTranslation,
        "aggregateUniqueQuery" to artifactLayout.aggregateUniqueQuery,
        "aggregateUniqueQueryHandler" to artifactLayout.aggregateUniqueQueryHandler,
        "aggregateUniqueValidator" to artifactLayout.aggregateUniqueValidator,
        "designCommand" to artifactLayout.designCommand,
        "designQuery" to artifactLayout.designQuery,
        "designClient" to artifactLayout.designClient,
        "designQueryHandler" to artifactLayout.designQueryHandler,
        "designClientHandler" to artifactLayout.designClientHandler,
        "designValidator" to artifactLayout.designValidator,
        "designApiPayload" to artifactLayout.designApiPayload,
        "designDomainEvent" to artifactLayout.designDomainEvent,
        "designDomainEventHandler" to artifactLayout.designDomainEventHandler,
    )

    private fun outputRootLayouts(): List<Pair<String, OutputRootLayout>> = listOf(
        "flow" to artifactLayout.flow,
        "drawing-board" to artifactLayout.drawingBoard,
    )

    companion object {
        private val packageFragmentRegex = Regex("[A-Za-z_][A-Za-z0-9_]*(\\.[A-Za-z_][A-Za-z0-9_]*)*")

        fun joinPackage(vararg fragments: String): String =
            fragments
                .map { it.trim('.') }
                .filter { it.isNotBlank() }
                .joinToString(".")

        fun joinPath(vararg fragments: String): String =
            fragments
                .map { it.replace('\\', '/').trim('/') }
                .filter { it.isNotBlank() }
                .joinToString("/")

        fun validatePackageFragment(value: String, label: String, allowBlank: Boolean = true): String {
            if (allowBlank && value.isBlank()) {
                return value
            }
            require(packageFragmentRegex.matches(value)) {
                "$label must be a valid relative Kotlin package fragment: $value"
            }
            return value
        }

        fun normalizeOutputRoot(value: String, familyName: String): String {
            validateOutputRoot("$familyName outputRoot", value)
            return joinPath(value)
        }

        private fun validateOutputRoot(label: String, value: String) {
            val normalized = value.replace('\\', '/')
            val segments = normalized.split('/')
            require(
                value.isNotBlank() &&
                    value.trim() == value &&
                    !normalized.startsWith("/") &&
                    !normalized.startsWith("\\") &&
                    !isAbsolutePath(value) &&
                    ".." !in segments,
            ) {
                "$label must be a valid relative filesystem path: $value"
            }
        }

        private fun isAbsolutePath(value: String): Boolean =
            try {
                Path.of(value).isAbsolute
            } catch (_: InvalidPathException) {
                false
            }
    }
}
