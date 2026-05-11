# Cap4k Authoring Guide Overview

[中文](index.zh-CN.md)

> This guide system defines how cap4k projects are expected to be written, reviewed, and intentionally extended beyond the default path.

Phase one keeps deep guide bodies Chinese-first. This page is the English navigation layer.

## What This Guide System Solves

- gives project authors a direct way to start writing cap4k projects
- gives reviewers a stable Default Happy Path audit baseline
- keeps human authors responsible for domain decisions, path choices, and final audit during AI-assisted work

## Human And AI Collaboration Boundary

This authoring guide is the human-facing decision and audit entrypoint. It helps human authors decide:

- which cap4k tactical object should carry a business behavior
- when to stay on the Default Happy Path and when to allow a bounded deviation
- which code should be generated, handwritten, copied as a generation snapshot, or customized through templates
- how to audit AI-assisted output before accepting it

AI agents may assist decisions, implement most changes, and run tests, compile, generation, analysis, and link checks before final audit. They do not replace human judgment over the domain flow, architecture tradeoffs, or final code shape.

AI authoring rules are maintained as an independent skill. The public authoring docs are not a runtime dependency of that skill; the two surfaces share project discipline but serve different users.

## Reading Paths

### Project Authors

1. [Framework Positioning](framework-positioning.md)
2. [Getting Started](getting-started.md)
3. [Default Happy Path](default-happy-path.md)
4. [Generator Guide](generator/index.zh-CN.md)
5. [Domain Authoring Guide](domain.zh-CN.md)
6. [Application Authoring Guide](application.zh-CN.md)
7. [Testing Contract](testing-contract.zh-CN.md)
8. [Adapter Authoring Guide](adapter.zh-CN.md)
9. [Advanced Concepts Guide](advanced/index.zh-CN.md)

### Deep Users / Framework Contributors

- start with this overview and Default Happy Path
- then move into horizontal contracts and reference material as needed

## Guide Entrypoints

- [Framework Positioning](framework-positioning.md)
- [Getting Started](getting-started.md)
- [Default Happy Path](default-happy-path.md)
- [Generator Guide](generator/index.zh-CN.md)
- [Domain Authoring Guide](domain.zh-CN.md)
- [Application Authoring Guide](application.zh-CN.md)
- [Adapter Authoring Guide](adapter.zh-CN.md)
- [Advanced Concepts Guide](advanced/index.zh-CN.md)

## Horizontal Contracts

- [Naming And Layout](naming-and-layout.zh-CN.md)
- [Generation / Handwritten Boundary](generation-boundaries.zh-CN.md)
- [Example Contract](example-contract.zh-CN.md)
- [Testing Contract](testing-contract.zh-CN.md)

## Audit Focus

Before accepting AI-assisted work, human reviewers should check:

- whether the business process is still expressed through aggregate roots, commands, queries, domain events, and orchestration surfaces
- whether write behavior stays in command handling instead of controller, job, or transport glue
- whether generated artifacts, handwritten artifacts, template overrides, and copied generation snapshots are clearly separated
- whether the `domain` and `application` happy path has behavior evidence that follows the testing contract
- whether the AI provided reproducible test, compile, generation, analysis, or link-check evidence
- whether gaps were recorded explicitly instead of being hidden behind local conventions

## Current Gaps And Extension Points

These topics can be discussed or demonstrated, but should not be treated as complete default capabilities in this v1 guide:

- value object, Saga, and Domain Service authoring qualification will continue to improve
- value object, Saga, Domain Service, and integration-event generator support needs later slices
- layered model and public tactical model qualification will continue to converge
- design-driven support for command, query, client, and domain_event contracts will continue to improve; `integration_event`, `value_object`, and `domain_service` remain follow-up extension points
- `drawing_board.json` remains a later extension point for cross-service integration-event communication
- addon / SPI authoring rules for advanced users should grow after more real usage
