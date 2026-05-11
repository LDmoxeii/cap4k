# 框架定位

[English](framework-positioning.md)

## 前景概念

- 聚合根
- 实体
- 领域事件
- 命令
- 查询

## 默认 Happy Path

- 单命令只变更一个聚合根
- 聚合根是写入主面
- 状态变更收敛到命令处理路径
- 领域事件由聚合根统一登记与发出
- `cli` 是防腐层边界，不是主流程真相源

## 作者角色定位

cap4k 面向 AI 辅助实现，但框架定位和业务边界仍由人类作者最终判断。

- 人类作者决定业务概念是否进入公开主路径
- 人类作者决定高级概念是否真的需要进入当前切片
- AI 可以辅助实现和验证，但不能把局部项目写法包装成框架默认承诺

## 背景概念

这些概念仍然重要，但它们位于公开第一屏叙事之后，通常要在默认主路径跑通后再进入视野。

- 值对象
- 集成事件
- 用于聚合装载与持久化边界的 Repository 契约
- 用于标准命令 / 查询执行的 handler 家族
- 作为外部能力防腐层边界的 `cli`

这些概念在代码中的默认协作方式见 [公开战术模型](tactical-model.zh-CN.md)。当前未完整支持或仍在 addon 方向上的能力，应按该页和 [项目编写工作流](project-authoring-workflow.zh-CN.md#保留缺口) 的缺口说明审计。

## 高级但有效的概念

- Domain Service
- Saga
- Strong ID

## 运行时与基础设施承载面

这些都是代码现实中的正式落地承载面，但它们支撑框架落地，不定义框架的公开第一身份。

- 基于 JPA 的 repository 与工作单元落地路径
- 集成事件传输与持久化适配
- 用于运行时装配的 starter 与自动配置模块
- 宿主技术栈需要时的其他 provider 级运行时支持

## 已退出核心定位的概念

- Wrapper 不再属于公开核心定位，并应在遗留清理范围外视为已弃用

## 高级建模提示

- 带可选只读引用上下文的读写模型分离属于高级模式
- repository 仍然只感知写模型

## 从定位到编写

- [项目编写工作流](project-authoring-workflow.zh-CN.md)
- [公开战术模型](tactical-model.zh-CN.md)
- [编写指南总览](index.zh-CN.md)
- [Default Happy Path](default-happy-path.zh-CN.md)
