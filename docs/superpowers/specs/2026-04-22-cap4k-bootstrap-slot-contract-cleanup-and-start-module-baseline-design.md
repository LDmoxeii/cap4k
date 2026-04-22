# Cap4k Bootstrap Slot Contract Cleanup And Start-Module Baseline

Date: 2026-04-22
Status: Draft for review

## Summary

Bootstrap should stop exposing a misleading package-slot contract and should stop shipping default marker files as fake skeleton proof.
At the same time, the default bootstrap module set should grow from three roles to four:

- `domain`
- `application`
- `adapter`
- `start`

The new `start` role is part of the default bootstrap structure, not an opt-in side capability.
Its baseline should be runnable and Spring-Boot-oriented, but it should stay minimal:

- generate a `start` module build script
- generate a `StartApplication.kt`
- wire the `start` module to depend on `adapter`, `application`, and `domain`

This slice does **not** make the default preset inherit `only-danmuku` private runtime preferences such as Jimmer, QueryDSL, project-specific properties classes, datasource details, or project-owned migration/resources.
Those richer runtime details should be reintroduced by verification projects through template overrides and slots.

## Goals

- Add `start` as the fourth default bootstrap module role.
- Make the default bootstrap skeleton runnable through a minimal Spring Boot host module.
- Correct the public meaning of `modulePackage(...)` so the name matches where generated files actually belong.
- Add an explicit resource slot surface for module resources.
- Remove default bootstrap marker source files from the preset.
- Keep bootstrap extensible enough that `only-danmaku-next` can recreate old template-driven runtime details without turning those details into framework defaults.

## Non-Goals

- Do not add multi-target slot routing inside a single source tree.
- Do not add raw-copy vs template-render execution modes for slots in this slice.
- Do not add a second package-slot concept such as `moduleBasePackage`.
- Do not import Jimmer, QueryDSL, Druid, project-specific properties classes, or private `only-*` config into the framework default preset.
- Do not turn bootstrap into a generic architecture-tree router.

## Problem Statement

### 1. `modulePackage(...)` is not an honest public name

The current public name suggests "the primary package area for this module", but the current implementation actually means:

- `{module}/src/main/kotlin/{basePackage}/...`

That is too weak and too ambiguous.
For most real generated artifacts, the framework already uses role-shaped package roots:

- domain artifacts under `.../{basePackage}/domain/...`
- application artifacts under `.../{basePackage}/application/...`
- adapter artifacts under `.../{basePackage}/adapter/...`

The slot name should align with that dominant contract instead of forcing users to remember a hidden mismatch.

### 2. Resource insertion is under-modeled

Today users can force resources through `moduleRoot(...)`, but that is a low-level filesystem escape hatch.
Common resource intent such as:

- `application.yml`
- `logback.xml`
- `db.migration/...`

deserves an explicit module-resource slot rather than a "manually remember `src/main/resources`" contract.

### 3. The default bootstrap skeleton still lacks a first-class host module

The current default preset produces `domain`, `application`, and `adapter`, but no dedicated start/host module.
That leaves bootstrap with an awkward default:

- generated structure is buildable
- generated structure is not a proper application host baseline

For actual project evolution, the host role should be explicit and structural.

### 4. Default marker files are framework noise

`DomainBootstrapMarker`, `ApplicationBootstrapMarker`, and `AdapterBootstrapMarker` are not runtime requirements.
They act mainly as visible placeholders.
That is not a good reason to impose them on every generated project.

Verification projects may still create proof files through project-owned slots, but the framework default preset should not do so.

## Design Decisions

### 1. Default bootstrap module roles become four fixed roles

The framework-owned bootstrap role set becomes:

- `domain`
- `application`
- `adapter`
- `start`

These roles are fixed by the preset contract.
Module names remain user-configurable through `bootstrap.modules`, including:

- `domainModuleName`
- `applicationModuleName`
- `adapterModuleName`
- `startModuleName`

This preserves symmetry across all default roles.
The managed root templates must therefore also include the configured `start` module in `settings.gradle.kts`.

### 2. The default preset includes a minimal runnable `start` baseline

The default `ddd-multi-module` bootstrap preset should additionally generate:

- `{startModuleName}/build.gradle.kts`
- `{startModuleName}/src/main/kotlin/{basePackage}/StartApplication.kt`

The generated `start` build should:

- apply the Boot plugin
- depend on `adapter`, `application`, and `domain`
- include only the minimal dependencies needed for a runnable Spring Boot host baseline

The generated `StartApplication.kt` should include:

- `@SpringBootApplication`
- `@EnableScheduling`
- `@EnableJpaRepositories`
- `@EntityScan`
- `main()`

This is intentionally a Spring Boot + JPA host baseline.
It is **not** `only-danmuku` full runtime parity.

Project-owned runtime details remain outside the framework default preset, including:

- `application.yml`
- `logback.xml`
- `db.migration/...`
- project-specific `@EnableConfigurationProperties`
- Jimmer / QueryDSL / Blaze / Druid specifics
- `only-*` private config blocks

Those details should be added by verification projects through template overrides and slots.

### 3. `modulePackage(...)` is redefined as the primary package root of the role

This slice keeps the public DSL name `modulePackage(...)`, but changes its meaning to match real framework package usage.

The mapping becomes:

| Role | Root |
| --- | --- |
| `domain` | `{domainModule}/src/main/kotlin/{basePackage}/domain/` |
| `application` | `{applicationModule}/src/main/kotlin/{basePackage}/application/` |
| `adapter` | `{adapterModule}/src/main/kotlin/{basePackage}/adapter/` |
| `start` | `{startModule}/src/main/kotlin/{basePackage}/` |

This means:

- `modulePackage("adapter")` is the normal high-level slot for adapter Kotlin code
- `modulePackage("start")` is the normal high-level slot for host-side Kotlin code

This slice intentionally does **not** add `moduleBasePackage(...)`.
That broader base-package-root contract does not currently justify its own first-class DSL.
Projects that need atypical placement can still use `moduleRoot(...)` as the low-level escape hatch.

### 4. Add `moduleResources(...)`

Add a new bounded slot kind:

- `moduleResources("domain" | "application" | "adapter" | "start")`

Its output roots are:

- `{module}/src/main/resources/`

This is the explicit high-level slot for module resource trees.
It exists so common runtime assets do not have to be modeled through `moduleRoot(...)`.

### 5. Keep `moduleRoot(...)` as the low-level escape hatch

`moduleRoot(...)` remains valid and should support all four roles.
Its root remains:

- `{module}/`

This is the proper place for uncommon layouts that do not deserve dedicated slot DSL.
It is also the fallback for project-specific structures that should not become framework contract.

### 6. Remove default bootstrap marker files

The default preset should stop generating:

- `DomainBootstrapMarker`
- `ApplicationBootstrapMarker`
- `AdapterBootstrapMarker`

No replacement framework-owned proof files are introduced.
If a verification project wants visible proof artifacts, it should generate them through project-owned slots.

### 7. Template rendering remains sufficient and intentionally narrow

Bootstrap fixed templates and slot files continue to support Pebble rendering with the existing bounded bootstrap context, including:

- `projectName`
- `basePackage`
- `basePackagePath`
- module names
- conflict policy
- mode / previewDir

This is sufficient for:

- minimal framework default start baseline
- verification-project-owned recreation of richer old template behavior

This slice does not widen bootstrap context into a full old codegen variable registry.

## Public Contract

Recommended shape after this slice:

```kotlin
cap4k {
    bootstrap {
        enabled.set(true)
        preset.set("ddd-multi-module")
        projectName.set("only-danmaku")
        basePackage.set("edu.only4.danmaku")

        modules {
            domainModuleName.set("only-danmaku-domain")
            applicationModuleName.set("only-danmaku-application")
            adapterModuleName.set("only-danmaku-adapter")
            startModuleName.set("only-danmaku-start")
        }

        slots {
            root.from("codegen/bootstrap-slots/root")
            buildLogic.from("codegen/bootstrap-slots/build-logic")
            moduleRoot("start").from("codegen/bootstrap-slots/start-root")
            modulePackage("adapter").from("codegen/bootstrap-slots/adapter-package")
            modulePackage("start").from("codegen/bootstrap-slots/start-package")
            moduleResources("start").from("codegen/bootstrap-slots/start-resources")
        }
    }
}
```

## Verification-Project Boundary

`only-danmaku-next` should be free to recreate old `only-danmuku` runtime behavior through:

- bootstrap template overrides
- `root` / `buildLogic` / `moduleRoot` / `modulePackage` / `moduleResources` slots

That project-level recreation is valid and desirable.
But it must not silently redefine the framework default preset.

This slice therefore separates two responsibilities:

- framework preset: minimal honest baseline
- verification project: full old-project flattening where needed

## Validation Strategy

### 1. API And Config Tests

Validate:

- `BootstrapModulesConfig` includes `startModuleName`
- slot role validation accepts `start`
- slot binding/config translation includes `MODULE_RESOURCES`

### 2. Planner Unit Tests

Validate:

- default preset plans the configured `startModuleName/build.gradle.kts`
- default preset plans `StartApplication.kt`
- managed `settings.gradle.kts` output includes the configured `startModuleName`
- `modulePackage("domain" | "application" | "adapter" | "start")` roots map as specified
- `moduleResources(...)` maps to `src/main/resources`
- default marker outputs disappear from the plan

### 3. Functional Bootstrap Tests

Validate:

- generated bootstrap projects include four modules
- generated `start` module compiles and participates in `build`
- rerun preserves the updated slot DSL
- `moduleResources("start")` can materialize resource files correctly

### 4. Documentation And Matrix Updates

Update:

- bootstrap README DSL and slot tables
- managed-root examples where module lists are shown
- capability matrix rows for:
  - `bootstrap.slot_bundle`
  - `bootstrap.start_module_baseline`

## Success Criteria

This slice is complete only when all of the following are true:

- the default bootstrap module set is `domain/application/adapter/start`
- `startModuleName` is a first-class bootstrap module setting
- default bootstrap output includes a minimal runnable `start` host baseline
- `modulePackage(...)` now means the primary package root of the role, not a generic base-package root
- `moduleResources(...)` exists and works for all four roles
- default bootstrap markers are gone
- verification projects can still recreate richer old runtime structure through overrides and slots without those details becoming framework defaults

## Recommended Follow-On Boundary

After this slice, acceptable follow-on work includes:

- updating `only-danmaku-next` to consume the cleaned slot contract
- using verification-project-owned templates and slots to recreate old `only-danmuku` host details

It should not automatically trigger:

- raw copy mode
- multi-target slot routing
- generic free-form destination DSL
- automatic inheritance of project-private runtime stacks into framework preset defaults
