# KSP 符号类型完整参考

## 概述

KSP (Kotlin Symbol Processing) 使用**符号 (Symbol)** 来表示代码中的各种元素。每种符号类型都提供了访问和分析相应代码结构的 API。

## 符号类型层次结构

```
KSNode (根接口)
├── KSAnnotated (带注解的符号)
│   ├── KSDeclaration (声明)
│   │   ├── KSClassDeclaration (类声明)
│   │   ├── KSFunctionDeclaration (函数声明)
│   │   ├── KSPropertyDeclaration (属性声明)
│   │   ├── KSTypeAlias (类型别名)
│   │   └── KSTypeParameter (类型参数)
│   ├── KSFile (文件)
│   ├── KSTypeReference (类型引用)
│   └── KSValueParameter (值参数)
├── KSAnnotation (注解)
├── KSType (类型)
├── KSTypeArgument (类型参数)
└── KSName (名称)
```

## 1. KSClassDeclaration - 类声明

### 定义

表示类、接口、对象、枚举的声明。

### 核心属性

```kotlin
interface KSClassDeclaration : KSDeclaration {
    val classKind: ClassKind                              // 类类型
    val primaryConstructor: KSFunctionDeclaration?        // 主构造函数
    val superTypes: Sequence<KSTypeReference>             // 父类和接口
    val isCompanionObject: Boolean                        // 是否是伴生对象

    // 继承自 KSDeclaration
    val simpleName: KSName                                // 简单名称
    val qualifiedName: KSName?                            // 全限定名
    val packageName: KSName                               // 包名
    val modifiers: Set<Modifier>                          // 修饰符
    val annotations: Sequence<KSAnnotation>               // 注解
    val parentDeclaration: KSDeclaration?                 // 父声明
    val containingFile: KSFile?                           // 所在文件
}
```

### ClassKind 枚举

```kotlin
enum class ClassKind {
    INTERFACE,      // 接口
    CLASS,          // 类
    ENUM_CLASS,     // 枚举类
    ENUM_ENTRY,     // 枚举值
    OBJECT,         // 对象
    ANNOTATION_CLASS // 注解类
}
```

### 常用方法

```kotlin
// 获取所有属性（包括继承的）
fun getAllProperties(): Sequence<KSPropertyDeclaration>

// 获取所有函数（包括继承的）
fun getAllFunctions(): Sequence<KSFunctionDeclaration>

// 获取声明的成员
fun getDeclaredProperties(): Sequence<KSPropertyDeclaration>
fun getDeclaredFunctions(): Sequence<KSFunctionDeclaration>

// 获取所有超类型
fun asStarProjectedType(): KSType
```

### 使用示例

#### 基本信息提取

```kotlin
fun analyzeClass(classDecl: KSClassDeclaration) {
    // 1. 基本信息
    println("Class: ${classDecl.simpleName.asString()}")
    println("Full name: ${classDecl.qualifiedName?.asString()}")
    println("Package: ${classDecl.packageName.asString()}")
    println("Kind: ${classDecl.classKind}")

    // 2. 修饰符
    println("Is abstract: ${Modifier.ABSTRACT in classDecl.modifiers}")
    println("Is data class: ${Modifier.DATA in classDecl.modifiers}")
    println("Is sealed: ${Modifier.SEALED in classDecl.modifiers}")

    // 3. 父类和接口
    classDecl.superTypes.forEach { superType ->
        val resolved = superType.resolve()
        println("Extends/Implements: ${resolved.declaration.qualifiedName?.asString()}")
    }
}
```

#### 判断类类型

