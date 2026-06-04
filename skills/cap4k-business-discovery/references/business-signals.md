# Business Signals

Use these signals to recognize business intent before tactical modeling. They are examples, not carrier decisions.

## Intent Signals

| Signal | Example | Capture In Brief |
|---|---|---|
| Actor intent | Editor submits a draft for review. | Actor, goal, requested state change, policy words. |
| Policy decision | Review can be approved only by a senior reviewer. | Policy name, eligibility facts, missing authority rules. |
| State transition | Approved content becomes publication ready after media is ready. | Source state, target state, required facts, forbidden transitions. |
| Read need | Operations needs a queue of overdue reviews. | Read audience, filters, sort order, freshness needs. |
| External fact | Media service reports processing completed. | External party, fact name, payload vocabulary, idempotency concern. |
| External capability | Publication asks a partner service to reserve a slot. | External party, request intent, response facts, fallback path. |
| Time rule | Review expires after three business days. | Clock source, timeout policy, compensation or retry expectation. |
| Public boundary | Partners consume publication status updates. | Published terms, stability expectations, version or compatibility concern. |

## Risk Words

- `approve`, `reject`, `submit`, `publish`, `cancel`, `expire`, `timeout`
- `policy`, `eligibility`, `quota`, `limit`, `unique`, `duplicate`
- `retry`, `recover`, `compensate`, `rollback`, `schedule`
- `callback`, `webhook`, `message`, `import`, `export`, `sync`
- `external fact`, `Published Language`, `Open Host Service`, `cross-service`
- `invariant`, `consistency`, `owner`, `lifecycle`, `state`

Mark these words in discovery output so tactical modeling can decide the cap4k carrier deliberately.

## Missing Facts To Ask About

- Who is allowed to start, approve, reject, or cancel the workflow?
- Which state names are business-visible, and which are implementation-only?
- What makes a repeated callback, message, or request the same business fact?
- Which external system is authoritative for a fact, and which system only observes it?
- What should happen when an external fact arrives late, repeats, conflicts, or is missing?
- Which read views are required for humans, automation, or partner systems?
- What compensation, retry, timeout, or recovery behavior is a business requirement?
- Which terms must be stable for another bounded context or external party?
