# Cap4k Framework Capability Audit and Positioning Design

Date: 2026-05-04

Status: Proposed

Scope: audit current framework capabilities against current code reality, classify them into stable language concepts, default happy-path rules, advanced tactical concepts, runtime/infra surfaces, and removal targets, then define the public positioning boundary that later README and guide work must follow.

Out of scope: implementation planning, README rewrite, AI-collaboration guide writing, wrapper deletion implementation, runtime module cleanup, Domain Service/Value Object/Saga teaching examples, frontend TypeScript generation, and roadmap/backlog migration work.

## Background

`cap4k` is not only a generator plugin set. It is a DDD tactical framework that currently spans:

- tactical language concepts
- application use-case contracts
- runtime repository and unit-of-work behavior
- domain event and integration event pipelines
- starter-driven infrastructure wiring
- pipeline/analysis/generation projections

The current problem is not lack of capability. The problem is that several layers are mixed together:

- stable DDD concepts
- cap4k default happy-path rules
- advanced but valid concepts
- runtime and infrastructure carrier layers
- historical concepts that should no longer anchor framework identity

Without a capability audit, README and public positioning drift toward one of two bad states:

1. overselling every implemented module as first-class identity
2. over-pruning valid concepts only because they are not default-path concepts

This spec fixes that by defining one audit framework, one public-positioning framework, and one current decision baseline.

## Problem

Current framework discussion can easily collapse into the wrong questions:

- "does this concept exist in code?"
- "is this concept on the first screen of README?"
- "is this concept part of the default happy path?"
- "is this concept a heavy runtime ecosystem?"
- "should this concept be removed?"

Those are different questions.

For example:

- `Value Object` is a stable DDD language concept, but not yet a front-positioned cap4k default-path selling point.
- `Saga` is a valid tactical concept and also has heavy runtime reality, but that does not make it part of the default path.
- `Wrapper` is heavily implemented today, but that does not mean it should remain in the framework's core narrative.

The audit must therefore separate:

1. language validity
2. default-path importance
3. runtime reality
4. public positioning weight
5. removal decisions

## Goals

1. Define the audit buckets that all current capabilities must be evaluated against.
2. Set the stable core language and application concepts that `cap4k` explicitly keeps.
3. Define the default happy-path rules that make `cap4k` a simplified, reviewable DDD tactical framework.
4. Preserve valid advanced concepts without forcing them into the default path.
5. Separate runtime/infra carrier layers from first-screen framework identity.
6. Identify the concepts that should leave the core narrative and later be removed in code.
7. Provide a stable positioning baseline for:
   - `#16` README rewrite
   - `#17` DDD + cap4k + AI collaboration guide
   - later follow-up specs only where the audited concept survives

## Non-Goals

This audit does not:

- treat "not default" as "not valid"
- remove stable DDD concepts just because current dogfood usage is low
- force all implemented runtime modules into first-screen public positioning
- decide detailed implementation steps for wrapper deletion or Strong ID redesign
- write tutorial content for Domain Service, Value Object, or Saga usage
- redesign current repository backend, event backend, or starter architecture

## Audit Framework

Every capability must be classified using the following buckets.

### 1. Stable core language concepts

Concepts that `cap4k` must explicitly recognize as part of its tactical language.

### 2. Default happy-path rules

Rules that define the intended mainstream way to use the framework in a simplified, reviewable manner.

### 3. Advanced but valid tactical concepts

Concepts that remain valid and supported, but are not part of the default path and should not dominate first-screen positioning.

### 4. Runtime / infra capability surfaces

Carrier layers, transport adapters, persistence implementations, starter wiring, and infrastructure modules that are real parts of the implementation landscape but are not themselves the framework's primary identity.

### 5. Remove / deprecate

Concepts that should leave the core narrative and later be removed from implementation surfaces.

## Framework Worldview Assumptions

This audit adopts the current simplified cap4k worldview:

- one project is the default bounded-context unit
- one project can contain multiple aggregate roots
- one aggregate root can own multiple internal entities
- aggregate mutation is aggregate-root-centered
- command-side mutation and query-side reading have different default responsibilities

These assumptions do not replace all DDD theory. They define the working frame for `cap4k` as a simplified tactical framework.

## Stable Core Language Concepts

The following remain stable first-class language concepts:

- Aggregate Root
- Entity
- Value Object
- Domain Event
- Integration Event

### Positioning Weight

#### Foreground

These belong in first-screen public positioning because the default cap4k path directly revolves around them:

- Aggregate Root
- Entity
- Domain Event

#### Background

These remain stable language concepts, but should be explained in deeper docs rather than treated as first-screen identity:

- Value Object
- Integration Event

### Rationale

