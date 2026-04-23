# Cap4k Artifact Template Import Contract Unification Design

Date: 2026-04-23
Status: Draft for review

## Summary

This slice unifies the import contract for `cap4kGenerate` artifact templates.

Today the system still has two different artifact-template behaviors:

- `design/` templates can use a two-pass render model and call `use(...)`
- regular artifact templates, especially `aggregate/*`, still rely on direct `import ...` lines plus planner-provided import lists

That split is no longer desirable.

The next iteration should make the artifact-template contract explicit and uniform:

- for all non-bootstrap artifact templates, `use(...)` is available
- for all non-bootstrap default Kotlin templates, final import emission is written in one place only:
  - `{% for import in imports(imports) %}`
  - `import {{ import }}`
  - `{% endfor %}`
- direct `import ...` lines are removed from the default non-bootstrap Kotlin templates
- import output order is stable and deterministic

This slice is intentionally limited to `cap4kGenerate` artifact rendering.

It does **not** change bootstrap rendering.

It does **not** reopen the bootstrap template contract.

It does **not** require planner-heavy rewrites just to satisfy the template contract.

## Why This Slice Exists

The current state is internally understandable but externally inconsistent.

From a user point of view, the relevant question is simple:

- "If I override a template, can I use `use(...)` to declare imports?"

The current answer is awkward:

- yes for `design/` templates
- no for regular artifact templates

That answer reflects implementation history, not a good user contract.

The old `cap4k-plugin-codegen` already proved a different direction:

- templates can declare imports with `use(...)`
- imports can be emitted in one final place
- complex templates such as entity templates can still be maintained that way

This slice does **not** restore the old runtime wholesale.

It does, however, deliberately recover the old system's strongest template ergonomics idea:

- template-owned import declaration
- one final import output path

This is a contract-cleanup slice, not a novelty slice.

## Goals

### Primary Goal

Unify the non-bootstrap artifact-template import contract around one model:

- templates declare imports through `use(...)`
- planner/context may still contribute imports through `imports`
- final output is emitted only through `imports(imports)`

### Concrete Goals

- Make `use(...)` available to all `cap4kGenerate` artifact templates, not just `design/`
- Keep `imports(imports)` as the single final import emission path
- Remove direct `import ...` lines from the default non-bootstrap Kotlin templates
- Define one deterministic final import ordering rule
- Preserve fail-fast conflict behavior for simple-name collisions
- Preserve bootstrap isolation by not changing `PebbleBootstrapRenderer`
- Preserve existing planner-generated import lists where they already exist

## Non-Goals

This slice will not:

- change bootstrap template rendering
- add `use(...)` to bootstrap templates
- change bootstrap root managed-section behavior
- reintroduce wildcard-import support as part of the new contract
- make no-arg `imports()` part of the new contract
- require aggregate planners to be fully redesigned before templates can migrate
- require all import/annotation decisions to move out of templates
- promise perfect alignment with every developer's local IntelliJ import-layout customization
- widen flow or drawing-board templates into Kotlin-specific import contracts where imports are not relevant

## Hard Requirements

These requirements are intentionally repeated because this slice is contract-heavy and must not drift during implementation.

### Requirement 1: One Final Import Emission Shape

For all non-bootstrap default Kotlin artifact templates, the only final import emission shape must be:

```pebble
{% for import in imports(imports) %}
import {{ import }}
{% endfor %}
```

Equivalent whitespace variants are acceptable.

What is **not** acceptable after this slice:

- direct `import com.foo.Bar` lines in the default non-bootstrap Kotlin templates
- multiple separate import output loops in the same template
- template-local raw import string concatenation

### Requirement 2: `use(...)` Must Be Available Everywhere in Artifact Rendering

For all non-bootstrap artifact templates rendered by `PebbleArtifactRenderer`, `use(...)` must be valid.

This requirement applies regardless of template family:

- `design/*`
- `aggregate/*`
- other artifact templates rendered through the same artifact renderer

This requirement is about renderer capability, not just about current default template usage.

Even if a given default template does not need `use(...)` today, the renderer contract must allow an override template to use it.

### Requirement 3: Deterministic Import Ordering

The final emitted import list must be stable and deterministic.

This slice does **not** require perfect IntelliJ import-layout emulation.

It does require that:

- the same input always yields the same ordered import output
- duplicate imports are deduplicated
- output ordering is independent of incidental collection traversal order
- output ordering does not drift between template families