```kotlin
fun checkClassType(classDecl: KSClassDeclaration) {
    when (classDecl.classKind) {
        ClassKind.CLASS -> {
            if (Modifier.DATA in classDecl.modifiers) {
                println("This is a data class")
            } else if (Modifier.ABSTRACT in classDecl.modifiers) {
                println("This is an abstract class")
            } else {
                println("This is a normal class")
            }
        }
        ClassKind.INTERFACE -> println("This is an interface")
        ClassKind.ENUM_CLASS -> println("This is an enum class")
        ClassKind.OBJECT -> {
            if (classDecl.isCompanionObject) {
                println("This is a companion object")
            } else {
                println("This is an object")
            }
        }
        ClassKind.ANNOTATION_CLASS -> println("This is an annotation class")
        ClassKind.ENUM_ENTRY -> println("This is an enum entry")
    }
}
```

#### 分析类成员

```kotlin
fun analyzeMembers(classDecl: KSClassDeclaration) {
    // 1. 属性
    println("Properties:")
    classDecl.getAllProperties().forEach { property ->
        println("  ${property.simpleName.asString()}: ${property.type.resolve()}")
    }

    // 2. 函数
    println("Functions:")
    classDecl.getAllFunctions().forEach { function ->
        val params = function.parameters.joinToString { it.name?.asString() ?: "" }
        println("  ${function.simpleName.asString()}($params)")
    }

    // 3. 构造函数
    classDecl.primaryConstructor?.let { constructor ->
        val params = constructor.parameters.joinToString {
            "${it.name?.asString()}: ${it.type.resolve()}"
        }
        println("Primary constructor: ($params)")
    }
}
```

#### 继承关系分析

```kotlin
fun analyzeInheritance(classDecl: KSClassDeclaration) {
    // 1. 查找父类
    val superClass = classDecl.superTypes
        .map { it.resolve().declaration }
        .filterIsInstance<KSClassDeclaration>()
        .firstOrNull { it.classKind == ClassKind.CLASS }

    if (superClass != null) {
        println("Extends: ${superClass.qualifiedName?.asString()}")
    }

    // 2. 查找实现的接口
    val interfaces = classDecl.superTypes
        .map { it.resolve().declaration }
        .filterIsInstance<KSClassDeclaration>()
        .filter { it.classKind == ClassKind.INTERFACE }
        .toList()

    if (interfaces.isNotEmpty()) {
        println("Implements:")
        interfaces.forEach {
            println("  - ${it.qualifiedName?.asString()}")
        }
    }
}
```

#### 检查特定父类

```kotlin
fun extendsBaseEntity(classDecl: KSClassDeclaration, baseClassName: String): Boolean {
    return classDecl.superTypes.any { superType ->
        val resolved = superType.resolve()
        resolved.declaration.qualifiedName?.asString() == baseClassName
    }
}

// 使用
if (extendsBaseEntity(classDecl, "com.example.BaseEntity")) {
    println("This class extends BaseEntity")
}
```

## 2. KSPropertyDeclaration - 属性声明

### 定义

表示类的属性、顶层属性或局部变量。

### 核心属性

```kotlin
interface KSPropertyDeclaration : KSDeclaration {
    val type: KSTypeReference                         // 属性类型
    val getter: KSPropertyGetter?                     // getter 方法
    val setter: KSPropertySetter?                     // setter 方法
    val isMutable: Boolean                            // 是否可变 (var)
    val hasBackingField: Boolean                      // 是否有后备字段

    // 继承自 KSDeclaration
    val simpleName: KSName                            // 属性名
    val modifiers: Set<Modifier>                      // 修饰符
    val annotations: Sequence<KSAnnotation>           // 注解
}
```

### 使用示例

#### 基本信息提取

```kotlin
fun analyzeProperty(property: KSPropertyDeclaration) {
    // 1. 基本信息
    println("Property: ${property.simpleName.asString()}")
    println("Type: ${property.type.resolve().declaration.qualifiedName?.asString()}")
    println("Is mutable: ${property.isMutable}")

    // 2. 修饰符
    println("Is private: ${Modifier.PRIVATE in property.modifiers}")
    println("Is lateinit: ${Modifier.LATEINIT in property.modifiers}")
    println("Is const: ${Modifier.CONST in property.modifiers}")

    // 3. 类型信息
    val type = property.type.resolve()
    println("Is nullable: ${type.isMarkedNullable}")
    println("Type arguments: ${type.arguments}")
}
```

