# Cap4k Mainline Roadmap

Date: 2026-04-17

## Purpose

This document records durable redesign status and decisions for `cap4k`.

It replaces the temporary handoff style with a longer-lived repository record for:

- completed mainline slices
- the current next mainline slice
- work that must stay on separate tracks
- bootstrap contract decisions that should not be rediscovered from chat history

## Original Mainline

The original mainline is:

1. reset architecture to a fixed-stage pipeline
2. migrate major capability slices into that pipeline
3. improve design-generator output quality until old template migration becomes practical

## Completed Mainline Slices

### Phase A: Pipeline Architecture Reset

Completed:

- pipeline foundation
- db aggregate minimal
- ir flow
- drawing-board
- DSL consolidation

Status:

- complete

Traceability:

- [pipeline redesign design](specs/2026-04-09-cap4k-pipeline-redesign-design.md)
- [pipeline db aggregate minimal design](specs/2026-04-09-cap4k-pipeline-db-aggregate-minimal-design.md)
- [pipeline ir flow design](specs/2026-04-10-cap4k-pipeline-ir-flow-design.md)
- [pipeline drawing-board design](specs/2026-04-10-cap4k-pipeline-drawing-board-design.md)
- [pipeline DSL consolidation design](specs/2026-04-10-cap4k-pipeline-dsl-consolidation-design.md)

### Phase B: Design Generator Quality Mainline

Completed:

- minimal usable
- type/import resolution
- auto-import
- template helpers
- default value
- `use()` helper
- design template migration / helper adoption
- representative old design template / override migration
- design query-handler family migration
- design client / client_handler family migration
- design validator family migration
- design api_payload migration
- design domain_event / domain_event_handler family migration
- design generator compile-level / integrated verification hardening

Status:

- the original design-generator quality mainline is complete through compile-level / integrated verification hardening
- roadmap completion state follows merged repository history; some linked slice specs may still retain pre-merge draft headers

Traceability:

- [design generator minimal usable design](specs/2026-04-10-cap4k-design-generator-minimal-usable-design.md)
- [design generator type/import resolution design](specs/2026-04-10-cap4k-design-generator-type-import-resolution-design.md)
- [design generator auto-import design](specs/2026-04-11-cap4k-design-generator-auto-import-design.md)
- [design generator template helpers design](specs/2026-04-11-cap4k-design-generator-template-helpers-design.md)
- [design generator default value design](specs/2026-04-12-cap4k-design-generator-default-value-design.md)
- [design generator use helper design](specs/2026-04-12-cap4k-design-generator-use-helper-design.md)
- [design template migration / helper adoption design](specs/2026-04-13-cap4k-design-template-migration-helper-adoption-design.md)
- [representative design template / override migration design](specs/2026-04-14-cap4k-representative-design-template-override-migration-design.md)
- [design query-handler family migration design](specs/2026-04-14-cap4k-design-query-handler-family-migration-design.md)
- [design client family migration design](specs/2026-04-14-cap4k-design-client-family-migration-design.md)
- [design validator family migration design](specs/2026-04-15-cap4k-design-validator-family-migration-design.md)
- [design api payload migration design](specs/2026-04-15-cap4k-design-api-payload-migration-design.md)
- [design domain event family migration design](specs/2026-04-15-cap4k-design-domain-event-family-migration-design.md)
- [design generator compile-level / integrated verification hardening design](specs/2026-04-15-cap4k-design-generator-compile-level-integrated-verification-hardening-design.md)

### Phase C: Bootstrap Capability Mainline

Completed:

- bootstrap / arch-template migration
- bootstrap generated-project verification hardening

Status:

- the bounded bootstrap capability line is complete through generated-project verification hardening
- bootstrap now exists as a separate capability with its own DSL, tasks, preset, bounded slots, renderer flow, functional closure, and generated-project verification gate

Traceability:

- [bootstrap / arch-template migration design](specs/2026-04-16-cap4k-bootstrap-arch-template-migration-design.md)
- [bootstrap generated-project verification hardening design](specs/2026-04-16-cap4k-bootstrap-generated-project-verification-hardening-design.md)

### Phase D: Cross-Generator Reference Boundary Mainline

Completed:

- cross-generator type-reference parity
- aggregate factory / specification parity
- aggregate wrapper parity
- aggregate unique-constraint family parity
- aggregate enum / enum-translation parity

Status:

- the cross-generator reference boundary line is complete through aggregate enum / enum-translation parity
- framework-facing type-reference work remains explicitly separated from broader aggregate parity and exploratory full-replacement gaps, and the aggregate-side consumer line now has planner, renderer, functional, and compile closure through bounded enum ownership and translation output

Traceability:

- [cross-generator type-reference parity design](specs/2026-04-17-cap4k-cross-generator-type-reference-parity-design.md)
- [aggregate factory / specification parity design](specs/2026-04-17-cap4k-aggregate-factory-specification-parity-design.md)
- [aggregate wrapper parity design](specs/2026-04-17-cap4k-aggregate-wrapper-parity-design.md)
- [aggregate unique-constraint family parity design](specs/2026-04-17-cap4k-aggregate-unique-constraint-family-parity-design.md)
- [aggregate enum / enum-translation parity design](specs/2026-04-17-cap4k-aggregate-enum-translation-parity-design.md)

