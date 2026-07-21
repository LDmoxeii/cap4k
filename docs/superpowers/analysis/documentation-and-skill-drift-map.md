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
- Context7 兼容性是后续 public docs 需要处理的问题，不是 skill 运行时依赖。Skill 安装后可以离线、脱离 repo 使用，因此不能要求运行时查询 Context7 才能理解 cap4k authoring contract。
- 当前 active public authoring docs 中已有 `docs/public/authoring/project-authoring-workflow.md` 提到 `sources.irAnalysis.inputDirs`、`build/cap4k code analysis` 和 `build/cap4k/analysis plan.json`。这些 spaced output path wording 是高风险漂移点，后续 Phase 2 需要按当前 code map 复核。
- 当前 active analysis map `docs/superpowers/analysis/pipeline-and-gradle-map.md` 已指出 stale wording：`kspKotlin`、`sources.irAnalysis.enabled`、`generators.flow.enabled`、`generators.drawingBoard.enabled`，并给出当前 `sources.irAnalysis.inputDirs` 和 `analysis-plan.json` 事实。
- 旧 dated analysis files 已在 Phase 1 删除；其中有用的 drift terms 已由 active maps 和 git history 承接，不应作为 Phase 2/3 的当前 authoring contract 或本地证据。
- issue #98 高风险 drift terms 需要持续扫描：KSP plan/generate wording、old enabled switches、enum-manifest-as-generator wording、design validator、client/cli handler、spaced output paths。
- 本页只做 drift-audit map；详细 public docs 信息架构和 skill runtime 文案设计属于后续 Phase 2/3。

## Source Anchors

- `README.md`: public docs 入口和 human-facing framing。
- `docs/public/`: public authoring/reference docs root，Phase 2 rewrite scope。
- `skills/`: cap4k agent skill root，Phase 3 rewrite scope。
- `skills/scripts/validate-cap4k-skills.ps1`: skill validation contract、自包含约束的可执行检查入口、stale wording scan。
- `docs/superpowers/analysis/pipeline-and-gradle-map.md`: 当前 pipeline/Gradle 事实和 stale KSP/enabled switch drift notes。
- `docs/superpowers/analysis/source-and-generator-contract-map.md`: 当前 source/generator contract 和 drift anchor。
- `docs/superpowers/analysis/analysis-flow-and-verification-map.md`: 当前 analysis flow、verification contract 和 stale wording anchor。
- `docs/superpowers/analysis/release-map.md`: 当前 release workflow 和发布相关 drift anchor。

## Contracts

- 代码事实优先于 analysis。Public docs 和 skills 的事实必须先回到当前源码、测试和 build files 验证，再更新文档。
- Analysis docs 是维护 agent 使用的当前代码地图和事实索引，不是 public docs。
- Public docs 面向 human cap4k users；它们可以解释工作流、示例和迁移，但不能用旧分析替代当前代码事实。
- Skills 面向 authoring agents；安装后的 skills 必须自包含，不能把 `docs/public/`、`docs/superpowers/analysis/`、GitHub issue、historical spec/plan、Context7 或 cap4k source checkout 当成运行时依赖。
- Phase 2 可处理 Context7 兼容性、public docs navigation 和人类用户阅读路径；Phase 3 必须保持 skill 运行时独立。
- Dated analysis files 中出现的旧 terms 只能作为漂移观察信号；新的 active maps 和后续 public/skill 文案必须明确当前 contract。

## Change Impact

- Phase 2 修改 `README.md` 或 `docs/public/` 时，要用本页 drift terms 做 targeted scan，避免把历史 analysis 旧词带入 public docs。
- Phase 3 修改 `skills/` 时，要继续运行 `skills/scripts/validate-cap4k-skills.ps1`，并检查 installed skill 是否脱离 repo 仍能理解核心规则。
- 如果 public docs 引入 Context7 兼容说明，必须把它限制为 public-doc discoverability 或外部文档消费问题，不得写成 skill runtime requirement。
- 如果 current code 重新引入 KSP dependency、enabled switches、design validator 或 client/cli handler contract，本页应从 drift warning 更新为 current fact，并附上 source anchors。
- 如果 output path source 从 spaced wording 统一为 hyphenated/current path，public docs、analysis maps、skills 和 validation scan 都应同步更新。

## Verification

从 cap4k worktree 根目录运行这些命令：

```powershell
rg -n "kspKotlin|sources\.irAnalysis\.enabled|generators\.flow\.enabled|generators\.drawingBoard\.enabled|design validator|client/cli|analysis plan\.json|cap4k code analysis" README.md docs/public skills docs/superpowers/analysis
Get-Content skills/scripts/validate-cap4k-skills.ps1 -Raw
```

修改本地图时可辅助读取这些清单：

```powershell
Get-ChildItem -Path docs/public -Recurse -File | Select-Object FullName
Get-ChildItem -Path skills -Recurse -File | Select-Object FullName
```

## Drift Watch

- KSP plan/generate wording：旧文档可能说 `cap4kPlan` / `cap4kGenerate` 依赖 `kspKotlin`；当前 active pipeline map 已标记这类说法为过时，除非代码重新引入该行为。
- Old enabled switches：`sources.irAnalysis.enabled`、`generators.flow.enabled`、`generators.drawingBoard.enabled` 已过时，除非当前 DSL source 重新引入这些 properties。
- enum-manifest-as-generator wording：记录 `enum-manifest` 前，先验证它是 source ID、generated source input、generator output，还是 public concept。
- design validator：旧的 `application.validators` wording 可能不代表当前 generator/design contract；public docs 或 skill 文案使用前，必须先从当前 source 验证。
- client/cli handler：旧 analysis 在 dated files 中使用该语言；public docs 和 skills 应优先使用当前 runtime/request boundary language，除非代码证明它仍是受支持的 authoring concept。
- Spaced output paths：`build/cap4k code analysis` 和 `build/cap4k/analysis plan.json` 是高风险 wording，因为当前 task maps 使用 `build/cap4k/analysis-plan.json` 和源码验证过的路径。
- Phase 2 public docs 保留 `cap4k code analysis` wording 前，必须先用实际 task outputs 和 generator source 校验。
- Historical specs/plans 和 GitHub issue text 不是当前 contract；它们只能用于提供扫描线索，不能用于判定事实。

## Not Covered

- Phase 2 public docs 的目录、教程正文、Context7 packaging details 或 human onboarding copy。
- Phase 3 skill decomposition、trigger descriptions、runtime package format 或 installed skill distribution flow。
- 完整 validation script redesign 或新的 stale-pattern enforcement implementation。
- Pipeline、runtime、generator template IDs 或 release workflow 的详细 source maps；这些主题应使用对应的 dedicated active maps。
