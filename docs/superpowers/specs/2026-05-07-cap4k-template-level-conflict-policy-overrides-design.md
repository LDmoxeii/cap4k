# Cap4k Template-Level Conflict Policy Override Design

Date: 2026-05-07

Status: Proposed

Scope: add template-level conflict policy overrides without changing the current pipeline ownership contract, primarily in:

- `cap4k-plugin-pipeline-api`
- `cap4k-plugin-pipeline-core`
- `cap4k-plugin-pipeline-gradle`
- planner call sites only when the shared planning layer cannot carry the resolution cleanly

Out of scope:

- broad template override redesign
- family-level conflict policy systems
- relation-level conflict policy systems
- generator ownership redesign
- wrapper cleanup
- legacy `cap4k-plugin-codegen` removal or compatibility work

## Backlog Source

This design is the implementation slice for:

- `#11` generator: support artifact-level conflict policy overrides

This slice closes the contract honestly as `template-level`, keyed by `templateId`.
It does not claim per-output-path artifact-instance selection.
That wording matches current repository reality, where multiple planned outputs can intentionally share the same `templateId`.

## Problem

Real migration and dogfood projects do not want one global policy for every checked-in artifact.

Typical need:

- overwrite generated contract surfaces such as request/response or factory/specification scaffolds
- preserve handwritten handlers, validators, controllers, subscribers, or behavior code

Current `templates.conflictPolicy` is project-global and too coarse for that workflow.

## Current Flow Audit

### 1. How `templates.conflictPolicy` enters plan items today

The current path is:

1. Gradle DSL sets `cap4k.templates.conflictPolicy`
2. `Cap4kProjectConfigFactory` maps it into `ProjectConfig.templates.conflictPolicy`
3. planners emit `ArtifactPlanItem.conflictPolicy` in one of three ways:
   - many planners set `conflictPolicy = config.templates.conflictPolicy` directly
   - aggregate checked-in helpers default `conflictPolicy` to `config.templates.conflictPolicy`
   - generated-source helpers hardcode `ConflictPolicy.OVERWRITE`
4. `DefaultPipelineRunner` currently passes planner-emitted `ArtifactPlanItem.conflictPolicy` through unchanged
5. `cap4kPlan` writes `PlanReport.items[*].conflictPolicy` exactly as carried by the runner result
6. `FilesystemArtifactExporter` still hard-forces `GENERATED_SOURCE` artifacts to `OVERWRITE` at write time

This means the current plan output already exposes one conflict policy field per artifact, but that field can only reflect:

- a planner hardcoded default
- or the one global template-level default

It cannot represent a direct template-level override.

### 2. Current predictability rules

Current ownership behavior is already split:

- `GENERATED_SOURCE` artifacts are overwriteable
- checked-in scaffolds mostly inherit the global template conflict policy
- a few checked-in surfaces deliberately hardcode `SKIP`, for example aggregate behavior scaffolds

That ownership split is intentional and should remain the baseline.

## Required Questions

### 1. Current `templates.conflictPolicy` path into plan items

Answer:

- it enters `ProjectConfig.templates.conflictPolicy` through the Gradle config factory
- planners or planner helpers copy it into `ArtifactPlanItem.conflictPolicy`
- generated-source helpers bypass it and emit `OVERWRITE`
- `DefaultPipelineRunner` currently does not resolve anything further
- `cap4kPlan` exposes the item field as-is in plan JSON

### 2. Which input model layer should own template-level override

Answer:

Template-level override should live in `TemplateConfig`, keyed by stable `templateId`.

Reason:

- conflict policy already belongs to template-generation configuration, not source configuration or canonical model
- `templateId` is the current stable template identity that already exists across planners, runner output, and `cap4kPlan`
- this avoids creating a new family hierarchy or generator-specific override DSL

Recommended shape:

```kotlin
data class TemplateConfig(
    val preset: String,
    val overrideDirs: List<String>,
    val conflictPolicy: ConflictPolicy,
    val templateConflictPolicies: Map<String, ConflictPolicy> = emptyMap(),
)
```

Gradle DSL should surface this as a simple map under `templates`, not as a new override subsystem.

### 3. How resolved policy should be exposed in planning output

Answer:

`ArtifactPlanItem.conflictPolicy` should remain the single stable field for the final resolved effective policy.

That means:

- no extra `resolvedConflictPolicy` field is required
- `cap4kPlan` keeps the same JSON shape for plan items
- the meaning of `conflictPolicy` becomes “final effective write behavior for this artifact in the pipeline contract”

This is stable because:

- plan JSON already serializes `ArtifactPlanItem.conflictPolicy`
- downstream tooling already reads plan items by stable fields such as `templateId`, `outputKind`, `resolvedOutputRoot`, and `conflictPolicy`
- no new reporting envelope is needed just to expose the resolved value

### 4. How to keep mixed generated / handwritten surfaces predictable

Answer:

Use a fixed precedence model:

1. `GENERATED_SOURCE` always resolves to `OVERWRITE`
2. non-generated artifacts use `templates.templateConflictPolicies[templateId]` when present
3. otherwise they keep the planner-emitted default

This keeps predictability because:

- generated-source ownership stays intact
- existing handwritten defaults stay intact when no override is configured
- explicit template overrides only affect the targeted template ids

## Core Decisions

