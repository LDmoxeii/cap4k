# Default Happy Path

> 本页是 cap4k 编写体系的规范中心。后续各层指南、生成器指南和审计检查都默认回到这一页。

本页统一使用“内容发布与处理示例项目”作为教学主场景。默认链路固定为：创建草稿、送审、启动媒体处理、接收处理结果、满足条件后发布内容；处理失败时支持重试或回退。

只要缺的是 generator-capable skeleton，就先回到 generation；只要缺的是 design / DDL / enum manifest / `types.registryFile` 这类业务输入合同，就先回到 modeling；只要缺的是 KSP metadata 产物或其生成配置，就先回到 generation / compile / setup。

## 规则强度说明

- `Must`：不满足就已经偏离默认路径，必须先修正，或明确说明为何进入高级模式。
- `Default`：默认应遵守；若偏离，作者和审阅者都要能说明为什么默认写法不足。
- `Avoid`：当前问题通常不该这样建模；只有在替代方案更糟时才考虑保留。
- `Advanced`：只有默认路径不够、并且团队能接受额外升级与审计成本时才进入。

## 硬规则总表

| 规则 | 强度 | 核心约束 |
| --- | --- | --- |
| 单命令单聚合根变更 | `Must` | 一个命令处理路径内只允许一个聚合根进入持久化变更边界 |
| 状态变更收敛到命令处理路径 | `Must` | 开放服务入口、外部事实入口、内部触发入口都不直接改聚合 |
| 聚合根是唯一写入主面 | `Must` | UoW 只保存聚合根，子实体和值对象通过聚合根持久化 |
| 领域事件由聚合根统一登记和发出 | `Must` | 事件可以描述子实体变化，但归属和登记主体属于聚合根 |
| 对外集成事件只在 application 编排点 attach | `Must` | 仅领域事件订阅器或明确的 application process 基于内部事实构造并 attach integration event；聚合、adapter 入口、普通边界代码不决定对外集成事件 |
| inbound integration event 是外部事实入口 | `Must` | 会推进状态的外部事实先转内部命令，不伪装成领域事件 |
| 默认禁止跨聚合写模型强引用 | `Default` | 只读弱引用属于高级模式 |
| 多 handler 顺序不保证 | `Default` | 顺序依赖应拆成阶段化流程 |
| 多个流程可以消费同一个领域事实 | `Default` | 事实相同则允许独立 listener 广播消费；写入判断仍在 command |
| 单一主动作 | `Default` | 写入口一次只推进一个命令，查入口一次只推进一个查询 |
| 查询观察不反向污染写模型 | `Must` | 查询路径只观察，不反向修复或污染写模型 |
| 外部能力端口是防腐边界，不是主流程真相源 | `Must` | 外部能力调用必须先穿过 client 防腐层 |

这张表是审阅入口，不是速查口号。下面的流向展开会把每条规则落回同一个教学项目，避免每页都换例子。

## 建模

### 单命令单聚合根变更

强度：`Must`

Why：
在示例项目里，`SubmitContentForReviewCmd` 只推进 `Content` 的状态变化，`StartMediaProcessingCmd` 只推进 `MediaProcessingTask` 的状态变化。这样命令边界、仓储边界、回滚边界保持一致，审阅者也能一眼看出“这次写入到底谁负责”。

Non-example：
`PublishContentCmd` 同时加载 `Content` 和 `MediaProcessingTask`，再一起改状态并持久化，试图把“审核通过”和“媒体完成”塞进一次写入。

Audit cues：

- 一个 handler 内是否出现多个聚合根仓储写入
- 业务动作是否其实在描述两个不同生命周期
- 如果需要两个聚合根协同，是否已经改成事件或阶段化流程

### 聚合根是唯一写入主面

强度：`Must`

Why：
示例项目中的 `Content` 才能决定稿件能否送审、批准、发布；`MediaProcessingTask` 才能决定处理状态能否进入成功或失败。子实体、内部集合、状态片段都只能通过根上的行为方法被修改，这样外部写入口始终只面对一个业务真相面。

Non-example：
让 controller 或 handler 直接拿到 `Content` 内部子实体后改字段，或者把某个子实体单独暴露成 `ApproveReviewStepCmd` 的命令目标。

Audit cues：

- 外部命令目标是不是永远指向聚合根
- 聚合内部状态迁移是否都通过根方法发生
- 是否存在“为了方便”把内部对象暴露给外部写路径的做法

### 默认禁止跨聚合写模型强引用

强度：`Default`

Why：
在示例项目里，`Content` 和 `MediaProcessingTask` 同属一个限界上下文，但它们仍然是不同聚合根。默认情况下，一个写模型不应直接持有另一个写模型的强引用，否则生命周期会被悄悄绑死。需要导航时，先用 ID 或查询读模型表达；只读弱引用属于高级模式，不是默认起点。

Non-example：
在 `Content` 写模型里直接挂一个可写的 `MediaProcessingTask` 对象引用，并在同一次命令处理里联动修改它。

Audit cues：

