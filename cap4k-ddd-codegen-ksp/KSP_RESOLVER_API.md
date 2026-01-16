# KSP Resolver API 完整参考

## 概述

`Resolver` 是 KSP 中最核心的 API，提供了查找、解析和访问代码符号的能力。每次调用 `process()` 方法时，KSP 都会传入一个 `Resolver` 实例。

## 核心接口

```kotlin
interface Resolver {
    // 符号查找
    fun getSymbolsWithAnnotation(annotationName: String, inDepth: Boolean = false): Sequence<KSAnnotated>
    fun getClassDeclarationByName(name: KSName): KSClassDeclaration?
    fun getDeclarationsFromPackage(packageName: String): Sequence<KSDeclaration>

    // 文件访问
    fun getAllFiles(): Sequence<KSFile>
    fun getNewFiles(): Sequence<KSFile>

    // 类型操作
    fun createKSTypeReferenceFromKSType(type: KSType): KSTypeReference
    fun getKSNameFromString(name: String): KSName
    fun getClassDeclarationByName(name: String): KSClassDeclaration?

    // Java 互操作
    fun mapToJvmSignature(declaration: KSDeclaration): String?
    fun overrides(overrider: KSFunctionDeclaration, overridee: KSFunctionDeclaration): Boolean

    // 内置类型
    fun builtIns: KSBuiltIns
}
```

## 1. 符号查找方法

### getSymbolsWithAnnotation()

**查找所有带有指定注解的符号**

```kotlin
fun getSymbolsWithAnnotation(
    annotationName: String,      // 注解的全限定名
    inDepth: Boolean = false     // 是否深度搜索（包括依赖）
): Sequence<KSAnnotated>
```

**使用示例：**

```kotlin
// 1. 查找带 @Aggregate 注解的所有符号
val aggregateSymbols = resolver.getSymbolsWithAnnotation(
    "com.only4.cap4k.ddd.core.domain.aggregate.annotation.Aggregate"
)

// 2. 过滤出类声明
aggregateSymbols.filterIsInstance<KSClassDeclaration>().forEach { classDecl ->
    println("Found aggregate: ${classDecl.simpleName.asString()}")
}

// 3. 深度搜索（包括依赖库中的注解）
val allEntities = resolver.getSymbolsWithAnnotation(
    "jakarta.persistence.Entity",
    inDepth = true  // 也搜索依赖的 JAR 包
)
```

**返回类型：**
- `Sequence<KSAnnotated>` - 可能包含：
  - `KSClassDeclaration` - 类
  - `KSFunctionDeclaration` - 函数
  - `KSPropertyDeclaration` - 属性
  - `KSFile` - 文件

**注意事项：**
- 必须使用**全限定名**（包含包名）
- 返回的是 `Sequence`，延迟计算，高效处理大量数据
- `inDepth = false`（默认）只搜索当前模块的源代码
- `inDepth = true` 会搜索所有依赖，但性能较慢

**常见错误：**

```kotlin
// ❌ 错误：使用简单名称
resolver.getSymbolsWithAnnotation("Entity")

// ✅ 正确：使用全限定名
resolver.getSymbolsWithAnnotation("jakarta.persistence.Entity")
```

### getClassDeclarationByName()

**通过名称查找类声明**

```kotlin
fun getClassDeclarationByName(name: KSName): KSClassDeclaration?
fun getClassDeclarationByName(name: String): KSClassDeclaration?  // 重载版本
```

**使用示例：**

```kotlin
// 1. 查找特定类
val userClass = resolver.getClassDeclarationByName(
    resolver.getKSNameFromString("com.example.domain.User")
)

// 2. 简化版本（直接传字符串）
val userClass = resolver.getClassDeclarationByName("com.example.domain.User")

// 3. 检查类是否存在
if (userClass != null) {
    println("Found class: ${userClass.qualifiedName?.asString()}")

    // 访问类的成员
    userClass.getAllProperties().forEach { prop ->
        println("  Field: ${prop.simpleName.asString()}")
    }
}

// 4. 查找 Kotlin 内置类
val stringClass = resolver.getClassDeclarationByName("kotlin.String")
val listClass = resolver.getClassDeclarationByName("kotlin.collections.List")
```

