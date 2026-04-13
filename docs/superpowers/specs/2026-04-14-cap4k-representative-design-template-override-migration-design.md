# Cap4k Representative Design Template / Override Migration Design

Date: 2026-04-14
Status: Draft for review

## Summary

The next original-mainline slice is no longer about adding helper surface.

That work is already complete:

- `type()`
- `imports()`
- `use()`
- Kotlin-ready `defaultValue`
- migration-friendly helper adoption in command and query override fixtures

The next quality step is to prove that a representative set of old design-template patterns can land on the new helper-first contract without reopening the old template-selection DSL.

This slice focuses on four representative templates from the old design path:

- `command.kt.peb`
- `query.kt.peb`
- `query_list.kt.peb`
- `query_page.kt.peb`

The key design decision is:

- preserve the old capability effect of query template variation
- do not preserve the old public mechanism of regex or pattern-based template selection

Instead, the new contract should use bounded planner-owned template variants.

For this slice, the design-generator should remain framework-owned and conservative:

- commands still map to `design/command.kt.peb`
- default queries map to `design/query.kt.peb`
- list queries map to `design/query_list.kt.peb`
- page queries map to `design/query_page.kt.peb`

The selection logic belongs in Kotlin planner code, not in user-configurable template rules.

## Why This Slice

The old `cap4k-plugin-codegen` design path did not just vary template content.

It also varied template choice for queries by class-name pattern:

- normal query -> `templates/query.kt.peb`
- list query -> `templates/query_list.kt.peb`
- page query -> `templates/query_page.kt.peb`

That behavior exists today in the old generator implementation:

- [QueryGenerator.kt](../../../cap4k-plugin-codegen/src/main/kotlin/com/only4/cap4k/plugin/codegen/generators/design/QueryGenerator.kt)

The current pipeline design generator does not yet model that distinction.

Today it only chooses between:

- `design/command.kt.peb`
- `design/query.kt.peb`

That behavior exists today in:

- [DesignArtifactPlanner.kt](../../../cap4k-plugin-pipeline-generator-design/src/main/kotlin/com/only4/cap4k/plugin/pipeline/generator/design/DesignArtifactPlanner.kt)

If the mainline stops at the current helper-adoption slice, then the framework can say:

- helper-first templates exist
- override directories work

but it still cannot say:

- representative old design template families have a stable migration landing zone

This slice closes that gap.

## Goals

- Migrate a representative old design-template family onto the helper-first pipeline contract
- Preserve realistic migration value for the old query/query-list/query-page template split
- Keep template selection owned by Kotlin planner logic rather than user template DSL
- Keep override template customization compatible with the current `overrideDirs` contract
- Add functional and renderer-level fixtures that prove representative migration patterns remain stable
- Define a conservative rule for how query variants are selected in the new pipeline

## Non-Goals

This slice will not:

- reopen generator-core or pipeline architecture
- restore user-configurable regex or pattern-based template selection
- add arbitrary template-routing DSL to the new framework contract
- widen `use()` beyond explicit import assistance inside design templates
- add sibling design-entry type support
- implement bootstrap or arch-template migration
- redesign non-design generators such as handlers, validators, or clients
- add a fully explicit query-variant field to the canonical model in this slice

## Current State

### Old Generator

The old query generator chooses templates by regex pattern on the generated query class name:

- default query: `^(?!.*(List|list|Page|page)).*$`
- list query: `^.*(List|list).*$`
- page query: `^.*(Page|page).*$`

That routing is currently encoded in:

- [QueryGenerator.kt](../../../cap4k-plugin-codegen/src/main/kotlin/com/only4/cap4k/plugin/codegen/generators/design/QueryGenerator.kt:59)

The old template family itself also differs semantically:

- default query uses `RequestParam<Response>`
- list query uses `ListQueryParam<Response>`
- page query uses `PageQueryParam<Response>()`

Those templates currently live at:

- [command.kt.peb](../../../cap4k-plugin-codegen/src/main/resources/templates/command.kt.peb)
- [query.kt.peb](../../../cap4k-plugin-codegen/src/main/resources/templates/query.kt.peb)
- [query_list.kt.peb](../../../cap4k-plugin-codegen/src/main/resources/templates/query_list.kt.peb)
- [query_page.kt.peb](../../../cap4k-plugin-codegen/src/main/resources/templates/query_page.kt.peb)

