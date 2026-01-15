# CLAUDE_CN.md

本文件为 Claude Code (claude.ai/code) 在此仓库中工作时提供中文指导。

## 构建命令

本项目使用 Gradle 与 Kotlin DSL：

- `./gradlew build` - 构建项目（仅构建活跃模块）
- `./gradlew check` - 运行所有检查包括测试
- `./gradlew clean` - 清理构建输出
- `./gradlew test` - 仅运行测试
- `./gradlew test --tests "*ClassName*"` - 运行特定测试类
- `./gradlew test --tests "*ClassName.methodName*"` - 运行特定测试方法
- `./gradlew :module-name:test` - 运行特定模块的测试（例如：`:ddd-domain-repo-jpa-querydsl:test`）

## 架构概览

Cap4k 是一个面向 Kotlin/JVM 应用程序的领域驱动设计（DDD）框架，集成了 Spring Boot。项目遵循多模块结构和 DDD 架构模式。

### 模块结构

#### 活跃模块

- **ddd-core** - 核心 DDD 框架接口和实现（纯接口，无依赖）
- **ddd-application-request-jpa** - 基于 JPA 的请求/命令执行，支持重试和调度
- **ddd-domain-event-jpa** - 基于 JPA 的事件溯源和事件存储实现
- **ddd-domain-repo-jpa** - 基于 JPA 的仓储实现与工作单元
- **ddd-domain-repo-jpa-querydsl** - QueryDSL 集成，用于类型安全的查询构建
- **ddd-integration-event-rabbitmq** - 基于 RabbitMQ 的集成事件实现
- **ddd-integration-event-rocketmq** - 基于 RocketMQ 的集成事件实现
- **ddd-integration-event-http** - 基于 HTTP 的集成事件发布和订阅
- **ddd-integration-event-http-jpa** - HTTP 集成事件订阅的 JPA 持久化
- **ddd-distributed-saga-jpa** - 基于 JPA 的分布式 Saga 编排，支持补偿和归档
- **ddd-distributed-locker-jdbc** - 基于 JDBC 的分布式锁
- **ddd-distributed-snowflake** - 分布式 ID 生成的雪花算法
- **cap4k-ddd-console** - 管理控制台，提供监控事件、请求、Saga、锁和雪花 ID 的 HTTP 端点
- **cap4k-ddd-starter** - Spring Boot 自动配置启动器

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
- **Saga** - 带补偿逻辑的长期运行业务流程

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

### Saga 实现 (`ddd-distributed-saga-jpa`)

分布式 Saga 模块为长期运行的业务流程提供编排：

#### 关键组件

- `SagaRecord` - Saga 状态管理和流程跟踪接口
- `SagaRecordImpl` - 处理 Saga 生命周期、补偿和流程结果的实现
- `JpaSagaRecordRepository` - 基于 JPA 的持久化，具有归档能力
- `JpaSagaScheduleService` - 用于补偿重试和归档的调度服务
- `SagaManager` - 高级 Saga 管理和编排

#### Saga 特性

- **补偿逻辑** - 自动重试，可配置间隔和最大尝试次数
- **流程跟踪** - 跟踪单个 Saga 步骤的结果和异常处理
- **归档系统** - 将完成/过期的 Saga 移动到归档表以提升性能
- **分布式锁** - 防止并发 Saga 处理冲突
- **分区支持** - 针对大型数据集的自动 MySQL 表分区

### 控制台管理 (`cap4k-ddd-console`)

控制台模块提供用于监控和管理 DDD 组件的 HTTP 端点：

#### 控制台服务

- `EventConsoleService` - 搜索和重试领域/集成事件
- `RequestConsoleService` - 搜索和重试失败的请求
- `SagaConsoleService` - 监控 Saga 执行和重试失败的 Saga
- `LockerConsoleService` - 查看分布式锁并强制解锁
- `SnowflakeConsoleService` - 监控雪花 ID 工作者分配

每个控制台服务提供 REST 端点用于：

