# KSP CodeGenerator API 完整参考

## 概述

`CodeGenerator` 是 KSP 中负责文件生成的核心 API。它提供了创建和写入源代码文件（Kotlin/Java）和资源文件的能力，并自动处理文件路径、增量编译依赖追踪等问题。

## 核心接口

```kotlin
interface CodeGenerator {
    /**
     * 创建新文件
     */
    fun createNewFile(
        dependencies: Dependencies,
        packageName: String,
        fileName: String,
        extensionName: String = "kt"
    ): OutputStream

    /**
     * 关联源文件（用于增量编译）
     */
    fun associate(
        sources: List<KSFile>,
        packageName: String,
        fileName: String,
        extensionName: String = "kt"
    )

    /**
     * 获取生成文件的根目录
     */
    val generatedFile: Collection<File>
}
```

## 1. createNewFile() 方法详解

### 方法签名

```kotlin
fun createNewFile(
    dependencies: Dependencies,      // 依赖追踪
    packageName: String,            // 包名
    fileName: String,               // 文件名（不含扩展名）
    extensionName: String = "kt"    // 文件扩展名
): OutputStream
```

### 参数说明

#### dependencies: Dependencies

**作用：** 追踪生成文件与源文件的依赖关系，用于增量编译

**类型定义：**

```kotlin
class Dependencies(
    val aggregating: Boolean,           // 是否是聚合文件
    vararg val sources: KSFile          // 依赖的源文件列表
) {
    companion object {
        // 不追踪任何依赖（每次都重新生成）
        val ALL_FILES = Dependencies(aggregating = true)
    }
}
```

**三种依赖模式：**

| 模式 | 构造方式 | 含义 | 增量编译行为 | 适用场景 |
|------|---------|------|-------------|---------|
| **独立文件** | `Dependencies(false)` | 不依赖任何源文件 | 只在首次生成，后续不变 | 静态配置文件、常量类 |
| **关联文件** | `Dependencies(false, file1, file2)` | 依赖特定源文件 | 任一源文件改变就重新生成 | 一对一代码生成（DTO、Mapper） |
| **聚合文件** | `Dependencies(true)` 或 `Dependencies.ALL_FILES` | 聚合多个源文件 | 任何源文件改变都重新生成 | 索引文件、元数据汇总 |

**示例对比：**

```kotlin
// 场景 1: 生成静态配置类（永不改变）
val configFile = codeGenerator.createNewFile(
    Dependencies(false),  // 不依赖任何源文件
    "com.example.config",
    "GeneratedConfig",
    "kt"
)

// 场景 2: 为单个实体生成 Mapper（一对一）
val mapperFile = codeGenerator.createNewFile(
    Dependencies(false, userEntityFile),  // 只依赖 User.kt
    "com.example.mapper",
    "UserMapper",
    "kt"
)
// 只有 User.kt 改变时才重新生成 UserMapper.kt

// 场景 3: 生成所有实体的索引（聚合）
val indexFile = codeGenerator.createNewFile(
    Dependencies(true),  // 依赖所有处理过的文件
    "com.example",
    "EntityIndex",
    "kt"
)
// 任何实体改变都会重新生成索引
```

#### packageName: String

**作用：** 指定生成文件的包名

**规则：**
- Kotlin/Java 文件：会影响包声明和文件路径
- 资源文件：只影响目录结构，不影响文件内容

**示例：**

```kotlin
// 生成 Kotlin 源文件
codeGenerator.createNewFile(
    Dependencies(false),
    "com.example.domain",  // → build/.../com/example/domain/
    "User",
    "kt"
)
// 文件路径: build/generated/ksp/main/kotlin/com/example/domain/User.kt
// 文件内容会自动添加: package com.example.domain

// 生成资源文件
codeGenerator.createNewFile(
    Dependencies(false),
    "metadata",  // → build/.../metadata/
    "aggregates",
    "json"
)
// 文件路径: build/generated/ksp/main/resources/metadata/aggregates.json
```

**特殊情况：**

```kotlin
// 空包名 - 生成在根目录
codeGenerator.createNewFile(
    Dependencies(false),
    "",  // 空包名
    "GlobalConfig",
    "kt"
)
// 文件路径: build/generated/ksp/main/kotlin/GlobalConfig.kt

// 嵌套包名
codeGenerator.createNewFile(
    Dependencies(false),
    "com.example.domain.aggregates.user",
    "User",
    "kt"
)
// 文件路径: build/.../com/example/domain/aggregates/user/User.kt
```