- `Aggregate Root` remains the central write-facing tactical object.
- `Entity` remains a real internal modeling unit rather than a hidden implementation detail.
- `Domain Event` is a core part of the default mutation-to-follow-up flow.
- `Value Object` must remain in the language, but current cap4k usage and ergonomics do not justify foreground positioning yet.
- `Integration Event` is also a stable language concept, but foregrounding it would misposition cap4k as an integration platform before explaining its aggregate-centered tactical core.

## Stable Core Application / Use-case Concepts

The following remain stable application/use-case concepts:

- Command
- Query
- Command Handler
- Query Handler
- Repository contract

### Positioning Weight

#### Foreground

- Command
- Query

#### Background

- Command Handler
- Query Handler
- Repository contract

### Rationale

- `Command` and `Query` are the public use-case model that cap4k wants users to think in.
- `CommandHandler` and `QueryHandler` are standard execution surfaces, but they should not displace `Command` and `Query` as the dominant public framing.
- `Repository contract` is important and real, but foregrounding it too early would shift cap4k toward generic persistence-framework positioning.

## Stable Core Integration-boundary Concepts

The following remain stable boundary concepts:

- cli
- Integration Event

`cli` is treated as an anti-corruption and external-capability boundary. It is not a process-orchestration role and not a domain truth source.

`Integration Event` is treated as the cross-bounded-context interaction concept. It remains distinct from `Domain Event`.

## Core Orchestration Surfaces

The following are process entry or progression surfaces:

- controller
- job
- domain event handler
- integration event handler

These are not the same layer as:

- `CommandHandler` / `QueryHandler`
- `cli`
- `Integration Event` as a language concept

They exist to collect input, advance process steps, and delegate into commands or queries under the framework's happy-path rules.

## Default Happy-path Rules

The following rules define the intended default path for `cap4k`.

### 1. Single-command single-aggregate-root mutation

One command handling path may query multiple aggregate roots as decision input, but only one aggregate root may enter the persisted mutation boundary.

### 2. Real domain state changes converge into command handling

Actual domain mutation must converge into the command handling path.

That means:

- controller does not directly mutate aggregate state
- job does not directly mutate aggregate state
- domain event handler does not directly mutate aggregate state
- integration event handler does not directly mutate aggregate state

These surfaces advance into commands instead of mutating aggregates themselves.

### 3. Aggregate root is the only write-facing surface

- aggregate-owned mutation happens through the aggregate root
- child entities do not become direct external command targets
- child-entity identity may exist internally, but must not become external primary-key-style mutation surface

### 4. Domain events are registered and released by aggregate root

Event content may describe child-entity facts, but event registration and release belong to the aggregate root boundary.

### 5. Strong cross-aggregate write-model references are disallowed by default

Cross-aggregate strong write-model references are not part of the default path.

Read-only weak references may exist only under explicitly bounded advanced modeling rules.

### 6. cli is anti-corruption boundary, not primary process truth source

`cli` is for external capability access, protocol isolation, and model translation.

It is not the default source of process truth and should not become the place where core business process logic is centered.

### 7. Multiple handlers have no guaranteed execution order

When multiple handlers react to the same event, execution order is not promised.

If order matters, the process must be decomposed into finer-grained events, commands, or stages.

### 8. Single primary action per orchestration surface

- write-facing orchestration surfaces emit at most one command as the primary action
- query-facing surfaces emit at most one query as the primary action
- mixing multiple command/query/cli actions inside one orchestration surface is not the default path

This is a default simplification rule rather than a claim that all valid process designs everywhere must look this way.

## Advanced but Valid Tactical Concepts

The following concepts remain valid and should not be removed:

- Domain Service
- Saga

### Domain Service

`Domain Service` remains a legitimate DDD tactical concept for business behavior that belongs to the domain but does not naturally belong to an aggregate or value object.

`cap4k` should keep the concept, but not treat it as a first-screen default path.

### Saga

`Saga` remains a legitimate advanced tactical concept for longer-running or cross-boundary process coordination.

`cap4k` should keep the concept, but it remains outside the framework's default simplified path.

### Positioning Rule

These concepts are:

- valid
- supported
- worth documenting

but they should not dominate the public front-facing explanation of what cap4k is.

## Advanced Engineering Reinforcement Concepts

The current advanced engineering reinforcement concept is:

- Strong ID

### Positioning

`Strong ID` is valuable for type-safety and explicit identity semantics, but it is not a core DDD building block in the same sense as aggregate, entity, value object, command, or domain event.

It should remain an advanced engineering enhancement rather than a default-path requirement.

### Current Direction

If later work continues this concept, it should be decoupled from wrapper-era assumptions and re-evaluated as an explicit ID-level extension.

