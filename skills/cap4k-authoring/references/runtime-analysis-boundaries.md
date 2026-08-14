# Runtime And Analysis Boundaries

- Domain owns invariants, Application owns use-case orchestration, Adapter maps protocols, and Start assembles runtime.
- Repository restores/accesses Aggregate roots and explicitly removes roots. Factory creates roots. The outer Command owns transaction and automatic Unit of Work stabilization; business code must not locate or flush Unit of Work.
- Mediator is a framework facade, not a business engine. Use only capabilities that the current machine catalog reports as installed and ready.
- Reliable Command and persisted/delayed Domain Event behavior requires the corresponding provider. Registration joins the current transaction and must not force a provider-wide flush.
- Runtime transport consumes, parses, registers and dispatches external events. Application subscribers interpret typed facts, enforce idempotency, translate semantics and delegate state changes.
- Analyzer output proves only the structures and relations it observed. Raw graph, normalized design projection, and Aggregate Structure are independent facts: do not substitute one for another or treat `drawing_board_aggregate_elements.json` as Design JSON.
- `analysis.json` v2 exposes `graph`, `designProjection`, and `aggregateStructure` as partition-local facts. Read the partition requested by the operation, its own status/sources/freshness/diagnostics, and do not use the section-level status or another partition as proof of completeness.
- Default Flow is an entry-centered business causal projection from the raw graph. It keeps concrete entry, Command, Domain Event, and Integration Event nodes; hides Command/Event Handler and Entity Method paths; excludes Query, Capability, and Validator from the default chain; and does not automatically stitch cross-entry processes. Pipeline `flow` is the only product entry.
- Analyzer evidence does not prove business intent, strategic correctness, transaction commit, delivery retry, or compensation.
- Existing evidence is fresh only when the snapshot proves matching project configuration and input identity; otherwise report `stale`, `unknown`, or `missing` and run the explicit planning task when needed.
