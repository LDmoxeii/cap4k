# 生成 / 手写边界

本页说明 cap4k 项目里哪些产物默认交给生成器，哪些责任必须回到手写主面。教学主场景仍然是 `Content`、`MediaProcessingTask` 与 `MediaProcessingCli` 组成的内容发布与处理项目。

## 全局边界矩阵

| 产物类型 | 默认归属 | 说明 |
| --- | --- | --- |
| 生成的聚合骨架 | 生成主面 | 例如 `Content`、`MediaProcessingTask` 的结构骨架由生成器落出；作者在手写文件中补行为，不直接抢 generator-owned 文件 |
| 生成的命令 / 查询契约骨架 | 生成主面 | `CreateContentDraftCmd`、`GetContentDetailQry` 这类契约骨架可生成；手写逻辑放在 handler 或 adapter 侧 |
| application orchestration 具体逻辑 | 手写主面 | 项目特有的送审、媒体处理推进、发布编排不能期待 generator 自动替你完成 |
| 外部系统协议转换 | 手写主面 | `MediaProcessingCli`、回调 payload、轮询结果转换都属于适配器责任 |
| 模板覆盖 | `Advanced` | 允许，但会引入升级漂移与额外审计成本，只在默认模板确实不够时进入 |

## Current Reality

- 当前模板覆盖采用项目级 `overrideDirs`。
- 当前覆盖方式是 preset 路径替换，不是 artifact / family 粒度 override。
- 一旦覆盖，团队就要承担升级漂移、差异复核、审计补偿成本。
- 这代表“当前真实能力”，不是建议把 override 当成默认起点。

## Future Strengthening Directions

- 更细粒度模板 override。
- artifact / family 级覆盖控制。
- 更强 generated / handwritten ownership 标记。
- override 漂移诊断。
- 升级期的覆盖差异审计支持。

这些方向只记录未来强化可能性，不表示当前版本已经具备这些能力。