### Phase E: Aggregate Relation Semantics Mainline

Completed:

- aggregate relation parity
- aggregate relation-side JPA control parity

Status:

- the bounded aggregate relation line is complete through relation-side JPA control parity
- aggregate-side relation work now has bounded source carriage, canonical inference, planner and renderer consumption, functional generation coverage, compile verification, and first-slice relation-side JPA control for `ONE_TO_MANY`, `MANY_TO_ONE`, and `ONE_TO_ONE`, while advanced relation forms remain explicitly separated

Traceability:

- [aggregate relation parity design](specs/2026-04-18-cap4k-aggregate-relation-parity-design.md)
- [aggregate relation-side JPA control parity design](specs/2026-04-20-cap4k-aggregate-relation-side-jpa-control-parity-design.md)

### Phase F: Aggregate Persistence Control Mainline

Completed:

- aggregate JPA annotation fine-grained control parity
- aggregate persistence field-behavior parity
- aggregate provider-specific persistence parity

Status:

- the bounded aggregate persistence-control line is complete through first-slice provider-specific persistence parity
- aggregate-side persistence control now has bounded source carriage, canonical enrichment, planner and renderer consumption, functional generation coverage, and compile verification for entity/table/column baseline JPA output, explicit field-behavior control, and first-slice provider-specific entity behavior without reopening relation-side ownership or full provider-specific recovery

Traceability:

- [aggregate JPA annotation fine-grained control parity design](specs/2026-04-19-cap4k-aggregate-jpa-annotation-fine-grained-control-parity-design.md)
- [aggregate persistence field-behavior parity design](specs/2026-04-19-cap4k-aggregate-persistence-field-behavior-parity-design.md)
- [aggregate provider-specific persistence parity design](specs/2026-04-19-cap4k-aggregate-provider-specific-persistence-parity-design.md)

## Current Mainline Contract

These points remain in force:

- `use()` is design-template-only
- `use()` only accepts explicit FQN strings
- `use()` is only for explicit imports, not type resolution
- `imports()` remains the output path for import lines
- `defaultValue` in render models is Kotlin-ready, not raw source text
- short-name handling remains conservative
- explicit FQN and symbol identity remain the truth source

## Current Next Mainline Slice

The original design-generator quality mainline is complete, and the bounded bootstrap capability line is complete through generated-project verification hardening.

The cross-generator reference boundary line is complete through aggregate enum / enum-translation parity.

The bounded aggregate relation line is complete through relation-side JPA control parity.

The bounded aggregate persistence-control line is complete through first-slice provider-specific persistence parity.

The next explicit framework slice is:

- aggregate advanced relation forms parity

Scope:

- build on the now-stable aggregate relation semantics and relation-side JPA control line rather than reopening relation inference from scratch
- keep the work inside the existing aggregate generator and aggregate-side rendering contract
- focus on the advanced relation forms that were intentionally excluded from the bounded relation and relation-side JPA slices
- recover bounded ownership-aware advanced relation behavior such as `mappedBy`, `@JoinTable`, `ManyToMany`, inverse or read-only join-column control like `insertable/updatable = false`, and closely related relation-form metadata only where the aggregate relation model can support it cleanly
- prove the advanced relation forms through planner, renderer, and bounded functional or compile verification
- keep the slice bounded to advanced relation forms parity, not full aggregate-side completion or relation re-architecture

Non-goals:

- do not restore mutable shared runtime type maps between generators
- do not widen this slice into relation re-architecture, user-code-preservation parity, or general source-semantic recovery
- do not broaden bootstrap beyond the current bounded contract
- do not silently expand into full provider-specific recovery, broader persistence recovery, or full aggregate-side parity beyond bounded advanced relation forms work
- do not turn exploratory parity notes into a general rewrite of the current pipeline architecture

## Bootstrap Decision

The bootstrap direction is:

- bootstrap should become a separate framework capability
- the public bootstrap contract should use slot-based extension
- users may add arbitrary numbers of files through bounded slots
- the public contract should not restore arbitrary insertion into any architecture-tree node
- the old `archTemplate` JSON remains migration input or reference material, not the future public runtime contract

## Non-Default Work

The following remain separate from the default mainline path:

- real-project integration boundary work
- project-specific unblock work
- broader bootstrap flexibility beyond slot-based extension

## Support Track Docs

Concrete support-track references:

- [real project local integration](specs/2026-04-09-cap4k-only-danmuku-local-integration-design.md)
- [real project integration boundaries](specs/2026-04-11-cap4k-real-project-integration-boundaries-design.md)
- [project type registry](specs/2026-04-12-cap4k-project-type-registry-design.md)

## Continue Rules

- If the user says only "continue", continue the original mainline.
- Do not default into an integration workaround.
- Do not silently merge bootstrap migration into design-template migration.
- Only promote project-specific patterns into framework contract when explicitly approved.
