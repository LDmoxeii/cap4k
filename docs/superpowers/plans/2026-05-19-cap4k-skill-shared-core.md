# Cap4k Skill Shared Core Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Add a shared cap4k skill core and activate it from focused cap4k skills so stable authoring constraints are on the normal runtime path.

**Architecture:** Keep each `SKILL.md` as a thin router. Put long-lived shared constraints under `skills/shared/rules`, keep focused detail in existing skill rules and workflows, and make `skills/scripts/validate-cap4k-skills.ps1` enforce the shared core contract.

**Tech Stack:** Markdown Agent Skills, PowerShell validation, existing cap4k repository skill layout.

---

## File Structure

Create:

- `skills/shared/rules/core-positioning.md`: top-level cap4k authoring stance and over-claim prevention.
- `skills/shared/rules/default-path-and-write-boundaries.md`: default path, command write boundary, zero-trust reads, domain facts, and entry convergence.
- `skills/shared/rules/ownership-and-generation-flow.md`: plan/generate/handwritten ownership gates.
- `skills/shared/rules/naming-layout-and-testing.md`: role-inferable layout and default test contract.
- `skills/shared/rules/advanced-mode-gates.md`: upgrade gates for advanced concepts.

Modify:

- `skills/cap4k-authoring/SKILL.md`: router boundary text only.
- `skills/cap4k-modeling/SKILL.md`: Always Read shared core entries.
- `skills/cap4k-generation/SKILL.md`: Always Read shared core entries.
- `skills/cap4k-implementation/SKILL.md`: Always Read shared core entries.
- `skills/cap4k-service-integration/SKILL.md`: Always Read shared core entries.
- `skills/cap4k-verification/SKILL.md`: Always Read shared core entries.
- `skills/cap4k-implementation/rules/mediator-and-uow.md`: explicit multi-read and single-write boundary.
- `skills/cap4k-implementation/workflows/implement-command-slice.md`: zero-trust checklist hardening.
- `skills/cap4k-verification/rules/test-strategy.md`: default test contract.
- `skills/cap4k-modeling/rules/tactical-modeling.md`: advanced gate pointer if needed.
- `skills/scripts/validate-cap4k-skills.ps1`: shared-core existence, route, and phrase checks.

Do not modify:

- `docs/public/authoring/**`
- workspace-level `.agents/skills/**`
- generator/runtime source code

## Task 1: Create Shared Core Rule Files

**Files:**

- Create: `skills/shared/rules/core-positioning.md`
- Create: `skills/shared/rules/default-path-and-write-boundaries.md`
- Create: `skills/shared/rules/ownership-and-generation-flow.md`
- Create: `skills/shared/rules/naming-layout-and-testing.md`
- Create: `skills/shared/rules/advanced-mode-gates.md`

- [ ] **Step 1: Create shared rule directory**

Run:

```powershell
New-Item -ItemType Directory -Force 'skills/shared/rules'
```

Expected: command succeeds and `skills/shared/rules` exists.

- [ ] **Step 2: Create `core-positioning.md`**

Use this exact content:

```markdown
# Core Positioning

- Start from the conservative cap4k default path before using advanced concepts.
- Do not present one project-specific implementation shape as a framework default.
- Human authors retain final responsibility for domain decisions, architecture tradeoffs, and audit.
- Unsupported framework capability must be recorded as a gap instead of implied as working behavior.
- Public authoring docs can inform skill maintenance, but runtime skills must be self-contained.
```

- [ ] **Step 3: Create `default-path-and-write-boundaries.md`**

Use this exact content:

```markdown
# Default Path And Write Boundaries

- One command path may persist only one aggregate root.
- Commands may read multiple aggregates or read facts for zero-trust validation, but those reads must not become shared write ownership.
- State-changing controllers, subscribers, jobs, external fact entries, and Open Host Service entries route into commands.
- Aggregate roots own write invariants and emit meaningful domain facts.
- Domain events describe business facts, not technical continuation steps.
- Callback and polling entries must converge to the same internal command semantics when they represent the same business fact.
- Query paths observe. They do not repair or mutate the write model.
```

