# Examples

`examples/` 把 public docs 里的概念和架构规则落到同一个可检查的参考项目上。本章只使用 sibling repo `cap4k-reference-content-studio` 作为示例宇宙；阅读这里的页面时，不需要在多个虚构项目之间切换。

推荐阅读方式是先在 public docs 页面理解一个设计点，再回到 sibling repo 查看完整代码、设计输入、生成计划、`.http` 文件、测试和分析证据。public docs 负责解释“为什么这样建模”和“应该看哪里”；`cap4k-reference-content-studio` 负责展示完整实现和可运行参考流程。

## Pages

- [Reference Content Studio](reference-content-studio.md)：参考项目总览、四个模块、业务场景和证据面。
- [Run The Reference Project](run-the-reference-project.md)：README 已验证的启动入口、`.http` 阅读顺序和本地观察路径。
- [Default Publication Flow](default-publication-flow.md)：从内容草稿到审核、媒体处理回调、最终发布的默认路径。
- [Paid Publication Saga Flow](paid-publication-saga-flow.md)：显式 opt-in 的 paid publication Saga、正向步骤和补偿。
- [Value Object And Type Inputs](value-object-and-type-inputs.md)：`MediaProcessingResultSnapshot`、`ReleasePolicy`、`MediaProcessingResultStatus` 与 type input manifests。
- [Generation And Analysis Evidence](generation-and-analysis-evidence.md)：`design/design.json`、schema、plan、analysis flows、drawing board 和测试证据面。

## How To Use

读概念页时，先抓住概念边界，再打开 examples 里的对应页面。例如 [Aggregate](../concepts/modeling-building-blocks/aggregate.md)、[Value Object](../concepts/modeling-building-blocks/value-object.md)、[Domain Event](../concepts/modeling-building-blocks/domain-event.md)、[Integration Event](../concepts/modeling-building-blocks/integration-event.md)、[Saga](../concepts/modeling-building-blocks/saga.md)、[Command](../concepts/execution-and-ownership/command.md)、[Subscriber](../concepts/execution-and-ownership/subscriber.md) 都可以在 `cap4k-reference-content-studio` 找到直接锚点。

读架构页时，先看 [Architecture](../architecture/index.md) 对四层职责的说明，再用本章里的参考项目映射到 `domain`、`application`、`adapter`、`start` 四个模块。这样可以把 Clean Architecture 规则、generated skeleton、handwritten logic 和测试边界放在同一组文件里检查。

真正要理解完整代码时，请进入 sibling repo `cap4k-reference-content-studio`。已提交的 inspection surfaces 包括 `design/design.json`、`design/value-objects.json`、`design/enums.json`、schema、source、`.http` 文件、smoke tests、contract tests、domain tests、`analysis/flows` 和 `analysis/drawing-board` 产物。

运行 README 中的 generation / analysis 命令后，还可以在本地 `build/cap4k/` 下查看 `plan.json` 和 `analysis-plan.json`。
