# Runtime Agent Descriptors After Retirements

## Depends on

Console/Snowflake retirement and the Runtime Agent API facts contract.

## Contract

Static capability descriptors contain only surviving Runtime capabilities. Locker, Snowflake,
Console, and Saga descriptors are absent; no `UNKNOWN` placeholder is emitted for a capability that
is explicitly retired. A capability that has not been executed remains `NOT_PERFORMED` or
`UNKNOWN` according to the manifest contract.

## Acceptance

Descriptor generation and current-runtime-facts validation reject retired identifiers and agree with
the Runtime registry's active provider identities.
