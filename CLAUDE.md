# CLAUDE.md

This file provides guidance to Claude Code (claude.ai/code) when working with code in this repository.

## Build Commands

This project uses Gradle with Kotlin DSL:

- `./gradlew build` - Build the project (only builds active modules)
- `./gradlew check` - Run all checks including tests
- `./gradlew clean` - Clean build outputs
- `./gradlew test` - Run tests only
- `./gradlew test --tests "*ClassName*"` - Run specific test class
- `./gradlew test --tests "*ClassName.methodName*"` - Run specific test method
- `./gradlew :module-name:test` - Run tests for specific module (e.g., `:ddd-domain-repo-jpa-querydsl:test`)

## Architecture Overview

Cap4k is a Domain-Driven Design (DDD) framework for Kotlin/JVM applications with Spring Boot integration. The project
follows a multi-module structure with DDD architectural patterns.

### Module Structure

#### Active Modules

- **ddd-core** - Core DDD framework interfaces and implementations (pure interfaces, no dependencies)
- **ddd-application-request-jpa** - JPA-based request/command execution with retry and scheduling
- **ddd-domain-event-jpa** - JPA-based event sourcing and event store implementation
- **ddd-domain-repo-jpa** - JPA-based repository implementation with Unit of Work
- **ddd-domain-repo-jpa-querydsl** - QueryDSL integration for type-safe query building
- **ddd-integration-event-rabbitmq** - RabbitMQ integration for cross-boundary events
- **ddd-integration-event-rocketmq** - RocketMQ integration for cross-boundary events
- **ddd-integration-event-http** - HTTP-based integration event publishing and subscription
- **ddd-integration-event-http-jpa** - JPA persistence for HTTP integration event subscriptions
- **ddd-distributed-saga-jpa** - JPA-based distributed saga orchestration with compensation and archiving
- **ddd-distributed-locker-jdbc** - JDBC-based distributed locking
- **ddd-distributed-snowflake** - Snowflake algorithm for distributed ID generation
- **cap4k-ddd-console** - Management console with HTTP endpoints for monitoring events, requests, sagas, locks, and
  snowflake IDs
- **cap4k-ddd-starter** - Spring Boot starter for automatic configuration

### Core Architecture

The framework is built around a central **Mediator** pattern that provides access to all DDD components through a
unified interface.

#### Key Components

- **Mediator/X** - Central access point with dual interfaces:
  - Verbose: `Mediator.repositories()`, `Mediator.commands()`
  - Concise: `X.repo()`, `X.cmd()`, `X.qry()`
- **Aggregates** - Domain aggregates with factory pattern support
- **Repositories** - Data persistence abstraction layer with JPA and QueryDSL implementations
- **Unit of Work** - Transaction management pattern
- **CQRS** - Request/Command/Query handling
- **Events** - Domain events and integration events system
- **Sagas** - Long-running business processes with compensation logic

#### Mediator Pattern Implementation

The `Mediator` interface unifies access to all framework components. It implements multiple supervisor interfaces and
provides both verbose and concise access patterns:

```kotlin
// Verbose access
Mediator.repositories().findById(id)
Mediator.commands().execute(command)

// Concise access via X class
X.repo().findById(id)
X.cmd().execute(command)
```

Core supervisors accessible through Mediator:

- `AggregateFactorySupervisor` - Factory pattern for aggregates
- `RepositorySupervisor` - Repository access and management
- `AggregateSupervisor` - Aggregate lifecycle management
- `DomainServiceSupervisor` - Domain service execution
- `RequestSupervisor` - CQRS request handling (commands, queries, sagas)
- `IntegrationEventSupervisor` - Cross-boundary event publishing
- `UnitOfWork` - Transaction management

#### DDD Annotation System

The framework uses annotations to classify and organize domain components:

**@Aggregate** - Marks classes as part of domain aggregates:

- `aggregate` - The aggregate name (e.g., "user", "order")
- `type` - Component type: "entity", "value-object", "repository", "factory", "factory-payload", "domain-event", "
  specification", "enum"
- `name` - Component display name
- `root` - Whether this entity is the aggregate root
- `description` - Component description

**@DomainService** - Marks domain service classes
**@DomainEvent** - Marks domain event classes
**@IntegrationEvent** - Marks cross-boundary events

#### Architecture Information System

The `ArchInfoManager` provides runtime introspection of the DDD architecture:

- Scans packages for annotated classes
- Categorizes components by type and aggregate
- Builds hierarchical architecture metadata
- Uses lazy loading with thread-safe initialization via `ResolvedClasses` data wrapper
- Provides JSON-serializable architecture information for documentation and tooling

#### Package Organization

`com.only4.cap4k.ddd.core` contains:
- `application/` - Application service layer (commands, queries, events, UoW)
  - `command/` - Command handling
  - `query/` - Query handling with list/page support
  - `saga/` - Saga orchestration
  - `event/` - Integration event management
  - `distributed/` - Distributed locking
