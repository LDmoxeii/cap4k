# Cap4k Skills 中文审核版

> 本文件是 `skills/` 目录的中文审核稿，便于人工审阅英文 skill 文档的意图、路由和约束。
> 它不是运行时 skill 文件，不参与 agent skill 发现，也不替代英文原件。
>
> 原始目录：`skills/`
> 覆盖范围：`*.md`、`routing.yaml`、校验脚本职责摘要。
> 未做事项：不把 PowerShell 校验脚本逐行翻译成中文可执行版本。

## 审核重点

- `skills/cap4k-authoring/routing.yaml` 是唯一的路由真相；不要在 Markdown 里维护第二套路由表。
- 完整 authoring 流程是：业务发现 -> 战术建模 -> 技术设计 -> 生成器输入 -> 生成审查 -> 手写实现 -> 验证审计。
- 生成后必须停下来给人审查生成 diff、输出所有权和 plan 对齐情况；不能自动继续到手写实现。
- 结构性文件优先由 cap4k 生成器产生；缺失的生成支持应回滚到技术设计或生成器输入，而不是手写平行结构。
- Repository 负责聚合读取、访问、装载；Unit of Work 负责持久化意图、删除意图、提交和保存边界。
- 传输层 HTTP/message 消费、解析、注册和分发属于 framework/runtime；业务 subscriber 只处理类型化外部事实的解释、幂等、语义翻译和委托。
- 验证声明必须匹配证据模式：`static-only` 不能被说成完整验证，跳过项必须披露。

## 入口路由

### `skills/cap4k-authoring/SKILL.md`

这是 cap4k 业务 authoring agent 的入口路由器，不是完整规则书。

始终读取：

1. `routing.yaml`
2. `../shared/workflows/forced-rollback.md`

会话纪律：

- 每个新的用户任务都要重新读取本文件和 `routing.yaml`。
- 不要把 public docs、analysis maps、issues、Context7 或历史 spec 当成运行时指令。
- 行动前先路由到当前阶段 skill。
- 涉及结构创建或修改时，确认被路由 workflow 会加载 `../shared/workflows/skeleton-generation-gate.md`。
- spec 级端到端示例使用 `routing.yaml` 中的 `content-studio-dry-run` 路由。

优先级：

1. 当前用户指令和明确项目范围。
2. `routing.yaml` 阶段和 specialist 路由。
3. 被路由 skill 的规则和 workflow。
4. 现有业务项目约定。
5. 领域决策仍然需要人工审核。

### `skills/cap4k-authoring/routing.yaml`

路由总览：

| route id | 触发场景 | 首先进入 | 后续/重点 |
|---|---|---|---|
| `full-authoring-flow` | 从零构建、端到端、完整 authoring、新 cap4k 项目 | `cap4k-business-discovery` | 依次进入战术建模、技术设计、生成器输入、生成审查、手写实现、验证审计 |
| `content-studio-dry-run` | 干跑 skill workflow，展示 Content Studio 端到端 authoring 路径 | `cap4k-authoring` | 读取干跑参考和强制回滚；覆盖全阶段回滚目标 |
| `business-discovery` | 先澄清业务想法、业务 slice、参与者/状态/外部事实 | `cap4k-business-discovery` | 只回滚到业务发现 |
| `tactical-modeling` | 建模聚合边界、映射 DDD carrier、决定 Command/Query/Event/Saga/Value Object | `cap4k-tactical-modeling` | 必读 tactical affordance map 和 forced rollback |
| `technical-design` | 编写技术设计、放置层/模块、决定 skeleton 预期和手写槽位 | `cap4k-technical-design` | 必读层/runtime 边界、生成器支持 skeleton、Skeleton Generation Gate |
| `generator-inputs` | 写 `design.json`，投影为生成器输入，维护 DDL/manifest/Gradle/addon | `cap4k-generator-inputs` | 必读生成器输入真相、生成器支持 skeleton、Skeleton Generation Gate |
| `generation-review` | 审查 `cap4kPlan`、生成输出所有权、plan 后生成 | `cap4k-generation-review` | 关注 `plan.json`、`conflictPolicy`、`templateId`、生成输出 |
| `handwritten-implementation` | 填充已生成 handler/subscriber/job 内的业务逻辑 | `cap4k-handwritten-implementation` | 必须有人审过生成输出；缺 skeleton 或所有权不清时不得手写 |
| `verification-audit` | 验证 cap4k 工作、审计生成/手写所有权、产出最终证据 | `cap4k-verification-audit` | 支持 `static-only`、`focused-local`、`full-evidence` 证据强度 |
| `service-integration` | 设计 Open Host Service、处理 callback、消费外部能力、建模入站 integration event | `cap4k-service-integration` | 关注 Published Language、external fact、callback/message/integration event 边界 |

路由负信号示例：

- 只要求验证时，不走完整 authoring。
- 只审 `plan.json` 时，不进入手写实现。
- 缺失 generated skeleton 时，不直接手写 handler。
- 没有外部服务边界的本地 CRUD/application use case，不走 service integration。
- 只有 provider SDK wiring 或 adapter handler mapping，但没有 Open Host Service、Published Language、external fact 或 Integration Event 边界决策时，不走 service integration。

### `skills/cap4k-authoring/references/content-studio-dry-run.md`

Content Studio 干跑场景：

流程：草拟内容 -> 提交审核 -> 批准内容 -> 媒体处理外部事实 -> 媒体就绪 -> 发布就绪 -> 发布。

业务发现输出：

- 目标：内容只有在审批通过且媒体就绪后，才能从草稿进入已发布。
- 参与者：Content Editor、Reviewer、Publisher、Media Platform。
- 词汇：Content Draft、Review Submission、Content Approval、Media Processing Fact、Media Ready、Publication Ready、Publication。
- 状态变化：drafted、submitted for review、approved、media processing observed、media ready、publication ready、published。
- 读需求：审核队列、媒体就绪视图、发布就绪视图、已发布内容历史。
- 策略：审批通过且媒体就绪后才允许发布。
- 外部事实：媒体平台返回媒体处理结果，必须先被翻译为业务事实，再影响发布就绪。

战术建模输出：

- Aggregate：`ContentItem` 拥有编辑生命周期、审核状态、媒体就绪标记和发布状态。
- Strong ID / Value Object：`ContentId`、`MediaAssetId`、`Channel`、`ContentBody`、`PublicationWindow`。
- Commands：`DraftContent`、`SubmitContentReview`、`ApproveContent`、`RecordMediaProcessingFact`、`MarkMediaReady`、`MarkPublicationReady`、`PublishContent`。
- Queries：`ListReviewQueue`、`GetMediaReadiness`、`ListPublicationReadyContent`、`GetPublishedContentHistory`。
- Domain Events：`ContentDrafted`、`ContentReviewSubmitted`、`ContentApproved`、`MediaReadyRecorded`、`PublicationReadyMarked`、`ContentPublished`。
- 入站外部事实：`MediaProcessingCompleted` 由 application subscriber 解释，并委托给合适的 command 路径。
- 出站 Integration Event：`ContentPublishedNotice` 对下游使用 Published Language。
- Saga / scheduled reaction：发布就绪反应会检查审批和媒体就绪。
- Domain Service / Specification：`PublicationReadinessPolicy` 表达“审批 + 媒体就绪”规则。

技术设计摘录：

- `businessIntent`：只在媒体就绪后发布已审批的编辑内容。
- `ubiquitousLanguage`：审核、媒体就绪、发布就绪和发布必须保持为不同概念。
- `aggregateBoundaries`：`ContentItem` 是生命周期聚合；媒体处理仍是外部事实。
- `cap4kCarriers`：Aggregate、Command、Query、Domain Event、Integration Event、Subscriber、Saga、Specification、Value Object、Strong ID。
- `cleanArchitecturePlacement`：Domain 持有生命周期不变量和就绪策略；Application 编排命令、查询和外部事实翻译；Adapter 映射协议和外部 payload；Start assembly 不承载业务语义。
- `generatorInputPlan`：通过受支持输入面表达聚合状态、命令、查询、事件、subscriber 预期、类型 manifest 和 projection 需求。
- `skeletonExpectations`：command/query handler、subscriber shell、event carrier、policy slot、projection、adapter payload 类型应作为生成或已批准的面出现。
- `handwrittenLogicSlots`：就绪策略、命令决策逻辑、外部事实语义翻译、幂等决策、发布通知映射。
- `ownershipExceptions`：干跑没有例外；缺少生成器支持 skeleton 时回滚，不手写补洞。
- `verificationEvidence`：路由读取、plan 证据审查、所有权分类、静态 diff 审查、回滚记录。
- `rollbackTriggers`：概念不匹配、carrier 不清、输入缺失、plan 不匹配、所有权冲突、实现绕过、结构漂移、验证漂移。

生成输入预期：

- DB/schema 或 design JSON 表达 `ContentItem`、生命周期状态、审核状态、媒体就绪字段、发布状态和读 projection。
- Type manifests 定义 Strong ID、Value Object 和生命周期状态枚举。
- 输入选项标识命令、查询、Domain Event、Integration Event、subscriber、projection 和 adapter payload 的生成 skeleton family。
- 外部事实输入命名必须保持媒体处理观察与内部发布就绪之间的边界。
- 缺失预期 skeleton 证据时，回到生成器输入或技术设计。

plan 审查预期：

- plan 证据应显示每个预期 command、query、event、subscriber、projection、adapter payload 位于正确模块和所有权类别。
- conflict policy 应保留 skeleton 的生成器所有权，把手写逻辑留在已批准槽位。
- template 和 output kind 要与技术设计契约一致。
- 缺少 `MediaProcessingCompleted` 翻译面、就绪策略槽位或发布通知 carrier 都是回滚发现。
- 当 route、plan 证据、所有权和回滚说明足够人工审核后，审查停止。

停止点：

- 在 plan 和生成输出审查识别出预期生成面、所有权类别和冲突后停止。
- 不从干跑继续进入手写实现。

人工审核门：

- 人工确认业务流程和战术 carrier。
- 人工确认 generated skeleton 所有权和已批准手写槽位。
- 人工明确授权后续手写实现任务。
- 有分歧时，回到引入错误假设的最早回滚目标。

