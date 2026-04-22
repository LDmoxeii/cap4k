package com.only4.cap4k.plugin.pipeline.gradle

import com.only4.cap4k.plugin.pipeline.api.BootstrapSlotBinding
import com.only4.cap4k.plugin.pipeline.api.BootstrapSlotKind
import com.only4.cap4k.plugin.pipeline.api.BootstrapMode
import org.gradle.api.file.ConfigurableFileCollection
import org.gradle.api.model.ObjectFactory
import org.gradle.api.Project
import org.gradle.api.provider.ListProperty
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
}

open class Cap4kSourcesExtension @Inject constructor(objects: ObjectFactory) {
    val designJson: DesignJsonSourceExtension = objects.newInstance(DesignJsonSourceExtension::class.java)
    val kspMetadata: KspMetadataSourceExtension = objects.newInstance(KspMetadataSourceExtension::class.java)
    val db: DbSourceExtension = objects.newInstance(DbSourceExtension::class.java)
    val enumManifest: EnumManifestSourceExtension =
        objects.newInstance(EnumManifestSourceExtension::class.java)
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

    fun enumManifest(block: EnumManifestSourceExtension.() -> Unit) {
        enumManifest.block()
    }

    fun irAnalysis(block: IrAnalysisSourceExtension.() -> Unit) {
        irAnalysis.block()
    }
}

open class DesignJsonSourceExtension @Inject constructor(objects: ObjectFactory) {
    val enabled: Property<Boolean> = objects.property(Boolean::class.java).convention(false)
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

open class EnumManifestSourceExtension @Inject constructor(objects: ObjectFactory) {
    val enabled: Property<Boolean> = objects.property(Boolean::class.java).convention(false)
    val files: ConfigurableFileCollection = objects.fileCollection()
}

open class IrAnalysisSourceExtension @Inject constructor(objects: ObjectFactory) {
    val enabled: Property<Boolean> = objects.property(Boolean::class.java).convention(false)
    val inputDirs: ConfigurableFileCollection = objects.fileCollection()
}

open class Cap4kGeneratorsExtension @Inject constructor(objects: ObjectFactory) {
    val design: DesignGeneratorExtension = objects.newInstance(DesignGeneratorExtension::class.java)
    val designQueryHandler: DesignQueryHandlerGeneratorExtension =
        objects.newInstance(DesignQueryHandlerGeneratorExtension::class.java)
    val designClient: DesignClientGeneratorExtension =
        objects.newInstance(DesignClientGeneratorExtension::class.java)
    val designClientHandler: DesignClientHandlerGeneratorExtension =
        objects.newInstance(DesignClientHandlerGeneratorExtension::class.java)
    val designValidator: DesignValidatorGeneratorExtension =
        objects.newInstance(DesignValidatorGeneratorExtension::class.java)
    val designApiPayload: DesignApiPayloadGeneratorExtension =
        objects.newInstance(DesignApiPayloadGeneratorExtension::class.java)
    val designDomainEvent: DesignDomainEventGeneratorExtension =
        objects.newInstance(DesignDomainEventGeneratorExtension::class.java)
    val designDomainEventHandler: DesignDomainEventHandlerGeneratorExtension =
        objects.newInstance(DesignDomainEventHandlerGeneratorExtension::class.java)
    val aggregate: AggregateGeneratorExtension = objects.newInstance(AggregateGeneratorExtension::class.java)
    val drawingBoard: DrawingBoardGeneratorExtension = objects.newInstance(DrawingBoardGeneratorExtension::class.java)
    val flow: FlowGeneratorExtension = objects.newInstance(FlowGeneratorExtension::class.java)

    fun design(block: DesignGeneratorExtension.() -> Unit) {
        design.block()
    }

    fun designQueryHandler(block: DesignQueryHandlerGeneratorExtension.() -> Unit) {
        designQueryHandler.block()
    }

    fun designClient(block: DesignClientGeneratorExtension.() -> Unit) {
        designClient.block()
    }

    fun designClientHandler(block: DesignClientHandlerGeneratorExtension.() -> Unit) {
        designClientHandler.block()
    }

    fun designValidator(block: DesignValidatorGeneratorExtension.() -> Unit) {
        designValidator.block()
    }

