# Cap4k Generator Input Contract And Skill Self-Containment Design

## Context

The documentation-system redesign PR already introduced the public docs and skill runtime structure for cap4k authoring. Review exposed a gap in the generator-input path: an agent can be asked to write cap4k generator inputs, but the current skill package does not yet carry enough self-contained knowledge to do that reliably after installation into an arbitrary user project.

The core problem is not that the agent needs to understand cap4k source code. The agent needs a compact, current, explicit data contract for the inputs consumed by the official cap4k generator:

- `design/design.json`
- DB/schema DDL comment annotations
- enum manifest
- value-object manifest
- Gradle extension input surfaces

The skill must make those contracts usable without depending on `docs/public`, analysis outputs, a reference project, or local repository paths. Public docs remain useful for human readers, but they are not a runtime dependency of an installed skill.

## Goals

- Make `cap4k-generator-inputs` self-contained enough for agent authoring in arbitrary user projects.
- Keep skill runtime pure: current cap4k rules only, no future plans, no historical drift vocabulary, no implementation-time slogans.
- Add public reference pages for human readers that stay aligned with the same source facts.
- Add a repo-level offline validation script that gives deterministic, repairable feedback for generated input files.
- Keep validation static and conservative. It is a feedback layer, not a replacement for `cap4kPlan`.

## Non-Goals

- Do not change generator behavior in this PR.
- Do not implement drawing-board recovery as a first-class generator input capability. That work is tracked separately in issue #102.
- Do not add machine-readable `.schema.json` files.
- Do not add MCP service integration.
- Do not connect to databases from the first validation script.
- Do not run Gradle, generate code, compile, or test as part of the validation script.
- Do not make skills depend on public docs, analysis output, or reference projects.

## Implementation Preconditions

Before implementing the skill changes, the implementer must read `skill-based-architecture` and apply its architecture constraints:

- keep `SKILL.md` thin and route-focused;
- use `rules/`, `workflows/`, and `references/` for their intended purposes;
- preserve progressive disclosure and token efficiency;
- avoid broad Always Read expansion;
- keep high-cost pitfalls activated on the relevant workflow path.

This is an implementation constraint for the PR author. It must not be written into cap4k runtime skill text as a user-facing rule. In particular, do not write rules like `Keep runtime guidance self-contained.` inside cap4k skills. That phrase is an authoring constraint, not something an agent using cap4k needs to read.

## Skill Architecture

`skills/cap4k-generator-inputs/SKILL.md` remains a thin entry point. Its Always Read list stays limited to:

1. `../shared/rules/generator-input-source-of-truth.md`
2. `../shared/workflows/skeleton-generation-gate.md`
3. `workflows/project-generator-inputs.md`

Do not add the full design, DB, or manifest contracts to Always Read. The workflow loads them on demand based on the input surface being edited.

`workflows/project-generator-inputs.md` becomes the main authoring procedure:

1. Read the approved technical design contract.
2. Scan the current workspace for mature project inputs and historical iteration materials such as existing `design/*.json`, `schema.sql`, manifests, plan evidence, and committed authoring materials. If they exist, read relevant examples first. If they do not exist, continue without blocking.
3. Identify the input surface needed by the design.
4. Load the specific self-contained contract reference for that surface.
5. Write or update the input.
6. If `scripts/validate-cap4k-generator-inputs.py` exists at the repository root, run it after editing generator inputs. If it is absent, disclose that the validation script is not present and perform a static self-check using the skill contract references.
7. Do not claim generator inputs are ready when validation reports `ERROR`.

The skill reference layout should be:

- `references/input-surfaces.md`: compact index of supported input surfaces and ownership boundaries.
- `references/design-json-contract.md`: current official `design.json` tags, fields, and combination rules.
- `references/db-schema-annotations.md`: DB comment annotation closed set and conflict rules.
- `references/manifest-contracts.md`: minimum enum and value-object manifest formats.

## Skill Runtime Content Rules

Skill runtime text must describe current cap4k behavior positively. It should not inject historical or future concepts into the agent context.