**返回值：**
- `KSClassDeclaration?` - 找到返回类声明，否则返回 `null`

**实用场景：**

```kotlin
// 检查类是否继承特定基类
fun isSubclassOf(classDecl: KSClassDeclaration, baseClassName: String): Boolean {
    val baseClass = resolver.getClassDeclarationByName(baseClassName) ?: return false
    return classDecl.superTypes.any {
        it.resolve().declaration == baseClass
    }
}

// 使用
if (isSubclassOf(userClass, "com.example.BaseEntity")) {
    println("User extends BaseEntity")
}
```

### getDeclarationsFromPackage()

**获取指定包下的所有声明**

```kotlin
fun getDeclarationsFromPackage(packageName: String): Sequence<KSDeclaration>
```

**使用示例：**

```kotlin
// 1. 获取包下的所有声明
val declarations = resolver.getDeclarationsFromPackage("com.example.domain")

// 2. 过滤出类
val classes = declarations.filterIsInstance<KSClassDeclaration>()

// 3. 过滤出函数
val topLevelFunctions = declarations.filterIsInstance<KSFunctionDeclaration>()

// 4. 统计信息
declarations.forEach { decl ->
    when (decl) {
        is KSClassDeclaration -> println("Class: ${decl.simpleName.asString()}")
        is KSFunctionDeclaration -> println("Function: ${decl.simpleName.asString()}")
        is KSPropertyDeclaration -> println("Property: ${decl.simpleName.asString()}")
    }
}
```

**实用场景：**

```kotlin
// 查找包下所有实体类
fun findAllEntities(packageName: String): List<KSClassDeclaration> {
    return resolver.getDeclarationsFromPackage(packageName)
        .filterIsInstance<KSClassDeclaration>()
        .filter { classDecl ->
            classDecl.annotations.any {
                it.shortName.asString() == "Entity"
            }
        }
        .toList()
}

// 使用
val entities = findAllEntities("com.example.domain")
println("Found ${entities.size} entities")
```

## 2. 文件访问方法

### getAllFiles()

**获取所有源文件**

```kotlin
fun getAllFiles(): Sequence<KSFile>
```

**使用示例：**

```kotlin
// 1. 遍历所有文件
resolver.getAllFiles().forEach { file ->
    println("File: ${file.fileName}")
    println("  Package: ${file.packageName.asString()}")
    println("  Path: ${file.filePath}")
}

// 2. 统计文件数量
val totalFiles = resolver.getAllFiles().count()
println("Total files: $totalFiles")

// 3. 过滤特定包的文件
val domainFiles = resolver.getAllFiles()
    .filter { it.packageName.asString().startsWith("com.example.domain") }

// 4. 查找包含特定注解的文件
val filesWithAggregates = resolver.getAllFiles()
    .filter { file ->
        file.declarations.any { decl ->
            decl is KSClassDeclaration && decl.annotations.any {
                it.shortName.asString() == "Aggregate"
            }
        }
    }
```

**KSFile 属性：**

```kotlin
interface KSFile {
    val fileName: String                    // 文件名
    val filePath: String                    // 完整路径
    val packageName: KSName                 // 包名
    val declarations: Sequence<KSDeclaration>  // 文件中的所有声明
    val annotations: Sequence<KSAnnotation>    // 文件级注解
}
```

### getNewFiles()

**获取本轮新生成的文件**

```kotlin
fun getNewFiles(): Sequence<KSFile>
```

**使用示例：**

```kotlin
override fun process(resolver: Resolver): List<KSAnnotated> {
    // 1. 检查是否有新文件
    val newFiles = resolver.getNewFiles().toList()

    if (newFiles.isEmpty()) {
        logger.info("No new files, this is the last round")
        // 可以在这里生成最终文件
    } else {
        logger.info("Found ${newFiles.size} new files")
        newFiles.forEach { file ->
            logger.info("  - ${file.fileName}")
        }
    }

    // 2. 只处理新文件中的声明
    resolver.getNewFiles().forEach { file ->
        file.declarations
            .filterIsInstance<KSClassDeclaration>()
            .forEach { classDecl ->
                processClass(classDecl)
            }
    }

    return emptyList()
}
```

**实用场景：**

