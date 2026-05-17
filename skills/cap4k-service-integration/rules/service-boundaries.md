# Service Boundary Rules

- Open Host Service means external systems or frontend synchronously consume internal capability; HTTP controller, RPC endpoint, and gRPC service are implementation forms.
- Published Language is the stable request, response, status, and error language exposed by an Open Host Service.
- External capability client means internal code consumes an outside capability through an anti-corruption port.
- External fact entry means an outside fact enters the system through callback, message listener, or inbound integration event.
- Internal fact publication means an internal fact is published outward, commonly as an outbound integration event.
- Read-oriented Open Host Service routes to query.
- Write-oriented Open Host Service routes to command.
- External fact entries that advance state route to command.
- Entry implementations never write repositories or aggregates directly.
- Client handlers translate external capability protocols and must not decide aggregate state machines.

### Two Directions Of External Interaction

- Internal consumes external: model the dependency as an external capability client behind internal domain language. Examples: resource storage, media storage, payment gateway, moderation service.
- External consumes internal: model the entry as open host service, external fact entry, published language, inbound message, callback, or integration event.
- Do not collapse these directions into one transport term. Avoid using "RPC" as the domain-facing name for the concept.

### Open Host Service Entry

- Open host service entries expose internal use cases to external consumers through published language.
- They are protocol translation and routing boundaries, not aggregate write boundaries by default.
- Write operations from open host service entries must route into commands.
- Read operations from open host service entries should route into queries or read-model APIs.
- Do not pass transport payloads directly into aggregate behavior.

### External Fact Entry

- External fact entries represent facts observed from outside the bounded context: callbacks, inbound messages, polling results, webhooks, or inbound integration events.
- Translate the external payload into internal published language before routing.
- Every write path from an external fact entry must enter a command.
- If one external fact appears to require multiple internal reactions, first consider command -> domain event fan-out.
- Multiple routes from one external fact entry are allowed for now, but treat that as a boundary-review signal.

### External Capability Client Invocation

- A command may call an external capability client when the external side effect is part of the same write use case and the command will update aggregate state based on the result.
- Prefer domain language names such as resource storage or media storage over provider names.
- Keep provider-specific terms out of aggregate behavior unless they are part of the business published language.
- Technical storage locator terms such as `objectKey` may appear inside infrastructure-facing contracts or media processing paths, but should not become aggregate identity or public business language by default.