- 使用过滤器搜索记录（按时间、状态、类型、UUID）
- 重试失败的操作
- 查看操作统计信息

### HTTP 集成事件 (`ddd-integration-event-http`)

基于 HTTP 的跨服务通信集成事件系统：

#### 关键组件

- `HttpIntegrationEventPublisher` - 通过 HTTP POST 向注册的订阅者发布事件
- `HttpIntegrationEventSubscriberRegister` - 管理事件订阅和取消订阅
- `HttpIntegrationEventSubscriberAdapter` - 适配传入的 HTTP 事件通知
- `IntegrationEventHttpSubscribeCommand` - 注册事件通知的命令
- `IntegrationEventHttpUnsubscribeCommand` - 删除事件订阅的命令

#### 功能特性

- 使用 HTTP 端点的动态订阅管理
- 失败 HTTP 传递的自动重试逻辑
- 使用 JPA 的持久化订阅者注册表（与 `ddd-integration-event-http-jpa` 结合使用时）
- 支持事件过滤和路由

### 技术栈

- Kotlin 2.2.20 与 Spring Boot 3.1.12
- Java 17 工具链
- JUnit 5 与 MockK 测试（推荐使用 Kotlin 测试断言）
- QueryDSL 用于类型安全的查询构建
- 启用构建缓存和配置缓存
- `buildSrc/` 中的约定插件用于共享构建逻辑
- Spring Boot BOM 用于依赖版本管理
- **Kotlin JPA 插件** - 自动生成无参构造函数并为 JPA 兼容性使类/属性开放

### 构建系统

项目使用复杂的 Gradle 设置：

- **约定插件**：`buildSrc/src/main/kotlin/kotlin-jvm.gradle.kts` 提供共享构建逻辑
- **Kotlin Spring 插件**：自动为 Spring 注解类添加 `open` 修饰符以支持代理兼容性
- **Kotlin JPA 插件**：自动生成无参构造函数并为 Hibernate 代理兼容性使 JPA 实体 `open`
- **版本目录**：`gradle/libs.versions.toml` 集中管理依赖版本
- **平台依赖**：使用 Spring Boot BOM 确保依赖版本一致性
- **测试配置**：增强的测试设置，包括 2GB 堆内存、10 分钟超时和全面日志记录

#### 构建依赖模式

所有模块遵循一致的依赖模式：

```kotlin
dependencies {
    // 版本管理平台（仅核心模块）
    api(platform(libs.springBootDependencies))
    
    // 项目依赖
    implementation(project(":ddd-core"))
    
    // 实现依赖
    implementation(libs.fastjson)
    implementation(kotlin("reflect"))
    
    // 编译时依赖
    compileOnly(libs.springContext)
    
    // 测试平台（包含 Spring 测试依赖的模块）
    testImplementation(platform(libs.springBootDependencies))
    
    // 测试框架（所有模块保持一致）
    testImplementation(libs.mockk)
    testImplementation("org.junit.jupiter:junit-jupiter:5.10.2")
    testImplementation("org.jetbrains.kotlin:kotlin-test")
    testRuntimeOnly("org.junit.platform:junit-platform-launcher")
}
```

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

### MockK 测试模式

为复杂实体创建模拟对象时（尤其是在 Saga 测试中）：

```kotlin
// 对于有很多属性的实体，使用宽松模拟
val mockEntity = mockk<EntityClass>(relaxed = true) {
    every { id } returns "test-id"
    every { importantProperty } returns expectedValue
    // 只配置测试中实际使用的属性
}
```

对于 Saga 相关测试，确保所有访问的属性都被模拟：

- `sagaProcesses` 属性应返回 `mutableListOf()` 以确保归档测试正常工作
- 使用 `answers` 回调来模拟异常处理测试中的状态变化

## 开发说明

- `gradle/libs.versions.toml` 中的版本目录管理所有依赖项版本
- 启用构建缓存和配置缓存以提升性能
- `buildSrc/` 中的约定插件提供共享构建逻辑
- 中文文档可在 `CLAUDE_CN.md` 中获取（更新 `CLAUDE.md` 时同步）

