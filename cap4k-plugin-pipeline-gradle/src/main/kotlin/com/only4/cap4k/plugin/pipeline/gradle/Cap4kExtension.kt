package com.only4.cap4k.plugin.pipeline.gradle

import com.only4.cap4k.plugin.pipeline.api.BootstrapSlotBinding
import com.only4.cap4k.plugin.pipeline.api.BootstrapSlotKind
import com.only4.cap4k.plugin.pipeline.api.BootstrapMode
import org.gradle.api.Named
import org.gradle.api.NamedDomainObjectContainer
import org.gradle.api.Project
import org.gradle.api.file.ConfigurableFileCollection
import org.gradle.api.model.ObjectFactory
import org.gradle.api.provider.ListProperty
import org.gradle.api.provider.MapProperty
import org.gradle.api.provider.Property
import javax.inject.Inject
import kotlin.io.path.invariantSeparatorsPathString

open class Cap4kExtension @Inject constructor(objects: ObjectFactory) {
    val project: Cap4kProjectExtension = objects.newInstance(Cap4kProjectExtension::class.java)
    val types: Cap4kTypesExtension = objects.newInstance(Cap4kTypesExtension::class.java)
    val sources: Cap4kSourcesExtension = objects.newInstance(Cap4kSourcesExtension::class.java)
    val generators: Cap4kGeneratorsExtension = objects.newInstance(Cap4kGeneratorsExtension::class.java)
    val templates: Cap4kTemplatesExtension = objects.newInstance(Cap4kTemplatesExtension::class.java)
    val bootstrap: Cap4kBootstrapExtension = objects.newInstance(Cap4kBootstrapExtension::class.java)
    val layout: Cap4kLayoutExtension = objects.newInstance(Cap4kLayoutExtension::class.java)
    val addons: Cap4kAddonsExtension = objects.newInstance(Cap4kAddonsExtension::class.java)

    fun project(block: Cap4kProjectExtension.() -> Unit) {
        project.block()
    }

    fun types(block: Cap4kTypesExtension.() -> Unit) {
        types.block()
    }

    fun sources(block: Cap4kSourcesExtension.() -> Unit) {
        sources.block()
    }

    fun generators(block: Cap4kGeneratorsExtension.() -> Unit) {
        generators.block()
    }

    fun templates(block: Cap4kTemplatesExtension.() -> Unit) {
        templates.block()
    }

    fun bootstrap(block: Cap4kBootstrapExtension.() -> Unit) {
        bootstrap.block()
    }

    fun layout(block: Cap4kLayoutExtension.() -> Unit) {
        layout.block()
    }

    fun addons(block: Cap4kAddonsExtension.() -> Unit) {
        addons.block()
    }
}

internal typealias PipelineExtension = Cap4kExtension

open class Cap4kProjectExtension @Inject constructor(objects: ObjectFactory) {
    val basePackage: Property<String> = objects.property(String::class.java)
    val applicationModulePath: Property<String> = objects.property(String::class.java)
    val domainModulePath: Property<String> = objects.property(String::class.java)
    val adapterModulePath: Property<String> = objects.property(String::class.java)
}

open class Cap4kTypesExtension @Inject constructor(objects: ObjectFactory) {
    val registryFile: Property<String> = objects.property(String::class.java)
    val enumManifest: TypeManifestExtension = objects.newInstance(TypeManifestExtension::class.java)
    val valueObjectManifest: TypeManifestExtension = objects.newInstance(TypeManifestExtension::class.java)

    fun enumManifest(block: TypeManifestExtension.() -> Unit) {
        enumManifest.block()
    }

    fun valueObjectManifest(block: TypeManifestExtension.() -> Unit) {
        valueObjectManifest.block()
    }
}

open class TypeManifestExtension @Inject constructor(objects: ObjectFactory) {
    val files: ConfigurableFileCollection = objects.fileCollection()
}

open class Cap4kAddonsExtension @Inject constructor(objects: ObjectFactory) {
    val providers: NamedDomainObjectContainer<Cap4kAddonProviderExtension> =
        objects.domainObjectContainer(Cap4kAddonProviderExtension::class.java) { id ->
            objects.newInstance(Cap4kAddonProviderExtension::class.java, id)
        }

