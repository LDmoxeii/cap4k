# cap4k

[English](README.md)

> cap4k 是一套面向 AI 驱动实现与人工审阅的简化版 DDD 战术框架。

## 主线

`聚合根 -> 实体 -> 命令 / 查询 -> 领域事件 -> 流程入口`

## 如何开始

1. 先阅读本 README 中的默认 happy path。
2. 然后阅读 [快速开始](docs/public/getting-started.zh-CN.md)。
3. 在把高级概念或运行时承载面当作默认承诺之前，再阅读 [框架定位](docs/public/framework-positioning.zh-CN.md)。

## cap4k 是什么

- 一套以聚合为中心的 DDD 战术框架
- 默认以命令 / 查询为主线
- 明确认可领域事件
- 面向 AI 驱动实现与人工审阅
- 能把设计、运行时、生成链路投影到同一框架世界观中，而不是把框架缩减成代码生成器

## cap4k 不是什么

- 不是一个泛化代码生成平台
- 不是一个以 JPA 包装为第一身份的框架
- 不是一个以集成事件平台为第一身份的框架
- 不是一个前端 TypeScript 生成框架
- 不是一个把所有 DDD 模式都同等前置宣传的框架

## 默认 Happy Path

- 单命令只变更一个聚合根
- 聚合根是写入主面
- 所有领域状态变更都收敛到命令处理路径
- 领域事件由聚合根统一登记与发出
- 多处理器执行顺序不承诺
- `cli` 是防腐层边界，不是主流程真相源

## 文档导航

- [快速开始](docs/public/getting-started.zh-CN.md)
- [框架定位](docs/public/framework-positioning.zh-CN.md)
- 仓库内部设计材料见 `docs/superpowers/`
