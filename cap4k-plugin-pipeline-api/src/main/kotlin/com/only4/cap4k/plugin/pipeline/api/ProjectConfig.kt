package com.only4.cap4k.plugin.pipeline.api

data class ProjectConfig(
    val basePackage: String = "",
    val layout: ProjectLayout = ProjectLayout.SINGLE_MODULE,
    // Module role -> repository-relative filesystem path.
    val modules: Map<String, String> = emptyMap(),
    val typeRegistry: TypeRegistryConfig = TypeRegistryConfig(),
    val sources: Map<String, SourceConfig> = emptyMap(),
    val generators: Map<String, GeneratorConfig> = emptyMap(),
    val templates: TemplateConfig = TemplateConfig(
        preset = "ddd-default",
        overrideDirs = emptyList(),
        conflictPolicy = ConflictPolicy.SKIP,
    ),
    val artifactLayout: ArtifactLayoutConfig = ArtifactLayoutConfig(),
    val managedFields: ManagedFieldDefaultsConfig = ManagedFieldDefaultsConfig(),
    val pipelineExtensions: Map<String, PipelineExtensionConfig> = emptyMap(),
) {
    fun typeRegistryFqns(): Map<String, String> = typeRegistry.entries.mapValues { it.value.fqn }
}

data class ManagedFieldDefaultsConfig(
    val identifierDefaultPolicy: String = "identifier.uuid7",
    val columnPolicyDefaults: Map<String, String> = emptyMap(),
)

data class TypeRegistryConfig(
    val entries: Map<String, TypeRegistryEntry> = emptyMap(),
    val registryFile: String = "",
    val enumManifestFiles: List<String> = emptyList(),
    val valueObjectManifestFiles: List<String> = emptyList(),
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

data class PipelineExtensionConfig(
    val id: String,
    val contributions: Map<String, PipelineContributionConfig> = emptyMap(),
)

data class PipelineContributionConfig(
    val id: String,
    val options: Map<String, String> = emptyMap(),
)

data class ArtifactLayoutConfig(
    val aggregate: PackageLayout = PackageLayout("domain.aggregates"),
    val aggregateSchema: PackageLayout = PackageLayout("domain._share.meta"),
    val aggregateRepository: PackageLayout = PackageLayout("adapter.domain.repositories"),
    val aggregateSharedEnum: PackageLayout = PackageLayout(
        packageRoot = "domain",
        defaultPackage = "shared",
        packageSuffix = "enums",
    ),
    val flow: OutputRootLayout = OutputRootLayout("flows"),
    val drawingBoard: OutputRootLayout = OutputRootLayout("design"),
    val designEndpoint: PackageLayout = PackageLayout("contract.endpoints"),
    val designCommand: PackageLayout = PackageLayout("application.commands"),
    val designQuery: PackageLayout = PackageLayout("application.queries"),
    val designCapability: PackageLayout = PackageLayout("application.capabilities"),
    val designQueryHandler: PackageLayout = PackageLayout("adapter.application.queries"),
    val designCapabilityHandler: PackageLayout = PackageLayout("adapter.application.capabilities"),
    val designDomainEvent: PackageLayout = PackageLayout(
        packageRoot = "domain.aggregates",
        packageSuffix = "events",
    ),
    val designDomainEventHandler: PackageLayout = PackageLayout(
        packageRoot = "application.subscribers.domain",
        packageSuffix = "",
    ),
    val designIntegrationEvent: PackageLayout = PackageLayout(
        packageRoot = "contract.events.integration",
        packageSuffix = "",
    ),
    val designIntegrationEventSubscriber: PackageLayout = PackageLayout(
        packageRoot = "application.subscribers.integration",
        packageSuffix = "",
    ),
    val designDomainServicePackage: PackageLayout = PackageLayout("domain.services"),
    val designDomainService: ArtifactLayoutRule = ArtifactLayoutRule("design/domain_service.kt.peb"),
    val valueObject: ArtifactLayoutRule = ArtifactLayoutRule("types/value-object"),
    val valueObjectJsonConverter: ArtifactLayoutRule = ArtifactLayoutRule("types/value-object-json-converter"),
)

data class ArtifactLayoutRule(
    val id: String,
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
    val options: Map<String, Any?> = emptyMap(),
)

data class GeneratorConfig(
    val options: Map<String, Any?> = emptyMap(),
)

data class TemplateConfig(
    val preset: String,
    val overrideDirs: List<String>,
    val conflictPolicy: ConflictPolicy,
    val templateConflictPolicies: Map<String, ConflictPolicy> = emptyMap(),
)

enum class ConflictPolicy {
    SKIP,
    OVERWRITE,
    FAIL,
}
