# 项目编写工作流

本页面向使用 cap4k 编写业务项目的人类作者。它给出从业务讨论到生成、实现、验证、分析和最终审计的默认顺序。

本页流程默认用 [示例总览](examples/index.md) 的内容发布与媒体处理项目落地。讨论、生成、实现和审计各阶段都应能回到 `Content`、`MediaProcessingTask`、callback 主路径和 polling 备用路径这套同一参考语境。

## 读者

  人类作者负责领域决策、架构取舍、生成结果审阅和最终审计。
  AI 可以作为主要实现者，帮助整理 DDL、design、代码和测试，但必须在审计前给出可复核证据。
  审阅者按本页确认流程是否完整，而不是只看代码是否能编译。

## 总流程

1. 和用户收敛业务流程。
2. 形成领域模型、DDL 和 design 输入。
3. 先跑 plan，再决定是否 generate。
4. 区分生成物、骨架、快照和手写代码。
5. 实现命令、查询、订阅、适配器和领域行为。
6. 运行测试与本地验证。
7. 生成 analysis 和流程图。
8. 人类做最终审计并记录缺口。

## 1. 和用户收敛业务流程

先把业务讲清楚，再让框架和生成器介入。讨论至少要回答：

  主流程是什么，备用流程是什么；
  哪些动作改变状态，哪些动作只是查询；
  哪些事实属于聚合内部，哪些事实来自外部系统；
  失败、重试、幂等、回调和补偿由谁承担；
  当前切片要实现到什么范围，哪些只记录为缺口。

复杂 DDD 流程在写代码前应先形成用户可读的技术方案。方案要说明聚合、命令、查询、事件、外部能力、测试和本地运行方式，而不是直接跳到类名堆叠。

## 2. 形成领域模型、DDL、design

领域模型决定业务边界，DDL 和 design 是生成器输入合同。

  聚合根、实体、值对象、生命周期和领域事件先由人类作者确认。
  DDL 用表、列、关系、唯一约束和注释表达可生成的聚合结构。
  design JSON 用 `command`、`query`、`client`、`api_payload`、`domain_event`、`integration_event`、`domain_service`、`saga` 表达用例、接口和骨架意图；`validator` 不是 core design tag。
  `types.enumManifest` 用于共享枚举定义，配合 DB `@Type` 使用；manifest entry 不需要重复写入 `types.registryFile`。
  `types.valueObjectManifest` 用于 JSON backed value object source；用 `scope = shared | aggregate` 区分共享和聚合独立值对象。

输入源细节见 [生成输入源](generator/input sources.md)。如果某个概念不能被当前输入源表达，应先标成缺口，不要伪装成已有生成能力。

## 3. 先 plan 后 generate

源码生成前先跑计划任务：

```powershell
./gradlew cap4kPlan
```

读取 `build/cap4k/plan.json`，至少确认：

  `generatorId`：来自哪个生成族；
  `templateId`：使用哪个模板；
  `outputPath`：将写到哪里；
  `outputKind`：是 `GENERATED_SOURCE`、`CHECKED_IN_SOURCE` 还是 `OUTPUT_ARTIFACT`；
  `resolvedOutputRoot`：最终输出根；
  `conflictPolicy`：遇到已有文件时跳过、覆盖还是失败。

确认计划符合预期后，再运行：

```powershell
./gradlew cap4kGenerate
```

`cap4kGenerate` 是默认的 planned source pipeline 生成任务；它按计划导出 design + aggregate 这条源码链路中的各类 artifact。只有当你明确只需要 build owned `GENERATED_SOURCE` 输出时，才改走：

```powershell
./gradlew cap4kGenerateSources
```

`cap4kGenerateSources` 只导出 aggregate / aggregate projection 家族里的 `GENERATED_SOURCE` 计划项，不负责完整的 planned source pipeline。bootstrap 也遵循同样原则：先 `cap4kBootstrapPlan`，再 `cap4kBootstrap`。

## 4. 区分生成物、骨架、快照、手写代码

cap4k 项目中至少有四类产物：

| 类型 | 典型位置 | 作者规则 |
|     |     |     |
| 活跃生成源码 | `build/generated/cap4k/main/kotlin` | 构建拥有，可能反复覆盖，不要手写业务逻辑 |
| checked in 骨架 | `src/main/kotlin` 中的计划产物 | 先看 `plan.json` 和 `conflictPolicy`，只有明确留给作者补充时才改 |
| 复制审计快照 | `src generated/main/kotlin` | 只是从 build 生成目录复制出来的审计/学习快照，不是活跃生成输出 |
| 普通手写代码 | 不在 recurring plan item 中的源码 | 按分层责任长期维护 |

`src generated/main/kotlin` 不能被当成生成器真正输出根。真实活跃生成输出仍在各模块的 `build/generated/cap4k/main/kotlin`。

更多 ownership 判断见 [生成 / 手写边界](generation boundaries.md)。

## 5. 实现命令、查询、订阅、适配器