- `domain/` - Domain layer (aggregates, repositories, domain services, events)
  - `aggregate/` - Aggregate root, entities, value objects, specifications
  - `repo/` - Repository pattern with persist listeners
  - `service/` - Domain services
  - `event/` - Domain event publishing and subscription
- `archinfo/` - Architecture introspection and metadata
- `share/` - Shared utilities and constants

### Repository Implementations

The framework provides multiple repository implementations for different query needs:

#### JPA Repository (`ddd-domain-repo-jpa`)

- `AbstractJpaRepository<ENTITY>` - Basic JPA repository with criteria queries
- `JpaPredicate<ENTITY>` - Type-safe predicate building using JPA Criteria API
- Supports standard CRUD operations, pagination, and custom criteria queries

#### QueryDSL Repository (`ddd-domain-repo-jpa-querydsl`)

- `AbstractQuerydslRepository<ENTITY>` - QueryDSL-based repository for type-safe queries
- `QuerydslPredicate<ENTITY>` - Fluent predicate builder using QueryDSL's BooleanBuilder
- `QuerydslPredicateSupport` - Utility object for converting between framework and QueryDSL types
- Provides compile-time query validation and better IDE support for complex queries

**QueryDSL Integration Features:**

- Type-safe query construction with `QuerydslPredicate.of(EntityClass.class).where(condition).orderBy(spec)`
- Automatic conversion between framework predicates and QueryDSL predicates
- Support for complex sorting with `OrderSpecifier` integration
- Seamless integration with Spring Data's `QuerydslPredicateExecutor`

**Usage Pattern:**

```kotlin
// Create a QueryDSL predicate
val predicate = QuerydslPredicate.of(User::class.java)
    .where(QUser.user.name.eq("John"))
    .orderBy(QUser.user.createdAt.desc())

// Use with repository
val users = repository.find(predicate, persist = false)
```

### Saga Implementation (`ddd-distributed-saga-jpa`)

The distributed saga module provides orchestration for long-running business processes:

#### Key Components

- `SagaRecord` - Interface for saga state management and process tracking
- `SagaRecordImpl` - Implementation handling saga lifecycle, compensation, and process results
- `JpaSagaRecordRepository` - JPA-based persistence with archiving capabilities
- `JpaSagaScheduleService` - Scheduling service for compensation retry and archiving
- `SagaManager` - High-level saga management and orchestration

#### Saga Features

- **Compensation Logic** - Automatic retry with configurable intervals and maximum attempts
- **Process Tracking** - Track individual saga steps with results and exception handling
- **Archiving System** - Move completed/expired sagas to archive tables for performance
- **Distributed Locking** - Prevent concurrent saga processing conflicts
- **Partitioning Support** - Automatic MySQL table partitioning for large datasets

### Console Management (`cap4k-ddd-console`)

The console module provides HTTP endpoints for monitoring and managing DDD components:

#### Console Services

- `EventConsoleService` - Search and retry domain/integration events
- `RequestConsoleService` - Search and retry failed requests
- `SagaConsoleService` - Monitor saga execution and retry failed sagas
- `LockerConsoleService` - View distributed locks and force unlock
- `SnowflakeConsoleService` - Monitor snowflake ID worker assignments

Each console service provides REST endpoints for:

- Searching records with filters (by time, status, type, UUID)
- Retrying failed operations
- Viewing operational statistics

### HTTP Integration Events (`ddd-integration-event-http`)

HTTP-based integration event system for cross-service communication:

#### Key Components

- `HttpIntegrationEventPublisher` - Publishes events via HTTP POST to registered subscribers
- `HttpIntegrationEventSubscriberRegister` - Manages event subscriptions and unsubscriptions
- `HttpIntegrationEventSubscriberAdapter` - Adapts incoming HTTP event notifications
- `IntegrationEventHttpSubscribeCommand` - Command to register for event notifications
- `IntegrationEventHttpUnsubscribeCommand` - Command to remove event subscriptions

#### Features

- Dynamic subscription management with HTTP endpoints
- Automatic retry logic for failed HTTP deliveries
- Persistent subscriber registry using JPA (when combined with `ddd-integration-event-http-jpa`)
- Support for event filtering and routing

### Technology Stack

- Kotlin 2.1.20 with Spring Boot 3.1.12
- Java 17 toolchain
- JUnit 5 with MockK for testing (Kotlin test assertions preferred)
- QueryDSL for type-safe query building
- Build caching and configuration caching enabled
- Convention plugins in `buildSrc/` for shared build logic
- Spring Boot BOM for dependency version management
- **Kotlin JPA Plugin** - Automatically generates no-arg constructors and makes classes/properties open for JPA
  compatibility

### Build System

The project uses a sophisticated Gradle setup:

- **Convention Plugin**: `buildSrc/src/main/kotlin/kotlin-jvm.gradle.kts` provides shared build logic
- **Kotlin Spring Plugin**: Automatically adds `open` modifier to Spring-annotated classes for proxy compatibility
- **Kotlin JPA Plugin**: Automatically generates no-arg constructors and makes JPA entities `open` for Hibernate proxy
  compatibility
