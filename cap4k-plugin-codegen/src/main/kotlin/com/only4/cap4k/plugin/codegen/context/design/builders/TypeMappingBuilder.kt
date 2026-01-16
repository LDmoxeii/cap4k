package com.only4.cap4k.plugin.codegen.context.design.builders

import com.only4.cap4k.plugin.codegen.context.ContextBuilder
import com.only4.cap4k.plugin.codegen.context.design.MutableDesignContext
import com.only4.cap4k.plugin.codegen.ksp.models.ElementMetadata

class TypeMappingBuilder : ContextBuilder<MutableDesignContext> {

    override val order: Int = 18

    override fun build(context: MutableDesignContext) {
        context.aggregateMap.values.forEach { aggregateInfo ->
            registerElement(aggregateInfo.aggregateRoot, context)

            recordIdentityType(aggregateInfo.identityType, aggregateInfo.aggregateRoot, context)

            aggregateInfo.entities.forEach { entity ->
                registerElement(entity, context)
            }

            aggregateInfo.valueObjects.forEach { valueObject ->
                registerElement(valueObject, context)
            }

            aggregateInfo.enums.forEach { enum ->
                registerElement(enum, context)
            }

            // 精简后的元信息不再包含 repository/factory/specification/domainEvents
        }
    }

    private fun registerElement(element: ElementMetadata, context: MutableDesignContext) {
        context.typeMapping[element.className] = element.qualifiedName
    }

    private fun recordIdentityType(
        identityType: String,
        rootElement: ElementMetadata,
        context: MutableDesignContext,
    ) {
        when {
            // 复合主键 (User.PK)
            identityType.contains(".") -> {
                val idTypeName = identityType.substringAfter(".")  // "PK"
                val fullIdType = "${rootElement.qualifiedName}.$idTypeName"
                context.typeMapping[identityType] = fullIdType  // "User.PK" -> "com.example.User.PK"
                context.typeMapping[idTypeName] = fullIdType     // "PK" -> "com.example.User.PK"
            }

            // 原始类型 (Long, String, Int, UUID)
            identityType in listOf("Long", "String", "Int", "UUID") -> {
                // 不需要记录到 typeMapping
            }

            // 自定义 ID 类型 (UserId)
            else -> {
                // 查找是否在 aggregateMap 中
                val idElement = context.aggregateMap.values
                    .flatMap { listOfNotNull(
                        it.aggregateRoot,
                        *it.entities.toTypedArray(),
                        *it.valueObjects.toTypedArray()
                    ) }
                    .find { it.className == identityType }

                if (idElement != null) {
                    context.typeMapping[identityType] = idElement.qualifiedName
                } else {
                    // 假设在聚合根同一包下
                    val assumedFullName = "${rootElement.packageName}.$identityType"
                    context.typeMapping[identityType] = assumedFullName
                }
            }
        }
    }
}