Allowed runtime content:

- supported `design.json` tag closed set;
- current source generation versus analysis generation boundaries;
- current DB comment annotation closed set;
- current ownership rules for generated skeletons;
- current validation workflow and stop conditions.

Disallowed runtime content:

- old KSP terminology unless the current user task explicitly introduced it;
- removed analysis switches or stale option names;
- future drawing-board recovery plans;
- issue links as operational rules;
- internal implementation slogans such as `Keep runtime guidance self-contained.`

Historical drift checks belong in scripts or review-only checks, not in ordinary generator-input authoring paths.

## Shared Rule Cleanup

Remove every runtime occurrence of `Keep runtime guidance self-contained.` from shared rules. It leaked an implementation decision into user-facing skill guidance.

Update `generator-input-source-of-truth.md`:

- Treat DB/schema, design JSON, manifests, Gradle settings, addons/options, and template override decisions as generator inputs.
- Treat `plan.json` and generated output as generated evidence.
- Treat analysis outputs as evidence, not ordinary source-generation inputs.
- Prevent ordinary implementation from treating analysis output as a normal input skeleton.

Do not write future drawing-board recovery support into this rule. The current official boundary remains: analysis output is evidence unless the user manually transforms compatible content into a supported generator input surface.

Update `generated-skeleton-ownership.md`:

- If cap4k generator inputs can express a structure, a missing skeleton returns to generator input or plan review.
- Do not create a parallel handwritten command, query, subscriber, client, saga, or similar structure merely to keep implementation moving.
- `plan.json`, analysis outputs, drawing-board outputs, and generated snapshots are evidence, not handwritten business logic surfaces.
- Replace internal shorthand such as `parallel structure is fine if names are similar` with direct current rules.

Remove `skills/shared/references/drift-gotchas.md` from ordinary runtime routes. Move still-valid current boundaries into positive rules or references. Keep historical term scanning in review-only or script checks.

## Design JSON Contract

The skill contract must include the current official normal tag set:

- `command`
- `query`
- `client`
- `api_payload`
- `domain_event`
- `integration_event`
- `domain_service`
- `saga`

It must also include these key rules:

- top-level document is an array;
- every entry is an object;
- `tag` and `name` are required nonblank strings;
- `package` is required except for `domain_event`;
- `description`, `aggregates`, `fields`, `resultFields`, `eventName`, `persist`, and `artifacts` are the supported public fields;
- `resultFields` are allowed only on `query`, `client`, and `api_payload`;
- `integration_event` must declare `eventName`;
- `eventName` is allowed only on `domain_event` and `integration_event`;
- `persist` is allowed only on `domain_event`;
- removed fields such as `desc`, `requestFields`, `responseFields`, `traits`, `role`, `scope`, and `entity` are rejected;
- `domain_event` field name `entity` is reserved;
- field type must use explicit type names and must not use `self`.

Drawing-board JSON can resemble design JSON, but it is not automatically valid `designJson.files` input. A drawing-board fragment can only be registered as design input when it satisfies the official design-json contract. Known incompatible cases, such as `command.resultFields`, must be reported as `ERROR` with `RECOVERY_HINT`; they must not be silently normalized.

## DB Annotation Contract

The skill and public reference must document the current DB comment annotation closed set.

Table comment annotations:

- `@Parent=<table>` / `@P=<table>`
- `@AggregateRoot=<bool>` / `@Root=<bool>` / `@R=<bool>`
- `@ValueObject` / `@VO`
- `@Ignore` / `@I`
- `@DynamicInsert=<bool>`
- `@DynamicUpdate=<bool>`

Column comment annotations:

- `@T=<TypeName>` / `@TYPE=<TypeName>`
- `@E=<items>` / `@ENUM=<items>`, requiring `@T`
- `@RefId=<TypeName>`
- `@Deleted`
- `@Version`
- `@GeneratedValue=identity` / `@GeneratedValue=database-identity`
- `@Managed`
- `@Inherited`
- `@Reference=<table>` / `@Ref=<table>`
- `@Relation=<type>` / `@Rel=<type>`
- `@Lazy=<bool>` / `@L=<bool>`
- `@Count=<value>` / `@C=<value>`
- `@RefAggregate=<AggregateName>`