#### 查找特定注解的属性

```kotlin
fun findIdProperty(classDecl: KSClassDeclaration): KSPropertyDeclaration? {
    return classDecl.getAllProperties()
        .find { property ->
            property.annotations.any { annotation ->
                annotation.shortName.asString() == "Id"
            }
        }
}

// 查找所有带 @Column 注解的属性
fun findColumnProperties(classDecl: KSClassDeclaration): List<KSPropertyDeclaration> {
    return classDecl.getAllProperties()
        .filter { property ->
            property.annotations.any {
                it.shortName.asString() == "Column"
            }
        }
        .toList()
}
```

#### 提取属性的注解参数

```kotlin
fun getColumnAnnotationInfo(property: KSPropertyDeclaration): ColumnInfo? {
    val columnAnnotation = property.annotations
        .find { it.shortName.asString() == "Column" }
        ?: return null

    val name = columnAnnotation.arguments
        .find { it.name?.asString() == "name" }
        ?.value as? String

    val nullable = columnAnnotation.arguments
        .find { it.name?.asString() == "nullable" }
        ?.value as? Boolean
        ?: true

    return ColumnInfo(name, nullable)
}

data class ColumnInfo(val name: String?, val nullable: Boolean)
```

#### 生成 Getter/Setter

```kotlin
fun generateGetterSetter(property: KSPropertyDeclaration): String {
    val name = property.simpleName.asString()
    val type = property.type.resolve().declaration.simpleName.asString()
    val capitalizedName = name.replaceFirstChar { it.uppercase() }

    return buildString {
        // Getter
        appendLine("fun get$capitalizedName(): $type {")
        appendLine("    return this.$name")
        appendLine("}")

        // Setter (if mutable)
        if (property.isMutable) {
            appendLine()
            appendLine("fun set$capitalizedName(value: $type) {")
            appendLine("    this.$name = value")
            appendLine("}")
        }
    }
}
```

## 3. KSFunctionDeclaration - 函数声明

### 定义

表示函数、方法、构造函数的声明。

### 核心属性

```kotlin
interface KSFunctionDeclaration : KSDeclaration {
    val functionKind: FunctionKind                    // 函数类型
    val parameters: List<KSValueParameter>            // 参数列表
    val returnType: KSTypeReference?                  // 返回类型
    val isAbstract: Boolean                           // 是否抽象
    val extensionReceiver: KSTypeReference?           // 扩展接收者

    // 继承自 KSDeclaration
    val simpleName: KSName                            // 函数名
    val modifiers: Set<Modifier>                      // 修饰符
    val annotations: Sequence<KSAnnotation>           // 注解
}
```

### FunctionKind 枚举

```kotlin
enum class FunctionKind {
    TOP_LEVEL,      // 顶层函数
    MEMBER,         // 成员函数
    STATIC,         // 静态函数
    LAMBDA,         // Lambda 表达式
    ANONYMOUS       // 匿名函数
}
```

### 使用示例

#### 基本信息提取

```kotlin
fun analyzeFunction(function: KSFunctionDeclaration) {
    // 1. 基本信息
    println("Function: ${function.simpleName.asString()}")
    println("Kind: ${function.functionKind}")
    println("Is abstract: ${function.isAbstract}")

    // 2. 参数
    println("Parameters:")
    function.parameters.forEach { param ->
        val paramName = param.name?.asString() ?: "unnamed"
        val paramType = param.type.resolve()
        println("  $paramName: ${paramType.declaration.simpleName.asString()}")
    }

    // 3. 返回类型
    function.returnType?.let { returnType ->
        println("Returns: ${returnType.resolve().declaration.simpleName.asString()}")
    }

    // 4. 扩展函数
    function.extensionReceiver?.let { receiver ->
        println("Extension on: ${receiver.resolve().declaration.simpleName.asString()}")
    }
}
```

