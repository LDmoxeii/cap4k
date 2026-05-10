# cap4k Artifact Addon SPI And only-engine Enum Translation Design

Date: 2026-05-10

Status: Proposed

Scope: define `#33` as a general artifact-addon extension point for `cap4k` and use only-engine enum translation as the first same-cycle reference addon migrated out of cap4k core.

Out of scope:

- implementing a full generic plugin ecosystem
- allowing addons to mutate canonical models
- allowing addons to replace renderer or exporter behavior
- preserving cap4k core `enumTranslation` DSL toggles
- keeping only-engine runtime imports or templates inside cap4k core
- redesigning enum source ownership, DB enum parsing, or enum type resolution

## Backlog Source

This design covers:

- `#33` generator: decouple enum translation from cap4k core via addon SPI

It also protects the later sequence:

- `#17` AI collaboration guide must not describe only-engine enum translation as a stable cap4k core capability
- `#27` reference project can continue with enum translation disabled until this issue lands

## Background

`cap4k` currently exposes enum translation as an aggregate artifact capability:

- `generators.aggregate.artifacts.enumTranslation`
- `layout.aggregateEnumTranslation`
- `enumManifest.generateTranslation`
- `SharedEnumDefinition.generateTranslation`
- `EnumTranslationArtifactPlanner`
- `aggregate/enum_translation.kt.peb`

The generated template is not generic. It imports and implements only-engine runtime contracts:

- `com.only.engine.translation.annotation.TranslationType`
- `com.only.engine.translation.core.TranslationInterface`
- `com.only.engine.translation.core.BatchTranslationInterface`

That means cap4k core currently carries an implicit only-engine adapter. This is the wrong dependency direction.

The correct ownership is:

- cap4k owns enum source parsing, enum ownership, canonical enum descriptors, and artifact pipeline mechanics
- only-engine owns only-engine translation runtime contracts and generated adapter shape

## Problem

The original enum translation implementation solved a real need, but it placed the solution at the wrong layer.

There are two separate responsibilities mixed together today:

1. cap4k enum resolution
   - shared enums from enum manifest
   - aggregate-local enums from DB metadata
   - canonical enum names and type FQNs
   - shared/local ownership rules
2. only-engine translation adapter generation
   - translation type naming
   - only-engine imports
   - only-engine interfaces
   - Spring component shape

Keeping both in cap4k core creates three risks:

- cap4k appears to depend on only-engine without declaring that dependency
- later docs and AI guidance can accidentally freeze only-engine translation as cap4k core behavior
- advanced users cannot add comparable artifact families without patching cap4k itself

## Goals

1. Remove only-engine-specific enum translation from cap4k core.
2. Keep cap4k enum parsing and enum canonical resolution as core capabilities.
3. Add a minimal, general artifact-addon SPI after canonical assembly.
4. Make addon artifacts behave like built-in artifacts for project users.
5. Support project-level template override for addon-provided templates.
6. Support template-level conflict policies for addon artifacts using the same `templateId` mechanism as built-in artifacts.
7. Implement only-engine enum translation as the first reference addon in the same delivery line, so it can migrate from the existing cap4k implementation instead of being reimplemented later.
8. Keep `#17` from depending on the current mixed core/addon story.

## Non-Goals

- Do not add enum-specific SPI such as `EnumTranslationProvider`.
- Do not let addons collect sources directly.
- Do not let addons mutate `CanonicalModel`.
- Do not let addons replace the renderer or exporter.
- Do not scan arbitrary project runtime classpaths for addons.
- Do not preserve per-enum `generateTranslation` toggles.
- Do not preserve aggregate-level `enumTranslation` toggles.
- Do not preserve `layout.aggregateEnumTranslation`.
- Do not keep only-engine templates in cap4k resources.

## Options Considered

### Option 1: hide or disable enumTranslation in cap4k core

This would keep most implementation code in place but stop recommending it.

Pros:

- fastest immediate patch
- low implementation cost

Cons:

- only-engine imports and templates remain in cap4k core
- docs and AI guidance can still discover the wrong capability story
- external users get no real extension point

Decision: reject.

### Option 2: enum-specific addon SPI

This would remove only-engine code from cap4k core and expose an enum translation extension point.

Pros:

- directly solves enum translation
- smaller than a general addon SPI

Cons:

- opens the wrong abstraction, because the extension need is artifact contribution, not enum translation specifically
- likely repeats when another artifact family needs external ownership
- gives advanced users a narrow, one-off mechanism

Decision: reject.

### Option 3: general artifact-addon SPI with only-engine enum translation as first addon

