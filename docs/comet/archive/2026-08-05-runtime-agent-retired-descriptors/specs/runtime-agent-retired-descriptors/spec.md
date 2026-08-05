# Runtime Agent Descriptors After Retirements

## Contract

Static capability descriptors contain only surviving Runtime capabilities. Descriptors for Console,
Snowflake, Locker, and Saga are rejected before snapshot encoding. Rejection applies when either the
capability identity's exact normalized segment or the provider identity equals one of those retired
identities. The implementation does not silently filter the descriptor and does not emit an
`UNKNOWN`, `NOT_APPLICABLE`, or other placeholder.

A capability that has not been executed remains represented by the existing manifest status
contract. This change does not infer successful execution from absence and does not introduce the
final `NOT_PERFORMED` or live provider registry model.

The retirement policy is shared by Agent snapshot adapters rather than implemented only in the
Gradle task. Current-runtime-facts validation covers active descriptor declaration sources and fails
when a retired identifier is reintroduced outside the explicit retirement policy and its focused
verification tests.

## Acceptance

- A descriptor with capability identity `runtime.console`, `runtime.snowflake`, `runtime.locker`, or
  `runtime.saga` is rejected before a manifest can be assembled.
- A descriptor whose provider identity is exactly `console`, `snowflake`, `locker`, or `saga` is
  rejected even when its capability identity uses another namespace.
- Unrelated identities that merely contain the same character sequence are not rejected.
- Existing surviving descriptors preserve their supported/effective projection and status behavior.
- The facts validator and Agent snapshot tests agree on the four retired identities.
- Agreement with active Runtime provider identities remains an acceptance requirement of the final
  `runtime-agent-api-facts` slice once that registry exists; this slice does not fabricate it.