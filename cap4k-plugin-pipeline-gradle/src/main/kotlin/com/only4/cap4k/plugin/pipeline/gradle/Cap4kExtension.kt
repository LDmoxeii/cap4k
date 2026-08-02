package com.only4.cap4k.plugin.pipeline.gradle

import org.gradle.api.Named
import org.gradle.api.NamedDomainObjectContainer
import org.gradle.api.file.ConfigurableFileCollection
import org.gradle.api.model.ObjectFactory
import org.gradle.api.provider.ListProperty
import org.gradle.api.provider.MapProperty
import org.gradle.api.provider.Property
import javax.inject.Inject

open class Cap4kExtension @Inject constructor(objects: ObjectFactory) {
    val project: Cap4kProjectExtension = objects.newInstance(Cap4kProjectExtension::class.java)
    val types: Cap4kTypesExtension = objects.newInstance(Cap4kTypesExtension::class.java)
    val sources: Cap4kSourcesExtension = objects.newInstance(Cap4kSourcesExtension::class.java)
    val generators: Cap4kGeneratorsExtension = objects.newInstance(Cap4kGeneratorsExtension::class.java)
    val templates: Cap4kTemplatesExtension = objects.newInstance(Cap4kTemplatesExtension::class.java)
    val layout: Cap4kLayoutExtension = objects.newInstance(Cap4kLayoutExtension::class.java)
    val managedFields: ManagedFieldsExtension = objects.newInstance(ManagedFieldsExtension::class.java)
    val pipelineExtensions: Cap4kPipelineExtensionsExtension =
        objects.newInstance(Cap4kPipelineExtensionsExtension::class.java)

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

    fun layout(block: Cap4kLayoutExtension.() -> Unit) {
        layout.block()
    }

    fun managedFields(block: ManagedFieldsExtension.() -> Unit) {
        managedFields.block()
    }

    fun pipelineExtensions(block: Cap4kPipelineExtensionsExtension.() -> Unit) {
        pipelineExtensions.block()
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

open class Cap4kPipelineExtensionsExtension @Inject constructor(objects: ObjectFactory) {
    val providers: NamedDomainObjectContainer<Cap4kPipelineExtensionProviderExtension> =
        objects.domainObjectContainer(Cap4kPipelineExtensionProviderExtension::class.java) { id ->
            objects.newInstance(Cap4kPipelineExtensionProviderExtension::class.java, id)
        }

    fun provider(id: String, block: Cap4kPipelineExtensionProviderExtension.() -> Unit) {
        providers.maybeCreate(id).block()
    }
}

abstract class Cap4kPipelineExtensionProviderExtension @Inject constructor(
    val id: String,
    objects: ObjectFactory,
) : Named {
    val contributions: NamedDomainObjectContainer<Cap4kPipelineContributionExtension> =
        objects.domainObjectContainer(Cap4kPipelineContributionExtension::class.java) { id ->
            objects.newInstance(Cap4kPipelineContributionExtension::class.java, id)
        }

    override fun getName(): String = id

    fun contribution(id: String, block: Cap4kPipelineContributionExtension.() -> Unit) {
        contributions.maybeCreate(id).block()
    }
}

abstract class Cap4kPipelineContributionExtension @Inject constructor(
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
    val flow: OutputRootLayoutExtension = objects.newInstance(OutputRootLayoutExtension::class.java)
        .convention("flows")
    val drawingBoard: OutputRootLayoutExtension = objects.newInstance(OutputRootLayoutExtension::class.java)
        .convention("design")
    val designCommand: PackageLayoutExtension = objects.newInstance(PackageLayoutExtension::class.java)
        .convention("application.commands")
    val designQuery: PackageLayoutExtension = objects.newInstance(PackageLayoutExtension::class.java)
        .convention("application.queries")
    val designCapability: PackageLayoutExtension = objects.newInstance(PackageLayoutExtension::class.java)
        .convention("application.capabilities")
    val designQueryHandler: PackageLayoutExtension = objects.newInstance(PackageLayoutExtension::class.java)
        .convention("adapter.application.queries")
    val designCapabilityHandler: PackageLayoutExtension = objects.newInstance(PackageLayoutExtension::class.java)
        .convention("adapter.application.capabilities")
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

    fun designCapability(block: PackageLayoutExtension.() -> Unit) {
        designCapability.block()
    }

    fun designQueryHandler(block: PackageLayoutExtension.() -> Unit) {
        designQueryHandler.block()
    }

    fun designCapabilityHandler(block: PackageLayoutExtension.() -> Unit) {
        designCapabilityHandler.block()
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
    val db: DbSourceExtension = objects.newInstance(DbSourceExtension::class.java)
    val irAnalysis: IrAnalysisSourceExtension = objects.newInstance(IrAnalysisSourceExtension::class.java)

    fun designJson(block: DesignJsonSourceExtension.() -> Unit) {
        designJson.block()
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
        aggregate.configured = true
        aggregate.block()
    }

    fun aggregateProjection(block: AggregateProjectionGeneratorExtension.() -> Unit) {
        aggregateProjection.configured = true
        aggregateProjection.block()
    }

    fun drawingBoard(block: DrawingBoardGeneratorExtension.() -> Unit) {
        drawingBoard.configured = true
        drawingBoard.block()
    }

    fun flow(block: FlowGeneratorExtension.() -> Unit) {
        flow.configured = true
        flow.block()
    }
}

open class AggregateGeneratorExtension @Inject constructor(objects: ObjectFactory) {
    internal var configured: Boolean = false
    val unsupportedTablePolicy: Property<String> = objects.property(String::class.java).convention("FAIL")
}

open class ManagedFieldsExtension @Inject constructor(objects: ObjectFactory) {
    val identifierDefaultPolicy: Property<String> =
        objects.property(String::class.java).convention("identifier.uuid7")
    val columnPolicyDefaults: MapProperty<String, String> =
        objects.mapProperty(String::class.java, String::class.java).convention(emptyMap())
}

open class AggregateProjectionGeneratorExtension @Inject constructor(objects: ObjectFactory) {
    internal var configured: Boolean = false
}

open class DrawingBoardGeneratorExtension @Inject constructor(objects: ObjectFactory) {
    internal var configured: Boolean = false
}

open class FlowGeneratorExtension @Inject constructor(objects: ObjectFactory) {
    internal var configured: Boolean = false
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