#### fileName: String

**作用：** 指定文件名（不含扩展名）

**规则：**
- 不要包含扩展名（扩展名由 `extensionName` 参数指定）
- 不要包含路径分隔符（`/` 或 `\`）
- 建议遵循目标语言的命名规范

**示例：**

```kotlin
// ✅ 正确
codeGenerator.createNewFile(deps, "com.example", "UserMapper", "kt")
codeGenerator.createNewFile(deps, "com.example", "user-config", "json")
codeGenerator.createNewFile(deps, "com.example", "README", "md")

// ❌ 错误
codeGenerator.createNewFile(deps, "com.example", "UserMapper.kt", "kt")  // 不要包含扩展名
codeGenerator.createNewFile(deps, "com.example", "user/Mapper", "kt")   // 不要包含路径
```

#### extensionName: String

**作用：** 指定文件扩展名，决定文件类型和生成路径

**常用扩展名：**

| 扩展名 | 文件类型 | 生成路径 | 用途 |
|-------|---------|---------|------|
| `kt` | Kotlin 源文件 | `kotlin/` | 生成 Kotlin 代码 |
| `java` | Java 源文件 | `java/` | 生成 Java 代码 |
| `json` | JSON 资源文件 | `resources/` | 元数据、配置 |
| `xml` | XML 资源文件 | `resources/` | 配置、资源 |
| `txt` | 文本资源文件 | `resources/` | 文档、日志 |
| `properties` | 属性文件 | `resources/` | 配置 |
| `md` | Markdown 文档 | `resources/` | 文档 |

**路径规则：**

```kotlin
// Kotlin 源文件
createNewFile(deps, "com.example", "User", "kt")
// → build/generated/ksp/main/kotlin/com/example/User.kt

// Java 源文件
createNewFile(deps, "com.example", "User", "java")
// → build/generated/ksp/main/java/com/example/User.java

// 资源文件（所有非 kt/java 扩展名）
createNewFile(deps, "metadata", "entities", "json")
// → build/generated/ksp/main/resources/metadata/entities.json

createNewFile(deps, "config", "app", "properties")
// → build/generated/ksp/main/resources/config/app.properties
```

### 返回值：OutputStream

**作用：** 用于写入文件内容的输出流

**使用模式：**

```kotlin
// 模式 1: 使用 use 自动关闭
val file = codeGenerator.createNewFile(deps, pkg, name, ext)
file.use { output ->
    output.write(content.toByteArray())
}

// 模式 2: 手动关闭
val file = codeGenerator.createNewFile(deps, pkg, name, ext)
file.write(content.toByteArray())
file.close()

