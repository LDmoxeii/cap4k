# Reliable Retry-Policy Snapshot

## Depends on

Runtime Jackson core contract and the reliable JPA substrate boundary.

## Contract

- A reliable Command/Event record stores the effective retry policy used for its delivery history.
- The current snapshot policy version is `1`.
- The current retryable classification is `ANY_EXCEPTION`.
- `@Retry` overrides `retryTimes` and `retryIntervals` when present. Without an annotation override,
  each carrier keeps its existing Command- or Event-owned fallback retry limit; this capability does
  not unify or change those different fallback limits.
- The default delay curve is deterministic: attempts 1-10 wait 1 minute, attempts 11-20 wait 5
  minutes, and attempts 21 and later wait 10 minutes.
- When custom retry intervals contain fewer entries than the retry history requires, later attempts
  repeat the final configured interval.
- The snapshot includes the effective retry limit, delay/backoff sequence, retryable classification,
  and policy version.
- Later annotation or configuration changes affect only newly created records; they do not mutate an
  existing record's history.
- Retry decisions remain inside the reliable state machine and do not become a generic scheduler.

## Acceptance

- Round-trip tests prove version `1`, classification `ANY_EXCEPTION`, retry limit, and intervals
  survive persistence and redelivery.
- Focused tests prove the 1/5/10-minute default curve boundaries and final custom-interval repetition.
- Annotation override tests prove `retryTimes` and `retryIntervals` are captured without replacing the
  distinct Command/Event fallback limits when no override exists.
- Mutation tests prove a changed current annotation or configuration cannot alter an existing record.
- Failure diagnostics contain no raw payload.
