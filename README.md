# cap4k

[中文文档](README.zh-CN.md)

cap4k is a simplified DDD tactical framework designed for AI-assisted implementation and human review.

## Mainline

`Aggregate Root -> Entity -> Command / Query -> Domain Event -> Orchestration Surfaces`

## How to Start

1. Read the default happy path in this README.
2. Continue with [Getting Started](docs/public/getting-started.md).
3. Read [Framework Positioning](docs/public/framework-positioning.md) before treating advanced concepts or runtime surfaces as default promises.

## What cap4k Is

- an aggregate-centered DDD tactical framework
- command/query driven by default
- domain-event aware
- designed for AI-assisted implementation and human review
- keeps design, runtime, and generation layers visible without collapsing the framework into code generation

## What cap4k Is Not

- not a generic code generator platform
- not a JPA-centric framework first and foremost
- not an external event-integration platform first and foremost
- not a frontend code generation framework
- not a framework that places every DDD pattern on the front page equally

## Default Happy Path

- single-command single-aggregate-root mutation
- aggregate root as the write-facing surface
- domain mutation converges into command handling
- domain events are registered and released by aggregate roots
- multiple handlers do not have guaranteed execution order
- `cli` is an anti-corruption boundary rather than the truth source of the process

## Documentation Map

- [Getting Started](docs/public/getting-started.md)
- [Framework Positioning](docs/public/framework-positioning.md)
- [Repository specs](docs/superpowers/specs/) and [implementation plans](docs/superpowers/plans/) for repository-internal design work
