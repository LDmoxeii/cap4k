# Service Integration Gotchas

- Framework transport and business subscriber are separate responsibilities. Runtime handles protocol intake, parsing, registration, and dispatch; the business subscriber handles typed fact meaning, idempotency, semantic translation, and delegation.
- Published Language leakage happens when a public contract mirrors Aggregate fields, persistence names, internal Domain Event payloads, or private technical IDs. Boundary language must be stable for external readers.
- Provider terms leak when business contracts say OSS bucket, S3 key, Stripe status, or vendor callback code instead of resource storage, payment result, or another internal capability term. Keep provider vocabulary in adapter-facing mapping unless it is truly public business language.
- Open Host Service is the boundary concept; controller, RPC, gRPC, and HTTP endpoint are implementation forms.
- Entry paths must not write Repository or Aggregate state directly. Writes enter Command or explicit application behavior, then Domain behavior enforces invariants and framework UoW records persistence intent.
- A write use case should not split into an entry calling an external client first and delegating to Command later. Put the outside call inside the approved application use case when it belongs to that use case.
- Inbound and outbound Integration Events are opposite directions. Inbound events are external facts interpreted by this system; outbound events publish confirmed internal facts in Published Language.
- Domain code should never receive callback bodies, protocol headers, message envelopes, provider status fields, or transport DTOs. Translate to typed business facts before reaching Domain behavior.