    fun designApiPayload(block: DesignApiPayloadGeneratorExtension.() -> Unit) {
        designApiPayload.block()
    }

    fun designDomainEvent(block: DesignDomainEventGeneratorExtension.() -> Unit) {
        designDomainEvent.block()
    }

    fun designDomainEventHandler(block: DesignDomainEventHandlerGeneratorExtension.() -> Unit) {
        designDomainEventHandler.block()
    }

    fun aggregate(block: AggregateGeneratorExtension.() -> Unit) {
        aggregate.block()
    }

    fun drawingBoard(block: DrawingBoardGeneratorExtension.() -> Unit) {
        drawingBoard.block()
    }

    fun flow(block: FlowGeneratorExtension.() -> Unit) {
        flow.block()
    }
}

open class DesignGeneratorExtension @Inject constructor(objects: ObjectFactory) {
    val enabled: Property<Boolean> = objects.property(Boolean::class.java).convention(false)
}

open class DesignQueryHandlerGeneratorExtension @Inject constructor(objects: ObjectFactory) {
    val enabled: Property<Boolean> = objects.property(Boolean::class.java).convention(false)
}

open class DesignClientGeneratorExtension @Inject constructor(objects: ObjectFactory) {
    val enabled: Property<Boolean> = objects.property(Boolean::class.java).convention(false)
}

open class DesignClientHandlerGeneratorExtension @Inject constructor(objects: ObjectFactory) {
    val enabled: Property<Boolean> = objects.property(Boolean::class.java).convention(false)
}

open class DesignValidatorGeneratorExtension @Inject constructor(objects: ObjectFactory) {
    val enabled: Property<Boolean> = objects.property(Boolean::class.java).convention(false)
}

open class DesignApiPayloadGeneratorExtension @Inject constructor(objects: ObjectFactory) {
    val enabled: Property<Boolean> = objects.property(Boolean::class.java).convention(false)
}

open class DesignDomainEventGeneratorExtension @Inject constructor(objects: ObjectFactory) {
    val enabled: Property<Boolean> = objects.property(Boolean::class.java).convention(false)
}

open class DesignDomainEventHandlerGeneratorExtension @Inject constructor(objects: ObjectFactory) {
    val enabled: Property<Boolean> = objects.property(Boolean::class.java).convention(false)
}

open class AggregateGeneratorExtension @Inject constructor(objects: ObjectFactory) {
    val enabled: Property<Boolean> = objects.property(Boolean::class.java).convention(false)
    val unsupportedTablePolicy: Property<String> = objects.property(String::class.java).convention("FAIL")
}

open class DrawingBoardGeneratorExtension @Inject constructor(objects: ObjectFactory) {
    val enabled: Property<Boolean> = objects.property(Boolean::class.java).convention(false)
    val outputDir: Property<String> = objects.property(String::class.java)
}

open class FlowGeneratorExtension @Inject constructor(objects: ObjectFactory) {
    val enabled: Property<Boolean> = objects.property(Boolean::class.java).convention(false)
    val outputDir: Property<String> = objects.property(String::class.java)
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

    fun moduleRoot(role: String): ConfigurableFileCollection =
        moduleRoot.getOrPut(role) { objects.fileCollection() }

    fun modulePackage(role: String): ConfigurableFileCollection =
        modulePackage.getOrPut(role) { objects.fileCollection() }

    fun bindings(project: Project): List<BootstrapSlotBinding> = buildList {
        addBindings(project, BootstrapSlotKind.ROOT, null, root)
        addBindings(project, BootstrapSlotKind.BUILD_LOGIC, null, buildLogic)
        moduleRoot.forEach { (role, sourceDirs) ->
            addBindings(project, BootstrapSlotKind.MODULE_ROOT, role, sourceDirs)
        }
        modulePackage.forEach { (role, sourceDirs) ->
            addBindings(project, BootstrapSlotKind.MODULE_PACKAGE, role, sourceDirs)
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

internal val Cap4kExtension.drawingBoardOutputDir: Property<String>
    get() = generators.drawingBoard.outputDir

internal val Cap4kExtension.flowOutputDir: Property<String>
    get() = generators.flow.outputDir

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