- 聚合字段里是否直接出现另一个聚合根实例
- 跨聚合协同是否仍通过命令、事件、查询或只读投影完成
- 如果引入只读弱引用，是否已经被明确标记为 `Advanced`

## 命令

### 状态变更收敛到命令处理路径

强度：`Must`

Why：
默认路径要求所有状态变更都能追溯到明确的命令处理。示例项目里，无论是开放服务入口、外部事实入口还是内部触发入口，最终都要转换为 `CreateContentDraftCmd`、`SubmitContentForReviewCmd`、`StartMediaProcessingCmd`、`PublishContentCmd` 这类内部命令，再由 handler 驱动聚合行为。

Non-example：
controller 直接调用仓储改 `Content`，job 直接把 `MediaProcessingTask` 标成成功，或者 subscriber 在事件回调里绕过命令层直接写库。

Audit cues：

- 开放服务入口、外部事实入口、内部触发入口是否只是做输入转换和调度
- 所有状态变更是否都能指向一个明确的内部命令
- 写入前是否先进入 handler，再进入聚合行为

## 事件

### 领域事件由聚合根统一登记和发出

强度：`Must`

Why：
示例项目里，“内容已提交送审”“媒体处理已完成”都可以描述聚合内部变化，但事件的登记主体仍然是 `Content` 或 `MediaProcessingTask` 聚合根本身。这样事件边界与业务真相边界保持一致，不会出现“子对象自己偷偷向外宣布状态变化”的情况。

Non-example：
子实体自己 new 一个事件并直接发布，或者 handler 在聚合外凭感觉补发领域事件。

Audit cues：

- 事件是否在聚合根行为完成后统一登记
- 事件载荷是否描述事实，而不是命令意图
- 是否存在聚合外部补写、补发、替发领域事件的代码

### 多 handler 顺序不保证

强度：`Default`

Why：
当 `MediaProcessingTask` 发出“处理完成”事件时，可能有多个订阅器分别更新读模型、触发后续命令、记录审计。默认不应依赖这些 handler 的执行顺序。只要流程真的要求“先 A 后 B”，就应该把顺序显式建模成新的阶段，而不是赌运行时顺序。

Non-example：
假设订阅器 A 一定先把数据写好，订阅器 B 再去读取；或者把两个顺序相关的副作用拆在两个独立 handler 里却没有额外流程约束。

Audit cues：

- 多个 handler 之间是否存在隐式先后依赖
- 顺序要求是否已经提升为新的命令、事件或阶段
- 读模型更新失败时，是否错误影响了写模型真相判断

### 多个流程可以消费同一个领域事实

强度：`Default`

Why：
当一个聚合已经产生了一个完成的业务事实，多个 application 流程可以各自监听这个事实。默认判断链是：先看业务行为是否真的不同，必要时拆聚合行为；再看完成的业务事实是否真的不同，必要时拆领域事件；如果只是多个流程都关心同一个事实，就保留一个领域事实并让独立 listener 广播消费。`cap4k-reference-content-studio` 示例项目里的 `ContentPublicationReadyDomainEvent` 表示内容已经具备发布条件。即时发布和付费发布都可以被这个事实唤醒，但最终分别进入自己的 command：即时发布只在 `ReleasePolicy.IMMEDIATE` 且内容 ready 时应用；付费发布只在 `ReleasePolicy.PAID`、未发布、未启动任务时应用。

listener 可以用事件快照做便宜过滤，避免明显无关的 ghost work，但这个过滤只是路由优化。真正的信任边界仍然是 command：command 重新加载状态，校验 release policy、readiness、existing task、already-applied state、ownership 和 invariant，预期不适用路径返回 typed no-op，例如 `NotPaidContent`、`NotPublicationReady`、`AlreadyStarted`、`AlreadyPublished`。审计时还要能看出哪个 listener 被唤醒、发送了哪个 command、命令 applied 还是 no-op、原因是什么。

Non-example：
因为即时发布和付费发布是两个消费者，就把同一个“内容已可发布”事实拆成 `ImmediatePublicationReadyEvent` 和 `PaidPublicationReadyEvent`；或者写一个中央 listener 读取状态后决定“这次归哪个流程”，把最终写入资格判断放在 listener 分支里。

Audit cues：

- 事件名称是否描述完成的业务事实，而不是下游消费者
- 行为不同、事实不同、消费者不同三件事是否被分开判断
- 多个 listener 是否独立，且不依赖执行顺序
- listener-side filter 是否只是优化，而不是最终写入判断
- 每条状态变更是否进入 command 并重新校验自身前置条件
- no-op 是否可观测，能说明 applied / no-op 和退让原因

### 对外集成事件只在 application 编排点 attach

强度：`Must`

Why：
示例项目里，如果“内容审核已通过”或“媒体处理已完成”需要对外通知，默认路径不是让 `Content` 或 `MediaProcessingTask` 直接承担跨服务协议，也不是让普通边界代码决定对外集成事件。聚合先登记领域事实；仅领域事件订阅器或明确的 application process 再根据该内部事实构造 outbound integration event，并在当前工作单元调用 `Mediator.events.attach(...)`。作者只负责在当前工作单元 attach；后续由运行时按配置交给集成传输适配器处理。

