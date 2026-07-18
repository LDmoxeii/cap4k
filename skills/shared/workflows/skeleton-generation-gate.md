# Skeleton Generation Gate

## Trigger Before

- command, query, client, api payload
- command handler, query handler, client handler
- domain event, integration event, subscriber skeleton
- domain service, saga, scheduled reaction
- aggregate, entity, relation, projection
- factory, specification, unique helper
- repository, controller, adapter, start skeleton
- package or directory skeleton
- any class/interface added only to fix compilation

## Gate Questions

1. Is this generator-supported?
2. Can current cap4k express it through DB/design/types/addon/options?
3. Does the project already have corresponding input?
4. Has plan evidence been reviewed?
5. Will generation create or update it?
6. If handwritten, is the exception in technical design?
7. Does the handwritten path preserve generated-vs-handwritten ownership?

## Allowed Pass States

- not generator-supported;
- generated from inputs and reviewed in plan;
- explicitly documented technical design exception;
- author-owned logic inside generated skeleton;
- explicit user override with risk and verification recorded.

## Failure Action

- stop implementation;
- return to technical design or generator inputs;
- update inputs or exception decision;
- review plan again.

## Evidence To Record

- Technical design section that expects this skeleton.
- Generator input file or source that should produce it.
- Plan item proving output path, outputKind, templateId, and conflictPolicy.
- Explicit exception if handwritten.
- Verification command or review that checked ownership.
