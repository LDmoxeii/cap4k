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

## 背景概念

- 值对象
- 集成事件
- Repository 契约
- handler 家族
- cli

## 高级但有效的概念

- Domain Service
- Saga
- Strong ID

## 运行时与基础设施承载面

- JPA 运行时与 repository 落地路径
- 集成事件传输与持久化适配
- starter 与自动配置
- 其他 provider 级运行时支持

## 已退出核心定位的概念

- Wrapper 不再属于公开核心定位

## 高级建模提示

- 读写模型分离下的只读弱引用模板上下文属于高级模式
- repository 仍然只感知写模型
