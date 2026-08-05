# Manual Redrive

## Depends on

Reliable Command/Event state and JPA atomic claim/lease.

## Contract

- Redrive requires explicit operator intent and a record identifier.
- Only retryable/terminal states permitted by policy can be redriven.
- Redrive creates a new attempt under the stored retry-policy snapshot and preserves prior failure
  facts.
- Redrive is idempotent for the same record/token request and cannot bypass claim/lease checks.

## Non-goals

No automatic infinite retry, public scheduler, or transport-specific redrive API.

## Acceptance

Tests cover allowed/denied states, duplicate operator requests, preserved history, token checks,
and subsequent acknowledgement.
