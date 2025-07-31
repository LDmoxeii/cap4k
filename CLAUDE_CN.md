# CLAUDE_CN.md

本文件为 Claude Code (claude.ai/code) 在此仓库中工作时提供中文指导。

## 构建命令

本项目使用 Gradle 与 Kotlin DSL：

- `./gradlew build` - 构建项目（仅构建活跃模块）
- `./gradlew check` - 运行所有检查包括测试
- `./gradlew clean` - 清理构建输出
- `./gradlew test` - 仅运行测试
- `./gradlew test --tests "*ClassName*"` - 运行特定测试类

## 架构概览

Cap4k 是一个面向 Kotlin/JVM 应用程序的领域驱动设计（DDD）框架，集成了 Spring Boot。项目遵循多模块结构和 DDD 架构模式。

### 模块结构

#### 活跃模块

- **ddd-core** - 核心 DDD 框架接口和实现（纯接口，无依赖）
- **ddd-domain-event-jpa** - 基于 JPA 的事件溯源和事件存储实现
- **ddd-domain-repo-jpa** - 基于 JPA 的仓储实现与工作单元
- **ddd-domain-repo-jpa-querydsl** - QueryDSL 集成，用于类型安全的查询构建

#### 可用但未激活的模块（在 settings 中注释）

- **ddd-distributed-locker-jdbc** - 基于 JDBC 的分布式锁
- **ddd-distributed-snowflake** - 分布式 ID 生成的雪花算法

### 核心架构

框架围绕中央**中介者**模式构建，通过统一接口提供对所有 DDD 组件的访问。

#### 关键组件

- **Mediator/X** - 中央访问点，具有双重接口：
  - 详细模式：`Mediator.repositories()`、`Mediator.commands()`
  - 简洁模式：`X.repo()`、`X.cmd()`、`X.qry()`
- **聚合** - 支持工厂模式的领域聚合
- **仓储** - 数据持久化抽象层，包含 JPA 和 QueryDSL 实现
- **工作单元** - 事务管理模式
- **CQRS** - 请求/命令/查询处理
- **事件** - 领域事件和集成事件系统

#### 中介者模式实现

`Mediator` 接口统一了对所有框架组件的访问。它实现了多个监督者接口，并提供详细和简洁的访问模式：

```kotlin
// 详细访问
Mediator.repositories().findById(id)
Mediator.commands().execute(command)

// 通过 X 类的简洁访问
X.repo().findById(id)
X.cmd().execute(command)
```

通过 Mediator 可访问的核心监督者：

- `AggregateFactorySupervisor` - 聚合的工厂模式
- `RepositorySupervisor` - 仓储访问和管理
- `AggregateSupervisor` - 聚合生命周期管理
- `DomainServiceSupervisor` - 领域服务执行
- `RequestSupervisor` - CQRS 请求处理（命令、查询、Saga）
- `IntegrationEventSupervisor` - 跨边界事件发布
- `UnitOfWork` - 事务管理

#### DDD 注解系统

框架使用注解来分类和组织领域组件：

**@Aggregate** - 标记类为领域聚合的一部分：

- `aggregate` - 聚合名称（例如："user"、"order"）
- `type` - 组件类型："entity"、"value-object"、"repository"、"factory"、"factory-payload"、"domain-event"、"specification"、"
  enum"
- `name` - 组件显示名称
- `root` - 此实体是否为聚合根
- `description` - 组件描述

**@DomainService** - 标记领域服务类
**@DomainEvent** - 标记领域事件类
**@IntegrationEvent** - 标记跨边界事件

#### 架构信息系统

`ArchInfoManager` 提供 DDD 架构的运行时内省：

- 扫描包中的注解类
- 按类型和聚合分类组件
- 构建分层架构元数据
- 通过 `ResolvedClasses` 数据包装器使用线程安全的懒加载初始化
- 为文档和工具提供 JSON 可序列化的架构信息

#### 包组织