#### 查找特定函数

```kotlin
fun findFunction(
    classDecl: KSClassDeclaration,
    name: String,
    paramCount: Int? = null
): KSFunctionDeclaration? {
    return classDecl.getAllFunctions()
        .filter { it.simpleName.asString() == name }
        .filter { paramCount == null || it.parameters.size == paramCount }
        .firstOrNull()
}

// 使用
val toStringMethod = findFunction(classDecl, "toString", 0)
val equalsMethod = findFunction(classDecl, "equals", 1)
```

#### 生成函数签名

```kotlin
fun generateSignature(function: KSFunctionDeclaration): String {
    val name = function.simpleName.asString()

    val params = function.parameters.joinToString(", ") { param ->
        val paramName = param.name?.asString() ?: ""
        val paramType = param.type.resolve().declaration.simpleName.asString()
        "$paramName: $paramType"
    }

    val returnType = function.returnType?.resolve()
        ?.declaration?.simpleName?.asString()
        ?: "Unit"

    return "fun $name($params): $returnType"
}

// 输出: fun findById(id: Long): User
```

#### 检查函数重写

```kotlin
fun isOverride(function: KSFunctionDeclaration): Boolean {
    return Modifier.OVERRIDE in function.modifiers
}

fun findOverriddenMethod(
    function: KSFunctionDeclaration,
    classDecl: KSClassDeclaration
): KSFunctionDeclaration? {
    if (!isOverride(function)) return null

    // 在父类中查找被重写的方法
    return classDecl.superTypes
        .map { it.resolve().declaration }
        .filterIsInstance<KSClassDeclaration>()
        .flatMap { it.getAllFunctions() }
        .find { superMethod ->
            superMethod.simpleName == function.simpleName &&
            superMethod.parameters.size == function.parameters.size
        }
}
```

## 4. KSAnnotation - 注解

### 定义

表示应用在声明上的注解。

### 核心属性

```kotlin
interface KSAnnotation : KSNode {
    val shortName: KSName                             // 注解简单名称
    val annotationType: KSTypeReference               // 注解类型
    val arguments: List<KSValueArgument>              // 注解参数
    val defaultArguments: List<KSValueArgument>       // 默认参数
    val useSiteTarget: AnnotationUseSiteTarget?       // 使用位置
}
```

### 使用示例

#### 读取注解信息

```kotlin
fun readAnnotation(annotation: KSAnnotation) {
    // 1. 注解名称
    println("Annotation: @${annotation.shortName.asString()}")

    // 2. 完整类型
    val fullName = annotation.annotationType.resolve()
        .declaration.qualifiedName?.asString()
    println("Full name: $fullName")

    // 3. 参数
    println("Arguments:")
    annotation.arguments.forEach { arg ->
        val name = arg.name?.asString() ?: "value"
        val value = arg.value
        println("  $name = $value")
    }
}
```

#### 查找特定注解

```kotlin
fun findAnnotation(
    annotated: KSAnnotated,
    annotationName: String
): KSAnnotation? {
    return annotated.annotations.find {
        it.shortName.asString() == annotationName
    }
}

// 使用
val entityAnnotation = findAnnotation(classDecl, "Entity")
val tableAnnotation = findAnnotation(classDecl, "Table")
```

#### 提取注解参数

```kotlin
fun getAnnotationValue(
    annotation: KSAnnotation,
    paramName: String = "value"
): Any? {
    return annotation.arguments
        .find { it.name?.asString() == paramName }
        ?.value
}

// 示例：读取 @Table(name = "users")
val tableAnnotation = findAnnotation(classDecl, "Table")
val tableName = getAnnotationValue(tableAnnotation!!, "name") as? String
println("Table name: $tableName")
```

