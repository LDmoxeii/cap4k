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

These concepts matter, but they sit behind the public first-screen story and usually become relevant after the default path is clear.

- Value Object
- Integration Event
- Repository contract for aggregate loading and persistence boundaries
- handler families for standard command/query execution
- `cli` as the anti-corruption boundary for external capabilities

## Advanced But Valid Concepts

- Domain Service
- Saga
- Strong ID

## Runtime And Infra Surfaces

These are real landing surfaces in the codebase, but they support the framework rather than define its public identity.

- JPA-based repository and unit-of-work landing path
- integration-event transport and persistence adapters
- starter and auto-configuration modules for runtime wiring
- other provider-specific runtime support where the host stack needs it

## Removed Or Deprecated Core Positioning

- Wrapper is not part of the public core positioning anymore and should be treated as deprecated outside legacy cleanup work

## Advanced Modeling Note

- advanced read/write model split with optional read-only reference context is non-default
- repository remains write-model only

## From Positioning to Authoring

- [Authoring Guide Overview](authoring/index.md)
- [Default Happy Path](authoring/default-happy-path.md)
