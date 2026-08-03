# Outcome

Make every accepted Cap4k Design JSON block a compile-valid, runtime-contract-complete tactical skeleton whose real compiler Analyzer output can be emitted as directly Design-JSON-compatible Drawing Board input and regenerate an equivalent skeleton without manual structural repair.

# Scope

- Repair the seven supported Design JSON tags across source validation, canonical assembly, design planning/rendering, compiler analysis, IR analysis merging, and Drawing Board generation.
- Establish the normalized tactical semantic equality contract `Design JSON == generated skeleton == Drawing Board`.
- Replace the hand-written Issue #92 pseudo-round-trip with a real two-project generate/compile/analyze/drawing-board/regenerate/compile gate.
- Complete Issue #102 through directly compatible Drawing Board fragments and explicit human/Agent import.
- Isolate only the H2/TestKit fixtures required to make the real gate reliable.

# Non-goals

- Do not automatically register or feed Drawing Board output into Generator input.
- Do not automatically convert outbound integration events into inbound events; that remains an explicit bounded-context decision.
- Do not infer business behavior, handler/subscriber bodies, repository strategy, Domain Service operations, transaction policy, or compensation logic from code.
- Do not add a sidecar skeleton index, a dedicated recovery subsystem, primitive arrays, compatibility shims, legacy aliases, or migration bridges.
- Do not weaken PR #152's runtime refusal to persist reliable-event Entity/Aggregate payloads.
- Do not change production JDBC connection lifecycle or introduce Analyzer auto-installation/product wiring.
- Do not begin Runtime capability audit or downstream real-project validation in this branch.

# Acceptance examples

- An omitted `artifacts` list expands to the tag defaults; explicit empty, cross-tag, contradictory, or secondary-only selections fail before planning.
- A `domain_service` block with identity/description/aggregates generates its metadata-bearing anchor; any non-empty `fields` or `resultFields` fails.
- A page Query/API Payload derives `pageNum: Int = 1` and `pageSize: Int = 10`; explicitly declaring either name on a page block fails, while the same names remain legal on non-page blocks.
- A persisted Domain Event without a non-blank `eventName` fails; a present name appears in both analysis metadata and runtime `@DomainEvent`, and conflicting views fail analysis.
- A reliable event payload field named `entity` survives when its semantic type is immutable; an actual Entity/Aggregate type anywhere inside recursive containers remains rejected.
- Strong IDs, Value Objects, enums, project/context classes, and external classes recover as canonical FQNs; builtins, standard containers, and current-block nested DTOs use stable short names recursively.
- `Array<T>` with recursive nullability and `emptyArray()` defaults round-trips; primitive arrays remain unsupported.
- `fields`, `resultFields`, and nested DTO constructor order survive Drawing Board output and regeneration.
- One analysis input directory with complete metadata cannot mask another incomplete directory; the combined Drawing Board request fails with actionable diagnostics.
- Project A generates and compiles, is analyzed by the real compiler registrar, and emits Drawing Board files; clean Project B uses only those files, regenerates and compiles, and has the same normalized tactical projection and framework-owned skeleton semantics.

# Constraints and invariants

- Work only on isolated branch `fix/design-roundtrip-contract` based on `origin/master@540fef09`; merge through a PR to `master`.
- Breaking cleanup is allowed and one clean current contract is preferred.
- Fixed pipeline ownership remains: sources parse, canonical assembly normalizes/validates, generators plan, renderer renders, Analyzer observes compiled structure, Drawing Board exports observation evidence.
- The dedicated `cap4k-analysis-metadata` compile-only/BINARY contract from PR #156 remains authoritative; do not restore old annotation packages or names.
- Runtime annotations and interfaces are part of semantic equality; handwritten business bodies and dependencies are not.
- File names/count/partitioning, JSON formatting, file/entry order, artifact order, optional empty arrays, and omitted versus identical effective defaults may normalize away. Field/nested order, type identity, nullability, defaults, artifact set/variant, direction, persist/eventName, and runtime annotation semantics may not.

# Decisions

- The confirmed implementation contract is the G-05 and fourth-branch handoff in `docs/framework-capability-audit` commit `52af91e9`; the user reconfirmed it when authorizing this implementation session.
- Keep all semantic repairs and the real gate in one branch so Generator and Analyzer cannot land against different metadata contracts.
- Implement in dependency order: accepted input/canonical validation; Generator projection; Analyzer recovery/consistency; Drawing Board compatibility/completeness; real gate and scoped TestKit isolation.
- Issue #102 is satisfied by direct Design JSON compatibility plus explicit import, not by automatic recovery behavior.
- Use one rich seven-tag fixture and one dedicated slow `DesignRoundTripFunctionalTest`; keep detailed positive/negative semantics in focused module tests.

# Open questions

- None. The user confirmed the complete contract before implementation began.

# Verification expectations

- Run focused tests for source/core validation, design generator/templates, compiler Analyzer, IR analysis source, and Drawing Board planner.
- The real gate must invoke `Cap4kCodeAnalysisCompilerRegistrar`; no helper may hand-write `design-elements.json`.
- The positive fixture covers all seven tags, meaningful primary/secondary/page/event variants, Strong ID, enum, Value Object, external FQN, nested List/Set/Map/Array/nullability/defaults, nested DTO order, legal `entity`, and persisted/transient/marker events.
- Negative coverage includes invalid artifacts, Domain Service payload declarations, page collisions, persisted events without names, runtime/metadata conflicts, semantic Entity payloads, primitive arrays, and incomplete per-input analysis metadata.
- Run repository `check` after focused and functional gates pass; record actual results and limitations before opening the PR.
