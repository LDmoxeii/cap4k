# cap4k Extension SPI, Addon, And Gap Map

Date: 2026-05-11

This file maps the extension path for generator addons and the gaps that should influence future authoring docs and skills.

## Addon SPI

Core API:

```kotlin
interface ArtifactAddonProvider {
    val id: String
    fun plan(context: ArtifactAddonContext): List<ArtifactPlanItem>
}
```

`ArtifactAddonContext` provides:

- project config;
- canonical model;
- generation options.

Addon providers are loaded from `cap4kAddon` jars through `ServiceLoader` using:

```text
META-INF/services/com.only4.cap4k.plugin.pipeline.api.ArtifactAddonProvider
```

Duplicate provider IDs are rejected.

## Addon Planning Semantics

The pipeline runner assembles built-in source models, then invokes addon providers after the canonical model is available.

Addon plan items then go through the same normalizer/export path as built-in plan items:

- generated source output is `OVERWRITE`;
- checked-in source uses the item policy unless overridden by `templates.templateConflictPolicies`;
- rendering uses the same renderer;
- `cap4kPlan`/`cap4kGenerate` review semantics remain the same.

Business project users should not need to care whether an artifact is built in or contributed by addon.

## Addon Template Layout

Addon templates are addressed by template IDs such as:

```text
addons/<addon-id>/<family>/<template>.peb
```

The addon jar resource path is:

```text
cap4k/addons/<addon-id>/<family>/<template>.peb
```

Project `templates.overrideDirs` are checked before addon resources. Therefore a project can override addon templates by placing the same relative path under an override dir.

Example:

```text
<override-dir>/addons/only-engine-enum-translation/aggregate/enum_translation.kt.peb
```

## Conflict Policy Compatibility

Per-template policies work for addon artifacts the same way they work for built-in artifacts:

```kotlin
templates {
    templateConflictPolicies.put("addons/<addon-id>/flow/entry.json.peb", "OVERWRITE")
}
```

The exact key must match the addon's `templateId`. Authoring docs should teach users to inspect `cap4kPlan` to get the actual template ID before writing a policy.

## Enum Translation Position

Enum translation has been removed from core aggregate artifacts. The current extension direction is:

- cap4k provides the addon SPI and template pipeline;
- only-engine or another addon can provide enum translation generation;
- business project A depends on both cap4k and the addon provider;
- project A overrides templates and conflict policies through normal cap4k mechanisms.

This should be documented as a general addon pattern, not as an enum-only special case.

## Business User Vs Addon Author

| Reader | Needs |
|---|---|
| Business project user | Add addon dependency, enable/configure addon behavior, inspect plan, override templates, set conflict policy |
| Addon author | Implement `ArtifactAddonProvider`, produce plan items, package templates/resources, register service loader file |
| cap4k framework author | Maintain SPI compatibility, plan normalization, renderer/exporter behavior |

The business authoring skill should cover the first row only. Addon-author guidance should be separate or clearly routed.

## Gaps For Public Authoring

| Gap | Why it matters |
|---|---|
| No complete business authoring workflow | Users need one path from modeling discussion to DDL/design, generation, implementation, tests, analysis, and audit |
| Tactical model not unified in docs | Users miss `Mediator`, factories, built-in repositories, UoW, lifecycle hooks, specifications, and domain services |
| Layer responsibility is easy to misstate | Query/client handlers are physically adapter-side while still serving application request contracts |
| Generated output ownership needs repetition | Users need to distinguish build-owned generated source, checked-in skeleton, and copied snapshot |
| Integration event mechanism is hard to infer | Users need framework flow diagrams, adapter endpoint rules, DB table requirements, and contract-sharing guidance |
| Testing guidance needs examples | Users need to know which tests teach behavior and which tests are implementation residue |
| Analysis workflow needs a ladder | Users need compile, IR input dirs, analysis plan, analysis generate, and flow/drawing-board review in order |

## Gaps For Generator Capability

| Gap | Current impact | Candidate issue direction |
|---|---|---|
| Design support for `integration_event` | External event contracts cannot be generated from design | Add design tag, generator, handler/subscriber skeleton, and drawing-board integration |
| Design support for `value_object` | Value objects stay DB/manual only | Add design vocabulary or clarify DB-first modeling boundary |
| Design support for `domain_service` | Domain services stay manual | Add design vocabulary/generator only if it avoids meaningless service shells |
| Lifecycle recognition deficiency | Intended lifecycle hook use can be missed in some cases | Track as framework defect, not template override task |
| Addon examples | SPI is available but business docs need a concrete pattern | Use enum translation or only-engine addon as reference once stable |
| Reference project stale DSL | `enumTranslation.set(false)` is invalid after core removal | Fix downstream project when aligned to latest cap4k |
| Integration event H2 DDL | HTTP-JPA integration needs framework tables for local examples | Provide minimal H2-compatible table subset |

## Future Skill Extraction

When rewriting the AI skill:

- keep `SKILL.md` as a concise router;
- put business-project rules under `rules/`;
- put step-by-step generation, implementation, verification, and analysis under `workflows/`;
- put this capability analysis under `references/` or use it only as source material;
- remove cap4k framework issue governance and skill-meta discussions from the business-project skill.

When rewriting public authoring docs:

- keep human readers in the decision/audit role;
- show DDD flow documents before code when a flow spans many classes;
- include concrete plan/generate/compile/test/analysis commands;
- explain where the human should audit generated output, not just how to run generators.