#### 处理复杂注解

```kotlin
// 注解定义
// @Column(name = "user_name", nullable = false, length = 100)
fun parseColumnAnnotation(property: KSPropertyDeclaration): ColumnConfig? {
    val annotation = findAnnotation(property, "Column") ?: return null

    val name = getAnnotationValue(annotation, "name") as? String
    val nullable = getAnnotationValue(annotation, "nullable") as? Boolean ?: true
    val length = getAnnotationValue(annotation, "length") as? Int ?: 255

    return ColumnConfig(
        name = name ?: property.simpleName.asString(),
        nullable = nullable,
        length = length
    )
}

data class ColumnConfig(
    val name: String,
    val nullable: Boolean,
    val length: Int
)
```

#### 处理数组参数

```kotlin
// @RequestMapping(value = ["/api/user", "/api/users"])
fun getArrayParameter(annotation: KSAnnotation, paramName: String): List<Any>? {
    val value = getAnnotationValue(annotation, paramName)

    return when (value) {
        is List<*> -> value.filterNotNull()
        is Array<*> -> value.filterNotNull()
        else -> null
    }
}

// 使用
val mappingAnnotation = findAnnotation(function, "RequestMapping")!!
val paths = getArrayParameter(mappingAnnotation, "value")
println("Paths: $paths")
```

## 5. KSType - 类型

### 定义

表示 Kotlin 的类型信息，包括类型参数、可空性等。

### 核心属性

```kotlin
interface KSType {
    val declaration: KSDeclaration                    // 类型声明
    val arguments: List<KSTypeArgument>               // 类型参数
    val isMarkedNullable: Boolean                     // 是否可空
    val isFunctionType: Boolean                       // 是否是函数类型
    val isSuspendFunctionType: Boolean                // 是否是挂起函数
}
```

### 使用示例

#### 基本类型分析

```kotlin
fun analyzeType(type: KSType) {
    // 1. 基本信息
    val typeName = type.declaration.qualifiedName?.asString()
    println("Type: $typeName")
    println("Is nullable: ${type.isMarkedNullable}")

    // 2. 泛型参数
    if (type.arguments.isNotEmpty()) {
        println("Type arguments:")
        type.arguments.forEach { arg ->
            val argType = arg.type?.resolve()
            println("  - ${argType?.declaration?.simpleName?.asString()}")
        }
    }

    // 3. 函数类型
    if (type.isFunctionType) {
        println("This is a function type")
    }
}
```

#### 判断类型关系

```kotlin
fun isSubtypeOf(type: KSType, baseTypeName: String): Boolean {
    // 直接比较
    if (type.declaration.qualifiedName?.asString() == baseTypeName) {
        return true
    }

    // 检查父类型
    val classDecl = type.declaration as? KSClassDeclaration ?: return false
    return classDecl.superTypes.any { superType ->
        val resolved = superType.resolve()
        isSubtypeOf(resolved, baseTypeName)
    }
}

// 使用
if (isSubtypeOf(propertyType, "kotlin.collections.List")) {
    println("This property is a List")
}
```

#### 处理泛型类型

```kotlin
fun extractGenericType(type: KSType): String? {
    // 例如: List<User> → User
    if (type.arguments.isEmpty()) return null

    val firstArg = type.arguments.first()
    val argType = firstArg.type?.resolve()
    return argType?.declaration?.qualifiedName?.asString()
}

// 例如: Map<String, User> → (String, User)
fun extractMapTypes(type: KSType): Pair<String, String>? {
    if (type.arguments.size != 2) return null

    val keyType = type.arguments[0].type?.resolve()
        ?.declaration?.qualifiedName?.asString()
        ?: return null

    val valueType = type.arguments[1].type?.resolve()
        ?.declaration?.qualifiedName?.asString()
        ?: return null

    return keyType to valueType
}
```

