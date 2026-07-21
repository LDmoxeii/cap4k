# Cap4k Generator Input Contract And Skill Self-Containment Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Make cap4k generator-input authoring self-contained for installed skills, align public reference docs with current input contracts, and add an offline validation script that gives deterministic repair feedback.

**Architecture:** Keep `skills/cap4k-generator-inputs/SKILL.md` as a thin route entry with the existing three Always Read files. Put agent-operational contracts in `skills/cap4k-generator-inputs/references/`, human-facing explanation in `docs/public/reference/`, shared invariants in `skills/shared/rules/`, and static file validation in the repo-root `scripts/validate-cap4k-generator-inputs.py`.

**Tech Stack:** Markdown skills/docs, PowerShell static skill checks, Python 3 standard library only for the new validator. Do not run Gradle, source generation, compilation, tests, installs, database connections, or application runtime checks unless the user explicitly permits them.

---

## Required Context For Every Worker

Every implementer or reviewer must read these before editing:

1. `C:\Users\LD_moxeii\.codex\skills\skill-based-architecture\SKILL.md`
2. `docs/superpowers/specs/2026-06-05-cap4k-generator-input-contract-and-skill-self-containment-design.md`
3. The current files listed in the task they are executing.

Every worker must state its write scope before editing. Do not edit outside that scope.

## Constraints

- Skill runtime must not depend on `docs/public`, analysis outputs, reference projects, GitHub issues, Context7, or cap4k source code.
- The workflow may tell agents to scan the current user workspace for mature generator inputs and historical iteration materials, but must not assume those files exist.
- Do not add machine-readable `.schema.json` files.
- Do not add MCP integration.
- Do not implement drawing-board recovery as a generator feature.
- Do not put future plans, issue links, historical stale terms, or authoring slogans into ordinary runtime skill paths.
- Keep `skills/cap4k-generator-inputs/SKILL.md` thin. Do not expand its Always Read list beyond the current three entries.
- Do not run Gradle, source generation, compilation, tests, installs, database connections, application runtime checks, or Python script execution unless the user explicitly permits that class of command in the execution turn.
- Do not modify git index or create commits unless the user explicitly requests commits in the execution turn.
## File Structure

Create:

- `skills/cap4k-generator-inputs/references/design-json-contract.md`: compact current `design/design.json` contract for agents.
- `skills/cap4k-generator-inputs/references/db-schema-annotations.md`: compact current DB comment annotation contract for agents.
- `skills/cap4k-generator-inputs/references/manifest-contracts.md`: compact enum and value-object manifest contracts for agents.
- `docs/public/reference/db-schema-annotations.md`: explanatory DB annotation reference for human readers.
- `docs/public/reference/generator-input-validation.md`: human-facing validator usage and output reference.
- `scripts/validate-cap4k-generator-inputs.py`: offline static validator for design JSON, DB/schema DDL comments, enum manifests, and value-object manifests.

Modify:

- `skills/cap4k-generator-inputs/SKILL.md`: keep Always Read exactly as-is; update Common Routes only if needed to point to the new on-demand references.
- `skills/cap4k-generator-inputs/workflows/project-generator-inputs.md`: make the workflow scan project-local examples/history, load only the relevant contract reference, and run the validator when present.
- `skills/cap4k-generator-inputs/references/input-surfaces.md`: keep as the input-surface index; add links to the new contract references and clarify analysis evidence boundary.
- `skills/shared/rules/generator-input-source-of-truth.md`: remove the implementation slogan and rewrite source/evidence boundaries positively.
- `skills/shared/rules/generated-skeleton-ownership.md`: remove the implementation slogan and rewrite missing-skeleton and generated-evidence ownership rules positively.
- `skills/shared/rules/cap4k-positioning.md`, `skills/shared/rules/layer-and-runtime-boundaries.md`, `skills/shared/rules/naming-layout-and-testing.md`, `skills/shared/rules/verification-claim-policy.md`: remove the `Keep runtime guidance self-contained.` runtime line.
- `skills/cap4k-authoring/routing.yaml`: remove ordinary task routes that load `../shared/references/drift-gotchas.md`.
- `skills/scripts/checks/structure.ps1`, `skills/scripts/checks/routing.ps1`, `skills/scripts/checks/skeleton-gate-refs.ps1`, `skills/scripts/checks/stale-terms.ps1`: adjust review-only checks so ordinary runtime routes no longer require `drift-gotchas.md`, while stale/historical text remains blocked by checks.
- `docs/public/reference/design-json.md`: align with the current official contract and drawing-board compatibility boundary.
- `docs/public/reference/analysis-outputs.md`: clarify analysis evidence is not ordinary source-generation input.
- `docs/public/reference/index.md`: add DB annotation and validation pages.
- `docs/public/generator/inputs-and-sources.md`: link the new references and clarify static validation feedback.
- `docs/public/reference/enum-manifest.md`: tighten contract wording around `aggregates`, duplicates, and removed translation flags.
- `docs/public/reference/value-object-manifest.md`: tighten contract wording around `aggregates`, `storage`, duplicates, and removed fields.

Reference only:

- `docs/superpowers/specs/2026-06-05-cap4k-generator-input-contract-and-skill-self-containment-design.md`
- `cap4k-plugin-pipeline-source-design-json/src/main/kotlin/com/only4/cap4k/plugin/pipeline/source/designjson/DesignJsonSourceProvider.kt`
- `cap4k-plugin-pipeline-source-design-json/src/test/kotlin/com/only4/cap4k/plugin/pipeline/source/designjson/DesignJsonSourceProviderTest.kt`
- `cap4k-plugin-pipeline-source-db/src/main/kotlin/com/only4/cap4k/plugin/pipeline/source/db/DbTableAnnotationParser.kt`
- `cap4k-plugin-pipeline-source-db/src/main/kotlin/com/only4/cap4k/plugin/pipeline/source/db/DbColumnAnnotationParser.kt`
- `cap4k-plugin-pipeline-source-db/src/main/kotlin/com/only4/cap4k/plugin/pipeline/source/db/DbRelationAnnotationParser.kt`
- `cap4k-plugin-pipeline-source-db/src/test/kotlin/com/only4/cap4k/plugin/pipeline/source/db/DbTableAnnotationParserTest.kt`
- `cap4k-plugin-pipeline-source-db/src/test/kotlin/com/only4/cap4k/plugin/pipeline/source/db/DbColumnAnnotationParserTest.kt`
- `cap4k-plugin-pipeline-source-db/src/test/kotlin/com/only4/cap4k/plugin/pipeline/source/db/DbRelationAnnotationParserTest.kt`
- `cap4k-plugin-pipeline-source-enum-manifest/src/main/kotlin/com/only4/cap4k/plugin/pipeline/source/enummanifest/EnumManifestSourceProvider.kt`
- `cap4k-plugin-pipeline-source-enum-manifest/src/test/kotlin/com/only4/cap4k/plugin/pipeline/source/enummanifest/EnumManifestSourceProviderTest.kt`
- `cap4k-plugin-pipeline-source-value-object-manifest/src/main/kotlin/com/only4/cap4k/plugin/pipeline/source/valueobject/ValueObjectManifestSourceProvider.kt`
- `cap4k-plugin-pipeline-source-value-object-manifest/src/test/kotlin/com/only4/cap4k/plugin/pipeline/source/valueobject/ValueObjectManifestSourceProviderTest.kt`

---

## Static Verification Commands Used Throughout

Use these static commands after relevant tasks when the execution turn allows local static inspection commands:

```powershell
git status --short -uall
git diff --check -- skills docs scripts docs/superpowers/plans/2026-06-05-cap4k-generator-input-contract-and-skill-self-containment.md
rg -n "T[B]D|T[O]DO|PLACEH[O]LDER|F[I]XME|historical[-]decision|historical[ ]decision|Continue[ ]Brainstorming|Open[ ]Decisions|implement[ ]later|fill[ ]in[ ]details|similar[ ]to" skills docs scripts
```

