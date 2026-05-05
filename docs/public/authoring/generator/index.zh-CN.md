# Generator Guide

> 本组文档只面向项目作者：什么时候建骨架，什么时候规划生成，什么时候真正落产物，什么时候才进入 analysis 任务族。

## 这条指南解决什么问题

- 什么时候跑 bootstrap
- 什么时候跑 plan / generate
- 什么时候进入 analysis 任务族
- 什么时候需要看 `plan.json`

## 最小原理

- 生成步骤顺序是固定的；作者只需要准备输入、启用需要的生成族、确认输出落点，不需要自己编排阶段顺序。
- `sources { }` 先提供设计、数据库、KSP 元数据或 IR 分析输入；插件会先把这些输入整理成统一的生成计划。
- `cap4kPlan` 先回答“这次将生成什么、落到哪里、由哪类输出根持有”。
- `cap4kGenerate` 再真正落源码产物。
- `cap4kAnalysis*` 只负责导出观察材料，不是默认主生成路径。

## 任务族硬边界

| 任务 | 只用在这里 | 不要拿来做什么 |
| --- | --- | --- |
| `cap4kPlan` | 规划 design / aggregate 这条源码生成链路，并写出 `build/cap4k/plan.json` | 不负责 bootstrap，也不负责 flow / drawing-board 分析导出 |
| `cap4kGenerate` | 按 `cap4kPlan` 同一条源码链路真正落源码产物 | 不替代 `cap4kPlan` 的人工确认，也不导出 analysis 产物 |
| `cap4kAnalysisPlan` | 规划 `irAnalysis` 驱动的 `flow` / `drawing-board` 导出，并写出 `build/cap4k/analysis-plan.json` | 不生成命令、查询、聚合、订阅器等业务源码 |
| `cap4kAnalysisGenerate` | 导出 `flow` / `drawing-board` 这类观察型产物 | 不替代 `cap4kGenerate`，也不是默认项目起手任务 |

默认作者顺序很窄：

1. 宿主根和模块还没就绪时，先处理 bootstrap。
2. 需要业务源码时，先跑 `cap4kPlan`。
3. 读 `build/cap4k/plan.json`，确认落点与归属。
4. 再跑 `cap4kGenerate`。
5. 只有明确需要 flow / drawing-board 时，才进入 `cap4kAnalysisPlan` / `cap4kAnalysisGenerate`。

## 阅读顺序

1. [Bootstrap](bootstrap.zh-CN.md)
2. [Code Generation](code-generation.zh-CN.md)
3. [Code Analysis](code-analysis.zh-CN.md)
4. [Generator DSL Reference](../../reference/generator-dsl.zh-CN.md)
