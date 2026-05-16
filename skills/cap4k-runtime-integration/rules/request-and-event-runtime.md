# Request And Event Runtime

- Commands, queries, and client/cli requests use request params and handlers.
- `Mediator.requests`, `Mediator.cmd`, and `Mediator.qry` send through request supervision.
- Domain events are internal domain facts.
- Integration events are cross-boundary messages.
- `EventSubscriberManager` bridges scanned integration event payloads to Spring `ApplicationEventPublisher`, so generated inbound `@EventListener` subscribers can receive them.