Expected when permitted:

```text
git status shows only files expected for the current task
git diff --check exits 0
rg exits 1 with no matches
```

When static skill validation is relevant and the execution turn permits running repository static check scripts, run:

```powershell
powershell -NoProfile -ExecutionPolicy Bypass -File skills/scripts/validate-cap4k-skills.ps1
```

Expected when permitted:

```text
cap4k skill validation passed.
```

If a command is not permitted by the active workspace policy or user instruction, do not run it. Perform the closest static read/search review and disclose the skipped command.
### Task 1: Add Self-Contained Skill Contract References

**Files:**

- Create: `skills/cap4k-generator-inputs/references/design-json-contract.md`
- Create: `skills/cap4k-generator-inputs/references/db-schema-annotations.md`
- Create: `skills/cap4k-generator-inputs/references/manifest-contracts.md`
- Modify: `skills/cap4k-generator-inputs/references/input-surfaces.md`
- Reference: `docs/superpowers/specs/2026-06-05-cap4k-generator-input-contract-and-skill-self-containment-design.md`
- Reference: source provider and parser files listed in the File Structure section

- [ ] **Step 1: Re-read the implementation constraints**

  Read:

  ```powershell
  Get-Content -Path "docs/superpowers/specs/2026-06-05-cap4k-generator-input-contract-and-skill-self-containment-design.md" -Raw
  Get-Content -Path "skills/cap4k-generator-inputs/SKILL.md" -Raw
  Get-Content -Path "skills/cap4k-generator-inputs/references/input-surfaces.md" -Raw
  ```

  Expected: the spec says skill runtime is self-contained, no `.schema.json`, no MCP, no generator behavior changes, and `SKILL.md` Always Read remains three entries.

- [ ] **Step 2: Create the design-json contract reference**

  Write `skills/cap4k-generator-inputs/references/design-json-contract.md` with this structure:

  ```markdown
  # Design JSON Contract

  Use this when editing files registered through `sources.designJson.files`, commonly `design/design.json`.

  ## Shape

  - The root document is a JSON array.
  - Every array item is an object.
  - `tag` and `name` are required nonblank strings.
  - `package` is required for every tag except `domain_event`.

  ## Supported Tags

  - `command`
  - `query`
  - `client`
  - `api_payload`
  - `domain_event`
  - `integration_event`
  - `domain_service`
  - `saga`

  ## Supported Public Fields

  - `tag`
  - `name`
  - `package`
  - `description`
  - `aggregates`
  - `fields`
  - `resultFields`
  - `eventName`
  - `persist`
  - `artifacts`

  ## Combination Rules

  - `resultFields` is allowed only on `query`, `client`, and `api_payload`.
  - `integration_event` must declare `eventName`.
  - `eventName` is allowed only on `domain_event` and `integration_event`.
  - `persist` is allowed only on `domain_event`.
  - Field types must use explicit type names and must not use `self`.
  - A `domain_event` field named `entity` is reserved and must not be written manually.

  ## Removed Or Rejected Fields

  Do not use `desc`, `requestFields`, `responseFields`, `traits`, `role`, `scope`, or `entity` as design entry fields.

  ## Drawing-Board Boundary

  Drawing-board JSON can resemble design JSON, but it is not automatically valid `sources.designJson.files` input. A drawing-board fragment can be registered as design input only after it satisfies this contract. For example, `command.resultFields` is invalid because `resultFields` is not allowed on `command`.
  ```

  Expected: the file does not mention old KSP, future issue plans, MCP, or public docs as runtime dependencies.

- [ ] **Step 3: Create the DB schema annotation contract reference**

  Write `skills/cap4k-generator-inputs/references/db-schema-annotations.md` with this structure:

  ```markdown
  # DB Schema Annotation Contract

  Use this when editing DDL comments or reviewing DB/schema facts for cap4k source generation.

  ## Table Comment Annotations

  - `@Parent=<table>` / `@P=<table>`
  - `@AggregateRoot=<bool>` / `@Root=<bool>` / `@R=<bool>`
  - `@ValueObject` / `@VO`
  - `@Ignore` / `@I`
  - `@DynamicInsert=<bool>`
  - `@DynamicUpdate=<bool>`

  ## Column Comment Annotations

  - `@T=<TypeName>` / `@TYPE=<TypeName>`
  - `@E=<items>` / `@ENUM=<items>`
  - `@RefId=<TypeName>`
  - `@Deleted`
  - `@Version`
  - `@GeneratedValue=identity`
  - `@GeneratedValue=database-identity`
  - `@Managed`
  - `@Inherited`
  - `@Reference=<table>` / `@Ref=<table>`
  - `@Relation=<type>` / `@Rel=<type>`
  - `@Lazy=<bool>` / `@L=<bool>`
  - `@Count=<value>` / `@C=<value>`
  - `@RefAggregate=<AggregateName>`

  ## Rules

  - Presence annotations such as `@ValueObject`, `@VO`, `@Ignore`, `@I`, `@Deleted`, `@Version`, `@Managed`, and `@Inherited` do not take explicit values.
  - Boolean annotations use strict lowercase `true` or `false`.
  - `@Parent` / `@P` cannot be combined with `@AggregateRoot=true`, `@Root=true`, or `@R=true`.
  - `@E` / `@ENUM` requires `@T` / `@TYPE` on the same column comment.
  - `@Relation` / `@Rel`, `@Lazy` / `@L`, and `@Count` / `@C` require `@Reference` / `@Ref` on the same column comment.
  - `@Relation` / `@Rel` supports `MANY_TO_ONE`, `ONE_TO_ONE`, `1:1`, `*:1`, `MANYTOONE`, and `ONETOONE`.
  - `@RefAggregate` conflicts with `@Reference` / `@Ref`.
  - `@RefAggregate` conflicts with `@RefId`.

  ## Rejected Annotations

  - Table comments reject `@IdGenerator`, `@IG`, and `@SoftDeleteColumn`.
  - Column comments reject `@Exposed`, `@Insertable`, and `@Updatable`.
  ```

  Expected: this file provides the closed set needed by agents without asking them to inspect the DB source provider.

- [ ] **Step 4: Create the manifest contract reference**

  Write `skills/cap4k-generator-inputs/references/manifest-contracts.md` with this structure:

  ```markdown
  # Manifest Contracts

  Use this when editing enum or value-object manifest files configured through the `types` Gradle extension.

  ## Enum Manifest

  Configure enum manifests with `types.enumManifest.files`, not `sources.enumManifest`.

  Root shape: JSON array.

  Entry fields:

  - `name`: required string.
  - `package`: required string.
  - `items`: required array.
  - `aggregates`: optional string array; omitted or empty means shared; current support allows at most one owner.

  Item fields:

  - `value`: required integer.
  - `name`: required string.
  - `desc`: required string.

  Rules:

  - Duplicate shared enum names are invalid.
  - Duplicate enum names under the same aggregate owner are invalid.
  - `generateTranslation` is not a current enum manifest field.

  ## Value-Object Manifest

  Configure value-object manifests with `types.valueObjectManifest.files`, not `sources.valueObjectManifest`.

  Root shape: JSON array.

  Entry fields:

  - `name`: required string.
  - `package`: required string.
  - `aggregates`: optional string array; omitted or empty means shared; current support allows at most one owner.
  - `storage`: optional string; only `json` is supported, and omitted means `json`.
  - `description`: optional string.
  - `fields`: optional array.

  Field item fields:

  - `name`: required string.
  - `type`: required string.
  - `nullable`: optional boolean.
  - `defaultValue`: optional string.

  Rules:

  - Duplicate shared value-object names are invalid.
  - Duplicate value-object names under the same aggregate owner are invalid.
  - `scope` and `aggregate` are removed; use `aggregates`.
  ```

  Expected: no reference to `value_object` as a normal `design/design.json` input tag in this skill runtime reference.

