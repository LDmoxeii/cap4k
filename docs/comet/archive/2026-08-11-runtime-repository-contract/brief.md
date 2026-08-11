# Outcome

Complete the Runtime Repository Contract so repository routing fails deterministically on ambiguity, generated repository carriers remain internal, the declared JPA predicate DSL is fully verified, both ID and Specification query paths expose one coherent sorting/pagination contract, PageData metadata is preserved, and read observation remains separate from persistence intent.

# Scope

- Reject duplicate Repository registrations for the same entityClass + predicateClass during Runtime initialization.
- Preserve the generated JPA repository carrier as an internal implementation detail.
- Verify all comparison and composition operations already declared by Field, Predicates, and generated Specification adapters, including nested expressions.
- Reconcile ID-predicate and Specification query behavior for sorting, offset/limit, PageData items, and totalCount.
- Fix PageData.transform so pageNum and pageSize are not exchanged.
- Cover empty, partial, out-of-range, stably ordered, and total-counted pages.
- Verify that reads only observe loaded aggregates; persistence intent is registered only by explicit new/remove or equivalent Unit of Work operations.

# Non-goals

- Reliable Command/Event state-machine changes.
- Integration Event transport changes.
- Analyzer changes.
- A compatibility bridge for superseded repository behavior.
- A new public predicate AST or generic task/query framework.

# Acceptance examples

- Two contributors registering the same entity and predicate route fail startup with a deterministic diagnostic naming both registrations.
- Distinct entity/predicate routes continue to coexist.
- Equality, null, comparison, collection, string, negation, conjunction, disjunction, nullable operands, and nested predicate combinations execute as declared.
- Equivalent ID and Specification queries honor the selected sorting and pagination contract and report totalCount independently of page item count.
- Empty first pages, partial final pages, and pages beyond the result set preserve requested pageNum/pageSize and return the correct totalCount.
- PageData.transform changes only item type/content and preserves pageNum, pageSize, and totalCount.
- find/findOne/findFirst/count/exists do not register new/delete persistence intent; explicit create/delete paths do.
- Generated repository adapters remain non-public.

# Constraints and invariants

- Base is origin/master at c634f289a304c1a4c5e8c245fc5e02fda1b4185e and includes PR #179.
- Breaking cleanup is allowed; no compatibility layer is required.
- JpaPredicate remains a carrier for ID filters or a JPA Specification. The comparison/composition vocabulary remains owned by the existing schema DSL.
- Duplicate ID inputs have entity-set semantics: one entity appears at most once, and missing IDs are omitted.
- Repository read observation must not be confused with persistence intent. Managed aggregate dirty checking remains valid inside a write Unit of Work.

# Decisions

- Duplicate route registration is rejected during repository supervisor initialization; last-write-wins is forbidden.
- PageData.transform preserves all source pagination metadata in its original field order.
- Generated repository carriers remain private/internal and are not promoted to an application-facing repository abstraction.
- Predicate completeness is proved against the existing Field + Predicates + generated Specification surface; JpaPredicate does not gain a second composition language.
- ID filters execute as database-side predicates and share the Specification path's sorting, offset/limit, PageData, and total-count semantics.
- When explicit OrderInfo is supplied, Runtime appends the entity ID ascending as a deterministic tie-breaker unless the ID is already ordered. Without explicit ordering, result order remains unspecified.

# Open questions

- None.

# Verification expectations

- Focused unit tests for duplicate registration, predicate operators/composition, PageData.transform, query parity, page boundaries, and Unit of Work intent boundaries.
- Focused JPA integration tests for stable sorting, offset/limit, empty/partial/out-of-range pages, and totalCount.
- Existing generated-source visibility tests remain green.
- Run the focused Gradle test tasks, ./gradlew check, git diff --check, and Comet Native verification/evidence checks before archive.
