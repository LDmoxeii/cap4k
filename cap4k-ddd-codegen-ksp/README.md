# KSP Processor - 注解元数据提取器

## 概述

这是一个基于 **KSP (Kotlin Symbol Processing)** 实现的注解处理器，用于在编译时扫描 Domain 层的注解并生成元数据 JSON 文件。

## 架构设计

### 核心组件

```
┌─────────────────────────────────────────────────────────────┐
│                    KSP 框架层                                │
│  ┌───────────────────────────────────────────────────────┐  │
│  │  Java SPI 机制: 发现并加载 Provider                    │  │
│  └───────────────────────────────────────────────────────┘  │
└─────────────────────────────────────────────────────────────┘
                            ↓
┌─────────────────────────────────────────────────────────────┐
│         AnnotationProcessorProvider                         │
│  ┌───────────────────────────────────────────────────────┐  │
│  │  SymbolProcessorProvider 实现                          │  │
│  │  - create(): 创建 AnnotationProcessor 实例             │  │
│  │  - 注入 CodeGenerator, Logger, Options                 │  │
│  └───────────────────────────────────────────────────────┘  │
└─────────────────────────────────────────────────────────────┘
                            ↓
┌─────────────────────────────────────────────────────────────┐
│            AnnotationProcessor                              │
│  ┌───────────────────────────────────────────────────────┐  │
│  │  SymbolProcessor 实现                                   │  │
│  │                                                         │  │
│  │  ① process(resolver): 多轮处理                         │  │
│  │     - processAggregateAnnotations()                    │  │
│  │     - processEntityAnnotations()                       │  │
│  │                                                         │  │
│  │  ② finish(): 最终生成                                  │  │
│  │     - generateMetadata()                               │  │
│  └───────────────────────────────────────────────────────┘  │
└─────────────────────────────────────────────────────────────┘
                            ↓
┌────────────────────────────────────────────┐
│                    元数据模型                │
│  ┌─────────────────┐   ┌──────────────┐    │
│  │ AggregateMetadata│  │FieldMetadata │   │
│  └─────────────────┘   └──────────────┘    │
└────────────────────────────────────────────┘
                            ↓
┌─────────────────────────────────────────────────────────────┐
│                   生成产物                                   │
│  build/generated/ksp/main/resources/metadata/               │
│    ├── aggregates.json                                      │
└─────────────────────────────────────────────────────────────┘
```

## 实现细节

### 注解扫描

#### @Aggregate

```kotlin
annotation class Aggregate(
    /**
     * 所属聚合
     *
     * @return
     */
    val aggregate: String = "",
    /**
     * 元素名称
     *
     * @return
     */
    val name: String = "",
    /**
     * 是否聚合根
     * @return
     */
    val root: Boolean = false,
    /**
     * 元素类型
     * entity、value-object、repository、factory、factory-payload、domain-event、specification、enum
     *
     * @return
     */
    val type: String = "",
    /**
     * 实体描述
     *
     * @return
     */
    val description: String = "",
    /**
     * 关联元素名称
     *
     * @return
     */
    vararg val relevant: String = []
) {
    companion object {
        const val TYPE_ENTITY: String = "entity"
        const val TYPE_VALUE_OBJECT: String = "value-object"
        const val TYPE_ENUM: String = "enum"
        const val TYPE_REPOSITORY: String = "repository"
        const val TYPE_DOMAIN_EVENT: String = "domain-event"
        const val TYPE_FACTORY: String = "factory"
        const val TYPE_FACTORY_PAYLOAD: String = "factory-payload"
        const val TYPE_SPECIFICATION: String = "specification"
    }
}
```

#### 处理 @Aggregate 注解

