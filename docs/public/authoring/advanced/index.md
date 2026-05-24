# Concept Selection Guide

> 本组页面不是概念总表，而是在 [Default Happy Path](../default-happy-path.md) 基础上选择合适建模概念的决策入口。

## 什么时候需要补充概念

- 你已经按默认路径把 `Content`、`MediaProcessingTask`、命令、事件、callback 主路径和 polling 备用路径梳理清楚，但模型仍然别扭。
- 继续硬塞在默认聚合、默认命令或默认查询里，会让业务边界失真，或者把手写面变成混杂的技术补丁。
- 审阅者已经能明确指出“问题不是规则没学会，而是当前业务需要另一个更贴切的概念来表达”。

## 决策入口

- 什么时候需要更完整的值语义，而不是继续用枚举或原始类型顶着写：[Value Object](value-object.md)
- 什么时候行为明显属于领域，但不自然落在 `Content`、`MediaProcessingTask` 或某个值对象里：[Domain Service](domain-service.md)
- 什么时候已经进入 persisted long-running coordination、最终一致性、恢复和补偿协调，而不是一次命令就能讲清：[Saga](saga.md)
- 什么时候需要更强的 ID 类型安全，但又不想把工程封装误讲成 DDD 核心概念：[Strong ID](strong-id.md)
- 什么时候只需要统一类型表达和导航表面，而不是让仓储直接持有跨聚合可写对象：[Read-only Weak Reference](read-only-weak-reference.md)

## 默认前提

- 先用 [Default Happy Path](../default-happy-path.md) 建立共同语境，再判断需要哪些概念。
- 引入概念前先能说明它解决什么问题，而不是因为“框架支持”就直接使用。
- callback 仍然是媒体处理结果回传的主路径，polling 仍然只是备用路径；任何概念都不能把这条主次关系翻掉。
- 当前 Saga runtime 的第一优先切片是 persisted coordination + retry/recovery/compensation，不是完整的 waiting-style workflow / callback-step resume 引擎。

## 参考主场景

- [内容发布与处理示例项目总览](../examples/reference-project-overview.md)
- [概念选择实践示例总览](../examples/advanced-concepts-overview.md)
- [内容草稿到发布主链路](../examples/content-draft-to-publish.md)
- [媒体处理 callback 主路径](../examples/media-processing-callback.md)
- [媒体处理 polling 备用路径](../examples/media-processing-polling.md)

这些示例不是额外阅读负担，而是概念选择的回跳底图。进入 `Value Object`、`Domain Service`、`Saga`、`Strong ID`、`Read-only Weak Reference` 前，先能在这五页里指出要解决的问题落在哪里，再判断是否需要它。

## 使用方式

1. 先定位你卡住的是值语义、领域归位、长流程协调、ID 类型安全，还是跨聚合只读导航。
2. 再读对应概念页，确认它是否真的解决这个问题，而不是顺手制造更重的维护成本。
3. 落地后按概念页里的审计检查点复核，确保团队知道自己为什么选择这个概念。
