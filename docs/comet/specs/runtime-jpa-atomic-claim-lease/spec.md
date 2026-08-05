# JPA Atomic Claim and Lease

## Depends on

Reliable JPA substrate and retry-policy snapshot.

## Contract

- Claim changes a record from claimable to owned in one database transaction/statement boundary.
- A claim token identifies the owner; token mismatch rejects acknowledgement, retry, redrive, and
  lease renewal.
- Lease renewal is bounded by the current token and cannot revive a terminal or cancelled record.
- Expired leases become claimable according to the state machine; concurrent claimers observe one
  winner.

## Acceptance

JPA integration tests exercise two concurrent claimers, renewal before/after expiry, token mismatch,
terminal records, and recovery after process loss.