For this slice, deterministic sorting is sufficient.

### Requirement 4: Bootstrap Is Explicitly Out of Scope

This slice must not accidentally broaden into bootstrap.

The following remain outside scope:

- `PebbleBootstrapRenderer`
- bootstrap template helper semantics
- bootstrap root template import behavior

This requirement is repeated on purpose:

- this is a `cap4kGenerate` artifact-template contract slice
- not a renderer-everywhere unification slice

## Current State

Current artifact rendering is split.

### `design/` templates

`design/` templates currently benefit from:

- a two-pass render path
- `use(...)`
- explicit import collection merged with generator-provided imports

### regular artifact templates

Regular artifact templates currently do not get the same render protocol.

They still rely on combinations of:

- direct `import ...` lines
- planner-provided `imports`
- planner-provided specialized import lists such as `jpaImports`
- template-local conditional import branches

The most visible example is:

- [aggregate/entity.kt.peb](C:/Users/LD_moxeii/Documents/code/only-workspace/cap4k/cap4k-plugin-pipeline-renderer-pebble/src/main/resources/presets/ddd-default/aggregate/entity.kt.peb)

The core issue is not that `entity.kt.peb` is "wrong".

The core issue is that the system currently exposes two different template contracts.

That contract split is the real problem this slice fixes.

## Affected Surface

### Renderer Surface

Primary implementation surface:

- `cap4k-plugin-pipeline-renderer-pebble/src/main/kotlin/com/only4/cap4k/plugin/pipeline/renderer/pebble/PebbleArtifactRenderer.kt`
- `cap4k-plugin-pipeline-renderer-pebble/src/main/kotlin/com/only4/cap4k/plugin/pipeline/renderer/pebble/PipelinePebbleExtension.kt`

This is where the real contract change happens.

### Default Template Surface

At minimum, the following default aggregate Kotlin templates are in direct migration scope because they currently contain direct import lines or currently rely on older import patterns:

- `aggregate/entity.kt.peb`
- `aggregate/enum.kt.peb`
- `aggregate/enum_translation.kt.peb`
- `aggregate/factory.kt.peb`
- `aggregate/repository.kt.peb`
- `aggregate/schema.kt.peb`
- `aggregate/specification.kt.peb`
- `aggregate/unique_query.kt.peb`
- `aggregate/unique_query_handler.kt.peb`
- `aggregate/unique_validator.kt.peb`
- `aggregate/wrapper.kt.peb`

The `design/*` Kotlin templates are also in scope, but mainly for renderer-contract alignment, regression protection, and consistency checks. Most of them already follow the target direction.

### Test Surface

At minimum:

- `PebbleArtifactRendererTest`
- renderer/generator tests that lock aggregate template output
- relevant functional and compile-level pipeline tests that prove generated Kotlin remains valid

## Recommended Approach

### Option 1: Unify all non-bootstrap artifact rendering under one two-pass import-collection model

This option makes `use(...)` a general artifact-render capability.

The renderer always:

1. creates a render session
2. performs a collecting pass
3. merges explicit imports with context imports
4. performs the final rendering pass

Pros:

- matches the intended user contract
- removes the special-case dependency on `design/` prefix
- gives override authors one stable mental model
- lets default regular templates adopt `use(...)` cleanly

Cons:

- touches renderer internals
- triggers a broad template and test update

### Option 2: Keep renderer split and emulate `use(...)` in planners

This would avoid broad renderer changes by pushing more imports into context planning.

Pros:

- smaller renderer change

Cons:

- fails the user contract
- keeps family-specific behavior
- pushes template ergonomics back into Kotlin planners
- does not actually give users uniform override capabilities

### Option 3: Keep `design/` special-cased and add more helper-specific exceptions

Pros:

- minimal immediate code changes

Cons:

- deepens the inconsistency
- makes future override behavior harder to explain
- directly conflicts with the requirements of this slice

### Recommendation

Implement Option 1.

The key point is:

- this slice is not about physically merging two `PebbleEngine` fields
- it is about promoting the existing two-pass import-collection behavior from a `design/` special case into the default artifact-render protocol for `cap4kGenerate`

## Detailed Contract

### Contract A: `use(...)`

`use(...)` remains an explicit import declaration helper.

Rules:

- available in all non-bootstrap artifact templates
- accepts one string FQCN argument
- returns an empty string
- registers an explicit import during collecting pass
- still fails fast on invalid input
- still fails fast on simple-name conflicts