- [ ] **Step 5: Update the input-surface index**

  Edit `skills/cap4k-generator-inputs/references/input-surfaces.md` so it remains an index, not a duplicate full contract:

  ```markdown
  ## Contract References

  - For `sources.designJson.files`, read `design-json-contract.md`.
  - For DB/schema DDL comments, read `db-schema-annotations.md`.
  - For `types.enumManifest.files` and `types.valueObjectManifest.files`, read `manifest-contracts.md`.
  - For Gradle extension fields, use the current project Gradle files and approved technical design; this skill does not provide a full Gradle DSL reference.

  ## Analysis Evidence Boundary

  Analysis outputs such as flow files and drawing-board files are observation evidence by default. They are not ordinary source-generation input skeletons. Manually copied drawing-board content must satisfy the relevant supported input contract before it can be registered as generator input.
  ```

  Expected: the file still lists supported generator input surfaces, and it does not depend on `docs/public`, analysis directories, reference projects, or GitHub issues.

- [ ] **Step 6: Static check the new references**

  Run:

  ```powershell
  rg -n "docs/public|GitHub issue|Context7|historical[-]decision|Open[ ]Decisions|Continue[ ]Brainstorming|future[ ]work|MCP|KSP|sources\.irAnalysis\.enabled|generators\.flow\.enabled|generators\.drawingBoard\.enabled" skills/cap4k-generator-inputs
  ```

  Expected: no output, except `MCP` may appear only if you have accidentally added it and must remove it from runtime skill files.

---

### Task 2: Update Generator Input Workflow And Shared Runtime Rules

**Files:**

- Modify: `skills/cap4k-generator-inputs/SKILL.md`
- Modify: `skills/cap4k-generator-inputs/workflows/project-generator-inputs.md`
- Modify: `skills/shared/rules/generator-input-source-of-truth.md`
- Modify: `skills/shared/rules/generated-skeleton-ownership.md`
- Modify: `skills/shared/rules/cap4k-positioning.md`
- Modify: `skills/shared/rules/layer-and-runtime-boundaries.md`
- Modify: `skills/shared/rules/naming-layout-and-testing.md`
- Modify: `skills/shared/rules/verification-claim-policy.md`
- Depends on: Task 1

- [ ] **Step 1: Verify the Always Read list before editing**

  Run:

  ```powershell
  Get-Content -Path "skills/cap4k-generator-inputs/SKILL.md" -Raw
  ```

  Expected Always Read list:

  ```markdown
  1. `../shared/rules/generator-input-source-of-truth.md`
  2. `../shared/workflows/skeleton-generation-gate.md`
  3. `workflows/project-generator-inputs.md`
  ```

  Do not add contract references to Always Read.

- [ ] **Step 2: Update Common Routes without expanding Always Read**

  If the Common Routes table needs more precision, use this shape in `skills/cap4k-generator-inputs/SKILL.md`:

  ```markdown
  | Task | Read | Workflow |
  |---|---|---|
  | Choose the generator input surface | `references/input-surfaces.md` | `workflows/project-generator-inputs.md` |
  | Update `design/design.json` or registered design JSON fragments | `references/input-surfaces.md`, then `references/design-json-contract.md` | `workflows/project-generator-inputs.md` |
  | Update DB/schema DDL comments | `references/input-surfaces.md`, then `references/db-schema-annotations.md` | `workflows/project-generator-inputs.md` |
  | Update enum or value-object manifests | `references/input-surfaces.md`, then `references/manifest-contracts.md` | `workflows/project-generator-inputs.md` |
  ```

  Expected: `SKILL.md` remains a route entry and does not become a full contract document.