手写实现面：

- Command handler 校验允许的状态迁移，并通过已批准面发出 Domain Event。
- `PublicationReadinessPolicy` 决定已审批且媒体就绪的内容能否成为发布就绪。
- Application subscriber 解释类型化媒体外部事实，处理幂等，并委托 command。
- Query handler 塑造审核队列、媒体就绪、发布就绪和发布历史读模型。
- 出站发布通知映射使用 Published Language，不暴露内部 Aggregate 形状。

验证模式：

- 干跑只使用 `static-only`。
- 证据限制为：路由选择、必读文件、干跑场景覆盖、生成输入预期、plan 审查预期、所有权边界、回滚示例、跳过检查披露。
- 不要为该参考声称 `focused-local` 或 `full-evidence`。

回滚示例：

- 把媒体处理当成内部内容审批：回到 business discovery。
- 混淆发布就绪和发布：回到 tactical modeling。
- `RecordMediaProcessingFact` 没有支持输入面：回到 generator inputs。
- plan 缺少 subscriber shell 或 readiness policy slot：回到 generator inputs 或 technical design。
- 编辑 generated skeleton 作为源真相：回到 generation review。
- 要求手写缺失的 generated command handler：回到 technical design。
- 把 static evidence 报告成更强证据：回到 verification audit。

## 阶段 Skill 翻译

### `skills/cap4k-business-discovery/SKILL.md`

用途：在战术建模前发现 cap4k business slice 的业务意图。用户描述业务想法、workflow、policy、external fact 或期望行为时使用。

始终读取：

1. `../shared/workflows/forced-rollback.md`
2. `workflows/discover-business-intent.md`
3. `references/business-signals.md`

规则：

- 本阶段不选择 Aggregate 边界。
- 标记高风险词，交给战术建模处理。
- 在投影生成器输入前，先询问缺失业务事实。
- 当业务 brief 连贯后，路由到 `cap4k-tactical-modeling`。

### `skills/cap4k-business-discovery/workflows/discover-business-intent.md`

输入可以是用户故事、业务目标、workflow 说明、policy 文本、external fact、callback、message、DDL 草稿或 design 草稿。

只询问缺失的业务事实，不要在本阶段要求用户选择 Aggregate、Command、Query、Event、Saga 或 generator input 细节。

要问的问题：

- 哪个业务结果应该改变或变得可见？
- 谁发起工作，谁受影响，哪个外部方参与？
- 哪些词是业务术语、状态名、策略名或外部事实？
- 哪些状态变化必须发生，哪些不能发生？
- 需要哪些读视图、列表、筛选或汇总？
- 哪些 callback、message、import/export 或外部观察会影响 workflow？
- 哪些 policy、eligibility、compensation、retry、timeout 或 approval 决策缺失？

输出形状：

```markdown
## Business Brief

- Goal:
- Actors:
- Vocabulary:
- State changes:
- Read needs:
- External facts:
- Policies:
- Open decisions:
- Risk words marked:
```

brief 必须区分用户提供的事实和假设；未决决策放入 `Open decisions`，不要投影为 tactical carrier。

风险词：

- policy / eligibility：approve、reject、qualify、allow、block、limit、quota、uniqueness、duplicate。
- workflow：submit、publish、cancel、expire、timeout、schedule、retry、recover、compensate。
- external fact：callback、webhook、message、imported、synced、provider、partner、external system。
- boundary：public contract、Published Language、Open Host Service、cross-service、outbound event。
- ownership：state owner、lifecycle、invariant、consistency、cannot change together。

退出标准：

- brief 包含目标、参与者、词汇、状态变化、读需求、外部事实、策略和 open decisions。
- 高风险词已标记给 tactical modeling。
- 阻塞业务事实已询问或明确记录为 open。
- 本阶段没有选择 Aggregate 边界或 generator input。
- 下一路由是 `cap4k-tactical-modeling`。

### `skills/cap4k-business-discovery/references/business-signals.md`

这些信号帮助识别业务意图，但不是 carrier 决策。

| 信号 | 示例 | brief 中捕获 |
|---|---|---|
| Actor intent | 编辑提交草稿审核 | actor、目标、请求的状态变化、policy 词 |
| Policy decision | 只有高级 reviewer 能批准 | policy 名、资格事实、缺失授权规则 |
| State transition | 媒体就绪后，已审批内容变成发布就绪 | 源状态、目标状态、必需事实、禁止迁移 |
| Read need | 运营需要逾期审核队列 | 读者、筛选、排序、新鲜度 |
| External fact | 媒体服务报告处理完成 | 外部方、事实名、payload 词汇、幂等关注点 |
| External capability | 发布需要 partner service 预留档期 | 外部方、请求意图、响应事实、fallback 路径 |
| Time rule | 审核三工作日后过期 | 时钟来源、timeout policy、补偿或重试预期 |
| Public boundary | partner 消费发布状态更新 | Published terms、稳定性预期、版本或兼容关注点 |

缺失事实应询问：

- 谁可以启动、批准、拒绝或取消 workflow？
- 哪些状态名是业务可见的，哪些只是实现内部？
- 如何判断重复 callback/message/request 是同一个业务事实？
- 哪个外部系统对事实有权威，哪个系统只是观察？
- 外部事实迟到、重复、冲突或缺失时怎么办？
- 哪些读视图给人、自动化或 partner 使用？
- 哪些补偿、重试、timeout、恢复行为是业务要求？
- 哪些术语必须对另一个 bounded context 或外部方稳定？

### `skills/cap4k-tactical-modeling/SKILL.md`

用途：当连贯的业务 brief 需要映射为 tactical DDD 决策、cap4k carrier、generator input 预期、rollback notes 和下一个 technical design 阶段时使用。

始终读取：

1. `../shared/references/tactical-affordance-map.md`
2. `../shared/workflows/forced-rollback.md`
3. `workflows/map-tactical-carriers.md`

规则：

- 从 business brief 开始，不从表结构或生成便利性开始。
- 每个通用 DDD 概念必须映射为 cap4k carrier，否则保留为 open decision。
- 当 business signal 没有清晰 carrier 时，记录 rollback notes。
- carrier table 连贯后才能路由到 `cap4k-technical-design`。

### `skills/cap4k-tactical-modeling/workflows/map-tactical-carriers.md`

从 `cap4k-business-discovery` 输出的 `Business Brief` 开始。如果缺少目标、参与者、词汇、状态变化、读需求、外部事实、policy 或 open decision，回到 business discovery。

当 brief 包含已标记风险词时，读取 `../references/modeling-gotchas.md`。

为每个 business signal 建一行：

| Business Signal | Cap4k Carrier | Reason | Generator Input Surface | Expected Skeleton Or Plan Evidence | Handwritten Logic Slot | Verification Evidence | Rollback Trigger |
|---|---|---|---|---|---|---|---|

使用 `../../shared/references/tactical-affordance-map.md` 作为 carrier 权威来源。不要保留泛化 DDD 标签，除非它们已经解析为 cap4k carrier、预期 skeleton 和验证证据。

Command/Query 拆分：

- 状态改变意图是 Command 候选。
- 只读视图是 Query 候选。
- Query 不得改变状态、发出业务事实或推进 workflow。
- 记录 actor、输入事实、预期状态变化和读输出形状。

Domain Event / Integration Event：

- bounded context 内部已完成业务事实用 Domain Event。
- 穿越 service、team、bounded context、callback 或 messaging 边界的稳定事实用 Integration Event。
- 不要把外部 callback 建成 Domain Event。
- 只有已完成业务事实不同才拆分 event，不因多个消费者而拆分。

Subscriber / Saga / Scheduled Reaction：

- Subscriber 用于轻薄 application reaction、过滤、幂等委托或后续 command 路由。
- Saga 只用于持久化的长流程协调、重试、恢复或补偿。
- Scheduled Reaction 用于时间、cron、timeout、补偿扫描或 polling fallback。
- 独立 reaction 和 zero-trust command 足够时，拒绝中央 listener / process-router 所有权。

Domain Service / Specification：

- Domain Service 只在领域决策跨聚合或没有自然 aggregate owner 时使用。
- Specification 用于保存聚合状态前必须守卫的 validation policy。
- 不要因为代码看起来过程式，或规则只是 UI filter，就创建它们。

回滚 notes：

- `concept mismatch -> tactical modeling`
- `unclear carrier -> technical design`
- `missing input -> generator inputs`
- `implementation bypass -> technical design`
- `structure drift -> earliest phase that introduced wrong assumption`

退出标准：

- carrier selection table 覆盖每个 business signal 和风险词。
- Command/Query、event、reaction、Domain Service、Specification 决策明确。
- 每个通用 DDD 概念映射为 cap4k carrier 或 open decision。
- rollback notes 标明后续证据冲突时回到哪里。

### `skills/cap4k-tactical-modeling/references/modeling-gotchas.md`

通用 DDD 概念必须映射到 cap4k carrier。不能只写 `aggregate`、`event`、`service`、`specification`、`workflow` 或 `boundary`，还必须记录 generator input surface、expected skeleton / plan evidence、handwritten logic 位置、verification evidence 和 rollback trigger。

高风险词：

- Boundary：aggregate、entity、value object、strong ID、reference、lifecycle、invariant、consistency。
- Use case：command、query、handler、orchestration、read model、projection。
- Event：domain event、integration event、subscriber、saga、scheduled reaction、timeout、retry、compensation、recovery。
- Service boundary：external fact、callback、webhook、message、Published Language、Open Host Service、external capability。
- Policy：eligibility、approval、quota、uniqueness、duplicate、validation、pre-save。

常见失败：

- 不要让表结构定义聚合边界，必须有业务一致性理由。
- 不要因为代码像过程式就创建 Domain Service。
- 不要在建模业务语义前选择 Value Object persistence carrier。
- 不要假装外部 callback 是 Domain Event。
- 在分类 service interaction boundary 前，不要选择传输或生成细节。
- 不要因多个 consumer 拆分 Domain Event，只有业务事实不同才拆。
- 不要把多流程所有权藏在一个中央 listener 里分发到私有业务 reaction。
- 不要建模自动 event-to-request 或 event-to-release 行为；出站 Integration Event 来自明确 application orchestration 或 subscriber 决策。

