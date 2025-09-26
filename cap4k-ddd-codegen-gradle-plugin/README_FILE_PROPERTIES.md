# 文件属性支持

Cap4k代码生成插件现在支持直接使用文件属性，而不仅仅是字符串路径。这提供了更好的类型安全性和IDE支持。

## 使用新的文件属性API

### 架构模板文件

**新方式（推荐）：**
```kotlin
cap4kCodegen {
    // 直接使用文件对象
    archTemplateFile.set(file("templates/arch-template.json"))
    // 或者使用 layout
    archTemplateFile.set(layout.projectDirectory.file("templates/arch-template.json"))
}
```

**旧方式（仍支持，但已废弃）：**
```kotlin
cap4kCodegen {
    archTemplate.set("templates/arch-template.json")
}
```

### 设计配置文件

**新方式（推荐）- 支持多文件：**
```kotlin
cap4kCodegen {
    // 支持多个设计配置文件
    designConfigFiles.from(
        file("config/base-design.conf"),
        file("config/module-a-design.conf"),
        file("config/module-b-design.conf")
    )

    // 或者使用文件集合
    designConfigFiles.setFrom(fileTree("config") { include("**/*.design") })

    // 单个文件也支持
    designConfigFiles.from(file("config/design.conf"))
}
```

**旧方式（仍支持，但已废弃）：**
```kotlin
cap4kCodegen {
    // 支持分号分隔的多文件路径
    designFile.set("config/design1.conf;config/design2.conf;config/design3.conf")

    // 单个文件
    designFile.set("config/design.conf")
}
```

## 优势

### 1. 类型安全
- 编译时检查文件路径
- IDE自动补全和导航支持
- 避免字符串路径错误

### 2. 相对路径处理
```kotlin
cap4kCodegen {
    // 自动相对于项目根目录
    archTemplateFile.set(file("src/main/resources/templates/arch.json"))

    // 支持不同的base directory
    designConfigFile.set(layout.buildDirectory.file("generated/design.conf"))
}
```

### 3. 文件存在性检查
```kotlin
// Gradle会在配置时检查文件是否存在（如果配置为必需）
cap4kCodegen {
    archTemplateFile.set(file("templates/arch.json"))
    // 如果文件不存在，Gradle会提供更好的错误信息
}
```

### 4. 任务依赖
```kotlin
// 可以轻松设置任务依赖
tasks.named("generateCode") {
    // 当模板文件改变时，自动重新执行任务
    inputs.file(cap4kCodegen.archTemplateFile)
}
```

## 向后兼容性

旧的字符串属性仍然支持，但会显示废弃警告：
- `archTemplate` → 使用 `archTemplateFile`
- `designFile` → 使用 `designConfigFiles`（支持多文件）

插件会按以下优先级处理配置：
1. **designConfigFiles**（最新，支持多文件）
2. **designConfigFile**（单文件，已废弃）
3. **designFile**（字符串路径，支持分号分隔多文件，已废弃）

## 迁移建议

1. **逐步迁移**：可以逐个配置项迁移，不需要一次性全部更改
2. **保持兼容**：现有配置继续工作，没有破坏性变更
3. **利用IDE支持**：新的文件属性提供更好的IDE智能提示

## Gradle文件属性的其他用法

### 多文件集合的高级用法

```kotlin
cap4kCodegen {
    // 从不同目录收集文件
    designConfigFiles.from(
        fileTree("src/main/resources/design") { include("*.conf") },
        fileTree("config/modules") { include("**/*.design") }
    )

    // 基于条件添加文件
    if (project.hasProperty("includeExtraDesigns")) {
        designConfigFiles.from("extra-designs.conf")
    }

    // 从其他项目或子模块获取
    designConfigFiles.from(
        project(":module-a").file("design.conf"),
        project(":module-b").file("design.conf")
    )

    // 使用Provider动态配置
    designConfigFiles.from(
        providers.systemProperty("design.files").map { paths ->
            paths.split(",").map { file(it.trim()) }
        }.orElse(listOf(file("default-design.conf")))
    )
}
```

### 任务依赖和增量构建

```kotlin
tasks.named("generateDesign") {
    // 当任何设计文件改变时，重新执行任务
    inputs.files(cap4kCodegen.designConfigFiles)

    // 确保设计文件存在
    doFirst {
        cap4kCodegen.designConfigFiles.files.forEach { file ->
            if (!file.exists()) {
                throw GradleException("Design file not found: ${file.absolutePath}")
            }
        }
    }
}
```

### 单文件 vs 多文件场景

```kotlin
cap4kCodegen {
    // 架构模板：通常是单个文件
    archTemplateFile.set(file("templates/arch.json"))

    // 设计配置：支持多文件组合
    designConfigFiles.from(
        file("base-entities.design"),     // 基础实体设计
        file("business-rules.design"),    // 业务规则设计
        file("integration-events.design") // 集成事件设计
    )
}
```