### 1. Resolution belongs in the shared planning layer, not in every planner

Template-level override should be resolved after planners emit raw `ArtifactPlanItem`s and before:

- plan report emission
- rendering
- exporting

Recommended location:

- `DefaultPipelineRunner`

Reason:

- it keeps planner contracts stable
- it avoids a broad rewrite across every generator family
- it ensures `cap4kPlan`, renderer input, and exporter input all see the same effective policy

This also means direct unit tests on isolated planners still observe planner defaults, while pipeline planning output observes resolved effective policy. That is acceptable because the issue is about planning output and generator behavior, not about exposing an additional raw-planner contract.

### 2. Template identity is `templateId` in this slice

Template-level override in this slice means:

- one override key per `templateId`

It does not mean:

- per output path instance
- per entity instance
- per relation instance
- per family alias

This is the smallest direct template-granularity model that current pipeline contracts can support without redesign.

### 3. Generated-source override attempts do not change ownership

If a user targets a generated-source artifact template with `SKIP` or `FAIL`, the resolved policy remains `OVERWRITE`.

Reason:

- generated-source ownership under `build/` is a mainline pipeline contract
- changing that here would reopen the generated-vs-handwritten boundary
- exporter behavior already hard-enforces this rule

Planning output should therefore show `OVERWRITE` for generated-source items even when a configured template override exists.

### 4. Planner hardcoded defaults remain overrideable for non-generated artifacts

Some checked-in artifacts are intentionally emitted with hardcoded defaults today, for example behavior scaffolds.

Template-level override should still be able to replace those defaults for non-generated artifacts emitted from the targeted template.

Reason:

- the issue asks for finer conflict control than one project-global default
- keeping planner hardcoded defaults as an unoverrideable exception would reintroduce hidden family policy behavior
- predictability is preserved as long as the resolved value is visible in `ArtifactPlanItem.conflictPolicy`

## Detailed Design

### A) API and DSL model

Add `templateConflictPolicies` to `TemplateConfig`.

Gradle side should mirror that with a simple map:

- `cap4k.templates.templateConflictPolicies`

Normalization rules:

- trim template ids
- reject blank template ids
- trim policy values
- parse policy values with existing `ConflictPolicy`

The model remains intentionally simple:

- keys are raw `templateId`
- values are raw `ConflictPolicy`

No new artifact selector language is introduced.

### B) Shared conflict-policy resolution

Add a shared resolver for `ArtifactPlanItem` effective conflict policy.

Recommended logic:

```kotlin
private fun resolveConflictPolicy(config: ProjectConfig, item: ArtifactPlanItem): ArtifactPlanItem {
    val resolved = when (item.outputKind) {
        ArtifactOutputKind.GENERATED_SOURCE -> ConflictPolicy.OVERWRITE
        else -> config.templates.templateConflictPolicies[item.templateId] ?: item.conflictPolicy
    }
    return if (resolved == item.conflictPolicy) item else item.copy(conflictPolicy = resolved)
}
```

`DefaultPipelineRunner` should apply this before render/export.

### C) Plan output behavior

After runner-level resolution:

- `PipelineResult.planItems[*].conflictPolicy` is final
- `PlanReport.items[*].conflictPolicy` is final
- renderer input is final
- exporter input is final

No new item field is needed.

### D) Planner impact

Most planners should remain unchanged.

That includes:

- planners that emit `config.templates.conflictPolicy`
- aggregate helper functions that default checked-in artifacts to `config.templates.conflictPolicy`
- planners that hardcode `SKIP`
- planners that hardcode `OVERWRITE` for generated sources

Reason:

- runner-level resolution composes on top of those defaults
- this avoids unnecessary planner churn

Planner-specific changes should only happen if a test or a hidden planner-only path proves a shared resolution layer is insufficient.

## Compatibility Expectations

### Safe default

If `templates.templateConflictPolicies` is empty:

- pipeline behavior is unchanged
- plan JSON shape is unchanged
- existing tests should continue to pass

### Intended behavior change

When a checked-in artifact template id is targeted:

- `ArtifactPlanItem.conflictPolicy` changes to the targeted override in `cap4kPlan`
- renderer/exporter behavior follows that resolved value

### Stable non-change

When a generated-source artifact template id is targeted:

- final resolved `ArtifactPlanItem.conflictPolicy` still remains `OVERWRITE`

## Verification

This slice is complete when all are true:

1. API/config tests prove `TemplateConfig` carries template-level conflict policy overrides
2. Gradle config factory tests prove DSL mapping and normalization into `TemplateConfig`
3. planning-layer regression tests prove checked-in artifacts can be overridden by `templateId`
4. planning-layer regression tests prove generated-source items still resolve to `OVERWRITE`
5. functional `cap4kPlan` coverage proves plan JSON exposes final resolved per-item `conflictPolicy`
6. functional coverage proves mixed checked-in and generated-source artifacts remain predictable under overrides

## Residual Risks

1. This slice keys overrides by `templateId`, so one override affects every item emitted from that template.
2. The plan JSON exposes final resolved policy, but it does not separately expose the reason or source of resolution. If downstream audit needs to distinguish “global default” from “template override” from “generated-source fixed overwrite”, that is future strengthening.
3. Overrides targeting generated-source templates are intentionally ignored at resolution time. This is predictable, but current scope does not add an explicit warning or diagnostic for that configuration.