    fun provider(id: String, block: Cap4kAddonProviderExtension.() -> Unit) {
        providers.maybeCreate(id).block()
    }
}

abstract class Cap4kAddonProviderExtension @Inject constructor(
    val id: String,
    objects: ObjectFactory,
) : Named {
    val options: MapProperty<String, String> = objects.mapProperty(String::class.java, String::class.java)

    override fun getName(): String = id

    fun option(key: String, value: String) {
        options.put(key, value)
    }
}

open class Cap4kLayoutExtension @Inject constructor(objects: ObjectFactory) {
    val aggregate: PackageLayoutExtension = objects.newInstance(PackageLayoutExtension::class.java)
        .convention("domain.aggregates")
    val aggregateSchema: PackageLayoutExtension = objects.newInstance(PackageLayoutExtension::class.java)
        .convention("domain._share.meta")
    val aggregateRepository: PackageLayoutExtension = objects.newInstance(PackageLayoutExtension::class.java)
        .convention("adapter.domain.repositories")
    val aggregateSharedEnum: PackageLayoutExtension = objects.newInstance(PackageLayoutExtension::class.java)
        .convention(packageRoot = "domain", packageSuffix = "enums", defaultPackage = "shared")
    val aggregateUniqueQuery: PackageLayoutExtension = objects.newInstance(PackageLayoutExtension::class.java)
        .convention(packageRoot = "application.queries", packageSuffix = "unique")
    val aggregateUniqueQueryHandler: PackageLayoutExtension = objects.newInstance(PackageLayoutExtension::class.java)
        .convention(packageRoot = "adapter.queries", packageSuffix = "unique")
    val aggregateUniqueValidator: PackageLayoutExtension = objects.newInstance(PackageLayoutExtension::class.java)
        .convention(packageRoot = "application.validators", packageSuffix = "unique")
    val flow: OutputRootLayoutExtension = objects.newInstance(OutputRootLayoutExtension::class.java)
        .convention("flows")
    val drawingBoard: OutputRootLayoutExtension = objects.newInstance(OutputRootLayoutExtension::class.java)
        .convention("design")
    val designCommand: PackageLayoutExtension = objects.newInstance(PackageLayoutExtension::class.java)
        .convention("application.commands")
    val designQuery: PackageLayoutExtension = objects.newInstance(PackageLayoutExtension::class.java)
        .convention("application.queries")
    val designClient: PackageLayoutExtension = objects.newInstance(PackageLayoutExtension::class.java)
        .convention("application.distributed.clients")
    val designQueryHandler: PackageLayoutExtension = objects.newInstance(PackageLayoutExtension::class.java)
        .convention("adapter.application.queries")
    val designClientHandler: PackageLayoutExtension = objects.newInstance(PackageLayoutExtension::class.java)
        .convention("adapter.application.distributed.clients")
    val designApiPayload: PackageLayoutExtension = objects.newInstance(PackageLayoutExtension::class.java)
        .convention("adapter.portal.api.payload")
    val designDomainEvent: PackageLayoutExtension = objects.newInstance(PackageLayoutExtension::class.java)
        .convention(packageRoot = "domain.aggregates", packageSuffix = "events")
    val designDomainEventHandler: PackageLayoutExtension = objects.newInstance(PackageLayoutExtension::class.java)
        .convention("application.subscribers.domain")
    val designIntegrationEvent: PackageLayoutExtension = objects.newInstance(PackageLayoutExtension::class.java)
        .convention("application.subscribers.integration")
    val designIntegrationEventSubscriber: PackageLayoutExtension = objects.newInstance(PackageLayoutExtension::class.java)
        .convention("application.subscribers.integration")

    fun aggregate(block: PackageLayoutExtension.() -> Unit) {
        aggregate.block()
    }

    fun aggregateSchema(block: PackageLayoutExtension.() -> Unit) {
        aggregateSchema.block()
    }

    fun aggregateRepository(block: PackageLayoutExtension.() -> Unit) {
        aggregateRepository.block()
    }

