# Technical Design Contract

Fill every heading before generator input authoring starts. If a heading has no known value, record the blocking question under `openDecisions`.

## businessIntent

State the business goal, actors, state changes, read needs, external facts, and policies accepted from discovery.

## ubiquitousLanguage

List business terms, status names, IDs, external facts, and published terms. Keep implementation-only names out unless they are required for generator input.

## aggregateBoundaries

Record each Aggregate candidate, identity concept, owned Entities, invariants, and lifecycle state transitions. Explain the consistency boundary.

## cap4kCarriers

List Command, Query, Domain Event, Integration Event, Subscriber, Saga, Scheduled Reaction, Domain Service, Specification, Value Object, Strong ID, External Capability, and Open Host Service decisions that apply.

## cleanArchitecturePlacement

Place each carrier in domain, application, adapter, or start. Record framework runtime responsibilities separately from business application responsibilities.

## generatorInputPlan

Name the generator input surface for each expected skeleton: DB/schema, `design/design.json`, value-object manifest, enum manifest, Gradle extension, addons/options, or template override.

## skeletonExpectations

List expected generated skeletons, plan evidence, output ownership, and the Skeleton Generation Gate result for structural changes.

## handwrittenLogicSlots

Identify where business logic will be written inside generated skeleton surfaces and which logic remains in domain behavior.

## ownershipExceptions

Record any explicit technical design exception for handwritten structure that cap4k generation does not support or the user overrides.

## verificationEvidence

Define static, focused-local, or full-evidence expectations for each decision. Claims must match evidence that will actually be produced.

## rollbackTriggers

List triggers that return to business discovery, tactical modeling, technical design, generator inputs, generation review, handwritten implementation, or verification audit.

## openDecisions

List unresolved questions, owner, and whether each blocks generator input authoring.
