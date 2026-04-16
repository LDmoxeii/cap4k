# Cap4k Bootstrap / Arch-Template Migration

Date: 2026-04-16
Status: Draft for review

## Summary

The design-generator quality mainline is now complete through compile-level / integrated verification hardening.

The next explicit framework slice should be:

- `bootstrap / arch-template migration`

This slice should not widen the design-generator contract and should not revive the old `archTemplate` JSON as a new public runtime DSL.

Instead, it should introduce bootstrap as a separate framework capability with:

- a framework-owned bootstrap preset
- fixed bootstrap template ids
- bounded slot-based extension points
- an explicit migration mapping from old `archTemplate` assets into the new contract

The first bootstrap slice should define the contract and prove one representative preset path, not attempt full old-JSON parity in one step.

## Goals

- Introduce bootstrap as an explicit framework capability rather than hiding it inside existing design or aggregate generators.
- Define a bounded public bootstrap contract for project skeleton generation.
- Keep the accepted bootstrap decision intact:
  - slot-based extension
  - arbitrary file counts inside bounded slots
  - no arbitrary insertion into any architecture-tree node
- Define how the old `archTemplate` asset is split into:
  - framework-owned preset structure
  - fixed bootstrap templates
  - bounded slot insertion
  - non-bootstrap generator routing that remains outside bootstrap
- Prove one representative `ddd-multi-module` bootstrap path as the first migration target.

## Non-Goals

- Do not revive old `archTemplate` JSON as the new public runtime contract.
- Do not turn bootstrap into a generic user-programmable tree DSL.
- Do not mix bootstrap into `cap4kGenerate` or existing design-family generators.
- Do not reopen the completed design-generator quality line.
- Do not attempt full old `archTemplate` parity in the first slice.
- Do not move support-track or real-project integration work into bootstrap by default.
- Do not redefine the existing slot-based bootstrap decision.

## Current Context

`cap4k` currently has two different kinds of generation logic inherited from the old system:

1. business artifact generation
   - command
   - query
   - query-handler
   - client
   - client-handler
   - validator
   - api_payload
   - domain_event
   - domain_event_handler
2. project skeleton generation
   - old `GenArchTask`
   - old `archTemplate` JSON trees

The first category has already been migrated into bounded pipeline-owned generators.
The second category has not.

The accepted repository direction already says:

- bootstrap remains a separate capability
- public bootstrap contract uses slot-based extension
- old `archTemplate` JSON is migration input or reference material, not the future public runtime contract

This slice therefore does not need to decide whether bootstrap is separate.
That decision is already made.
It needs to define how that separate capability should look.

## Why The Old Arch Template Needs Reinterpretation

The old `cap4k-ddd-codegen-template-multi-nested.json` mixes two different responsibilities:

1. project skeleton structure
   - root files
   - module directories
   - module build files
   - package scaffold directories
2. generator-side routing
   - `tag`
   - `pattern`
   - template selection per design family or naming variant

Those two responsibilities should not stay fused.

Under the new architecture:

- project skeleton structure belongs to bootstrap
- business artifact routing belongs to bounded generators

This is the most important migration boundary in the entire slice.

If this boundary is not made explicit, bootstrap migration will silently pull already-migrated generator routing back into a new open DSL and undo the mainline cleanup that already happened.

## Design Decision

Bootstrap should be introduced as a separate capability with its own public block and its own tasks.

Recommended public surface:

```kotlin
cap4k {
    bootstrap {
        enabled.set(true)
        preset.set("ddd-multi-module")
    }
}
```

Recommended execution tasks:

- `cap4kBootstrapPlan`
- `cap4kBootstrap`

This is preferred over embedding bootstrap inside `cap4kGenerate` because:

- bootstrap does not consume the same canonical artifact model as business generators
- bootstrap plans a project skeleton, not a set of design or aggregate artifacts
- keeping it separate preserves framework boundary clarity
- future bootstrap validation can evolve independently of generator quality gates

## Public Bootstrap Contract

The first bootstrap contract should contain four bounded areas:

1. framework-owned preset selection
2. project identity and fixed module layout
3. fixed bootstrap template ids with overrides
4. bounded slot-based extension

### 1. Preset

The first slice should support exactly one preset:

- `ddd-multi-module`

This preset owns the default project skeleton shape.

Users do not provide a new tree DSL.
They select a framework-owned preset and configure bounded parameters around it.

### 2. Project Identity And Fixed Module Layout

The first slice should expose only stable project parameters, not free-form tree structure:

```kotlin
cap4k {
    bootstrap {
        enabled.set(true)
        preset.set("ddd-multi-module")

        projectName.set("only-danmuku")
        basePackage.set("edu.only4.danmuku")

        modules {
            domainModuleName.set("only-danmuku-domain")
            applicationModuleName.set("only-danmuku-application")
            adapterModuleName.set("only-danmuku-adapter")
        }
    }
}
```