// 模式 3: 使用 bufferedWriter（推荐用于大文件）
val file = codeGenerator.createNewFile(deps, pkg, name, ext)
file.bufferedWriter().use { writer ->
    writer.write(content)
}
```

## 2. 生成文件的完整流程

### 基础流程

```kotlin
class MyProcessor(
    private val codeGenerator: CodeGenerator,
    private val logger: KSPLogger
) : SymbolProcessor {

    override fun finish() {
        // 1. 准备文件内容
        val content = buildContent()

        // 2. 创建文件
        val file = codeGenerator.createNewFile(
            dependencies = Dependencies(false),
            packageName = "com.example.generated",
            fileName = "GeneratedClass",
            extensionName = "kt"
        )

        // 3. 写入内容
        file.use { output ->
            output.write(content.toByteArray())
        }

        // 4. 日志输出
        logger.info("Generated: GeneratedClass.kt")
    }

    private fun buildContent(): String {
        return """
            package com.example.generated

            class GeneratedClass {
                fun hello() = "Hello, KSP!"
            }
        """.trimIndent()
    }
}
```

### 生成 Kotlin 代码

```kotlin
fun generateKotlinClass(
    codeGenerator: CodeGenerator,
    entity: KSClassDeclaration
) {
    val packageName = entity.packageName.asString()
    val className = "${entity.simpleName.asString()}Repository"

    // 构建 Kotlin 代码
    val content = buildString {
        appendLine("package $packageName")
        appendLine()
        appendLine("import org.springframework.data.jpa.repository.JpaRepository")
        appendLine()
        appendLine("interface $className : JpaRepository<${entity.simpleName.asString()}, Long> {")
        appendLine("    fun findByName(name: String): List<${entity.simpleName.asString()}>")
        appendLine("}")
    }

    // 生成文件
    val file = codeGenerator.createNewFile(
        Dependencies(false, entity.containingFile!!),
        packageName,
        className,
        "kt"
    )

    file.bufferedWriter().use { it.write(content) }
}
```

### 生成 Java 代码

```kotlin
fun generateJavaClass(
    codeGenerator: CodeGenerator,
    entity: KSClassDeclaration
) {
    val packageName = entity.packageName.asString()
    val className = "${entity.simpleName.asString()}Mapper"

    // 构建 Java 代码
    val content = """
        package $packageName;

        import org.mapstruct.Mapper;

        @Mapper(componentModel = "spring")
        public interface $className {
            ${entity.simpleName.asString()}DTO toDTO(${entity.simpleName.asString()} entity);
            ${entity.simpleName.asString()} toEntity(${entity.simpleName.asString()}DTO dto);
        }
    """.trimIndent()

    // 生成文件（注意扩展名是 "java"）
    val file = codeGenerator.createNewFile(
        Dependencies(false, entity.containingFile!!),
        packageName,
        className,
        "java"
    )

    file.bufferedWriter().use { it.write(content) }
}
```

### 生成 JSON 资源文件

```kotlin
fun generateMetadataJson(
    codeGenerator: CodeGenerator,
    entities: List<EntityMetadata>
) {
    // 使用 Gson 序列化
    val gson = GsonBuilder().setPrettyPrinting().create()
    val json = gson.toJson(entities)

    // 生成 JSON 文件
    val file = codeGenerator.createNewFile(
        Dependencies(false),  // 聚合文件，不依赖特定源文件
        "metadata",           // 包名（实际是目录）
        "entities",           // 文件名
        "json"                // 扩展名
    )

    file.bufferedWriter().use { it.write(json) }
}

// 生成路径: build/generated/ksp/main/resources/metadata/entities.json
```

### 生成配置文件

```kotlin
fun generatePropertiesFile(
    codeGenerator: CodeGenerator,
    config: Map<String, String>
) {
    // 构建 properties 内容
    val content = buildString {
        appendLine("# Auto-generated configuration")
        appendLine("# Generated at: ${java.time.LocalDateTime.now()}")
        appendLine()
        config.forEach { (key, value) ->
            appendLine("$key=$value")
        }
    }

    // 生成 properties 文件
    val file = codeGenerator.createNewFile(
        Dependencies(false),
        "config",
        "generated",
        "properties"
    )

    file.bufferedWriter().use { it.write(content) }
}
```

## 3. 高级用法

### 批量生成文件

```kotlin
class BatchGenerator(
    private val codeGenerator: CodeGenerator,
    private val logger: KSPLogger
) {
    fun generateRepositories(entities: List<KSClassDeclaration>) {
        entities.forEach { entity ->
            try {
                generateRepository(entity)
                logger.info("Generated repository for ${entity.simpleName.asString()}")
            } catch (e: Exception) {
                logger.error("Failed to generate repository for ${entity.simpleName.asString()}: ${e.message}")
            }
        }
    }

    private fun generateRepository(entity: KSClassDeclaration) {
        val packageName = "${entity.packageName.asString()}.repository"
        val className = "${entity.simpleName.asString()}Repository"

        val content = buildRepositoryContent(entity)

        val file = codeGenerator.createNewFile(
            Dependencies(false, entity.containingFile!!),
            packageName,
            className,
            "kt"
        )

        file.bufferedWriter().use { it.write(content) }
    }

    private fun buildRepositoryContent(entity: KSClassDeclaration): String {
        // 构建内容...
        return ""
    }
}
```

### 多文件生成（为一个源文件生成多个目标文件）

```kotlin
fun generateEntityFiles(
    codeGenerator: CodeGenerator,
    entity: KSClassDeclaration
) {
    val sourceFile = entity.containingFile!!

    // 1. 生成 Repository
    generateFile(
        codeGenerator,
        Dependencies(false, sourceFile),
        "${entity.packageName.asString()}.repository",
        "${entity.simpleName.asString()}Repository",
        "kt",
        buildRepositoryContent(entity)
    )

    // 2. 生成 Service
    generateFile(
        codeGenerator,
        Dependencies(false, sourceFile),
        "${entity.packageName.asString()}.service",
        "${entity.simpleName.asString()}Service",
        "kt",
        buildServiceContent(entity)
    )

    // 3. 生成 Controller
    generateFile(
        codeGenerator,
        Dependencies(false, sourceFile),
        "${entity.packageName.asString()}.controller",
        "${entity.simpleName.asString()}Controller",
        "kt",
        buildControllerContent(entity)
    )
}