- [ ] **Step 4: Create `ownership-and-generation-flow.md`**

Use this exact content:

```markdown
# Ownership And Generation Flow

- Source generation follows the cap4k planned-source contract: `cap4kPlan` establishes `plan.json` ownership before `cap4kGenerate` materializes source.
- Generator-capable skeletons belong to generation inputs and planned output, not ad hoc implementation files.
- Design, DDL, enum manifest, `types.registryFile`, and KSP metadata are generation input contracts.
- `src/main/kotlin` does not automatically mean handwritten ownership.
- Copied generated snapshots are evidence only, not active authoring surfaces.
- Generated or checked-in skeleton editability depends on `generatorId`, `templateId`, `outputKind`, `resolvedOutputRoot`, and `conflictPolicy`.
```

- [ ] **Step 5: Create `naming-layout-and-testing.md`**

Use this exact content:

```markdown
# Naming Layout And Testing

- Files belong in responsibility directories; do not place code by convenience or physical proximity.
- File name plus directory should make the role inferable.
- Keep transport DTOs, external protocol details, query projections, and domain behavior in their proper layers.
- Default verification starts with domain behavior tests and application orchestration tests.
- Test helpers must stay thin and must not hide business semantics behind opaque DSLs.
- Analysis output helps review relationships and flows, but does not replace compile or tests.
```

- [ ] **Step 6: Create `advanced-mode-gates.md`**

Use this exact content:

```markdown
# Advanced Mode Gates

- Use advanced concepts only after the default aggregate, command, event, and query path is insufficient.
- Domain Service is for domain decisions that do not naturally belong to one aggregate or value object.
- Saga is for persisted long-running coordination, retry, recovery, compensation, or cross-time waiting.
- Read-only weak references support navigation or type expression without introducing writable cross-aggregate object graphs.
- Strong ID is engineering reinforcement, not a substitute for aggregate, command, and naming boundaries.
- Value Object modeling starts from business value semantics, then chooses primitive, inline, JSON-backed, or table-backed persistence.
```

- [ ] **Step 7: Verify shared files exist**

Run:

```powershell
Get-ChildItem 'skills/shared/rules' -File | Select-Object -ExpandProperty Name
```

Expected output includes exactly these new files:

```text
advanced-mode-gates.md
core-positioning.md
default-path-and-write-boundaries.md
naming-layout-and-testing.md
ownership-and-generation-flow.md
```

## Task 2: Activate Shared Core In Skill Entry Points

**Files:**

- Modify: `skills/cap4k-authoring/SKILL.md`
- Modify: `skills/cap4k-modeling/SKILL.md`
- Modify: `skills/cap4k-generation/SKILL.md`
- Modify: `skills/cap4k-implementation/SKILL.md`
- Modify: `skills/cap4k-service-integration/SKILL.md`
- Modify: `skills/cap4k-verification/SKILL.md`

- [ ] **Step 1: Update `cap4k-authoring` boundary text**

In `skills/cap4k-authoring/SKILL.md`, replace the `## Boundaries` list with:

```markdown
## Boundaries

- Use these skills for business projects built with cap4k.
- Do not load public docs as runtime instructions unless the user asks for public documentation work.
- Focused skills carry the shared cap4k constraints; this router only chooses the task path.
```

- [ ] **Step 2: Update `cap4k-modeling` Always Read**

In `skills/cap4k-modeling/SKILL.md`, replace the `## Always Read` list with:

```markdown
## Always Read

1. `../shared/rules/core-positioning.md`
2. `../shared/rules/default-path-and-write-boundaries.md`
3. `../shared/rules/advanced-mode-gates.md`
4. `rules/domain-language.md`
5. `rules/tactical-modeling.md`
```

- [ ] **Step 3: Update `cap4k-generation` Always Read**

