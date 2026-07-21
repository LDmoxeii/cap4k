# Map Tactical Carriers

## Business Brief Input

Start with the `Business Brief` from `cap4k-business-discovery`. If goal, actors, vocabulary, state changes, read needs, external facts, policies, or open decisions are missing, return to business discovery.

Read `../references/modeling-gotchas.md` when the brief contains marked risk words.

## Carrier Selection Table

Create one row per business signal:

| Business Signal | Cap4k Carrier | Reason | Generator Input Surface | Expected Skeleton Or Plan Evidence | Handwritten Logic Slot | Verification Evidence | Rollback Trigger |
|---|---|---|---|---|---|---|---|

Use `../../shared/references/tactical-affordance-map.md` as the carrier authority. Do not keep generic DDD labels unless they resolve to a cap4k carrier, expected skeleton, and verification evidence.

## Command/Query Split

- Classify each state-changing intent as a Command candidate.
- Classify each read-only view as a Query candidate.
- Reject any Query that mutates state, emits a business fact, or advances workflow.
- Record the actor, input facts, expected state change, and read output shape.

## Domain Event vs Integration Event

- Use Domain Event for an internal completed business fact inside the bounded context.
- Use Integration Event for a stable fact crossing service, team, bounded-context, callback, or messaging boundaries.
- Do not model external callbacks as Domain Events.
- Split events only when the completed business facts differ, not because several consumers exist.

## Subscriber/Saga/Scheduled Reaction Decision

- Use Subscriber for thin application reaction, filtering, idempotent delegation, or follow-up command routing.
- Use Saga only for persisted long-running coordination, retry, recovery, or compensation.
- Use Scheduled Reaction for time, cron, timeout, compensation scan, or polling fallback.
- Reject central listener or process-router ownership when independent reactions and zero-trust commands are sufficient.

## Domain Service/Specification Decision

- Use Domain Service only when a domain decision spans aggregates or has no natural aggregate owner.
- Use Specification when a validation policy must guard saved aggregate state before persistence.
- Do not create either carrier because code feels procedural or a rule is merely a UI filter.

## Rollback Notes

Record one of these notes for every unclear or risky row:

- `concept mismatch -> tactical modeling`
- `unclear carrier -> technical design`
- `missing input -> generator inputs`
- `implementation bypass -> technical design`
- `structure drift -> earliest phase that introduced wrong assumption`

## Exit Criteria

- The carrier selection table covers each business signal and marked risk word.
- Command/Query, event, reaction, Domain Service, and Specification decisions are explicit.
- Each generic DDD concept maps to a cap4k carrier or open decision.
- Rollback notes identify where to return if later evidence conflicts.