private fun generateFile(
    codeGenerator: CodeGenerator,
    dependencies: Dependencies,
    packageName: String,
    fileName: String,
    extension: String,
    content: String
) {
    val file = codeGenerator.createNewFile(dependencies, packageName, fileName, extension)
    file.bufferedWriter().use { it.write(content) }
}
```

### 条件生成（避免重复生成）

```kotlin
class ConditionalGenerator(
    private val codeGenerator: CodeGenerator,
    private val logger: KSPLogger
) {
    private val generatedFiles = mutableSetOf<String>()

    fun generateIfNotExists(
        entity: KSClassDeclaration,
        suffix: String,
        contentBuilder: (KSClassDeclaration) -> String
    ) {
        val fileKey = "${entity.qualifiedName?.asString()}_$suffix"

        // 检查是否已生成
        if (fileKey in generatedFiles) {
            logger.warn("File already generated: $fileKey")
            return
        }

        // 生成文件
        val file = codeGenerator.createNewFile(
            Dependencies(false, entity.containingFile!!),
            entity.packageName.asString(),
            "${entity.simpleName.asString()}$suffix",
            "kt"
        )

        val content = contentBuilder(entity)
        file.bufferedWriter().use { it.write(content) }

        // 标记已生成
        generatedFiles.add(fileKey)
        logger.info("Generated: $fileKey")
    }
}

// 使用
val generator = ConditionalGenerator(codeGenerator, logger)
entities.forEach { entity ->
    generator.generateIfNotExists(entity, "Repository") { buildRepositoryContent(it) }
    generator.generateIfNotExists(entity, "Service") { buildServiceContent(it) }
}
```

### 模板化生成

```kotlin
class TemplateBasedGenerator(
    private val codeGenerator: CodeGenerator
) {
    private val templates = mapOf(
        "repository" to """
            package {{package}}

            import org.springframework.data.jpa.repository.JpaRepository

            interface {{className}}Repository : JpaRepository<{{className}}, {{idType}}> {
                {{#methods}}
                fun {{methodName}}({{parameters}}): {{returnType}}
                {{/methods}}
            }
        """.trimIndent(),

        "service" to """
            package {{package}}

            import org.springframework.stereotype.Service

            @Service
            class {{className}}Service(
                private val repository: {{className}}Repository
            ) {
                fun findAll() = repository.findAll()
                fun findById(id: {{idType}}) = repository.findById(id)
                fun save(entity: {{className}}) = repository.save(entity)
            }
        """.trimIndent()
    )

    fun generate(
        templateName: String,
        entity: KSClassDeclaration,
        context: Map<String, Any>
    ) {
        val template = templates[templateName]
            ?: throw IllegalArgumentException("Template not found: $templateName")

        // 简单的模板替换（实际项目中应使用 Velocity、Freemarker 等）
        var content = template
        context.forEach { (key, value) ->
            content = content.replace("{{$key}}", value.toString())
        }

        val file = codeGenerator.createNewFile(
            Dependencies(false, entity.containingFile!!),
            context["package"] as String,
            "${context["className"]}${templateName.capitalize()}",
            "kt"
        )

        file.bufferedWriter().use { it.write(content) }
    }
}
```

## 4. 生成路径详解

### 标准生成路径

```
项目根目录/
└── build/
    └── generated/
        └── ksp/
            ├── main/
            │   ├── kotlin/           ← .kt 文件
            │   │   └── com/example/
            │   │       └── User.kt
            │   ├── java/             ← .java 文件
            │   │   └── com/example/
            │   │       └── UserMapper.java
            │   └── resources/        ← 其他文件（.json, .xml, .txt 等）
            │       └── metadata/
            │           └── entities.json
            └── test/                 ← 测试代码的生成文件
                ├── kotlin/
                ├── java/
                └── resources/
```

### 多模块项目的生成路径

```
multi-module-project/
├── module-a/
│   └── build/generated/ksp/main/kotlin/
└── module-b/
    └── build/generated/ksp/main/kotlin/
