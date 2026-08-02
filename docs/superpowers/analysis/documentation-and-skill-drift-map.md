# Documentation And Skill Drift Map

## Purpose

本页记录当前 public docs 与 repo-local cap4k authoring skill 的维护边界和高风险漂移点。它是维护地图，不是用户教程，也不复制 Agent API 或 generator/runtime contract。

## Current Facts

- Human-facing 文档入口是根 `README.md` 与 `docs/public/`。
- 当前 generator 文档位于 `docs/public/generator/`；Gradle、Agent API、DSL、input schema 和 output contract 位于 `docs/public/reference/`。
- Agent-facing canonical source 只有 `skills/cap4k-authoring/` 下的 5 个文件：`SKILL.md`、`routing.yaml` 与三个按需 reference。
- `skills/cap4k-authoring/SKILL.md` 是薄入口；`routing.yaml` 是唯一操作路由，不存在多阶段 specialist skill 链或第二套路由表。
- Skill 默认 manifest-first：先运行 `cap4kAgentSnapshot` 并读取 `build/cap4k/agent/manifest.json`，再按 operation route 加载 project、capabilities、inputs、ownership、runtime、analysis 或 diagnostics 分区。
- Agent API 的 supported catalog 表示 plugin/已成功加载 extension 的能力；effective project view 表示当前项目是否 configured、ready、blocked 或 not-applicable。文档不得混淆二者。
- Bootstrap 子系统、任务、DSL、markers、guard、slots、fixtures 与 authoring workflow 已退役；官方 GitHub Template 或人工结构是项目初始化路径。
- 普通 generator 的 checked-in skeleton、generated-source ownership、`SKIP`、handwritten slot 与 managed-field handler slot 仍然有效，不能因 Bootstrap 退役一并删除或写成过时。
- Domain Event 的当前边界是不可变历史事实 payload；Aggregate/Entity 引用必须继续被 runtime 拒绝。
- Analyzer output 是观察证据而非业务真相，不得作为自动改写 generator input 的授权。
- 当前只保留 repo-local skill source；installer、分发副本、自动同步和 MCP adapter 都不是本次 current contract。

## Source Anchors

- `README.md`：公共文档总入口。
- `docs/public/index.md`：公共文档信息架构。
- `docs/public/generator/index.md`：生成器用户入口。
- `docs/public/reference/gradle-plugin.md`：当前 Gradle tasks 和 DSL。
- `docs/public/reference/agent-api.md`：manifest-first snapshot、状态、freshness 与凭据边界。
- `skills/cap4k-authoring/SKILL.md`：agent 操作边界。
- `skills/cap4k-authoring/routing.yaml`：唯一 route source。
- `skills/scripts/validate-cap4k-skills.ps1`：薄 skill 的可执行结构与漂移检查。
- `docs/comet/changes/complete-ddd-authoring-workflow/specs/complete-ddd-authoring-workflow/spec.md`：当前 change 的已确认边界。

## Contracts

- 当前源码、测试、provider descriptor 和 versioned machine contract 优先于 prose；文档与 skill 只解释并链接这些事实。
- Public docs 面向学习和使用 cap4k 的人；skill 面向已经具备通用软件工程与基础 DDD 能力的 agent。二者可以共享术语，但不应互相复制大量易漂移的 capability catalog。
- Skill 不治理组织级战略 DDD，不强制 Strategic Workspace、design dossier、phase chain、rollback workflow 或 cap4k-specific approval state machine。
- Skill 不静态承诺某个 carrier/provider 一定可用；必须读取 Agent API 的 capability/effective state。
- `invalid`、`partial`、`unavailable`、freshness 和 diagnostic 语义只能按 Agent API contract 描述，不能从普通日志措辞推断。
- 历史 specs、plans、dated audits 和 GitHub issue text 只能提供决策背景或漂移线索。若文件自称 current，必须带快照范围或醒目的历史声明。

## Validation Contract

`skills/scripts/validate-cap4k-skills.ps1` 依次运行：

- `structure-and-routing.ps1`：固定 5 文件、固定 6 route、合法 Agent API 分区和有效 required reads；
- `thin-surface.ps1`：always-read 与总体字节预算；
- `active-term-scan.ps1`：skill/AGENTS 中已退役任务、旧 specialist route 与 forced rollback 术语；
- `link-check.ps1`：检查 `skills/`、`docs/public/`、根 `README.md` 与 `cap4k-plugin-pipeline-gradle/README.md` 的本地 Markdown 链接和可识别的 Markdown 路径 code span。

该验证器覆盖薄 skill 结构和主要 active authoring 文档入口，但不是全仓库文档链接检查器；`docs/reviews/`、`docs/superpowers/analysis/` 与其他 module README 仍需对变更文件执行 targeted link/term scan。

## Drift Watch

- 已退役 Bootstrap 名称只能出现在历史说明、负向兼容测试或明确的“不得使用”边界中，不能列入 current tasks/capabilities。
- 已删除 specialist skills（business discovery、tactical modeling、technical design、generator inputs、generation review、handwritten implementation、verification audit、service integration）不能再作为 current route 或必读文件。
- `cap4kPlan`、`cap4kGenerate`、`cap4kGenerateSources`、`cap4kAnalysisPlan`、`cap4kAnalysisGenerate` 和 `cap4kAgentSnapshot` 的名称、输出路径与依赖关系必须回到当前 Gradle plugin 源码和测试验证。
- `sources.irAnalysis.enabled`、`generators.flow.enabled`、`generators.drawingBoard.enabled`、KSP plan/generate 依赖和 spaced output paths 属于已知旧措辞，除非代码重新引入。
- `enum-manifest`、design entries、value-object manifest、DB schema 与 IR analysis 是 input/source 还是 generator/output，必须以 provider descriptor 和 reference schema 为准。
- 任何把 Analyzer 写成业务事实来源、把 supported catalog 写成 project ready、或把已有 plan 写成 live schema fresh 的内容都属于语义漂移。

## Verification

从仓库根目录运行：

```powershell
pwsh -NoLogo -NoProfile -File skills/scripts/validate-cap4k-skills.ps1
rg -n "cap4kBootstrap|cap4k\.bootstrap|cap4k-plugin-pipeline-bootstrap|cap4k-business-discovery|cap4k-tactical-modeling|forced-rollback" README.md docs/public docs/reviews cap4k-plugin-pipeline-gradle/README.md
rg -n "sources\.irAnalysis\.enabled|generators\.flow\.enabled|generators\.drawingBoard\.enabled|analysis plan\.json|cap4k code analysis" README.md docs/public skills
```

命中不一定是错误；必须区分 current capability claim、明确 retirement warning、负向测试与历史快照。

## Not Covered

- 组织级 DDD 决策与 Strategic Workspace。
- 独立官方 Template 仓库的 skill 集成。
- MCP、installer 或正式 skill distribution。
- 历史计划的逐篇迁移；历史文件通过快照声明保留时，不作为 current contract。