### `skills/cap4k-technical-design/SKILL.md`

用途：把已批准的 tactical model 固化为完整技术设计契约，之后才能写 generator input 或手写实现。

始终读取：

1. `../shared/rules/layer-and-runtime-boundaries.md`
2. `../shared/workflows/skeleton-generation-gate.md`
3. `workflows/write-technical-design-contract.md`

规则：

- 生成器输入开始前，填完每个 technical design contract heading。
- 层放置和 framework runtime 边界必须明确。
- 改 generator input 或手写文件前，记录 skeleton 预期。
- carrier 不清时回到 `cap4k-tactical-modeling`。

### `skills/cap4k-technical-design/workflows/write-technical-design-contract.md`

输入：

- 来自 business discovery 的连贯 brief。
- 来自 tactical modeling 的已批准 carrier selection table。
- 共享 layer/runtime boundaries 和 Skeleton Generation Gate。

流程：

1. 复制 `../references/technical-design-contract.md` 的每个 heading。
2. 根据 business brief 和 tactical carrier table 填写每个 heading。
3. 未解决事实放在 `openDecisions`，不要藏进 assumptions。
4. 对每个预期 skeleton，在编辑 generator input 或手写文件前回答 Skeleton Generation Gate 问题。
5. 对每个 carrier 记录层放置、generator input surface、handwritten slot、verification evidence 和 rollback trigger。
6. carrier 不匹配时回到 `cap4k-tactical-modeling`。
7. 缺失或不支持的 generator input 问题，只有在 contract 标明 intended carrier 后才进入 generator input 阶段。

完成门：

- `../references/technical-design-contract.md` 的每个 heading 都存在。
- 每个 heading 都有内容或 blocking open decision。
- blocking carrier / skeleton decision 未解决时，不开始 generator input authoring。
- skeleton expectations 区分 generated structure 与 handwritten logic slots。
- ownership exceptions 必须显式，不能从编译压力推断。

### `skills/cap4k-technical-design/references/technical-design-contract.md`

生成器输入开始前必须填完以下标题。未知值记录在 `openDecisions`。

- `businessIntent`：业务目标、参与者、状态变化、读需求、外部事实、policy。
- `ubiquitousLanguage`：业务术语、状态名、ID、外部事实、published terms；除非 generator input 必需，否则排除实现内部名。
- `aggregateBoundaries`：每个 Aggregate 候选、identity concept、owned Entity、不变量、生命周期状态迁移、一致性边界。
- `cap4kCarriers`：适用的 Command、Query、Domain Event、Integration Event、Subscriber、Saga、Scheduled Reaction、Domain Service、Specification、Value Object、Strong ID、External Capability、Open Host Service。
- `cleanArchitecturePlacement`：每个 carrier 放在 domain、application、adapter 或 start；framework runtime 职责和业务 application 职责分开记录。
- `generatorInputPlan`：每个预期 skeleton 的输入面：DB/schema、`design/design.json`、value-object manifest、enum manifest、Gradle extension、addons/options 或 template override。
- `skeletonExpectations`：预期 generated skeleton、plan evidence、output ownership 和 Skeleton Generation Gate 结果。
- `handwrittenLogicSlots`：业务逻辑写入 generated skeleton 的哪里，哪些逻辑仍在 domain behavior。
- `ownershipExceptions`：cap4k generation 不支持或用户 override 时的显式手写结构例外。
- `verificationEvidence`：每个决策的 static、focused-local 或 full-evidence 预期；声明必须匹配实际会产出的证据。
- `rollbackTriggers`：列出回到各阶段的触发条件。
- `openDecisions`：未解决问题、owner，以及是否阻塞 generator input authoring。

### `skills/cap4k-generator-inputs/SKILL.md`

用途：把已批准 technical design 投影到 generator inputs，例如 DB/schema、design JSON、manifests、Gradle extension settings、addons/options、template override 决策。

始终读取：

1. `../shared/rules/generator-input-source-of-truth.md`
2. `../shared/workflows/skeleton-generation-gate.md`
3. `workflows/project-generator-inputs.md`

常见路由：

- 选择 generator input surface：读 `references/input-surfaces.md`。
- 更新 `design/design.json` 或已注册 design JSON fragments：先读 `input-surfaces.md`，再读 `design-json-contract.md`。
- 更新 DB/schema DDL 注释：先读 `input-surfaces.md`，再读 `db-schema-annotations.md`。
- 更新 enum 或 value-object manifest：先读 `input-surfaces.md`，再读 `manifest-contracts.md`。

停止条件：

- 技术设计缺失或仍有未解决 carrier 决策。
- 请求的 input surface 不清楚。
- 输入会创建未被批准 design 支持的结构。
- generator inputs 尚不连贯时却请求 plan review。

### `skills/cap4k-generator-inputs/workflows/project-generator-inputs.md`

流程：

1. 读取已批准 technical design contract。
2. 扫描当前 workspace 中成熟项目输入和历史迭代材料，例如 `design/*.json`、schema DDL、manifest、Gradle extension blocks、已提交 plan evidence、过往 authoring 材料；有相关例子才读，缺失则继续。
3. 读取 `../references/input-surfaces.md`。
4. 识别必需 generator input surface。
5. 按 surface 读取具体 contract：`sources.designJson.files` -> `design-json-contract.md`；DB/schema DDL comments -> `db-schema-annotations.md`；enum/value-object manifest -> `manifest-contracts.md`。
6. 只有 design 支持 carrier、placement、ownership 和 expected skeleton 时才更新 input。
7. carrier、package、owner 或 expected skeleton 不清楚时回到 technical design。
8. 业务概念不再能清楚映射 cap4k 时回到 tactical modeling。
9. 如果仓库根有 `scripts/validate-cap4k-generator-inputs.py`，对变更输入运行它；如果没有，披露 validator 不存在，并基于 contract reference 做静态自查。
10. validation 报 `ERROR` 时，不要声称 generator inputs ready。

要记录的证据：

- 授权该 input 的技术设计章节。
- 承载生成事实的 input file 或 setting。
- 使用的 contract reference。
- 预期 plan item 或 skeleton family。
- validation 结果或 validator 缺失披露。
- input 无法表达 design 时的 rollback target。

### `skills/cap4k-generator-inputs/references/input-surfaces.md`

支持的 generator input surface：

- DB/schema
- `design/design.json`
- value-object manifest
- enum manifest
- Gradle extension
- addons/options
- template override decisions

这些 surface 只有在 technical design contract 支持 carrier、placement、ownership 和 expected skeleton 时，才能作为 source input。

source notes：

- DB/schema 承载 aggregate、entity、relation、repository、factory、specification、enum binding、unique helper、primary-key identity 事实。
- `design/design.json` 承载 command、query、client、api payload、domain event、integration event、domain service、saga contract。
- Value-object manifests 承载 JSON-backed value-object source definitions。
- Enum manifests 承载 schema type annotation 引用的 shared enum definitions。
- Gradle extension settings、addons/options、template override decisions 是 authoring infrastructure，不是业务 source truth。

身份和引用契约：

- aggregate-root primary-key metadata 默认生成 Strong ID 类型。
- 同一 context aggregate reference 使用 `@RefAggregate=<AggregateName>`，解析为目标 aggregate ID 类型。
- 本地 external-reference identity 使用 `@RefId=<TypeName>` 表示本地语言映射上游/外部概念。
- `@GeneratedValue=identity` 和 `@GeneratedValue=database-identity` 是显式 database identity 语义的 legacy compatibility signal，不是默认 aggregate ID 路径。
- identity、reference 或 generated-value 事实缺失/不清楚时，plan review 前回到 `cap4k-technical-design`。

analysis evidence boundary：

- flow / drawing-board 等 analysis output 默认是观察证据。
- analysis output 不是普通 source-generation input skeleton。
- 手工复制 drawing-board 内容时，必须先满足支持的 input contract，才能注册进 generator input surface。

### `skills/cap4k-generator-inputs/references/db-schema-annotations.md`

DB/schema DDL 注释支持：

- 表注解：`@Parent` / `@P`、`@AggregateRoot` / `@Root` / `@R`、`@ValueObject` / `@VO`、`@Ignore` / `@I`、`@DynamicInsert`、`@DynamicUpdate`。
- 列注解：`@T` / `@TYPE`、`@E` / `@ENUM`、`@RefId`、`@Deleted`、`@Version`、`@GeneratedValue=identity`、`@GeneratedValue=database-identity`、`@Managed`、`@Inherited`、`@Reference` / `@Ref`、`@Relation` / `@Rel`、`@Lazy` / `@L`、`@Count` / `@C`、`@RefAggregate`。

规则：

- presence annotation 不显式取值。
- boolean 必须是小写 `true` / `false`。
- `@Parent` / `@P` 不能与 `@AggregateRoot=true`、`@Root=true`、`@R=true` 组合。
- `@E` / `@ENUM` 需要 `@T` / `@TYPE`。
- `@Relation` / `@Rel`、`@Lazy` / `@L`、`@Count` / `@C` 需要同一列有 `@Reference` 或 `@Ref`。
- `@Relation` / `@Rel` 支持 `MANY_TO_ONE`、`ONE_TO_ONE`、`1:1`、`*:1`、`MANYTOONE`、`ONETOONE`。
- `@RefAggregate` 与 `@Reference` / `@Ref` 冲突。
- `@RefAggregate` 与 `@RefId` 冲突。

当前无效注解：

- 表：`@IdGenerator` / `@IG`、`@SoftDeleteColumn`
- 列：`@Exposed`、`@Insertable`、`@Updatable`

### `skills/cap4k-generator-inputs/references/design-json-contract.md`

范围：编辑通过 `sources.designJson.files` 注册的文件，通常是 `design/design.json`。

文档形状：

- 根 JSON 值是数组。
- 每个数组项是对象。
- `tag` 和 `name` 是必需非空字符串。
- 除 `domain_event` 外，每种 tag 都需要 `package`。

支持 tag：

- `command`
- `query`
- `client`
- `api_payload`
- `domain_event`
- `integration_event`
- `domain_service`
- `saga`

支持 public fields：

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

