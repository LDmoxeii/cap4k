# Cap4k Event-Driven Skill Rules Design

## Goal

Refine the cap4k authoring skills so they consistently guide business projects toward design-driven generation, explicit write boundaries, zero-trust commands, and event-driven orchestration.

The target is the source skill set in `skills/`. Installed copies outside this repository are deployment artifacts and are not the source of truth.

## Background

Recent review of `cap4k-reference-content-studio` exposed several rule gaps:

- The skill language was too close to "only commands may use repositories", which incorrectly conflicts with query handlers and read infrastructure.
- Command handlers were allowed to drift into process coordination by reading state, branching, and sending more commands.
- Generated-capable surfaces were sometimes handwritten instead of being added to `design.json` and regenerated.
- Listener/subscriber guidance did not state strongly enough that hidden `on(event)` dispatchers are a smell.
- Event payload guidance did not distinguish aggregate snapshots, fact deltas, child keys, and public identities.
- The authoring language talked too directly about controllers, while the better abstraction is open service entry and external fact entry.

## Scope

This change updates cap4k skills and supporting references only. It does not change the cap4k runtime, generator behavior, example project code, or downstream installed skill copies.

## Non-Goals

- Do not introduce a new code package or generated layer named `Application Process`, `Process Policy`, or similar.
- Do not require deleting generated subscriber shells. Generated empty shells can remain until business logic is implemented.
- Do not fight the current generator behavior where generated domain events may carry an aggregate snapshot.
- Do not resolve the split flow rendering concern in this change. Track it via the existing investigation issue.

## Terminology Decisions

### Existing Flow-Orchestration Roles

Cap4k already has enough application-level orchestration surfaces:

- Open host service entry: a stable entry for external consumers to invoke internal use cases.
- External fact entry: a stable entry for facts observed from outside the bounded context, such as callbacks, messages, polling results, or inbound integration events.
- Domain event listener/subscriber: a reaction to an internal domain fact.
- Job: a time-based or batch trigger.
- Saga: persisted long-running coordination, retry, recovery, compensation, or cross-time waiting.
- Command: the explicit aggregate write boundary.
- Query: the explicit read boundary.

Do not add another formal code layer above these. The documentation may describe "flow-orchestration responsibility", but implementation guidance must route that responsibility through the existing surfaces.

### External Interaction Has Two Directions

The skills must separate two concerns:

- Internal consumes external: modeled as an external capability client behind a domain language boundary. Example: media storage, resource storage, payment gateway, moderation service.
- External consumes internal: modeled as open host service entries, external fact entries, published language, inbound messages, callbacks, and integration events.

Do not collapse these into a single "RPC" or "client" bucket. Avoid naming the concept with overly technical transport terms.

## Required Rules

### Repository Access Is A Read/Write Boundary Rule

The rule is not "only commands can use repositories".

Required guidance:

- Command handlers are aggregate write boundaries and may use repositories needed to load/write aggregates.
- Query handlers are read boundaries and may use repositories, JPA, projections, or read-model infrastructure in read-only mode.
- Domain event listeners, external fact entries, open host service entries, controllers, jobs, client handlers, and Saga coordinators must not directly mutate aggregates or call write repositories.
- Flow-routing reads outside a command should normally go through a query instead of ad hoc repository access.
- `Mediator.uow.save()` belongs inside an explicit write boundary and should persist aggregate roots only.
- Child entities, value objects, inline values, and JSON-backed values are persisted through the aggregate root.

### Commands Are Zero-Trust Write Boundaries

A command must validate its own write preconditions and must not trust previous routing checks.

Required guidance:

- Treat controller, open host service entry, external fact entry, domain event listener, job, query result, another command, and Saga state as untrusted hints.
- Load the aggregate root or required write target inside the command.
- Validate target existence, ownership, child membership, status, version-sensitive preconditions, and business invariants before mutating.
- Expected non-ready or already-applied states should return an explicit no-op result instead of throwing.
- Missing target, invalid identity, wrong ownership, invalid child key, and invariant violation should throw a domain/application error.
- Idempotent commands are allowed and expected. Repeated delivery should converge.

