# Documentation And Skill Drift Map

## Purpose

本页是 issue #98 Phase 1 的 public docs 与 cap4k skills 漂移审计地图。它为维护 agent 提供当前文件位置、边界和高风险旧词索引，不是 Phase 2 public docs 设计，也不是 Phase 3 skill runtime 设计。

## Current Facts

- Public docs 当前位于 repo root `README.md` 和 `docs/public/`。它们面向 human cap4k users，Phase 2 会重写。
- `docs/public/` 当前包含 `authoring/` 和 `reference/` 文档树，包括 getting started、framework positioning、authoring workflow、generator docs、advanced topics、examples 和 `reference/generator-dsl.md`。
- Skills 当前位于 `skills/`。它们面向使用 cap4k 编写业务系统的 agents，Phase 3 会重写。
- `skills/` 当前包含 focused skills：`cap4k-authoring`、`cap4k-generated-output-review`、`cap4k-generation`、`cap4k-implementation`、`cap4k-modeling`、`cap4k-service-integration`、`cap4k-verification`，以及 `skills/shared/rules/` 和 `skills/scripts/validate-cap4k-skills.ps1`。
- `skills/scripts/validate-cap4k-skills.ps1` 要求至少 7 个 skill directories，要求每个 `SKILL.md` 存在、行数不超过 100、包含 frontmatter，并检查本地 markdown links。
- `validate-cap4k-skills.ps1` 要求 shared rules 存在：`core-positioning.md`、`default-path-and-write-boundaries.md`、`ownership-and-generation-flow.md`、`naming-layout-and-testing.md`、`advanced-mode-gates.md`。
- `validate-cap4k-skills.ps1` 要求 focused skills 引用指定 shared rules，并扫描 skills、AGENTS.md、public authoring docs、analysis docs、部分 runtime source，阻止多类 stale wording。
- Installed skills 必须在 runtime 自包含。安装后的 skill 不应依赖 `docs/superpowers/analysis/`、`docs/public/`、GitHub issues、historical specs/plans、Context7 或 cap4k source repository 才能正常执行。
- Context7 compatibility 是未来 public-doc concern，不是 skill runtime dependency。Skill 可以被安装后离线/脱离 repo 使用，因此不能要求 runtime 去查询 Context7 才能理解 cap4k authoring contract。
- 当前 active public authoring docs 中已有 `docs/public/authoring/project-authoring-workflow.md` 提到 `sources.irAnalysis.inputDirs`、`build/cap4k code analysis` 和 `build/cap4k/analysis plan.json`。这些 spaced output path wording 是高风险漂移点，后续 Phase 2 需要按当前 code map 复核。
- 当前 active analysis map `docs/superpowers/analysis/pipeline-and-gradle-map.md` 已指出 stale wording：`kspKotlin`、`sources.irAnalysis.enabled`、`generators.flow.enabled`、`generators.drawingBoard.enabled`，并给出当前 `sources.irAnalysis.inputDirs` 和 `analysis-plan.json` 事实。
- 旧 dated analysis files 仍包含历史 drift terms，例如 `client/cli`、`design validator`、`kspKotlin`、old enabled switches。这些文件是历史分析证据，不应作为 Phase 2/3 的当前 authoring contract。
- issue #98 高风险 drift terms 需要持续扫描：KSP plan/generate wording、old enabled switches、enum-manifest-as-generator wording、design validator、client/cli handler、spaced output paths。
- 本页只做 drift-audit map；详细 public docs 信息架构和 skill runtime 文案设计属于后续 Phase 2/3。

## Source Anchors

- `README.md`: public docs 入口和 human-facing framing。
- `docs/public/`: public authoring/reference docs root，Phase 2 rewrite scope。
- `skills/`: cap4k agent skill root，Phase 3 rewrite scope。
- `skills/scripts/validate-cap4k-skills.ps1`: skill validation contract、自包含约束的可执行检查入口、stale wording scan。
- `docs/superpowers/analysis/pipeline-and-gradle-map.md`: 当前 pipeline/Gradle 事实和 stale KSP/enabled switch drift notes。
- `docs/superpowers/analysis/2026-05-11-*.md`: dated historical analysis files；只能作为历史 drift evidence，不能覆盖 current code facts。