This opens one extension point after canonical assembly. Addons can inspect `ProjectConfig` and `CanonicalModel`, then contribute normal `ArtifactPlanItem`s. only-engine enum translation becomes a reference addon using this path.

Pros:

- removes only-engine from cap4k core
- gives advanced users a reusable artifact-generation extension point
- keeps canonical model ownership in cap4k
- keeps rendering, conflict policy, plan visibility, and export behavior unified
- lets only-engine migrate the existing planner/template logic directly

Cons:

- larger than a pure removal
- requires Gradle addon dependency wiring
- requires addon resource template resolution

Decision: choose this option.

## Chosen Design

The minimal public extension layer is:

```text
SourceProvider -> CanonicalAssembler -> ArtifactAddonProvider -> Renderer -> Exporter
```

The addon SPI is post-canonical and pre-render:

- addon providers cannot add source snapshots
- addon providers cannot mutate canonical models
- addon providers can inspect the already assembled `CanonicalModel`
- addon providers can contribute `ArtifactPlanItem`s
- addon plan items flow through the same pipeline as built-in artifact plan items

Conceptual API:

```kotlin
interface ArtifactAddonProvider {
    val id: String

    fun plan(context: ArtifactAddonContext): List<ArtifactPlanItem>
}

data class ArtifactAddonContext(
    val config: ProjectConfig,
    val model: CanonicalModel,
    val options: Map<String, Any?>,
)
```

The exact package and API details can be finalized in the implementation plan, but the shape must stay artifact-oriented rather than enum-oriented.

## Addon Installation

Addon installation should be explicit build-time configuration, not runtime classpath scanning.

Project A should depend on two separate concepts when using only-engine enum translation:

```kotlin
dependencies {
    implementation("com.only4:only-engine-translation:...")
    cap4kAddon("com.only4:only-engine-cap4k-addon:...")
}
```

Meaning:

- `implementation` provides runtime classes needed by generated application code
- `cap4kAddon` provides generation-time providers and bundled templates

cap4k should not scan arbitrary `implementation` dependencies for addon providers. That would make generation behavior depend on unrelated runtime dependencies and would make accidental provider loading hard to diagnose.

The Gradle plugin should create a resolvable `cap4kAddon` configuration and load addon providers from that configuration.

Provider loading rules:

- no addon configuration means no addon providers
- duplicate provider ids fail fast
- malformed addon provider metadata fails fast
- provider load failures fail fast with the provider id and dependency context where possible

## Addon Template Identity

Addon template ids must be stable plain paths.

Use:

```text
addons/<addonId>/<path>
```

Example:

```text
addons/only-engine-enum-translation/aggregate/enum_translation.kt.peb
```

Do not use URI-like ids such as:

```text
addon:only-engine-enum-translation/aggregate/enum_translation.kt.peb
```

Reasons:

- plain paths work better with Windows, Git, IDEs, and template override directories
- existing `templateConflictPolicies` already key by template id
- users can reason about addon and built-in templates through one path-based mechanism

## Template Resolution

Addon template resolution must support project overrides.

Resolution order:

1. project `templates.overrideDirs`
2. addon jar resources
3. cap4k preset resources, only for built-in cap4k templates

For the only-engine addon example, project override file:

```text
cap4k-templates/addons/only-engine-enum-translation/aggregate/enum_translation.kt.peb
```

Addon jar bundled resource:

```text
cap4k/addons/only-engine-enum-translation/aggregate/enum_translation.kt.peb
```

If an addon template id cannot be found in project overrides or in the owning addon jar, generation must fail fast. It must not silently fall back to a cap4k core preset template.

## Consumer Experience

For project users, built-in and addon artifacts must behave the same once they are in the plan.

This means addon artifacts must support the same mechanisms:

- `cap4kPlan`
- `cap4kGenerate`
- `templates.overrideDirs`
- `templates.templateConflictPolicies`
- global `templates.conflictPolicy`
- standard exporter behavior
- standard `SKIP`, `OVERWRITE`, and `FAIL` semantics

Example: project A overrides only-engine addon conflict policy exactly like a built-in template:

```kotlin
cap4k {
    templates {
        templateConflictPolicies.put(
            "addons/only-engine-enum-translation/aggregate/enum_translation.kt.peb",
            "OVERWRITE"
        )
    }
}
```

Addon providers must not bypass the pipeline and write files directly. They must return normal `ArtifactPlanItem`s.

## cap4k Core Removal

cap4k core should remove:

- `generators.aggregate.artifacts.enumTranslation`
- `layout.aggregateEnumTranslation`
- `enumManifest.generateTranslation`
- `SharedEnumDefinition.generateTranslation`
- canonical enum translation enable flags
- `EnumTranslationArtifactPlanner`
- `aggregate/enum_translation.kt.peb`
- public docs that describe enum translation as a built-in aggregate artifact
- tests and fixtures that compile only-engine translation stubs as cap4k core behavior

cap4k core should keep:

- enum manifest as a shared enum source
- DB local enum parsing
- shared/local enum ownership resolution
- canonical enum descriptors
- enum class generation
- type registry and enum field type resolution

Without an installed addon, no enum translation artifact should appear in `cap4kPlan` or `cap4kGenerate`.

## only-engine Addon

The same delivery line should implement an only-engine addon as the first reference implementation.

Suggested build-time module:

```text
only-engine-cap4k-addon
```

The exact module name can be adjusted to only-engine repository conventions, but the module must:

- depend on the cap4k artifact addon SPI
- contribute enum translation artifacts through `ArtifactAddonProvider`
- bundle its default template under `cap4k/addons/only-engine-enum-translation/...`
- own only-engine imports, interfaces, annotations, naming, and runtime contract decisions
- generate translation artifacts for resolved shared/local enums by default when installed

The only-engine addon can migrate the existing cap4k implementation logic:

- shared enum translation candidates
- local enum translation candidates
- enum FQN resolution usage
- translation type key naming
- `TranslationType`
- `TranslationInterface`
- `BatchTranslationInterface`

After migration, cap4k must not contain `com.only.engine.translation.*` imports, templates, or compile fixtures.

## Plan Visibility

Addon artifacts must appear in plan output like built-in artifacts.

The plan item should make these facts visible:

- addon generator id
- template id
- module role
- output path
- conflict policy
- output kind

This is important for reviewability and for later AI guidance. Addon behavior must not be a hidden side effect of generation.

## Failure Behavior

Fail fast in these cases:

- duplicate addon provider id
- configured addon cannot be resolved
- addon provider throws during planning
- addon plan item references an addon template that cannot be resolved
- addon template id claims an addon id that has no loaded provider
- addon attempts to use reserved cap4k built-in template namespaces

Output path conflicts should continue through the standard exporter and conflict policy semantics unless the implementation discovers a pre-export ambiguity that is already handled elsewhere in the pipeline.

## Public Documentation

Public docs should say:

- enum generation remains cap4k core
- enum translation is not cap4k core
- enum translation requires an addon such as only-engine's cap4k addon
- addon artifacts use the same template override and conflict policy mechanisms as built-in artifacts
- build-time addon dependency and runtime library dependency are separate concerns

Public docs should not describe only-engine enum translation as a normal `generators.aggregate.artifacts` flag.

## Implementation Boundaries

The implementation plan should split work into at least these slices:

1. cap4k API and Gradle plumbing for artifact addons
2. addon-aware template resolution
3. cap4k core enum translation removal
4. only-engine addon reference implementation
5. fixture and documentation updates
6. downstream or sample verification

Because this issue includes same-cycle only-engine addon work, the plan must explicitly name which repository owns each slice. The cap4k issue remains the governing issue because the primary repair is the cap4k extension point and core cleanup. If execution needs separate repo-level tracking for only-engine, that issue should be linked from `#33` rather than redefining the design.

## Acceptance Criteria

- cap4k exposes a general post-canonical `ArtifactAddonProvider`-style SPI.
- cap4k Gradle plugin supports explicit build-time addon dependencies.
- addon providers can contribute normal `ArtifactPlanItem`s.
- addon artifacts appear in `cap4kPlan`.
- addon artifacts generate through `cap4kGenerate`.
- addon artifacts honor `templates.overrideDirs`.
- addon artifacts honor `templates.templateConflictPolicies` by `templateId`.
- addon artifacts use standard conflict policy and exporter behavior.
- addon bundled templates can be overridden by project templates.
- cap4k core no longer exposes aggregate `enumTranslation` DSL.
- cap4k core no longer exposes `layout.aggregateEnumTranslation`.
- enum manifest and canonical enum models no longer carry `generateTranslation`.
- cap4k core no longer contains only-engine translation imports or templates.
- without an addon installed, enum translation artifacts are absent from plan and generation.
- only-engine provides a same-cycle addon that migrates the old enum translation planner/template behavior.
- only-engine addon is the first reference implementation for the new artifact addon SPI.
- public docs explain the addon boundary and the identical consumer behavior for built-in and addon artifacts.