### Kotlin 开发指南

- 使用 `ENTITY: Any` 类型边界以提高类型安全性
- 对于流式接口和方法链，优先使用 `apply` 作用域函数
- 使用 `companion object` 而不是静态方法进行工厂方法
- 适当利用 Kotlin 的空安全性，使用 `?` 和 `!!` 操作符
- 使用类型别名解决命名冲突（例如：`import com.querydsl.core.types.Predicate as QuerydslPredicate`）

### 仓储实现工作指南

- 添加新仓储实现时，继承核心 `Repository<ENTITY>` 接口
- 实现 `supportPredicateClass()` 返回特定的断言类型
- 在 `@PostConstruct` 初始化方法中注册断言和仓储反射器以进行框架集成
- 使用 `QuerydslPredicateSupport` 工具在框架和 QueryDSL 类型之间转换

### Saga 开发指南

- Saga 流程应该是幂等的，以处理重试场景
- 对失败的 Saga 步骤使用适当的错误处理和补偿逻辑
- 为高容量 Saga 表考虑分区策略
- 实施适当的归档以在 Saga 容量增长时保持性能

### 服务构造函数模式

关键框架服务遵循特定的构造函数模式，在测试中必须维护：

#### DefaultRequestSupervisor 构造函数

```kotlin
DefaultRequestSupervisor(
    requestHandlers: List<RequestHandler<*, *>>,
    requestInterceptors: List<RequestInterceptor<*, *>>,
    validator: Validator?,
    requestRecordRepository: RequestRecordRepository,
    svcName: String,
    threadPoolSize: Int,
    threadFactoryClassName: String
)
```

#### DefaultSagaSupervisor 构造函数

```kotlin
DefaultSagaSupervisor(
    requestHandlers: List<RequestHandler<*, *>>,
    requestInterceptors: List<RequestInterceptor<*, *>>,
    validator: Validator?,
    sagaRecordRepository: SagaRecordRepository,
    svcName: String,
    threadPoolSize: Int = 10,
    threadFactoryClassName: String = ""
)
```

#### DefaultEventPublisher 构造函数

```kotlin
DefaultEventPublisher(
    eventSubscriberManager: EventSubscriberManager,
    integrationEventPublishers: List<IntegrationEventPublisher>,
    eventRecordRepository: EventRecordRepository,
    eventMessageInterceptorManager: EventMessageInterceptorManager,
    domainEventInterceptorManager: DomainEventInterceptorManager,
    integrationEventInterceptorManager: IntegrationEventInterceptorManager,
    integrationEventPublisherCallback: IntegrationEventPublisher.PublishCallback,
    threadPoolSize: Int
)
```

#### DefaultEventInterceptorManager 构造函数

```kotlin
DefaultEventInterceptorManager(
    eventMessageInterceptors: List<EventMessageInterceptor>,
    eventInterceptors: List<EventInterceptor>,
    eventRecordRepository: EventRecordRepository
)
```

#### JpaRequestScheduleService 构造函数

```kotlin
JpaRequestScheduleService(
    requestManager: RequestManager,
    locker: Locker,
    compensationLockerKey: String,
    archiveLockerKey: String,
    enableAddPartition: Boolean,
    jdbcTemplate: JdbcTemplate
)
```

注意：在最近的更新中，`JpaRequestScheduleService` 移除了 `svcName` 参数。

更新这些服务时，确保所有测试构造函数都相应更新。

# 重要指令提醒

做被要求的事情；不多不少。
除非绝对必要，否则不要创建文件。
始终优先编辑现有文件而不是创建新文件。
除非用户明确请求，否则不要主动创建文档文件（*.md）或 README 文件。

# 重要指令提醒
做被要求的事情；不多不少。
除非绝对必要，否则不要创建文件。
始终优先编辑现有文件而不是创建新文件。
除非用户明确请求，否则不要主动创建文档文件（*.md）或 README 文件。
