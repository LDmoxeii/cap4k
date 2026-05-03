package com.only4.cap4k.plugin.pipeline.api

data class ProjectConfig(
    val basePackage: String,
    val layout: ProjectLayout,
    // Module role -> repository-relative filesystem path.
    val modules: Map<String, String>,
    val typeRegistry: Map<String, TypeRegistryEntry> = emptyMap(),
    val sources: Map<String, SourceConfig>,
    val generators: Map<String, GeneratorConfig>,
    val templates: TemplateConfig,
    val artifactLayout: ArtifactLayoutConfig = ArtifactLayoutConfig(),
    val aggregateSpecialFieldDefaults: AggregateSpecialFieldDefaultsConfig = AggregateSpecialFieldDefaultsConfig(),
) {
    fun enabledSourceIds(): Set<String> = sources.filterValues { it.enabled }.keys

    fun enabledGeneratorIds(): Set<String> = generators.filterValues { it.enabled }.keys

    fun typeRegistryFqns(): Map<String, String> = typeRegistry.mapValues { it.value.fqn }
}

data class AggregateSpecialFieldDefaultsConfig(
    val idDefaultStrategy: String = "uuid7",
    val deletedDefaultColumn: String = "",
    val versionDefaultColumn: String = "",
)

data class TypeRegistryEntry(
    val fqn: String,
    val converter: TypeRegistryConverter = TypeRegistryConverter.nested(),
)

data class TypeRegistryConverter(
    val kind: TypeRegistryConverterKind,
    val fqn: String? = null,
) {
    companion object {
        fun none(): TypeRegistryConverter = TypeRegistryConverter(TypeRegistryConverterKind.NONE)

        fun nested(): TypeRegistryConverter = TypeRegistryConverter(TypeRegistryConverterKind.NESTED)

        fun explicit(fqn: String): TypeRegistryConverter =
            TypeRegistryConverter(TypeRegistryConverterKind.EXPLICIT, fqn)
    }
}

enum class TypeRegistryConverterKind {
    NONE,
    NESTED,
    EXPLICIT,
}

enum class ProjectLayout {
    SINGLE_MODULE,
    MULTI_MODULE,
}

data class ArtifactLayoutConfig(
    val aggregate: PackageLayout = PackageLayout("domain.aggregates"),
    val aggregateSchema: PackageLayout = PackageLayout("domain._share.meta"),
    val aggregateRepository: PackageLayout = PackageLayout("adapter.domain.repositories"),
    val aggregateSharedEnum: PackageLayout = PackageLayout(
        packageRoot = "domain",
        defaultPackage = "shared",
        packageSuffix = "enums",
    ),
    val aggregateEnumTranslation: PackageLayout = PackageLayout("adapter.domain.translation"),
    val aggregateUniqueQuery: PackageLayout = PackageLayout(
        packageRoot = "application.queries",
        packageSuffix = "unique",
    ),
    val aggregateUniqueQueryHandler: PackageLayout = PackageLayout(
        packageRoot = "adapter.queries",
        packageSuffix = "unique",
    ),
    val aggregateUniqueValidator: PackageLayout = PackageLayout(
        packageRoot = "application.validators",
        packageSuffix = "unique",
    ),
    val flow: OutputRootLayout = OutputRootLayout("flows"),
    val drawingBoard: OutputRootLayout = OutputRootLayout("design"),
    val designCommand: PackageLayout = PackageLayout("application.commands"),
    val designQuery: PackageLayout = PackageLayout("application.queries"),
    val designClient: PackageLayout = PackageLayout("application.distributed.clients"),
    val designQueryHandler: PackageLayout = PackageLayout("adapter.application.queries"),
    val designClientHandler: PackageLayout = PackageLayout("adapter.application.distributed.clients"),
    val designValidator: PackageLayout = PackageLayout("application.validators"),
    val designApiPayload: PackageLayout = PackageLayout("adapter.portal.api.payload"),
    val designDomainEvent: PackageLayout = PackageLayout(
        packageRoot = "domain.aggregates",
        packageSuffix = "events",
    ),
    val designDomainEventHandler: PackageLayout = PackageLayout(
        packageRoot = "application.subscribers.domain",
        packageSuffix = "",
    ),
)

data class PackageLayout(
    val packageRoot: String,
    val packageSuffix: String = "",
    val defaultPackage: String = "",
)

data class OutputRootLayout(
    val outputRoot: String,
)

data class SourceConfig(
    val enabled: Boolean,
    val options: Map<String, Any?> = emptyMap(),
)

data class GeneratorConfig(
    val enabled: Boolean,
    val options: Map<String, Any?> = emptyMap(),
)

data class TemplateConfig(
    val preset: String,
    val overrideDirs: List<String>,
    val conflictPolicy: ConflictPolicy,
)

enum class ConflictPolicy {
    SKIP,
    OVERWRITE,
    FAIL,
}