In `skills/cap4k-generation/SKILL.md`, replace the `## Always Read` list with:

```markdown
## Always Read

1. `../shared/rules/core-positioning.md`
2. `../shared/rules/ownership-and-generation-flow.md`
3. `rules/input-contracts.md`
4. `rules/output-ownership.md`
```

- [ ] **Step 4: Update `cap4k-implementation` Always Read**

In `skills/cap4k-implementation/SKILL.md`, replace the `## Always Read` list with:

```markdown
## Always Read

1. `../shared/rules/default-path-and-write-boundaries.md`
2. `../shared/rules/ownership-and-generation-flow.md`
3. `rules/source-of-truth-and-skeletons.md`
4. `rules/layering.md`
5. `rules/mediator-and-uow.md`
```

- [ ] **Step 5: Update `cap4k-service-integration` Always Read**

In `skills/cap4k-service-integration/SKILL.md`, replace the `## Always Read` list with:

```markdown
## Always Read

1. `../shared/rules/default-path-and-write-boundaries.md`
2. `../shared/rules/naming-layout-and-testing.md`
3. `rules/service-boundaries.md`
4. `rules/integration-events.md`
```

- [ ] **Step 6: Update `cap4k-verification` Always Read**

In `skills/cap4k-verification/SKILL.md`, replace the `## Always Read` list with:

```markdown
## Always Read

1. `../shared/rules/default-path-and-write-boundaries.md`
2. `../shared/rules/ownership-and-generation-flow.md`
3. `../shared/rules/naming-layout-and-testing.md`
4. `rules/evidence-contract.md`
5. `rules/test-strategy.md`
```

- [ ] **Step 7: Verify line budgets**

Run:

```powershell
Get-ChildItem skills -Directory |
  Where-Object { $_.Name -ne 'scripts' -and $_.Name -ne 'shared' } |
  ForEach-Object {
    $file = Join-Path $_.FullName 'SKILL.md'
    [pscustomobject]@{ Skill = $_.Name; Lines = (Get-Content $file).Count }
  }
```

Expected: every listed `SKILL.md` has `Lines` less than or equal to `100`.

## Task 3: Harden Implementation And Verification Rules

**Files:**

- Modify: `skills/cap4k-implementation/rules/mediator-and-uow.md`
- Modify: `skills/cap4k-implementation/workflows/implement-command-slice.md`
- Modify: `skills/cap4k-verification/rules/test-strategy.md`
- Modify: `skills/cap4k-modeling/rules/tactical-modeling.md`

- [ ] **Step 1: Add command read/write boundary rules**

In `skills/cap4k-implementation/rules/mediator-and-uow.md`, under `### UoW Persistence Boundary`, add these bullets after `Mediator.uow.save(...) belongs inside an explicit write boundary.`:

```markdown
- A command may read multiple aggregates or read facts for zero-trust validation.
- Only one aggregate root may enter the persistence write boundary in one command path.
- Non-target aggregate reads must stay read-only and must not share write responsibility.
```

- [ ] **Step 2: Harden zero-trust command checklist**

In `skills/cap4k-implementation/workflows/implement-command-slice.md`, under `## Zero-Trust Command Boundary`, replace the final checklist item:

```markdown
- [ ] Persist only aggregate roots through `Mediator.uow.save(...)`.
```

with:

```markdown
- [ ] Read multiple aggregates or facts only for validation or fact observation.
- [ ] Persist exactly one aggregate root through `Mediator.uow.save(...)`.
```

- [ ] **Step 3: Expand verification test strategy**

Replace all content in `skills/cap4k-verification/rules/test-strategy.md` with:

```markdown
# Test Strategy

- Prefer domain behavior tests and application orchestration tests first.
- Use adapter or integration smoke tests when the project claims runnable HTTP, callback, message, or external event behavior.
- Keep test helpers thin; helpers should expose business semantics instead of hiding them behind opaque DSLs.
- Use generated analysis output to review relationships and flows, not to replace compile/tests.
- Avoid brittle line-by-line snapshots of generated analysis output.
- For docs-only or skill-only changes, use targeted scans, validation scripts, and `git diff --check`.
```