```kotlin
private fun processAggregateAnnotations(resolver: Resolver) {
    // 1. 查找所有带 @Aggregate 注解的符号
    val aggregateSymbols = resolver.getSymbolsWithAnnotation(
        "com.only4.cap4k.ddd.core.domain.aggregate.annotation.Aggregate"
    )

    // 2. 过滤出类声明
    aggregateSymbols.filterIsInstance<KSClassDeclaration>().forEach { classDecl ->
        // 3. 获取注解实例
        val annotation = classDecl.annotations.first {
            it.shortName.asString() == "Aggregate"
        }

        // 4. 读取注解参数
        val aggregateName = annotation.arguments
            .find { it.name?.asString() == "aggregate" }
            ?.value as? String
            ?: classDecl.simpleName.asString()

        val isRoot = annotation.arguments
            .find { it.name?.asString() == "root" }
            ?.value as? Boolean
            ?: false

        // 5. 解析标识类型
        val identityType = resolveIdentityType(classDecl)

        // 6. 提取字段信息
        val fields = extractFields(classDecl)

        // 7. 构建元数据
        val metadata = AggregateMetadata(
            aggregateName = aggregateName,
            className = classDecl.simpleName.asString(),
            qualifiedName = classDecl.qualifiedName?.asString() ?: "",
            packageName = classDecl.packageName.asString(),
            isAggregateRoot = isRoot,
            identityType = identityType,
            fields = fields
        )

        // 8. 添加到集合
        aggregates.add(metadata)

        logger.info("Processed aggregate: $aggregateName (root=$isRoot)")
    }
}
```

#### 字段提取

```kotlin
private fun extractFields(classDecl: KSClassDeclaration): List<FieldMetadata> {
    return classDecl.getAllProperties().map { property ->
        // 检查是否是 ID 字段
        val isId = property.annotations.any {
            it.shortName.asString() == "Id"
        }

        // 解析字段类型
        val type = property.type.resolve()
        val typeName = type.declaration.qualifiedName?.asString()
            ?: type.toString()

        // 提取所有注解名称
        val annotations = property.annotations.map {
            it.shortName.asString()
        }.toList()

        FieldMetadata(
            name = property.simpleName.asString(),
            type = typeName,
            isId = isId,
            isNullable = type.isMarkedNullable,
            annotations = annotations
        )
    }.toList()
}
```

#### ID 类型解析

```kotlin
private fun resolveIdentityType(classDecl: KSClassDeclaration): String {
    // 查找所有带 @Id 注解的字段
    val idFields = classDecl.getAllProperties()
        .filter { property ->
            property.annotations.any {
                it.shortName.asString() == "Id"
            }
        }
        .toList()

    return when {
        // 没有 ID 字段 → 默认 Long
        idFields.isEmpty() -> "Long"

        // 单一 ID 字段 → 使用字段类型
        idFields.size == 1 -> {
            val type = idFields.first().type.resolve()
            type.declaration.qualifiedName?.asString() ?: "Long"
        }

        // 复合主键 → 使用内部类名称
        else -> "${classDecl.simpleName.asString()}.PK"
    }
}
```

### 元数据生成

```kotlin
override fun finish() {
    // 防止重复生成（虽然 finish() 只调用一次，但加个保险）
    if (!metadataGenerated) {
        generateMetadata()
        metadataGenerated = true
    }
}

private fun generateMetadata() {
    val gson = GsonBuilder().setPrettyPrinting().create()

    // 生成 aggregates.json
    if (aggregates.isNotEmpty()) {
        val aggregatesFile = codeGenerator.createNewFile(
            Dependencies(false),
            "metadata",
            "aggregates",
            "json"
        )
        aggregatesFile.write(gson.toJson(aggregates).toByteArray())
        aggregatesFile.close()
    }

    // 生成 entities.json
    if (entities.isNotEmpty()) {
        val entitiesFile = codeGenerator.createNewFile(
            Dependencies(false),
            "metadata",
            "entities",
            "json"
        )
        entitiesFile.write(gson.toJson(entities).toByteArray())
        entitiesFile.close()
    }

    logger.info("Generated metadata: ${aggregates.size} aggregates, ${entities.size} entities")
}
```

## 元数据模型

### AggregateMetadata

```kotlin
data class AggregateMetadata(
    val aggregateName: String,        // 聚合名称
    val className: String,            // 类名
    val qualifiedName: String,        // 全限定名
    val packageName: String,          // 包名
    val isAggregateRoot: Boolean,     // 是否是聚合根
    val isEntity: Boolean,            // 是否是实体
    val isValueObject: Boolean,       // 是否是值对象
    val identityType: String,         // 标识类型（ID 类型）
    val fields: List<FieldMetadata>   // 字段列表
)
```

### FieldMetadata

```kotlin
data class FieldMetadata(
    val name: String,                 // 字段名
    val type: String,                 // 字段类型
    val isId: Boolean,                // 是否是 ID
    val isNullable: Boolean,          // 是否可空
    val annotations: List<String>     // 注解列表
)
```

## 生成的 JSON 示例

### aggregates.json

