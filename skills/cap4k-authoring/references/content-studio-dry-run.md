# Content Studio Dry Run

## Scenario

Flow: draft content -> submit review -> approve content -> media processing external fact -> media ready -> publication ready -> publish.

- A content editor drafts article copy, selects a channel, and attaches intended media.
- The editor submits the draft for review.
- A reviewer approves the content semantics and channel fit.
- A media platform sends a media processing external fact for the attached media.
- The content slice records media ready after the external fact is interpreted.
- The publication policy marks the content publication ready when approval and media readiness are both present.
- A publisher publishes the ready content.

## Business Discovery Output

- Business goal: move editorial content from draft to published only after approval and media readiness.
- Actors and external parties: Content Editor, Reviewer, Publisher, and Media Platform.
- Vocabulary: Content Draft, Review Submission, Content Approval, Media Processing Fact, Media Ready, Publication Ready, Publication.
- State changes: drafted, submitted for review, approved, media processing observed, media ready, publication ready, published.
- Read needs: review queue, media readiness view, publication readiness view, published content history.
- Policies: approved content plus ready media is required before publication.
- External fact: media processing result arrives from the Media Platform and must be translated before affecting publication readiness.

## Tactical Modeling Output

- Aggregate: `ContentItem` owns the editorial lifecycle, review status, media readiness marker, and publication state.
- Strong IDs and value objects: `ContentId`, `MediaAssetId`, `Channel`, `ContentBody`, and `PublicationWindow`.
- Commands: `DraftContent`, `SubmitContentReview`, `ApproveContent`, `RecordMediaProcessingFact`, `MarkMediaReady`, `MarkPublicationReady`, and `PublishContent`.
- Queries: `ListReviewQueue`, `GetMediaReadiness`, `ListPublicationReadyContent`, and `GetPublishedContentHistory`.
- Domain Events: `ContentDrafted`, `ContentReviewSubmitted`, `ContentApproved`, `MediaReadyRecorded`, `PublicationReadyMarked`, and `ContentPublished`.
- Inbound external fact: `MediaProcessingCompleted` is interpreted by an application subscriber and delegated to the appropriate command path.
- Outbound Integration Event: `ContentPublishedNotice` uses published language for downstream readers.
- Saga or scheduled reaction: a publication readiness reaction checks approval and media readiness before marking publication ready.
- Domain Service or Specification: `PublicationReadinessPolicy` expresses the approval plus media readiness rule.

## Technical Design Contract Excerpt

- `businessIntent`: publish approved editorial content only when media is ready.
- `ubiquitousLanguage`: keep review, media readiness, publication readiness, and publication as distinct concepts.
- `aggregateBoundaries`: `ContentItem` is the lifecycle aggregate; media processing remains an external fact.
- `cap4kCarriers`: Aggregate, Command, Query, Domain Event, Integration Event, Subscriber, Saga, Specification, Value Object, and Strong ID.
- `cleanArchitecturePlacement`: domain holds lifecycle invariants and readiness policy; application orchestrates commands, queries, and external fact translation; adapter maps protocols and external payload shape; start assembly remains outside business semantics.
- `generatorInputPlan`: express aggregate state, commands, queries, events, subscriber expectations, type manifests, and projection needs through supported input surfaces.
- `skeletonExpectations`: command handlers, query handlers, subscriber shell, event carriers, policy slot, projections, and adapter payload types appear as generated or explicitly accepted surfaces.
- `handwrittenLogicSlots`: readiness policy, command decision logic, external fact semantic translation, idempotency decision, and publication notice mapping.
- `ownershipExceptions`: none for this dry run; missing generator-supported skeletons roll back instead of being handwritten.
- `verificationEvidence`: route reads, plan evidence review, ownership classification, static diff review, and rollback notes.
- `rollbackTriggers`: concept mismatch, unclear carrier, missing input, plan mismatch, ownership conflict, implementation bypass, structure drift, and verification drift.

## Generator Input Expectations

- DB/schema or design JSON represents `ContentItem`, lifecycle states, review state, media readiness fields, publication state, and read projections.
- Type manifests define strong IDs, value objects, and status enums used by the lifecycle.
- Input options identify generated skeleton families for commands, queries, domain events, integration events, subscribers, projections, and adapter payloads.
- External fact input names must preserve the boundary between media processing observation and internal publication readiness.
- Missing expected skeleton evidence returns to generator inputs or technical design.

## Plan Review Expectations

- Plan evidence should show each expected command, query, event, subscriber, projection, and adapter payload in the correct module and ownership class.
- Conflict policy should preserve generator ownership for skeletons and leave handwritten logic inside approved slots.
- Template and output kind choices should match the technical design contract.
- Any missing `MediaProcessingCompleted` translation surface, readiness policy slot, or publication notice carrier is a rollback finding.
- Review stops when route, plan evidence, ownership, and rollback notes are clear enough for human audit.

## Generation Stop Point

Stop after plan and generated-output review identify the expected generated surfaces, ownership classes, and conflicts. Do not continue into handwritten implementation from this dry run.

## Human Review Gate

- Human review confirms the business flow and tactical carriers.
- Human review confirms generated skeleton ownership and approved handwritten slots.
- Human review explicitly authorizes any later handwritten implementation task.
- Any disagreement sends the work to the earliest rollback target that introduced the wrong assumption.

## Handwritten Implementation Surfaces

- Command handler logic validates allowed state transitions and emits domain events through approved surfaces.
- `PublicationReadinessPolicy` decides whether approved content with ready media can become publication ready.
- Application subscriber logic interprets the typed media external fact, handles idempotency, and delegates to command handling.
- Query handlers shape review queue, media readiness, publication readiness, and publication history reads.
- Outbound publication notice mapping uses published language and does not expose internal aggregate shape.

## Verification Mode

Use `static-only` for this dry run. Evidence is limited to route selection, required reads, dry-run scenario coverage, generator input expectations, plan review expectations, ownership boundaries, rollback examples, and skipped-check disclosure.

Do not claim `focused-local` or `full-evidence` for this reference.

## Rollback Examples

- Concept mismatch: if media processing is treated as internal content approval, return to business discovery.
- Unclear carrier: if publication readiness is confused with publication, return to tactical modeling.
- Missing input: if `RecordMediaProcessingFact` has no supported input surface, return to generator inputs.
- Plan mismatch: if plan evidence omits the subscriber shell or readiness policy slot, return to generator inputs or technical design.
- Generation ownership conflict: if generated skeletons are edited as source truth, return to generation review.
- Implementation bypass: if someone asks to handwrite a missing generated command handler, return to technical design.
- Verification drift: if static evidence is reported as broader evidence, return to verification audit.
