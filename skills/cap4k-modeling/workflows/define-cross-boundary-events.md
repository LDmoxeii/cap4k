# Define Cross-Boundary Events

1. Identify whether the fact originates inside the domain or outside the service boundary.
2. Use domain events for internal domain facts emitted by aggregate behavior.
3. Use `integration_event` for cross-boundary contracts.
4. For every `integration_event`, define `role`, `eventName`, payload fields, producer, consumer, and serialization expectations.
5. For inbound events, define the application command or process step that receives the translated fact.
6. For outbound events, define which domain fact or command result releases the message.
7. Keep MQ binding, HTTP callback shape, and external protocol mapping out of the domain model.
