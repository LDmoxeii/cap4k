# CLAUDE.md

This file provides guidance to Claude Code (claude.ai/code) when working with code in this repository.

## Build Commands

This project uses Gradle with Kotlin DSL:

- `./gradlew build` - Build the project
- `./gradlew check` - Run all checks including tests
- `./gradlew clean` - Clean build outputs
- `./gradlew test` - Run tests only
- `./gradlew test --tests "*ClassName*"` - Run specific test class

## Architecture Overview

Cap4k is a Domain-Driven Design (DDD) framework for Kotlin/JVM applications with Spring Boot integration. The project
follows a multi-module structure with DDD architectural patterns.

### Core Components

The framework is built around a central **Mediator** pattern that provides access to all DDD components through a
unified interface. Key architectural components include:

- **Mediator/X**: Central access point for all framework components (Mediator.kt:18, X interface at line 182 provides
  shortcut methods)
- **Aggregates**: Domain aggregates with factory pattern support
- **Repositories**: Data persistence abstraction layer
- **Domain Services**: Core business logic services
- **Unit of Work**: Transaction management pattern
- **Request/Command/Query Handlers**: CQRS pattern implementation
- **Domain Events**: Event-driven architecture for domain concerns
- **Integration Events**: Cross-bounded context event communication

### Module Structure

- `ddd-core/` - Core DDD framework interfaces and implementations
- `ddd-distributed-*` - Distributed system components (currently commented out)
- `ddd-domain-*` - Domain-specific implementations (currently commented out)

### Package Organization

Core packages follow DDD layering in `com.only4.cap4k.ddd.core`:

- `application/` - Application service layer (commands, queries, events, UoW)
- `domain/` - Domain layer (aggregates, repositories, domain services, events)
- `share/` - Shared utilities and constants

### Framework Usage Patterns

The framework provides two main access patterns:

1. **Mediator interface**: Full descriptive method names (`Mediator.repositories()`, `Mediator.commands()`)
2. **X interface**: Short aliases for concise code (`X.repo()`, `X.cmd()`, `X.qry()`)

Both interfaces provide access to the same underlying supervisors and management components.

## Development Notes

- Uses Kotlin 2.1.20 with Spring Boot 3.1.12
- JVM toolchain set to Java 17
- Test framework: JUnit 5 with MockK for mocking
- Build caching and configuration caching enabled for performance
- Convention plugins in `buildSrc/` for shared build logic
