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

#### Available but Inactive Modules (commented in settings)

- **ddd-distributed-locker-jdbc** - JDBC-based distributed locking
- **ddd-distributed-snowflake** - Snowflake algorithm for distributed ID generation
- **ddd-domain-event-jpa** - JPA-based event sourcing and event store
- **ddd-domain-repo-jpa** - JPA-based repository implementation with Unit of Work

### Core Architecture

The framework is built around a central **Mediator** pattern that provides access to all DDD components through a
unified interface.

#### Key Components

- **Mediator/X** - Central access point with dual interfaces:
  - Verbose: `Mediator.repositories()`, `Mediator.commands()`
  - Concise: `X.repo()`, `X.cmd()`, `X.qry()`
- **Aggregates** - Domain aggregates with factory pattern support
- **Repositories** - Data persistence abstraction layer
- **Unit of Work** - Transaction management pattern
- **CQRS** - Request/Command/Query handling
- **Events** - Domain events and integration events system

#### Package Organization

`com.only4.cap4k.ddd.core` contains:
- `application/` - Application service layer (commands, queries, events, UoW)
- `domain/` - Domain layer (aggregates, repositories, domain services, events)
- `share/` - Shared utilities and constants

### Technology Stack

- Kotlin 2.1.20 with Spring Boot 3.1.12
- Java 17 toolchain
- JUnit 5 with MockK for testing
- Build caching and configuration caching enabled
- Convention plugins in `buildSrc/` for shared build logic
