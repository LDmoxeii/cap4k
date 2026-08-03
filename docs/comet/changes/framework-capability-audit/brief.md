# Outcome

Audit Generator, Runtime, and Analyzer after the Skill responsibility reset from PR #153, preserve the decisions in one continuous audit context, and determine when the combined four-block system is coherent enough to begin downstream real-project validation.

# Scope

- Treat the merged thin Skill and machine-readable Agent API direction as the accepted Skill baseline.
- Audit Generator first, then Runtime, then Analyzer against current mainline contracts, implementation, tests, public documentation, Agent API facts, and relevant open issues.
- Audit the semantic round trip `Design JSON -> generated skeleton -> Analyzer -> Drawing Board`, including whether every accepted design field is materially represented and recoverable without manual structural repair.
- Classify findings as verified, partial, drift, missing-core, provider/extension, or downstream-evidence.
- Discuss findings before any implementation is authorized.
- Re-evaluate an affected block after an independently implemented fix has merged to `master` and has been brought back to the audit line.
- Produce a combined downstream-readiness decision only after all three remaining blocks have been audited.

# Non-goals

- Do not validate the reference project before the three remaining block audits are complete.
- Do not claim that cap4k covers every strategic DDD concept or can prove domain correctness.
- Do not repair Generator, Runtime, Analyzer, documentation, or downstream projects opportunistically on the audit branch.
- Do not create separate Generator, Runtime, or Analyzer audit branches.
- Do not preserve compatibility for hypothetical users through aliases, shims, dual paths, migration bridges, deprecation periods, or legacy fallback behavior.
- Do not reopen the retired Bootstrap capability.

# Acceptance examples

- Given a Generator contract advertised by descriptors, documentation, or Agent API facts, the audit identifies the actual source, canonical model, planner/template, output ownership, conflict policy, and proportionate tests, or records a specific discrepancy.
- Given a Runtime boundary such as reliable event payload persistence, the audit treats the current runtime semantic boundary as authoritative and identifies generator or documentation drift without weakening that runtime boundary.
- Given an Analyzer observation, the audit distinguishes structural evidence from business truth and identifies freshness, coverage, and transport limitations.
- Given a supported Design JSON entry, generation produces a structurally complete skeleton, Analyzer recovers the same tactical design, and the resulting Drawing Board can regenerate an equivalent skeleton without silently losing or inventing design semantics.
- Given a generated handler, subscriber, domain service, or other behavior surface, business policy may remain handwritten, but compilation shape, declared types, annotations, artifact selection, wiring contracts, and other framework-owned structure do not require manual completion caused by a generator omission.
- Given a finding that requires implementation, the audit records the decision and expected acceptance evidence while leaving the code unchanged on this branch.
- Given a fix merged independently to `master`, the audit refreshes from that mainline fact and re-runs the affected gate before changing its conclusion.
- Downstream validation is not opened while any unresolved missing-core or cross-block drift finding remains.

# Constraints and invariants

- `docs/framework-capability-audit` is the only continuous audit and decision branch.
- Framework fixes use isolated short-lived `feature/*` or `fix/*` branches and enter `master` through the repository's normal pull-request path.
- Cap4k currently has no external users; breaking iteration is allowed and a single clean current contract is preferred.
- Humans own strategic/domain decisions and acceptance. Agents assist investigation, translation, implementation, and verification without claiming business authority.
- Skill routes; Generator projects explicit inputs and canonical models; Runtime owns real execution semantics; Analyzer supplies structural observation and engineering evidence.
- Analyzer output never feeds Generator automatically. Any use of a Drawing Board as authoring input remains an explicit human/Agent action.
- `Design JSON`, its generated skeleton, and the Analyzer Drawing Board must represent the same normalized tactical design. File names, file counts, and physical partitioning may differ; semantic content may not.
- Framework-owned skeleton structure must be complete. Handwritten business behavior is outside this equality contract and remains a human/Agent implementation responsibility.
- Current repository facts outrank historical chat, stale issue text, and old plans.
- PR #152's runtime refusal to persist reliable-event entity payloads is an accepted boundary and must not be reversed as a generator fix.

# Decisions

