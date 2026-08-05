# Safe Failure Facts and Reliable Result Repository Removal

## Contract

- Failure facts retain safe type/message summaries, attempt and timing, correlation identifiers,
  and terminal/retryable classification.
- Raw business payload JSON, persisted entity state, and secrets never appear in failure facts or
  logs.
- Obsolete reliable-result polling repositories, archive paths, and their command/event bypasses
  are deleted.
- Completion is observed through the reliable state machine, not by polling a separate result
  store.

## Non-goals

Do not add a new result API, generic job history, or compatibility archive.

## Acceptance

Static references to deleted result/archival surfaces are absent. Tests verify safe diagnostics,
terminal failure state, and no raw payload leakage.
