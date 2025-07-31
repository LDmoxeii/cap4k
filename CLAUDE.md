# CLAUDE.md

This file provides guidance to Claude Code (claude.ai/code) when working with code in this repository.

## Build Commands

This project uses Gradle with Kotlin DSL:

- `./gradlew build` - Build the project (only builds active modules)
- `./gradlew check` - Run all checks including tests
- `./gradlew clean` - Clean build outputs
- `./gradlew test` - Run tests only
- `./gradlew test --tests "*ClassName*"` - Run specific test class

## Architecture Overview

Cap4k is a Domain-Driven Design (DDD) framework for Kotlin/JVM applications with Spring Boot integration. The project
follows a multi-module structure with DDD architectural patterns.

### Module Structure

#### Active Modules

- **ddd-core** - Core DDD framework interfaces and implementations (pure interfaces, no dependencies)
- **ddd-domain-event-jpa** - JPA-based event sourcing and event store implementation
- **ddd-domain-repo-jpa** - JPA-based repository implementation with Unit of Work
- **ddd-domain-repo-jpa-querydsl** - QueryDSL integration for type-safe query building

#### Available but Inactive Modules (commented in settings)

- **ddd-distributed-locker-jdbc** - JDBC-based distributed locking
- **ddd-distributed-snowflake** - Snowflake algorithm for distributed ID generation

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

### Technology Stack

- Kotlin 2.1.20 with Spring Boot 3.1.12
- Java 17 toolchain
- JUnit 5 with MockK for testing (Kotlin test assertions preferred)
- QueryDSL for type-safe query building
- Build caching and configuration caching enabled
- Convention plugins in `buildSrc/` for shared build logic

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

# important-instruction-reminders

Do what has been asked; nothing more, nothing less.
NEVER create files unless they're absolutely necessary for achieving your goal.
ALWAYS prefer editing an existing file to creating a new one.
NEVER proactively create documentation files (*.md) or README files. Only create documentation files if explicitly
requested by the User.
