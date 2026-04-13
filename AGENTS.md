# Cap4k Agent Guide

## First Read

When continuing work in `cap4k`, read this file first, then read:

- [Mainline handoff](C:/Users/LD_moxeii/Documents/code/only-workspace/cap4k/docs/superpowers/2026-04-13-cap4k-mainline-handoff.md)
- [Original architecture reset spec](C:/Users/LD_moxeii/Documents/code/only-workspace/cap4k/docs/superpowers/specs/2026-04-09-cap4k-pipeline-redesign-design.md)

## What This Project Is Doing

`cap4k` is in a breaking redesign from the old mixed Gradle/codegen/plugin model to a fixed-stage pipeline.

The stable direction is:

- fixed pipeline stages owned by plugin developers
- repository-level source and generator configuration
- canonical model between sources and generators
- renderer helpers that stay thin and do not take type-resolution ownership back from Kotlin code

## Do Not Reopen These Boundaries

- Pipeline stage order is not customizable by project users.
- Project users can enable or disable sources and generators, but cannot inject custom runtime logic.
- Sibling design-entry type references are still unsupported.
- Short-name auto resolution must stay conservative.
- Symbol identity and explicit FQN remain the source of truth for imports.
- `use()` is design-template-only and must remain a thin explicit-import helper.

## Mainline vs. Integration Work

There are two kinds of work in this repo now:

1. Mainline design-generator quality work
2. Real-project integration boundary work

If the user says "continue the original mainline", use the handoff document and continue the next pending mainline slice.

If the user says "unblock real project integration", read the handoff document section on support tracks first. Do not silently turn an integration workaround into a new global framework rule.

## Current Next Mainline Target

The next mainline step is:

- design template migration / helper adoption

That means:

- consolidate recommended template usage around `type()`, `imports()`, `use()`, and formatted `defaultValue`
- migrate default/override design-template patterns toward the new helper contract
- avoid reopening generator-core or source-layer decisions unless the user explicitly changes scope

## Notes

- `docs/superpowers/specs/` and `docs/superpowers/plans/` contain the historical slices
- the handoff doc explains which slices are completed, which are support tracks, and what should come next