组合规则：

- `resultFields` 只允许在 `query`、`client`、`api_payload` 上。
- `integration_event` 需要 `eventName`。
- `eventName` 只允许在 `domain_event` 和 `integration_event` 上。
- `persist` 只允许在 `domain_event` 上。
- field 的 `type` 必须是显式类型名，不能用 `self`。
- `domain_event.fields` 中字段名 `entity` 保留。

无效字段：

- `desc`
- `requestFields`
- `responseFields`
- `traits`
- `role`
- `scope`
- `entity`

flow / drawing-board 输出可能像 design JSON，但不是自动有效的 `sources.designJson.files` 输入。手工复制后必须满足本 contract，例如 `command.resultFields` 无效，注册前要修正。

### `skills/cap4k-generator-inputs/references/manifest-contracts.md`

manifest 通过 `types` input surface 使用。不要把 `value_object` 当成普通 design JSON tag。

Enum Manifest：

- 通过 `types.enumManifest.files` 注册，不通过 `sources.enumManifest`。
- 根 JSON 是数组。
- 每项需要 `name`、`package`、`items`。
- `aggregates` 省略或空表示 shared。
- 当前支持 `aggregates` 最多标识一个 owner。
- 每个 item 需要 integer `value`、string `name`、string `desc`。
- duplicate shared names 无效；同 owner 下 duplicate names 无效。
- `generateTranslation` 不是当前字段。

Value-Object Manifest：

- 通过 `types.valueObjectManifest.files` 注册，不通过 `sources.valueObjectManifest`。
- 根 JSON 是数组。
- 每项需要 `name` 和 `package`。
- `aggregates` 省略或空表示 shared。
- 当前支持 `aggregates` 最多标识一个 owner。
- `storage` 只能是 `json`；省略表示 `json`。
- `description` 和 `fields` 可选。
- 有 `fields` 时，每个 field 需要 `name` 和 `type`。
- field 的 `nullable` 和 `defaultValue` 可选。
- duplicate shared names 无效；同 owner 下 duplicate names 无效。
- 已移除的 `scope` 和 `aggregate` 无效，使用 `aggregates`。

### `skills/cap4k-generation-review/SKILL.md`

用途：审查 cap4k plan evidence、generated output ownership、conflict policies、template IDs、addon artifacts、generation drift 和进入手写实现前的停止点。

始终读取：

1. `rules/generation-stop-policy.md`
2. `../shared/references/output-ownership-taxonomy.md`
3. `workflows/review-plan-and-generate.md`

常见路由：

- 生成前审查 plan evidence：读 `references/plan-review-gotchas.md`。
- 审查 generated output ownership 或 drift：读 `references/plan-review-gotchas.md`。

停止条件：

- plan evidence 缺失。
- ownership、conflict policy、template ID、module placement 或 output kind 不清。
- generation 已完成，generated diff 需要人工审查。

### `skills/cap4k-generation-review/workflows/review-plan-and-generate.md`

流程：

1. 读取 technical design 和 generator inputs。
2. 审查 plan/output 前读取 `../references/plan-review-gotchas.md`。
3. 只有用户指令和环境策略允许 runtime/generation 命令时，才运行 cap4kPlan。
4. 如果不允许运行 cap4kPlan，请求许可或报告精确命令，不执行。
5. 审查 `plan.json` 或 `bootstrap-plan.json`。
6. 将 output ownership 分类为 checked-in skeleton、build-owned generated source、generated snapshot/evidence、template override 或 handwritten logic。
7. 检查 `conflictPolicy`、`templateId`、module placement、`outputKind`、`generatorId`、`outputPath`、`resolvedOutputRoot`。
8. 如果手写逻辑会被覆盖、所有权不清或 plan output 不匹配技术设计，则停止。
9. 只有用户指令和环境策略允许 runtime/generation 命令，且 plan review gate 允许时，才运行 cap4kGenerate。
10. 如果不允许运行 cap4kGenerate，请求许可或报告精确命令，不执行。
11. cap4kGenerate 后，停下来等待人工审查，不继续手写实现。

报告发现：

- 已审查 plan evidence。
- 按 output family 分类所有权。
- 冲突、template override 风险、addon artifacts 或 drift。
- generation 是否运行、是否请求权限、或是否只报告精确命令。
- 当前阻塞 handwritten implementation 的人工审核门。

### `skills/cap4k-generation-review/rules/generation-stop-policy.md`

- 只有 plan review gate 允许后，generation 才能运行。
- generation 后，停下来给人审查 generated diff、ownership、plan-output alignment。
- generation 后不要继续进入 handwritten implementation。
- 只有用户明确授权后，才能恢复 implementation。

### `skills/cap4k-generation-review/references/plan-review-gotchas.md`

- `src/main/kotlin` 不自动等于 handwritten；先看 plan ownership。
- 判断 generated files 前必须审 plan output。
- `generatorId`、`templateId`、`outputKind`、`conflictPolicy`、`outputPath`、`resolvedOutputRoot` 要一起看。
- addon artifacts 和内置 artifacts 一样要审所有权。
- addon template ID 必须保留在 provider namespace：`addons/<providerId>/...`。
- provider-scoped options 不代表 source 或 canonical SPI ownership。
- validator-like artifacts 默认 addon-owned，除非是 aggregate unique-helper output。
- enum 和 value-object manifest entries 在 `types {}` 下，不应重复放进 custom type registry entries。
- Strong ID drift 常见表现：aggregate ID 退化成 primitive、同 context reference 没解析为目标 ID 类型、本地 reference language 被上游 language 替换、aggregate identity 在 persistence path 赋值。
- `@VO` 不是默认 value-object 路径；检查 value-object manifest 和 source conventions。
- 没有 payload fields 的 `integration_event` 无效。
- Template override 会改变未来 generated skeleton 形状，必须按同样 ownership rules 审查。
- public docs 和 AI skills 面向受众可以不同，但代码事实必须一致。

### `skills/cap4k-handwritten-implementation/SKILL.md`

用途：在人工审过 generated output 后，在已批准 generated skeleton 内实现手写业务逻辑，包括 command handler、query handler、subscriber、job、controller、factory、specification、domain service、Repository access、Mediator routing、UoW persistence。

始终读取：

1. `rules/implementation-entry-gates.md`
2. `../shared/rules/generated-skeleton-ownership.md`
3. `workflows/implement-inside-generated-skeletons.md`

路由边界：

- 在 `cap4k-generation-review` 和人工审查 generated output 后使用。
- 如果结构 skeleton 缺失、所有权不清或 handwritten slot 不在 technical design 中，回到更早阶段，不创建平行结构。

常见任务：

- 填充已批准 command、query、subscriber 或 job 逻辑。
- 添加 internal command/query routing。
- 持久化 aggregate changes。

### `skills/cap4k-handwritten-implementation/workflows/implement-inside-generated-skeletons.md`

编辑手写逻辑前读取 `../references/implementation-gotchas.md`。

执行要点：

- 确认人类已审查 generated output。
- 确认用户明确授权 handwritten implementation。
- 识别 generated skeleton 和 handwritten slot。
- 避免创建平行结构。
- Repository 只用于 aggregate access。
- Unit of Work 用于 persistence/delete intent 和 commit。
- 内部 command/query routing 时把 Mediator 当作 framework facade。
- skeleton 或 ownership 错误时回到更早阶段。

### `skills/cap4k-handwritten-implementation/rules/implementation-entry-gates.md`

- 人类已审查 generated output。
- 用户明确授权 implementation。
- Technical design 标识 handwritten slots。
- 结构性变更通过 Skeleton Generation Gate。
- 缺少 generator-supported skeleton 时，回到 generator inputs 或 technical design。

### `skills/cap4k-handwritten-implementation/references/implementation-gotchas.md`

使用 `../../shared/workflows/skeleton-generation-gate.md` 判断 skeleton 存在、所有权和结构变更；不要在这里复制 gate。

要点：

- Query handler 默认位于 adapter packages。
- 直接注入 handler 会绕过 cap4k request supervision。
- Internal triggers 不应变成 write repositories。
- Generated subscriber shells 实现后需要语义化方法。
- 聚合拥有的 value objects 通过 aggregate persistence 保存。
- entry implementation 调外部 client 前，必须先通过 command 路由状态变化。
- 编辑 generated request、handler、subscriber surface 前，先检查 `build/cap4k/plan.json`，例如 `*Cmd.kt`、`*Qry.kt`、`*QryHandler.kt` 和 generated subscriber shells。
- 缺失 generator output 属于 generator inputs 或 technical design，不属于 handwritten parallel structure。

隐藏 listener dispatch：

- 强烈不建议一个 public `on(event)` listener 手工分发到多个 private business reaction methods。
- 使用多个独立、带业务语义名称的 `@EventListener` 方法。
- cap4k 不保证多个 listener 的顺序，因此 listener 触发的 command 必须幂等且 zero-trust。

listener filter 过度：

- listener-side condition 只是便宜 routing filter。
- 如果它决定最终写资格，trust boundary 就被移出 command。
- Command 必须重新加载状态，校验 release policy、readiness、existing tasks、already-applied state、ownership、invariants，并为正常撤退路径返回明确 no-op reasons。

Zero-Trust Command Boundary：

- 在 command 内加载 aggregate root 或 write target。
- 变更前校验目标存在、所有权、aggregate status、child membership 和 business invariants。
- Query result、listener filter、job check、Saga state、另一个 command、external entry validation 都不足以作为写入信任。
- 对预期 not-ready 或 already-applied 状态返回明确 no-op。
- 缺失 target、invalid identity、wrong ownership、invalid child keys、invariant violation 抛 domain/application error。
- 读取多个 aggregate/fact 只用于 validation 或 fact observation。
- 通过 Unit of Work 精确持久化一个 aggregate root。

Command-to-Command：

- 默认立场：command 是写边界，不是流程协调器。
- command 只能在同一同步写 use case 内作为本地复用调用另一个 command。
- 如果 command 读取状态、分支并发送多个后续 command，很可疑；优先考虑 domain event fan-out、external fact entry、job 或 Saga。
- 如果仍然 command-to-command，记录为什么 called command 是本地同步复用，以及为什么 event-driven continuation 更差。