```kotlin
// 判断是否是最后一轮处理
private var isFirstRound = true

override fun process(resolver: Resolver): List<KSAnnotated> {
    if (isFirstRound) {
        // 第一轮：处理所有文件
        processAllFiles(resolver.getAllFiles())
        isFirstRound = false
    } else {
        // 后续轮：只处理新文件
        processAllFiles(resolver.getNewFiles())
    }

    // 如果没有新文件，说明是最后一轮
    if (resolver.getNewFiles().toList().isEmpty()) {
        generateFinalOutput()
    }

    return emptyList()
}
```

## 3. 类型操作方法

### createKSTypeReferenceFromKSType()

**从 KSType 创建类型引用**

```kotlin
fun createKSTypeReferenceFromKSType(type: KSType): KSTypeReference
```

**使用示例：**

```kotlin
// 1. 创建类型引用
val stringType = resolver.builtIns.stringType
val typeRef = resolver.createKSTypeReferenceFromKSType(stringType)

// 2. 用于构建代码
fun generatePropertyDeclaration(name: String, type: KSType): String {
    val typeRef = resolver.createKSTypeReferenceFromKSType(type)
    return "var $name: ${typeRef.resolve().declaration.simpleName.asString()}"
}
```

### getKSNameFromString()

**从字符串创建 KSName**

```kotlin
fun getKSNameFromString(name: String): KSName
```

**使用示例：**

```kotlin
// 1. 创建 KSName
val packageName = resolver.getKSNameFromString("com.example.domain")
val className = resolver.getKSNameFromString("com.example.domain.User")

// 2. 用于查找类
val userClass = resolver.getClassDeclarationByName(className)

// 3. 比较名称
fun isInPackage(decl: KSDeclaration, packageName: String): Boolean {
    val targetPackage = resolver.getKSNameFromString(packageName)
    return decl.packageName == targetPackage
}
```

### builtIns

**访问 Kotlin 内置类型**

```kotlin
val builtIns: KSBuiltIns
```

**KSBuiltIns 提供的类型：**

```kotlin
interface KSBuiltIns {
    val anyType: KSType              // Any
    val nothingType: KSType          // Nothing
    val unitType: KSType             // Unit
    val numberType: KSType           // Number
    val byteType: KSType             // Byte
    val shortType: KSType            // Short
    val intType: KSType              // Int
    val longType: KSType             // Long
    val floatType: KSType            // Float
    val doubleType: KSType           // Double
    val charType: KSType             // Char
    val booleanType: KSType          // Boolean
    val stringType: KSType           // String
    val iterableType: KSType         // Iterable<*>
    val arrayType: KSType            // Array<*>
}
```

**使用示例：**

```kotlin
// 1. 检查类型是否是内置类型
fun isBuiltInType(type: KSType): Boolean {
    val builtIns = resolver.builtIns
    return when (type) {
        builtIns.intType -> true
        builtIns.stringType -> true
        builtIns.booleanType -> true
        // ... 其他类型
        else -> false
    }
}

// 2. 类型转换为 Kotlin 类型名
fun getKotlinTypeName(type: KSType): String {
    val builtIns = resolver.builtIns
    return when (type) {
        builtIns.intType -> "Int"
        builtIns.longType -> "Long"
        builtIns.stringType -> "String"
        builtIns.booleanType -> "Boolean"
        else -> type.declaration.simpleName.asString()
    }
}

// 3. 生成默认值
fun getDefaultValue(type: KSType): String {
    val builtIns = resolver.builtIns
    return when (type) {
        builtIns.intType -> "0"
        builtIns.longType -> "0L"
        builtIns.booleanType -> "false"
        builtIns.stringType -> "\"\""
        else -> "null"
    }
}
```

## 4. Java 互操作方法

### mapToJvmSignature()

**获取 JVM 签名**

```kotlin
fun mapToJvmSignature(declaration: KSDeclaration): String?
```

**使用示例：**

