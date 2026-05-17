# Cap4k Business Authoring Guidance Redesign Design

Date: 2026-05-11

Status: Proposed

Scope: redesign the `#17` human authoring docs and AI authoring skill after the 2026-05-11 capability analysis, so both surfaces target business projects built with cap4k instead of cap4k framework maintenance.

Out of scope:

- runtime implementation changes
- generator implementation changes
- reference project changes
- issue lifecycle governance for downstream business projects
- cap4k framework contributor guidance
- skill architecture meta-rules as business author content
- English parity beyond keeping existing English entrypoints coherent

## Background

The first `#17-v1` docs/skill slice created a useful structure, but review found a role-boundary problem:

- the AI skill reads as if it is for cap4k framework authors in several places;
- `references/issue-lifecycle.md` controls issue behavior that belongs to cap4k project governance, not business-project authoring;
- gotchas such as runtime context bloat and thin shell drift are skill-maintenance rules, not things a business-project AI author should carry as domain guidance;
- tactical layering rules incorrectly state that application owns query/client/cli handlers as code locations, even though generated query/client handlers are physically adapter-side;
- command/query/client usage rules are too blunt and do not match the intended `Mediator` and process-orchestration model.

To avoid another rewrite based on partial memory, the repository now has internal analysis files under `docs/superpowers/analysis/`:

- `2026-05-11-cap4k-business-project-authoring-capability-map.md`
- `2026-05-11-cap4k-bootstrap-plugin-and-template-map.md`
- `2026-05-11-cap4k-generator-input-output-and-verification-map.md`
- `2026-05-11-cap4k-public-tactical-model-and-layering-map.md`
- `2026-05-11-cap4k-runtime-support-and-integration-map.md`
- `2026-05-11-cap4k-testing-analysis-and-flow-map.md`
- `2026-05-11-cap4k-extension-spi-addon-and-gap-map.md`

This redesign uses those files as source material for two reader-specific outputs.

## Readers

### Human Business Author

The human author uses `docs/public/authoring` to:

- make domain and architectural decisions;
- understand cap4k's default authoring path;
- review DDL/design/generator output;
- audit final behavior and code shape;
- decide whether a gap becomes a cap4k issue, downstream issue, or local project decision.

### AI Business Author

The AI author uses `skills/cap4k-authoring` to:

- help clarify domain flows before code;
- produce business-project DDL/design/spec/plan when needed;
- implement generated and handwritten cap4k project slices;
- run TDD, compile, generator, analysis, and focused smoke checks before human audit;
- report evidence and gaps.

The AI business author is not a cap4k framework maintainer unless the user explicitly changes scope.

## Goals

1. Make `docs/public/authoring` capable enough for human business authors to understand the end-to-end cap4k project authoring path.
2. Rebuild `skills/cap4k-authoring` as a self-contained business-project AI authoring skill.
3. Remove cap4k framework author material from the business-project skill.
4. Correct tactical layering rules:
   - domain owns domain model and behavior;
   - application owns use-case contracts, write-side command handling, orchestration, subscribers, validators, and business process intent;
   - adapter owns transport, persistence adapters, query handlers, client/cli handlers, controllers, and external bridges.
5. Teach `Mediator`, built-in repository, factory, UoW, lifecycle, specification, domain service, request, domain event, and integration event as one tactical model.
6. Teach generation from DB/design/enum/KSP/IR sources with generated-output ownership and verification.
7. Teach analysis output and testing as normal project-authoring steps.
8. Keep unsupported capabilities visible as gaps.

## Non-Goals

This slice does not:

- add `integration_event`, `value_object`, or `domain_service` design support;
- add lifecycle-recognition runtime fixes;
- add enum translation back into cap4k core;
- implement an addon;
- modify `cap4k-reference-content-studio`;
- define issue lifecycle rules for every business project that happens to use cap4k;
- force AI skill users to read public docs or example repositories during normal operation.

## Human Authoring Design

The public human docs should be reshaped around authoring capability, not around the history of how `#17` was implemented.

### Required public authoring capabilities

`docs/public/authoring` must explain:

- how to start a minimal runnable bootstrap project;
- how Gradle plugin configuration is organized;
- how template overrides and slots work;
- how DB input works, including DB custom annotations and uniqueness conventions;
- how design input works, including supported tags and unsupported tags;
- how to inspect `cap4kPlan`, `cap4kGenerate`, and `cap4kGenerateSources`;
- how to distinguish active generated output, checked-in skeletons, copied snapshots, and handwritten code;
- how the public tactical model works;
- how commands, queries, client/cli handlers, subscribers, jobs, repositories, factories, domain services, UoW, lifecycle hooks, specifications, and events fit by layer;
- how integration events are published, consumed, and shared as contracts;
- how to use `.http` files or smoke tests when OpenAPI/Swagger is not part of the goal;
- how to run analysis through `cap4kAnalysisPlan` and `cap4kAnalysisGenerate`;
- how to design a DDD flow document before implementation when a flow spans many classes;
- how to write useful tests that match the testing contract.

