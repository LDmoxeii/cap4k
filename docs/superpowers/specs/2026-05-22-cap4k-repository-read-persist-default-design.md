# Cap4k Repository Read Persist Default Design

## Background

Current cap4k read APIs default `persist = true` across `Repository`, `RepositorySupervisor`, `AggregateSupervisor`, and `Mediator`.

In runtime this flag is not a trivial repository-local option. In `DefaultRepositorySupervisor`, `persist = true` also causes the loaded entity or aggregate root to be registered into `UnitOfWork.persist(...)` after the repository read completes.

That means ordinary reads implicitly opt into later write tracking unless the author remembers to override the default with `persist = false`.

## Problem

This default creates an awkward authoring surface:

- read-only queries and validation reads silently cross into the persistence write boundary;
- command handlers that really intend to mutate one loaded aggregate do not visibly differ from handlers that only read facts;
- authors have to remember to mark many reads as `persist = false` even though the more common case is read-only access.

This clashes with the broader cap4k rule that one command path may read many facts, but should keep the write boundary explicit and narrow.

## Design Goal

Make ordinary repository and aggregate reads non-persistent by default, while preserving explicit opt-in for write paths that intend to mutate a loaded entity or aggregate and later call `Mediator.uow.save()`.

## Proposed Contract

Change the default `persist` value from `true` to `false` for read methods on:

- `Repository`
- `RepositorySupervisor`
- `AggregateSupervisor`
- `Mediator.repositories`
- `Mediator.aggregates`

The runtime meaning of the flag does not change:

- `persist = false`: repository reads may still materialize entities normally, but runtime must not auto-register them into `UnitOfWork`;
- `persist = true`: runtime continues to register loaded entities into `UnitOfWork.persist(...)` so later mutation plus `save()` works as before.

## Scope

This slice includes:

- API default-value changes for the repository and aggregate read chain;
- focused runtime tests for the new default and explicit opt-in behavior;
- updates to command or sample code that currently relies on the old implicit default;
- synchronized documentation updates in `docs/public/authoring`, `docs/superpowers/analysis`, and `skills`;
- explicit breaking-change documentation.

This slice does not include:

- redesigning `UnitOfWork`;
- removing the `persist` parameter;
- changing `remove`, `create`, or explicit `UnitOfWork.persist(...)` semantics;
- broad repository abstraction cleanup beyond the default-value and call-site migration.

## Runtime Semantics

The key semantic rule remains:

- repository read does not itself persist to the database;
- `persist = true` only means "enlist this loaded entity into the runtime write set";
- actual database persistence still occurs only through `Mediator.uow.save()`.

This issue only changes which side of that rule is implicit by default.

## Migration Rule

After the default flips to `false`, any command path that loads an entity or aggregate, mutates it in memory, and expects `Mediator.uow.save()` to flush those changes must explicitly pass `persist = true`.

Typical cases that should stay default `false`:

- query handlers;
- existence checks;
- validation reads;
- list or page reads used only to branch logic;
- read-only projections inside adapters.

Typical cases that should become explicit `persist = true`:

- command handlers that load one aggregate root and then mutate it;
- runtime slices that rely on auto-enlistment rather than explicit `UnitOfWork.persist(...)`.

## Verification Strategy

Verification must cover:

- repository and aggregate read APIs now defaulting to detached/non-enlisted behavior;
- explicit `persist = true` still enlisting into `UnitOfWork`;
- impacted sample or downstream command paths updated to remain correct;
- doc and skill guidance updated to explain the explicit write boundary.

## Breaking-Change Note

This is an intentional breaking behavior change. Existing command handlers that rely on implicit enlistment through default repository reads must either:

- pass `persist = true` explicitly; or
- switch to another explicit persistence path if that better matches the use case.