`com.only4.cap4k.ddd.core` 包含：
- `application/` - 应用服务层（命令、查询、事件、工作单元）
  - `command/` - 命令处理
  - `query/` - 查询处理，支持列表/分页
  - `saga/` - Saga 编排
  - `event/` - 集成事件管理
  - `distributed/` - 分布式锁
- `domain/` - 领域层（聚合、仓储、领域服务、事件）
  - `aggregate/` - 聚合根、实体、值对象、规约
  - `repo/` - 仓储模式与持久化监听器
  - `service/` - 领域服务
  - `event/` - 领域事件发布和订阅
- `archinfo/` - 架构内省和元数据
- `share/` - 共享工具和常量

### 仓储实现

框架为不同查询需求提供多种仓储实现：

#### JPA 仓储 (`ddd-domain-repo-jpa`)

- `AbstractJpaRepository<ENTITY>` - 使用条件查询的基础 JPA 仓储
- `JpaPredicate<ENTITY>` - 使用 JPA Criteria API 的类型安全断言构建
- 支持标准 CRUD 操作、分页和自定义条件查询

#### QueryDSL 仓储 (`ddd-domain-repo-jpa-querydsl`)

- `AbstractQuerydslRepository<ENTITY>` - 基于 QueryDSL 的类型安全查询仓储
- `QuerydslPredicate<ENTITY>` - 使用 QueryDSL 的 BooleanBuilder 的流式断言构建器
- `QuerydslPredicateSupport` - 用于在框架和 QueryDSL 类型之间转换的工具对象
- 提供编译时查询验证和对复杂查询更好的 IDE 支持

**QueryDSL 集成特性：**

- 使用 `QuerydslPredicate.of(EntityClass.class).where(condition).orderBy(spec)` 进行类型安全的查询构建
- 框架断言和 QueryDSL 断言之间的自动转换
- 支持与 `OrderSpecifier` 集成的复杂排序
- 与 Spring Data 的 `QuerydslPredicateExecutor` 无缝集成

**使用模式：**

```kotlin
// 创建 QueryDSL 断言
val predicate = QuerydslPredicate.of(User::class.java)
  .where(QUser.user.name.eq("John"))
  .orderBy(QUser.user.createdAt.desc())

// 与仓储一起使用
val users = repository.find(predicate, persist = false)
```

### 技术栈

- Kotlin 2.1.20 与 Spring Boot 3.1.12
- Java 17 工具链
- JUnit 5 与 MockK 测试（推荐使用 Kotlin 测试断言）
- QueryDSL 用于类型安全的查询构建
- 启用构建缓存和配置缓存
- `buildSrc/` 中的约定插件用于共享构建逻辑

## 测试

使用 JUnit 5 与 Kotlin 测试断言和 MockK 进行模拟测试：

- `./gradlew test` - 运行所有测试
- `./gradlew test --tests "*ClassName*"` - 运行特定测试类
- `./gradlew test --tests "*ClassName.methodName*"` - 运行特定测试方法
- `./gradlew :module-name:test` - 运行特定模块的测试（例如：`:ddd-domain-repo-jpa-querydsl:test`）

测试文件位于 `src/test/kotlin`，包结构与主代码相同。

### 测试约定

- 使用 Kotlin 测试断言（`kotlin.test.*`）而不是 JUnit 断言，以获得更好的 Kotlin 集成
- 推荐使用中文 `@DisplayName` 注解，以提高测试报告的可读性
- 测试类应以 `Test` 后缀命名（例如：`QuerydslPredicateTest`）
- 在类和方法级别都使用 `@DisplayName` 进行描述

## 开发说明

- `gradle/libs.versions.toml` 中的版本目录管理所有依赖项版本
- 启用构建缓存和配置缓存以提升性能
- `buildSrc/` 中的约定插件提供共享构建逻辑
- 中文文档可在 `CLAUDE_CN.md` 中获取（更新 `CLAUDE.md` 时同步）

# 重要指令提醒

做被要求的事情；不多不少。
除非绝对必要，否则不要创建文件。
始终优先编辑现有文件而不是创建新文件。
除非用户明确请求，否则不要主动创建文档文件（*.md）或 README 文件。