- [ ] **Step 4: Add advanced gate pointer to modeling rules**

In `skills/cap4k-modeling/rules/tactical-modeling.md`, add this bullet after `Specifications model validation policies only when the project intentionally demonstrates or enforces that concept.`:

```markdown
- Advanced concepts must pass the shared advanced-mode gate before they become the default model shape.
```

- [ ] **Step 5: Verify hardened phrases**

Run:

```powershell
rg -n "zero-trust validation|exactly one aggregate root|domain behavior tests|advanced-mode gate" skills/cap4k-implementation skills/cap4k-verification skills/cap4k-modeling
```

Expected: matches appear in the modified rule/workflow files.

## Task 4: Extend Skill Validation

**Files:**

- Modify: `skills/scripts/validate-cap4k-skills.ps1`

- [ ] **Step 1: Exclude non-skill shared directory from validation**

Update the validator's `$skillDirs` filter to exclude both `scripts` and `shared`:

```powershell
$skillDirs = Get-ChildItem -LiteralPath 'skills' -Directory |
  Where-Object { $_.Name -notin @('scripts', 'shared') }
```

- [ ] **Step 2: Add shared-core file checks**

After the broken-link check block and before `$skillTextFiles = ...`, add:

```powershell
$sharedRulePaths = @(
  'skills/shared/rules/core-positioning.md',
  'skills/shared/rules/default-path-and-write-boundaries.md',
  'skills/shared/rules/ownership-and-generation-flow.md',
  'skills/shared/rules/naming-layout-and-testing.md',
  'skills/shared/rules/advanced-mode-gates.md'
)

$missingSharedRules = $sharedRulePaths |
  Where-Object { -not (Test-Path -LiteralPath $_) }

if ($missingSharedRules.Count -gt 0) {
  throw "Missing shared cap4k skill rules: $($missingSharedRules -join ', ')"
}

$requiredSkillRefs = @{
  'cap4k-modeling' = @(
    '../shared/rules/core-positioning.md',
    '../shared/rules/default-path-and-write-boundaries.md',
    '../shared/rules/advanced-mode-gates.md'
  )
  'cap4k-generation' = @(
    '../shared/rules/core-positioning.md',
    '../shared/rules/ownership-and-generation-flow.md'
  )
  'cap4k-implementation' = @(
    '../shared/rules/default-path-and-write-boundaries.md',
    '../shared/rules/ownership-and-generation-flow.md'
  )
  'cap4k-service-integration' = @(
    '../shared/rules/default-path-and-write-boundaries.md',
    '../shared/rules/naming-layout-and-testing.md'
  )
  'cap4k-verification' = @(
    '../shared/rules/default-path-and-write-boundaries.md',
    '../shared/rules/ownership-and-generation-flow.md',
    '../shared/rules/naming-layout-and-testing.md'
  )
}

foreach ($skillName in $requiredSkillRefs.Keys) {
  $skillFile = Join-Path 'skills' (Join-Path $skillName 'SKILL.md')
  $skillText = Get-Content -LiteralPath $skillFile -Raw
  foreach ($requiredRef in $requiredSkillRefs[$skillName]) {
    if ($skillText -notlike "*$requiredRef*") {
      throw "$skillName SKILL.md is missing shared rule reference: $requiredRef"
    }
  }
}
```

- [ ] **Step 3: Add shared-core phrase checks**

After `$combined = $allText -join "`n"` and before `$forbiddenPatterns = @(`, add:

```powershell
$sharedCoreCombined = $sharedRulePaths |
  ForEach-Object { Get-Content -LiteralPath $_ -Raw } |
  Join-String -Separator "`n"

