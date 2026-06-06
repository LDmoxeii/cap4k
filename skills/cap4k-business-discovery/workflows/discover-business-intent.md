# Discover Business Intent

## Inputs

- User story, business goal, workflow notes, policy text, external fact, callback, message, DDL draft, or design draft.
- Existing business vocabulary from the user or project evidence.
- Known actors, external parties, state names, read needs, and failure paths.

## Questions To Ask

- What business outcome should change or become visible?
- Who initiates the work, who is affected, and which external party participates?
- Which words are business terms, status names, policy names, or external facts?
- Which state changes must happen, and which state changes must not happen?
- Which read views, lists, filters, or summaries are needed?
- Which callbacks, messages, imports, exports, or external observations affect the workflow?
- Which policy, eligibility, compensation, retry, timeout, or approval decisions are missing?

Ask only for missing business facts. Do not ask the user to choose Aggregate, Command, Query, Event, Saga, or generator input details during this phase.

## Business Brief Output

Use this shape before routing to tactical modeling:

```markdown
## Business Brief

- Goal:
- Actors:
- Vocabulary:
- State changes:
- Read needs:
- External facts:
- Policies:
- Open decisions:
- Risk words marked:
```

The brief must separate facts supplied by the user from assumptions. If a decision is unresolved, keep it under `Open decisions` instead of projecting it into a tactical carrier.

## Risk Words To Mark

- Policy or eligibility words: approve, reject, qualify, allow, block, limit, quota, uniqueness, duplicate.
- Workflow words: submit, publish, cancel, expire, timeout, schedule, retry, recover, compensate.
- External fact words: callback, webhook, message, imported, synced, provider, partner, external system.
- Boundary words: public contract, Published Language, Open Host Service, cross-service, outbound event.
- Ownership words: state owner, lifecycle, invariant, consistency, cannot change together.

## Exit Criteria

- The business brief includes goal, actors, vocabulary, state changes, read needs, external facts, policies, and open decisions.
- High-risk words are marked for tactical modeling.
- Blocking business facts have been asked for or explicitly recorded as open.
- No Aggregate boundary or generator input has been chosen in this phase.
- The next route is `cap4k-tactical-modeling`.
