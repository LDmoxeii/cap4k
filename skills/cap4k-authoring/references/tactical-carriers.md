# Tactical Carriers

Use business meaning first, then confirm current support and inputs in `capabilities.json` and `inputs.json`.

- Aggregate owns identity, lifecycle, and invariants that must change consistently; Entity lives inside an Aggregate boundary.
- Value Object and Strong ID express value equality and type-safe identity, not independent lifecycle.
- Command changes facts; Query observes without mutation; Capability calls an external owner through an anti-corruption boundary.
- Endpoint is one transport-neutral published Actor operation with explicit stable `operationName` and Request/Response. Business code calls it through `Mediator.endpoints`; a Provider implementation or generated RPC Consumer remote handler is a local `EndpointHandler`, never a directly called client service.
- Domain Service holds a domain decision with no natural Aggregate owner; it is not an application transaction script.
- Domain Event is an internal completed historical fact with explicit immutable fields. Its ownership never permits an Aggregate or Entity payload.
- Integration Event is stable published language across a service or bounded-context boundary. Its payload belongs in the dependency-leaf contract module, while a business subscriber remains application-owned, interprets typed facts and delegates writes; transport intake belongs to runtime adapters.
- Subscriber is a thin reaction. Scheduled Reaction handles time or polling triggers. Durable multi-step progress needs an explicitly selected provider when the machine catalog exposes no first-class carrier.
- Factory creates roots, Repository loads/accesses and explicitly removes roots, and Application orchestration coordinates use cases.

A generic Specification is not automatically a generated carrier. Place the rule on an Aggregate or Value Object, a Domain Service, repository predicate, database constraint, or explicit external implementation according to its meaning.
