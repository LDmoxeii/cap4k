# 测试合同

## 何时阅读本页

- 当你已经按 [Default Happy Path](./default-happy-path.md) 确定 `domain` / `application` 主链路，并准备给项目建立第一版默认测试形态时，读本页。
- 当你需要把测试、本地运行、analysis 和最终人类审计串成一个交付顺序时，同时阅读 [项目编写工作流](project-authoring-workflow.md#6-测试与本地运行) 与 [analysis 步骤](project-authoring-workflow.md#7-生成-analysis-和流程图)。
- 如果你还没有先讲清 `Content`、`MediaProcessingTask`、`TriggerMediaProcessingCli`、callback 主路径和 polling 备用路径，先回到[参考项目总览](./examples/reference-project-overview.md)以及默认主路径相关页面，包括[内容草稿到发布主链路](./examples/content-draft-to-publish.md)、[媒体处理 callback 主路径](./examples/media-processing-callback.md) 和 [媒体处理 polling 备用路径](./examples/media-processing-polling.md)。

## 这份合同解决什么问题

- 不让 runnable reference project 自己发明一套测试形态。
- 不让测试骨架掩盖领域行为。
- 不让框架魔法把作者和 AI 带偏。

这份合同只回答“第一版默认测试长什么样、归谁拥有、放在什么层表达”。它不是 bootstrap 生成功能说明，也不是运行时测试支持库说明；如果作者想回看默认主链路与共享语境，应先回到 [Default Happy Path](./default-happy-path.md)、[参考项目总览](./examples/reference-project-overview.md)、[内容草稿到发布主链路](./examples/content-draft-to-publish.md)、[媒体处理 callback 主路径](./examples/media-processing-callback.md) 和 [媒体处理 polling 备用路径](./examples/media-processing-polling.md)。

## 默认边界

- 第一版官方默认只塑形 `domain` 行为测试与 `application` 编排测试。
- `bootstrap` 不生成测试骨架。
- `cap4k` 不提供 built-in heavy testing DSL。
- `adapter / persistence / integration` 可以测试，但不属于第一版统一骨架。

换句话说，这一页明确拒绝把测试 ownership 交给 bootstrap、生成产物或 runtime helper。默认测试合同活在作者指南里，服务的是作者如何表达领域规则与 application 编排，而不是框架如何代写测试。

## 共享参考锚点

- 统一参考项目仍然是 `Content`、`MediaProcessingTask`、`TriggerMediaProcessingCli`、callback 主路径、polling 备用路径。
- 默认 application 测试交接缝是 `ApproveContentReviewCmd -> StartMediaProcessingCmd`。

这份测试合同复用的仍然是 `#21` 已经建立的 shared reference project，不额外发明第二套样例宇宙。审阅者如果要核对默认主路径、命令语义和回传入口，应直接回跳到[参考项目总览](./examples/reference-project-overview.md)、[内容草稿到发布主链路](./examples/content-draft-to-publish.md)、[媒体处理 callback 主路径](./examples/media-processing-callback.md) 与 [媒体处理 polling 备用路径](./examples/media-processing-polling.md)，确认这里提到的交接缝和 callback-first、polling-fallback 语义完全一致。

## 默认测试形态

- `domain` 测试直接暴露规则、状态推进、拒绝条件。
- `application` 测试直接暴露命令编排、端口调用和 `ApproveContentReviewCmd -> StartMediaProcessingCmd` 交接缝。
- 主断言优先是前置事实、触发动作、业务结果，而不是容器启动。
- 测试执行结果和测试形态充分性要分开审计。`./gradlew test`
  通过只说明现有测试成功运行，不自动说明关键行为已经落在正确层被直接覆盖。
- 关键行为如果只通过 Spring / HTTP / callback smoke 间接覆盖，应在审计里标成残余测试风险，而不是用 smoke test 代替领域或 application 聚焦测试。

默认形态的目标是让读者一眼看见业务语义，而不是先被测试基础设施吞掉注意力。`Content` 的测试应当直接说明内容为何能从草稿推进到送审、批准、发布，`MediaProcessingTask` 的测试应当直接说明任务如何进入处理中、成功、失败或重试；application 测试则要把谁发出 `StartMediaProcessingCmd`、谁调用 `TriggerMediaProcessingCli` 讲清，而不是把重点放在 runtime wiring 是否能启动起来。

当示例包含 JSON-backed 值对象，例如 `MediaProcessingResultSnapshot`，默认测试也应保持业务可读：

- 值对象测试覆盖构造、归一化、非法输入和等值语义。
- converter 测试覆盖一次轻量 roundtrip，证明 JSON 承载没有改变领域值。
- 聚合行为测试覆盖 `MediaProcessingTask` 如何收下 snapshot。
- application 测试确认命令只保存聚合根，不把聚合内部值对象单独加入工作单元。

当示例包含多个 listener 响应同一个领域事实时，测试形态还应显式说明：

- listener 之间不依赖执行顺序；
- 每条后续写路径都会进入自己的 command 做 zero-trust 校验；
- 不适用的 listener 通过 command-side no-op / retreat 安全退出；
- no-op 结果应尽量有 typed decision 或其他可检查原因，避免只剩
  `false` 这类无法说明退让原因的布尔值。
- 审计或诊断至少能说明哪个 listener 被唤醒、发送了哪个 command、命令 applied 还是 no-op，以及 no-op 原因。

## 允许的薄 helper

- 允许：test data builder、fake port、少量 fixture setup。
- 不允许：把业务语义折叠成 opaque DSL；把 runtime wiring 偷带进默认测试路径。

helper 的价值只能是减轻样板噪音，不能替代业务叙述。`builder` 应帮助作者更快搭出前置事实，`fake port` 应帮助作者看清 application 对外部边界的调用，fixture setup 也应保持薄且局部；一旦 helper 让测试不再直接显露 `Content`、`MediaProcessingTask`、`ApproveContentReviewCmd` 或 `StartMediaProcessingCmd` 的语义，它就已经越界。

## 非默认但允许存在的测试

- 测试机制 / 风格可以更重，例如 `@SpringBootTest`。
- 测试范围 / 目标可以更靠外，例如 repository / JPA wiring、callback controller / integration listener、polling job、full infra end-to-end。
- 这些测试不是被禁止，而是第一版 testing contract 不统一塑形它们。

这些测试可以存在，也经常有实际价值，但它们不应反过来定义第一版默认测试长什么样。这里要先分清两类事情：`@SpringBootTest` 这类是“怎么起测试”的机制选择，repository / JPA wiring、callback controller / integration listener、polling job、full infra end-to-end 这类是“在测哪一层、哪一段入口”的范围选择。第一版合同只说明它们都不在统一默认骨架内，而不是把它们揉成同一种测试类别。

## 与 runnable reference project 的关系

- `#27` 只负责示范这份合同，不重新定义规则。
- 最少应展示 `Content`、`MediaProcessingTask` 和 `ApproveContentReviewCmd -> StartMediaProcessingCmd` 的 application 样本。

runnable reference project 是这份合同的消费者，不是来源。它应该拿 `#21` 已经固定的共享参考项目来演示默认测试如何落地，而不是重新解释 callback、polling、`TriggerMediaProcessingCli` 或命令交接缝是什么；如果示例与本页冲突，应先以本页和其回链的默认路径文档为准。

## 审计线索

- 看测试是否先把前置事实、触发动作、业务结果讲清。
- 看 `domain` 测试是否依赖重容器。
- 看 `application` 测试是否掉回 runtime wiring。
- 看关键 aggregate lifecycle 是否同时有正向推进和负向拒绝测试。
- 看 state-changing command 是否覆盖 zero-trust validation、no-op、already-applied 和 invariant-rejection 路径。
- 看 multi-listener continuation 是否覆盖独立 listener、幂等、不依赖顺序，以及不适用路径的 command-side retreat。
- 看 no-op 结果是否足够可检查，能说明退让原因。
- 看测试是否能复核 multi-listener outcome 的观测语义，而不是只证明某条 happy path 最终成功。
- 看直接 SQL fixture、enum ordinal、`@TestMethodOrder`、手写 polling loop 是否已记录残余风险，且没有被当成 command-level policy 的唯一证明。
- 看 helper 展开后，测试主体里是否仍能直接看见 `Content`、`MediaProcessingTask`、`ApproveContentReviewCmd`、`StartMediaProcessingCmd`、fake port 调用或业务结果断言；如果看不见，就说明 helper 已经过厚。
- 看 JSON-backed 值对象测试是否围绕值语义、converter roundtrip 和聚合行为展开，而不是回到 table-backed `@VO` 或单独持久化值对象。

进一步审计时，还应看测试是否继续沿用 `Content`、`MediaProcessingTask`、`TriggerMediaProcessingCli`、callback 主路径与 polling 备用路径这些共享锚点；如果一个测试样本需要脱离这组对象才能成立，通常说明它已经不是默认 testing contract 在塑形的范围。
