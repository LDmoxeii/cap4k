# Reliable Retry-Policy Snapshot

## Depends on

Runtime Jackson core contract and the reliable JPA substrate boundary.

## Contract

- A reliable Command/Event record stores the effective retry policy used for the attempt.
- The snapshot includes retry limit, delay/backoff, retryable classification, and policy version or
  equivalent identity.
- Later configuration changes affect only newly created records; they do not mutate an existing
  record's history.
- Retry decisions remain inside the reliable state machine and do not become a generic scheduler.

## Acceptance

Round-trip tests prove the snapshot survives persistence and redelivery; mutation tests prove a
changed current configuration cannot alter an existing record; failure diagnostics contain no raw
payload.