The first slice keeps module roles fixed:

- `domain`
- `application`
- `adapter`

Module names are configurable.
Module-role creation is not.

This is intentional.
The first slice should prove bounded bootstrap migration, not reopen the entire architecture-tree grammar.

### 3. Fixed Bootstrap Templates And Overrides

Bootstrap should reuse the same overall override philosophy already used elsewhere in the pipeline:

```kotlin
cap4k {
    bootstrap {
        templates {
            preset.set("ddd-default-bootstrap")
            overrideDirs.from("codegen/bootstrap-templates")
        }
    }
}
```

The key rule is:

- template ids are fixed and framework-owned
- users may override content
- users may not define a new routing language

Representative fixed bootstrap template ids in the first slice should include:

- `bootstrap/root/settings.gradle.kts.peb`
- `bootstrap/root/build.gradle.kts.peb`
- `bootstrap/module/domain-build.gradle.kts.peb`
- `bootstrap/module/application-build.gradle.kts.peb`
- `bootstrap/module/adapter-build.gradle.kts.peb`
- representative package scaffold templates if needed by the preset

### 4. Bounded Slots

The first slice should implement the accepted slot-based extension decision directly.

Users may add arbitrary numbers of files, but only inside framework-defined slot categories.

Recommended first-slice slot set:

- `root`
- `build-logic`
- `module-root:<role>`
- `module-package:<role>`

Meaning:

- `root`
  - additional files at repository root
- `build-logic`
  - build logic or convention-support files
- `module-root:<role>`
  - files under a module root directory
- `module-package:<role>`
  - files under that module's base package

Representative public shape:

```kotlin
cap4k {
    bootstrap {
        slots {
            root.from("codegen/bootstrap-slots/root")
            buildLogic.from("codegen/bootstrap-slots/build-logic")
            moduleRoot("domain").from("codegen/bootstrap-slots/domain-root")
            moduleRoot("application").from("codegen/bootstrap-slots/application-root")
            moduleRoot("adapter").from("codegen/bootstrap-slots/adapter-root")
            modulePackage("domain").from("codegen/bootstrap-slots/domain-package")
            modulePackage("application").from("codegen/bootstrap-slots/application-package")
            modulePackage("adapter").from("codegen/bootstrap-slots/adapter-package")
        }
    }
}
```

This preserves the accepted bootstrap promise:

- arbitrary file counts are allowed
- arbitrary tree-node insertion is not

## Conflict Policy

Bootstrap should reuse the existing conflict-policy concept, but the first slice should keep it global rather than per-slot.

Recommended bootstrap-level shape:

```kotlin
cap4k {
    bootstrap {
        conflictPolicy.set(ConflictPolicy.FAIL)
    }
}
```

Recommended default:

- `FAIL`

Rationale:

- bootstrap conflicts are more structural than design-artifact conflicts
- a skipped root file or module build file can silently create half-generated projects
- fail-fast behavior is safer for the first slice

## Legacy Arch-Template Mapping

The old `archTemplate` should be treated as migration input, not as the new contract itself.

The migration logic should explicitly separate old template content into four categories.

### Category A: Structural Nodes

Old examples:

- `root`
- nested `dir`
- module directory trees
- package scaffold directory trees

These map into:

- the `ddd-multi-module` preset structure

They should no longer be user-authored runtime JSON nodes.
They become framework-owned preset layout.

### Category B: Static Template Files

Old examples:

- root `settings.gradle.kts`
- root `build.gradle.kts`
- module `build.gradle.kts`
- fixed configuration files
- fixed readme or script files

These map into:

- fixed bootstrap template ids

They remain overrideable through bounded template override directories, but their routing and destination remain framework-owned.

### Category C: Extensible Placement Zones

Old examples:

- adding additional files near root
- adding files under a given module subtree
- adding files under a module package subtree

These map into:

- bounded slots

This is the new contract replacement for the old open tree-insertion flexibility.

### Category D: Generator-Owned Template Routing

Old examples:

- `tag = query`
- `pattern = List/Page`
- `tag = query_handler`
- `tag = client`
- `tag = validator`
- `tag = api_payload`
- `tag = domain_event`

These do **not** map into bootstrap.

They are already owned by bounded pipeline generators and should remain there.

This means:

- bootstrap does not perform design-family routing
- bootstrap does not interpret naming-variant pattern rules
- bootstrap does not choose query/query-list/query-page style templates

Those concerns stay in generator-owned logic.

## Representative Mapping Table

The first slice should document the migration mapping explicitly.

