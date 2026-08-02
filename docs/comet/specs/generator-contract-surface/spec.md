# Generator Contract Surface

## Purpose
Keep the live cap4k generator descriptor, canonical model, planner registry, renderer/Agent fixtures, plan evidence, and public authoring contract aligned.

## Requirements

### Scheduled Reaction boundary
- The Design JSON descriptor MUST NOT list Scheduled Reaction.
- Normal tags remain `command`, `query`, `capability`, `api_payload`, `domain_event`, `integration_event`, and `domain_service`.
- Canonical default artifacts and built-in planners MUST contain no Scheduled Reaction, Job, or generic validator carrier.
- `scheduled_reaction` and `job` MUST fail as unsupported normal tags.
- Job remains handwritten application implementation; no alias, empty planner, template, runtime carrier, compatibility task, or migration bridge may be added.
- Agent API and current public docs MUST project this boundary truthfully.

### Plan and Agent ownership contract
- Public and test examples MUST use live short generator ids, not `design-*` or generic `design` ids.
- `outputPath` MUST be the complete repository-relative target path and MUST NOT be concatenated with `resolvedOutputRoot`.
- `resolvedOutputRoot` is optional root metadata: checked-in items may leave it empty or provide a source root; generated-source rebasing updates it to the actual generated root.
- Agent ownership items MUST preserve the same generator/template/path/root semantics as plan items.

### Standalone validator retirement
- The Python validator script and public reference page MUST be deleted.
- Live README, public docs, skills, fixtures, CI, scripts, tests, and navigation MUST expose no validator command, wrapper, alias, no-op entry, or second offline rule set.
- Source-provider parsing, canonical assembly, `cap4kPlan` diagnostics, and `cap4kAgentSnapshot` remain authoritative.
- Historical specs/plans and archived Comet evidence are provenance, not live entry points, and are excluded from zero-reference scans.

### Dead Specification residue
- `ArtifactLayoutResolver` MUST expose no generic aggregate Specification package helper.
- Its corresponding test MUST be removed.
- No Specification generator or compatibility helper may replace it.

### Cross-surface evidence
- Focused evidence MUST connect seven supported tags to canonical default artifacts and registered planner ids through a real plan.
- Agent Snapshot MUST assert the current descriptor projection.
- Renderer and Agent codec positive fixtures MUST use current ids and path shapes.
- Plan tests MUST precisely assert checked-in and generated-source path/root shapes relevant to this contract.

## Non-goals
Strong ID MVC binding, analyzer round trip, analysis metadata migration, H2 isolation, optional projection extensions, diagnostics improvements, or runtime event-boundary changes.

## Compatibility
This is breaking cleanup. Old generator ids, validator commands, aliases, deprecated wrappers, and migration entry points MUST NOT be retained.