```

### 访问生成文件

```kotlin
// 在 Gradle 构建脚本中
sourceSets {
    main {
        kotlin {
            // KSP 自动添加生成的 Kotlin 源码目录
            srcDir("build/generated/ksp/main/kotlin")
        }
        resources {
            // KSP 自动添加生成的资源目录
            srcDir("build/generated/ksp/main/resources")
        }
    }
}
```

## 5. Dependencies 深度解析

### 增量编译原理

KSP 通过 `Dependencies` 追踪文件依赖关系，实现增量编译：

```
源文件 A 改变
    ↓
检查哪些生成文件依赖 A
    ↓
只重新生成依赖 A 的文件
    ↓
其他文件保持不变
```

### 三种依赖策略对比

```kotlin
// 策略 1: 独立文件（Dependencies(false)）
// 场景: 生成静态常量类
val file1 = codeGenerator.createNewFile(
    Dependencies(false),
    "com.example",
    "Constants",
    "kt"
)
// 特点:
// - 首次生成后永不改变
// - 即使源代码改变也不会重新生成
// - 适用于静态配置、版本信息等

// 策略 2: 关联特定文件（Dependencies(false, file1, file2)）
// 场景: 为 User 实体生成 UserRepository
val file2 = codeGenerator.createNewFile(
    Dependencies(false, userEntityFile),
    "com.example.repository",
    "UserRepository",
    "kt"
)
// 特点:
// - 只有 User.kt 改变时才重新生成
// - Product.kt 改变不影响 UserRepository
// - 适用于一对一的代码生成

// 策略 3: 聚合所有文件（Dependencies(true)）
// 场景: 生成所有实体的索引
val file3 = codeGenerator.createNewFile(
    Dependencies(true),
    "com.example",
    "EntityIndex",
    "kt"
)
// 特点:
// - 任何源文件改变都重新生成
// - 用于汇总性质的文件
// - 适用于元数据索引、路由表等
```

### 实战示例：智能依赖管理

```kotlin
class SmartDependencyGenerator(
    private val codeGenerator: CodeGenerator
) {
    // 记录每个实体的源文件
    private val entityFiles = mutableMapOf<String, KSFile>()

    fun collectEntity(entity: KSClassDeclaration) {
        entityFiles[entity.simpleName.asString()] = entity.containingFile!!
    }

    fun generateRepositories() {
        entityFiles.forEach { (entityName, sourceFile) ->
            // 使用关联依赖：Repository 只依赖对应的实体
            val file = codeGenerator.createNewFile(
                Dependencies(false, sourceFile),  // 只依赖这一个实体
                "com.example.repository",
                "${entityName}Repository",
                "kt"
            )
            file.bufferedWriter().use { it.write(buildRepositoryContent(entityName)) }
        }
    }

    fun generateEntityIndex() {
        // 使用聚合依赖：索引依赖所有实体
        val allFiles = entityFiles.values.toTypedArray()
        val file = codeGenerator.createNewFile(
            Dependencies(false, *allFiles),  // 依赖所有实体文件
            "com.example",
            "EntityIndex",
            "kt"
        )

        val content = buildString {
            appendLine("package com.example")
            appendLine()
            appendLine("object EntityIndex {")
            appendLine("    val entities = listOf(")
            entityFiles.keys.forEach { entityName ->
                appendLine("        ${entityName}::class,")
            }
            appendLine("    )")
            appendLine("}")
        }

        file.bufferedWriter().use { it.write(content) }
    }

    private fun buildRepositoryContent(entityName: String): String {
        return """
            package com.example.repository

            import org.springframework.data.jpa.repository.JpaRepository
            import com.example.domain.$entityName

            interface ${entityName}Repository : JpaRepository<$entityName, Long>
        """.trimIndent()
    }
}
```

## 6. 常见问题与解决方案

### Q1: FileAlreadyExistsException

**问题：** `java.io.FileAlreadyExistsException`

**原因：** 在 `process()` 中多次调用 `createNewFile()` 创建同名文件

**解决方案：**

```kotlin
class MyProcessor : SymbolProcessor {
    private var filesGenerated = false

    override fun process(resolver: Resolver): List<KSAnnotated> {
        // 只收集数据，不生成文件
        collectData(resolver)
        return emptyList()
    }

    override fun finish() {
        // 在 finish() 中一次性生成所有文件
        if (!filesGenerated) {
            generateAllFiles()
            filesGenerated = true
        }
    }
}
```

### Q2: 生成的文件找不到

**问题：** 编译后找不到生成的文件

**检查清单：**

```kotlin
// 1. 确认文件确实被生成了
override fun finish() {
    logger.info("Generating file...")
    val file = codeGenerator.createNewFile(deps, pkg, name, ext)
    file.bufferedWriter().use { it.write(content) }
    logger.info("File generated successfully")
}