在开始实现前先过一道骨架闸门：

  如果缺的是 `command`、`query`、`client`、`api_payload`、`domain_event`、`integration_event`、`domain_service`、`saga`、subscriber、`*QryHandler.kt`、`*CliHandler.kt` 这类 generator capable skeleton，停止 implementation，回到 generation。这里的 `domain_event` 不只是 payload；它还会生成对应 subscriber / handler 壳。`integration_event` 只有 inbound 会生成 subscriber 壳，outbound 只生成事件契约。
  如果缺的是 aggregate、entity、repository、factory、specification、enum、唯一 helper 这类 aggregate family skeleton，并且 DDL / type contract 已经存在，停止 implementation，回到 generation。关系和字段映射事实属于 aggregate / entity generation input，不是独立 plan item 或独立 skeleton 家族。
  如果缺的是 design entry、DDL 表列注释、`types.enumManifest`、`types.valueObjectManifest`、`types.registryFile` 这类业务输入合同，停止 generation，回到 modeling。
- 如果缺的是 generation 依赖的 DB、design、manifest 或 IR 输入链路，先回到 generation / compile / setup，不要自动判成 modeling。
  只有当前 generator 明确不支持的 surface，才允许手写补齐，并且必须在审阅说明中写出“不支持生成”的原因。

实现时按 [公开战术模型](tactical model.md) 归位：

  聚合行为、生命周期、规约和领域服务表达领域规则；
  命令处理器推进写用例，加载聚合、调用行为并通过工作单元保存；
  查询处理器默认物理落在 adapter 侧，用于读模型和投影；
  外部能力 client handler 默认物理落在 adapter 侧，用于外部能力防腐；
  订阅器、job 或应用服务负责流程编排，通过 `Mediator.cmd`、`Mediator.qry`、`Mediator.requests` 发送下一步请求；
  controller、消息入口和回调入口只做协议转换，不抢业务真相。

当流程跨多个类、多个入口或多个聚合时，应先更新技术方案，再实现代码。

## 6. 测试与本地运行

默认先验证领域和应用主链路：

  domain 测试覆盖聚合状态迁移、拒绝条件、生命周期和领域服务决策；
  application 测试覆盖命令处理、订阅编排、外部能力 fake 和工作单元保存；
  adapter、persistence、integration 测试可以存在，但不应替代领域和应用行为测试；
  没有 OpenAPI/Swagger 目标时，可以用 `.http` 文件或 smoke test 证明本地入口可运行。

审计测试时要同时看执行结果和覆盖形态。全量测试通过仍然可能留下测试形态缺口，例如关键命令 guard 只被 HTTP smoke 间接覆盖、领域负向生命周期规则没有直接行为测试、多个 listener 响应同一领域事实但缺少幂等和 command side retreat 覆盖、no op 结果只有布尔值无法断言退让原因。出现这些情况时，应记录残余测试风险，而不是把 runtime smoke 当成 command level policy 的唯一证明。

AI 交付时必须提供测试、编译、生成或本地 smoke 的证据。没有证据的“已完成”不能直接进入人类审计。

## 7. 生成 analysis 和流程图

analysis 是观察链路，不是源码生成链路。通常在代码能编译后运行：

```powershell
./gradlew cap4kAnalysisPlan
./gradlew cap4kAnalysisGenerate
```

先确认 `sources.irAnalysis.inputDirs` 指向各模块 `build/cap4k code analysis` 输出，再检查 `build/cap4k/analysis plan.json` 是否只包含预期的 flow / drawing board 产物。

analysis 输出用于审阅流程、依赖和设计结果。它不能替代测试，也不能倒推业务源码 ownership。

## 8. 人类审计

最终审计由人类作者负责。至少确认：

  业务流程是否仍由聚合、命令、查询、事件和编排面表达；
  生成输入是否与领域决策一致；
  生成物、手写物、模板覆盖和快照是否分界清楚；
  写入是否收敛在命令处理和工作单元边界；
  query / external capability client handler 的物理落点和责任描述是否正确；
  测试、本地运行、生成和 analysis 证据是否可复核；
  测试覆盖形态是否足够：domain、application、adapter/integration smoke、generation/design contract、analysis/flow evidence 各自承担了正确责任；
  关键行为若只由 smoke test 间接证明，是否已经记录残余测试风险；
  缺口是否被记录，而不是被局部代码伪装成框架能力。

AI 可以完成大量实现工作，但不能替代人类做领域判断和最终审计。

## 保留缺口

当前应显式保留的缺口包括：

  design 输入暂不支持 `value_object`；
  `validator` 不是 core design tag；通用 validator artifact 必须由 addon 贡献；
  `integration_event` 已支持契约和 inbound subscriber 骨架，但 MQ 绑定、外部协议适配和业务处理逻辑仍由项目手写；
  生命周期识别仍有已知限制；
  enum translation 属于 addon 方向，不是核心 aggregate DSL 开关；
  HTTP JPA 集成事件本地示例需要最小框架表 DDL；
  addon 作者指南与业务项目使用 addon 是两件事。
