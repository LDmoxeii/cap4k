# Cap4k Aggregate Persistence Runtime Verification Hardening Design

## Purpose

This slice hardens the aggregate persistence line by proving selected generated aggregate persistence output works beyond text rendering and Kotlin compilation.

The goal is not to add new generator behavior. The goal is to introduce a bounded runtime verification gate for persistence behavior that is already accepted on the mainline:

- identity id generation
- explicit custom `@GenericGenerator` id generation
- `@Version`
- scalar `@Column(insertable/updatable)` controls
- `@DynamicInsert`
- `@DynamicUpdate`
- `@SQLDelete`
- `@Where`

The verification target is a generated representative aggregate project. The test should run the pipeline, compile the generated domain module, then execute a small Hibernate/JPA runtime smoke program against the generated entities.

## Current Context

The aggregate persistence mainline is complete through bounded generic-generator parity. Current coverage proves:

- source carriage for explicit DB comment metadata
- canonical enrichment and planner render models
- renderer output for the bounded persistence annotations
- functional generation assertions
- Kotlin compile-level viability

The remaining gap is runtime validity. A generated entity can compile while still failing Hibernate boot or runtime mapping because of invalid annotation combinations, unsupported generated-value wiring, bad soft-delete SQL, or provider-specific metadata shape.

This slice closes that gap with a small runtime verification harness. It intentionally does not become a real-project integration track.

## Design Principles

- Runtime verification must test generated output, not mock render contexts.
- The fixture should be small and representative, not a real application.
- Hibernate/JPA boot and metadata smoke is the primary goal.
- CRUD smoke should stay minimal and focused on the highest-risk accepted behavior.
- Custom `@GenericGenerator` persist behavior is only required if a tiny fixture-side generator implementation can support it without expanding the slice.
- No new persistence semantics should be introduced.
- No relation-side runtime verification should be introduced in this slice.

## Verification Level

This slice should verify three levels.

### Level 1: Generated Project Boot Smoke

The runtime fixture must:

- run `cap4kGenerate`
- compile the generated domain module
- start a Hibernate `SessionFactory` or JPA `EntityManagerFactory`
- register generated aggregate entity classes
- fail if generated persistence annotations are not accepted by the provider

This is the minimum success gate.

### Level 2: Minimal Runtime Behavior Smoke

The runtime smoke should exercise a very small number of behaviors:

- insert and query an identity-id entity
- insert and update a versioned entity
- remove a soft-delete entity and verify normal queries are filtered by `@Where`
- verify the row remains physically present and the soft-delete marker changed

The runtime smoke should avoid broad repository, transaction manager, Spring Boot, or business-invariant testing.

### Level 3: Custom Generic Generator Boot, Optional Persist

The custom generator path must at least be part of Hibernate boot metadata:

- generated entity has `@GeneratedValue(generator = "...")`
- generated entity has `@GenericGenerator(name = "...", strategy = "...")`
- Hibernate accepts the mapping during boot

Persisting an entity through the custom generator path is a stretch goal, not a hard requirement, unless the fixture can provide a tiny and stable generator implementation without pulling in larger runtime plumbing.

## Fixture Strategy

This slice should introduce a dedicated runtime-capable aggregate fixture rather than mutating the existing compile fixtures into mixed-purpose test assets.

Recommended fixture:

- `aggregate-persistence-runtime-sample`

The fixture should reuse the same overall aggregate project shape already used by compile fixtures:

- root project with `cap4k` plugin
- `demo-domain`
- `demo-application`
- `demo-adapter`
- DB source enabled against fixture-local H2
- aggregate generator enabled

The runtime fixture should be based on the current aggregate persistence compile fixtures, but with a separate purpose:

- compile fixtures remain compile-only quality gates
- runtime fixture becomes the runtime boot/smoke gate

## Runtime Harness

The runtime harness should use a small executable smoke entrypoint inside the generated sample instead of adding Spring Boot test infrastructure.

Recommended shape:

- add a small runtime smoke source under the fixture, for example `demo-domain/src/testFixtures` or a bounded runtime source folder already compiled by the fixture build
- expose a simple Gradle task that runs a small main entrypoint after generation and compilation
- the smoke entrypoint should bootstrap Hibernate/JPA directly, using a minimal programmatic Hibernate configuration such as `org.hibernate.cfg.Configuration` or `HibernatePersistenceConfiguration`

The smoke entrypoint should:

1. build a Hibernate `SessionFactory` or JPA `EntityManagerFactory`
2. point to the fixture-local H2 datasource
3. register the generated aggregate entities
4. execute the bounded runtime smoke steps
5. exit with failure on the first runtime contract break

This keeps the runtime verification focused on generated persistence mappings rather than on application framework wiring.

## Representative Entities

