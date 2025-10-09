# Velocity 模板迁移指南

本文档提供从字符串模板迁移到 Velocity 模板的完整指南。

## 目录

- [概述](#概述)
- [为什么使用 Velocity](#为什么使用-velocity)
- [快速开始](#快速开始)
- [迁移步骤](#迁移步骤)
- [语法对照](#语法对照)
- [实战示例](#实战示例)
- [最佳实践](#最佳实践)
- [常见问题](#常见问题)

## 概述

cap4k-ddd-codegen-gradle-plugin 现在支持两种模板引擎：

1. **字符串模板** (原有方式)：简单的变量替换，使用 `${variable}` 语法
2. **Velocity 模板** (新增)：强大的模板引擎，支持条件判断、循环、宏等高级特性

**重要**：两种模板引擎完全兼容，可以在同一项目中混用，无需立即迁移现有模板。

## 为什么使用 Velocity

### 字符串模板的限制

```kotlin
// 字符串模板 - 只能做简单替换
{
    "type": "file",
    "name": "${Entity}.java",
    "format": "raw",
    "data": "package ${basePackage}.domain;\n\npublic class ${Entity} {\n    private String id;\n    // 无法根据字段列表动态生成\n}"
}
```

### Velocity 的优势

```velocity
## Velocity 模板 - 支持条件和循环
package ${basePackage}.domain;

#if($Entity.endsWith("Entity"))
@Entity
#end
public class ${Entity} {
    @Id
    private String id;

#foreach($field in $fields)
    private ${field.type} ${field.name};
#end

#if($includeGetters)
#foreach($field in $fields)
    public ${field.type} get${StringUtils.capitalize($field.name)}() {
        return ${field.name};
    }
#end
#end
}
```

**优势对比**：

| 特性   | 字符串模板 | Velocity 模板                      |
|------|-------|----------------------------------|
| 变量替换 | ✅     | ✅                                |
| 条件判断 | ❌     | ✅ `#if/#elseif/#else`            |
| 循环迭代 | ❌     | ✅ `#foreach`                     |
| 宏定义  | ❌     | ✅ `#macro`                       |
| 工具类  | ❌     | ✅ `$StringUtils`, `$DateUtils` 等 |
| 模板包含 | ❌     | ✅ `#parse`                       |
| 代码复用 | 低     | 高                                |
| 可维护性 | 低     | 高                                |

## 快速开始

### 1. 最小化迁移示例

**原字符串模板**：

```json
{
  "type": "file",
  "name": "${Entity}.java",
  "format": "raw",
  "data": "package ${basePackage}.domain;\n\npublic class ${Entity} {}"
}
```

**迁移后 Velocity 模板**：

```json
{
  "type": "file",
  "name": "${Entity}.java",
  "format": "velocity",
  "data": "package ${basePackage}.domain;\n\npublic class ${Entity} {}"
}
```

**改动**：只需将 `"format": "raw"` 改为 `"format": "velocity"` (或 `"vm"`)。

### 2. 使用外部 .vm 文件

**步骤**：

1. 在 `src/main/resources/vm/` 创建模板文件 `Entity.java.vm`：

```velocity
package ${basePackage}.domain.aggregates.${aggregate};

import jakarta.persistence.*;

@Entity
@Table(name = "${StringUtils.camelToSnake($Entity)}")
public class ${Entity} {
    @Id
    private String id;
}
```

2. 在模板 JSON 中引用：

```json
{
  "type": "file",
  "name": "${Entity}.java",
  "format": "velocity",
  "data": "vm/Entity.java.vm"
}
```

或使用 `format: "url"` 先加载文件：

```json
{
  "type": "file",
  "name": "${Entity}.java",
  "format": "url",
  "data": "vm/Entity.java.vm"
}
```

**注意**：使用 `"format": "url"` 时，TemplateNode 会先加载文件内容，然后在 `renderFile()` 阶段检测到 Velocity 格式并进行渲染。

## 迁移步骤

### Step 1: 识别迁移候选

优先迁移以下场景的模板：

1. **重复代码多**：同样的代码结构在多个模板中重复
2. **需要条件生成**：根据配置决定是否生成某些代码
3. **需要循环生成**：遍历字段列表、方法列表等
4. **复杂格式化**：需要字符串大小写转换、命名转换等

### Step 2: 创建 .vm 模板文件

在 `src/main/resources/vm/` 下创建目录结构：

```
vm/
├── common/
│   └── macros.vm          # 通用宏定义
├── entity/
│   ├── Entity.java.vm     # 实体类模板
│   └── ValueObject.java.vm
├── repository/
│   └── Repository.java.vm # 仓储接口模板
└── aggregate/
    └── Aggregate.md.vm    # 聚合文档模板
```

### Step 3: 编写 Velocity 模板

**使用工具类**：

```velocity
## 工具类自动注入，可直接使用
$StringUtils.capitalize("userName")    ## 输出: UserName
$StringUtils.camelToSnake("userName")  ## 输出: user_name
$DateUtils.now()                       ## 输出: 2025-10-09
$TypeUtils.isString("VARCHAR")         ## 输出: true
```

**使用条件判断**：

```velocity
#if($generateGetterSetter)
    public String getId() { return id; }
    public void setId(String id) { this.id = id; }
#end

#if($TypeUtils.isString($fieldType))
    @Column(length = 255)
#elseif($TypeUtils.isDate($fieldType))
    @Temporal(TemporalType.TIMESTAMP)
#else
    @Column
#end
```

**使用循环**：

```velocity
#foreach($field in $fields)
    private ${field.type} ${field.name};
#end

## 带索引的循环
#foreach($field in $fields)
    ## $foreach.index: 0-based index
    ## $foreach.count: 1-based count
    ## $foreach.hasNext: 是否有下一个元素
    private ${field.type} field${foreach.count};#if($foreach.hasNext)#end

#end
```

**使用宏**：

```velocity
## 定义宏
#macro(getter $type $name)
    public ${type} get${StringUtils.capitalize($name)}() {
        return ${name};
    }
#end

## 使用宏
#foreach($field in $fields)
#getter($field.type $field.name)
#end
```

### Step 4: 引用宏文件

```velocity
## 在模板开头引用公共宏
#parse("vm/common/macros.vm")

## 使用宏
#fileHeader("${Entity} 实体类", "cap4k-codegen")
package ${basePackage}.domain;

public class ${Entity} {
#foreach($field in $fields)
#entityField($field)
#end
}
```

### Step 5: 更新模板配置

在模板 JSON 配置中更新 `format` 字段：

```json
{
  "type": "file",
  "name": "${Entity}.java",
  "format": "velocity",
  // 或 "vm"
  "data": "vm/entity/Entity.java.vm"
}
```

### Step 6: 测试验证

运行代码生成任务：

```bash
./gradlew generateDDD
```

检查生成的代码是否符合预期。

## 语法对照

### 变量引用

| 字符串模板            | Velocity 模板                 | 说明               |
|------------------|-----------------------------|------------------|
| `${variable}`    | `$variable` 或 `${variable}` | Velocity 两种写法都支持 |
| `${basePackage}` | `$basePackage`              | 简写形式             |
| `${entity.name}` | `$entity.name`              | 对象属性访问           |

### 注释

| 语法          | 说明   | 示例             |
|-------------|------|----------------|
| `##`        | 单行注释 | `## 这是注释`      |
| `#* ... *#` | 多行注释 | `#* 多行注释内容 *#` |

### 条件判断

```velocity
## if-elseif-else
#if($condition)
    ## 条件为真
#elseif($anotherCondition)
    ## 另一个条件
#else
    ## 都不满足
#end

## 常用条件表达式
#if($variable)              ## 变量存在且非 null
#if($variable == "value")   ## 相等判断
#if($variable != "value")   ## 不等判断
#if($number > 0)            ## 数值比较
#if($list.isEmpty())        ## 调用方法
#if(!$variable)             ## 取反
```

### 循环

```velocity
## foreach 循环
#foreach($item in $list)
    $item.name
#end

## 循环变量
#foreach($field in $fields)
    字段索引: $foreach.index      ## 0-based
    字段序号: $foreach.count      ## 1-based
    是否最后: $foreach.hasNext    ## boolean
    是否第一: $foreach.first      ## boolean
    是否最后: $foreach.last       ## boolean
#end

## 循环控制
#foreach($item in $list)
    #if($item.skip)
        #break           ## 跳出循环
    #end
    处理 $item
#end
```

### 宏定义

```velocity
## 定义无参宏
#macro(simpleMacro)
    固定内容
#end

## 定义带参宏
#macro(fieldGetter $type $name)
    public ${type} get${StringUtils.capitalize($name)}() {
        return ${name};
    }
#end

## 调用宏
#simpleMacro()
#fieldGetter("String" "userName")
```

### 包含其他模板

```velocity
## 包含并解析模板
#parse("vm/common/macros.vm")

## 包含但不解析(作为文本)
#include("static-content.txt")
```

## 实战示例

### 示例 1: 实体类生成

**需求**：根据数据库表生成 JPA 实体类

**Velocity 模板** (`vm/entity/Entity.java.vm`)：

```velocity
#parse("vm/common/macros.vm")
#fileHeader("${Entity} 实体类", "cap4k-codegen")
package ${basePackage}.domain.aggregates.${aggregate};

import com.only4.cap4k.ddd.core.domain.aggregate.annotation.Aggregate;
import jakarta.persistence.*;
import org.hibernate.annotations.*;

@Aggregate(aggregate = "${aggregate}", type = "entity", name = "${Entity}"#if($isRoot), root = true#end)
@Entity
@Table(name = "${StringUtils.camelToSnake($Entity)}")
@DynamicInsert
@DynamicUpdate
public class ${Entity}#if($entityBaseClass) extends ${entityBaseClass}#end {

    @Id
    @GeneratedValue(generator = "${idGenerator}")
    private String id;

#foreach($field in $fields)
#if($field.comment)
    /**
     * ${field.comment}
     */
#end
#if($TypeUtils.isString($field.type))
    @Column(name = "${StringUtils.camelToSnake($field.name)}", length = ${field.length}#if(!$field.nullable), nullable = false#end)
#elseif($TypeUtils.isDate($field.type))
    @Temporal(TemporalType.TIMESTAMP)
    @Column(name = "${StringUtils.camelToSnake($field.name)}"#if(!$field.nullable), nullable = false#end)
#elseif($TypeUtils.isNumber($field.type))
    @Column(name = "${StringUtils.camelToSnake($field.name)}"#if(!$field.nullable), nullable = false#end)
#else
    @Column(name = "${StringUtils.camelToSnake($field.name)}")
#end
    private ${field.type} ${field.name};

#end
    // Getters and Setters
#foreach($field in $fields)
    public ${field.type} get${StringUtils.capitalize($field.name)}() {
        return ${field.name};
    }

    public void set${StringUtils.capitalize($field.name)}(${field.type} ${field.name}) {
        this.${field.name} = ${field.name};
    }

#end
}
```

### 示例 2: Repository 接口生成

**Velocity 模板** (`vm/repository/Repository.java.vm`)：

```velocity
#parse("vm/common/macros.vm")
#fileHeader("${Entity} 仓储接口", "cap4k-codegen")
package ${basePackage}.adapter.domain.repositories;

import ${basePackage}.domain.aggregates.${aggregate}.${Entity};
import com.only4.cap4k.ddd.core.domain.repo.Repository;

#if($repositorySupportQuerydsl)
import com.only4.cap4k.ddd.repo.jpa.querydsl.AbstractQuerydslRepository;
#else
import com.only4.cap4k.ddd.repo.jpa.AbstractJpaRepository;
#end

/**
 * ${Entity} 仓储接口
 *
 * @author cap4k-codegen
 * @date ${DateUtils.now()}
 */
public interface ${Entity}Repository extends Repository<${Entity}> {
#if($customMethods)

#foreach($method in $customMethods)
    /**
     * ${method.comment}
     */
    ${method.returnType} ${method.name}(#foreach($param in $method.params)${param.type} ${param.name}#if($foreach.hasNext), #end#end);

#end
#end
}
```

### 示例 3: 聚合文档生成

**Velocity 模板** (`vm/aggregate/Aggregate.md.vm`)：

```velocity
# ${StringUtils.capitalize($aggregate)} 聚合

> 生成时间: ${DateUtils.datetime()}

## 概述

${aggregateDescription}

## 实体列表

#foreach($entity in $entities)
### ${entity.name}

**说明**: ${entity.description}

**表名**: `${StringUtils.camelToSnake($entity.name)}`

**字段列表**:

| 字段名 | 类型 | 说明 | 可空 |
|--------|------|------|------|
#foreach($field in $entity.fields)
| ${field.name} | ${field.type} | ${field.comment} | #if($field.nullable)是#else否#end |
#end

#end

## 仓储接口

#foreach($repo in $repositories)
- `${repo.name}`: ${repo.description}
#end

## 领域事件

#foreach($event in $domainEvents)
- `${event.name}`: ${event.description}
#end
```

### 示例 4: 使用通用宏

**公共宏定义** (`vm/common/macros.vm`)：

```velocity
## 文件头注释
#macro(fileHeader $description $author)
/**
 * ${description}
 *
 * @author ${author}
 * @date ${DateUtils.now()}
 */
#end

## JPA 字段注解
#macro(jpaColumn $field)
#if($field.comment)
    /**
     * ${field.comment}
     */
#end
#if($TypeUtils.isString($field.type))
    @Column(name = "${StringUtils.camelToSnake($field.name)}", length = ${field.length}#if(!$field.nullable), nullable = false#end)
#elseif($TypeUtils.isDate($field.type))
    @Temporal(TemporalType.TIMESTAMP)
    @Column(name = "${StringUtils.camelToSnake($field.name)}"#if(!$field.nullable), nullable = false#end)
#else
    @Column(name = "${StringUtils.camelToSnake($field.name)}"#if(!$field.nullable), nullable = false#end)
#end
    private ${field.type} ${field.name};
#end

## Getter 方法
#macro(getter $type $name)
    public ${type} get${StringUtils.capitalize($name)}() {
        return ${name};
    }
#end

## Setter 方法
#macro(setter $type $name)
    public void set${StringUtils.capitalize($name)}(${type} ${name}) {
        this.${name} = ${name};
    }
#end

## Getter/Setter 方法对
#macro(getterSetter $type $name)
#getter($type $name)

#setter($type $name)
#end
```

**使用宏的模板**：

```velocity
#parse("vm/common/macros.vm")

#fileHeader("User 实体", "cap4k-codegen")
package ${basePackage}.domain;

public class User {
    private String id;
    private String name;

#getterSetter("String" "id")

#getterSetter("String" "name")
}
```

## 最佳实践

### 1. 模板组织

```
vm/
├── common/
│   ├── macros.vm          # 通用宏
│   ├── imports.vm         # 通用导入
│   └── copyright.vm       # 版权声明
├── entity/
│   ├── Entity.java.vm     # 实体模板
│   ├── ValueObject.java.vm
│   └── Enum.java.vm
├── repository/
│   └── Repository.java.vm
└── application/
    ├── Command.java.vm
    ├── Query.java.vm
    └── CommandHandler.java.vm
```

### 2. 宏的使用原则

- **单一职责**：每个宏只负责一个功能
- **参数清晰**：宏参数名称要有明确含义
- **文档注释**：在宏定义前添加说明注释

```velocity
## 生成 JPA 实体字段及注解
## 参数:
##   $field - 字段对象,包含 type, name, comment, nullable, length 等属性
#macro(entityField $field)
    ## 实现...
#end
```

### 3. 条件判断优化

**不推荐**：

```velocity
#if($field.type == "String" || $field.type == "VARCHAR" || $field.type == "TEXT")
    @Column(length = 255)
#end
```

**推荐**：

```velocity
#if($TypeUtils.isString($field.type))
    @Column(length = 255)
#end
```

### 4. 循环中的性能优化

**不推荐**：每次循环都调用方法

```velocity
#foreach($field in $fields)
    ## 每次都调用 getFields()
    字段总数: $entity.getFields().size()
#end
```

**推荐**：提前获取

```velocity
#set($fieldCount = $fields.size())
#foreach($field in $fields)
    字段总数: $fieldCount
#end
```

### 5. 空值处理

```velocity
## 使用默认值
${variable}#if(!$variable)默认值#end

## 或使用 Velocity 的 quiet reference
$!variable  ## 如果 variable 为 null,输出空字符串而不是 $variable

## 安全调用
#if($object && $object.property)
    $object.property
#end
```

### 6. 字符串拼接

**不推荐**：

```velocity
#set($fullName = $firstName + " " + $lastName)
```

**推荐**：

```velocity
#set($fullName = "${firstName} ${lastName}")
```

### 7. 模板复用

将通用逻辑提取到宏文件：

```velocity
## common/entity-utils.vm
#macro(generateFields $fields)
#foreach($field in $fields)
    private ${field.type} ${field.name};
#end
#end

#macro(generateGettersSetters $fields)
#foreach($field in $fields)
#getterSetter($field.type $field.name)
#end
#end
```

使用时：

```velocity
#parse("vm/common/entity-utils.vm")

public class ${Entity} {
#generateFields($fields)

#generateGettersSetters($fields)
}
```

## 常见问题

### Q1: Velocity 模板中 `$` 符号冲突

**问题**：需要输出字面量 `$` 符号

**解决**：使用转义

```velocity
## 方式 1: 使用 \$
价格: \$100

## 方式 2: 使用 ${symbol_dollar}
价格: ${symbol_dollar}100
```

### Q2: 模板渲染报错 "Unresolved reference"

**问题**：引用了不存在的变量

**解决**：

1. 使用 quiet reference：`$!variable`
2. 添加存在性检查：`#if($variable)...$end`
3. 检查上下文中是否真的存在该变量

### Q3: 循环中最后一个元素不需要逗号

**解决**：使用 `$foreach.hasNext`

```velocity
#foreach($item in $list)
    "${item}"#if($foreach.hasNext),#end

#end
```

输出：

```
    "item1",
    "item2",
    "item3"
```

### Q4: 如何在 Velocity 中调用 Java 方法

**解决**：使用工具类

```velocity
## 调用 StringUtils 的方法
$StringUtils.capitalize("hello")  ## 输出: Hello
$StringUtils.camelToSnake("userName")  ## 输出: user_name

## 调用对象的方法
$entity.getName()
$list.size()
$map.get("key")
```

### Q5: 如何在模板中定义局部变量

**解决**：使用 `#set`

```velocity
#set($localVar = "value")
#set($sum = $a + $b)
#set($upperName = $StringUtils.capitalize($name))

使用变量: $localVar
```

### Q6: 模板中如何处理多行字符串

**解决**：

```velocity
## 方式 1: 直接换行
#set($multiline = "第一行
第二行
第三行")

## 方式 2: 使用宏
#macro(multilineContent)
第一行内容
第二行内容
第三行内容
#end
```

### Q7: 如何在 Velocity 中使用正则表达式

**解决**：Velocity 本身不支持正则，但可以通过工具类实现

```kotlin
// 在 Kotlin 中添加工具类
object RegexUtils {
    @JvmStatic
    fun matches(text: String, pattern: String): Boolean {
        return text.matches(Regex(pattern))
    }

    @JvmStatic
    fun replace(text: String, pattern: String, replacement: String): String {
        return text.replace(Regex(pattern), replacement)
    }
}
```

```velocity
## 在模板中使用
#if($RegexUtils.matches($fieldName, "[A-Z].*"))
    字段名以大写字母开头
#end
```

### Q8: 字符串模板和 Velocity 模板可以混用吗

**答**：可以！在同一个项目中可以混用两种模板：

```json
[
  {
    "type": "file",
    "name": "Simple.java",
    "format": "raw",  // 字符串模板
    "data": "package ${basePackage};\n\npublic class Simple {}"
  },
  {
    "type": "file",
    "name": "Complex.java",
    "format": "velocity",  // Velocity 模板
    "data": "vm/Complex.java.vm"
  }
]
```

### Q9: 如何调试 Velocity 模板

**方法**：

1. **输出调试信息**：

```velocity
## 输出变量值
DEBUG: basePackage = $basePackage
DEBUG: entity = $entity
```

2. **检查变量是否存在**：

```velocity
#if($variable)
    变量存在: $variable
#else
    变量不存在
#end
```

3. **输出完整上下文**：

```velocity
## 列出所有可用变量
#foreach($key in $context.keys)
    $key = $context.get($key)
#end
```

### Q10: 迁移后性能如何

**答**：Velocity 模板的性能与字符串模板相当，甚至在复杂场景下更优：

- **首次渲染**：Velocity 需要解析模板，略慢
- **后续渲染**：Velocity 有缓存机制，性能接近
- **复杂逻辑**：Velocity 直接在模板中处理，比多次字符串替换更快

性能影响可忽略，实际瓶颈通常在数据库查询和文件 I/O。

## 总结

Velocity 模板迁移是渐进式的，建议：

1. ✅ **新模板优先使用 Velocity**
2. ✅ **复杂模板优先迁移**
3. ✅ **简单模板可保持不变**
4. ✅ **充分利用宏和工具类**
5. ✅ **编写可复用的模板组件**

通过 Velocity 模板，可以大幅提升代码生成的灵活性和可维护性，减少重复代码，提高开发效率。
