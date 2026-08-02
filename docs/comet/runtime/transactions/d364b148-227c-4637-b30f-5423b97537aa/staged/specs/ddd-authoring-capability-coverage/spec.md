# DDD Authoring Capability Coverage

## Purpose

cap4k MUST maintain a reviewable coverage contract that separates DDD problem-space strategic design from solution-space tactical implementation. The contract MUST let a future authoring skill decide whether a concept is handled by human modeling workflow, cap4k source/runtime, cap4k generation, an external provider, or an explicitly recorded gap.

## Coverage Dimensions

Every audited DDD concept MUST be evaluated across the four cap4k capability blocks and their handoffs:

1. Skill collaboration, strategic artifacts and strategic-to-tactical translation.
2. Generator inputs, planning, ownership and structural projection.
3. Runtime tactical semantics and execution boundaries.
4. Analyzer observation, causal/structural evidence, drift detection and feedback to the skill.

Runnable or focused project evidence MUST be evaluated across the four blocks rather than treated as a fifth implementation block.

The audit MUST NOT treat handwritten expressibility as equivalent to first-class framework or generator support.

## Concept Groups

The mandatory benchmark is the complete core concept system of *Implementing Domain-Driven Design*:

- domains and subdomains;
- bounded contexts and context maps;
- architecture;
- entities, value objects, domain services, domain events, modules, aggregates, factories and repositories;
- bounded-context integration;
- the application layer.

The contract MUST cover:

- strategic design artifacts and decisions;
- tactical modeling building blocks;
- application/use-case orchestration;
- bounded-context integration patterns;
- persistence, reliability and long-running workflow boundaries;
- generation ownership and verification evidence.

Event Sourcing, independently persisted CQRS read models, Process Manager/Saga and module-runtime frameworks MUST be reported as a modern-extension appendix. Their absence MUST NOT by itself fail the core-book sufficiency decision.

## Status Vocabulary

Each concept MUST receive one of these primary statuses:

- `strong`: current cap4k has coherent contracts and adequate evidence for the audited layer.
- `partial`: useful support exists, but an important contract, generator surface, runtime behavior, or proof is incomplete.
- `external/provider`: the concept is intentionally delegated outside cap4k core, with a defined integration boundary.
- `missing/not-first-class`: no coherent first-class contract exists at the audited layer.

The report MAY add layer-specific qualifiers, but MUST preserve the difference between design-method support and technical support.

## Sufficiency Decision

The final audit MUST state separately:

- whether cap4k is sufficient as a tactical implementation substrate for ordinary bounded-context applications;
- whether cap4k plus the authoring skill is sufficient for the confirmed end-to-end DDD workflow;
- whether the translation from strategic artifacts to cap4k tactical carriers has acceptably low cost and preserves traceability;
- which gaps block skill optimization now;
- which gaps can remain explicit non-goals or provider boundaries;
- which gaps require a cap4k issue before the skill can claim complete coverage.

Strategic design decisions, including subdomain classification, bounded-context discovery, context-map relationships and organizational ownership, MAY be owned by human-and-skill collaboration without first-class cap4k runtime types.

Tactical design MUST be evaluated as a cap4k responsibility. The authoring skill MUST translate confirmed strategic decisions into cap4k tactical carriers, generator input surfaces, expected skeletons, handwritten business-logic slots and verification evidence.

Framework quality MUST include translation friction. The audit MUST identify duplicate entry, manual reshaping, information loss, unsupported carriers and structural exceptions between strategic artifacts and cap4k inputs.

The accepted low-friction target is structured, traceable, semi-automatic projection. Human confirmation remains required for domain judgments. After confirmation, the skill SHOULD be able to project decisions into supported generator inputs without re-asking established business facts or forcing the author to guess cap4k carriers.

## Four-Block Collaboration

- The skill owns human collaboration, strategic-design artifacts, tactical translation, rollback and end-to-end orchestration.
- The generator owns deterministic projection from supported tactical inputs to planned and materialized structure with explicit ownership.
- The runtime owns tactical execution semantics, transactions, persistence, messaging and provider boundaries.
- The analyzer owns post-implementation observation and feedback. Its ability to expose structure, calls, causal flow and drift MUST be audited separately from compile/test evidence.

The final report MUST identify broken or high-friction handoffs between each adjacent block.

## Analyzer Authority Boundary

Analyzer outputs MUST be treated as observation, verification and drift-feedback evidence. They MAY show implemented structure, calls, causal edges, design projections and recovery hints. They MUST return to the skill for human interpretation and rollback decisions.

Analyzer output MUST NOT automatically become authoritative business truth or ordinary source-generation input. A fragment MAY enter generation only after human confirmation and transformation onto a supported DB, design JSON, manifest, Gradle, addon/options or template input contract.

The audit MUST separately report analyzer coverage gaps, including incomplete cross-entry process projection, mixed graph/design-projection transport, drawing-board recovery compatibility and absent runnable evidence.

## Required Audit Deliverable

The completed audit MUST include:

1. A concept-by-concept matrix for the mandatory book benchmark.
2. A four-block rating for skill, generator, runtime and analyzer.
3. A handoff-friction assessment for strategic-to-tactical translation, generation, execution and analysis feedback.
4. A direct sufficiency conclusion for ordinary bounded-context DDD delivery.
5. A separate conclusion on whether the existing authoring skill may now claim a complete DDD workflow.
6. A split of gaps into skill-only work, cap4k framework/generator/analyzer work, explicit provider boundaries and optional modern extensions.
7. Current repository and backlog evidence plus explicit static-verification limitations.

## Traceability

Every material conclusion MUST trace to current repository evidence or current backlog state. Historical plans MAY explain intent but MUST NOT override current source, current contracts, or current issue state.

## Verification Limits

A static audit MUST be labelled static. It MUST identify missing runnable reference-project evidence, unrun tests, and open framework issues separately from concept coverage.
