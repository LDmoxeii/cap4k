# Integration Event Rules

- Inbound integration events are external facts entering the project.
- Outbound integration events are internal facts made available outside the project.
- HTTP, RabbitMQ, and RocketMQ are adapter transports, not modeling categories.
- Inbound events that advance state must translate into command before touching internal state; non-state-changing observations may translate into query or an explicit application entry point.
- Outbound events are derived from domain facts or application process results and attached at an application orchestration point.
- Do not model external callbacks as domain events.
- Do not let aggregate roots own cross-service protocol details.
- Do not make business code responsible for transport/runtime delivery after an outbound event is attached.
- Event contract stability depends on event name, payload schema, subscriber identity, and serialization behavior.

### Integration Event Payload Boundary

- Outbound integration events publish stable business language, not internal persistence structure.
- Do not expose non-aggregate-root technical IDs as standalone resource identities.
- If consumers need child-level information, prefer aggregate-scoped child keys, deltas, or read-model links.
- External callbacks and inbound messages are external facts. Route writes from them into commands.
- Outbound integration events should be attached from a domain-event subscriber or explicit application process; aggregates only expose the internal facts those components react to.
