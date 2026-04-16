# Cap4k Bootstrap Generated-Project Verification Hardening

Date: 2026-04-16
Status: Draft for review

## Summary

The first bounded bootstrap capability slice is now complete through:

- bootstrap DSL and config
- separate bootstrap plan and generate tasks
- one framework-owned preset: `ddd-multi-module`
- fixed bootstrap template ids
- bounded slot insertion
- representative legacy arch-template characterization
- bootstrap functional generate-level closure

That work proved that bootstrap can:

- plan files
- render files
- export a representative project skeleton

The next framework slice should raise the quality bar from:

- bootstrap file generation correctness

to:

- generated project usability correctness
- representative module compile viability
- override and slot behavior that still preserves generated project usability

This slice therefore introduces generated-project verification hardening for the bounded bootstrap capability already in place.

## Goals

- Verify that a representative generated bootstrap project is recognized as a real Gradle project after `cap4kBootstrap`.
- Verify that representative generated modules can execute minimal Kotlin compilation after bootstrap generation.
- Verify that fixed template overrides and bounded slot insertion still preserve generated-project usability.
- Add reusable functional harness support for running Gradle commands against the generated project subtree rather than only the fixture root.
- Tighten bootstrap quality gates without widening the bootstrap contract itself.

## Non-Goals

- Do not add a second bootstrap preset in this slice.
- Do not widen slot categories or restore arbitrary architecture-tree insertion.
- Do not revive old `archTemplate` JSON as a runtime DSL.
- Do not turn this slice into real-project integration work.
- Do not expand bootstrap verification into full application runtime verification.
- Do not require generated projects to pass full `build` or `check`.
- Do not reopen the completed design-generator quality line.

## Current Context

`cap4k` now has a bounded bootstrap capability with:

- `cap4kBootstrapPlan`
- `cap4kBootstrap`
- one preset: `ddd-multi-module`
- one preset renderer bundle: `ddd-default-bootstrap`
- bounded slots:
  - `root`
  - `build-logic`
  - `module-root:<role>`
  - `module-package:<role>`

The current proof level is already meaningful:

- bootstrap config validation
- bootstrap plan generation
- bootstrap file generation
- fixed template override support
- bounded slot insertion support

But it still leaves one important gap:

- the generated `projectName/` subtree is treated as generated files, not yet as a verified project artifact

That means the current suite can prove:

- correct output paths
- correct template ids
- correct slot attribution

without yet proving:

- generated `settings.gradle.kts` is usable by Gradle
- generated root and module build files form a coherent project
- representative generated Kotlin source under the bootstrap scaffold participates in module compilation
- override and slot behavior do not silently degrade generated-project usability

This is the same kind of gap that the design-generator line had before compile-level hardening.

## Design Decision

The next mainline slice should be:

- `bootstrap generated-project verification hardening`

This is preferred over immediately broadening bootstrap flexibility because:

- the current bottleneck is quality proof, not missing bootstrap features
- a stronger generated-project gate will make future bootstrap work safer
- it preserves the already accepted bounded bootstrap contract
- it prevents the project from expanding bootstrap semantics before generated outputs are adequately verified

## Verification Target

The verification target in this slice is:

- the generated project under `<fixture-root>/<projectName>/`

not:

- the fixture root project that invokes `cap4kBootstrap`

This distinction is critical.

The fixture root is only a bootstrap consumer.
The thing that must be verified is the generated project subtree, because that subtree is the product of bootstrap.

## Scope Structure

This slice should contain three linked verification layers.

### 1. Generated-Project Smoke

The first layer should verify that a generated bootstrap project is recognized as a working Gradle project skeleton.

Representative commands should include:

- `help`
- `tasks`

Those commands should run against the generated project directory, not the fixture root.

This layer should catch issues such as:

- invalid generated `settings.gradle.kts`
- broken root `build.gradle.kts`
- invalid included module declarations
- generated root/build-logic structure that Gradle cannot parse

### 2. Module-Level Compile Viability

The second layer should verify that representative generated modules can execute minimal Kotlin compilation.

The first slice should keep this bounded to:

- `:only-danmuku-domain:compileKotlin`
- `:only-danmuku-application:compileKotlin`
- `:only-danmuku-adapter:compileKotlin`

These commands do not need to prove the generated project is production-ready.
They need to prove that the generated bootstrap skeleton is coherent enough to act as a real project starting point.

This layer should catch issues such as:

- broken module build templates
- invalid module dependencies
- incorrect source-root or package scaffold output
- slot-inserted Kotlin files that break compilation

### 3. Override-And-Slot Integrated Project Check