// 2. 检查生成路径
// Kotlin 文件: build/generated/ksp/main/kotlin/
// Java 文件: build/generated/ksp/main/java/
// 资源文件: build/generated/ksp/main/resources/

// 3. 确认文件已关闭
file.close()  // 或使用 use {} 自动关闭

// 4. 重新编译
// ./gradlew clean build
```

### Q3: 生成的代码编译错误

**问题：** 生成的 Kotlin/Java 代码无法编译

**调试技巧：**

```kotlin
fun generateWithValidation(
    codeGenerator: CodeGenerator,
    entity: KSClassDeclaration
) {
    val content = buildContent(entity)

    // 调试：先输出到日志
    logger.info("Generated content:\n$content")

    // 调试：保存到临时文件查看
    val debugFile = File("debug_output.kt")
    debugFile.writeText(content)

    // 正式生成
    val file = codeGenerator.createNewFile(deps, pkg, name, "kt")
    file.bufferedWriter().use { it.write(content) }
}
```

**常见错误：**

```kotlin
// ❌ 错误：缺少包声明
val content = """
    class User {  // 缺少 package 声明
        var name: String? = null
    }
"""

// ✅ 正确
val content = """
    package com.example.domain  // 添加 package

    class User {
        var name: String? = null
    }
"""

// ❌ 错误：缺少导入
val content = """
    package com.example

    class UserService {
        fun save(user: User) {}  // User 未导入
    }
"""

// ✅ 正确
val content = """
    package com.example

    import com.example.domain.User  // 添加 import

    class UserService {
        fun save(user: User) {}
    }
"""
```

### Q4: 字符编码问题

**问题：** 生成的文件包含中文乱码

**解决方案：**

```kotlin
// 方式 1: 使用 bufferedWriter（推荐）
val file = codeGenerator.createNewFile(deps, pkg, name, ext)
file.bufferedWriter(Charsets.UTF_8).use { writer ->
    writer.write(content)
}

// 方式 2: 指定字符集
val file = codeGenerator.createNewFile(deps, pkg, name, ext)
file.write(content.toByteArray(Charsets.UTF_8))
file.close()
```

### Q5: 路径分隔符问题（Windows vs Linux）

**问题：** 在 Windows 和 Linux 上生成的路径不一致

**解决方案：**

```kotlin
// ❌ 错误：硬编码路径分隔符
val path = "com\\example\\domain"  // Windows
val path = "com/example/domain"    // Linux

// ✅ 正确：使用包名，让 KSP 处理路径
codeGenerator.createNewFile(
    deps,
    "com.example.domain",  // 使用点分隔的包名
    "User",
    "kt"
)
// KSP 会自动转换为正确的路径分隔符
```

## 7. 最佳实践

### 1. 使用 buildString 构建代码

```kotlin
// ✅ 推荐：使用 buildString
fun buildKotlinClass(className: String, fields: List<Field>): String = buildString {
    appendLine("package com.example")
    appendLine()
    appendLine("data class $className(")
    fields.forEachIndexed { index, field ->
        append("    val ${field.name}: ${field.type}")
        if (index < fields.size - 1) appendLine(",")
        else appendLine()
    }
    appendLine(")")
}

// ❌ 不推荐：手动拼接字符串
fun buildKotlinClass(className: String, fields: List<Field>): String {
    var result = "package com.example\n\n"
    result += "data class $className(\n"
    // ... 容易出错
    return result
}
```

### 2. 使用 trimIndent() 保持代码可读性

```kotlin
// ✅ 推荐：使用 trimIndent
val content = """
    package com.example

    class User {
        var name: String? = null
        var age: Int? = null
    }
""".trimIndent()

// ❌ 不推荐：手动处理缩进
val content = "package com.example\n\nclass User {\n    var name: String? = null\n}"
```

### 3. 统一的文件生成辅助方法

```kotlin
class CodeGeneratorHelper(private val codeGenerator: CodeGenerator) {

    fun generateKotlinFile(
        dependencies: Dependencies,
        packageName: String,
        fileName: String,
        content: String
    ) {
        val file = codeGenerator.createNewFile(dependencies, packageName, fileName, "kt")
        file.bufferedWriter(Charsets.UTF_8).use { it.write(content) }
    }