- PR #153 completed the Skill responsibility reset and is the baseline for this audit.
- Audit the remaining blocks sequentially in this order: Generator, Runtime, Analyzer.
- Keep audit artifacts and cross-block decisions on one branch to minimize context loss.
- Findings are discussed before implementation; implementation is forked into another session and branch by the user when accepted.
- Perform one combined downstream validation only after all four blocks have a coherent current contract.
- Compatibility work requires future evidence of a real consumer and an explicit compatibility requirement.
- Scheduled Reaction remains a handwritten application Job/reaction surface. Remove it from the Design JSON capability descriptor and generated-skeleton claims; do not add a first-class tag, canonical carrier, planner, template, or runtime execution contract in this audit cycle.
- Strong ID Spring MVC path/query binding is part of the Generator gate. Every generated Strong ID must expose a JVM-static String factory that delegates to the existing semantic parser; Runtime must not add reflection-based conversion machinery. Verify the contract with real MVC path and query binding tests.
- Retire the standalone Python generator-input validator and its public documentation surface. Authoritative validation belongs to the actual source providers, canonical assembler, `cap4kPlan` diagnostics, and Agent API evidence; do not preserve a second partial parser or compatibility command.
- Read-model weak-reference projection remains an optional provider/extension boundary. The built-in `aggregate-projection` stays opt-in, adapter-owned, scalar-only, and without read-model runtime; `@RefAggregate` remains a typed scalar identity reference and does not create an automatic projection object relation.
- Do not create automatic Analyzer-to-Generator feedback. Instead, require a semantic round-trip contract: `Design JSON == generated skeleton == Drawing Board`, modulo physical file representation and explicitly accepted non-semantic normalization.
- A generator defect may not force humans to repair framework-owned skeleton structure. All accepted design declarations must be generated into a compile-valid, runtime-contract-complete structural surface and remain recoverable by Analyzer; only domain behavior and business decisions remain handwritten.
- Compare round trips by normalized tactical semantics, not literal JSON text. Normalization may ignore file names/count/partitioning, JSON formatting, file/entry order, artifact order, omitted optional empty arrays, omitted defaults versus the same effective defaults, and type-expression spelling that resolves to the same canonical FQN. It must preserve field/result-field and nested DTO order, resolved type identity, nullability, default semantics, artifact set/variant, event direction, persist/eventName, and runtime annotation semantics.
- Keep `domain_service` as a metadata-only design anchor. It may declare identity, package, description, aggregate ownership, and its `domain-service` artifact, but must reject non-empty `fields` and `resultFields`. Generator emits the annotated service class anchor; business methods remain handwritten, and Analyzer does not infer operation contracts from method bodies. A future first-class operation schema requires a separate confirmed design.
- Require every accepted Design JSON entry to select a non-empty, tag-compatible artifact set containing its primary structural carrier. Secondary query/capability handlers and domain/integration subscribers are valid only alongside their primary contract/event; `integration-subscriber` additionally requires `integration-event:inbound`. Reject explicit empty artifacts, cross-tag families, and secondary-only selections. Omitted `artifacts` continues to expand to the documented defaults; do not add a metadata-only generated carrier.
- Preserve the shared `defaultValue` contract for Domain Event and Integration Event fields. Generator must render the supported stable default-expression subset on event payload and nested DTO constructors, and Analyzer must recover the same normalized default semantics. Whether using a default is domain-correct remains an authoring decision; event templates may not silently discard an accepted default.
- Require non-blank `domain_event.eventName` when `persist: true`; keep it optional for transient Domain Events. Generator must project a present name into `@DomainEvent(value = ..., persist = ...)`, and Analyzer must compare the authoring metadata name with the runtime annotation value and fail on conflict. A persisted event may not have an empty runtime `eventType`.
- Keep explicit BINARY-retained annotation metadata as the lossless carrier for Drawing Board and flow recovery, but move and rename `BuildingBlock` and `AggregateElement` out of `ddd-core` into a dedicated compile-time analysis-metadata contract/module with no runtime meaning and a compile-only project dependency. Default generator templates emit this metadata. Projects may remove it in custom templates when they intentionally opt out of Drawing Board and the corresponding analysis recovery. Do not introduce a sidecar skeleton index in this cycle.
- Fail fast when a requested Drawing Board or flow-analysis task lacks metadata required for complete recovery. Diagnostics must identify the missing symbols, affected capability, and how to restore the default metadata-emitting templates or annotations. Never silently omit unannotated elements or emit a complete-looking partial Drawing Board.
- Permit `entity` as an ordinary Domain Event payload field name. Remove name-based filtering from canonical assembly and Analyzer recovery, and rely exclusively on the resolved recursive semantic type validator to reject actual Entity/Aggregate payload types. Preserve PR #152's runtime refusal to persist reliable-event entity payloads.
- Treat `pageNum/pageSize` on `page` Query and API Payload variants as framework-owned PageRequest structure implied by the variant, not ordinary Design JSON fields. Generator keeps deriving them; Analyzer excludes them only after confirming the page carrier and PageRequest semantics, never by global name filtering. Reject explicit same-name fields on a page variant, allow them on non-page variants, and keep `1/10` as the current variant default contract until a separate explicit page configuration is designed.
- Emit stable type identities in Drawing Board: use normalized short names only for Design JSON builtins, standard containers, and nested DTOs declared by the current building block; emit resolved canonical FQNs for Strong IDs, Value Objects, enums, other project/context types, and external types. Apply the rule recursively inside containers, and never choose a short name merely because the current analysis classpath happens to make it unambiguous.
- Retain `Array<T>` as a first-class Design JSON type and implement lossless Analyzer/Drawing Board recovery for recursive container nesting, element/container nullability, and normalized `emptyArray()` defaults. Keep recursive reliable-event payload validation. Primitive arrays such as `IntArray` and `ByteArray` remain outside the current contract.
- Preserve declaration order for `fields`, `resultFields`, and every nested DTO. Drawing Board may normalize artifact, file, and entry order, but constructor position is tactical semantics and must not be sorted away.
- Deliver Generator repairs through four focused branches/PRs: `fix/generator-contract-surface`, `fix/strong-id-mvc-binding`, `feature/analysis-metadata-contract`, then `fix/design-roundtrip-contract`. The first three may run in parallel from current `origin/master`; the round-trip branch starts only after the metadata-contract PR has merged and includes the real seven-tag gate plus required TestKit isolation. Refresh this audit line from merged `master` evidence after each PR. Keep G-06 optional and G-07 outside the blocking repair set.
- PR #154 merged the Generator contract-surface repair as `d310f3fa`; PR #155 rebased on that result, passed required `check`, and merged the Strong ID MVC repair as `9e0e0bcd`; PR #156 rebased on both prior repairs, passed the combined required `check`, and merged the analysis-metadata contract as `540fef09`. The audit line now includes `origin/master@540fef09`. Only `fix/design-roundtrip-contract` remains before the Generator gate can become ready.
- Start `fix/design-roundtrip-contract` from `origin/master@540fef09` and keep all G-05 semantic repairs in that one branch. Implement in contract order: accepted-input validation and canonical normalization; generator structural/runtime projection; Analyzer recovery and metadata/runtime consistency; Drawing Board compatibility and completeness; then the real second-generation gate and only the TestKit isolation that gate requires.
- Replace the current hand-written Issue #92 pseudo-round-trip with one real gate using two clean project copies. Project A must generate and compile all modules, run the real compiler Analyzer on the generated sources, and emit Drawing Board files. Project B must disable the original Design JSON, use only Project A's Drawing Board files as explicit ordinary Design JSON inputs, regenerate, and compile. Compare normalized canonical projections and the two generations' framework-owned skeletons; a hand-written `design-elements.json`, a second `plan` without generation, or a single shared generated tree is not evidence.
- Evaluate analysis completeness per configured analysis input directory. A complete module must not mask a second module that contains candidate nodes but lacks the metadata/evidence required for complete Drawing Board recovery; the combined request must fail rather than emit a partial board.
- Satisfy Issue #102 through directly Design-JSON-compatible Drawing Board output and explicit human/Agent import. Cross-context outbound-to-inbound variant conversion remains an explicit context decision; the framework must neither auto-register Drawing Board output nor auto-flip event direction.
- Isolate the real gate from H2 file locks with a temp-project-unique in-memory fixture URL and no `DB_CLOSE_DELAY=-1`. Do not change production JDBC lifecycle or mechanically rewrite unrelated fixtures in this branch.

