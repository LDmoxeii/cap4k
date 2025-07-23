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

### 核心组件

框架围绕中心的 **中介者（Mediator）** 模式构建，通过统一接口提供对所有 DDD 组件的访问。主要架构组件包括：

- **Mediator/X**：所有框架组件的中央访问点（Mediator.kt:18，X 接口在第 182 行提供快捷方法）
- **聚合（Aggregates）**：支持工厂模式的领域聚合
- **仓储（Repositories）**：数据持久化抽象层
- **领域服务（Domain Services）**：核心业务逻辑服务
- **工作单元（Unit of Work）**：事务管理模式
- **请求/命令/查询处理器**：CQRS 模式实现
- **领域事件（Domain Events）**：面向领域关注点的事件驱动架构
- **集成事件（Integration Events）**：跨界限上下文事件通信

### 模块结构

- `ddd-core/` - 核心 DDD 框架接口和实现
- `ddd-distributed-*` - 分布式系统组件（目前被注释掉）
- `ddd-domain-*` - 领域特定实现（目前被注释掉）

### 包组织结构

核心包遵循 DDD 分层结构在 `com.only4.cap4k.ddd.core`：

- `application/` - 应用服务层（命令、查询、事件、工作单元）
- `domain/` - 领域层（聚合、仓储、领域服务、事件）
- `share/` - 共享工具和常量

### 框架使用模式

框架提供两种主要的访问模式：

1. **Mediator 接口**：完整的描述性方法名（`Mediator.repositories()`、`Mediator.commands()`）
2. **X 接口**：简洁代码的短别名（`X.repo()`、`X.cmd()`、`X.qry()`）

两个接口都提供对相同底层监督者和管理组件的访问。

## 开发说明

- 使用 Kotlin 2.1.20 和 Spring Boot 3.1.12
- JVM 工具链设置为 Java 17
- 测试框架：JUnit 5 和用于模拟的 MockK
- 启用构建缓存和配置缓存以提高性能
- `buildSrc/` 中的约定插件用于共享构建逻辑