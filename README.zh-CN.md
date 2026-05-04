# cap4k

[English](README.md)

cap4k 是一套面向 AI 驱动实现与人工审阅的简化版 DDD 战术框架。

## 主线

`聚合根 -> 实体 -> 命令 / 查询 -> 领域事件 -> 编排层`

## 如何开始

1. 先阅读本 README 中的默认 happy path。
2. 然后阅读 [快速开始](docs/public/getting-started.zh-CN.md)。
3. 在把高级概念或运行时承载面当作默认承诺之前，再阅读 [框架定位](docs/public/framework-positioning.zh-CN.md)。

## cap4k 是什么

- 一套以聚合为中心的 DDD 战术框架
- 默认以命令 / 查询为主线
- 明确认可领域事件
- 面向 AI 驱动实现与人工审阅
- 让设计、运行时与生成层保持可见，而不是把框架缩减成代码生成器

## cap4k 不是什么

- 不是一个泛化代码生成平台
- 不是一个以 JPA 包装为第一身份的框架
- 不是一个围绕下游事件处理来组织的框架
- 不是一个前端代码生成框架
- 不是一个把所有 DDD 模式都同等前置宣传的框架

## 默认 Happy Path

- 单命令只变更一个聚合根
- 聚合根是写入侧主入口
- 所有领域状态变更都收敛到命令处理路径
- 领域事件由聚合根统一登记与发出
- 多处理器执行顺序不承诺
- `cli` 是防腐层边界，不是主流程的事实来源

## 文档导航

- [快速开始](docs/public/getting-started.zh-CN.md)
- [框架定位](docs/public/framework-positioning.zh-CN.md)
- 仓库内部的规范、计划和设计材料都在 `docs/superpowers/` 下
