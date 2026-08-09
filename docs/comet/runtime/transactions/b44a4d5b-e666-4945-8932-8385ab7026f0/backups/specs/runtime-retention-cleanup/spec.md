# Reliable Record Retention and Cleanup

## Depends on

Reliable JPA substrate, retry-policy snapshot, and manual redrive policy.

## Contract

- Retention is evaluated from durable state, completion time, failure facts, and configured policy.
- Active, leased, retryable, and redrive-eligible records are never removed by cleanup.
- Cleanup is idempotent, bounded, and observable through safe counts/facts.
- Deletion never becomes an alternate acknowledgement or state transition path.

## Acceptance

Tests cover completed/failed/expired records, active leases, redrive eligibility, repeated cleanup,
and bounded deletion batches.