- **Version Catalog**: `gradle/libs.versions.toml` centralizes dependency versions
- **Platform Dependencies**: Uses Spring Boot BOM for consistent dependency versions
- **Test Configuration**: Enhanced test setup with 2GB heap, 10-minute timeout, and comprehensive logging

#### Build Dependencies Pattern

All modules follow consistent dependency patterns:

```kotlin
dependencies {
  // Platform for version management (core modules only)
  api(platform(libs.springBootDependencies))

  // Project dependencies
  implementation(project(":ddd-core"))

  // Implementation dependencies
  implementation(libs.fastjson)
  implementation("org.jetbrains.kotlin:kotlin-reflect")

  // Compile-only dependencies
  compileOnly(libs.springContext)

  // Test platform (for modules with Spring test dependencies)
  testImplementation(platform(libs.springBootDependencies))

  // Test framework (consistent across all modules)
  testImplementation(libs.mockk)
  testImplementation("org.junit.jupiter:junit-jupiter:5.10.2")
  testImplementation("org.jetbrains.kotlin:kotlin-test")
  testRuntimeOnly("org.junit.platform:junit-platform-launcher")
}
```

## Testing

Testing uses JUnit 5 with Kotlin test assertions and MockK for mocking:

- `./gradlew test` - Run all tests
- `./gradlew test --tests "*ClassName*"` - Run specific test class
- `./gradlew test --tests "*ClassName.methodName*"` - Run specific test method
- `./gradlew :module-name:test` - Run tests for specific module (e.g., `:ddd-domain-repo-jpa-querydsl:test`)

Test files are located in `src/test/kotlin` with the same package structure as main code.

### Testing Conventions

- Use Kotlin test assertions (`kotlin.test.*`) rather than JUnit assertions for better Kotlin integration
- Chinese `@DisplayName` annotations are preferred for better readability in test reports
- Test classes should be named with `Test` suffix (e.g., `QuerydslPredicateTest`)
- Use `@DisplayName` for both class and method level descriptions

### MockK Testing Patterns

When creating mock objects for complex entities (especially in saga tests):

```kotlin
// For entities with many properties, use relaxed mocking
val mockEntity = mockk<EntityClass>(relaxed = true) {
  every { id } returns "test-id"
  every { importantProperty } returns expectedValue
  // Only configure properties that are actually used in the test
}
```

For saga-related tests, ensure all accessed properties are mocked:

- `sagaProcesses` property should return `mutableListOf()` for proper archiving tests
- Use `answers` callback for simulating state changes in exception handling tests

## Development Notes

- Version catalog in `gradle/libs.versions.toml` manages all dependency versions
- Build caching and configuration caching are enabled for performance
- Convention plugins in `buildSrc/` provide shared build logic
- Chinese documentation available in `CLAUDE_CN.md` (sync when updating `CLAUDE.md`)

### Kotlin Development Guidelines

- Use `ENTITY: Any` type bounds for better type safety
- Prefer `apply` scoping functions for fluent interfaces and method chaining
- Use `companion object` for factory methods instead of static methods
- Leverage Kotlin's null safety with `?` and `!!` operators appropriately
- Use type aliases to resolve naming conflicts (e.g., `import com.querydsl.core.types.Predicate as QuerydslPredicate`)

### Working with Repository Implementations

- When adding new repository implementations, extend from core `Repository<ENTITY>` interface
- Implement `supportPredicateClass()` to return the specific predicate type
- Register predicate and repository reflectors in `@PostConstruct` init methods for framework integration
- Use `QuerydslPredicateSupport` utilities for converting between framework and QueryDSL types

### Saga Development Guidelines

- Saga processes should be idempotent to handle retry scenarios
- Use proper error handling and compensation logic for failed saga steps
- Consider partitioning strategies for high-volume saga tables
- Implement proper archiving to maintain performance as saga volume grows

### Service Constructor Patterns

Key framework services follow specific constructor patterns that must be maintained in tests:

#### DefaultRequestSupervisor Constructor

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

#### DefaultSagaSupervisor Constructor

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

#### DefaultEventPublisher Constructor

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

#### DefaultEventInterceptorManager Constructor

```kotlin
DefaultEventInterceptorManager(
    eventMessageInterceptors: List<EventMessageInterceptor>,
    eventInterceptors: List<EventInterceptor>,
    eventRecordRepository: EventRecordRepository
)
```

#### JpaRequestScheduleService Constructor

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

Note: The `svcName` parameter was removed from `JpaRequestScheduleService` in recent updates.

When updating these services, ensure all test constructors are updated accordingly.

# important-instruction-reminders

Do what has been asked; nothing more, nothing less.
NEVER create files unless they're absolutely necessary for achieving your goal.
ALWAYS prefer editing an existing file to creating a new one.
NEVER proactively create documentation files (*.md) or README files. Only create documentation files if explicitly
requested by the User.