```kotlin
// 1. 获取方法的 JVM 签名
val functionDecl: KSFunctionDeclaration = ...
val jvmSignature = resolver.mapToJvmSignature(functionDecl)
println("JVM Signature: $jvmSignature")
// 输出: com/example/MyClass.myMethod(Ljava/lang/String;)V

// 2. 获取类的 JVM 签名
val classDecl: KSClassDeclaration = ...
val classSignature = resolver.mapToJvmSignature(classDecl)
println("Class Signature: $classSignature")
// 输出: com/example/MyClass

// 3. 用于生成字节码或反射操作
fun generateReflectionCode(functionDecl: KSFunctionDeclaration): String {
    val signature = resolver.mapToJvmSignature(functionDecl) ?: return ""
    return """
        val method = Class.forName("${signature.substringBeforeLast('.')}")
            .getMethod("${functionDecl.simpleName.asString()}")
    """.trimIndent()
}
```

### overrides()

**检查函数是否重写另一个函数**

```kotlin
fun overrides(
    overrider: KSFunctionDeclaration,   // 重写者
    overridee: KSFunctionDeclaration    // 被重写者
): Boolean
```

**使用示例：**

```kotlin
// 1. 检查方法重写关系
val childMethod: KSFunctionDeclaration = ...
val parentMethod: KSFunctionDeclaration = ...

if (resolver.overrides(childMethod, parentMethod)) {
    println("${childMethod.simpleName.asString()} overrides ${parentMethod.simpleName.asString()}")
}

// 2. 查找所有重写的方法
fun findOverriddenMethods(classDecl: KSClassDeclaration): List<KSFunctionDeclaration> {
    val overriddenMethods = mutableListOf<KSFunctionDeclaration>()

    classDecl.getAllFunctions().forEach { method ->
        // 遍历父类的所有方法
        classDecl.superTypes.forEach { superType ->
            val superClass = superType.resolve().declaration as? KSClassDeclaration
            superClass?.getAllFunctions()?.forEach { superMethod ->
                if (resolver.overrides(method, superMethod)) {
                    overriddenMethods.add(method)
                }
            }
        }
    }

    return overriddenMethods
}

// 3. 检查是否重写了特定方法
fun overridesToString(classDecl: KSClassDeclaration): Boolean {
    val toStringMethod = resolver.getClassDeclarationByName("kotlin.Any")
        ?.getAllFunctions()
        ?.find { it.simpleName.asString() == "toString" }
        ?: return false

    return classDecl.getAllFunctions().any { method ->
        resolver.overrides(method, toStringMethod)
    }
}
```

## 5. 高级用法示例

### 完整的实体扫描

```kotlin
class EntityScanner(
    private val resolver: Resolver,
    private val logger: KSPLogger
) {
    fun scanAllEntities(): List<EntityInfo> {
        val entities = mutableListOf<EntityInfo>()

        // 1. 通过注解查找
        resolver.getSymbolsWithAnnotation("jakarta.persistence.Entity")
            .filterIsInstance<KSClassDeclaration>()
            .forEach { classDecl ->
                entities.add(extractEntityInfo(classDecl))
            }

        // 2. 通过包名查找
        resolver.getDeclarationsFromPackage("com.example.domain")
            .filterIsInstance<KSClassDeclaration>()
            .filter { it.isEntity() }
            .forEach { classDecl ->
                entities.add(extractEntityInfo(classDecl))
            }

        return entities
    }

    private fun extractEntityInfo(classDecl: KSClassDeclaration): EntityInfo {
        return EntityInfo(
            name = classDecl.simpleName.asString(),
            packageName = classDecl.packageName.asString(),
            fields = extractFields(classDecl),
            superClass = extractSuperClass(classDecl),
            interfaces = extractInterfaces(classDecl)
        )
    }

    private fun extractFields(classDecl: KSClassDeclaration): List<FieldInfo> {
        return classDecl.getAllProperties().map { property ->
            FieldInfo(
                name = property.simpleName.asString(),
                type = property.type.resolve().declaration.qualifiedName?.asString() ?: "",
                isNullable = property.type.resolve().isMarkedNullable
            )
        }.toList()
    }

    private fun extractSuperClass(classDecl: KSClassDeclaration): String? {
        return classDecl.superTypes
            .map { it.resolve().declaration }
            .filterIsInstance<KSClassDeclaration>()
            .firstOrNull()
            ?.qualifiedName?.asString()
    }

    private fun extractInterfaces(classDecl: KSClassDeclaration): List<String> {
        return classDecl.superTypes
            .map { it.resolve().declaration }
            .filter { it.classKind == ClassKind.INTERFACE }
            .mapNotNull { it.qualifiedName?.asString() }
            .toList()
    }
}
```

