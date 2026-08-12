# Cap4k Capability Matrix

## Purpose

This document is an internal audit aid. Current capability support is established by production descriptors, registries, task registration, generated Agent facts, and focused verification.

It records:

- what the framework currently claims to support
- which verification layers prove that support
- whether a capability must also appear in `only-danmaku-next`
- which gaps remain explicitly open

This document is not:

- a roadmap
- a future-wishlist
- a restatement of chat history

## Field Contract

| Field | Meaning |
| --- | --- |
| `capabilityId` | Stable identifier used by specs, plans, and verification docs |
| `family` | Capability family such as `aggregate` or `design` |
| `status` | One of `implemented`, `partial`, `deferred`, `blocked` |
| `contract` | Current supported boundary, not desired future behavior |
| `verificationLayers` | Current proof layers: `unit`, `compile`, `functional`, `runtime`, `project` |
| `verificationTargets` | Concrete tests, fixtures, or project stages that provide the proof |
| `projectRequired` | Whether `only-danmaku-next` must eventually verify this capability |
| `notesOrGaps` | Current caveats, deferred edges, or missing layers |

## Current Matrix

| capabilityId | family | status | contract | verificationLayers | verificationTargets | projectRequired | notesOrGaps |
| --- | --- | --- | --- | --- | --- | --- | --- |
| `aggregate.minimal_baseline` | `aggregate` | `implemented` | DB-backed aggregate generation can emit bounded aggregate and schema-meta outputs for selected tables. | `functional`, `compile` | `PipelinePluginFunctionalTest`; `PipelinePluginCompileFunctionalTest`; aggregate compile fixtures | `yes` | Not yet materialized in the first-round verification-project stages. |
| `aggregate.factory` | `aggregate` | `implemented` | Every supported aggregate root generates a Factory and payload; there is no factory switch and no generated Aggregate Specification surface. | `unit`, `functional`, `compile`, `runtime` | `AggregateArtifactPlannerTest`; aggregate functional/compile fixtures; JPA Factory/UoW runtime tests | `yes` | Only genuinely unresolved business or managed fields may produce an explicit generation blocker. |
| `aggregate.enum_translation` | `aggregate` | `deferred` | Cap4k core no longer emits aggregate enum translation artifacts; enum translation output is addon-owned through build-time `ArtifactAddonProvider` dependencies. | `unit`, `functional`, `compile` | addon SPI tests; enum manifest stale flag rejection; only-engine addon verification once published | `yes` | `only-engine` owns the first enum translation addon under #33. Core shared/local enum generation remains separate and implemented. |
| `aggregate.persistence_controls` | `aggregate` | `implemented` | Aggregate persistence controls cover bounded field-behavior, provider-specific entity behavior, and custom generator output within the accepted contract. | `unit`, `functional`, `compile` | aggregate planner tests; aggregate functional fixtures; aggregate compile fixtures | `yes` | Runtime persistence smoke is the next explicit framework hardening slice. |
| `aggregate.relation_baseline` | `aggregate` | `partial` | Relation support covers bounded one-to-one, many-to-one, and one-to-many semantics plus accepted inverse read-only behavior. | `unit`, `functional`, `compile` | relation planner tests; relation functional fixtures; relation compile fixtures | `yes` | `ManyToMany` and join-table recovery remain deferred. |
| `design.integration_event` | `design` | `implemented` | Design JSON supports `integration_event` with `eventName` and `artifacts[{ family: "integration-event", variant: "inbound" \| "outbound" }]`; contracts live under `application.subscribers.integration.<variant>`, inbound events can generate Spring `@EventListener` subscribers, and outbound events generate contracts only. | `unit`, `functional`, `compile` | design integration event parser/canonical/planner/template tests; code-analysis extraction tests; `design-integration-event-compile-sample` | `yes` | No MQ-specific generators and no `EventSubscriber<T>` subscriber skeletons in this slice. |

## Usage Rules

When a new framework slice lands:

1. update or add the relevant matrix row
2. point to the concrete verification targets
3. decide whether the capability is required in `only-danmaku-next`
4. record the missing layer honestly if project verification has not happened yet
