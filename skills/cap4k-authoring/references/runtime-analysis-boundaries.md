# Runtime And Analysis Boundaries

- Domain owns invariants, Application owns use-case orchestration, Adapter maps protocols, and Start assembles runtime.
- Repository restores/accesses Aggregate roots and explicitly removes roots. Factory creates roots. The outer Command owns transaction and automatic Unit of Work stabilization; business code must not locate or flush Unit of Work.
- Mediator is a framework facade, not a business engine. Use only capabilities that the current machine catalog reports as installed and ready.
- Reliable Command and persisted/delayed Domain Event behavior requires the corresponding provider. Registration joins the current transaction and must not force a provider-wide flush.
- Runtime transport consumes, parses, registers and dispatches external events. Application subscribers interpret typed facts, enforce idempotency, translate semantics and delegate state changes.
- Analyzer output proves only the structures and relations it observed. It does not prove business intent, strategic correctness, transaction commit, delivery retry, or compensation.
- Existing evidence is fresh only when the snapshot proves matching project configuration and input identity; otherwise report `stale`, `unknown`, or `missing` and run the explicit planning task when needed.
