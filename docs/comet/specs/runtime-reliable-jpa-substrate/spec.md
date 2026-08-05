# Reliable JPA Substrate

## Goal

Provide the internal persistence substrate used by reliable Command and Event state machines.

## Contract

- Claim is atomic and returns a delivery token/ownership record.
- A lease has an expiry and can be renewed only by its current token owner.
- Lost, expired, or mismatched tokens cannot acknowledge or mutate a record.
- Effective retry policy is snapshotted with the record/attempt; later configuration changes do not
  rewrite an in-flight record's history.
- Safe failure facts include type, message summary, attempt, and timing, but never raw business
  payload JSON.
- Retention hooks are explicit and state-aware.

## Boundaries

The substrate is private Runtime infrastructure. It is not a public scheduler, generic task API,
or user-configurable job framework. Command/Event semantics remain in their owner branches.

## Acceptance

Focused JPA tests cover atomic claim races, token mismatch, lease renewal/expiry, retry snapshot
immutability, safe failure persistence, and atomic writes. No transport or repository public API is
changed here.