| Old arch-template concern | New contract target | Notes |
|---|---|---|
| root/module/package directory tree | preset-owned layout | not user-authored runtime tree |
| root/module fixed files | fixed bootstrap template ids | overrideable, not reroutable |
| add more files near root/module/package | bounded slots | slot categories are fixed |
| `tag` / `pattern` template routing | stays in bounded generators | not bootstrap |
| old `archTemplate` JSON file itself | migration input / reference asset | not public runtime DSL |

## Minimum First-Slice Closure

The first bootstrap slice should stop at one closed, representative capability set.

It should include:

1. separate bootstrap DSL block
2. separate bootstrap tasks
3. one preset:
   - `ddd-multi-module`
4. configurable project identity:
   - `projectName`
   - `basePackage`
   - `domain/application/adapter` module names
5. fixed bootstrap templates for:
   - root `settings.gradle.kts`
   - root `build.gradle.kts`
   - three module build files
   - representative package scaffold
6. bounded slots:
   - `root`
   - `build-logic`
   - `module-root:<role>`
   - `module-package:<role>`
7. one representative mapping example from the old `cap4k-ddd-codegen-template-multi-nested.json`

That is enough to prove:

- the new bootstrap contract is real
- old project skeleton intent can be carried into it
- the old open JSON tree does not need to survive as a public runtime DSL

## Explicitly Deferred From The First Slice

The first bootstrap slice should explicitly defer:

- arbitrary architecture-tree insertion
- user-defined module roles
- multiple bootstrap presets
- per-slot conflict policy
- full old `archTemplate` node-by-node parity
- generator-family routing inside bootstrap
- support-track project-specific bootstrap variants
- integration of bootstrap into `cap4kGenerate`

These items are deferred because they would either:

- reopen already-closed framework boundaries
- make the first slice too large to validate clearly
- or collapse bootstrap back into an open DSL migration

## Representative Migration Example

The first slice should use the old `cap4k-ddd-codegen-template-multi-nested.json` as a migration characterization example.

The example should explicitly show:

1. old module tree
   - `{{ artifactId }}-domain`
   - `{{ artifactId }}-application`
   - `{{ artifactId }}-adapter`
2. old root and module build files
3. old package scaffold placement
4. old design-family routing nodes that are now excluded from bootstrap ownership

The example is not there to preserve old JSON structure.
It is there to prove that the old asset can be decomposed into the new contract.

## Relationship To Exploratory Parity Docs

The following documents are useful input for this slice:

- [Pipeline Full-Replacement Gap Analysis](../../design/pipeline-migration/feasibility-analysis.md)
- [Pipeline Full-Replacement - Exploratory Backlog](../../design/pipeline-migration/issues.md)

Their role here is limited:

- they help characterize the old `GenArch` / scaffolding problem
- they help confirm that bootstrap parity is a real separate gap

They do **not** define the active architecture for this slice.

Current active bootstrap direction remains governed by:

- [AGENTS.md](../../../AGENTS.md)
- [mainline-roadmap.md](../mainline-roadmap.md)

## Validation Strategy

The first bootstrap slice should prove itself at four levels:

1. contract/config validation
   - invalid preset names fail clearly
   - missing required project identity fields fail clearly
   - invalid slot declarations fail clearly
2. bootstrap plan validation
   - `cap4kBootstrapPlan` lists planned files and slot insertions
   - output is attributable by preset and slot
3. bootstrap generation validation
   - `cap4kBootstrap` creates a representative multi-module skeleton
   - generated root/module files land in the expected places
4. bounded slot validation
   - representative files added through `root`, `module-root`, and `module-package` land in the correct destinations
   - no arbitrary node insertion path exists

The first slice does not need to prove every future bootstrap use case.
It needs to prove the new contract and the preset/slot model.

## Success Criteria

This slice is successful when:

- bootstrap exists as a separate capability with its own DSL block and tasks
- one framework-owned preset (`ddd-multi-module`) can generate a representative project skeleton
- fixed bootstrap template ids are overrideable without introducing a new routing DSL
- bounded slots allow arbitrary file counts inside fixed slot categories
- the old `archTemplate` asset is explicitly mapped into:
  - preset structure
  - fixed templates
  - bounded slots
  - excluded generator-owned routing
- the new contract does not restore arbitrary architecture-tree insertion

## Recommended Next Step

After this spec is approved, the implementation plan should stay focused on:

1. bootstrap DSL and config model
2. bootstrap plan / bootstrap task
3. one `ddd-multi-module` preset
4. fixed template ids for root/module skeleton
5. bounded slot insertion
6. representative validation of old-asset mapping

That keeps the first bootstrap slice closed, testable, and consistent with the already-accepted boundary decisions.