Saga 补偿：

- 需要持久化反向补偿时，优先 Saga，而不是 handler-local rollback code。
- 用 `execCompensableProcess(...)` 处理可补偿 forward steps，并在 forward success 后持久化 reverse-compensation request metadata。
- 业务意图决定进入补偿时，用明确 `requestCompensation(code, reason)`。
- operator-triggered compensation 放在 manager 或 control-plane entrypoint。
- 不要把 forward retry exhaustion 当作 compensation trigger；补偿必须被明确请求。

### `skills/cap4k-service-integration/SKILL.md`

用途：cap4k 业务服务交互设计和实现，包括 Open Host Service entries、Published Language、external capability clients、external fact entries、inbound/outbound integration events、callbacks、message listeners，以及跨服务边界的 command/query routing。

始终读取：

1. `rules/integration-event-boundaries.md`
2. `../shared/rules/layer-and-runtime-boundaries.md`

常见路由：

- Open Host Service：`workflows/design-open-host-service.md`
- external capability consumption：`workflows/consume-external-capability.md`
- inbound integration event：`workflows/handle-inbound-integration-event.md`

额外读取：

- 设计 boundary contracts、Open Host Service payloads 或 Integration Event fields 时，读 `rules/published-language.md`。
- 为 service-boundary work 写 technical design、generator inputs 或 handwritten slots 前，读 `references/gotchas.md`。

停止条件：

- transport mechanics 被分配给 business code。
- entry 直接写 repository 或 aggregate state。
- provider terms 泄漏进 boundary language。
- 写流程先调用 client，再进入 command/application use case。

### `skills/cap4k-service-integration/workflows/consume-external-capability.md`

当内部代码消费另一个 service/provider 拥有的 capability 时使用。

输入信号：

- 用户提到 payment、storage、media processing、moderation、notification、search、third-party API 或其他 external provider。
- Command 或 application use case 需要外部副作用或外部状态。
- provider vocabulary 开始出现在 business 或 Domain names 中。

技术设计字段更新：

- `businessIntent`：为什么需要外部 capability。
- `ubiquitousLanguage`：内部 capability 名和结果术语。
- `cleanArchitecturePlacement`：application-facing client contract、adapter client handler、接收翻译结果的 Domain behavior。
- `cap4kCarriers`：external capability request/client、Command handler、可选 status Query/read model。
- `generatorInputPlan`：预期 client/request/handler skeleton 和必需 design input/exception。
- `handwrittenLogicSlots`：provider mapping、retry/idempotency policy、command orchestration、Aggregate behavior call。
- `verificationEvidence`：provider terms 限制在 adapter-facing 区域，用例进入 Command 后才改变状态。
- `rollbackTriggers`：provider terms 进入 Domain、client-first write split、缺少 skeleton input。

生成输入影响：

- cap4k 可表达 external capability 时，使用生成器支持 client/request/handler skeleton。
- 创建新 client/handler path 前，先把 handwritten structural exception 记录进 technical design。
- 不要把 provider SDK DTO 和 transport status fields 放进代表业务语言的 generator inputs。

手写槽位：

- application-facing client contract 用业务语言命名 capability。
- adapter client handler 映射 provider protocol、credentials、status codes、DTOs。
- capability 属于写 use case 时，由 command handler 调用。
- aggregate behavior 接收已翻译结果并记录业务状态。
- Domain behavior 改变状态后，UoW 通过 framework capability 记录持久化意图。

验证证据：

- technical design 显示 capability name、provider mapping boundary、retry/idempotency policy、rollback target。
- diff 中 provider terms 只出现在 adapter-facing handlers、config 或 infrastructure mapping。
- write paths 先进入 Command 或 application use case，再由 external client invocation 改变业务状态。
- 同一写 use case 中，不存在 entry 先 client call 再 command delegation。

回滚目标：

- 外部依赖是否是 capability 不清楚时回到 tactical modeling。
- client skeleton、provider mapping boundary 或 idempotency policy 不清楚时回到 technical design / generator inputs。

### `skills/cap4k-service-integration/workflows/design-open-host-service.md`

当外部消费者需要同步访问内部 capability 时使用。

输入信号：

- 用户要求 Open Host Service、public API、synchronous external access、RPC/gRPC/HTTP entry、partner integration 或 frontend-facing capability。
- 任务命名了外部读者依赖的 request/response/status/error 词。
- 写 entry 可能绕过 Command，或 entry 可能直接读 Aggregates。

技术设计字段更新：

- `businessIntent`：外部消费者和业务理由。
- `ubiquitousLanguage`：request、response、status、errors 的 Published Language。
- `cleanArchitecturePlacement`：Adapter 做 entry protocol mapping，Application 做 use-case routing，Domain 做 invariants。
- `cap4kCarriers`：写用 Command，读用 Query/read API，API payload/controller skeleton 只作为 protocol surface。
- `generatorInputPlan`：cap4k 支持时，预期 payload、controller、command/query、handler skeleton inputs。
- `handwrittenLogicSlots`：mapping、validation translation、authorization policy hook、command/query delegation。
- `verificationEvidence`：path review、routing review、entry 不直接 repository/aggregate write。
- `rollbackTriggers`：consumer contract 不清、entry 直接改状态、泄漏 internal model fields。

生成输入影响：

- 可用时优先 generator-supported API payload、controller、Command、Query、handler skeleton。
- 不要为编译手写缺失的 generated skeleton；回到 generator inputs 或记录 technical design exception。
- 生成 payload skeleton 前，先稳定 Published Language field names。

手写槽位：

- Adapter entry 把 protocol input/output 映射到 Published Language 并委托。
- Application Command 或 Query handler 编排 use case。
- Domain behavior 强制业务 invariants。
- UoW 和 Mediator 是从已批准 application surface 使用的 framework capabilities，不是项目自有实现。

验证证据：

- technical design 记录 consumer、Published Language、read/write split、generator inputs、rollback target。
- diff 显示 entry code 只映射 protocol shape 并委托。
- entry implementation 不直接写 Repository、UoW 或 Aggregate state。
- boundary fields 默认不镜像 private Aggregate 或 persistence structure。

### `skills/cap4k-service-integration/workflows/handle-inbound-integration-event.md`

当外部事实通过 Integration Event、callback、webhook 或 message path 到达，业务项目需要解释它时使用。

输入信号：

- 用户说 inbound Integration Event、external fact、callback、webhook、message、partner event、payment result、media-ready event 或 provider status update。
- 接收事实可能推进内部状态。
- 任务混合 transport listener detail 和业务解释。

技术设计字段更新：

- `businessIntent`：external fact source 和项目为何关心。
- `ubiquitousLanguage`：类型化 external fact name、Published Language fields、provider-term translations。
- `cleanArchitecturePlacement`：runtime transport 在 business code 外；application subscriber 解释；Domain behavior 在 Command/application delegation 后。
- `cap4kCarriers`：Integration Event type、inbound subscriber、Command/application behavior、可选 Domain Event fan-out。
- `generatorInputPlan`：`design.json` 的 `integration_event` entry 或其他支持的 event type / skeleton placement input。
- `handwrittenLogicSlots`：idempotency、semantic translation、duplicate handling、command delegation、failure/retry policy。
- `verificationEvidence`：typed fact boundary、business subscriber 中无 transport mechanics、protocol fields 不到达 Domain behavior。
- `rollbackTriggers`：inbound/outbound 方向不清、幂等缺失、状态变化没经过 Command/application delegation。

生成输入影响：

- 可用时使用 cap4k 生成器支持 Integration Event type 和 subscriber skeleton placement。
- 不要把 framework transport listeners、parser setup、registration hooks、dispatch loops 或 message-consumer plumbing 手写成业务项目 workaround。
- generation 无法表达 expected skeleton 时，回到 generator inputs 或先记录 technical design exception，再创建结构。

手写槽位：

- Application inbound subscriber 解释类型化 external fact。
- idempotency check 防重复投递。
- translation 把 provider/partner terms 转换为内部 use-case language。
- 状态变化委托给 Command 或明确 application behavior。
- Domain behavior 只接收业务事实并执行 invariants。

明确拒绝：

- business subscriber 不创建 transport listeners、protocol registration、external envelope parsing 或 runtime dispatch。
- callback bodies、transport headers、provider protocol fields 不进入 Domain code。
- message/callback delivery mechanics 留在 Domain/Application business logic 外；业务路径只保留 typed fact meaning、idempotency 和 delegation。

验证证据：

- technical design 记录 source、typed fact、Published Language fields、idempotency key、command/application delegate、retry policy、rollback target。
- diff 显示没有在 business project 重建 framework transport work。
- subscriber code 接收 typed fact，并通过 Command/application behavior 委托状态变化。
- Domain API 不暴露 callback bodies、headers、provider status fields、transport envelopes。

### `skills/cap4k-service-integration/rules/integration-event-boundaries.md`

Integration Event 通过 Published Language 在 service 或 bounded-context 边界间承载已确认事实。

runtime 与 business 拆分：

- cap4k framework/runtime transport 处理 HTTP 和 message intake、parsing、registration、dispatch。
- business application inbound subscriber 接收类型化 external fact。
- business subscriber 解释事实、处理幂等、翻译语义，并在需要状态变化时委托 Command 或 application behavior。
- Domain code 只接收类型化业务事实；绝不接收 callback bodies、transport headers、provider status fields 或 protocol envelopes。
- framework Unit of Work 和 Mediator 能力由业务项目使用；项目代码不拥有这些 framework mechanisms。
- 不要求用户为 Integration Events 手写 framework transport intake、parser registration、runtime dispatch 或 message-consumer plumbing。

入站 event 规则：

- 入站 Integration Events 建模为 external facts，而不是内部 Domain Events。
- 选择 handler 前定义 typed external fact 和 Published Language。
- idempotency 和 semantic translation 放在 application inbound subscriber。
- 状态变化通过 Command 或明确 application behavior 委托。
- Aggregate invariants 留在通过 use case 到达的 Domain behavior；不要把 protocol fields 传给 Aggregate methods。

出站 event 规则：