    fun aggregateSharedEnum(block: PackageLayoutExtension.() -> Unit) {
        aggregateSharedEnum.block()
    }

    fun aggregateUniqueQuery(block: PackageLayoutExtension.() -> Unit) {
        aggregateUniqueQuery.block()
    }

    fun aggregateUniqueQueryHandler(block: PackageLayoutExtension.() -> Unit) {
        aggregateUniqueQueryHandler.block()
    }

    fun aggregateUniqueValidator(block: PackageLayoutExtension.() -> Unit) {
        aggregateUniqueValidator.block()
    }

    fun flow(block: OutputRootLayoutExtension.() -> Unit) {
        flow.block()
    }

    fun drawingBoard(block: OutputRootLayoutExtension.() -> Unit) {
        drawingBoard.block()
    }

    fun designCommand(block: PackageLayoutExtension.() -> Unit) {
        designCommand.block()
    }

    fun designQuery(block: PackageLayoutExtension.() -> Unit) {
        designQuery.block()
    }

    fun designClient(block: PackageLayoutExtension.() -> Unit) {
        designClient.block()
    }

    fun designQueryHandler(block: PackageLayoutExtension.() -> Unit) {
        designQueryHandler.block()
    }

    fun designClientHandler(block: PackageLayoutExtension.() -> Unit) {
        designClientHandler.block()
    }

    fun designApiPayload(block: PackageLayoutExtension.() -> Unit) {
        designApiPayload.block()
    }

    fun designDomainEvent(block: PackageLayoutExtension.() -> Unit) {
        designDomainEvent.block()
    }

    fun designDomainEventHandler(block: PackageLayoutExtension.() -> Unit) {
        designDomainEventHandler.block()
    }

    fun designIntegrationEvent(block: PackageLayoutExtension.() -> Unit) {
        designIntegrationEvent.block()
    }

    fun designIntegrationEventSubscriber(block: PackageLayoutExtension.() -> Unit) {
        designIntegrationEventSubscriber.block()
    }
}

open class PackageLayoutExtension @Inject constructor(objects: ObjectFactory) {
    val packageRoot: Property<String> = objects.property(String::class.java)
    val packageSuffix: Property<String> = objects.property(String::class.java).convention("")
    val defaultPackage: Property<String> = objects.property(String::class.java).convention("")

    fun convention(
        packageRoot: String,
        packageSuffix: String = "",
        defaultPackage: String = "",
    ): PackageLayoutExtension {
        this.packageRoot.convention(packageRoot)
        this.packageSuffix.convention(packageSuffix)
        this.defaultPackage.convention(defaultPackage)
        return this
    }
}

open class OutputRootLayoutExtension @Inject constructor(objects: ObjectFactory) {
    val outputRoot: Property<String> = objects.property(String::class.java)

    fun convention(outputRoot: String): OutputRootLayoutExtension {
        this.outputRoot.convention(outputRoot)
        return this
    }
}

open class Cap4kSourcesExtension @Inject constructor(objects: ObjectFactory) {
    val designJson: DesignJsonSourceExtension = objects.newInstance(DesignJsonSourceExtension::class.java)
    val kspMetadata: KspMetadataSourceExtension = objects.newInstance(KspMetadataSourceExtension::class.java)
    val db: DbSourceExtension = objects.newInstance(DbSourceExtension::class.java)
    val irAnalysis: IrAnalysisSourceExtension = objects.newInstance(IrAnalysisSourceExtension::class.java)

    fun designJson(block: DesignJsonSourceExtension.() -> Unit) {
        designJson.block()
    }

    fun kspMetadata(block: KspMetadataSourceExtension.() -> Unit) {
        kspMetadata.block()
    }

    fun db(block: DbSourceExtension.() -> Unit) {
        db.block()
    }

    fun irAnalysis(block: IrAnalysisSourceExtension.() -> Unit) {
        irAnalysis.block()
    }
}

open class DesignJsonSourceExtension @Inject constructor(objects: ObjectFactory) {
    val manifestFile: Property<String> = objects.property(String::class.java)
    val files: ConfigurableFileCollection = objects.fileCollection()
}

