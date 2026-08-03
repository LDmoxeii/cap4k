# Outcome

PR #157 satisfies the complete design round-trip contract after closing every review blocker against the current head. Design JSON, generated skeletons, real Analyzer recovery, Drawing Board, and second-generation skeletons preserve the same normalized tactical semantics without accepting inputs that Analyzer cannot recover.

# Scope

- Validate page-derived field collisions by the root segment of a flat semantic path.
- Reject Kotlin primitive arrays after final canonical type resolution, including FQN and alias paths.
- Reconcile event metadata with runtime annotation literals exactly and preserve runtime subscriber direction semantics.
- Make form-feed string defaults reversible as Kotlin Unicode escapes.
- Freeze C0 and original Design JSON before any Project A operation and prove the input remains unchanged.
- Complete the rich fixture and focused business-body invariance evidence required by the canonical specification.
- Run focused tests, the real two-project round-trip gate, full `check`, and create fresh Comet verification/archive evidence for the review-fix head.

# Non-goals

- Do not automatically register or backfeed Drawing Board output.
- Do not automatically convert outbound events to inbound; cross-context changes remain explicit manual decisions.
- Do not restore sidecars, infer business bodies, support primitive arrays, change production database connection lifecycle, or weaken the reliable-event Entity payload boundary from PR #152.
- Do not edit or replace the archived `2026-08-03-design-roundtrip-contract` evidence.
- Do not begin Runtime capability audit or Analyzer product auto-installation work.

# Acceptance examples

- A page Query field `pageNum.value` or `pageSize[].value` fails before generation; `filter.pageNum` remains legal, and non-page blocks may still use top-level `pageNum` and `pageSize`.
- `kotlin.IntArray`, an alias resolving to it, and a recursively nested primitive array fail with final canonical FQN evidence; `Array<Int>` remains supported.
- Runtime event names with surrounding whitespace, metadata-missing/runtime-present names, blank subscribers, and whitespace-wrapped `[none]` cannot be normalized into a false metadata match or wrong direction.
- A transient Domain Event with both metadata and runtime names absent remains valid.
- A U+000C default recovers as a supported `\\u000c` Kotlin literal and survives Project B generation and compilation.
- C0 and the original Design JSON bytes are frozen before Generate/Compile/Analyzer; Project A operations cannot mutate the source without failing the gate.
- The fixture explicitly proves ordinary/page Query, ordinary/page API payload, optional-secondary selected and explicit primary-only forms, inbound/outbound events, and persisted/transient/marker Domain Events.
- Different handwritten method bodies, injected dependencies, and repository calls recover identical Drawing Board semantics when framework-owned structure is unchanged.

# Constraints and invariants

- Compare normalized tactical semantics, not file names, JSON layout, entry/file order, or artifact order.
- Preserve ordered fields, result fields, nested DTOs, canonical type identity/nullability/defaults, artifact variants, page semantics, event direction, persist/eventName, and runtime annotation/interface semantics.
- Source validation and canonical assembly remain dual enforcement boundaries.
- Runtime event literals are authoritative runtime facts and are not trimmed or filled from another representation during Analyzer reconciliation.
- Analyzer primitive-array rejection and canonical input rejection must use the same Kotlin primitive-array identity set.
- The real gate must execute `Cap4kCodeAnalysisCompilerRegistrar`; synthetic analysis JSON is forbidden.

# Decisions

- All six PR review blockers are current-head defects, not stale-mainline findings.
- The review wording about missing primary/secondary coverage was broader than the code facts, because the fixture already has some secondary and default primary-only forms. The required fix is explicit paired coverage of optional-secondary selection versus non-default primary-only, plus the other missing variants.
- Event direction is preserved within one bounded context. Cross-context outbound-to-inbound reuse remains an explicit manual edit.
- Existing archived verification is historical and will not be rewritten; this change creates a new review-fix archive.
- The user explicitly confirmed this complete review-fix contract on 2026-08-03 and authorized Build.

# Open questions

- None.

# Verification expectations

- Focused source/core validation tests cover page root paths and final-identity primitive arrays.
- Focused Analyzer tests cover exact event literal reconciliation, runtime subscriber classification, U+000C recovery, and business-body invariance.
- `DesignRoundTripFunctionalTest` proves pre-operation C0/input freezing, explicit rich-shape coverage, real compiler analysis, C0/C1 equality, both generations' skeleton equality, and runtime annotation equality.
- All affected module tests, the dedicated slow gate, repository `check`, Comet bounded check, and GitHub required `check` pass on the updated head.
