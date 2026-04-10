package com.only4.cap4k.plugin.pipeline.gradle

import org.gradle.api.file.ConfigurableFileCollection
import org.gradle.api.model.ObjectFactory
import org.gradle.api.provider.ListProperty
import org.gradle.api.provider.Property
import javax.inject.Inject

open class PipelineExtension @Inject constructor(objects: ObjectFactory) {
    val basePackage: Property<String> = objects.property(String::class.java)
    val applicationModulePath: Property<String> = objects.property(String::class.java)
    val domainModulePath: Property<String> = objects.property(String::class.java)
    val adapterModulePath: Property<String> = objects.property(String::class.java)
    val designFiles: ConfigurableFileCollection = objects.fileCollection()
    val kspMetadataDir: Property<String> = objects.property(String::class.java)
    val dbUrl: Property<String> = objects.property(String::class.java)
    val dbUsername: Property<String> = objects.property(String::class.java)
    val dbPassword: Property<String> = objects.property(String::class.java)
    val dbSchema: Property<String> = objects.property(String::class.java)
    val dbIncludeTables: ListProperty<String> = objects.listProperty(String::class.java)
    val dbExcludeTables: ListProperty<String> = objects.listProperty(String::class.java)
    val templateOverrideDir: Property<String> = objects.property(String::class.java)
}