#### 类型转换为字符串

```kotlin
fun typeToString(type: KSType): String {
    val baseName = type.declaration.simpleName.asString()

    // 处理泛型
    val typeArgs = if (type.arguments.isNotEmpty()) {
        val args = type.arguments.joinToString(", ") { arg ->
            arg.type?.resolve()?.let { typeToString(it) } ?: "*"
        }
        "<$args>"
    } else {
        ""
    }

    // 处理可空性
    val nullable = if (type.isMarkedNullable) "?" else ""

    return "$baseName$typeArgs$nullable"
}

// 输出示例:
// List<String> → "List<String>"
// Map<String, User?> → "Map<String, User?>"
// String? → "String?"
```

## 6. KSFile - 文件

### 定义

表示 Kotlin 源文件。

### 核心属性

```kotlin
interface KSFile : KSAnnotated {
    val fileName: String                              // 文件名
    val filePath: String                              // 完整路径
    val packageName: KSName                           // 包名
    val declarations: Sequence<KSDeclaration>         // 文件中的声明
    val annotations: Sequence<KSAnnotation>           // 文件级注解
}
```

### 使用示例

#### 文件信息提取

```kotlin
fun analyzeFile(file: KSFile) {
    println("File: ${file.fileName}")
    println("Path: ${file.filePath}")
    println("Package: ${file.packageName.asString()}")

    // 统计声明
    val classes = file.declarations.filterIsInstance<KSClassDeclaration>().count()
    val functions = file.declarations.filterIsInstance<KSFunctionDeclaration>().count()
    val properties = file.declarations.filterIsInstance<KSPropertyDeclaration>().count()

    println("Classes: $classes")
    println("Top-level functions: $functions")
    println("Top-level properties: $properties")
}
```

#### 查找文件中的特定类

```kotlin
fun findClassInFile(file: KSFile, className: String): KSClassDeclaration? {
    return file.declarations
        .filterIsInstance<KSClassDeclaration>()
        .find { it.simpleName.asString() == className }
}
```

## 7. KSValueParameter - 值参数

### 定义

表示函数或构造函数的参数。

### 核心属性

```kotlin
interface KSValueParameter : KSAnnotated {
    val name: KSName?                                 // 参数名
    val type: KSTypeReference                         // 参数类型
    val isVararg: Boolean                             // 是否是可变参数
    val isNoInline: Boolean                           // 是否 noinline
    val isCrossInline: Boolean                        // 是否 crossinline
    val hasDefault: Boolean                           // 是否有默认值
}
```

### 使用示例

```kotlin
fun analyzeParameter(param: KSValueParameter) {
    val name = param.name?.asString() ?: "unnamed"
    val type = param.type.resolve()
    val typeName = type.declaration.simpleName.asString()

    println("Parameter: $name: $typeName")
    println("Is vararg: ${param.isVararg}")
    println("Has default: ${param.hasDefault}")
    println("Is nullable: ${type.isMarkedNullable}")
}
```

## 8. KSTypeReference - 类型引用

### 定义

表示对类型的引用，需要通过 `resolve()` 获取实际类型。

### 核心属性

```kotlin
interface KSTypeReference : KSAnnotated {
    val element: KSReferenceElement                   // 引用元素
    val modifiers: Set<Modifier>                      // 修饰符

    fun resolve(): KSType                             // 解析为实际类型
}
```

### 使用示例

```kotlin
fun analyzeTypeReference(typeRef: KSTypeReference) {
    // 解析类型
    val type = typeRef.resolve()

    println("Type: ${type.declaration.qualifiedName?.asString()}")
    println("Is nullable: ${type.isMarkedNullable}")

    // 注解
    typeRef.annotations.forEach { annotation ->
        println("Annotation: @${annotation.shortName.asString()}")
    }
}
```

## 9. 实用工具函数

### 类型检查工具

