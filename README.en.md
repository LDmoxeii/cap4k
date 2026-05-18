# cap4k

[![CI](https://github.com/LDmoxeii/cap4k/actions/workflows/ci.yml/badge.svg)](https://github.com/LDmoxeii/cap4k/actions/workflows/ci.yml)
[![Maven Central](https://img.shields.io/maven-central/v/io.github.ldmoxeii/ddd-core)](https://central.sonatype.com/artifact/io.github.ldmoxeii/ddd-core)
[![GitHub Release](https://img.shields.io/github/v/release/LDmoxeii/cap4k)](https://github.com/LDmoxeii/cap4k/releases)
[![GitHub license](https://img.shields.io/badge/license-MIT-blue.svg)](https://github.com/LDmoxeii/cap4k/blob/master/LICENSE)
[![Ask DeepWiki](https://deepwiki.com/badge.svg)](https://deepwiki.com/LDmoxeii/cap4k)

[中文文档](README.md)

cap4k is a simplified DDD tactical framework designed for AI-assisted implementation and human review.

## Mainline

`Aggregate Root -> Entity -> Command / Query -> Domain Event -> Orchestration Surfaces`

## How to Start

1. Read the default happy path in this README.
2. Continue with [Getting Started (Chinese)](docs/public/authoring/getting-started.md).
3. Read [Framework Positioning (Chinese)](docs/public/authoring/framework-positioning.md) before treating advanced concepts or runtime surfaces as default promises.

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

- [Getting Started (Chinese)](docs/public/authoring/getting-started.md)
- [Framework Positioning (Chinese)](docs/public/authoring/framework-positioning.md)
- [Authoring Guide Overview (Chinese)](docs/public/authoring/index.md)
