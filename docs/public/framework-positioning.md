# Framework Positioning

[中文](framework-positioning.zh-CN.md)

## Foreground Concepts

- Aggregate Root
- Entity
- Domain Event
- Command
- Query

## Default Happy Path

- single-command single-aggregate-root mutation
- aggregate root as the write-facing surface
- mutation converges into command handling
- domain events are registered and released by aggregate roots
- `cli` is an anti-corruption boundary, not the truth source of the process

## Background Concepts

- Value Object
- Integration Event
- Repository contract
- handler family
- cli

## Advanced But Valid Concepts

- Domain Service
- Saga
- Strong ID

## Runtime And Infra Surfaces

- JPA runtime and repository landing path
- integration-event transport and persistence adapters
- starter and auto-configuration
- other provider-specific runtime support

## Removed Or Deprecated Core Positioning

- Wrapper is not part of the public core positioning anymore

## Advanced Modeling Note

- advanced read/write model split with read-only weak-reference template context is non-default
- repository remains write-model only
