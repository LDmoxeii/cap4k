# Integration Event Core Contract

## Depends on

Reliable Event State and Runtime Jackson core contract; must precede broker-specific transport
branches.

## Contract

- One Integration Event envelope carries event identity, type metadata, origin context, delivery
  attempt, and payload JSON.
- Envelope encode/decode uses the shared Runtime Jackson boundary and never persists entities.
- Inbound and outbound paths use the same reliable event state semantics; transport adapters only
  map provider metadata and acknowledgement.
- Subscriber identity is explicit and stable for retries and duplicate delivery.

## Acceptance

Core envelope round-trip tests cover metadata, null/default values, nested payloads, Strong IDs,
context ownership, deterministic JSON, and safe error diagnostics.
