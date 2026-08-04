# DDD Authoring Capability Coverage Audit

**Date:** 2026-08-01
**Scope:** current `cap4k` mainline after retirement of framework bootstrap and process-heavy skill surfaces
**Authority:** current provider descriptors, Gradle task contracts, machine evidence, public docs, and the thin `skills/cap4k-authoring` router

## Conclusion

The current framework supports a focused DDD authoring loop:

1. inspect the current project and installed framework capabilities;
2. choose a supported tactical carrier and source/input projection;
3. prepare author-owned local inputs;
4. run plan and review diagnostics/ownership;
5. generate supported structure;
6. implement durable logic only in author-owned surfaces;
7. use analysis and verification as evidence, not as business truth.

Cap4k does not own strategic discovery, ubiquitous-language approval, bounded-context decisions, or business-policy sign-off. Those remain human and project responsibilities. The repository-local skill is therefore a thin framework-operations guide, not a generic DDD process engine.

## Authoritative Machine Contract

Run `cap4kAgentSnapshot` and read `build/cap4k/agent/manifest.json` first. Load only the required sections:

- `project.json`: project/module shape and public tasks;
- `capabilities.json`: supported catalog plus effective current-project state;
- `inputs.json`: configured sources, local paths, safety, and stable redacted identities;
- `ownership.json`: planned artifacts, templates, output kind/root, conflict policy, and freshness;
- `runtime.json`: the versioned Event Handler authoring/execution contract, installed extensions, and explicit runtime/provider boundaries;
- `analysis.json`: analysis configuration and available evidence;
- `diagnostics.json`: actionable failures, hints, and unsupported boundaries.

The stable capability identity links supported and effective views. Provider descriptors are the source of capability truth; the Gradle adapter normalizes them and must not maintain a second static capability catalog.

## Public Gradle Surface

The supported project operations are:

- `cap4kPlan`
- `cap4kGenerate`
- `cap4kGenerateSources`
- `cap4kAnalysisPlan`
- `cap4kAnalysisGenerate`
- `cap4kAgentSnapshot`

Project creation is outside the pipeline plugin. Use the official project template or explicit manual structure work. Retired bootstrap tasks, DSL, modules, markers, guards, slots, aliases, and migration workflows are not supported capabilities.

## Tactical And Generator Coverage

The machine catalog is authoritative for the installed version. Current built-in provider families cover:

- relational schema source and Aggregate/projection generation;
- design JSON source and application/domain carrier generation;
- enum and value-object manifest sources and type generation;
- IR analysis source and flow/drawing-board output artifacts;
- Pipeline Extension contributions for artifact addons and managed-field policies.

The authoring guidance may explain the meaning of Aggregate, Entity, Value Object, Strong ID, Command, Query, Capability, Domain Service, Domain Event, Integration Event, Subscriber, Factory, Repository, and application orchestration. It must still confirm provider availability, activation, inputs, output ownership, and boundaries from the snapshot before claiming that the current project can use them.

Generic Specification is not a generated carrier. Place a rule according to its meaning: Aggregate or Value Object invariant, Domain Service decision, repository predicate, database constraint, or explicit external implementation.

Saga/Process Manager, Event Sourcing, full CQRS, semantic module enforcement, and other non-first-class capabilities must be reported as provider/extension/unsupported boundaries rather than implied framework features.

## Ownership And Safety Boundaries

- Author-owned input files are source truth for generation.
- `CHECKED_IN_SOURCE` is first-materialized project source. Existing handwritten logic is protected by conflict policy and version-control review.
- `GENERATED_SOURCE` is build-owned and replaceable; durable handwritten logic does not belong there.
- `OUTPUT_ARTIFACT` is evidence or visualization output, not business source truth.
- Analyzer output is observational evidence. It does not prove business intent, strategic correctness, transaction commit, delivery retry, idempotency, or compensation.
- Domain Events remain explicit immutable historical facts; an Aggregate or Entity payload is not permitted.
- Reliable Command and persisted/delayed Domain Event behavior requires the corresponding installed provider.
- Repository restores/accesses Aggregate roots and explicitly removes roots. Factory creates roots. The outer Command owns transaction and automatic Unit of Work stabilization.

The Agent snapshot task is read-only and must not connect to a configured live database or other live source. Existing plan evidence is fresh only when configuration and local-input identities prove a match. Live-source evidence remains `unknown` unless the contract can prove freshness; the explicit plan task is the next action.

All snapshot files and diagnostics are credential-redacted. They may expose configured key names, presence, safety class, and redacted stable identity, but never password, token, private key, embedded credential, or raw connection string.

## Failure Semantics

- Invalid required configuration/input: write the recoverable snapshot partitions and diagnostics, mark the manifest `invalid`, then fail the task.
- Optional unavailable partition, such as unconfigured analysis: mark the manifest `partial`, keep a stable reason, and succeed.
- Gradle configuration/evaluation failure before Agent task startup: no structured snapshot is guaranteed; fall back to ordinary Gradle failure evidence.
- Missing or stale plan evidence: do not infer freshness; run the explicit planning task and review diagnostics before mutation.

## Thin Skill Contract

`skills/cap4k-authoring/SKILL.md` and `routing.yaml` are the only always-read skill surface. Routes cover only framework operations:

- inspect project;
- select carrier/input;
- plan/generate;
- implement author-owned logic;
- inspect analysis;
- verify/diagnose.

Focused references explain tactical semantics, output ownership, and runtime/analysis boundaries. The skill must not require strategic workspaces, fixed design dossiers, approval gates, phase chains, forced rollback workflows, or removed specialist skill packages.

## Verification Gates

The current change is complete only when:

- provider descriptor tests prove actual source/generator/extension capability truth;
- Agent contract tests prove stable supported/effective identity, status aggregation, freshness, deterministic hashes, credential redaction, and complete partition output;
- Gradle functional tests prove public task registration, invalid-snapshot failure, optional-partial success, and no live database collection;
- skill checks prove unique routing, progressive loading, thin-surface limits, current task/input names, ownership boundaries, analyzer authority, unsupported claims, and absence of retired active terms;
- public documentation links resolve and describe the same active task/input/ownership contract.