- 出站 Integration Events 用 Published Language 向外发布已确认内部事实。
- Domain Events 可以触发出站 Integration Event publication，但它们不是同一个 contract。
- 不要把 internal Aggregate shape、persistence structure 或 non-public technical IDs 暴露为 Integration Event contract。
- 在 cap4k runtime capabilities 支持的 application orchestration point 协调出站 event emission；不要让 Aggregate roots 拥有跨服务投递机制。

审查问题：

- 这个事实是入站还是出站？
- 哪些 Published Language terms 对外部读者稳定？
- 哪部分是 framework/runtime transport，哪部分是业务解释？
- 入站路径用什么 idempotency key 和 duplicate-handling 行为保护？
- 哪个 Command 或 application behavior 拥有状态变化？
- 哪个 technical design field 记录 versioning、compatibility、retry 和 rollback 决策？

### `skills/cap4k-service-integration/rules/published-language.md`

Published Language 是外部读者用来理解 service capability、request、response、error 或 Integration Event 的稳定边界语言。

- boundary names、fields、status meanings、error meanings 都是兼容性契约。
- 不要把 internal Aggregate shape 暴露为 external contract。
- 不要因为 Entity fields、persistence names 或 internal Domain Event payloads 存在，就镜像它们。
- 使用外部消费者可以稳定依赖的术语；provider-specific 或 protocol-specific words 只有在它们确实属于 public business vocabulary 时才能进入。
- 在 generator input 或 handwritten implementation 前，先把 versioning、compatibility、deprecation、consumer-impact 决策记录到 technical design。

### `skills/cap4k-service-integration/references/gotchas.md`

- Framework transport 和 business subscriber 是不同职责。runtime 处理 protocol intake、parsing、registration、dispatch；business subscriber 处理 typed fact meaning、idempotency、semantic translation、delegation。
- Published Language 泄漏发生在 public contract 镜像 Aggregate fields、persistence names、internal Domain Event payloads 或 private technical IDs 时。边界语言必须对外部读者稳定。
- Provider terms 泄漏示例：业务 contract 使用 OSS bucket、S3 key、Stripe status、vendor callback code，而不是 resource storage、payment result 或内部 capability term。除非是真正 public business language，否则 provider vocabulary 只留在 adapter-facing mapping。
- Open Host Service 是边界概念；controller、RPC、gRPC、HTTP endpoint 是实现形式。
- Entry path 不得直接写 Repository 或 Aggregate state。写入进入 Command 或明确 application behavior，然后 Domain behavior 执行 invariants，framework UoW 记录 persistence intent。
- 同一写 use case 不应拆成 entry 先调用 external client，之后再委托 Command。外部调用属于该 use case 时，应放入已批准 application use case。
- 入站和出站 Integration Events 方向相反：入站是本系统解释的 external facts，出站是用 Published Language 发布已确认内部事实。
- Domain code 不应收到 callback bodies、protocol headers、message envelopes、provider status fields 或 transport DTOs。到达 Domain behavior 前先翻译为 typed business facts。

### `skills/cap4k-verification-audit/SKILL.md`

用途：cap4k 验证和审计声明，包括 static review、focused local evidence、full evidence、generated-vs-handwritten ownership checks、skipped-check disclosure、rollback targeting 和 final evidence summaries。

始终读取：

1. `../shared/rules/verification-claim-policy.md`
2. `references/evidence-modes.md`
3. `workflows/run-verification-audit.md`

路由边界：

- 任务要求 verification、audit、final evidence 或 claim strength 时使用。
- route-level `routing.yaml` 为该路由提供 forced rollback、drift gotchas 和 runtime capability map。

常见任务：

- Static-only skill/docs audit。
- 用户允许时的 focused local evidence。
- Final verification summary。

### `skills/cap4k-verification-audit/workflows/run-verification-audit.md`

执行顺序：

- 声明 verification mode。
- 列出用户/环境允许的命令。
- 运行 checks。
- 把 findings 映射到 rollback target。
- 说明 skipped checks。
- final claim 限制在实际产生的 evidence 内。

coverage 和 residual risk audit：

- 分类 coverage 形状：domain behavior、application command boundary、adapter/integration smoke、generation/design contract、analysis/flow、static diff/path review。
- 说明哪些关键行为只通过 smoke coverage 间接覆盖。
- 记录证据无法证明 command/flow 为什么撤退的 ambiguous no-op outcomes。
- 记录重 fixture coupling，例如 direct SQL fixtures、enum ordinal assertions、test ordering、hand-rolled polling。
- residual behavior risk 要和 command exit status 分开报告。

compile、test、analysis、HTTP、generation evidence 只有用户指令和环境策略都允许时才能使用。否则将这些检查报告为 skipped，并把最终声明限制在实际证据内。

### `skills/cap4k-verification-audit/references/evidence-modes.md`

| Mode | 允许证据 | 声明限制 |
|---|---|---|
| `static-only` | 文本审查、diff 审查、路径审查、plan/output 审查、drift scan | 只能声明结构/静态审计 |
| `focused-local` | 用户允许时的 targeted generation、analysis、compile 或 focused tests | 只能声明聚焦面已验证 |
| `full-evidence` | 明确授权下更广的 compile/test/analysis/HTTP evidence | 对被检查范围声明完整证据 |

## 共享规则与参考资料

### `skills/shared/rules/cap4k-positioning.md`

始终为真：

- 把 cap4k 视为 DDD tactical framework 加 generator-backed authoring system。
- 拒绝 generic CRUD framing。
- 用 generator 稳定结构。
- 由人决定业务语义。

漂移检查：

- 防止“cap4k 只是 CRUD scaffolding”。
- 防止“生成器决定业务模型”。
- 防止“生成结构替代领域判断”。
- 防止“应用本规则前先读外部项目历史”。

### `skills/shared/rules/generated-skeleton-ownership.md`

始终为真：

- cap4k skeletons 由 cap4k 生成。
- 复杂业务逻辑写在 generated checked-in skeleton 的已批准 author-owned surface 内。
- build-owned generated source 不允许手写编辑。
- generated snapshots 不允许手写编辑。
- 如果 generator inputs 能表达缺失结构，回到 generator input 或 plan review，不创建 handwritten parallel skeleton。
- 创建 parallel handwritten structure 前，需要显式 technical design exception。
- `plan.json`、analysis outputs、drawing-board outputs、generated snapshots 是证据，不是 handwritten business logic surfaces。
- 每个路径决策都保留 generated-vs-handwritten ownership。

漂移检查：

- 防止“缺 skeleton 就手写推进”。
- 防止“名字相似的 parallel structure 没问题”。
- 防止“`src/main` 就是 handwritten ownership”。
- 防止“business logic 写进 build-owned generated source”。
- 防止“generated snapshots 是可编辑 authoring surfaces”。

### `skills/shared/rules/generator-input-source-of-truth.md`

始终为真：

- DB/schema、design JSON、enum manifest、value-object manifest、Gradle extension settings、addons/options、template override decisions 都是 generator inputs。
- `plan.json`、generated output、generated snapshots、flow output、drawing-board output 是 generated evidence。
- analysis outputs 默认是 observation evidence，不是普通 source-generation input skeleton。
- 手工复制 analysis fragment 只有放到支持的 input surface 且满足当前 contract 时，才算 generator input。

漂移检查：

- 防止“`plan.json` 是业务 source truth”。
- 防止把 analysis output 未转换为有效 DB/schema、design JSON、manifest、Gradle setting、addon/option 或 template decision 就当成支持输入。
- 防止“generated evidence 能替代 generator inputs”。
- 防止“missing inputs 应通过 handwritten skeletons 补”。

### `skills/shared/rules/layer-and-runtime-boundaries.md`

始终为真：

- business invariants 放 Domain。
- use-case orchestration 放 Application。
- protocol shape mapping 放 Adapter。
- runtime assembly 放 Start。
- Repository 用于 aggregate read/access/load。
- Unit of Work 用于 persistence intent、delete intent、commit/save。
- Mediator 是 framework facade。
- framework/runtime HTTP 和 message transport 处理 consume、parse、register、dispatch。
- business inbound subscribers 解释 typed external facts、确保幂等、翻译语义并委托。

漂移检查：

- 防止“Repository 拥有聚合持久化”。
- 防止“项目代码拥有 Unit of Work mechanics”。
- 防止“Mediator 是 business engine”。
- 防止“subscribers 拥有 transport parsing 和 dispatch”。
- 防止“HTTP/message subscribers 拥有 consume/parse/register/dispatch”。
- 防止“controllers/adapters 拥有 domain invariants”。

### `skills/shared/rules/naming-layout-and-testing.md`

始终为真：

- 从文件名和目录能推断角色。
- domain behavior tests 与 application orchestration tests 分开。
- adapter mapping tests 与 runtime wiring tests 分开。
- generation evidence checks 与 behavior tests 分开。
- transport DTOs、external protocol details、query projections、domain behavior 放在各自层内。

漂移检查：

- 防止按便利或物理接近放文件。
- 防止一种测试验证所有层。
- 防止 generation evidence 替代 behavior tests。
- 防止不透明 helper DSL 隐藏业务语义。

### `skills/shared/rules/verification-claim-policy.md`

始终为真：

- 每个 claim 都要匹配 evidence mode。
- 不要把 static-only evidence 报告成 full verification。
- 披露 skipped checks。
- 直接说明 unresolved verification gaps。

漂移检查：

- 防止“静态审过就是完全验证”。
- 防止“没运行就是通过”。
- 防止 final report 省略 skipped checks。
- 防止 claim strength 超过可用 evidence。

### `skills/shared/workflows/forced-rollback.md`

后续阶段会检验早期假设。当证据推翻早期阶段时，不要继续向前。

回滚操作：

- 停止 forward work。
- 识别无效假设。
- 删除或隔离由该假设创建的 artifacts。
- 更新目标阶段 artifact。
- 重新运行相关 gate。
- 记录恢复后的 evidence。

| 症状 | 回滚到 |
|---|---|
| concept mismatch | `cap4k-tactical-modeling` |
| unclear carrier | `cap4k-technical-design` |
| missing input | `cap4k-generator-inputs` |
| plan mismatch | `cap4k-generator-inputs` 或 `cap4k-technical-design` |
| generation ownership conflict | `cap4k-generation-review` |
| implementation bypass | `cap4k-technical-design` |
| structure drift | 引入错误假设的最早阶段 |
| verification drift | 引入错误假设的最早阶段 |