This slice does not relax `use(...)` into a broad legacy runtime.

It remains narrow and explicit.

### Contract B: `imports(imports)`

`imports(imports)` remains the only final emission path for imports.

After this slice, it must emit the merged result of:

- planner/context-provided imports
- template-owned imports declared through `use(...)`

This contract must be true for all non-bootstrap artifact templates, not just `design/`.

### Contract C: Default Template Shape

For default non-bootstrap Kotlin templates, import handling should follow this pattern:

1. top-of-template `use(...)` declarations, possibly under conditions
2. one final import block using `imports(imports)`

This requirement is intentionally repeated:

- top-of-template import declaration is allowed
- direct final `import ...` lines are not
- the single final import output path must be `imports(imports)`

### Contract D: Ordering

The final merged import list must be:

- deduplicated
- deterministic
- stable across runs
- stable across template families

This slice intentionally chooses a simpler and safer contract:

- deterministic ordering

This slice does **not** promise:

- byte-for-byte equivalence with every IntelliJ local optimization layout
- grouping by vendor
- grouping by standard-library buckets
- blank-line-separated import blocks

The recommended implementation rule is simple:

- normalize
- deduplicate
- sort deterministically

The spec does not require a specific comparator name, but the implementation must be explicit and test-locked.

### Contract E: No Planner Rewrite Requirement

This slice does not require planners to stop providing `imports`.

This point is important and repeated on purpose:

- planners may continue to provide `imports`
- aggregate planners may continue to provide `jpaImports` or similar transitional context inputs
- templates may continue to contain conditional logic that determines whether to call `use(...)`

What changes is the final import contract:

- all final import lines come from `imports(imports)`
- any fixed import that used to be written directly should now be declared through `use(...)`

This means the slice is a contract-unification slice, not a forced planner-architecture rewrite.

## Aggregate-Specific Interpretation

The aggregate family is the main migration surface.

This spec is deliberately explicit here so the requirement does not drift.

### What this slice does require for aggregate templates

- direct `import ...` lines in aggregate Kotlin templates are removed
- those imports are replaced by `use(...)`, including conditional `use(...)`
- final imports are emitted only through `imports(imports)`
- existing planner-provided import lists remain valid inputs

### What this slice does not require for aggregate templates

- moving all import decisions into planners
- introducing a new aggregate-wide planner architecture first
- replacing all existing booleans such as `hasVersionFields` or `dynamicInsert`
- redesigning entity/annotation planning before import-contract unification can land

### Example of intended migration style

Current aggregate entity templates may look like:

```pebble
{% if hasVersionFields -%}
import jakarta.persistence.Version
{% endif -%}
```

The intended migrated form is:

```pebble
{% if hasVersionFields -%}
{{ use("jakarta.persistence.Version") -}}
{% endif -%}
```

Final emission remains:

```pebble
{% for import in imports(imports) -%}
import {{ import }}
{% endfor -%}
```

This is the key migration pattern.

It is intentionally repeated because this is the most important template-level transformation in the slice.

## Design Family Interpretation

The design family already uses `use(...)`, but it currently receives special treatment through a dedicated render path.

After this slice:

- design templates still work
- their output does not regress
- `use(...)` remains valid
- but the capability is no longer tied to a `design/` special-case renderer contract

This slice should weaken the significance of `design/` as a renderer-behavior boundary.

It does not need to remove the prefix from template IDs.

It does need to remove the prefix as the reason why `use(...)` works.

## Old Codegen Relationship

The old `cap4k-plugin-codegen` already demonstrated that complex templates, including entity templates, can be maintained with:

- top-of-template `use(...)`
- one final `imports()` emission point

That historical precedent is relevant.

However, this slice does not blindly restore every old helper semantic.

The following old-runtime behaviors are intentionally not promoted into the new contract by default:

- wildcard import support as a preferred path
- no-arg `imports()` as the new standard contract
- renderer-wide legacy compatibility shims

This slice takes the old system's strongest template ergonomics idea, but keeps the new system's stricter contract boundaries.

## Implementation Strategy

### Step 1: Generalize artifact render protocol

`PebbleArtifactRenderer` should treat import collection as a general artifact-render concern, not a design-only concern.

Practical effect:

- the collecting pass becomes a standard artifact-render step
- the final rendering pass becomes a standard artifact-render step
- templates that never call `use(...)` should continue to render correctly with no semantic change

