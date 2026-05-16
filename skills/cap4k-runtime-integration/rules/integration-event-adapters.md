# Integration Event Adapters

- HTTP, RabbitMQ, and RocketMQ adapters transport integration events.
- Inbound integration events should translate into commands, queries, or process steps.
- Outbound integration events should be released from meaningful domain facts or command results.
- MQ binding and external protocol mapping are project-owned integration code, not design JSON generation.
- Cross-service contract stability depends on event name, schema, and serialization behavior.
