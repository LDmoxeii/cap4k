# CLAUDE_CN.md

这个文件为 Claude Code (claude.ai/code) 在这个代码仓库中工作时提供中文指导。

## 构建命令

这个项目使用 Gradle 和 Kotlin DSL：

- `./gradlew build` - 构建项目
- `./gradlew check` - 运行所有检查包括测试
- `./gradlew clean` - 清理构建输出
- `./gradlew test` - 只运行测试
- `./gradlew test --tests "*ClassName*"` - 运行特定测试类

## 架构概览

Cap4k 是一个面向 Kotlin/JVM 应用程序的领域驱动设计（DDD）框架，集成了 Spring Boot。项目遵循多模块结构和 DDD 架构模式。

### 模块结构

#### 激活的模块

- **ddd-core** - 核心 DDD 框架接口和实现（纯接口，无依赖）

#### 可用但未激活的模块（在设置中被注释）

- **ddd-distributed-locker-jdbc** - 基于 JDBC 的分布式锁
- **ddd-distributed-snowflake** - Snowflake 算法的分布式 ID 生成
- **ddd-domain-event-jpa** - 基于 JPA 的事件溯源和事件存储
- **ddd-domain-repo-jpa** - 基于 JPA 的仓储实现和工作单元

### 核心架构

框架围绕中心的 **中介者（Mediator）** 模式构建，通过统一接口提供对所有 DDD 组件的访问。

#### 核心组件

- **Mediator/X** - 中央访问点，提供两种接口：
    - 详细接口：`Mediator.repositories()`、`Mediator.commands()`
    - 简洁接口：`X.repo()`、`X.cmd()`、`X.qry()`
- **聚合（Aggregates）** - 支持工厂模式的领域聚合
- **仓储（Repositories）** - 数据持久化抽象层
- **工作单元（Unit of Work）** - 事务管理模式
- **CQRS** - 请求/命令/查询处理
- **事件（Events）** - 领域事件和集成事件系统

#### 包组织结构

`com.only4.cap4k.ddd.core` 包含：
- `application/` - 应用服务层（命令、查询、事件、工作单元）
- `domain/` - 领域层（聚合、仓储、领域服务、事件）
- `share/` - 共享工具和常量

## 开发说明

- 使用 Kotlin 2.1.20 和 Spring Boot 3.1.12
- JVM 工具链设置为 Java 17
- 测试框架：JUnit 5 和用于模拟的 MockK
- 启用构建缓存和配置缓存以提高性能
- `buildSrc/` 中的约定插件用于共享构建逻辑
