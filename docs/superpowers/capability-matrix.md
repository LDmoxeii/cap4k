# Cap4k Capability Matrix

## Purpose

This document is the current human-readable truth source for Cap4k capability support.

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
| `family` | Capability family such as `bootstrap` or `aggregate` |
| `status` | One of `implemented`, `partial`, `deferred`, `blocked` |
| `contract` | Current supported boundary, not desired future behavior |
| `verificationLayers` | Current proof layers: `unit`, `compile`, `functional`, `runtime`, `project` |
| `verificationTargets` | Concrete tests, fixtures, or project stages that provide the proof |
| `projectRequired` | Whether `only-danmaku-next` must eventually verify this capability |
| `notesOrGaps` | Current caveats, deferred edges, or missing layers |

## Current Matrix

| capabilityId | family | status | contract | verificationLayers | verificationTargets | projectRequired | notesOrGaps |
| --- | --- | --- | --- | --- | --- | --- | --- |
| `bootstrap.in_place_root` | `bootstrap` | `implemented` | Default bootstrap mode is in-place self-hosting root rewrite against a recognized host root with bounded managed-section ownership. | `unit`, `functional`, `project` | `BootstrapManagedSectionMergerTest`; `BootstrapRootStateGuardTest`; `PipelinePluginBootstrapInPlaceFunctionalTest`; `only-danmaku-next: stage/1-bootstrap-in-place` | `yes` | Truly empty-directory launch is still out of scope. |
| `bootstrap.preview_subtree` | `bootstrap` | `implemented` | Preview mode renders a separate subtree only when explicitly requested with `mode = PREVIEW_SUBTREE` and `previewDir`. | `unit`, `functional` | `Cap4kBootstrapConfigFactoryTest`; `PipelinePluginBootstrapPreviewFunctionalTest`; `PipelinePluginBootstrapGeneratedProjectFunctionalTest` | `no` | First-round verification-project state stays on the in-place path. |
| `aggregate.minimal_baseline` | `aggregate` | `implemented` | DB-backed aggregate generation can emit bounded aggregate and schema-meta outputs for selected tables. | `functional`, `compile` | `PipelinePluginFunctionalTest`; `PipelinePluginCompileFunctionalTest`; aggregate compile fixtures | `yes` | Not yet materialized in the first-round verification-project stages. |
| `aggregate.factory_specification` | `aggregate` | `implemented` | Aggregate factory and specification outputs are supported as bounded optional surfaces under the new pipeline. | `unit`, `functional`, `compile` | `AggregateArtifactPlannerTest`; aggregate functional fixtures; aggregate compile fixtures | `yes` | Verification-project stage not started yet. |
| `aggregate.wrapper` | `aggregate` | `implemented` | Wrapper output is available as a bounded aggregate-side optional surface. | `unit`, `functional`, `compile` | aggregate planner tests; aggregate functional fixtures; aggregate compile fixtures | `yes` | Verification-project stage not started yet. |
| `aggregate.unique_bundle` | `aggregate` | `implemented` | `unique-query`, `unique-query-handler`, and `unique-validator` are treated as one lifecycle-coupled capability bundle. | `unit`, `functional`, `compile` | aggregate planner tests; aggregate functional fixtures; aggregate compile fixtures | `yes` | Should enter the verification project as one stage, not three unrelated stages. |
| `aggregate.enum_translation` | `aggregate` | `implemented` | Domain enum and translation output support bounded aggregate ownership and explicit aggregate-side translation generation. | `unit`, `functional`, `compile` | aggregate planner tests; aggregate functional fixtures; aggregate compile fixtures | `yes` | Shared-domain enum DSL is still a separate design discussion. |
| `aggregate.persistence_controls` | `aggregate` | `implemented` | Aggregate persistence controls cover bounded field-behavior, provider-specific entity behavior, and custom generator output within the accepted contract. | `unit`, `functional`, `compile` | aggregate planner tests; aggregate functional fixtures; aggregate compile fixtures | `yes` | Runtime persistence smoke is the next explicit framework hardening slice. |
| `aggregate.relation_baseline` | `aggregate` | `partial` | Relation support covers bounded one-to-one, many-to-one, and one-to-many semantics plus accepted inverse read-only behavior. | `unit`, `functional`, `compile` | relation planner tests; relation functional fixtures; relation compile fixtures | `yes` | `ManyToMany` and join-table recovery remain deferred. |

## Usage Rules

When a new framework slice lands:
1. update or add the relevant matrix row
2. point to the concrete verification targets
3. decide whether the capability is required in `only-danmaku-next`
4. record the missing layer honestly if project verification has not happened yet