$requiredSharedCorePatterns = @(
  'One command path may persist only one aggregate root',
  'zero-trust validation',
  '`cap4kPlan` establishes `plan.json` ownership before `cap4kGenerate`',
  'Copied generated snapshots are evidence only',
  'Start from the conservative cap4k default path',
  'File name plus directory should make the role inferable',
  'domain behavior tests and application orchestration tests'
)

foreach ($pattern in $requiredSharedCorePatterns) {
  if (-not $sharedCoreCombined.Contains($pattern)) {
    throw "Missing required shared core wording: $pattern"
  }
}
```

- [ ] **Step 4: Verify validator syntax**

Run:

```powershell
powershell -NoProfile -Command '$null = [scriptblock]::Create((Get-Content -Raw "skills/scripts/validate-cap4k-skills.ps1")); "validator syntax ok"'
```

Expected output:

```text
validator syntax ok
```

## Task 5: Run Full Validation

**Files:**

- No edits expected.

- [ ] **Step 1: Run skill validation**

Run:

```powershell
powershell -NoProfile -ExecutionPolicy Bypass -File skills/scripts/validate-cap4k-skills.ps1
```

Expected output:

```text
cap4k skill validation passed for 7 skills.
```

- [ ] **Step 2: Run stale runtime dependency scan**

Run:

```powershell
rg -n "read docs/public/authoring during normal operation|cap4k-runtime-integration|runtime-integration|src-generated/main/kotlin" skills AGENTS.md
```

Expected: no matches. `rg` exits with code `1` when there are no matches.

- [ ] **Step 3: Run shared-core activation scan**

Run:

```powershell
rg -n "../shared/rules" skills/cap4k-modeling/SKILL.md skills/cap4k-generation/SKILL.md skills/cap4k-implementation/SKILL.md skills/cap4k-service-integration/SKILL.md skills/cap4k-verification/SKILL.md
```

Expected: each focused skill except the router and generated-output-review has at least two shared-rule references.

- [ ] **Step 4: Run whitespace validation**

Run:

```powershell
git diff --check
```

Expected: no output and exit code `0`.

- [ ] **Step 5: Inspect changed files**

Run:

```powershell
git status --short
```

Expected: changed files are limited to:

```text
?? docs/superpowers/plans/2026-05-19-cap4k-skill-shared-core.md
?? docs/superpowers/specs/2026-05-19-cap4k-skill-shared-core-design.md
?? skills/shared/
 M skills/cap4k-authoring/SKILL.md
 M skills/cap4k-generation/SKILL.md
 M skills/cap4k-implementation/SKILL.md
 M skills/cap4k-implementation/rules/mediator-and-uow.md
 M skills/cap4k-implementation/workflows/implement-command-slice.md
 M skills/cap4k-modeling/SKILL.md
 M skills/cap4k-modeling/rules/tactical-modeling.md
 M skills/cap4k-service-integration/SKILL.md
 M skills/cap4k-verification/SKILL.md
 M skills/cap4k-verification/rules/test-strategy.md
 M skills/scripts/validate-cap4k-skills.ps1
```

## Task 6: Commit

**Files:**

- All files changed by Tasks 1-5.

- [ ] **Step 1: Review final diff summary**

Run:

```powershell
git diff --stat
```

Expected: diff is concentrated in `docs/superpowers/**` and `skills/**`.

- [ ] **Step 2: Commit changes**

Run:

```powershell
git add docs/superpowers/specs/2026-05-19-cap4k-skill-shared-core-design.md docs/superpowers/plans/2026-05-19-cap4k-skill-shared-core.md skills
git commit -m "docs: add shared cap4k skill core"
```

Expected: commit succeeds on branch `feature/skill-shared-core`.

## Self-Review

- Spec coverage: Tasks 1-4 implement shared core files, activation, focused hardening, and validation enforcement. Task 5 verifies runtime self-containment and line discipline.
- Placeholder scan: This plan intentionally avoids placeholder terms and gives exact file paths, content, commands, and expected output.
- Type/path consistency: The plan uses existing `cap4k-service-integration` naming and does not reintroduce `cap4k-runtime-integration`.