The third layer should verify that the two key bootstrap extension points preserve generated-project usability:

- fixed template overrides
- bounded slot insertion

The verification should prove that a generated project still works when:

- a fixed bootstrap template is overridden
- representative files are inserted through bounded slots

This layer should stay representative, not exhaustive.

## Fixture Strategy

This slice should not use one giant all-purpose fixture.

It should use three representative fixture classes:

- `bootstrap-generated-project-smoke-sample`
- `bootstrap-generated-project-override-sample`
- `bootstrap-generated-project-invalid-sample`

### Smoke Sample

The smoke sample should:

- use the default `ddd-multi-module` preset
- configure one representative root slot file
- configure one representative `module-package:domain` slot file
- generate a project subtree such as `only-danmuku/`
- run:
  - `help`
  - `tasks`
  - representative module `compileKotlin` commands

### Override Sample

The override sample should:

- override at least one fixed bootstrap template
- still generate a usable project
- prove that the generated project continues to pass representative `help` and compile checks

### Invalid Sample

The invalid sample should:

- verify fail-fast bootstrap validation remains intact
- verify invalid slot configuration or invalid generated-project verification setup fails clearly

This sample does not need module compilation.

## Harness Design

This slice should add a thin generated-project runner helper to the functional suite.

That helper should:

1. run `cap4kBootstrap` in the fixture root
2. locate the generated `<projectName>/` subtree
3. execute Gradle commands against that generated subtree

This helper should remain intentionally small.

It should not become:

- a general bootstrap scenario engine
- a second bootstrap abstraction layer
- a generalized project-templating test framework

Its job is only to make generated-project verification repeatable.

## Allowed Follow-On Fixes Inside This Slice

This slice may include follow-on fixes only when they are directly exposed by generated-project verification.

Allowed examples:

- incorrect generated root or module build template content
- generated settings/module wiring mistakes
- generated package scaffold mistakes that break compilation
- bootstrap renderer or exporter behavior that prevents the generated project from being usable
- bounded slot output behavior that breaks generated-project usability

Not allowed as default follow-ons:

- new bootstrap presets
- broader slot flexibility
- support-track real-project bootstrap variants
- design-generator contract changes unrelated to bootstrap verification
- runtime behavior verification for generated applications

## Verification Commands

The representative verification commands in this slice should stay bounded.

Recommended commands:

- generated root:
  - `help`
  - `tasks`
- generated modules:
  - `:only-danmuku-domain:compileKotlin`
  - `:only-danmuku-application:compileKotlin`
  - `:only-danmuku-adapter:compileKotlin`

This slice should not require:

- full `build`
- full `check`
- Spring Boot startup
- integration tests

The point is generated-project viability, not end-to-end business correctness.

## Why This Slice Is Mainline Work

This slice belongs on the default mainline because:

- bootstrap is now a first-class framework capability
- its current quality bottleneck is verification depth, not feature breadth
- stronger generated-project validation protects future bootstrap changes without reopening boundaries

It is not support-track work because it does not target one real consumer repository.
It targets the framework-owned bootstrap contract itself.

## Relationship To Existing Bootstrap Decision

This slice does not change the accepted bootstrap contract:

- one preset
- fixed template ids
- bounded slots
- no arbitrary tree-node insertion
- old `archTemplate` remains migration input/reference material only

It strengthens proof of that contract.

## Validation Strategy

This slice should validate at four levels:

1. generated-project smoke
   - generated root project accepts `help` and `tasks`
2. module compile viability
   - representative generated modules execute `compileKotlin`
3. override preservation
   - fixed template overrides still yield a usable generated project
4. slot preservation
   - bounded slot insertion still yields a usable generated project

The invalid path should also remain covered:

- invalid slot configuration still fails fast
- generated-project verification should not mask bootstrap validation failures

## Success Criteria

This slice is successful when:

- generated bootstrap projects can be exercised as real Gradle projects, not just as generated file trees
- representative generated modules can execute minimal Kotlin compilation
- fixed bootstrap template overrides preserve generated-project usability
- bounded slot insertion preserves generated-project usability
- the verification harness clearly targets the generated project subtree under `<projectName>/`
- bootstrap verification is stronger without broadening the bootstrap contract

## Recommended Next Step

After this spec is approved, the implementation plan should stay focused on:

1. generated-project runner support in the functional harness
2. smoke fixture for generated-project Gradle recognition
3. representative module compile checks
4. override-and-slot generated-project checks
5. final bootstrap-focused and broader pipeline regression

That keeps the slice aligned with the existing bootstrap boundary while materially improving quality proof.