# Open questions

- Confirmed on 2026-08-03: `fix/design-roundtrip-contract` may start from `origin/master@540fef09` under the complete G-05 contract and real two-project round-trip gate recorded here. This continuous audit remains in Shape, and Runtime audit remains deferred until that PR merges and is re-evaluated.

# Verification expectations

- Cite current source files, descriptors, generated plans/templates, tests, public documentation, Agent API outputs, and relevant GitHub issues for each finding.
- Run focused module checks and compile/functional fixtures in proportion to the audited risk; never report an unrun check as passing.
- Record accepted unsupported boundaries and their cross-block impact explicitly.
- Add real end-to-end round-trip evidence that invokes the Analyzer compiler on generated skeletons, compares normalized Design JSON with Drawing Board across every supported tag and artifact variant, regenerates from Drawing Board, and compiles the regenerated result.
- Keep validation and semantic extraction coverage in focused module tests; keep one dedicated slow `DesignRoundTripFunctionalTest` with a rich seven-tag fixture as the cross-module acceptance gate. The gate must exercise the real `Cap4kCodeAnalysisCompilerRegistrar` rather than assuming an ordinary fixture `compileKotlin` task installs the Analyzer.
- The positive fixture must cover all seven tags and meaningful primary/secondary and page/event variants, stable FQNs, `Array<T>` and recursive containers/nullability, defaults, nested DTO order, legal `entity` field names, page-derived fields, and persisted/transient/marker event semantics. Negative tests must cover invalid artifacts, page collisions, Domain Service payload declarations, persisted events without names, runtime/metadata conflicts, semantic Entity payloads, and incomplete analysis input.
- Maintain recoverable Comet checkpoints that reference the current audit artifact and next action.
- Before opening downstream validation, verify that no unresolved missing-core or cross-block drift finding remains and that the thin Skill can route an agent to machine-readable facts for all relevant blocks.
