# Cap4k Authoring Skill 中文审核

> 本文件审核当前 repo-local `skills/cap4k-authoring/`，不参与 skill 发现，也不是另一份运行时规则。
> 当前权威仍是 `SKILL.md`、`routing.yaml` 与三个按需 reference；旧的多阶段完整 authoring skill 系统已经删除。

## 当前定位

`cap4k-authoring` 是薄的 agent 路由器和框架现场手册，不是 DDD 流程引擎。

- 人与组织负责领域调研、统一语言、子域与限界上下文判断、优先级和最终决策。
- Agent 可以与人讨论业务意图并运用通用 DDD 能力，但不要求建立 Strategic Workspace、固定战略档案或 cap4k 专属审批流程。
- Skill 只保留难以从通用工程知识稳定推导的 cap4k 事实：机器能力发现、战术载体提示、输入与任务、输出所有权、运行时/provider 边界、分析证据边界和高风险失败解释。
- 项目初始化由官方 GitHub Template、团队模板或人工结构负责；已退役的 Bootstrap 子系统不是当前能力。

## 当前文件面

当前运行时 skill 固定为 5 个文件：

| 文件 | 职责 | 加载方式 |
| --- | --- | --- |
| `skills/cap4k-authoring/SKILL.md` | 启动步骤和不可违反的操作边界 | 始终读取 |
| `skills/cap4k-authoring/routing.yaml` | 唯一操作路由表 | 始终读取 |
| `references/tactical-carriers.md` | 战术载体选择提示 | 选择 carrier/input 时按需读取 |
| `references/ownership-boundaries.md` | 生成、所有权和手写边界 | 计划、生成或实现时按需读取 |
| `references/runtime-analysis-boundaries.md` | 运行时/provider/分析证据边界 | 实现、分析或验证时按需读取 |

不存在 specialist skill 链、阶段状态机、forced rollback workflow、兼容别名或安装分发副本。`routing.yaml` 是唯一的路由真相。

## 当前操作路由

| route id | 用途 | Agent API 分区 | 按需 reference |
| --- | --- | --- | --- |
| `inspect-project` | 检查项目形状与可用能力 | project、capabilities、inputs、diagnostics | 无 |
| `select-carrier-input` | 把实现意图映射到当前支持的载体与输入 | capabilities、inputs、ownership、diagnostics | tactical carriers |
| `plan-generate` | 审查计划并执行结构生成 | inputs、ownership、diagnostics | ownership boundaries |
| `implement-owned-logic` | 判断可写位置并实现长期业务逻辑 | ownership、runtime、diagnostics | ownership + runtime/analysis boundaries |
| `inspect-analysis` | 读取 flow/drawing-board 等分析证据 | analysis、diagnostics | runtime/analysis boundaries |
| `verify-diagnose` | 验证变更或诊断生成失败 | 全部详情分区 | ownership + runtime/analysis boundaries |

路由针对一次 cap4k 操作，而不是强迫用户进入固定的业务发现、战略设计、技术设计、实现和验收阶段链。

## Agent API 读取纪律

1. 先运行或刷新只读的 `cap4kAgentSnapshot`。
2. 首先读取 `build/cap4k/agent/manifest.json`。
3. 按 route 只加载需要的分区，避免把全部框架事实塞进上下文。
4. 以 `capabilities.json` 的 supported catalog 判断当前 plugin 版本能做什么，以 effective project view 判断这个项目现在是否 ready；两者不能混淆。
5. `invalid` 快照是失败任务留下的诊断证据；`partial` 可以是有效项目缺少可选分区后的成功结果。若 Gradle 在任务启动前就失败，不得声称存在快照。

## 核心事实审核

### 战术载体

- Aggregate/Entity 管理一致性边界；Value Object 与 Strong ID 表达值和类型安全身份。
- Command 改变事实，Query 只观察，Capability 通过防腐边界调用外部所有者。
- Domain Event 必须是显式、不可变的历史事实字段，不能持有 Aggregate 或 Entity。
- Integration Event 是跨服务或限界上下文的稳定 Published Language；业务 subscriber 解释类型化事实并委托状态变更，传输入口属于 runtime adapter。
- Factory 创建 root，Repository 装载、访问并显式删除 root，Application 编排 use case。
- Saga/Process Manager、Event Sourcing、full CQRS 与 semantic module enforcement 只能按机器目录实际报告为 provider/extension/unsupported，不能由 skill 静态承诺。

### 生成与所有权

- Generator input 是 author-owned source；plan、diagnostics、analysis 与可视化输出是证据，不是业务源真相。
- `CHECKED_IN_SOURCE` 第一次物化后归项目源码所有，现有文件由 `SKIP` 保护；需要重生时应显式删除、重新生成并进行版本控制审查。
- `GENERATED_SOURCE` 属于 build，可替换，不能承载长期手写逻辑。
- 缺少 generator-supported 结构时应先修改输入并重跑 plan，不得手写平行 skeleton 只为通过编译。
- 普通 handwritten slot、managed-field handler slot 和 `ConflictPolicy.SKIP` 仍是当前生成器能力，不属于已删除的 Bootstrap 机制。

### 运行时与分析

- Domain 持有不变量，Application 编排 use case，Adapter 映射协议，Start 负责装配。
- 外层 Command 拥有事务与自动 Unit of Work 稳定化；业务代码不能定位或主动 flush Unit of Work。
- reliable Command、持久化/延迟 Domain Event 等行为取决于相应 provider，必须读取当前机器能力目录确认。
- Analyzer 是观察证据，不证明业务意图、战略正确性、事务提交、消息投递、重试或补偿，也不能自动改写生成输入。
- 只有 snapshot identity 证明配置和本地输入匹配时，已有 plan/analysis evidence 才能报告为 fresh；否则必须使用 stale、unknown 或 missing。

## 人工审核点

Skill 不创建额外治理流程，但正常工程协作仍需要人工承担：

- 对业务意图、领域边界与关键模型决策作最终确认；
- 对 generator plan、生成 diff、输出所有权和手写位置进行 change review；
- 对实现后的行为、验证证据和未证明边界进行验收。

## 可执行验证

从仓库根目录运行：

```powershell
pwsh -NoLogo -NoProfile -File skills/scripts/validate-cap4k-skills.ps1
```

校验器验证固定 5 文件结构、6 个 operation route、Agent API 分区名、按需 reference、always-read/总字节预算、本地 Markdown 链接以及已退役活动术语。它验证 skill 结构与漂移约束，不证明领域设计或项目行为正确。