open class KspMetadataSourceExtension @Inject constructor(objects: ObjectFactory) {
    val enabled: Property<Boolean> = objects.property(Boolean::class.java).convention(false)
    val inputDir: Property<String> = objects.property(String::class.java)
}

open class DbSourceExtension @Inject constructor(objects: ObjectFactory) {
    val enabled: Property<Boolean> = objects.property(Boolean::class.java).convention(false)
    val url: Property<String> = objects.property(String::class.java)
    val username: Property<String> = objects.property(String::class.java)
    val password: Property<String> = objects.property(String::class.java)
    val schema: Property<String> = objects.property(String::class.java)
    val includeTables: ListProperty<String> = objects.listProperty(String::class.java)
    val excludeTables: ListProperty<String> = objects.listProperty(String::class.java)
}

open class IrAnalysisSourceExtension @Inject constructor(objects: ObjectFactory) {
    val inputDirs: ConfigurableFileCollection = objects.fileCollection()
}

open class Cap4kGeneratorsExtension @Inject constructor(objects: ObjectFactory) {
    val aggregate: AggregateGeneratorExtension = objects.newInstance(AggregateGeneratorExtension::class.java)
    val aggregateProjection: AggregateProjectionGeneratorExtension =
        objects.newInstance(AggregateProjectionGeneratorExtension::class.java)
    val drawingBoard: DrawingBoardGeneratorExtension = objects.newInstance(DrawingBoardGeneratorExtension::class.java)
    val flow: FlowGeneratorExtension = objects.newInstance(FlowGeneratorExtension::class.java)

    fun aggregate(block: AggregateGeneratorExtension.() -> Unit) {
        aggregate.block()
    }

    fun aggregateProjection(block: AggregateProjectionGeneratorExtension.() -> Unit) {
        aggregateProjection.block()
    }

    fun drawingBoard(block: DrawingBoardGeneratorExtension.() -> Unit) {
        drawingBoard.block()
    }

    fun flow(block: FlowGeneratorExtension.() -> Unit) {
        flow.block()
    }
}

open class AggregateGeneratorExtension @Inject constructor(objects: ObjectFactory) {
    val enabled: Property<Boolean> = objects.property(Boolean::class.java).convention(false)
    val unsupportedTablePolicy: Property<String> = objects.property(String::class.java).convention("FAIL")
    val specialFields: AggregateSpecialFieldsExtension =
        objects.newInstance(AggregateSpecialFieldsExtension::class.java)
    val artifacts: AggregateGeneratorArtifactsExtension =
        objects.newInstance(AggregateGeneratorArtifactsExtension::class.java)

    fun specialFields(block: AggregateSpecialFieldsExtension.() -> Unit) {
        specialFields.block()
    }

    @Deprecated("generators.aggregate.idPolicy is removed. Use generators.aggregate.specialFields instead.")
    fun idPolicy(@Suppress("UNUSED_PARAMETER") block: AggregateIdPolicyExtension.() -> Unit) {
        throw IllegalArgumentException(
            "generators.aggregate.idPolicy is removed. Use generators.aggregate.specialFields { idDefaultStrategy, deletedDefaultColumn, versionDefaultColumn }."
        )
    }

    fun artifacts(block: AggregateGeneratorArtifactsExtension.() -> Unit) {
        artifacts.block()
    }
}

open class AggregateIdPolicyExtension @Inject constructor(objects: ObjectFactory) {
    @Deprecated("Use generators.aggregate.specialFields.idDefaultStrategy instead.")
    val defaultStrategy: Property<String> = objects.property(String::class.java).convention("uuid7")

    @Deprecated("aggregate-level id overrides are removed.")
    fun aggregate(name: String, strategy: String) {
        throw IllegalArgumentException(
            "generators.aggregate.idPolicy.aggregate(...) is removed. Use generators.aggregate.specialFields.idDefaultStrategy and column-level @GeneratedValue declarations."
        )
    }