Non-example：
聚合行为里直接组装跨服务 payload，adapter 入口或普通边界代码决定对外集成事件，或者绕过 `Mediator.events.attach(...)` 调用任何直接发布/低层发送 API。

Audit cues：

- outbound integration event 是否从领域事实或 application process 派生
- attach 是否发生在领域事件订阅器或明确的 application process 中
- 聚合是否没有跨服务 event name、payload schema、订阅方身份和传输协议知识

### inbound integration event 是外部事实入口

强度：`Must`

Why：
媒体处理平台通过消息或 callback 推回“处理已成功”时，这个事实来自系统外部。它可以推进内部状态，但必须先作为外部事实进入 adapter 或 integration subscriber，再转换成 `MarkMediaProcessingSucceededCmd`。它不是 `MediaProcessingTask` 聚合刚刚产生的领域事件。

Non-example：
把外部消息包装成 `MediaProcessingCompletedDomainEvent` 后交给领域订阅器，或者 integration subscriber 直接改 `MediaProcessingTask` 状态。

Audit cues：

- inbound integration event 是否先经过外部事实入口
- 会推进状态的外部事实是否转换成内部命令
- 是否没有把外部 payload、第三方状态码或消息协议带进领域事件

## 编排

### 单一主动作

强度：`Default`

Why：
默认入口一次只推进一个主动作。示例项目里，送审、启动媒体处理、发布内容、查询详情都各自有独立入口。这样写入口的成功条件和失败责任清晰，查入口也不会偷偷混入写语义。

Non-example：
一个“万能入口”同时尝试创建草稿、送审、触发媒体处理，或者一个查询接口顺手补写状态。

Audit cues：

- 当前入口是否只有一个主命令或一个主查询
- 入口失败时，责任边界是否清晰
- 如果业务需要长流程，是否已经拆成阶段化编排

## 查询

### 查询观察不反向污染写模型

强度：`Must`

Why：
示例项目要求 `GetContentDetailQry` 和 `GetMediaProcessingProgressQry` 只负责观察，不负责修复写模型状态。查询是为了看 `Content` 与 `MediaProcessingTask` 的当前投影，不是为了“顺便补一刀”把聚合改成自己想要的样子。

Non-example：
查询详情时发现状态过期，于是直接在查询路径里修改聚合；或者为了少写一层读模型，把查询结构直接当作写模型往回存。

Audit cues：

- 查询 handler 是否只读
- repository 是否仍只面向写模型，而不是被查询结构污染
- 查询结果中的派生字段是否来自投影、组装或外部观察，而不是偷偷改写聚合

## 集成边界

### 外部能力端口是防腐边界

强度：`Must`

Why：
示例项目中的媒体处理系统是外部能力。发起处理、轮询状态或查询处理结果属于内部主动消费外部能力，默认只能通过 `TriggerMediaProcessingCli`、`GetMediaProcessingStatusCli` 这类 client 防腐边界完成，并把外部协议翻译成内部业务语言。

接收媒体处理回调不是 client 调用，而是外部事实入口。callback controller 或 inbound subscriber 只负责接收已经发生的外部事实；会推进状态的 payload 必须翻译成内部命令，纯观察类回传才可以进入 query 或明确的 application entry point，不能把外部 DTO 当成流程真相源。

Non-example：
应用层直接依赖第三方 SDK 对象推进业务流程，或者 subscriber 直接拿外部回调 payload 改聚合，不经过边界转换。

Audit cues：

- 外部协议是否先被转换成内部命令 / 查询
- client 是否只承担防腐和能力边界职责，而不是自己保存业务真相
- callback 主路径与 polling 备用路径是否都遵守同一内部命令边界

## 最小工作流合同

1. 先判断当前工作是否需要先写 spec / plan；涉及新流程、新边界、新规则时，不跳过这一步。
2. 先跑 `cap4kPlan`，确认这次会生成什么、覆盖什么、缺什么。
3. 再跑 `cap4kGenerate`，把生成产物落到默认生成主面。
4. 如果发现缺的是 generator-capable skeleton，不要在 implementation 里手写替代，先回到 generation；如果缺的是 design / DDL / enum manifest / `types.registryFile` 这类业务输入合同，先回到 modeling；如果缺的是 generation 依赖的 KSP metadata 输出、配置或生产链路，先回到 generation / compile / setup。
5. 只有在分析链路明确需要时才进入 `cap4kAnalysis*`，不要把 analysis 当成默认主生成路径。
6. 生成完成后再进入手写补全，把项目特有编排、边界转换、查询组装写在手写主面。
7. 完成后必须验证，至少确认生成结果、目录归位、命令边界、聚合边界和最小运行检查都成立。
8. 验证通过后再进入 review，由审阅者按本页规则表和审计线索判断是否仍在 Default Happy Path 内。