### `skills/shared/workflows/skeleton-generation-gate.md`

触发时机：

- command、query、client、api payload。
- command handler、query handler、client handler。
- domain event、integration event、subscriber skeleton。
- domain service、saga、scheduled reaction。
- aggregate、entity、relation、projection。
- factory、specification、unique helper。
- repository、controller、adapter、start skeleton。
- package 或 directory skeleton。
- 任何只为修编译而新增的 class/interface。

Gate 问题：

1. 这是否 generator-supported？
2. 当前 cap4k 能否通过 DB/design/types/addon/options 表达它？
3. 项目已有对应 input 吗？
4. plan evidence 是否已审？
5. generation 会创建或更新它吗？
6. 如果手写，exception 是否在 technical design 里？
7. handwritten path 是否保留 generated-vs-handwritten ownership？

允许通过状态：

- not generator-supported。
- generated from inputs and reviewed in plan。
- explicitly documented technical design exception。
- author-owned logic inside generated skeleton。
- explicit user override with risk and verification recorded。

失败动作：

- 停止 implementation。
- 回到 technical design 或 generator inputs。
- 更新 inputs 或 exception decision。
- 再次 review plan。

要记录的 evidence：

- 期待该 skeleton 的 technical design section。
- 应产生它的 generator input file/source。
- 证明 output path、outputKind、templateId、conflictPolicy 的 plan item。
- 如果手写，记录 explicit exception。
- 检查 ownership 的 verification command 或 review。

### `skills/shared/references/drift-gotchas.md`

高成本措辞和所有权陷阱：

- 不要把当前 planning/generation 描述成旧 KSP-only 行为；使用当前 cap4k generation 和 plan 词汇。
- 不要提 removed/stale analysis switches，例如旧 flow/drawing-board enabled flags。
- 不要把 `validator` 列为普通 `design/design.json` tag。
- 不要让业务 Integration Event subscriber 负责 HTTP/message consumption、parser registration 或 transport dispatch。
- 不要使用 `Repository save` 或 `Repository saves aggregates` 这类旧措辞；Repository 负责 reads/access/loads，Unit of Work 负责 persistence/delete/commit。
- 不要写业务项目实现 Unit of Work 或 Mediator；它们是 framework capabilities。
- 不要使用旧路径 `src-generated/main/kotlin`。
- 不要使用旧 `client/cli` 边界措辞；当前区分 `client` 和 `client-handler`。
- 拒绝旧的带空格 analysis output path：`build/cap4k code analysis`、`build/cap4k/analysis plan.json`。
- 不要把 `value_object` 列为普通 design-json tag，除非任务提供 plan evidence 和 technical design 已明确支持。
- 不要假设每个 Integration Event subscriber 默认生成；出站 event contract 是默认 family selection，入站 contract/subscriber 需要显式兼容选择。
- 不要混淆 ordinary source generation 与 analysis-only flow/drawing-board output；二者输入、generator family、ownership、review claim 不同。

### `skills/shared/references/generator-supported-skeletons.md`

当 cap4k inputs 能表达某类 skeleton 时，这些 skeleton family 必须视为 generator-owned structure。缺失 supported skeleton 时回到 generator inputs 或 technical design；不要为推进实现而手写。

支持 family 摘要：

| Family | 常见输入面 | Agent 规则 |
|---|---|---|
| command | `design/design.json` 的 `command` block 或 aggregate-aligned design input | 状态改变 use case 使用 generated command/handler slots |
| query | `design/design.json` 的 `query` block | 保持只读语义，plan review 前不手写补 query skeleton |
| client | `design/design.json` 的 `client` block | application-facing external capability request shape 是 generated structure |
| api payload | `design/design.json` 的 `api_payload` block | protocol DTO shape 放在 adapter-facing generated payload slots |
| domain event | `design/design.json` 的 `domain_event` block，通常关联 aggregate | 用于内部领域事实；不要为拿 skeleton 转成 Integration Event |
| integration event | 带 inbound/outbound variant 的 `integration_event` block | 用于 Published Language；inbound subscriber 需要显式支持选择 |
| subscriber | domain-subscriber 或 integration-subscriber artifact selection | subscriber 保持轻薄，缺失则回到 event design 或 artifact selection |
| domain service | `design/design.json` 的 `domain_service` block | 用于无单一 aggregate owner 的 domain collaboration |
| saga | `design/design.json` 的 `saga` block | 只用于可恢复、可补偿或持久化流程协调 |
| aggregate | DB/schema aggregate markers 和 aggregate generator input | generator input 能表达时不手写 aggregate root structure |
| entity | DB/schema entity/table relation inference | entity structure 跟随 aggregate modeling 和 schema input |
| factory | aggregate generator family 或 design-supported factory skeleton | 可用时 creation policy 放 generated factory slots |
| specification | aggregate/specification generator family 或 unique helper input | 预保存约束缺 skeleton 时回到 generator inputs |
| repository | aggregate persistence generator family | Repository 负责 read/access/load；save ownership 属于 Unit of Work |
| controller | API/adapter generator family 或 addon-supported adapter input | controller skeleton 属于 adapter protocol mapping，不拥有业务规则 |
| job | scheduled reaction、compensation、polling 或 addon-supported job input | 缺 job support 时，手写前回到 technical design |
| projection | aggregate-projection generator family 或 read-model input | projection 是 generated/read ownership，不是 command-side aggregate shortcut |

缺 skeleton 规则：

1. 停止 structural implementation。
2. 检查结构是否属于 DB/schema、`design/design.json`、value-object manifest、enum manifest、Gradle extension、addon/options 或 template override。
3. input 能表达时，回到 generator inputs；只有 plan review 允许后才能 regenerate。
4. input 不能表达时，回到 technical design，记录已批准 handwritten structural decision 后再创建 skeleton。
5. ownership 不清时阻塞，直到 generation review 分类 output family 和 conflict policy。

### `skills/shared/references/output-ownership-taxonomy.md`

用于审查 plan evidence、generated diffs、template overrides 或 handwritten implementation surfaces。

Checked-In Skeleton：

- 稳定源文件，通常在模块 `src/main/kotlin` root。
- 可能包含 handwritten slots、generated wiring shape 或 managed sections。
- `src/main/kotlin` 不自动代表完全 handwritten ownership。
- plan signal：`outputKind = CHECKED_IN_SOURCE`、`resolvedOutputRoot` 指向 checked-in module source root、`conflictPolicy` 常用 `SKIP` 保护现有 handwritten logic。
- 规则：业务逻辑只写 approved slots，不手工替换 generator-owned structure。

Build-Owned Generated Source：

- generation 会重建，位于 build-generated source root。
- 不是长期 handwritten implementation 区域。
- plan signal：`outputKind = GENERATED_SOURCE`、`resolvedOutputRoot` 在 build-generated cap4k source root、`conflictPolicy` 常用 `OVERWRITE`。
- 规则：不要放长期业务逻辑；改 generator input、template 或 addon。

Generated Snapshot Or Evidence：

- 包括 plan files、bootstrap plans、analysis plans、flow output、drawing-board output 和其他 review artifacts。
- 它们说明 generation 观察或计划了什么，不是 business source truth。
- 规则：用证据指导 rollback 和 ownership decision，不在 evidence outputs 中实现业务行为。

Template Override：

- 改变某个 family 的 generated shape，是 source-controlled authoring infrastructure，不是普通业务逻辑。
- 需要 generation review，因为会影响该 family 的所有未来 skeleton。

Handwritten Logic：

- 业务行为、orchestration、translation、idempotency、compensation、policy、tests，写在已批准 surfaces。
- 应位于 generated skeleton slots 或明确 documented structural exceptions。
- 典型位置：aggregate behavior、value object validation、factory policy、domain service logic、specification checks、command/query handlers、subscribers、saga processes、scheduled reactions、external capability orchestration、adapter protocol mapping、controller request conversion、client-handler translation、persistence mapping。
- 规则：必须保留 generated-vs-handwritten ownership；generator inputs 能表达结构时，不创建 parallel skeleton families。

Conflict policy：

| `conflictPolicy` | 常见含义 | 必需审查 |
|---|---|---|
| `SKIP` | 已存在 checked-in file 被保护 | 确认 handwritten slots 保留，缺失更新是有意的 |
| `OVERWRITE` | build-owned 或明确 regenerated output 可替换 | 确认没有 handwritten logic 位于该 output root |
| `FAIL` | 已存在文件应阻塞 materialization | generation 前停下并解决 ownership/bootstrap conflict |

如果 policy 与 ownership 不一致，停止 implementation，回到 generation review 或 technical design。

### `skills/shared/references/runtime-capability-map.md`

Repository：

- Repository 以 read/access/load 为主，可以按 identity、IDs 或 specification 恢复 aggregates，并在支持时使用 aggregate load plans。
- 它不拥有 commit semantics。
- Agent 规则：不要描述为 save owner。Command path 通过 Repository 加载 aggregates，通过 Unit of Work 持久化意图。

Unit Of Work：

- Unit of Work 拥有 persistence intent、delete intent、commit/save、transaction propagation、lifecycle interception。
- 它收集要 persist/remove 的 entities，并与 runtime interceptors 协调 save behavior。
- Agent 规则：application handlers 通过 Unit of Work 记录 persistence/delete intent，并按 framework contract 调用 commit behavior。不要把 UoW mechanics 分配给 business project code。

Mediator：

- Mediator 是 framework facade 和 delegation entrypoint，跨 repository、aggregate factory、domain service、Unit of Work、integration event、request capabilities 等 runtime supervisors。
- 它不是独立 business engine。
- Agent 规则：需要时把 Mediator 当 framework-facing convenience，但业务决策留在 domain/application code。

Specification：

- Specification 和 unique helper 可通过 Unit of Work interception 参与 pre-save checks。
- 这让 pre-save constraints 成为 domain/runtime boundary，而不是 controller-only validation。
- Agent 规则：需要 pre-save constraint 时，优先 generator-supported specification/unique helper surfaces，或先记录 technical design exception 再手写结构。