### Current Pipeline

The pipeline planner currently exposes only two design artifact template ids:

- `design/command.kt.peb`
- `design/query.kt.peb`

That routing currently lives in:

- [DesignArtifactPlanner.kt](../../../cap4k-plugin-pipeline-generator-design/src/main/kotlin/com/only4/cap4k/plugin/pipeline/generator/design/DesignArtifactPlanner.kt:25)

The canonical pipeline request model currently only distinguishes:

- `RequestKind.COMMAND`
- `RequestKind.QUERY`

That model currently lives in:

- [PipelineModels.kt](../../../cap4k-plugin-pipeline-api/src/main/kotlin/com/only4/cap4k/plugin/pipeline/api/PipelineModels.kt:182)

This means the current pipeline cannot yet express representative migration of:

- list-query template structure
- page-query template structure
- query-family override directories that expect distinct template files

## Scope Decision

Three approaches were considered.

### Option 1: Keep a single pipeline query template and prove migration only through examples

Treat `query_list.kt.peb` and `query_page.kt.peb` as historical reference only.
Keep pipeline output on a single `design/query.kt.peb` template and encode any variation inside one larger template.

Pros:

- smallest implementation surface
- no planner routing change

Cons:

- weak migration fidelity
- old override template families do not have a direct landing zone
- pushes real migration complexity into giant conditional templates

### Option 2: Add bounded planner-owned query template variants with conservative class-name inference

Add framework-owned query template variants in the pipeline:

- `design/query.kt.peb`
- `design/query_list.kt.peb`
- `design/query_page.kt.peb`

Select them in Kotlin planner logic using conservative rules derived from `typeName`.

Pros:

- preserves the migration value of the old query family
- does not restore pattern DSL as user contract
- keeps template selection inside framework-owned generator logic
- keeps override templates simple because each variant gets its own file

Cons:

- expands planner logic slightly
- still relies on internal naming inference until a later explicit model exists

### Option 3: Add an explicit query variant to the canonical model now

Add a new canonical semantic such as `QueryVariant.DEFAULT | LIST | PAGE`, then route planner output from that explicit model.

Pros:

- most explicit long-term model
- avoids inference

Cons:

- widens canonical/source model scope in a slice that is supposed to focus on representative migration
- requires extra upstream source and compatibility design
- too large for this mainline step

### Recommendation

Implement Option 2.

This is the smallest slice that preserves practical migration value without reopening framework boundaries.

## Contract Decision

The new public contract for this slice is:

- project users may override a bounded set of framework-owned design template ids
- project users may not define their own template-selection rules
- query template variation is framework-owned

The bounded design template family should become:

- `design/command.kt.peb`
- `design/query.kt.peb`
- `design/query_list.kt.peb`
- `design/query_page.kt.peb`

The public customization point remains:

- `templates { overrideDirs.from(...) }`

The public customization point does not become:

- regex-based template matching
- file-pattern routing rules
- arbitrary user-defined template dispatch logic

## Query Variant Selection

This slice should add a conservative internal query-variant selection rule.

### Rule

Planner logic should classify query templates as follows:

- if `typeName` ends with `PageQry`, use `design/query_page.kt.peb`
- else if `typeName` ends with `ListQry`, use `design/query_list.kt.peb`
- else use `design/query.kt.peb`

### Why suffix-based and not old broad regex

The old system matched `List` and `Page` anywhere in the class name.

That behavior is too loose for the new contract because it:

- creates more ambiguity
- is harder to explain as stable framework behavior
- looks like a hidden return of template pattern DSL

Suffix-based routing is narrower and easier to defend.

It preserves the common old naming shapes:

- `GetUserListQry`
- `GetUserPageQry`

without turning class-name matching into a public language feature.

### Ownership

This selection rule belongs in Kotlin planner code.

Templates should not need to know how they were selected.

Override templates should simply implement the contract of the template id they are overriding.

## Template Migration Target