The reference must include conflict and dependency rules, including:

- `@Parent` cannot combine with `@AggregateRoot=true`;
- presence annotations do not accept explicit values;
- boolean annotations use strict boolean values;
- `@Relation`, `@Lazy`, and `@Count` require `@Reference` or `@Ref` on the same column comment;
- `@RefAggregate` conflicts with `@Reference` / `@Ref`;
- `@RefAggregate` conflicts with `@RefId`;
- unsupported or removed annotations must be reported clearly.

## Public Docs

Add:

- `docs/public/reference/db-schema-annotations.md`
- `docs/public/reference/generator-input-validation.md`

Update:

- `docs/public/reference/design-json.md`
- `docs/public/reference/analysis-outputs.md`
- `docs/public/reference/index.md`
- `docs/public/generator/inputs-and-sources.md`

The public docs and skill references are synchronized from current source facts, but neither is the source of authority for the other.

Public docs should be more explanatory and example-oriented. Skill references should be shorter, closed-set oriented, and optimized for agent action.

`analysis-outputs.md` must preserve the current boundary:

- `cap4kAnalysisGenerate` is not source generation;
- drawing-board outputs are analysis evidence by default;
- manually copied drawing-board content must satisfy the design-json contract before it can be registered through `sources.designJson.files`;
- arbitrary analysis outputs are not ordinary source-generation input skeletons.

## Validation Script

Add a repository-level script:

```powershell
python scripts/validate-cap4k-generator-inputs.py `
  --design design/design.json `
  --schema schema.sql `
  --enum design/enums.json `
  --value-object design/value-objects.json
```

The first version supports only offline file validation:

- no database connections;
- no Gradle execution;
- no code generation;
- no compilation or tests;
- Windows and Python-first implementation is acceptable.

Arguments:

- `--design`: repeatable; accepts normal design JSON and drawing-board-compatible JSON fragments.
- `--schema`: repeatable; accepts DDL SQL files.
- `--enum`: repeatable; accepts enum manifests.
- `--value-object`: repeatable; accepts value-object manifests.
- `--json`: optional structured output.

Default output is human-readable text. Structured JSON output contains issue objects with fields such as:

- `level`
- `file`
- `path`
- `message`
- `hint`

Levels:

- `ERROR`: current official input contract rejects the file or field. Exit nonzero.
- `WARN`: suspicious or incomplete static evidence. Does not fail by itself.
- `RECOVERY_HINT`: migration context for a related error, especially drawing-board or historical input fragments. Does not fail by itself.

The script must not automatically normalize or delete fields.

DDL comment parsing supports:

- MySQL inline `COMMENT '...'` table and column comments;
- PostgreSQL/H2 `COMMENT ON TABLE ... IS '...'` and `COMMENT ON COLUMN ... IS '...'`.

If the script sees an annotation but cannot reliably determine whether it belongs to a table or column context, it reports `WARN` or `UNKNOWN_CONTEXT` rather than pretending to know.

## Future Work

Drawing-board recovery as a first-class generator input path is tracked by issue #102. This design intentionally does not implement or document that as a current official capability in runtime skills.

An MCP server may later wrap schema discovery, examples, and validation tools, but it is not the root solution. The root solution is a stable input contract plus deterministic validation feedback.

## Verification

Implementation should be verified with static checks only unless the user explicitly permits broader execution:

- read changed skill routes and ensure `SKILL.md` remains thin;
- confirm ordinary routes no longer load `drift-gotchas.md`;
- confirm historical drift terms are not injected into runtime skill references;
- inspect public docs links and reference index;
- run the Python validation script against small valid and invalid fixtures if local script execution is allowed;
- run existing skill check scripts only if they are static and allowed by the current workspace rules.