    @Deprecated("entity-level id overrides are removed.")
    fun entity(name: String, strategy: String) {
        throw IllegalArgumentException(
            "generators.aggregate.idPolicy.entity(...) is removed. Use generators.aggregate.specialFields.idDefaultStrategy and column-level @GeneratedValue declarations."
        )
    }
}

open class AggregateSpecialFieldsExtension @Inject constructor(objects: ObjectFactory) {
    val idDefaultStrategy: Property<String> = objects.property(String::class.java).convention("uuid7")
    val deletedDefaultColumn: Property<String> = objects.property(String::class.java).convention("")
    val versionDefaultColumn: Property<String> = objects.property(String::class.java).convention("")
    val managedDefaultColumns: ListProperty<String> =
        objects.listProperty(String::class.java).convention(emptyList())
}

open class AggregateGeneratorArtifactsExtension @Inject constructor(objects: ObjectFactory) {
    val factory: Property<Boolean> = objects.property(Boolean::class.java).convention(false)
    val specification: Property<Boolean> = objects.property(Boolean::class.java).convention(false)
    val unique: Property<Boolean> = objects.property(Boolean::class.java).convention(false)
}

open class AggregateProjectionGeneratorExtension @Inject constructor(objects: ObjectFactory) {
    val enabled: Property<Boolean> = objects.property(Boolean::class.java).convention(false)
}

open class DrawingBoardGeneratorExtension @Inject constructor(objects: ObjectFactory) {
}

open class FlowGeneratorExtension @Inject constructor(objects: ObjectFactory) {
}

open class Cap4kBootstrapExtension @Inject constructor(objects: ObjectFactory) {
    val enabled: Property<Boolean> = objects.property(Boolean::class.java).convention(false)
    val preset: Property<String> = objects.property(String::class.java).convention("ddd-multi-module")
    val mode: Property<BootstrapMode> = objects.property(BootstrapMode::class.java).convention(BootstrapMode.IN_PLACE)
    val previewDir: Property<String> = objects.property(String::class.java)
    val projectName: Property<String> = objects.property(String::class.java)
    val basePackage: Property<String> = objects.property(String::class.java)
    val modules: Cap4kBootstrapModulesExtension =
        objects.newInstance(Cap4kBootstrapModulesExtension::class.java)
    val templates: Cap4kBootstrapTemplatesExtension =
        objects.newInstance(Cap4kBootstrapTemplatesExtension::class.java)
    val slots: Cap4kBootstrapSlotsExtension =
        objects.newInstance(Cap4kBootstrapSlotsExtension::class.java)
    val conflictPolicy: Property<String> = objects.property(String::class.java).convention("FAIL")

    fun modules(block: Cap4kBootstrapModulesExtension.() -> Unit) {
        modules.block()
    }

    fun templates(block: Cap4kBootstrapTemplatesExtension.() -> Unit) {
        templates.block()
    }

    fun slots(block: Cap4kBootstrapSlotsExtension.() -> Unit) {
        slots.block()
    }
}

open class Cap4kBootstrapModulesExtension @Inject constructor(objects: ObjectFactory) {
    val domainModuleName: Property<String> = objects.property(String::class.java)
    val applicationModuleName: Property<String> = objects.property(String::class.java)
    val adapterModuleName: Property<String> = objects.property(String::class.java)
    val startModuleName: Property<String> = objects.property(String::class.java)
}

open class Cap4kBootstrapTemplatesExtension @Inject constructor(objects: ObjectFactory) {
    val preset: Property<String> = objects.property(String::class.java).convention("ddd-default-bootstrap")
    val overrideDirs: ConfigurableFileCollection = objects.fileCollection()
}

open class Cap4kBootstrapSlotsExtension @Inject constructor(private val objects: ObjectFactory) {
    val root: ConfigurableFileCollection = objects.fileCollection()
    val buildLogic: ConfigurableFileCollection = objects.fileCollection()

    private val moduleRoot: MutableMap<String, ConfigurableFileCollection> = linkedMapOf()
    private val modulePackage: MutableMap<String, ConfigurableFileCollection> = linkedMapOf()
    private val moduleResources: MutableMap<String, ConfigurableFileCollection> = linkedMapOf()

