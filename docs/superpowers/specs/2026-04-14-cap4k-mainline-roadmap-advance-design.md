# Cap4k Mainline Roadmap Advance After Query-Handler Migration

## Summary

This design updates the durable `cap4k` mainline roadmap so it matches repository reality after two completed mainline slices:

- representative old design template / override migration
- design query-handler family migration

The roadmap should then point to the next original-mainline slice instead of leaving the previous slice listed as still current.

## Goals

- Mark the representative old design template / override migration slice as completed.
- Record design query-handler family migration as a completed mainline slice.
- Advance the roadmap's current next mainline slice to the next design-generator quality step.
- Keep bootstrap and support-track work separate from the default mainline path.

## Non-Goals

- Do not rewrite the broader redesign history.
- Do not fold real-project integration findings into the mainline roadmap.
- Do not reopen bootstrap / arch-template migration.
- Do not implement the next mainline slice in this document update.

## Decision

The roadmap should be updated as follows:

1. `representative old design template / override migration` moves from "Current Next Mainline Slice" into completed mainline work.
2. `design query-handler family migration` is added as completed mainline work immediately after that slice.
3. The new `Current Next Mainline Slice` becomes:
   - `design client / client_handler family migration`

## Rationale

This keeps the roadmap aligned with the actual merged repository state:

- request-side representative migration work is already landed
- bounded query-handler migration is already landed
- the next coherent old-design-family migration remains on the mainline quality track

`client / client_handler` is the recommended next slice because it is still a design-family migration, it is adjacent to the newly completed query-handler work, and it does not widen framework scope into bootstrap or project-specific integration debt.

## Roadmap Edit Scope

The roadmap update should be limited to:

- completed mainline slices
- the status sentence for the current design-generator quality phase
- the "Current Next Mainline Slice" section

No support-track sections should be expanded beyond preserving their existing references.