    fun generateJavaFile(
        dependencies: Dependencies,
        packageName: String,
        fileName: String,
        content: String
    ) {
        val file = codeGenerator.createNewFile(dependencies, packageName, fileName, "java")
        file.bufferedWriter(Charsets.UTF_8).use { it.write(content) }
    }

    fun generateResourceFile(
        dependencies: Dependencies,
        path: String,
        fileName: String,
        extension: String,
        content: String
    ) {
        val file = codeGenerator.createNewFile(dependencies, path, fileName, extension)
        file.bufferedWriter(Charsets.UTF_8).use { it.write(content) }
    }
}
```

### 4. 代码格式化

```kotlin
class FormattedCodeGenerator(
    private val codeGenerator: CodeGenerator
) {
    fun generateFormattedKotlinClass(entity: KSClassDeclaration) {
        val content = buildString {
            // 包声明
            appendLine("package ${entity.packageName.asString()}")
            appendLine()

            // 导入语句
            val imports = collectImports(entity)
            imports.sorted().forEach { import ->
                appendLine("import $import")
            }
            if (imports.isNotEmpty()) appendLine()

            // 类声明
            appendLine("/**")
            appendLine(" * Generated class for ${entity.simpleName.asString()}")
            appendLine(" * Generated at: ${java.time.LocalDateTime.now()}")
            appendLine(" */")
            appendLine("class ${entity.simpleName.asString()}Repository {")
            appendLine("    // TODO: Add implementation")
            appendLine("}")
        }

        val file = codeGenerator.createNewFile(
            Dependencies(false, entity.containingFile!!),
            entity.packageName.asString(),
            "${entity.simpleName.asString()}Repository",
            "kt"
        )

        file.bufferedWriter(Charsets.UTF_8).use { it.write(content) }
    }

    private fun collectImports(entity: KSClassDeclaration): List<String> {
        // 收集需要的导入语句
        return listOf(
            "org.springframework.stereotype.Repository",
            "org.springframework.data.jpa.repository.JpaRepository"
        )
    }
}
```

### 5. 错误处理

```kotlin
class SafeCodeGenerator(
    private val codeGenerator: CodeGenerator,
    private val logger: KSPLogger
) {
    fun generateSafely(
        entity: KSClassDeclaration,
        generator: (KSClassDeclaration) -> String
    ) {
        try {
            val content = generator(entity)

            // 验证内容
            if (content.isBlank()) {
                logger.warn("Empty content for ${entity.simpleName.asString()}")
                return
            }

            // 生成文件
            val file = codeGenerator.createNewFile(
                Dependencies(false, entity.containingFile!!),
                entity.packageName.asString(),
                "${entity.simpleName.asString()}Generated",
                "kt"
            )

            file.bufferedWriter(Charsets.UTF_8).use { it.write(content) }
            logger.info("Successfully generated for ${entity.simpleName.asString()}")

        } catch (e: FileAlreadyExistsException) {
            logger.error("File already exists: ${e.message}")
        } catch (e: IOException) {
            logger.error("IO error while generating: ${e.message}")
        } catch (e: Exception) {
            logger.error("Unexpected error: ${e.message}", e)
        }
    }
}
```

## 8. 完整示例

### 实体代码生成器

```kotlin
class EntityCodeGenerator(
    private val codeGenerator: CodeGenerator,
    private val logger: KSPLogger
) : SymbolProcessor {

    private val entities = mutableListOf<KSClassDeclaration>()

    override fun process(resolver: Resolver): List<KSAnnotated> {
        // 收集所有实体
        resolver.getSymbolsWithAnnotation("jakarta.persistence.Entity")
            .filterIsInstance<KSClassDeclaration>()
            .forEach { entities.add(it) }

        return emptyList()
    }

    override fun finish() {
        entities.forEach { entity ->
            generateRepository(entity)
            generateService(entity)
            generateController(entity)
        }

        // 生成索引文件
        generateEntityIndex()
    }

    private fun generateRepository(entity: KSClassDeclaration) {
        val packageName = "${entity.packageName.asString()}.repository"
        val className = "${entity.simpleName.asString()}Repository"

        val content = """
            package $packageName

            import org.springframework.data.jpa.repository.JpaRepository
            import ${entity.qualifiedName!!.asString()}

            interface $className : JpaRepository<${entity.simpleName.asString()}, Long> {
                fun findByName(name: String): List<${entity.simpleName.asString()}>
            }
        """.trimIndent()

        val file = codeGenerator.createNewFile(
            Dependencies(false, entity.containingFile!!),
            packageName,
            className,
            "kt"
        )

        file.bufferedWriter(Charsets.UTF_8).use { it.write(content) }
        logger.info("Generated repository: $className")
    }

    private fun generateService(entity: KSClassDeclaration) {
        val packageName = "${entity.packageName.asString()}.service"
        val className = "${entity.simpleName.asString()}Service"
        val repositoryClass = "${entity.simpleName.asString()}Repository"

        val content = """
            package $packageName

            import org.springframework.stereotype.Service
            import ${entity.qualifiedName!!.asString()}
            import ${entity.packageName.asString()}.repository.$repositoryClass

            @Service
            class $className(
                private val repository: $repositoryClass
            ) {
                fun findAll() = repository.findAll()

                fun findById(id: Long) = repository.findById(id)

                fun save(entity: ${entity.simpleName.asString()}) = repository.save(entity)

                fun deleteById(id: Long) = repository.deleteById(id)
            }
        """.trimIndent()

        val file = codeGenerator.createNewFile(
            Dependencies(false, entity.containingFile!!),
            packageName,
            className,
            "kt"
        )

        file.bufferedWriter(Charsets.UTF_8).use { it.write(content) }
        logger.info("Generated service: $className")
    }

    private fun generateController(entity: KSClassDeclaration) {
        val packageName = "${entity.packageName.asString()}.controller"
        val className = "${entity.simpleName.asString()}Controller"
        val serviceClass = "${entity.simpleName.asString()}Service"

        val content = """
            package $packageName

            import org.springframework.web.bind.annotation.*
            import ${entity.qualifiedName!!.asString()}
            import ${entity.packageName.asString()}.service.$serviceClass

            @RestController
            @RequestMapping("/api/${entity.simpleName.asString().lowercase()}")
            class $className(
                private val service: $serviceClass
            ) {
                @GetMapping
                fun findAll() = service.findAll()

                @GetMapping("/{id}")
                fun findById(@PathVariable id: Long) = service.findById(id)

                @PostMapping
                fun create(@RequestBody entity: ${entity.simpleName.asString()}) = service.save(entity)

                @PutMapping("/{id}")
                fun update(@PathVariable id: Long, @RequestBody entity: ${entity.simpleName.asString()}) = service.save(entity)

                @DeleteMapping("/{id}")
                fun delete(@PathVariable id: Long) = service.deleteById(id)
            }
        """.trimIndent()

        val file = codeGenerator.createNewFile(
            Dependencies(false, entity.containingFile!!),
            packageName,
            className,
            "kt"
        )

        file.bufferedWriter(Charsets.UTF_8).use { it.write(content) }
        logger.info("Generated controller: $className")
    }

    private fun generateEntityIndex() {
        val basePackage = entities.firstOrNull()?.packageName?.asString() ?: return

        val content = buildString {
            appendLine("package $basePackage")
            appendLine()
            entities.forEach { entity ->
                appendLine("import ${entity.qualifiedName!!.asString()}")
            }
            appendLine()
            appendLine("/**")
            appendLine(" * Auto-generated entity index")
            appendLine(" * Total entities: ${entities.size}")
            appendLine(" */")
            appendLine("object EntityIndex {")
            appendLine("    val entities = listOf(")
            entities.forEach { entity ->
                appendLine("        ${entity.simpleName.asString()}::class,")
            }
            appendLine("    )")
            appendLine("}")
        }

        val allEntityFiles = entities.mapNotNull { it.containingFile }.toTypedArray()
        val file = codeGenerator.createNewFile(
            Dependencies(false, *allEntityFiles),
            basePackage,
            "EntityIndex",
            "kt"
        )

        file.bufferedWriter(Charsets.UTF_8).use { it.write(content) }
        logger.info("Generated entity index with ${entities.size} entities")
    }
}
```

## 9. 参考资源

- [KSP 官方文档 - CodeGenerator](https://kotlinlang.org/docs/ksp-reference.html#codegenerator)
- [KSP API 文档](https://kotlin.github.io/symbol-processing/api/)
- [KSP 示例项目](https://github.com/google/ksp/tree/main/examples)
- [增量编译指南](https://kotlinlang.org/docs/ksp-incremental.html)

## 许可

与主项目相同
