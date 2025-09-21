# Cap4k DDD 代码生成 Gradle 插件

这是一个基于 cap4j-ddd-codegen-maven-plugin 移植的 Gradle 插件，用于从数据库 schema 生成 DDD 领域代码。

## 功能特性

- ✅ 从数据库表结构生成 Kotlin 实体类
- ✅ 支持聚合根和值对象
- ✅ 生成枚举类
- ✅ 生成 Schema 辅助类
- ✅ 支持 MySQL 和 PostgreSQL 数据库
- ✅ 可配置的代码生成模板
- ✅ 与 cap4k DDD 框架集成
- 🔄 生成仓储接口和实现 (TODO)
- 🔄 生成应用层设计元素 (TODO)

## 快速开始

### 1. 应用插件

在你的 `build.gradle.kts` 中：

```kotlin
plugins {
    id("com.only4.cap4k.ddd.codegen") version "1.0.0-SNAPSHOT"
}
```

### 2. 配置插件

```kotlin
cap4kCodegen {
    basePackage.set("com.example.domain")
    multiModule.set(false)

    database {
        url.set("jdbc:mysql://localhost:3306/test")
        username.set("root")
        password.set("password")
        schema.set("test")
        tables.set("user_*,order_*")
        ignoreTables.set("*_temp")
    }

    generation {
        generateSchema.set(true)
        generateAggregate.set(true)
        entityBaseClass.set("BaseEntity")
        versionField.set("version")
        deletedField.set("deleted")
    }
}
```

### 3. 运行代码生成

```bash
# 生成实体类
./gradlew genEntity

# 生成所有代码
./gradlew genAll
```

## 可用任务

- `genArch` - 生成项目架构结构
- `genEntity` - 从数据库表生成实体类
- `genRepository` - 生成仓储类 (TODO)
- `genDesign` - 生成设计元素 (TODO)
- `genAll` - 执行所有生成任务

## 配置选项

### 基础配置

| 属性                     | 类型      | 默认值   | 描述       |
|------------------------|---------|-------|----------|
| `basePackage`          | String  | -     | 基础包路径    |
| `multiModule`          | Boolean | false | 是否为多模块项目 |
| `archTemplate`         | String  | -     | 架构模板文件路径 |
| `archTemplateEncoding` | String  | UTF-8 | 模板文件编码   |
| `outputEncoding`       | String  | UTF-8 | 输出文件编码   |

### 数据库配置

| 属性             | 类型     | 默认值 | 描述        |
|----------------|--------|-----|-----------|
| `url`          | String | -   | 数据库连接URL  |
| `username`     | String | -   | 数据库用户名    |
| `password`     | String | -   | 数据库密码     |
| `schema`       | String | -   | 数据库Schema |
| `tables`       | String | ""  | 包含的表名模式   |
| `ignoreTables` | String | ""  | 忽略的表名模式   |

### 代码生成配置

| 属性                    | 类型      | 默认值     | 描述            |
|-----------------------|---------|---------|---------------|
| `generateSchema`      | Boolean | false   | 是否生成Schema辅助类 |
| `generateAggregate`   | Boolean | false   | 是否生成聚合封装类     |
| `entityBaseClass`     | String  | ""      | 实体基类          |
| `rootEntityBaseClass` | String  | ""      | 根实体基类         |
| `versionField`        | String  | version | 乐观锁字段名        |
| `deletedField`        | String  | deleted | 软删字段名         |

## 表注解支持

在数据库表注释中使用特殊注解来控制代码生成：

```sql
-- 标记为聚合根
CREATE TABLE user
    (
    id   BIGINT PRIMARY KEY,
    name VARCHAR(100)
    ) COMMENT '@AggregateRoot;@Aggregate=user;用户表';

-- 标记为值对象
CREATE TABLE user_address
    (
    id     BIGINT PRIMARY KEY,
    street VARCHAR(200)
    ) COMMENT '@ValueObject;@Aggregate=user;地址信息';

-- 枚举字段
ALTER TABLE user
    ADD COLUMN status INT COMMENT '@Enum=1:Active:激活,2:Inactive:未激活;用户状态';
```

支持的注解：

- `@AggregateRoot` - 标记为聚合根
- `@ValueObject` - 标记为值对象
- `@Aggregate=name` - 指定聚合名称
- `@Module=name` - 指定模块名称
- `@Enum=value:name:description` - 定义枚举值

## 生成的代码结构

```
src/main/kotlin/
└── com/example/domain/
    ├── entities/           # 实体类
    │   ├── User.kt
    │   └── UserAddress.kt
    ├── enums/             # 枚举类
    │   └── UserStatus.kt
    ├── meta/              # Schema辅助类
    │   ├── UserSchema.kt
    │   └── UserAddressSchema.kt
    └── aggregates/        # 聚合封装类
        └── AggUser.kt
```

## 与 Cap4k 框架集成

生成的代码完全兼容 cap4k DDD 框架：

```kotlin
@Aggregate(aggregate = "user", type = "entity", root = true)
@Entity
@Table(name = "user")
data class User : BaseEntity() {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "id")
    var id: Long = 0

    @Column(name = "name")
    var name: String = ""

    @Column(name = "status")
    var status: Int = 0
}
```

## 支持的数据库类型

- MySQL 5.7+
- PostgreSQL 10+
- SQL Server (计划支持)
- Oracle (计划支持)

## 版本历史

### 1.0.0-SNAPSHOT

- 首次发布
- 支持基本的实体代码生成
- MySQL 和 PostgreSQL 支持
- 枚举和 Schema 生成

## 贡献

欢迎提交 Issue 和 Pull Request！

## 许可证

与 cap4k 项目保持一致的许可证。
