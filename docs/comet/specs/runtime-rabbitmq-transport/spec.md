# RabbitMQ Transport Contract

## Depends on

Integration Event Core Contract and provider composition.

## Contract

- Exchange/route and subscription identity are explicit configuration.
- Publisher confirmation is awaited before reliable delivery is marked handed off.
- Consumer acknowledgement occurs only after the synchronous handler scope completes.
- Broker redelivery maps to the reliable event state machine without duplicate state machines.
- Error paths preserve safe failure facts and never log raw payload JSON.

## Acceptance

Focused adapter tests cover route identity, publisher confirm success/failure, ack timing, nack/
redelivery, subscription identity, and context propagation.