### Command-To-Command Calls Are Narrow Exceptions

Commands are not general-purpose coordinators.

Required guidance:

- A command may call another command only as local reuse inside the same synchronous write use case.
- A command that reads state, branches, and sends multiple follow-up commands is a coordinator-command smell.
- Cross-fact, cross-time, conditional continuation, retry, recovery, or compensation should usually be modeled as domain events, external fact entries, jobs, or Saga.
- If a follow-up can be driven by a domain fact, prefer event fan-out over command nesting.

### Event-Driven Flow Is The Default

The default shape for business continuation is:

1. Command mutates an aggregate.
2. Aggregate behavior records a meaningful domain fact.
3. One or more independent listeners react to that fact.
4. Each listener routes writes into zero-trust commands.
5. Repeated delivery converges through idempotency and command-side precondition checks.

Do not emit technical events such as "command completed" merely to continue a process. Domain events should state business facts. External callbacks are external facts, not domain events.

### Listener Organization

Cap4k does not guarantee execution order between multiple listeners or listener methods for the same event.

Required guidance:

- Multiple independent reactions should be represented as multiple `@EventListener` methods.
- Strongly discourage a single public `on(event)` method that manually dispatches to multiple business reaction methods.
- Listener methods should have business-semantic names and perform one reaction.
- Private helper methods are acceptable for shared technical concerns, but not for hiding a business dispatch table.
- Listener-side checks are routing filters only. The called command must still validate.
- Listener failure diagnostics are a known improvement area and should point to the tracking issue.

### External Fact Entry

External fact entries should translate protocol facts into internal language and route writes into commands.

Required guidance:

- Do not pass external protocol payloads directly into aggregate behavior.
- Do not directly mutate aggregates or write repositories from external fact entries.
- If one external fact appears to require multiple reactions, first consider command -> domain event fan-out.
- Multiple routes from one external fact entry are allowed for now, but should be treated as a boundary-review signal.

### Event Payload Boundary

Generated domain events may include an aggregate snapshot. Additional payload should be limited to fact information that the snapshot cannot express well.

Required guidance:

- Extra event fields are appropriate for added child items, removed child items, deltas, before/after values, and computed fact results.
- Extra event fields should not duplicate arbitrary aggregate state.
- Non-aggregate-root technical or persistence IDs must not become public independent resource identities.
- Outbound integration events and open host write contracts should not expose child technical IDs as standalone identifiers.
- Read models may expose aggregate-scoped child keys for UI display, diffing, and selection.
- Write commands that target child elements must include the aggregate root identity plus a child key and validate membership inside the command.

### Design-Driven Generation

Generated-capable surfaces must be generated.

Required guidance:

- Before adding event, subscriber, command, query, client, validator, or API payload surfaces, decide whether `design.json` supports generating that surface.
- If the generator supports it, update `design.json` first and regenerate.
- Do not quietly handwrite generator-supported surfaces.
- If a surface cannot be generated, state the reason in review or final notes.
- Do not delete generated subscriber shells simply because they are empty.

## Documentation Targets

The implementation should update the source skills under `skills/`:

- `skills/cap4k-modeling`
- `skills/cap4k-implementation`
- `skills/cap4k-service-integration`
- `skills/cap4k-generation`
- `skills/cap4k-verification`

The implementation may update public authoring docs only if needed to keep terminology aligned, but the primary change is the skill source.

## Verification Requirements

The implementation must verify:

- No updated cap4k skill file still states that repositories are command-only.
- Command guidance explicitly states zero-trust validation and no-op semantics.
- Command-to-command guidance is limited to same synchronous write-use-case reuse.
- Listener guidance strongly discourages public manual dispatch through one `on(event)` method.
- External interaction guidance distinguishes internal consumption of external capabilities from external consumption of internal services.
- Design-driven generation guidance says generated-capable surfaces must be represented in `design.json` and regenerated.
- References do not point to installed skill copies outside this repository.

## Related Tracking Issues

- Split flow investigation: <https://github.com/LDmoxeii/cap4k/issues/55>
- Multi-listener failure diagnostics: <https://github.com/LDmoxeii/cap4k/issues/56>
