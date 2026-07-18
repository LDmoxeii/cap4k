# Cap4k Command Result Fields Design

Date: 2026-07-18

Status: Proposed

Scope: relax the public design JSON contract so `command` blocks may declare `resultFields`, while preserving the existing request/result rendering path, generated ownership rules, static validation behavior, and non-result tag rejection.

## Backlog Source

This design covers the follow-up from the local cap4k investigation:

- `command.resultFields` currently fails in design JSON even though command generation and runtime types can already carry a response payload.
- The current failure is caused by source/canonical/validator allowlists, not by the command renderer losing `resultFields`.

This is a contract adjustment. It should not be implemented as a template workaround.

## Problem

The current public design JSON contract allows `resultFields` only on:

- `query`
- `client`
- `api_payload`

That rule came from the #92 artifact metadata cleanup and was later repeated in the generator input contract. It makes this design JSON invalid:

```json
[
  {
    "tag": "command",
    "package": "order.submit",
    "name": "SubmitOrder",
    "aggregates": ["Order"],
    "fields": [
      { "name": "orderId", "type": "OrderId" }
    ],
    "resultFields": [
      { "name": "accepted", "type": "Boolean" }
    ]
  }
]
```

The current code rejects this before generation:

- `DesignJsonSourceProvider.resultFieldTags` omits `command`.
- `DefaultCanonicalAssembler.ResultFieldTags` omits `command`.
- `scripts/validate-cap4k-generator-inputs.py.RESULT_FIELD_TAGS` omits `command`.

However, downstream capability already exists:

- command planning calls the shared payload render model factory;
- the render model factory passes `block.resultFields` for command blocks;
- the command Pebble template renders `data class Response(...)` when `resultFields` is non-empty;
- the runtime `Command<PARAM, RESULT>` and `RequestParam<RESULT>` contracts already support non-`Unit` result types;
- code-analysis recovery already treats command families as having result fields when a generated or handwritten `Response` class exists.

The result is an inconsistent authoring surface: analysis can observe command response shape, the renderer can emit it, but current design JSON cannot declare it.

## Chosen Approach

Add `command` to the official `resultFields`-capable design tags.

The allowed set becomes:

```text
command
query
client
api_payload
```

`fields` keeps its existing meaning as the command request payload. `resultFields` becomes the command response payload shape.

When a command block omits `resultFields` or declares an empty array, generation keeps the current no-result skeleton shape:

```kotlin
data object Response
```

When a command block declares non-empty `resultFields`, generation keeps the existing template behavior:

```kotlin
data class Response(
    val accepted: Boolean,
)
```

The generated handler remains a generated skeleton. It may contain TODO placeholders for response values. Business logic and actual response construction remain handwritten implementation inside the generated handler slot after generated output review.

## DDD Positioning

Commands may return a command outcome. This design permits that outcome to be explicitly modeled in design JSON.

Acceptable command result examples:

- acceptance or rejection flags;
- created or updated aggregate identifiers;
- request receipt identifiers;
- aggregate version or event version values needed by the caller;
- compact business decision results produced by the command use case.

Non-goal command result examples:

- query read models;
- paged result sets;
- projection snapshots that should be modeled as `query`;
- external API response envelopes that should be modeled as `api_payload`;
- client provider result contracts that should be modeled as `client`.

This is a semantic guidance rule, not a new generator-enforced field-name rule. The generator cannot infer whether a result field is too read-model-like from the field name alone.

## Public Design JSON Contract Change

Update the active public contract:

- `resultFields` is allowed on `command`, `query`, `client`, and `api_payload`.
- `resultFields` is still rejected on `domain_event`, `integration_event`, `domain_service`, and `saga`.
- Removed fields such as `responseFields` remain invalid.
- `fields` and `resultFields` continue to use the same field object shape.
- Field types still must use explicit type names and must not use `self`.

This design supersedes only the earlier contract statements that make `command.resultFields` invalid. It does not reopen legacy `responseFields`, `requestFields`, `desc`, `traits`, `role`, `scope`, or entry-level `entity`.

## Drawing-Board And Analysis Boundary

The analysis boundary remains intact:

- `cap4kAnalysisGenerate` output is observation evidence by default.
- Drawing-board JSON is not automatically a source generation input.
- Manually copied drawing-board content must satisfy the current design JSON contract before it is registered through `sources.designJson.files`.

After this design is implemented, `command.resultFields` is no longer a reason by itself for a drawing-board fragment to be invalid design JSON. Other incompatibilities, such as removed fields, unsupported tags, unsupported artifacts, or invalid event fields, still make the copied content invalid.

## Implementation Boundary

Expected cap4k repair areas:

- `cap4k-plugin-pipeline-source-design-json`
- `cap4k-plugin-pipeline-core`
- repository-level generator input validation script
- focused tests
- public docs and skill contract references

The command renderer and runtime command APIs should not need structural changes for this slice. If implementation discovers a renderer defect while adding tests, fix the defect locally in the renderer path rather than adding a normalization workaround earlier in the pipeline.

Do not move command response rendering logic into source providers or canonical assembly. Those layers should only validate and preserve the declared payload.

## Required Code Changes

Update these allowlists:

- `DesignJsonSourceProvider.resultFieldTags`
- `DefaultCanonicalAssembler.ResultFieldTags`
- `scripts/validate-cap4k-generator-inputs.py.RESULT_FIELD_TAGS`

The new value should include:

```text
command
query
client
api_payload
```

Validation must still fail fast when non-result tags declare `resultFields`.

## Required Documentation Changes

Update current references:

- `docs/public/reference/design-json.md`
- `docs/public/reference/analysis-outputs.md`
- `skills/cap4k-generator-inputs/references/design-json-contract.md`

The docs must:

- list `command` as a valid `resultFields` tag;
- remove examples that say `command.resultFields` is invalid;
- keep the drawing-board evidence boundary;
- preserve the removed-field rejection rules;
- describe command result semantics as command outcomes, not query read models.

Historical specs should not be rewritten as if they never contained the old rule. If needed, add forward references from current docs to this spec rather than mutating old dated intent.

## Testing Strategy

Add focused tests before or alongside implementation.

Minimum source/canonical tests:

- design-json source accepts a `command` block with `resultFields`;
- design-json source preserves command result field names, types, nullability, and default values;
- canonical assembly accepts and preserves `command.resultFields`;
- canonical assembly still rejects `resultFields` on at least one non-result design tag, such as `domain_service` or `saga`;
- `integration_event.resultFields` remains rejected with the existing integration-event-specific diagnostic if applicable.

Minimum generator tests:

- command planner context contains non-empty `resultFields` for a command block that declares them;
- command template output uses `data class Response(...)` when command `resultFields` is non-empty;
- command template output still uses `data object Response` when command `resultFields` is empty;
- response nested types and imports work through the existing response namespace path when command result fields use nested or registered types.

Minimum validator tests or fixtures:

- validator accepts `command.resultFields`;
- validator still errors for `saga.resultFields` or `domain_service.resultFields`;
- validator still errors for removed `responseFields`;
- recovery hint text no longer says `command.resultFields` is invalid.

Do not broaden this into a full generation regression suite unless implementation touches shared rendering or import resolution infrastructure.

## Acceptance Criteria

- Public design JSON accepts `command.resultFields`.
- Canonical design blocks preserve command result fields.
- Generated command payloads with result fields render a typed `Response` data class.
- Generated command payloads without result fields keep the existing `Response` object behavior.
- Query, client, and API payload result-field behavior remains unchanged.
- Domain event, integration event, domain service, and saga still reject result fields.
- Removed legacy fields remain rejected.
- Public docs, skill references, and static validator agree on the same allowed tag set.
- Code-analysis recovery and design-json authoring no longer contradict each other for command response shape.

## Risks

### Risk 1: Commands Become Queries

Allowing command results may encourage authors to return full read models from command handlers.

Mitigation:

- document command results as command outcomes only;
- keep paged/read projection result examples under `query`;
- keep API response envelope examples under `api_payload`.

### Risk 2: Partial Contract Update

If only the Kotlin allowlist changes, the Python validator or skill contract may still tell agents that valid input is invalid.

Mitigation:

- update code, validator, public docs, and skill references in one implementation slice;
- add static search verification for old `command.resultFields invalid` wording.

### Risk 3: Losing Non-Result Tag Protection

Expanding the allowlist too broadly could accidentally permit result payloads on events, domain services, or sagas.

Mitigation:

- add only `command`;
- keep negative tests on at least one non-result tag and on `integration_event`;
- inspect the final diff for broad allowlist replacement mistakes.

## Rollback Triggers

Rollback to this technical design if implementation discovers:

- command template output cannot compile structurally with non-empty `resultFields`;
- command result nested types cannot use the existing response namespace path;
- runtime command dispatch requires no-result command semantics;
- validator and Kotlin source/canonical rules cannot be kept consistent without a larger schema redesign.

Rollback to generator-input design if:

- the public design JSON field contract needs more than an allowlist change;
- drawing-board recovery needs first-class normalization instead of manual contract compliance.

## Lifecycle Notes

After this spec lands:

- write a separate implementation plan before changing production code;
- keep implementation in a non-`master` worktree or branch;
- update any linked issue or backlog item with this spec path;
- leave release and downstream verification decisions outside this spec.