### 依赖关系分析

```kotlin
class DependencyAnalyzer(private val resolver: Resolver) {

    // 分析类的依赖关系
    fun analyzeDependencies(className: String): DependencyGraph {
        val classDecl = resolver.getClassDeclarationByName(className)
            ?: return DependencyGraph(emptyList())

        val dependencies = mutableSetOf<String>()

        // 1. 从字段类型分析
        classDecl.getAllProperties().forEach { property ->
            val type = property.type.resolve()
            dependencies.add(type.declaration.qualifiedName?.asString() ?: "")
        }

        // 2. 从父类分析
        classDecl.superTypes.forEach { superType ->
            dependencies.add(superType.resolve().declaration.qualifiedName?.asString() ?: "")
        }

        // 3. 从方法参数分析
        classDecl.getAllFunctions().forEach { function ->
            function.parameters.forEach { param ->
                dependencies.add(param.type.resolve().declaration.qualifiedName?.asString() ?: "")
            }
        }

        return DependencyGraph(dependencies.toList())
    }

    // 查找循环依赖
    fun findCircularDependencies(packageName: String): List<CircularDependency> {
        val classes = resolver.getDeclarationsFromPackage(packageName)
            .filterIsInstance<KSClassDeclaration>()
            .toList()

        val circularDeps = mutableListOf<CircularDependency>()

        // 简化的循环依赖检测
        classes.forEach { classA ->
            val depsA = analyzeDependencies(classA.qualifiedName?.asString() ?: "")

            classes.forEach { classB ->
                if (classA != classB) {
                    val depsB = analyzeDependencies(classB.qualifiedName?.asString() ?: "")

                    // 检查 A -> B 且 B -> A
                    if (depsA.contains(classB) && depsB.contains(classA)) {
                        circularDeps.add(CircularDependency(classA, classB))
                    }
                }
            }
        }

        return circularDeps
    }
}
```

### 代码质量检查

```kotlin
class CodeQualityChecker(private val resolver: Resolver) {

    // 检查未使用的导入
    fun checkUnusedImports(file: KSFile): List<String> {
        val unusedImports = mutableListOf<String>()

        // 获取文件中实际使用的类型
        val usedTypes = mutableSetOf<String>()
        file.declarations.forEach { decl ->
            if (decl is KSClassDeclaration) {
                collectUsedTypes(decl, usedTypes)
            }
        }

        // 比较导入和使用
        // (这里简化了，实际需要解析 import 语句)

        return unusedImports
    }

    // 检查过大的类
    fun checkLargeClasses(threshold: Int = 500): List<LargeClassInfo> {
        return resolver.getAllFiles()
            .flatMap { it.declarations }
            .filterIsInstance<KSClassDeclaration>()
            .map { classDecl ->
                val lineCount = countLines(classDecl)
                LargeClassInfo(
                    className = classDecl.qualifiedName?.asString() ?: "",
                    lineCount = lineCount
                )
            }
            .filter { it.lineCount > threshold }
            .toList()
    }

    // 检查缺少文档的公共 API
    fun checkMissingDocumentation(): List<String> {
        return resolver.getAllFiles()
            .flatMap { it.declarations }
            .filterIsInstance<KSClassDeclaration>()
            .filter { it.isPublic() }
            .filter { it.docString.isNullOrBlank() }
            .mapNotNull { it.qualifiedName?.asString() }
            .toList()
    }

    private fun collectUsedTypes(classDecl: KSClassDeclaration, usedTypes: MutableSet<String>) {
        classDecl.getAllProperties().forEach { property ->
            usedTypes.add(property.type.resolve().declaration.qualifiedName?.asString() ?: "")
        }
    }

    private fun countLines(classDecl: KSClassDeclaration): Int {
        // 简化实现
        return 0
    }
}
```

## 6. 性能优化技巧

### 使用 Sequence 而不是 List