The representative migration target for this slice is not a byte-for-byte copy of old templates.

It is the helper-first equivalent of the old template family.

Each template in the new family should:

- use `type()` for field types
- use `imports()` for emitted imports
- use `use()` only for explicit framework imports that belong in the template body
- use `defaultValue` from the render model directly
- avoid depending on low-level render-context details beyond the stable contract

The expected semantic landing should be:

### `design/command.kt.peb`

- helper-first command template
- continues current command semantics

### `design/query.kt.peb`

- helper-first default query template
- request inherits `RequestParam<Response>`

### `design/query_list.kt.peb`

- helper-first list query template
- request inherits `ListQueryParam<Response>`

### `design/query_page.kt.peb`

- helper-first page query template
- request inherits `PageQueryParam<Response>()`

## Implementation Shape

This slice should remain tightly scoped.

### Preset Templates

The default preset family should gain:

- `design/query_list.kt.peb`
- `design/query_page.kt.peb`

and keep:

- `design/command.kt.peb`
- `design/query.kt.peb`

### Planner

The design artifact planner should:

- keep `COMMAND` -> `design/command.kt.peb`
- keep `QUERY` routing framework-owned
- add conservative query-family routing by `typeName` suffix

This does not require changing stage order, source wiring, or template override mechanism.

### Override Fixtures

Functional override fixtures should include representative override files for:

- `design/command.kt.peb`
- `design/query.kt.peb`
- `design/query_list.kt.peb`
- `design/query_page.kt.peb`

These fixtures should prove that a project can migrate the old query-family shape without needing a routing DSL.

### Tests

This slice should add or update tests in three places:

1. planner tests
   - prove variant selection chooses the right template id from conservative suffix rules
2. renderer tests
   - prove each query variant composes the helper contract correctly
3. functional Gradle tests
   - prove override directories can replace all four representative templates end to end

## Future Compatibility

This slice intentionally stops short of adding an explicit query-variant semantic to the canonical model.

If a later slice introduces that semantic, the migration path should be:

- explicit semantic wins over inference
- bounded template ids stay the same
- override template paths stay the same

That means this slice is not throwaway work.

It establishes the stable template family first, while leaving room for a later model refinement if justified.

## Risks

### Risk 1: The slice silently reintroduces old template DSL behavior

If implementation exposes pattern routing or configurable template matching, the framework boundary regresses.

Mitigation:

- keep routing in planner code only
- do not add user-facing pattern configuration
- keep the set of template ids bounded and framework-owned

### Risk 2: Query variant inference becomes too broad or surprising

If matching is too loose, users will not be able to predict which template gets chosen.

Mitigation:

- use conservative suffix rules only
- document the exact suffix rules
- add planner tests that lock the routing behavior

### Risk 3: The slice expands into explicit source-model redesign

If implementation tries to add a full canonical variant model now, the slice becomes too large.

Mitigation:

- keep this slice at planner-owned bounded inference
- defer explicit model widening to a later slice if needed

### Risk 4: Migration fixtures prove only happy-path rendering and miss override reality

Mitigation:

- add functional override fixtures for all representative files
- assert generated output shape, imports, and parent request type

## Acceptance Criteria

This slice is complete when:

- the pipeline preset contains helper-first templates for:
  - `design/command.kt.peb`
  - `design/query.kt.peb`
  - `design/query_list.kt.peb`
  - `design/query_page.kt.peb`
- the design planner can route queries to:
  - default query template
  - list query template
  - page query template
- that routing uses bounded framework-owned logic rather than user-configurable rules
- renderer tests verify helper-first rendering for representative query variants
- functional override tests verify a project can override the representative template family end to end
- no new public DSL is introduced for template dispatch
- bootstrap and integration work remain out of scope

## Expected Outcome

After this slice:

- the original mainline can claim a representative old design-template family now has a stable migration landing zone
- the helper-first contract covers not only command and default query templates, but also list/page query variants
- old query-family behavior is preserved as a framework-owned effect, not as a revived user-owned template-routing DSL
- the next decisions can focus either on further representative template families or on a later explicit semantic model if the current bounded routing proves insufficient