```kotlin
object TypeUtils {
    fun isString(type: KSType): Boolean {
        return type.declaration.qualifiedName?.asString() == "kotlin.String"
    }

    fun isInt(type: KSType): Boolean {
        return type.declaration.qualifiedName?.asString() == "kotlin.Int"
    }

    fun isLong(type: KSType): Boolean {
        return type.declaration.qualifiedName?.asString() == "kotlin.Long"
    }

    fun isList(type: KSType): Boolean {
        val typeName = type.declaration.qualifiedName?.asString()
        return typeName == "kotlin.collections.List" ||
               typeName == "kotlin.collections.MutableList"
    }

    fun isMap(type: KSType): Boolean {
        val typeName = type.declaration.qualifiedName?.asString()
        return typeName == "kotlin.collections.Map" ||
               typeName == "kotlin.collections.MutableMap"
    }

    fun isPrimitive(type: KSType): Boolean {
        val typeName = type.declaration.qualifiedName?.asString()
        return typeName in setOf(
            "kotlin.Int", "kotlin.Long", "kotlin.Short", "kotlin.Byte",
            "kotlin.Float", "kotlin.Double", "kotlin.Boolean", "kotlin.Char"
        )
    }
}
```

### 注解查找工具

```kotlin
object AnnotationUtils {
    fun hasAnnotation(annotated: KSAnnotated, annotationName: String): Boolean {
        return annotated.annotations.any {
            it.shortName.asString() == annotationName
        }
    }

    fun getAnnotationValue(
        annotated: KSAnnotated,
        annotationName: String,
        paramName: String = "value"
    ): Any? {
        val annotation = annotated.annotations.find {
            it.shortName.asString() == annotationName
        } ?: return null

        return annotation.arguments
            .find { it.name?.asString() == paramName }
            ?.value
    }

    fun getAllAnnotations(annotated: KSAnnotated): Map<String, Map<String, Any?>> {
        return annotated.annotations.associate { annotation ->
            val name = annotation.shortName.asString()
            val params = annotation.arguments.associate { arg ->
                (arg.name?.asString() ?: "value") to arg.value
            }
            name to params
        }
    }
}
```

### 类结构分析工具

```kotlin
object ClassAnalyzer {
    fun isDataClass(classDecl: KSClassDeclaration): Boolean {
        return Modifier.DATA in classDecl.modifiers
    }

    fun isSealedClass(classDecl: KSClassDeclaration): Boolean {
        return Modifier.SEALED in classDecl.modifiers
    }

    fun isAbstractClass(classDecl: KSClassDeclaration): Boolean {
        return Modifier.ABSTRACT in classDecl.modifiers
    }

    fun isEnum(classDecl: KSClassDeclaration): Boolean {
        return classDecl.classKind == ClassKind.ENUM_CLASS
    }

    fun isInterface(classDecl: KSClassDeclaration): Boolean {
        return classDecl.classKind == ClassKind.INTERFACE
    }

    fun isObject(classDecl: KSClassDeclaration): Boolean {
        return classDecl.classKind == ClassKind.OBJECT
    }

    fun getPublicProperties(classDecl: KSClassDeclaration): List<KSPropertyDeclaration> {
        return classDecl.getAllProperties()
            .filter { Modifier.PUBLIC in it.modifiers || it.modifiers.isEmpty() }
            .toList()
    }

    fun getPublicFunctions(classDecl: KSClassDeclaration): List<KSFunctionDeclaration> {
        return classDecl.getAllFunctions()
            .filter { Modifier.PUBLIC in it.modifiers || it.modifiers.isEmpty() }
            .toList()
    }
}
```

## 10. 完整示例

### 实体分析器

