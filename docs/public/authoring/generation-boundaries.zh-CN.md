# 生成 / 手写边界

## 何时阅读本页

- 当你已经知道默认流程，但需要判断“这段代码该交给生成器，还是该留给作者手写”时，读本页。
- 如果还没有先确认默认规则，请先回到 [Default Happy Path](default-happy-path.zh-CN.md)。

本页说明 cap4k 项目里哪些产物默认交给生成器，哪些责任必须回到手写主面。教学主场景仍然是 `Content`、`MediaProcessingTask` 与 `MediaProcessingCli` 组成的内容发布与处理项目。

## 全局边界矩阵

| 产物类型 | 默认归属 | 说明 |
| --- | --- | --- |
| 生成的聚合骨架 | 生成主面 | 例如 `Content`、`MediaProcessingTask` 的结构骨架由生成器落出；作者在手写文件中补行为，不直接改写由生成器负责的文件 |
| 生成的命令 / 查询契约骨架 | 生成主面 | `CreateContentDraftCmd`、`GetContentDetailQry` 这类契约骨架可生成；手写逻辑放在 handler 或 adapter 侧 |
| 应用层流程编排 | 手写主面 | 项目特有的送审、媒体处理推进、发布编排不能期待生成器自动替你完成 |
| 外部系统协议转换 | 手写主面 | `MediaProcessingCli`、回调 payload、轮询结果转换都属于适配器责任 |
| 模板覆盖 | `Advanced` | 允许，但会引入升级漂移与额外审计成本，只在默认模板确实不够时进入 |

## 如何识别生成主面与手写主面

- 先看 `build/cap4k/plan.json`：这里会列出本次 `cap4kPlan` 规划出的目标产物与落点路径。凡是被 `cap4kGenerate` 计划写入、重复生成后仍会被覆盖的目标文件，都应先视为生成主面。
- 再看模块里的实际目录：生成后的契约骨架通常会落在业务模块的 `src/main/kotlin/.../application/commands/...`、`src/main/kotlin/.../application/queries/...`，或聚合对应的目标目录中。路径本身不自动说明“能不能手改”，是否属于生成主面要以 `plan.json` 和重复生成行为为准。
- 再看分析输入目录：像 `domain/build/generated/ksp/main/resources/metadata/...` 这类路径是生成或分析出来的输入资料，不是作者手写主面，也不该被当成业务逻辑落点。
- 手写主面通常是作者自己维护、需要长期保留人工修改的文件，例如 `application` 下的处理器实现、`adapter/application/...`、`adapter/portal/...`、`adapter/integration/...` 中的边界转换文件。它们不依赖 `plan.json` 直接落产物，重新生成也不应该默默覆盖作者改动。

## 当前现实（Current Reality）

- 当前模板覆盖采用项目级 `overrideDirs`。
- 当前覆盖方式是 preset 路径替换，不是 artifact / family 粒度 override。
- 一旦覆盖，团队就要承担升级漂移、差异复核、审计补偿成本。
- 这代表“当前真实能力”，不是建议把 override 当成默认起点。

上面这些规则就是今天的判断合同。下面只记录可能的后续强化方向，不改变当前默认边界。

## 后续强化方向（Future Strengthening Directions）

- 更细粒度模板 override。
- artifact / family 级覆盖控制。
- 更强的生成 / 手写归属标记。
- override 漂移诊断。
- 升级期的覆盖差异审计支持。

这些方向只记录未来强化可能性，不表示当前版本已经具备这些能力。