The fixture should stay small. Two entities are enough.

### `VideoPost`

This entity should represent the higher-risk bounded persistence behavior:

- explicit custom `@GenericGenerator` path or, if persist smoke is not feasible, at least custom generator boot metadata
- `@Version`
- `@DynamicInsert`
- `@DynamicUpdate`
- `@SQLDelete`
- `@Where`

### `AuditLog`

This entity should remain on the default bounded identity path:

- `@GeneratedValue(strategy = GenerationType.IDENTITY)`
- no custom generator annotations

This proves custom and default paths coexist in the same generated project.

## Runtime Assertions

### Required Assertions

The runtime smoke must prove:

1. Hibernate/JPA boot succeeds with generated aggregate entities.
2. Identity-path entity insert succeeds and the entity can be queried back.
3. Versioned entity update succeeds through the normal optimistic-locking mapping path.
4. Soft-delete entity remove path uses bounded generated annotations without runtime mapping failure.
5. After soft delete:
   - normal entity query is filtered by `@Where`
   - the physical row still exists
   - the soft-delete marker column is updated

### Custom Generator Assertions

`@GenericGenerator` should be treated as legacy-compatibility parity, not as a new recommended API surface. Hibernate 6 documentation describes newer identifier generator APIs as superseding `@GenericGenerator`, while older custom `IdentifierGenerator` implementations remain compatible. This slice keeps `@GenericGenerator` only because old CAP4K output used it.

Hard requirement:

- custom-generator entity participates in provider boot successfully

Conditional stretch:

- persist custom-generator entity
- flush succeeds
- generated id is present

This conditional runtime assertion is allowed only if the fixture-side generator implementation remains tiny and stable.

## Boundaries

This slice is intentionally not:

- a new persistence feature slice
- a relation runtime verification slice
- a Spring Boot integration test slice
- a repository behavior slice
- a real-project support slice

It does not add:

- sequence or table strategy recovery
- generator parameter bags
- split generator name and strategy inputs
- relation runtime semantics
- `ManyToMany`
- `mappedBy`
- `@JoinTable`
- full provider-specific runtime coverage beyond the already accepted bounded annotations

## Implementation Shape

The implementation should be a test/harness slice, not a generator behavior slice.

Expected implementation areas:

1. Runtime fixture
   - add `aggregate-persistence-runtime-sample`
   - include a small H2-backed runtime setup
   - include a small smoke entrypoint or task

2. Functional test
   - add a Gradle functional test that copies the runtime fixture
   - runs `cap4kGenerate`
   - runs the runtime smoke task
   - asserts the task succeeds

3. Generated output checks
   - keep a small set of file assertions for the important generated annotation shapes
   - do not duplicate the full renderer test suite in the functional test

4. Optional tiny custom generator implementation
   - only if needed and small
   - it must live inside the fixture
   - it must not become a new framework contract

No production generator code should change unless runtime verification exposes an actual generated-output defect. If a defect is found, the fix must stay within the already accepted persistence behavior and should not add new semantics.

## Testing Strategy

The primary new test should live with the Gradle functional tests because it verifies generated-project behavior.

Recommended test name:

- `aggregate persistence runtime smoke validates generated Hibernate mappings`

The test should run a bounded command sequence equivalent to:

1. copy runtime fixture
2. run `cap4kGenerate`
3. run fixture runtime smoke task

The verification command for this slice should include:

```powershell
./gradlew :cap4k-plugin-pipeline-gradle:test --tests "com.only4.cap4k.plugin.pipeline.gradle.PipelinePluginPersistenceRuntimeFunctionalTest"
```

If a new test class is not introduced, the equivalent focused test in the existing functional test class is acceptable. The implementation plan should choose the lowest-friction option that keeps runtime tests discoverable.

The final focused regression should include:

```powershell
./gradlew :cap4k-plugin-pipeline-gradle:test
```

If fixture dependency or runtime setup touches shared compile helper infrastructure, include the affected focused module tests as well.

## Success Criteria

This slice is complete when:

- a generated aggregate runtime fixture boots Hibernate/JPA with generated entities
- identity id path persists and queries successfully
- version mapping participates in a basic update path
- soft-delete mapping updates the marker and `@Where` filters normal queries
- custom generic-generator mapping participates in provider boot
- custom generic-generator persist is either covered by a tiny fixture generator or explicitly left as a documented residual risk
- no new persistence semantics are introduced
- focused Gradle functional regression passes

## Residual Risk

This slice still does not prove:

- real application transaction manager behavior
- Spring Boot autoconfiguration
- repository behavior
- production database dialects
- concurrency conflict handling for optimistic locking
- full Hibernate provider behavior matrix

Those are outside the current mainline. They belong to later support-track or explicitly activated integration work.