### Proposed public doc changes

Add focused pages:

```text
docs/public/authoring/project-authoring-workflow.md
docs/public/authoring/tactical-model.md
docs/public/authoring/generator/input-sources.md
docs/public/authoring/generator/addons-and-spi.md
```

Update existing pages to route to these pages instead of duplicating all details:

```text
docs/public/authoring/index.md
docs/public/authoring/getting-started.md
docs/public/authoring/framework-positioning.md
docs/public/authoring/domain.md
docs/public/authoring/application.md
docs/public/authoring/adapter.md
docs/public/authoring/generation-boundaries.md
docs/public/authoring/testing-contract.md
docs/public/authoring/generator/index.md
docs/public/authoring/generator/bootstrap.md
docs/public/authoring/generator/code-generation.md
docs/public/authoring/generator/code-analysis.md
```

Existing English entrypoints may receive minimal navigation fixes, but full English parity is not required in this slice.

## AI Skill Design

The AI skill should be for AI authors of business projects using cap4k.

### Content to remove from the business-project skill

Remove or move out of `skills/cap4k-authoring`:

- cap4k framework issue lifecycle governance;
- skill architecture meta gotchas such as "Runtime Context Bloat" and "Thin Shell Drift";
- rationalizations about how to structure skills;
- framework-author instructions that explain how to maintain cap4k itself;
- any rule that treats the skill as a public-doc link farm;
- any rule that says application physically owns query/client/cli handlers.

The workspace-level issue-governance skill can still govern cap4k issue updates when the current task is about cap4k repository work. That is separate from the business-project authoring skill.

### Required skill capabilities

`skills/cap4k-authoring` should teach AI business authors:

- how to discuss with the user and derive domain model, design JSON, and DDL;
- how to write user-readable technical方案 documents before complex implementation;
- how to create and verify a minimal bootstrap-generated project;
- how to configure generator sources, artifacts, slots, template overrides, and conflict policies;
- how to use DB input, DB annotations, enum manifest, and uniqueness conventions;
- how to use design input and recognize unsupported design tags;
- how to verify generated output and copied snapshots;
- how to implement tactical code with `Mediator`, repository, factory, domain service, UoW, lifecycle, specifications, commands, queries, client/cli handlers, subscribers, jobs, and events;
- how to model integration event publishing/consumption without coupling sibling services to application modules;
- how to run tests and analysis.

### Proposed skill structure

```text
skills/cap4k-authoring/
  SKILL.md
  rules/
    role-boundary.md
    layering-and-tactical-model.md
    generator-ownership.md
    runtime-tactical-contract.md
    testing-and-verification.md
  workflows/
    clarify-domain-design.md
    bootstrap-minimal-project.md
    generate-from-db.md
    generate-from-design.md
    implement-project-slice.md
    review-generated-output.md
    run-analysis-and-flow-review.md
  references/
    capability-index.md
    known-gaps.md
    gotchas.md
```

`SKILL.md` remains a concise router. Formal rules remain under `skills/cap4k-authoring`, and harness entries remain discovery shells with inline routing tables only.

## Known Gaps To Preserve

Keep these as explicit gaps, not implied framework support:

- `integration_event` design source support is not implemented;
- `value_object` design source support is not implemented;
- `domain_service` design source support is not implemented;
- lifecycle recognition has known limitations;
- enum translation is addon-owned, not core aggregate DSL;
- integration event HTTP-JPA needs minimal framework table DDL when used in local examples;
- addon authoring guidance is separate from business-project addon usage.

## Validation

The implementation should verify:

1. only docs, skill, and thin shell files changed;
2. public authoring docs contain the new capability routes;
3. `skills/cap4k-authoring` no longer contains business-irrelevant issue lifecycle or skill-meta rule content;
4. layering text says query/client handlers are adapter-side physical handlers while application owns request contracts and orchestration intent;
5. command/query/client/cli rules match the `Mediator` model from the analysis files;
6. generated-output ownership distinguishes build output, checked-in skeletons, snapshots, and handwritten code;
7. known gaps remain visible;
8. `git diff --check` passes.

## Acceptance Criteria

- Human authoring docs can guide a business project from modeling discussion through generation, implementation, testing, analysis, and audit.
- AI authoring skill is clearly for AI authors of business projects using cap4k.
- The skill does not contain cap4k framework issue-governance rules or skill-architecture meta rules as business authoring content.
- The skill and public docs agree on tactical layering and generated-output ownership.
- Unsupported generator/runtime capabilities are called gaps.
- No runtime, generator, reference project, or downstream project code is changed.