Integration Event Transport Split：

- Framework/runtime transport adapter 消费外部 HTTP/message input，解析/注册 events，存储 event records，并通过已配置 adapters 分发 typed Integration Event payloads。
- Business application inbound subscriber 接收 typed external fact，处理 idempotency 和 semantic translation，然后委托 commands/application behavior 改状态。
- Agent 规则：不要把 external protocol consumption、parser registration、transport dispatch 分配给 business subscriber。不要把 inbound payload 直接推入 aggregates。

Saga Runtime Scope：

- Saga runtime 支持 request-oriented process coordination、subprocesses、compensable subprocesses、explicit compensation requests、retry、archival、scheduled compensation。
- Agent 规则：需要 persistent progress、retry、recovery 或 compensation 时使用 Saga。不要把 Saga 描述成 generic callback-resume workflow engine，除非此 skill bundle 已根据 verified code facts 更新。

Analysis Evidence：

- Analysis outputs 是 observation evidence，可辅助 review flow、drawing-board、source contracts 和 drift。
- 它们不是 source skeletons，也不能替代 generation 的 plan evidence。
- Agent 规则：analysis evidence 只能支持 static review claims；不能当作 business source truth 或 installed skill runtime prerequisite。

### `skills/shared/references/tactical-affordance-map.md`

这是 tactical carrier 映射权威表。中文审核摘要如下：

| Business Signal | Cap4k Carrier | 使用时 | 不使用时 | 输入面 / 证据 / 手写位置 / 回滚 |
|---|---|---|---|---|
| state-changing intent | Command | 用户、policy、event reaction 或 scheduled trigger 要改变业务事实 | 只读或格式化视图，没有状态迁移 | `design/design.json` command block 或 aggregate-derived command input；handler 在 application，domain invariants 在 aggregate；Query 变更状态或 handler 拥有 invariant 时回滚 |
| read-only view | Query | 观察当前状态、构建 read model 或显示数据 | 会改 aggregate 状态、触发不可逆副作用或推进 workflow | `design/design.json` query block、query artifact selection 或 projection input；不得有 UoW persistence intent；Query 持久化或发业务事件时回滚 |
| business lifecycle owner | Aggregate | cluster 拥有 identity、一致性规则、生命周期状态和必须一起变化的行为 | 只是 read shape、external payload、pure value 或跨聚合协调 | DB/schema aggregate markers 和 aggregate generator input；application/adapter 变成生命周期 owner 或 schema 标错 aggregate root 时回滚 |
| identity/value semantics | Value Object / Strong ID | 值相等、稳定 ID、类型安全或 JSON-backed structured value | 拥有生命周期行为、独立持久化 identity 或跨步骤 process state | value-object manifest、enum manifest、DB type markers、strong ID conventions；value type 长出生命周期职责或被 primitive 绕过时回滚 |
| internal fact | Domain Event | bounded context 内部发生了领域事实，内部 reaction 可跟随 | 是稳定外部 contract 或 inbound external message | DB/schema 或 `domain_event` block；Domain behavior 发事实，application subscriber 委托 command/policy；暴露为外部 contract 时回滚 |
| external published fact | Integration Event | fact 跨 service/team/bounded context/callback/messaging 边界，需要稳定 Published Language | 只是内部 domain notification 或同 bounded context 内 direct method call | `integration_event` block 加 inbound/outbound variant；application inbound subscriber 处理解释、幂等、翻译、command delegation；transport 被交给业务 subscriber 或内部 entity 泄漏时回滚 |
| reaction after event | Subscriber | 内部或 typed external event 触发轻薄 reaction、filter、command delegation 或 follow-up request | reaction 拥有 long-running state、retry/compensation workflow 或核心 aggregate invariant | event block 加 subscriber artifact selection；subscriber 在 application layer，复杂写入回到 command/aggregate；subscriber 变 workflow owner、直接改 aggregate 或解析 protocol 时回滚 |
| long-running/recoverable process | Saga | 需要 persisted progress、retry、recovery、compensation 或明确 process state | 简单同步 reaction、callback-first state update 或 stateless command chain | `saga` block；Saga/task handler 在 application，domain change 委托 command/aggregate；只因多步骤使用 Saga 时回滚 |
| scheduled/polling trigger | Scheduled Reaction | time、cron、timeout、compensation scan 或 polling fallback 触发 application behavior | user command、external callback 或无时间边界的 domain event reaction | addon/options、job/scheduled skeleton input；job 直接写 aggregate、复制 callback truth 或补建模缺口时回滚 |
| cross-service call | External Capability | application logic 要通过 anti-corruption boundary 请求外部系统 | 内部 domain service、repository read 或 protocol controller entrypoint | `client` block 或 adapter capability input；application-facing request model 和 adapter client handler 翻译 protocol；aggregate 直接调外部服务或 adapter 决定业务事实时回滚 |
| stable external contract | Open Host Service / Published Language | 其他系统依赖稳定 command、callback、payload、event 或 vocabulary | 内部 DTO 变动、private aggregate state 或一次性 adapter convenience | API payload、integration event、controller/client inputs 和 Published Language design；adapter 映射 protocol，application/domain 拥有语义；暴露内部字段或无兼容审查时回滚 |
| pre-save constraint | Specification | 保存前必须检查 domain rule，或 uniqueness/eligibility 守卫 saved aggregate state | 一次性 UI validation、read filter 或 post-save reaction | DB/schema uniqueness markers、specification generator input 或 unique helper input；只在 controller/query/path 或保存后检查时回滚 |
| domain collaboration not owned by one aggregate | Domain Service | 领域决策跨 aggregates 或 policies，且没有单一 aggregate owner | application orchestration、external protocol translation、persistence query 或简单 aggregate method | `domain_service` block；domain service 由 application orchestration 调用，aggregate 保留自身 invariant；变 transaction script、repository facade 或 external client wrapper 时回滚 |

## 校验脚本职责摘要

### `skills/scripts/validate-cap4k-skills.ps1`

主入口。依次运行：

- `structure.ps1`
- `routing.ps1`
- `progressive-loading.ps1`
- `self-contained-runtime.ps1`
- `skeleton-gate-refs.ps1`
- `stale-terms.ps1`
- `link-check.ps1`

通过时输出 `cap4k skill validation passed.`。

### `skills/scripts/checks/structure.ps1`

检查：

- 必需 skill 目录存在：`cap4k-authoring`、`cap4k-business-discovery`、`cap4k-tactical-modeling`、`cap4k-technical-design`、`cap4k-generator-inputs`、`cap4k-generation-review`、`cap4k-handwritten-implementation`、`cap4k-verification-audit`、`cap4k-service-integration`、`shared`、`scripts`。
- 已移除旧目录不存在：`cap4k-modeling`、`cap4k-generation`、`cap4k-generated-output-review`、`cap4k-implementation`、`cap4k-verification`。
- 至少 9 个 focused cap4k skill directories。
- 每个 focused skill directory 都有 `SKILL.md`。
- 共享规则/流程/参考文件存在。
- 共享核心文案包含关键 guardrails。

### `skills/scripts/checks/routing.ps1`

检查：

- `skills/cap4k-authoring/routing.yaml` 存在。
- 禁止重复 route-map Markdown。
- 每个 required route id 存在。
- `full-authoring-flow` 包含完整链条和关键触发词。
- `content-studio-dry-run` 包含干跑入口和必读文件。
- 高风险 route 带有 required positive/negative signals。
- 各 route 的 required reads 完整且路径能解析。
- workflow path 必须从 `routing.yaml` 相对路径出发，不能使用不合规 shorthand。
- 禁止组合路由短语，例如把两个真实 skill id 合成一句。

### `skills/scripts/checks/progressive-loading.ps1`

检查：

- 每个 focused `SKILL.md` 不超过 100 行。
- frontmatter 必须包含 `name` 和 `description`。
- 必须有 `## Always Read`。
- Always Read 条目不超过 3 个。
- `SKILL.md` 中不能复制 tactical affordance table。
- 每个 required skill 都引用该阶段必须 progressive-load 的文件。

### `skills/scripts/checks/link-check.ps1`

检查：

- `skills/**/*.md` 中 Markdown link 和 code span 中的本地 `.md` 路径可解析。
- 忽略 HTTP 链接。
- 跳过 fenced code block 内的 code span 检查。

### `skills/scripts/checks/stale-terms.ps1`

检查：

- active skill runtime text 和 root shell text 中没有已移除旧 skill 引用。
- service integration 旧文件名和旧引用不存在。
- 不出现旧 KSP、旧 analysis switches、旧路径、旧 route、旧 generator/design 术语。
- 防止把 Repository 写成保存 owner、防止业务项目实现 UoW/Mediator、防止把 business subscriber 写成 transport consumer。
- public authoring docs 和 analysis docs 中也检查部分已移除 event guidance。

### `skills/scripts/checks/skeleton-gate-refs.ps1`

检查：

- 高风险技能路径会激活 `../shared/workflows/skeleton-generation-gate.md`。
- business discovery、tactical modeling、verification audit 会激活 forced rollback。
- 高风险共享参考资料有 routing.yaml 或 workflow 入站激活路径。

### `skills/scripts/checks/self-contained-runtime.ps1`

检查运行时 skill 文件是否依赖外部资料作为必读前提，例如：

- `docs/public`
- `docs/superpowers/analysis`
- GitHub issue
- Context7
- historical specs
- cap4k source checkout/source tree

允许否定或维护语境，例如 “do not read ...”、“not a runtime prerequisite”、“maintenance”等。

## 审核建议

建议人工重点看这些位置：

1. `routing.yaml` 的 positive/negative signals 是否会误触发或漏触发实际中文/英文用户请求。
2. `Skeleton Generation Gate` 是否覆盖所有结构性创建入口，尤其是 handler、subscriber、controller、job。
3. `Output Ownership Taxonomy` 与当前 generator plan 输出字段是否完全一致。
4. `service-integration` 的 Published Language 和 Integration Event 边界是否符合团队实际架构。
5. `stale-terms.ps1` 的禁词是否会误伤未来中文文档；中文审核稿因此放在 `docs/reviews/`，避免进入 `skills/**/*.md` 运行时扫描。