- [ ] **Step 3: Rewrite the project generator input workflow**

  Replace `skills/cap4k-generator-inputs/workflows/project-generator-inputs.md` with a workflow that includes these operational steps:

  ```markdown
  # Project Generator Inputs

  1. Read the approved technical design contract.
  2. Scan the current workspace for mature project inputs and historical iteration materials relevant to the requested change, such as `design/*.json`, schema DDL files, manifests, Gradle extension blocks, committed plan evidence, and prior authoring materials. If they exist, read only the relevant examples before editing. If they do not exist, continue without blocking.
  3. Read `../references/input-surfaces.md`.
  4. Identify the required generator input surface.
  5. Load the specific contract reference for that surface:
     - `../references/design-json-contract.md` for `sources.designJson.files`.
     - `../references/db-schema-annotations.md` for DB/schema DDL comments.
     - `../references/manifest-contracts.md` for enum and value-object manifests.
  6. Update input only when the design supports the carrier, placement, ownership, and expected skeleton.
  7. Return to technical design if the carrier, package, owner, or expected skeleton is unclear.
  8. Return to `cap4k-tactical-modeling` if the business concept no longer maps cleanly to a cap4k carrier.
  9. If `scripts/validate-cap4k-generator-inputs.py` exists at the repository root, run it against the changed generator inputs. If it is absent, disclose that the validation script is not present and perform a static self-check with the loaded contract references.
  10. Do not claim generator inputs are ready while validation reports `ERROR`.

  ## Evidence To Record

  - Technical design section that authorizes the input.
  - Input file or setting that carries the generator fact.
  - Contract reference used for the input surface.
  - Expected plan item or skeleton family.
  - Validation result, or explicit disclosure that the validator script is absent.
  - Rollback target if the input cannot express the design.
  ```

  Expected: the workflow tells the agent to use project-local mature examples when present, while not making reference projects or docs public pages a skill dependency.

- [ ] **Step 4: Rewrite generator input source-of-truth positively**

  Update `skills/shared/rules/generator-input-source-of-truth.md` so `## Always True` includes:

  ```markdown
  - Treat DB/schema definitions as generator inputs.
  - Treat design JSON as a generator input.
  - Treat enum and value-object manifests as generator inputs.
  - Treat Gradle extension settings as generator inputs.
  - Treat addons, options, and template override decisions as generator inputs.
  - Treat `plan.json`, generated output, generated snapshots, flow output, and drawing-board output as generated evidence.
  - Treat analysis outputs as observation evidence by default, not ordinary source-generation input skeletons.
  - A manually copied analysis fragment becomes a generator input only when it is placed on a supported input surface and satisfies that surface's current contract.
  ```

  Keep `## Drift Checks`, but rewrite the analysis line to avoid quoting the old phrase as runtime guidance:

  ```markdown
  - Prevent treating analysis output as a supported generator input without transformation into a valid DB/schema, design JSON, manifest, Gradle setting, addon/option, or template decision.
  ```

  Expected: the phrase `Keep runtime guidance self-contained.` is removed.

- [ ] **Step 5: Rewrite generated skeleton ownership positively**

  Update `skills/shared/rules/generated-skeleton-ownership.md` so `## Always True` includes:

  ```markdown
  - Treat cap4k skeletons as generated by cap4k.
  - Put complex business logic inside approved author-owned surfaces of generated checked-in skeletons.
  - Exclude build-owned generated source from handwritten editing.
  - Exclude generated snapshots from handwritten editing.
  - If cap4k generator inputs can express a missing structure, return to generator input or plan review instead of creating a parallel handwritten skeleton.
  - Require an explicit technical design exception before creating parallel handwritten structure.
  - Treat `plan.json`, analysis outputs, drawing-board outputs, and generated snapshots as evidence, not handwritten business logic surfaces.
  - Preserve generated-versus-handwritten ownership in every path decision.
  ```

  Expected: no line says parallel handwritten structure is acceptable just because names are similar.

- [ ] **Step 6: Remove the leaked implementation slogan from other shared rules**

  In these files, remove only the line `- Keep runtime guidance self-contained.` and keep the rest of the rule unchanged unless the surrounding grammar requires a minimal edit:

  ```text
  skills/shared/rules/cap4k-positioning.md
  skills/shared/rules/layer-and-runtime-boundaries.md
  skills/shared/rules/naming-layout-and-testing.md
  skills/shared/rules/verification-claim-policy.md
  ```

  Expected: no runtime skill rule contains `Keep runtime guidance self-contained.`.

- [ ] **Step 7: Static check runtime wording**

  Run:

  ```powershell
  rg -n "Keep runtime guidance self-contained|GitHub issue|future work|MCP|historical[-]decision|Open[ ]Decisions|Continue[ ]Brainstorming" skills
  ```

  Expected: no output.

---

### Task 3: Remove Drift Gotchas From Ordinary Runtime Routes

**Files:**

- Modify: `skills/cap4k-authoring/routing.yaml`
- Modify: `skills/scripts/checks/structure.ps1`
- Modify: `skills/scripts/checks/routing.ps1`
- Modify: `skills/scripts/checks/skeleton-gate-refs.ps1`
- Modify: `skills/scripts/checks/stale-terms.ps1`
- Reference: `skills/shared/references/drift-gotchas.md`

- [ ] **Step 1: Locate ordinary runtime routes that still load drift gotchas**

  Run:

  ```powershell
  rg -n "drift-gotchas" skills
  ```

  Expected before editing: matches in `skills/cap4k-authoring/routing.yaml` and skill check scripts.

- [ ] **Step 2: Remove ordinary route references**

  Edit `skills/cap4k-authoring/routing.yaml` and remove `../shared/references/drift-gotchas.md` from route read lists.

  Expected: ordinary cap4k authoring routes no longer load the historical gotcha log into normal task context.

- [ ] **Step 3: Keep stale term checks review-only**

  Update the PowerShell checks so they no longer require `skills/shared/references/drift-gotchas.md` to be routed, while still rejecting stale runtime wording:

  - In `skills/scripts/checks/structure.ps1`, remove `skills/shared/references/drift-gotchas.md` from any required runtime structure list if it is treated as mandatory route material.
  - In `skills/scripts/checks/routing.ps1`, remove assertions that require `../shared/references/drift-gotchas.md` in runtime route lists.
  - In `skills/scripts/checks/skeleton-gate-refs.ps1`, remove assertions that require `../shared/references/drift-gotchas.md` on skeleton gate routes.
  - In `skills/scripts/checks/stale-terms.ps1`, keep stale-pattern assertions active. If the script has special masking logic for examples inside `drift-gotchas.md`, leave that masking only if the file remains as a review reference; otherwise remove the now-unused mask branch.

  Expected: historical/stale text remains blocked by checks, but `drift-gotchas.md` is no longer an ordinary runtime route dependency.

- [ ] **Step 4: Decide whether the file stays as a review-only reference**

  Keep `skills/shared/references/drift-gotchas.md` only if it is no longer loaded by ordinary routes and is only used by check scripts or maintainers. Delete it only if all valid current boundaries have been moved into positive rules/references and no check script needs to mask example text from it.

  Expected: `rg -n "drift-gotchas" skills/cap4k-authoring skills/cap4k-generator-inputs skills/shared/workflows skills/shared/rules` returns no output.

- [ ] **Step 5: Static check routes**

  Run:

  ```powershell
  rg -n "drift-gotchas" skills/cap4k-authoring skills/cap4k-generator-inputs skills/shared/workflows skills/shared/rules
  ```

  Expected: no output.

---

### Task 4: Update Public Reference Documentation

**Files:**

- Create: `docs/public/reference/db-schema-annotations.md`
- Create: `docs/public/reference/generator-input-validation.md`
- Modify: `docs/public/reference/design-json.md`
- Modify: `docs/public/reference/analysis-outputs.md`
- Modify: `docs/public/reference/index.md`
- Modify: `docs/public/generator/inputs-and-sources.md`
- Modify: `docs/public/reference/enum-manifest.md`
- Modify: `docs/public/reference/value-object-manifest.md`
- Depends on: Task 1

- [ ] **Step 1: Create the DB schema annotations reference page**

  Write `docs/public/reference/db-schema-annotations.md` as a human-facing page with these sections:

  ```markdown
  # DB Schema Annotations

  DB/schema comments are generator inputs for persistence and aggregate structure. They are read from DDL or database metadata by the DB source provider.

  ## Table Comment Annotations

  | Annotation | Meaning |
  | --- | --- |
  | `@Parent=<table>` / `@P=<table>` | Marks this table as a child of another table. |
  | `@AggregateRoot=<bool>` / `@Root=<bool>` / `@R=<bool>` | Explicitly marks aggregate-root status. |
  | `@ValueObject` / `@VO` | Marks table-derived value object shape. |
  | `@Ignore` / `@I` | Excludes the table from generation. |
  | `@DynamicInsert=<bool>` | Requests dynamic insert metadata. |
  | `@DynamicUpdate=<bool>` | Requests dynamic update metadata. |

  ## Column Comment Annotations

  | Annotation | Meaning |
  | --- | --- |
  | `@T=<TypeName>` / `@TYPE=<TypeName>` | Overrides or binds the generated field type. |
  | `@E=<items>` / `@ENUM=<items>` | Declares enum items and requires `@T`. |
  | `@RefId=<TypeName>` | Marks a local external-reference identity type. |
  | `@Deleted` | Marks the soft-delete column. |
  | `@Version` | Marks the optimistic-lock version column. |
  | `@GeneratedValue=identity` / `@GeneratedValue=database-identity` | Marks explicit database identity semantics. |
  | `@Managed` | Marks framework-managed field metadata. |
  | `@Inherited` | Marks inherited field metadata. |
  | `@Reference=<table>` / `@Ref=<table>` | Names the referenced table for relation metadata. |
  | `@Relation=<type>` / `@Rel=<type>` | Names relation type. |
  | `@Lazy=<bool>` / `@L=<bool>` | Marks lazy relation metadata. |
  | `@Count=<value>` / `@C=<value>` | Marks relation count metadata. |
  | `@RefAggregate=<AggregateName>` | References another aggregate by aggregate name. |

  ## Conflict And Dependency Rules

  - `@Parent` / `@P` cannot combine with aggregate-root true.
  - Presence annotations do not accept explicit values.
  - Boolean values are lowercase `true` or `false`.
  - `@E` / `@ENUM` requires `@T` / `@TYPE`.
  - `@Relation` / `@Rel`, `@Lazy` / `@L`, and `@Count` / `@C` require `@Reference` / `@Ref`.
  - `@RefAggregate` conflicts with `@Reference` / `@Ref`.
  - `@RefAggregate` conflicts with `@RefId`.

  ## Removed Or Unsupported Annotations

  - Use column `@GeneratedValue=identity` or `@GeneratedValue=database-identity`; do not use table `@IdGenerator` or `@IG`.
  - Use column `@Deleted`; do not use table `@SoftDeleteColumn`.
  - Do not use column `@Exposed`, `@Insertable`, or `@Updatable`.
  ```

  Expected: this page can be more explanatory than the skill reference, but should not introduce unsupported annotations.

- [ ] **Step 2: Create the generator input validation page**

  Write `docs/public/reference/generator-input-validation.md` with:

  ```markdown
  # Generator Input Validation

  `scripts/validate-cap4k-generator-inputs.py` is an offline, conservative feedback tool for files an agent or author writes before running cap4k generation.

  ## Scope

  It validates:

  - design JSON files registered through `sources.designJson.files`;
  - DB/schema DDL comments in SQL files;
  - enum manifests configured through `types.enumManifest.files`;
  - value-object manifests configured through `types.valueObjectManifest.files`.

  It does not connect to databases, run Gradle, generate code, compile, run tests, or normalize input files.

  ## Usage

  ```powershell
  python scripts/validate-cap4k-generator-inputs.py `
    --design design/design.json `
    --schema schema.sql `
    --enum design/enums.json `
    --value-object design/value-objects.json
  ```

  Use `--json` for structured output.

  ## Issue Levels

  | Level | Meaning | Exit behavior |
  | --- | --- | --- |
  | `ERROR` | The current official input contract rejects the file or field. | Any `ERROR` exits nonzero. |
  | `WARN` | Static evidence is suspicious or incomplete. | Does not fail by itself. |
  | `RECOVERY_HINT` | Repair context for a related error, especially copied analysis or drawing-board fragments. | Does not fail by itself. |
  ```

  Expected: the page describes the current validator only and does not promise DB permission prompts or MCP.

- [ ] **Step 3: Update design-json public reference**

  Edit `docs/public/reference/design-json.md` to include the stricter official contract:

  - root is a JSON array;
  - every entry is an object;
  - `tag` and `name` are required;
  - `package` is required except for `domain_event`;
  - supported public fields are `tag`, `name`, `package`, `description`, `aggregates`, `fields`, `resultFields`, `eventName`, `persist`, and `artifacts`;
  - `resultFields` only on `query`, `client`, and `api_payload`;
  - `integration_event` requires `eventName`;
  - `eventName` only on `domain_event` and `integration_event`;
  - `persist` only on `domain_event`;
  - removed fields `desc`, `requestFields`, `responseFields`, `traits`, `role`, `scope`, and `entity` are rejected;
  - `domain_event` field name `entity` is reserved;
  - field type must not use `self`;
  - drawing-board fragments must satisfy this contract before being registered as design input.

  Expected: public docs can mention unsupported examples such as `validator` and `value_object` because they are human reference material, but the skill runtime contract should stay positive and current.

- [ ] **Step 4: Update analysis output boundary**

  In `docs/public/reference/analysis-outputs.md`, add a boundary section:

  ```markdown
  ## Source Generation Boundary

  `cap4kAnalysisGenerate` is not source generation. Flow and drawing-board outputs are observation evidence by default.

  A drawing-board file can be manually copied or registered as design JSON input only when its content satisfies the current [Design JSON](design-json.md) contract. For example, a copied fragment that places `resultFields` on a `command` is invalid as design JSON and should be corrected before using `sources.designJson.files`.

  Arbitrary analysis outputs are not ordinary source-generation input skeletons.
  ```

  Expected: this preserves the current boundary without documenting future recovery support.

- [ ] **Step 5: Update indexes and generator overview**

  In `docs/public/reference/index.md`, add lookup rows:

  ```markdown
  | DB/schema comment annotations, relation metadata, type markers | [DB Schema Annotations](db-schema-annotations.md) |
  | Offline validation for design JSON, schema comments, enum manifests, and value-object manifests | [Generator Input Validation](generator-input-validation.md) |
  ```

  In `docs/public/generator/inputs-and-sources.md`, link the new reference pages from the DB/schema and Input Feedback sections.

  Expected: users can discover DB annotation and validator docs from the reference index and generator input overview.

- [ ] **Step 6: Tighten manifest public docs**

  In `docs/public/reference/enum-manifest.md`, make these current rules explicit:

  - root is a JSON array;
  - `aggregates` is optional, omitted/empty means shared, and current support allows at most one owner;
  - duplicate shared names are invalid;
  - duplicate same-owner names are invalid;
  - `generateTranslation` is removed/not a manifest field.

  In `docs/public/reference/value-object-manifest.md`, make these current rules explicit:

  - root is a JSON array;
  - `aggregates` is optional, omitted/empty means shared, and current support allows at most one owner;
  - `storage` supports only `json`, and omitted means `json`;
  - duplicate shared names are invalid;
  - duplicate same-owner names are invalid;
  - `scope` and `aggregate` are removed; use `aggregates`.

  Expected: public manifest pages align with the skill manifest contract while staying more explanatory.

- [ ] **Step 7: Static check public docs**

  Run:

  ```powershell
  rg -n "drawing-board.*first-class|issue #102|MCP[ ]server|database[ ]connection|DB[ ]connection[ ]prompt|future[ ]recovery" docs/public
  ```

  Expected: no output. Public docs should describe current capability, not future iteration plans.

---

### Task 5: Add Offline Generator Input Validator

**Files:**

- Create: `scripts/validate-cap4k-generator-inputs.py`
- Reference: skill contract references from Task 1

- [ ] **Step 1: Create the scripts directory**

  Run:

  ```powershell
  New-Item -ItemType Directory -Path "scripts" -Force | Out-Null
  ```

  Expected: `scripts` exists at the repository root.

- [ ] **Step 2: Create the validator module skeleton**

  Write `scripts/validate-cap4k-generator-inputs.py` with this top-level structure:

  ```python
  import argparse
  import json
  import re
  import sys
  from dataclasses import dataclass
  from pathlib import Path
  from typing import Any


  ERROR = "ERROR"
  WARN = "WARN"
  RECOVERY_HINT = "RECOVERY_HINT"


  @dataclass
  class Issue:
      level: str
      file: str
      path: str
      message: str
      hint: str = ""


  def add_issue(issues: list[Issue], level: str, file: Path, path: str, message: str, hint: str = "") -> None:
      issues.append(Issue(level=level, file=str(file), path=path, message=message, hint=hint))


  def read_json_file(file: Path, issues: list[Issue]) -> Any | None:
      try:
          return json.loads(file.read_text(encoding="utf-8"))
      except FileNotFoundError:
          add_issue(issues, ERROR, file, "$", "file does not exist")
      except json.JSONDecodeError as exc:
          add_issue(issues, ERROR, file, "$", f"invalid JSON: {exc.msg}", f"line {exc.lineno}, column {exc.colno}")
      return None


  def parse_args(argv: list[str]) -> argparse.Namespace:
      parser = argparse.ArgumentParser(description="Validate cap4k generator input files.")
      parser.add_argument("--design", action="append", default=[], help="Design JSON file. Repeatable.")
      parser.add_argument("--schema", action="append", default=[], help="DDL SQL file. Repeatable.")
      parser.add_argument("--enum", action="append", default=[], help="Enum manifest JSON file. Repeatable.")
      parser.add_argument("--value-object", action="append", default=[], help="Value-object manifest JSON file. Repeatable.")
      parser.add_argument("--json", action="store_true", help="Emit structured JSON output.")
      return parser.parse_args(argv)


  def main(argv: list[str]) -> int:
      args = parse_args(argv)
      issues: list[Issue] = []
      for value in args.design:
          validate_design(Path(value), issues)
      for value in args.schema:
          validate_schema(Path(value), issues)
      for value in args.enum:
          validate_enum_manifest(Path(value), issues)
      for value in args.value_object:
          validate_value_object_manifest(Path(value), issues)
      emit_output(issues, args.json)
      return 1 if any(issue.level == ERROR for issue in issues) else 0


  if __name__ == "__main__":
      raise SystemExit(main(sys.argv[1:]))
  ```

  Expected: the file imports standard library modules only. Functions referenced here are implemented in later steps before static verification.

- [ ] **Step 3: Implement output formatting**

  Add:

  ```python
  def issue_to_dict(issue: Issue) -> dict[str, str]:
      return {
          "level": issue.level,
          "file": issue.file,
          "path": issue.path,
          "message": issue.message,
          "hint": issue.hint,
      }


  def emit_output(issues: list[Issue], json_output: bool) -> None:
      if json_output:
          print(json.dumps([issue_to_dict(issue) for issue in issues], ensure_ascii=False, indent=2))
          return

      if not issues:
          print("OK: no issues found.")
          return

      for issue in issues:
          location = f"{issue.file}:{issue.path}" if issue.path else issue.file
          print(f"{issue.level}: {location}: {issue.message}")
          if issue.hint:
              print(f"  hint: {issue.hint}")
  ```

  Expected: empty validation emits a short OK line; any `ERROR` is still handled by `main` exit code.

- [ ] **Step 4: Implement design JSON validation**

  Add constants and function:

  ```python
  DESIGN_TAGS = {
      "command",
      "query",
      "client",
      "api_payload",
      "domain_event",
      "integration_event",
      "domain_service",
      "saga",
  }
  DESIGN_PUBLIC_FIELDS = {
      "tag",
      "name",
      "package",
      "description",
      "aggregates",
      "fields",
      "resultFields",
      "eventName",
      "persist",
      "artifacts",
  }
  DESIGN_REMOVED_FIELDS = {"desc", "requestFields", "responseFields", "traits", "role", "scope", "entity"}
  RESULT_FIELD_TAGS = {"query", "client", "api_payload"}
  EVENT_NAME_TAGS = {"domain_event", "integration_event"}


  def is_nonblank_string(value: Any) -> bool:
      return isinstance(value, str) and bool(value.strip())


  def validate_field_array(file: Path, issues: list[Issue], entry_name: str, value: Any, path: str, *, domain_event: bool) -> None:
      if value is None:
          return
      if not isinstance(value, list):
          add_issue(issues, ERROR, file, path, "must be an array")
          return
      for index, field in enumerate(value):
          field_path = f"{path}[{index}]"
          if not isinstance(field, dict):
              add_issue(issues, ERROR, file, field_path, "field item must be an object")
              continue
          name = field.get("name")
          field_type = field.get("type")
          if not is_nonblank_string(name):
              add_issue(issues, ERROR, file, f"{field_path}.name", "field name is required")
          if domain_event and name == "entity":
              add_issue(issues, ERROR, file, f"{field_path}.name", f"domain_event {entry_name} field 'entity' is reserved")
          if not is_nonblank_string(field_type):
              add_issue(issues, ERROR, file, f"{field_path}.type", "field type is required")
          elif re.search(r"(^|[^A-Za-z0-9_])self([^A-Za-z0-9_]|$)", field_type, re.IGNORECASE):
              add_issue(issues, ERROR, file, f"{field_path}.type", "field type must not use self", "Use an explicit type name.")


  def validate_design(file: Path, issues: list[Issue]) -> None:
      data = read_json_file(file, issues)
      if data is None:
          return
      if not isinstance(data, list):
          add_issue(issues, ERROR, file, "$", "design JSON root must be an array")
          return
      for index, entry in enumerate(data):
          path = f"$[{index}]"
          if not isinstance(entry, dict):
              add_issue(issues, ERROR, file, path, "design entry must be an object")
              continue
          tag = entry.get("tag")
          name = entry.get("name")
          if not is_nonblank_string(tag):
              add_issue(issues, ERROR, file, f"{path}.tag", "tag is required")
          elif tag not in DESIGN_TAGS:
              add_issue(issues, ERROR, file, f"{path}.tag", f"unsupported design tag: {tag}")
          if not is_nonblank_string(name):
              add_issue(issues, ERROR, file, f"{path}.name", "name is required")
              name = "<unnamed>"
          if tag != "domain_event" and not is_nonblank_string(entry.get("package")):
              add_issue(issues, ERROR, file, f"{path}.package", "package is required except for domain_event")

          for field_name in sorted(set(entry) - DESIGN_PUBLIC_FIELDS):
              add_issue(issues, WARN, file, f"{path}.{field_name}", "unknown design entry field")
          for field_name in sorted(DESIGN_REMOVED_FIELDS & set(entry)):
              add_issue(issues, ERROR, file, f"{path}.{field_name}", f"removed design entry field: {field_name}")

          result_fields = entry.get("resultFields")
          if result_fields is not None and tag not in RESULT_FIELD_TAGS:
              add_issue(issues, ERROR, file, f"{path}.resultFields", f"{tag} must not declare resultFields", "If this came from drawing-board output, transform it before registering it as design JSON.")
              add_issue(issues, RECOVERY_HINT, file, f"{path}.resultFields", "drawing-board-compatible JSON is not automatically valid design JSON", "Only register fragments that satisfy the design-json contract.")
          if tag == "integration_event" and not is_nonblank_string(entry.get("eventName")):
              add_issue(issues, ERROR, file, f"{path}.eventName", f"integration_event {name} must declare eventName")
          if "eventName" in entry and tag not in EVENT_NAME_TAGS:
              add_issue(issues, ERROR, file, f"{path}.eventName", "eventName is allowed only on domain_event and integration_event")
          if "persist" in entry and tag != "domain_event":
              add_issue(issues, ERROR, file, f"{path}.persist", "persist is allowed only on domain_event")

          validate_field_array(file, issues, str(name), entry.get("fields"), f"{path}.fields", domain_event=(tag == "domain_event"))
          validate_field_array(file, issues, str(name), result_fields, f"{path}.resultFields", domain_event=False)
  ```

  Expected: the validator rejects `command.resultFields` and emits a `RECOVERY_HINT` instead of silently normalizing.

- [ ] **Step 5: Implement enum manifest validation**

  Add:

  ```python
  def optional_string_list(value: Any, file: Path, issues: list[Issue], path: str) -> list[str]:
      if value is None:
          return []
      if not isinstance(value, list):
          add_issue(issues, ERROR, file, path, "must be an array of strings")
          return []
      result: list[str] = []
      for index, item in enumerate(value):
          if not is_nonblank_string(item):
              add_issue(issues, ERROR, file, f"{path}[{index}]", "must be a nonblank string")
          else:
              result.append(item)
      return result


  def validate_enum_manifest(file: Path, issues: list[Issue]) -> None:
      data = read_json_file(file, issues)
      if data is None:
          return
      if not isinstance(data, list):
          add_issue(issues, ERROR, file, "$", "enum manifest root must be an array")
          return
      shared_names: set[str] = set()
      owned_names: set[tuple[str, str]] = set()
      for index, entry in enumerate(data):
          path = f"$[{index}]"
          if not isinstance(entry, dict):
              add_issue(issues, ERROR, file, path, "enum manifest entry must be an object")
              continue
          name = entry.get("name")
          if not is_nonblank_string(name):
              add_issue(issues, ERROR, file, f"{path}.name", "name is required")
              name = "<unnamed>"
          if not is_nonblank_string(entry.get("package")):
              add_issue(issues, ERROR, file, f"{path}.package", "package is required")
          if "generateTranslation" in entry:
              add_issue(issues, ERROR, file, f"{path}.generateTranslation", "generateTranslation is removed from enum manifests")
          aggregates = optional_string_list(entry.get("aggregates"), file, issues, f"{path}.aggregates")
          if len(aggregates) > 1:
              add_issue(issues, ERROR, file, f"{path}.aggregates", f"enum {name} may declare at most one aggregate")
          if not aggregates:
              if str(name) in shared_names:
                  add_issue(issues, ERROR, file, f"{path}.name", f"duplicate shared enum definition: {name}")
              shared_names.add(str(name))
          else:
              key = (aggregates[0], str(name))
              if key in owned_names:
                  add_issue(issues, ERROR, file, f"{path}.name", f"duplicate aggregate enum definition: {name} in {aggregates[0]}")
              owned_names.add(key)
          items = entry.get("items")
          if not isinstance(items, list):
              add_issue(issues, ERROR, file, f"{path}.items", "items is required and must be an array")
              continue
          for item_index, item in enumerate(items):
              item_path = f"{path}.items[{item_index}]"
              if not isinstance(item, dict):
                  add_issue(issues, ERROR, file, item_path, "enum item must be an object")
                  continue
              if not isinstance(item.get("value"), int) or isinstance(item.get("value"), bool):
                  add_issue(issues, ERROR, file, f"{item_path}.value", "value is required and must be an integer")
              if not is_nonblank_string(item.get("name")):
                  add_issue(issues, ERROR, file, f"{item_path}.name", "name is required")
              if not is_nonblank_string(item.get("desc")):
                  add_issue(issues, ERROR, file, f"{item_path}.desc", "desc is required")
  ```

  Expected: duplicate checks match the current provider behavior for shared and same-owner enum names.

- [ ] **Step 6: Implement value-object manifest validation**

  Add:

  ```python
  def validate_value_object_manifest(file: Path, issues: list[Issue]) -> None:
      data = read_json_file(file, issues)
      if data is None:
          return
      if not isinstance(data, list):
          add_issue(issues, ERROR, file, "$", "value-object manifest root must be an array")
          return
      shared_names: set[str] = set()
      owned_names: set[tuple[str, str]] = set()
      for index, entry in enumerate(data):
          path = f"$[{index}]"
          if not isinstance(entry, dict):
              add_issue(issues, ERROR, file, path, "value-object manifest entry must be an object")
              continue
          name = entry.get("name")
          if not is_nonblank_string(name):
              add_issue(issues, ERROR, file, f"{path}.name", "name is required")
              name = "<unnamed>"
          if not is_nonblank_string(entry.get("package")):
              add_issue(issues, ERROR, file, f"{path}.package", "package is required")
          for removed in ("scope", "aggregate"):
              if removed in entry:
                  add_issue(issues, ERROR, file, f"{path}.{removed}", f"{removed} is removed; use aggregates")
          storage = entry.get("storage", "json")
          if storage != "json":
              add_issue(issues, ERROR, file, f"{path}.storage", f"value object {name} storage must be json")
          aggregates = optional_string_list(entry.get("aggregates"), file, issues, f"{path}.aggregates")
          if len(aggregates) > 1:
              add_issue(issues, ERROR, file, f"{path}.aggregates", f"value object {name} may declare at most one aggregate")
          if not aggregates:
              if str(name) in shared_names:
                  add_issue(issues, ERROR, file, f"{path}.name", f"duplicate shared value object definition: {name}")
              shared_names.add(str(name))
          else:
              key = (aggregates[0], str(name))
              if key in owned_names:
                  add_issue(issues, ERROR, file, f"{path}.name", f"duplicate aggregate value object definition: {name} in {aggregates[0]}")
              owned_names.add(key)
          fields = entry.get("fields", [])
          if not isinstance(fields, list):
              add_issue(issues, ERROR, file, f"{path}.fields", "fields must be an array")
              continue
          for field_index, field in enumerate(fields):
              field_path = f"{path}.fields[{field_index}]"
              if not isinstance(field, dict):
                  add_issue(issues, ERROR, file, field_path, "field item must be an object")
                  continue
              if not is_nonblank_string(field.get("name")):
                  add_issue(issues, ERROR, file, f"{field_path}.name", "name is required")
              if not is_nonblank_string(field.get("type")):
                  add_issue(issues, ERROR, file, f"{field_path}.type", "type is required")
              if "nullable" in field and not isinstance(field.get("nullable"), bool):
                  add_issue(issues, ERROR, file, f"{field_path}.nullable", "nullable must be a boolean")
              if "defaultValue" in field and not isinstance(field.get("defaultValue"), str):
                  add_issue(issues, ERROR, file, f"{field_path}.defaultValue", "defaultValue must be a string")
  ```

  Expected: `scope`, `aggregate`, unsupported storage, and duplicate names are rejected.

- [ ] **Step 7: Implement conservative DDL comment validation**

  Add:

  ```python
  MYSQL_COMMENT_RE = re.compile(r"comment\s+'((?:''|[^'])*)'", re.IGNORECASE)
  COMMENT_ON_TABLE_RE = re.compile(r"comment\s+on\s+table\s+[\w.\"]+\s+is\s+'((?:''|[^'])*)'", re.IGNORECASE)
  COMMENT_ON_COLUMN_RE = re.compile(r"comment\s+on\s+column\s+[\w.\"]+\s+is\s+'((?:''|[^'])*)'", re.IGNORECASE)
  ANNOTATION_RE = re.compile(r"@([A-Za-z][A-Za-z0-9_]*)(?:=([^;\\s]+))?")
  TABLE_ANNOTATIONS = {"Parent", "P", "AggregateRoot", "Root", "R", "ValueObject", "VO", "Ignore", "I", "DynamicInsert", "DynamicUpdate"}
  COLUMN_ANNOTATIONS = {"T", "TYPE", "E", "ENUM", "RefId", "Deleted", "Version", "GeneratedValue", "Managed", "Inherited", "Reference", "Ref", "Relation", "Rel", "Lazy", "L", "Count", "C", "RefAggregate"}
  REJECTED_TABLE_ANNOTATIONS = {"IdGenerator", "IG", "SoftDeleteColumn"}
  REJECTED_COLUMN_ANNOTATIONS = {"Exposed", "Insertable", "Updatable"}
  PRESENCE_ANNOTATIONS = {"ValueObject", "VO", "Ignore", "I", "Deleted", "Version", "Managed", "Inherited"}
  BOOLEAN_ANNOTATIONS = {"AggregateRoot", "Root", "R", "DynamicInsert", "DynamicUpdate", "Lazy", "L"}
  RELATION_TYPES = {"MANY_TO_ONE", "ONE_TO_ONE", "1:1", "*:1", "MANYTOONE", "ONETOONE"}


  def unescape_sql_comment(value: str) -> str:
      return value.replace("''", "'")


  def parse_annotations(comment: str) -> list[tuple[str, str | None]]:
      return [(match.group(1), match.group(2)) for match in ANNOTATION_RE.finditer(comment)]


  def validate_annotation_values(file: Path, issues: list[Issue], path: str, annotations: list[tuple[str, str | None]], context: str) -> None:
      names = {name for name, _ in annotations}
      for name, value in annotations:
          if name in PRESENCE_ANNOTATIONS and value is not None:
              add_issue(issues, ERROR, file, path, f"@{name} does not accept an explicit value")
          if name in BOOLEAN_ANNOTATIONS and value not in (None, "true", "false"):
              add_issue(issues, ERROR, file, path, f"@{name} must use lowercase true or false")
          if name in {"Parent", "P", "T", "TYPE", "E", "ENUM", "RefId", "Reference", "Ref", "Relation", "Rel", "Count", "C", "RefAggregate"} and (value is None or value == ""):
              add_issue(issues, ERROR, file, path, f"@{name} requires a nonblank value")
          if name == "GeneratedValue" and value not in ("identity", "database-identity"):
              add_issue(issues, ERROR, file, path, f"unsupported @GeneratedValue strategy: {value if value is not None else ''}")
          if name in {"Relation", "Rel"} and value is not None and value.upper() not in RELATION_TYPES:
              add_issue(issues, ERROR, file, path, f"unsupported @{name} value: {value}")

      if context == "table":
          if ("Parent" in names or "P" in names) and any(name in names for name in ("AggregateRoot", "Root", "R")):
              true_root = any(value == "true" for name, value in annotations if name in {"AggregateRoot", "Root", "R"})
              if true_root:
                  add_issue(issues, ERROR, file, path, "@Parent/@P cannot be combined with aggregate-root true")
      if context == "column":
          if ("E" in names or "ENUM" in names) and not ("T" in names or "TYPE" in names):
              add_issue(issues, ERROR, file, path, "@E/@ENUM requires @T/@TYPE")
          if any(name in names for name in ("Relation", "Rel", "Lazy", "L", "Count", "C")) and not ("Reference" in names or "Ref" in names):
              add_issue(issues, ERROR, file, path, "@Relation/@Rel, @Lazy/@L, and @Count/@C require @Reference/@Ref")
          if "RefAggregate" in names and ("Reference" in names or "Ref" in names):
              add_issue(issues, ERROR, file, path, "@RefAggregate conflicts with @Reference/@Ref")
          if "RefAggregate" in names and "RefId" in names:
              add_issue(issues, ERROR, file, path, "@RefAggregate conflicts with @RefId")


  def validate_comment(file: Path, issues: list[Issue], path: str, comment: str, context: str) -> None:
      annotations = parse_annotations(comment)
      if not annotations:
          return
      allowed = TABLE_ANNOTATIONS if context == "table" else COLUMN_ANNOTATIONS if context == "column" else TABLE_ANNOTATIONS | COLUMN_ANNOTATIONS
      rejected = REJECTED_TABLE_ANNOTATIONS if context == "table" else REJECTED_COLUMN_ANNOTATIONS if context == "column" else REJECTED_TABLE_ANNOTATIONS | REJECTED_COLUMN_ANNOTATIONS
      for name, _ in annotations:
          if name in rejected:
              add_issue(issues, ERROR, file, path, f"unsupported {context} annotation @{name}")
          elif name not in allowed:
              add_issue(issues, WARN, file, path, f"unknown {context} annotation @{name}")
      if context in {"table", "column"}:
          validate_annotation_values(file, issues, path, annotations, context)
      else:
          add_issue(issues, WARN, file, path, "annotation context is unknown", "Use COMMENT ON TABLE/COLUMN or clear DDL placement so table and column rules can be checked.")


  def validate_schema(file: Path, issues: list[Issue]) -> None:
      try:
          text = file.read_text(encoding="utf-8")
      except FileNotFoundError:
          add_issue(issues, ERROR, file, "$", "file does not exist")
          return
      for index, match in enumerate(COMMENT_ON_TABLE_RE.finditer(text)):
          validate_comment(file, issues, f"COMMENT_ON_TABLE[{index}]", unescape_sql_comment(match.group(1)), "table")
      for index, match in enumerate(COMMENT_ON_COLUMN_RE.finditer(text)):
          validate_comment(file, issues, f"COMMENT_ON_COLUMN[{index}]", unescape_sql_comment(match.group(1)), "column")
      known_spans = [match.span() for match in COMMENT_ON_TABLE_RE.finditer(text)] + [match.span() for match in COMMENT_ON_COLUMN_RE.finditer(text)]
      for index, match in enumerate(MYSQL_COMMENT_RE.finditer(text)):
          if any(start <= match.start() and match.end() <= end for start, end in known_spans):
              continue
          prefix = text[max(0, match.start() - 240):match.start()].lower()
          context = "unknown"
          if "create table" in prefix and re.search(r"\)\s*$", prefix):
              context = "table"
          elif "create table" in prefix:
              context = "column"
          validate_comment(file, issues, f"COMMENT[{index}]", unescape_sql_comment(match.group(1)), context)
  ```

  Expected: the parser is conservative. If it cannot determine table/column context, it reports a warning rather than pretending to know.

- [ ] **Step 8: Check Python script syntax**

  If the execution turn has explicit permission to run local Python validation commands, run:

  ```powershell
  python -m py_compile scripts/validate-cap4k-generator-inputs.py
  ```

  Expected when permitted: exit code 0 and no output.

  If local Python execution is not permitted, do not run this command. Statically inspect imports, function definitions, and `main()` wiring, then disclose that Python syntax execution was skipped by workspace policy.

- [ ] **Step 9: Check small offline validator smoke behavior**

  Use these checks only when the execution turn has explicit permission to run local Python validation commands. Create temporary files under a local temp directory and validate them. Do not run Gradle, source generation, compilation, or database connections.

  When permitted, run:

  ```powershell
  $tmp = New-Item -ItemType Directory -Path ([System.IO.Path]::Combine([System.IO.Path]::GetTempPath(), "cap4k-validator-smoke-" + [System.Guid]::NewGuid().ToString("N")))
  Set-Content -Path (Join-Path $tmp "valid-design.json") -Encoding UTF8 -Value '[{"tag":"query","package":"content.read","name":"GetContent","resultFields":[{"name":"title","type":"String"}]}]'
  python scripts/validate-cap4k-generator-inputs.py --design (Join-Path $tmp "valid-design.json")
  ```

  Expected when permitted:

  ```text
  OK: no issues found.
  ```

  When permitted, run:

  ```powershell
  Set-Content -Path (Join-Path $tmp "invalid-design.json") -Encoding UTF8 -Value '[{"tag":"command","package":"content","name":"Submit","resultFields":[{"name":"accepted","type":"Boolean"}]}]'
  python scripts/validate-cap4k-generator-inputs.py --design (Join-Path $tmp "invalid-design.json")
  ```

  Expected when permitted: nonzero exit and output containing:

  ```text
  ERROR:
  RECOVERY_HINT:
  ```

  When permitted, delete the temp directory after inspection:

  ```powershell
  Remove-Item -LiteralPath $tmp -Recurse -Force
  ```

  If local Python execution is not permitted, do not run these commands. Statically inspect the corresponding validation branches and issue messages, then disclose the skipped smoke checks.

---
### Task 6: Final Static Review And Documentation Integrity

**Files:**

- Review all files changed in Tasks 1-5

- [ ] **Step 1: Inspect changed files**

  Run static inspection commands:

  ```powershell
  git status --short
  git diff -- docs/public skills scripts
  ```

  Expected: only planned files changed. No generator Kotlin source behavior changes.

- [ ] **Step 2: Run whitespace diff check**

  Run:

  ```powershell
  git diff --check -- docs/public skills scripts
  ```

  Expected: no output and exit code 0.

- [ ] **Step 3: Check skill validation**

  If the execution turn permits running repository static check scripts, run:

  ```powershell
  powershell -ExecutionPolicy Bypass -File skills/scripts/validate-cap4k-skills.ps1
  ```

  Expected when permitted:

  ```text
  cap4k skill validation passed.
  ```

  If this command is not permitted, do not run it. Statically inspect `skills/scripts/checks/*.ps1`, route references, and changed skill files, then disclose the skipped command.

  If this fails because a check still requires `drift-gotchas.md` in ordinary routes, fix the check or route according to Task 3. Do not re-add `drift-gotchas.md` to ordinary runtime routes to satisfy an outdated check.

- [ ] **Step 4: Run targeted runtime dependency scans**

  Run:

  ```powershell
  rg -n "docs/public|GitHub issue|Context7|historical[-]decision|Open[ ]Decisions|Continue[ ]Brainstorming|future[ ]work|MCP[ ]server|drawing-board[ ]recovery" skills
  ```

  Expected: no ordinary runtime dependency matches.

  Run:

  ```powershell
  rg -n "Keep runtime guidance self-contained" skills docs/public
  ```

  Expected: no runtime guidance matches. If a check script contains the phrase as a forbidden pattern, verify it is inside `skills/scripts/checks`, not runtime guidance.

  Run:

  ```powershell
  rg -n "../shared/references/drift-gotchas.md" skills/cap4k-authoring skills/cap4k-generator-inputs skills/cap4k-*/*.md skills/cap4k-*/workflows skills/cap4k-*/rules
  ```

  Expected: no ordinary runtime route matches.

- [ ] **Step 5: Check Python validator syntax and smoke behavior**

  If the execution turn has explicit permission to run local Python validation commands, run:

  ```powershell
  python -m py_compile scripts/validate-cap4k-generator-inputs.py
  ```

  Expected when permitted: no output and exit 0.

  If local Python execution is permitted, run the temporary-file smoke checks from Task 5 Step 9.

  If local Python execution is not permitted, do not run these commands. Statically inspect imports, function definitions, `main()` wiring, validation branches, and issue messages, then disclose the skipped commands.

- [ ] **Step 6: Review public docs and skill links by search**

  Run:

  ```powershell
  rg -n "db-schema-annotations.md|generator-input-validation.md|design-json-contract.md|manifest-contracts.md" docs/public skills
  ```

  Expected:

  - Public docs link `db-schema-annotations.md` and `generator-input-validation.md`.
  - Skill files link `design-json-contract.md`, `db-schema-annotations.md`, and `manifest-contracts.md`.
  - Skill runtime does not link public docs as required reading.

- [ ] **Step 7: Prepare PR review summary**

  Write a concise PR #101 summary covering:

  - skill runtime now self-contains current generator input contracts through focused references;
  - public docs now explain DB annotations and validation for human readers;
  - validator provides deterministic offline feedback without DB/Gradle/generation/compile/test behavior;
  - ordinary runtime routes no longer load historical drift gotchas;
  - verification commands run, commands skipped by policy, and remaining risk.

## Commit Policy

Do not run `git add` or `git commit` from this plan unless the user explicitly asks for commits in the execution turn. If commits are requested, use `git commit --no-verify` as required by this repository workflow.