## Contracts

- Code wins over analysis。Public docs 和 skills 的事实必须先回到 current source、tests、build files 验证，再更新文档。
- Analysis docs 是维护 agent 的 current code map/fact index，不是 public docs。
- Public docs 面向 human cap4k users；它们可以解释工作流、示例和迁移，但不能用旧分析替代当前代码事实。
- Skills 面向 authoring agents；installed skills 必须 self-contained，不能把 `docs/public/`、`docs/superpowers/analysis/`、GitHub issue、historical spec/plan、Context7 或 cap4k source checkout 当成 runtime dependency。
- Phase 2 可处理 Context7 兼容性、public docs navigation 和人类用户阅读路径；Phase 3 必须保持 skill runtime independence。
- Dated analysis files 中出现的旧 terms 只能作为 drift watch 信号；新 active maps 和后续 public/skill 文案必须明确 current contract。

## Change Impact

- Phase 2 修改 `README.md` 或 `docs/public/` 时，要用本页 drift terms 做 targeted scan，避免把历史 analysis 旧词带入 public docs。
- Phase 3 修改 `skills/` 时，要继续运行 `skills/scripts/validate-cap4k-skills.ps1`，并检查 installed skill 是否脱离 repo 仍能理解核心规则。
- 如果 public docs 引入 Context7 兼容说明，必须把它限制为 public-doc discoverability 或外部文档消费问题，不得写成 skill runtime requirement。
- 如果 current code 重新引入 KSP dependency、enabled switches、design validator 或 client/cli handler contract，本页应从 drift warning 更新为 current fact，并附上 source anchors。
- 如果 output path source 从 spaced wording 统一为 hyphenated/current path，public docs、analysis maps、skills 和 validation scan 都应同步更新。

## Verification

Run these commands from the cap4k worktree root:

```powershell
rg -n "kspKotlin|sources\.irAnalysis\.enabled|generators\.flow\.enabled|generators\.drawingBoard\.enabled|design validator|client/cli|analysis plan\.json|cap4k code analysis" README.md docs/public skills docs/superpowers/analysis
Get-Content skills/scripts/validate-cap4k-skills.ps1 -Raw
```

Useful inventory reads when changing this map:

```powershell
Get-ChildItem -Path docs/public -Recurse -File | Select-Object FullName
Get-ChildItem -Path skills -Recurse -File | Select-Object FullName
```

## Drift Watch

- KSP plan/generate wording: old docs may say `cap4kPlan` / `cap4kGenerate` depend on `kspKotlin`; current active pipeline map says this is stale unless code reintroduces it。
- Old enabled switches: `sources.irAnalysis.enabled`、`generators.flow.enabled`、`generators.drawingBoard.enabled` are stale unless current DSL source reintroduces those properties。
- enum-manifest-as-generator wording: verify whether `enum-manifest` is a source ID, generated source input, generator output, or public concept before documenting it。
- design validator: old `application.validators` wording may not represent current generator/design contract; verify from current source before public or skill text uses it。
- client/cli handler: old analysis uses this language in dated files; public docs and skills should prefer current runtime/request boundary language unless code proves this is still a supported authoring concept。
- Spaced output paths: `build/cap4k code analysis` and `build/cap4k/analysis plan.json` are high-risk wording because current task maps use `build/cap4k/analysis-plan.json` and source-verified paths。
- `cap4k code analysis` wording should be checked against actual task outputs and generator source before Phase 2 public docs keep it。
- Historical specs/plans and GitHub issue text are not current contracts; use them to seed scans, not to settle facts。

## Not Covered

- Phase 2 public docs table of contents, tutorial prose, Context7 packaging details, or human onboarding copy。
- Phase 3 skill decomposition, trigger descriptions, runtime package format, or installed skill distribution flow。
- Full validation script redesign or new stale-pattern enforcement implementation。
- Detailed source maps for pipeline, runtime, generator template IDs, or release workflow; use the dedicated active maps for those topics。