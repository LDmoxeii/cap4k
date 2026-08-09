# Reliable Record Retention and Cleanup

## Goal

Provide the Runtime-owned, bounded deletion boundary for reliable Command and Event records after
their execution state has reached a cleanup-owned terminal state.

## Contract

- Every reliable Command/Event carrier has a nullable terminalization timestamp separate from its
  execution `expireAt` deadline.
- Successful acknowledgement and terminal expiry/exhaustion transitions persist the current
  terminalization time in the same conditional state transition.
- Cleanup accepts an explicit retention policy, service identity, current time, and positive batch
  limit. The policy provides separate retention durations for successful, exhausted, and expired
  records.
- Cleanup selects only records whose service identity matches, whose state is one of
  `EXECUTED`/`DELIVERED`, `EXHAUSTED`, or `EXPIRED`, whose terminalization timestamp is at or before
  the corresponding cutoff, and whose lease is null at delete time.
- The final delete operation rechecks all eligibility predicates. A concurrent claim, redrive, or
  state transition therefore prevents deletion rather than being overwritten by a stale cleanup
  read.
- Cleanup is bounded by the requested batch size, idempotent, and returns only safe aggregate
  counts. It never returns payloads, failure stack traces, or business data.
- Cleanup does not acknowledge, retry, redrive, or otherwise mutate a reliable record; deletion is
  never an alternate state-machine transition.
- Cleanup is explicitly invoked by private Runtime/JPA infrastructure. Automatic scheduled
  deletion is outside this slice.

## State boundaries

Cleanup-owned terminal states:

| Record | Success | Failed/exhausted | Expired |
| --- | --- | --- | --- |
| Command | `EXECUTED` | `EXHAUSTED` | `EXPIRED` |
| Event | `DELIVERED` | `EXHAUSTED` | `EXPIRED` |

The following states are never deleted by this contract: `INIT`, `EXECUTING`/`DELIVERING`,
`EXCEPTION`, and `CANCEL`. This keeps retryable and potentially redrive-eligible history intact.

## Scenarios

### Successful record past cutoff

- **WHEN** a successful record has a terminalization timestamp older than the policy's successful
  retention duration
- **THEN** one cleanup call may delete it, subject to service, null-lease, and batch guards

### Terminal record before cutoff

- **WHEN** an expired or exhausted record is newer than its state-specific retention cutoff
- **THEN** cleanup leaves it durable

### Retryable or redrive-eligible record

- **WHEN** an `EXCEPTION` or `CANCEL` record is old enough to match a generic timestamp cutoff
- **THEN** cleanup leaves it durable because this slice does not own those states

### Live lease

- **WHEN** an otherwise eligible terminal record has a non-null live lease
- **THEN** cleanup does not delete it

### Bounded and repeated cleanup

- **WHEN** cleanup is called with a batch limit and then called again with the same policy
- **THEN** the first call deletes no more than the limit, and the second call is idempotent for the
  records already removed

### Concurrent state change

- **WHEN** a record changes state or obtains a lease after candidate selection but before delete
- **THEN** the conditional delete affects zero rows for that record

## Non-goals

- No manual redrive API or redrive history implementation.
- No generic Scheduler/Job/Task framework or automatic cleanup scheduler.
- No payload archive, result polling, transport, or broker-specific behavior.
- No broad deletion of cancellation history or retryable failures.

## Verification

Focused and real JPA tests MUST cover both Command and Event carriers for:

- terminal timestamp population on success, expiry, and exhaustion;
- state-specific cutoff eligibility;
- service isolation;
- active/live lease exclusion;
- retryable and cancellation exclusion;
- bounded batch deletion;
- repeated cleanup idempotency;
- concurrent state-change fencing;
- entity/JPA/SQL schema alignment.