### Step 2: Keep helper semantics narrow

`use(...)` stays:

- explicit
- FQCN-based
- fail-fast on conflicts

This slice does not broaden helper semantics just because the usage surface broadens.

### Step 3: Migrate all default non-bootstrap Kotlin templates

Migration rule:

- replace direct import lines with `use(...)`
- keep one final `imports(imports)` loop

This must be done consistently, not opportunistically.

The migration must be one-pass and complete for the in-scope templates.

Partial migration is specifically not recommended because it would leave the codebase with two simultaneous contracts.

### Step 4: Lock deterministic sorting

Sorting must be made explicit in the merged import output path.

This slice does not allow the final order to depend on:

- incidental template declaration order
- set iteration behavior
- differences between design and regular render paths

### Step 5: Preserve bootstrap isolation

`PebbleBootstrapRenderer` must not be pulled into this refactor.

This requirement is repeated because scope creep here would be easy and costly.

## Testing Strategy

Testing must prove the contract, not just the implementation shape.

### Renderer Tests

`PebbleArtifactRendererTest` must be extended so that it proves:

1. regular templates can use `use(...)`
2. merged imports remain deduplicated
3. merged imports are deterministically sorted
4. invalid `use(...)` usage still fails fast
5. conflicting simple names still fail fast
6. a regular template using `use(...)` and `imports(imports)` behaves like the old design-only path used to

These are the tests that lock the renderer contract.

### Template Regression Tests

Default template regression coverage must include migrated aggregate templates.

At minimum, coverage should prove:

- entity template still renders correct imports under combinations of:
  - generated value
  - version
  - converter
  - dynamic insert/update
  - soft delete
  - relation imports
- repository/factory/specification/wrapper/unique templates still render valid import lists after migration

### Planner Tests

Planner tests should only change where current expected import lists or render-model behavior are directly affected.

This slice is not a planner-rearchitecture slice.

So planner tests should not be churned unless the actual planner output contract changes.

### Functional and Compile-Level Tests

At least the representative functional and compile-level tests that consume aggregate and design output should be rerun.

The purpose is not to retest every import permutation end to end.

The purpose is to ensure:

- migrated templates still compile
- no generator family regresses because of the renderer contract unification

## Risks

### Risk 1: Accidental bootstrap expansion

The renderer refactor could tempt implementation to also unify bootstrap.

This would be the wrong scope.

Mitigation:

- keep bootstrap renderer separate
- keep bootstrap tests and contracts unchanged
- explicitly reject bootstrap helper expansion in this slice

### Risk 2: Contract drift during template migration

Because many templates are involved, it would be easy to migrate some templates to the new pattern while leaving others half-migrated.

Mitigation:

- define the in-scope default template list up front
- complete migration in one pass
- add a review pass specifically checking for residual direct import lines

### Risk 3: Hidden ordering drift

If sorting is not centralized and explicit, import output may remain stable in most cases but drift in edge cases.

Mitigation:

- make final ordering explicit in one place
- lock deterministic ordering in renderer tests

### Risk 4: Turning this into an aggregate planner rewrite

The aggregate entity template is complex enough that implementation may be tempted to fold this slice into planner refactoring.

Mitigation:

- explicitly allow conditional `use(...)` in templates
- keep the contract target on final import emission, not on planner purity

## Acceptance Criteria

This slice is complete only when all of the following are true:

- `use(...)` works for all non-bootstrap artifact templates rendered by `PebbleArtifactRenderer`
- default non-bootstrap Kotlin templates no longer emit final import lines directly
- default non-bootstrap Kotlin templates emit final imports only through `imports(imports)`
- final import ordering is deterministic and test-locked
- design templates keep working without relying on a `design/`-only special-case renderer contract
- aggregate templates migrate without requiring a planner redesign
- bootstrap rendering is unchanged
- renderer and regression tests prove the contract end to end

## Final Scope Reminder

This is a large slice, but it is still one bounded slice.

It is:

- a `cap4kGenerate` artifact import-contract unification slice

It is not:

- a bootstrap slice
- a planner architecture rewrite
- an IntelliJ-perfect import-layout emulation slice
- a legacy runtime restoration slice

The repeated rule set for implementation is therefore:

- unify artifact import capability
- keep bootstrap isolated
- migrate all in-scope default templates in one pass
- use `use(...)` for declaration
- use `imports(imports)` for final output
- keep ordering deterministic
