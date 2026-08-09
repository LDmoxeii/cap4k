# Manual Redrive

## Outcome

Manual Redrive is a private Runtime operation that turns one explicitly selected failed reliable
Command or Event record back into a normal claimable record. It never executes the handler or
publishes an Event directly.

## Contract

- Redrive requires explicit operator intent, a record identifier, and an opaque idempotency token
  for the request.
- The request is evaluated against the current durable state and version through an atomic JPA
  transition. A stale version/state, active lease, or ownership token mismatch has zero durable
  write effect.
- Redrive is allowed only for `EXCEPTION` and `EXHAUSTED` records whose existing `expireAt` is still
  in the future. `EXPIRED`, `EXECUTING`/`DELIVERING`, `EXECUTED`/`DELIVERED`, and `CANCEL` records
  are denied deterministically.
- A successful redrive clears the previous delivery ownership (`deliveryToken` and `leaseUntil`),
  resets `triedTimes` to `0`, makes the record due immediately for the normal worker/coordinator
  path, preserves the immutable retry-policy snapshot, preserves the original `expireAt`, and
  preserves prior safe failure facts. Manual redrive never extends the record lifetime.
- A duplicate request with the same record identifier and request token is idempotent. It must not
  reset the record twice or create a second independent delivery attempt.
- After a successful durable reset, the existing Command worker or Event coordinator is woken. The
  subsequent claim, execution/handoff, acknowledgement, and failure transitions use the existing
  reliable state machine without a parallel redrive path.

## Non-goals

- No automatic infinite retry, background redrive loop, or generic scheduler/task framework.
- No transport-specific redrive API.
- No result polling, archive record, `CommandManager.retry`, Locker bypass, or compatibility bridge.
- No mutation of the retry-policy snapshot and no persistence of raw business payloads, exception
  messages, stack traces, or delivery ownership tokens as operator data.

## Acceptance

- Tests cover an allowed failed Command and Event record becoming claimable and subsequently
  acknowledged through the normal token/lease path.
- Tests cover denied success/cancelled/active-lease states and stale state/version requests with
  zero durable write effect.
- Tests cover duplicate requests with the same idempotency token and prove no double reset or
  second attempt is created.
- Tests prove previous safe failure facts and the persisted retry-policy snapshot remain intact.
- Tests prove the worker/coordinator wake-up is requested only after a successful durable reset.
