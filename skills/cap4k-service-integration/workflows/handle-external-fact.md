# Handle External Fact

1. Classify the input as an external fact, not a domain event.
2. Translate the external payload into internal language at the adapter/application boundary.
3. Route state changes to command.
4. Route non-state-changing observations to query or an explicit application entry point.
5. Keep callback payloads, message protocol headers, and third-party status codes out of domain events.
6. Ensure callback, message listener, and inbound integration event paths converge to the same internal command semantics.
