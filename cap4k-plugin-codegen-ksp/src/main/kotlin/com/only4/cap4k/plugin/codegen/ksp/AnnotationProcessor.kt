package com.only4.cap4k.plugin.codegen.ksp

import com.google.devtools.ksp.processing.*
import com.google.devtools.ksp.symbol.KSAnnotated
import com.google.devtools.ksp.symbol.KSClassDeclaration
import com.google.devtools.ksp.symbol.KSFile
import com.google.gson.GsonBuilder
import com.only4.cap4k.plugin.codegen.ksp.models.AggregateMetadata
import com.only4.cap4k.plugin.codegen.ksp.models.ElementMetadata
import com.only4.cap4k.plugin.codegen.ksp.models.ElementMetadata.Companion.ElementType
import com.only4.cap4k.plugin.codegen.ksp.models.FieldMetadata

/**
 * KSP 注解处理器（两阶段处理）
 *
 * Phase 1: 扫描所有 @Aggregate 注解的类，构建 AnnotatedElement（包含 aggregateName）
 * Phase 2: 按 aggregateName 分组，构建以聚合根为中心的 AggregateMetadata
 */
class AnnotationProcessor(
    private val codeGenerator: CodeGenerator,
    private val logger: KSPLogger,
    private val options: Map<String, String>
) : SymbolProcessor {

    /**
     * 带注解信息的元素（临时结构）
     */
    private data class AnnotatedElement(
        val aggregateName: String,
        val element: ElementMetadata,
        val source: KSFile?
    )

    // Phase 1: 扁平的元素列表（带 aggregateName）
    private val annotatedElements = mutableListOf<AnnotatedElement>()

    // 聚合 -> 源文件集合（用于增量编译依赖关联）
    private val aggregateSources: MutableMap<String, MutableSet<KSFile>> = mutableMapOf()

    // Phase 2: 以聚合为单位的元数据
    private val aggregates = mutableListOf<AggregateMetadata>()

    @Volatile
    private var metadataGenerated = false

    override fun process(resolver: Resolver): List<KSAnnotated> {
        // Phase 1: 扫描所有 @Aggregate 注解的类
        scanAggregateElements(resolver)

        return emptyList()
    }

    override fun finish() {
        if (!metadataGenerated) {
            // Phase 2: 构建聚合层次结构
            buildAggregateHierarchy()

            // 生成 JSON
            generateMetadata()
            metadataGenerated = true
        }
    }

    /**
     * Phase 1: 扫描所有 @Aggregate 注解的类，构建 ElementMetadata
     */
    private fun scanAggregateElements(resolver: Resolver) {
        val aggregateSymbols = resolver.getSymbolsWithAnnotation(
            "com.only4.cap4k.ddd.core.domain.aggregate.annotation.Aggregate"
        )

        aggregateSymbols.filterIsInstance<KSClassDeclaration>().forEach { classDecl ->
            val annotation = classDecl.annotations.first {
                it.shortName.asString() == "Aggregate"
            }

            // 提取注解属性
            val aggregateName = annotation.arguments
                .find { it.name?.asString() == "aggregate" }
                ?.value as? String
                ?: classDecl.simpleName.asString()

            val isRoot = annotation.arguments
                .find { it.name?.asString() == "root" }
                ?.value as? Boolean
                ?: false

            val typeRaw = annotation.arguments
                .find { it.name?.asString() == "type" }
                ?.value as? String
                ?: ""

            // 规范化类型（去掉 "Aggregate.TYPE_" 前缀）
            val type = normalizeType(typeRaw)

            val identityType = resolveIdentityType(classDecl)

            val element = ElementMetadata(
                className = resolveClassName(classDecl),
                qualifiedName = classDecl.qualifiedName?.asString() ?: "",
                packageName = classDecl.packageName.asString(),
                type = type,
                isAggregateRoot = isRoot,
                identityType = identityType,
                fields = extractFields(classDecl)
            )

            // 存储为 AnnotatedElement
            val src = classDecl.containingFile
            annotatedElements.add(AnnotatedElement(aggregateName, element, src))
            if (src != null) {
                aggregateSources.computeIfAbsent(aggregateName) { mutableSetOf() }.add(src)
            }

            logger.info("Scanned element: aggregate=$aggregateName, class=${element.className}, type=$type, root=$isRoot")
        }
    }

    /**
     * 规范化类型字符串
     * 将 "Aggregate.TYPE_ENTITY" 转换为 "entity"
     */
    private fun normalizeType(typeRaw: String): String {
        return when {
            typeRaw.endsWith("TYPE_ENTITY") || typeRaw == ElementType.ENTITY -> ElementType.ENTITY
            typeRaw.endsWith("TYPE_VALUE_OBJECT") || typeRaw == ElementType.VALUE_OBJECT -> ElementType.VALUE_OBJECT
            typeRaw.endsWith("TYPE_ENUM") || typeRaw == ElementType.ENUM -> ElementType.ENUM
            typeRaw.endsWith("TYPE_REPOSITORY") || typeRaw == ElementType.REPOSITORY -> ElementType.REPOSITORY
            typeRaw.endsWith("TYPE_DOMAIN_EVENT") || typeRaw == ElementType.DOMAIN_EVENT -> ElementType.DOMAIN_EVENT
            typeRaw.endsWith("TYPE_FACTORY") || typeRaw == ElementType.FACTORY -> ElementType.FACTORY
            typeRaw.endsWith("TYPE_FACTORY_PAYLOAD") || typeRaw == ElementType.FACTORY_PAYLOAD -> ElementType.FACTORY_PAYLOAD
            typeRaw.endsWith("TYPE_SPECIFICATION") || typeRaw == ElementType.SPECIFICATION -> ElementType.SPECIFICATION
            else -> {
                logger.warn("Unknown type: $typeRaw, defaulting to entity")
                ElementType.ENTITY
            }
        }
    }

    /**
     * Phase 2: 按 aggregateName 分组，构建聚合层次结构
     */
    private fun buildAggregateHierarchy() {
        // 按 aggregateName 分组
        val elementsByAggregate = annotatedElements.groupBy { it.aggregateName }

        elementsByAggregate.forEach { (aggregateName, annotatedElementList) ->
            val elements = annotatedElementList.map { it.element }

            // 查找聚合根（root=true 且 type=entity）
            val aggregateRootCandidates = elements.filter {
                it.isAggregateRoot && it.type == ElementType.ENTITY
            }

            if (aggregateRootCandidates.isEmpty()) {
                logger.warn("Aggregate '$aggregateName' has no aggregate root (root=true, type=entity), skipping")
                return@forEach
            }

            if (aggregateRootCandidates.size > 1) {
                logger.error("Aggregate '$aggregateName' has multiple aggregate roots: ${aggregateRootCandidates.map { it.className }}, using first")
            }

            val aggregateRoot = aggregateRootCandidates.first()

            // 按类型分类其他元素
            val entities = elements.filter {
                it.type == ElementType.ENTITY && !it.isAggregateRoot
            }

            val valueObjects = elements.filter {
                it.type == ElementType.VALUE_OBJECT
            }

            val enums = elements.filter {
                it.type == ElementType.ENUM
            }

            // 构建 AggregateMetadata
            val aggregateMetadata = AggregateMetadata(
                aggregateName = aggregateName,
                aggregateRoot = aggregateRoot,
                entities = entities,
                valueObjects = valueObjects,
                enums = enums
            )

            aggregates.add(aggregateMetadata)

            logger.info(
                "Built aggregate: $aggregateName " +
                        "(root=${aggregateRoot.className}, " +
                        "entities=${entities.size}, " +
                        "valueObjects=${valueObjects.size}, " +
                        "enums=${enums.size})"
            )
        }
    }

    /**
     * 解析类名（处理嵌套类）
     *
     * 对于嵌套类，返回相对于包的完整路径，例如：
     * - 普通类: "Category"
     * - 嵌套类: "CategoryFactory.Payload"
     */
    private fun resolveClassName(classDecl: KSClassDeclaration): String {
        val qualifiedName = classDecl.qualifiedName?.asString() ?: return classDecl.simpleName.asString()
        val packageName = classDecl.packageName.asString()

        // 如果没有包名，直接返回简单名
        if (packageName.isEmpty()) {
            return classDecl.simpleName.asString()
        }

        // 去掉包名前缀，得到类的相对路径
        // 例如: "com.example.CategoryFactory.Payload" - "com.example" = "CategoryFactory.Payload"
        val relativeClassName = if (qualifiedName.startsWith("$packageName.")) {
            qualifiedName.substring(packageName.length + 1)
        } else {
            classDecl.simpleName.asString()
        }

        return relativeClassName
    }

    private fun resolveIdentityType(classDecl: KSClassDeclaration): String {
        val idFields = classDecl.getAllProperties()
            .filter { property ->
                property.annotations.any {
                    it.shortName.asString() == "Id"
                }
            }
            .toList()

        return when {
            idFields.isEmpty() -> "Long"
            idFields.size == 1 -> {
                val type = idFields.first().type.resolve()
                type.declaration.qualifiedName?.asString() ?: "Long"
            }

            else -> "${classDecl.simpleName.asString()}.PK"
        }
    }

    private fun extractFields(classDecl: KSClassDeclaration): List<FieldMetadata> {
        return classDecl.getAllProperties().map { property ->
            val isId = property.annotations.any {
                it.shortName.asString() == "Id"
            }

            val type = property.type.resolve()
            val typeName = type.declaration.qualifiedName?.asString()
                ?: type.toString()

            FieldMetadata(
                name = property.simpleName.asString(),
                type = typeName,
                isId = isId,
                isNullable = type.isMarkedNullable,
                annotations = property.annotations.map {
                    it.shortName.asString()
                }.toList()
            )
        }.toList()
    }

    private fun generateMetadata() {
        val gson = GsonBuilder().setPrettyPrinting().create()

        if (aggregates.isEmpty()) {
            logger.warn("No aggregates found, skipping JSON generation")
            return
        }

        // 1) 为每个聚合生成独立的元数据文件，并将其与对应源文件进行关联
        aggregates.forEach { agg ->
            val safeName = sanitizeFileName(agg.aggregateName)
            val depsSources = aggregateSources[agg.aggregateName]?.toTypedArray() ?: emptyArray()
            val file = codeGenerator.createNewFile(
                // 声明为 isolating（依赖于该聚合相关的源文件集合）
                Dependencies(false, *depsSources),
                "metadata",
                "aggregate-$safeName",
                "json"
            )
            file.write(gson.toJson(agg).toByteArray())
            file.close()
            logger.info("Generated aggregate metadata file: aggregate-$safeName.json")
        }

        // 2) 生成一个聚合索引文件（aggregating output），关联到所有已扫描到的源文件
        //    这样任意一个输入变更都会让 KSP 重新处理所有已关联文件，确保本处理器能看到完整的符号集。
        val allSources: Array<KSFile> = aggregateSources.values
            .flatten()
            .toSet()
            .toTypedArray()

        if (allSources.isNotEmpty()) {
            val indexOut = codeGenerator.createNewFile(
                Dependencies(true, *allSources),
                "metadata",
                "aggregates-index",
                "json"
            )
            val indexPayload = mapOf(
                "aggregates" to aggregates.map { it.aggregateName }.sorted(),
                "count" to aggregates.size
            )
            indexOut.write(gson.toJson(indexPayload).toByteArray())
            indexOut.close()
            logger.info("Generated aggregates-index.json (aggregating)")
        }
    }

    private fun sanitizeFileName(name: String): String {
        // 仅保留字母、数字、下划线和短横线，其他替换为下划线
        return name.replace(Regex("[^A-Za-z0-9_-]"), "_")
    }
}