```kotlin
// ❌ 不好：立即计算所有结果
val classes = resolver.getAllFiles()
    .flatMap { it.declarations }
    .filterIsInstance<KSClassDeclaration>()
    .toList()  // 强制计算所有元素

// ✅ 好：延迟计算
val classes = resolver.getAllFiles()
    .flatMap { it.declarations }
    .filterIsInstance<KSClassDeclaration>()
    .take(10)  // 只计算前 10 个
```

### 缓存查找结果

```kotlin
class CachedResolver(private val resolver: Resolver) {
    private val classCache = mutableMapOf<String, KSClassDeclaration?>()

    fun getClassDeclarationByName(name: String): KSClassDeclaration? {
        return classCache.getOrPut(name) {
            resolver.getClassDeclarationByName(name)
        }
    }
}
```

### 避免重复解析类型

```kotlin
// ❌ 不好：多次解析同一个类型
properties.forEach { property ->
    val type1 = property.type.resolve()
    val type2 = property.type.resolve()  // 重复解析
}

// ✅ 好：缓存解析结果
properties.forEach { property ->
    val type = property.type.resolve()  // 只解析一次
    // 使用 type
}
```

## 7. 常见问题

### Q1: getSymbolsWithAnnotation 返回空？

**检查清单：**
1. 确认使用全限定名
2. 确认源代码中确实有该注解
3. 检查依赖配置

```kotlin
// 调试代码
logger.info("Searching for: $annotationName")
val count = resolver.getSymbolsWithAnnotation(annotationName).count()
logger.info("Found $count symbols")
```

### Q2: getClassDeclarationByName 返回 null？

**可能原因：**
1. 类名拼写错误
2. 类不在当前编译范围内
3. 需要使用 `inDepth = true`

```kotlin
// 调试：列出所有可用的类
resolver.getAllFiles()
    .flatMap { it.declarations }
    .filterIsInstance<KSClassDeclaration>()
    .forEach { logger.info("Available class: ${it.qualifiedName?.asString()}") }
```

### Q3: 类型解析失败？

```kotlin
// 安全的类型解析
fun safeResolveType(typeRef: KSTypeReference): String {
    return try {
        typeRef.resolve().declaration.qualifiedName?.asString()
            ?: typeRef.toString()
    } catch (e: Exception) {
        logger.warn("Failed to resolve type: $typeRef")
        "Any"  // 降级方案
    }
}
```

## 8. 完整示例

```kotlin
class MyProcessor(
    private val codeGenerator: CodeGenerator,
    private val logger: KSPLogger
) : SymbolProcessor {

    override fun process(resolver: Resolver): List<KSAnnotated> {
        // 1. 查找所有实体
        val entities = resolver.getSymbolsWithAnnotation("jakarta.persistence.Entity")
            .filterIsInstance<KSClassDeclaration>()
            .toList()

        logger.info("Found ${entities.size} entities")

        // 2. 处理每个实体
        entities.forEach { entity ->
            processEntity(resolver, entity)
        }

        // 3. 检查是否是最后一轮
        if (resolver.getNewFiles().toList().isEmpty()) {
            generateSummary(entities)
        }

        return emptyList()
    }

    private fun processEntity(resolver: Resolver, entity: KSClassDeclaration) {
        logger.info("Processing: ${entity.simpleName.asString()}")

        // 提取信息
        val fields = entity.getAllProperties().toList()
        val methods = entity.getAllFunctions().toList()

        // 检查父类
        val superClass = entity.superTypes
            .map { it.resolve().declaration }
            .filterIsInstance<KSClassDeclaration>()
            .firstOrNull()

        if (superClass != null) {
            logger.info("  Extends: ${superClass.qualifiedName?.asString()}")
        }

        // 生成代码
        generateCodeForEntity(entity, fields, methods)
    }

    private fun generateCodeForEntity(
        entity: KSClassDeclaration,
        fields: List<KSPropertyDeclaration>,
        methods: List<KSFunctionDeclaration>
    ) {
        // 生成代码逻辑...
    }

    private fun generateSummary(entities: List<KSClassDeclaration>) {
        // 生成摘要文件...
    }
}
```

## 参考资源

- [KSP 官方文档 - Resolver](https://kotlinlang.org/docs/ksp-reference.html#resolver)
- [KSP API 文档](https://kotlin.github.io/symbol-processing/api/)
- [KSP 示例项目](https://github.com/google/ksp/tree/main/examples)
