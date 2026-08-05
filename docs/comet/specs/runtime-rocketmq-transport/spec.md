# RocketMQ Transport Contract

## Depends on

Integration Event Core Contract and provider composition.

## Contract

- Topic/tag/consumer-group identity is explicit configuration.
- A send is handed off only after the provider's supported send result/confirmation is successful.
- Consumer success is acknowledged only after synchronous handler completion.
- Provider exceptions map to retryable/terminal reliable-event outcomes with safe diagnostics.
- Raw event payload JSON is excluded from error logs.

## Acceptance

Focused adapter tests cover route and subscription identity, send success/failure, acknowledgement
timing, provider error mapping, duplicate delivery, and context propagation.
