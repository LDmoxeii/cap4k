# Framework Capability Audit Charter

## Outcome

Audit the four cap4k capability blocks under the responsibility reset established by PR #153, then decide whether the combined system is ready for downstream real-project validation.

The Skill block is the accepted baseline. This audit branch continues with Generator, Runtime, and Analyzer before opening the downstream validation gate.

## Responsibility Boundary

- Humans own domain research, organizational coordination, Bounded Context decisions, important tradeoffs, and final acceptance.
- General-purpose agents assist reasoning, translation, implementation, and verification without claiming domain truth.
- Skill owns thin cap4k task routing, operating guidance, and a small number of stable high-risk boundaries.
- Generator owns supported projection from explicit inputs and canonical models into reviewable plans and code structure.
- Runtime owns real execution semantics and non-negotiable framework boundaries.
- Analyzer owns structural observation and engineering evidence, never business authority.

The audit does not evaluate cap4k by asking whether it implements every strategic DDD concept. It evaluates whether each block fulfills its assigned responsibility and hands sufficient evidence to the next block.

## Branch Protocol

- `docs/framework-capability-audit` is the only continuous audit and decision branch.
- Generator, Runtime, and Analyzer audits are performed sequentially on this branch so cross-block decisions retain one context.
- Audit findings are discussed before implementation.
- A finding that requires code changes is implemented in a separately forked session on an isolated `feature/*` or `fix/*` branch.
- Audit work does not opportunistically fix framework code.
- After a fix reaches `master`, the result is brought back to this audit line and the affected gate is re-evaluated against the new mainline fact.
- Downstream reference-project validation starts only after Generator, Runtime, and Analyzer gates have been reviewed together.

## Compatibility Premise

Cap4k currently has no external users. Breaking iteration is explicitly allowed.

Therefore the default decision is:

- prefer one clean current contract over compatibility layers;
- do not retain legacy aliases, no-op shims, dual paths, migration bridges, deprecation periods, or fallback behavior merely to preserve old internal usage;
- delete superseded contracts, fixtures, documentation, and tests in the same change when a boundary is reset;
- update downstream internal projects to the new contract instead of weakening the framework;
- introduce compatibility only when new evidence identifies a real consumer and a confirmed compatibility requirement.

## Audit Method

Each capability block is assessed against current `master`, current tests, provider/catalog contracts, machine-readable Agent API facts, public and internal documentation, and current GitHub backlog.

Findings use these classes:

- `verified`: responsibility is implemented and supported by proportionate evidence;
- `partial`: useful capability exists but its responsibility or proof is incomplete;
- `drift`: two current surfaces disagree about the same contract;
- `missing-core`: the block lacks a capability required by its accepted responsibility;
- `provider/extension`: the boundary is intentionally external and must be disclosed;
- `downstream-evidence`: framework facts are coherent but require real-project proof.

For every finding, record:

- the responsible block;
- the authoritative contract;
- code and test evidence;
- cross-block effect;
- whether discussion, framework implementation, documentation correction, or downstream proof is required.

## Block Gates

### Generator

- Supported inputs and capability descriptors match actual planners and templates.
- Canonical models preserve the semantics required for generation without taking ownership of domain decisions.
- Plan items expose identity, output kind, ownership, path/root, template, and conflict policy needed for review.
- Generated and checked-in surfaces align with runtime contracts.
- Unsupported tactical carriers are disclosed instead of simulated through parallel skeletons.

### Runtime

- Aggregate, Repository, Factory, application execution, Unit of Work, event, integration, and provider boundaries have coherent execution semantics.
- Non-negotiable domain boundaries are explicit, enforced, and tested.
- Starter/provider composition exposes capability presence and conflict behavior honestly.
- Missing advanced capabilities remain provider/extension boundaries rather than implicit promises.

### Analyzer

- Compiler/IR extraction, transport, flow, drawing-board, and Agent API analysis sections agree about what is observed.
- Evidence freshness and availability are explicit.
- Observation coverage is sufficient to review important generated and handwritten tactical paths.
- Analyzer output cannot silently become generator input or business truth.
- Known blind spots and cross-service/process limitations are disclosed and tracked.

## Downstream Readiness Gate

Downstream validation may begin when:

- no unresolved `missing-core` or cross-block `drift` finding remains in Generator, Runtime, or Analyzer;
- accepted `partial` and `provider/extension` boundaries have explicit impact statements;
- the Agent API can expose the relevant installed capability, input, ownership, runtime, analysis, and diagnostic facts;
- the thin Skill can route an agent to those facts without duplicating them;
- a bounded real-project scenario can exercise all four blocks without relying on retired or compatibility-only behavior.

Issue #27 remains the downstream evidence gate. Its historical issue text must be refreshed against current mainline before execution; old Bootstrap and already-closed generator-gap assumptions are not executable requirements.
