package com.only4.cap4k.plugin.pipeline.gradle

import org.gradle.api.file.ConfigurableFileCollection
import org.gradle.api.model.ObjectFactory
import org.gradle.api.provider.ListProperty
import org.gradle.api.provider.Property
import javax.inject.Inject

open class Cap4kExtension @Inject constructor(objects: ObjectFactory) {
    val project: Cap4kProjectExtension = objects.newInstance(Cap4kProjectExtension::class.java)
    val types: Cap4kTypesExtension = objects.newInstance(Cap4kTypesExtension::class.java)
    val sources: Cap4kSourcesExtension = objects.newInstance(Cap4kSourcesExtension::class.java)
    val generators: Cap4kGeneratorsExtension = objects.newInstance(Cap4kGeneratorsExtension::class.java)
    val templates: Cap4kTemplatesExtension = objects.newInstance(Cap4kTemplatesExtension::class.java)

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

open class IrAnalysisSourceExtension @Inject constructor(objects: ObjectFactory) {
    val enabled: Property<Boolean> = objects.property(Boolean::class.java).convention(false)
    val inputDirs: ConfigurableFileCollection = objects.fileCollection()
}

open class Cap4kGeneratorsExtension @Inject constructor(objects: ObjectFactory) {
    val design: DesignGeneratorExtension = objects.newInstance(DesignGeneratorExtension::class.java)
    val aggregate: AggregateGeneratorExtension = objects.newInstance(AggregateGeneratorExtension::class.java)
    val drawingBoard: DrawingBoardGeneratorExtension = objects.newInstance(DrawingBoardGeneratorExtension::class.java)
    val flow: FlowGeneratorExtension = objects.newInstance(FlowGeneratorExtension::class.java)

    fun design(block: DesignGeneratorExtension.() -> Unit) {
        design.block()
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
