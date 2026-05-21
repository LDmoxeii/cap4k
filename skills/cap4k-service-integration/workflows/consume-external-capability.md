# Consume External Capability

1. Name the internal capability in business language, not provider language.
2. Define the client request/response contract in application terms.
3. Keep provider SDK types, object keys, buckets, status codes, and transport DTOs in adapter handlers.
4. If the capability is part of a write use case, call the client inside the command handler.
5. After the client returns, update aggregate state through aggregate behavior and save the aggregate root through UoW.
6. Define idempotency, retry, and cleanup at the command use-case boundary.
