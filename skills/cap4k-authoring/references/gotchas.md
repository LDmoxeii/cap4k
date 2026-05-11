# Gotchas

## Runtime Context Bloat

Do not instruct agents to read the public authoring docs or example repositories during normal skill use. Put required rules in this skill.

## Mediator Bypass

Do not inject command/query handlers directly when the intended cap4k path is `Mediator.cmd`, `Mediator.qry`, `Mediator.cli`, or `Mediator.uow`.

## Repository Misplacement

Jobs, controllers, and transport adapters should not directly become business persistence surfaces. Route write behavior through commands and use queries for read-oriented views.

## Generated Snapshot Confusion

`src-generated/main/kotlin` is an audit snapshot when copied into a reference project. It is not necessarily the active generator output directory.

## Skeleton Overwrite

Handler and factory skeletons that are meant to receive handwritten logic should use `SKIP` conflict policy.

## Unsupported Capability Drift

If value object, saga, domain service, or integration-event generation is not supported by the current generator, record that as a gap instead of inventing a local convention and calling it framework behavior.

## Thin Shell Drift

Do not put formal rules in `AGENTS.md`, `.agents/skills`, or `.cursor/skills`. Those entries are routing shells. The formal rulebook lives under `skills/cap4k-authoring`.

## Rationalizations To Reject

| Rationalization | Reject Because |
|---|---|
| "The skill can just read the public docs." | Runtime context becomes too large and the AI line is no longer self-contained. |
| "A shell can just point to SKILL.md." | Soft pointers disappear in long sessions; shells need inline routing tables. |
| "Gotchas can live only in references." | Stored gotchas are not activated unless normal task paths route to them. |
| "This is cap4k-specific, so project narrative is fine." | Records should still be reusable patterns inside cap4k work, not one-off incident reports. |