## Runtime / Infra Capability Surfaces

The following are real implementation surfaces that the audit must acknowledge:

- starter / auto-configuration
- JPA repository and unit-of-work runtime
- integration-event transport and persistence adapters
- distributed locker support
- distributed snowflake support
- saga runtime, persistence, and console
- provider-specific and persistence-specific support layers

### Positioning Rule

These surfaces are real and important, but they do not define first-screen framework identity.

They should be described as:

- current default landing paths
- runtime carriers
- infrastructure support ecosystems

not as the primary answer to "what is cap4k?"

### JPA Runtime

JPA currently acts as the default repository and unit-of-work landing path.

That reality must be acknowledged, but `cap4k` must not be positioned primarily as a JPA wrapper framework.

### Starter and Auto-configuration

Starter wiring is a major part of current runtime reality, but it remains a carrier layer rather than the framework's first identity.

### Integration-event Runtime Ecosystem

Transport and persistence modules around integration events are substantial and should be treated as their own runtime ecosystem.

That does not change the rule that `Integration Event` as a concept and the transport/persistence ecosystem around it are different layers.

### Distributed Locker and Snowflake

These remain real infra subsystems with contracts, implementations, starter wiring, and console tooling.

They belong in the runtime/infra bucket rather than in first-screen public identity.

### Saga Runtime

Saga runtime, persistence, and console reality are already heavy.

That strengthens the distinction between:

- `Saga` as a valid advanced concept
- saga runtime ecosystem as a runtime/infra surface

## Remove / Deprecate

The current explicit remove/deprecate target is:

- Wrapper

### Decision

`Wrapper` should leave the core narrative and later be removed from implementation surfaces.

This is not because it is lightly used. It is because it is not the right stable tactical concept for the framework to keep centering.

### Current Reality

`Wrapper` is still heavily implemented today across:

- planner
- renderer
- DSL switches
- old codegen surfaces
- tests
- documentation

That means removal is a real follow-up implementation task, not just wording cleanup.

## Advanced Modeling Mode: Read-only Weak References

Read-only weak references are allowed only as an advanced modeling mode.

### Default Rule

Default cap4k keeps read/write separation:

- repository only senses the write model
- repository whole-aggregate loading only applies to the write model
- query loading remains separate from repository mutation semantics

### Allowed Advanced Mode

The framework may expose enough weak-reference metadata in the template context to support:

- unified type expression
- unified navigation surface

for advanced templates.

This advanced mode does not change:

- repository semantics
- query loading responsibility
- write-model consistency boundary

### First-stage Recommended Output Shape

The first-stage recommendation is:

- separate classes for write-model and read-side/navigation-side outputs
- shared weak-reference understanding in template context

Single-class unified output may be considered later, but it is not a first-stage commitment.

## Public Positioning Guidance

### First-screen Foreground

Public first-screen positioning should focus on:

- Aggregate Root
- Entity
- Domain Event
- Command
- Query
- the default happy-path rules

### Background / Deeper Documentation

Public deeper documentation should explicitly cover:

- Value Object
- Integration Event
- Repository contract
- Command Handler / Query Handler
- cli
- Domain Service
- Saga
- Strong ID
- runtime / infra capability surfaces

### Positioning Principle

`cap4k` does not aim for the fewest concepts.

It aims for:

- the fewest false or misleading concepts
- the clearest default path
- a stable tactical language

## Follow-up Implications

### README Rewrite

`#16` must follow this spec's public-positioning guidance and avoid:

- front-loading runtime carriers
- pretending every implemented module is core identity
- hiding advanced but valid concepts as if they do not exist

### DDD + cap4k + AI Collaboration Guide

`#17` should build on this spec's happy-path rules and concept layering rather than redefining framework identity from scratch.

### Concept-specific Follow-up Specs

Follow-up specs should be created only for concepts or capability surfaces that survive this audit as:

- stable core language/application concepts
- advanced but valid concepts
- runtime/infra surfaces that still require explicit lifecycle decisions

## Acceptance Criteria

This audit spec is complete when:

1. stable language concepts, application concepts, orchestration surfaces, and boundary concepts are explicitly classified
2. default happy-path rules are explicit enough to anchor future README and guide work
3. advanced but valid concepts are preserved without being mistaken for default-path concepts
4. runtime/infra surfaces are acknowledged without becoming first-screen identity
5. `Wrapper` is explicitly classified as a remove/deprecate target
6. advanced read-only weak-reference modeling is explicitly bounded without changing repository semantics
7. later issue work can use this spec to judge whether a capability belongs to:
   - core language
   - default path
   - advanced concept
   - runtime/infra surface
   - removal path