```json
[
  {
    "aggregateName": "Category",
    "aggregateRoot": {
      "className": "Category",
      "qualifiedName": "com.example.demo.domain.aggregates.category.Category",
      "packageName": "com.example.demo.domain.aggregates.category",
      "type": "entity",
      "isAggregateRoot": true,
      "identityType": "kotlin.Long",
      "fields": [
        {
          "name": "id",
          "type": "kotlin.Long",
          "isId": true,
          "isNullable": false,
          "annotations": [
            "Id",
            "GeneratedValue",
            "GenericGenerator",
            "Column"
          ]
        },
        {
          "name": "parentId",
          "type": "kotlin.Long",
          "isId": false,
          "isNullable": false,
          "annotations": [
            "Column"
          ]
        },
        {
          "name": "nodePath",
          "type": "kotlin.String",
          "isId": false,
          "isNullable": false,
          "annotations": [
            "Column"
          ]
        },
        {
          "name": "sort",
          "type": "kotlin.Byte",
          "isId": false,
          "isNullable": false,
          "annotations": [
            "Column"
          ]
        },
        {
          "name": "code",
          "type": "kotlin.String",
          "isId": false,
          "isNullable": false,
          "annotations": [
            "Column"
          ]
        },
        {
          "name": "name",
          "type": "kotlin.String",
          "isId": false,
          "isNullable": false,
          "annotations": [
            "Column"
          ]
        },
        {
          "name": "icon",
          "type": "kotlin.String",
          "isId": false,
          "isNullable": true,
          "annotations": [
            "Column"
          ]
        },
        {
          "name": "background",
          "type": "kotlin.String",
          "isId": false,
          "isNullable": true,
          "annotations": [
            "Column"
          ]
        },
        {
          "name": "createUserId",
          "type": "kotlin.Long",
          "isId": false,
          "isNullable": true,
          "annotations": [
            "Column"
          ]
        },
        {
          "name": "createBy",
          "type": "kotlin.String",
          "isId": false,
          "isNullable": true,
          "annotations": [
            "Column"
          ]
        },
        {
          "name": "createTime",
          "type": "kotlin.Long",
          "isId": false,
          "isNullable": true,
          "annotations": [
            "Column"
          ]
        },
        {
          "name": "updateUserId",
          "type": "kotlin.Long",
          "isId": false,
          "isNullable": true,
          "annotations": [
            "Column"
          ]
        },
        {
          "name": "updateBy",
          "type": "kotlin.String",
          "isId": false,
          "isNullable": true,
          "annotations": [
            "Column"
          ]
        },
        {
          "name": "updateTime",
          "type": "kotlin.Long",
          "isId": false,
          "isNullable": true,
          "annotations": [
            "Column"
          ]
        },
        {
          "name": "deleted",
          "type": "kotlin.Boolean",
          "isId": false,
          "isNullable": false,
          "annotations": [
            "Column"
          ]
        }
      ]
    },
    "entities": [],
    "valueObjects": [],
    "enums": [],
    "factory": {
      "className": "CategoryFactory",
      "qualifiedName": "com.example.demo.domain.aggregates.category.factory.CategoryFactory",
      "packageName": "com.example.demo.domain.aggregates.category.factory",
      "type": "factory",
      "isAggregateRoot": false,
      "identityType": "Long",
      "fields": []
    },
    "factoryPayload": {
      "className": "Payload",
      "qualifiedName": "com.example.demo.domain.aggregates.category.factory.CategoryFactory.Payload",
      "packageName": "com.example.demo.domain.aggregates.category.factory",
      "type": "factory-payload",
      "isAggregateRoot": false,
      "identityType": "Long",
      "fields": [
        {
          "name": "name",
          "type": "kotlin.String",
          "isId": false,
          "isNullable": false,
          "annotations": []
        }
      ]
    },
    "specification": {
      "className": "CategorySpecification",
      "qualifiedName": "com.example.demo.domain.aggregates.category.specs.CategorySpecification",
      "packageName": "com.example.demo.domain.aggregates.category.specs",
      "type": "specification",
      "isAggregateRoot": false,
      "identityType": "Long",
      "fields": []
    },
    "domainEvents": []
  }
]
```

## 参考资源

- [KSP 官方文档](https://kotlinlang.org/docs/ksp-overview.html)
- [KSP GitHub](https://github.com/google/ksp)
- [KSP 示例项目](https://github.com/google/ksp/tree/main/examples)