    fun moduleRoot(role: String): ConfigurableFileCollection =
        moduleRoot.getOrPut(role) { objects.fileCollection() }

    fun modulePackage(role: String): ConfigurableFileCollection =
        modulePackage.getOrPut(role) { objects.fileCollection() }

    fun moduleResources(role: String): ConfigurableFileCollection =
        moduleResources.getOrPut(role) { objects.fileCollection() }

    fun bindings(project: Project): List<BootstrapSlotBinding> = buildList {
        addBindings(project, BootstrapSlotKind.ROOT, null, root)
        addBindings(project, BootstrapSlotKind.BUILD_LOGIC, null, buildLogic)
        moduleRoot.forEach { (role, sourceDirs) ->
            addBindings(project, BootstrapSlotKind.MODULE_ROOT, role, sourceDirs)
        }
        modulePackage.forEach { (role, sourceDirs) ->
            addBindings(project, BootstrapSlotKind.MODULE_PACKAGE, role, sourceDirs)
        }
        moduleResources.forEach { (role, sourceDirs) ->
            addBindings(project, BootstrapSlotKind.MODULE_RESOURCES, role, sourceDirs)
        }
    }

    private fun MutableList<BootstrapSlotBinding>.addBindings(
        project: Project,
        kind: BootstrapSlotKind,
        role: String?,
        sourceDirs: ConfigurableFileCollection,
    ) {
        val projectRoot = project.projectDir.toPath().toAbsolutePath().normalize()
        sourceDirs.files
            .map { project.file(it).toPath().toAbsolutePath().normalize() }
            .map { sourcePath ->
                if (sourcePath.startsWith(projectRoot)) {
                    projectRoot.relativize(sourcePath).invariantSeparatorsPathString
                } else {
                    sourcePath.invariantSeparatorsPathString
                }
            }
            .sorted()
            .forEach { sourceDir ->
            add(
                BootstrapSlotBinding(
                    kind = kind,
                    role = role,
                    sourceDir = sourceDir,
                )
            )
            }
    }
}

open class Cap4kTemplatesExtension @Inject constructor(objects: ObjectFactory) {
    val preset: Property<String> = objects.property(String::class.java).convention("ddd-default")
    val overrideDirs: ConfigurableFileCollection = objects.fileCollection()
    val conflictPolicy: Property<String> = objects.property(String::class.java).convention("SKIP")
    val templateConflictPolicies: MapProperty<String, String> =
        objects.mapProperty(String::class.java, String::class.java).convention(emptyMap())
    internal val templateOverrideDir: Property<String> = objects.property(String::class.java)
}

internal val Cap4kExtension.basePackage: Property<String>
    get() = project.basePackage

internal val Cap4kExtension.applicationModulePath: Property<String>
    get() = project.applicationModulePath

internal val Cap4kExtension.domainModulePath: Property<String>
    get() = project.domainModulePath

internal val Cap4kExtension.adapterModulePath: Property<String>
    get() = project.adapterModulePath

internal val Cap4kExtension.designFiles: ConfigurableFileCollection
    get() = sources.designJson.files

internal val Cap4kExtension.kspMetadataDir: Property<String>
    get() = sources.kspMetadata.inputDir

internal val Cap4kExtension.irAnalysisInputDirs: ConfigurableFileCollection
    get() = sources.irAnalysis.inputDirs

internal val Cap4kExtension.dbUrl: Property<String>
    get() = sources.db.url

internal val Cap4kExtension.dbUsername: Property<String>
    get() = sources.db.username

internal val Cap4kExtension.dbPassword: Property<String>
    get() = sources.db.password

internal val Cap4kExtension.dbSchema: Property<String>
    get() = sources.db.schema

internal val Cap4kExtension.dbIncludeTables: ListProperty<String>
    get() = sources.db.includeTables

internal val Cap4kExtension.dbExcludeTables: ListProperty<String>
    get() = sources.db.excludeTables

internal val Cap4kExtension.templateOverrideDir: Property<String>
    get() = templates.templateOverrideDir
