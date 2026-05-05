# Default Happy Path

[中文](default-happy-path.zh-CN.md)

> This is the normative center of the cap4k authoring model. Other authoring guides are expected to stay consistent with the rules defined here.

This English page stays shorter than the Chinese page, but it preserves the same final `Must` rule inventory.

The shared teaching project for this guide family is a single bounded-context content publishing and media processing project built around `Content`, `MediaProcessingTask`, and `MediaProcessingCli`. Its default chain is create draft, submit for review, start media processing, receive the processing result, publish when conditions are met, and support retry or rollback when processing fails.

## Rule Strengths

- `Must`
- `Default`
- `Avoid`
- `Advanced`

Interpretation:

- `Must` means the default path is broken if the rule is violated.
- `Default` means this is the baseline shape unless a stronger reason is documented.
- `Avoid` marks shapes that usually introduce drift.
- `Advanced` marks deliberate deviations with extra audit and maintenance cost.

## Compact Rule Table

| Rule | Strength | Constraint |
| --- | --- | --- |
| single-command single-aggregate-root mutation | `Must` | one command path may only enter one aggregate-root persistence boundary |
| mutation converges into command handling | `Must` | controller / job / subscriber surfaces do not directly mutate aggregates |
| aggregate root is the only write-facing surface | `Must` | child entities are not external write targets |
| domain events are registered and released by aggregate roots | `Must` | event content may describe child change, but event ownership remains at the root |
| cross-aggregate write-model strong reference is forbidden by default | `Default` | read-only weak reference is advanced only |
| multiple handlers have no guaranteed order | `Default` | sequencing should be made explicit through staged flow |
| one main action per surface | `Default` | write surfaces advance one command, read surfaces advance one query |
| query observation does not back-pollute the write model | `Must` | query paths observe only; they do not repair or contaminate aggregate state |
| cli is an anti-corruption boundary rather than process truth | `Must` | external capabilities must cross a boundary first |

## Modeling

- `single-command single-aggregate-root mutation` keeps `SubmitContentForReviewCmd` focused on `Content` and `StartMediaProcessingCmd` focused on `MediaProcessingTask`.
- `aggregate root is the only write-facing surface` means child objects are mutated only through root behavior.
- `cross-aggregate write-model strong reference is forbidden by default` means cross-root coordination should prefer IDs, events, queries, or explicitly advanced read-only weak references.

## Command

- `mutation converges into command handling` means every write path, including controllers, callbacks, jobs, and subscribers, must convert into an internal command before aggregate state changes.

## Event

- `domain events are registered and released by aggregate roots` keeps event ownership aligned with business truth.
- `multiple handlers have no guaranteed order` means sequencing must be promoted into an explicit staged flow when order matters.

## Orchestration

- `one main action per surface` keeps write entry points narrow and reviewable.
- If a use case spans multiple stages, model the stages explicitly instead of hiding them inside one handler.

## Query

- `query observation does not back-pollute the write model` means query handlers remain read-only and do not repair aggregate state.
- Query surfaces observe `Content` and `MediaProcessingTask`; they do not repair or back-write aggregate state.
- Read models may project from the write model, but they do not become the write model.

## Integration Boundary

- `cli is an anti-corruption boundary rather than process truth` means external media-processing protocols must be translated before they enter the internal command or query path.
- Callback / integration-event return is the preferred path. Polling is a compatibility fallback, not the main truth source.

## Minimum Workflow Contract

1. Decide whether the work needs a spec and plan first.
2. Run `cap4kPlan` before generation.
3. Run `cap4kGenerate` before handwritten completion.
4. Enter `cap4kAnalysis*` only when the analysis path is explicitly needed.
5. Handwritten orchestration and boundary completion happen after generation.
6. Verification is required before review.
