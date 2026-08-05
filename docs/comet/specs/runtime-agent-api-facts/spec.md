# Runtime Agent API Facts

## Depends on

All Runtime contracts and surface cleanup.

## Contract

- The static manifest declares capability identity, ownership, and status.
- Unperformed or unobservable work is explicitly `NOT_PERFORMED` or `UNKNOWN`; absence is never
  upgraded to success by inference.
- Current provider state is read from the Runtime registry. Optional Actuator exposure is a live
  diagnostic view, not a second source of truth.
- Retired Console/Snowflake/Locker/Saga capabilities are absent from active descriptors.
- The manifest describes framework capability, not business intent or domain correctness.

## Acceptance

Manifest generation tests, duplicate capability diagnostics, provider-registry snapshots, and
retired-descriptor scans must agree on capability IDs and statuses.
