# Integration Event Rules

- Inbound integration events are external facts entering the project.
- Outbound integration events are internal facts published outside the project.
- HTTP, RabbitMQ, and RocketMQ are adapter transports, not modeling categories.
- Inbound events that advance state must translate into command before touching internal state; non-state-changing observations may translate into query or an explicit application entry point.
- Outbound events should be derived from domain facts or application process results.
- Do not model external callbacks as domain events.
- Do not let aggregate roots own cross-service protocol details.
- Event contract stability depends on event name, payload schema, subscriber identity, and serialization behavior.
