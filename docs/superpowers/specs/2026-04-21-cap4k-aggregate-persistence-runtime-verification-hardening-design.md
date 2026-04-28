# Cap4k Aggregate JPA Runtime Defect Reproduction Design

## Purpose

This slice replaces the earlier narrow "runtime verification hardening" idea with a more honest target:

- reproduce the JPA aggregate persistence defects exposed by `only-danmuku`
- classify whether each defect is a cap4k bug, a JPA mapping choice, or an unsupported persistence contract
- fix in place where the current JPA-backed runtime can support the desired contract safely
- defer backend replacement unless reproduction proves the current JPA path cannot support the contract without unacceptable complexity

This is not a backend comparison slice. The first priority is to make the current JPA implementation accountable with focused runtime fixtures.

## Current Context

The aggregate generator can now emit a broad set of bounded persistence annotations and relation mappings. Compile-level verification is not enough:

- generated entities can compile while Hibernate boot fails
- relation mappings can boot while aggregate save/load behavior is wrong
- application-side ID generation can compile while unit-of-work new/existing detection routes persistence incorrectly

The real defects should be reproduced inside `cap4k` before opening a larger persistence backend track.

## Real-Project Defects To Reproduce

### 1. Preassignable Application-Side IDs

The requirement is not "default generated ID or manual ID" as two separate entity modes.

The requirement is:

- an entity may declare an application-side default ID generator, such as Snowflake
- normal creation may omit the ID and let the framework assign one
- selected business flows may obtain an ID before database insert
- that preassigned ID must be used for the later insert
- the flow must not require insert-then-query just to learn the ID

This should be modeled as a preassignable application-side ID policy.

JPA distinction:

- plain assigned identifiers are standard ORM usage: application sets the ID before `persist`
- `@GeneratedValue` means the persistence provider owns generation at persist time
- combining `@GeneratedValue` with arbitrary user-preassigned IDs is not a portable JPA contract
- Hibernate can support this with a custom identifier generator or with a framework-level ID allocator, but cap4k must make the contract explicit

The runtime fixture must prove the desired cap4k contract, not rely on accidental provider behavior.

Required reproduction:

- insert aggregate with ID omitted; framework/provider assigns ID
- insert aggregate with ID already set; persisted row keeps that ID
- unit-of-work must treat the preassigned-ID aggregate as new, not route it to a failing `merge`
- no preliminary insert/query workaround is allowed

### 2. Aggregate Loading Boundary and Lazy Relation Behavior

The real issue is not simply whether `FetchType.LAZY` or `FetchType.EAGER` appears in generated code.

The issue is whether cap4k's repository/unit-of-work contract can support aggregate use without forcing every aggregate relation to be eagerly loaded forever.

Required reproduction:

- load an aggregate through the repository in a normal application transaction
- access owned children inside the transaction
- verify the unit-of-work registration and transaction boundary are sufficient
- identify whether failures are caused by missing transaction scope, detached entities, generated relation mapping, or repository API shape

The fixture should not blindly encode `EAGER` as the framework answer. If eager loading is needed for a specific aggregate shape, that must be a deliberate capability decision.

### 3. Three-Level Aggregate Whole-Save Behavior

`only-danmuku` has aggregate structures like root -> child -> grandchild. The suspected defect is that whole-save/cascade behavior may fail or produce incorrect persistence effects.

Required reproduction:

- create a root aggregate with child and grandchild entities
- persist through the cap4k unit-of-work/repository path
- update nested children and grandchildren
- remove nested children/grandchildren where orphan removal is configured
- verify database state after flush/transaction commit

This should be a runtime behavior test, not a renderer assertion.

## Fixture Strategy

Introduce a dedicated runtime fixture instead of mutating compile-only fixtures.

Recommended fixture:

- `aggregate-jpa-runtime-defect-sample`

The fixture should be intentionally small but structurally representative:

- H2-backed database
- generated aggregate entities
- a tiny runtime smoke entrypoint or Gradle task
- direct Spring/JPA or direct Hibernate boot only if that is the lowest-friction way to exercise the cap4k runtime path

The fixture should prefer cap4k's real repository/unit-of-work path when validating behavior. Direct Hibernate boot is useful only for isolating mapping validity.

## ID Contract Design Options

The implementation plan must choose one explicit ID strategy after reproducing the defect.

### Option A: Framework-Level ID Allocator

The framework exposes an application-side ID allocation path. Factories or command handlers can request an ID before constructing the aggregate, and generated entities use assigned IDs rather than relying on provider generation.

Pros:

- matches the business requirement most directly
- ID is available before persistence
- avoids ambiguous `@GeneratedValue` semantics
- is portable across JPA providers

Cons:

- changes the framework contract around generated IDs
- requires generator/runtime integration beyond annotation output

### Option B: Hibernate Assigned-Or-Generated Identifier Generator

Generated mappings keep a Hibernate-specific generator that returns the existing ID if present and otherwise generates one.

Pros:

- preserves the old annotation style more closely
- can keep normal "omit ID" creation ergonomic

Cons:

- Hibernate-specific
- easy to misunderstand as portable JPA
- still needs unit-of-work new-entity detection to treat preassigned IDs as new

### Option C: Pure JPA Assigned IDs

Generated entities do not use `@GeneratedValue` for application-side IDs. The framework always assigns IDs before persist.

Pros:

- portable and simple at the ORM layer
- no provider-specific generator behavior

Cons:

- no provider-side default generation path
- requires cap4k to supply IDs consistently before persistence

Recommended direction for the spec:

- treat Snowflake-style IDs as application-side IDs
- do not model this as database identity generation
- do not rely on plain JPA `@GeneratedValue` to accept preassigned IDs
- decide between Option A and Option B only after the reproduction test exposes the current failure mode

## Boundaries

This slice is not:

- a full persistence backend comparison
- a Jimmer/MyBatis/JOOQ replacement decision
- a relation model redesign
- a query generator change
- a design-json change
- a broad real-project integration workaround

This slice may change production code only when a failing runtime reproduction proves a cap4k bug or an explicitly approved contract gap.

## Testing Strategy

The implementation should follow reproduction-first discipline:

1. create a minimal fixture that reproduces one defect
2. run the fixture and capture the failure
3. fix only the proven failure
4. keep the regression fixture as the support contract

Focused tests should live with the Gradle functional/runtime verification tests.

Expected verification shape:

```powershell
./gradlew :cap4k-plugin-pipeline-gradle:test --tests "*AggregateJpaRuntime*"
```

The exact class name can be chosen by the implementation plan, but the test names should mention the defect being proved:

- preassignable application-side id persists as new aggregate
- repository load keeps aggregate children usable inside transaction
- three-level aggregate cascades save and orphan removal correctly

## Success Criteria

This slice is complete when:

- the three real-project persistence defects are represented as focused runtime fixtures or explicitly classified as non-defects
- preassignable application-side ID behavior has an explicit cap4k contract
- unit-of-work behavior does not misclassify preassigned-ID new aggregates
- aggregate load behavior is explained by transaction/repository boundaries rather than accidental eager loading
- three-level aggregate save/update/delete behavior is either supported with tests or documented as unsupported with a clear reason
- backend replacement remains deferred unless the reproduction evidence justifies it

## Residual Risk

This slice still does not prove:

- production MySQL dialect behavior
- every relation shape
- concurrency and optimistic-lock conflict behavior
- full Spring Boot application wiring
- alternative persistence backend viability

Those belong to later support-track or backend-comparison work.