```kotlin
class EntityAnalyzer {
    data class EntityInfo(
        val name: String,
        val packageName: String,
        val tableName: String?,
        val properties: List<PropertyInfo>,
        val primaryKey: PropertyInfo?,
        val indexes: List<String>,
        val superClass: String?
    )

    data class PropertyInfo(
        val name: String,
        val type: String,
        val isNullable: Boolean,
        val columnName: String?,
        val isPrimaryKey: Boolean,
        val isUnique: Boolean
    )

    fun analyze(classDecl: KSClassDeclaration): EntityInfo {
        // 1. 基本信息
        val name = classDecl.simpleName.asString()
        val packageName = classDecl.packageName.asString()

        // 2. 表名（从 @Table 注解）
        val tableName = AnnotationUtils.getAnnotationValue(
            classDecl, "Table", "name"
        ) as? String

        // 3. 属性信息
        val properties = classDecl.getAllProperties().map { property ->
            analyzeProperty(property)
        }.toList()

        // 4. 主键
        val primaryKey = properties.find { it.isPrimaryKey }

        // 5. 索引
        val indexes = extractIndexes(classDecl)

        // 6. 父类
        val superClass = classDecl.superTypes
            .map { it.resolve().declaration }
            .filterIsInstance<KSClassDeclaration>()
            .filter { it.classKind == ClassKind.CLASS }
            .firstOrNull()
            ?.qualifiedName?.asString()

        return EntityInfo(
            name = name,
            packageName = packageName,
            tableName = tableName,
            properties = properties,
            primaryKey = primaryKey,
            indexes = indexes,
            superClass = superClass
        )
    }

    private fun analyzeProperty(property: KSPropertyDeclaration): PropertyInfo {
        val type = property.type.resolve()

        return PropertyInfo(
            name = property.simpleName.asString(),
            type = type.declaration.qualifiedName?.asString() ?: "Unknown",
            isNullable = type.isMarkedNullable,
            columnName = AnnotationUtils.getAnnotationValue(
                property, "Column", "name"
            ) as? String,
            isPrimaryKey = AnnotationUtils.hasAnnotation(property, "Id"),
            isUnique = (AnnotationUtils.getAnnotationValue(
                property, "Column", "unique"
            ) as? Boolean) ?: false
        )
    }

    private fun extractIndexes(classDecl: KSClassDeclaration): List<String> {
        // 从 @Table 注解提取索引信息
        // 简化实现
        return emptyList()
    }
}
```

### 代码生成器辅助类

```kotlin
class CodeGeneratorHelper {
    fun generateEntityDTO(classDecl: KSClassDeclaration): String {
        val className = classDecl.simpleName.asString()
        val packageName = classDecl.packageName.asString()

        val properties = classDecl.getAllProperties()
            .filter { Modifier.PUBLIC in it.modifiers || it.modifiers.isEmpty() }
            .toList()

        return buildString {
            appendLine("package $packageName.dto")
            appendLine()
            appendLine("data class ${className}DTO(")
            properties.forEachIndexed { index, property ->
                val name = property.simpleName.asString()
                val type = typeToString(property.type.resolve())
                append("    val $name: $type")
                if (index < properties.size - 1) {
                    appendLine(",")
                } else {
                    appendLine()
                }
            }
            appendLine(")")
        }
    }

    private fun typeToString(type: KSType): String {
        val baseName = type.declaration.simpleName.asString()
        val nullable = if (type.isMarkedNullable) "?" else ""

        val typeArgs = if (type.arguments.isNotEmpty()) {
            val args = type.arguments.joinToString(", ") { arg ->
                arg.type?.resolve()?.let { typeToString(it) } ?: "*"
            }
            "<$args>"
        } else {
            ""
        }

        return "$baseName$typeArgs$nullable"
    }
}
```

## 参考资源

- [KSP 官方文档 - API Reference](https://kotlinlang.org/docs/ksp-reference.html)
- [KSP API JavaDoc](https://kotlin.github.io/symbol-processing/api/)
- [KSP GitHub](https://github.com/google/ksp)

## 许可

与主项目相同
