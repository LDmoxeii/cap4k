# 快速开始

[English](getting-started.md)

## 适用对象

- 想落地 DDD，但不想先搭一套过重框架叙事的团队
- 希望先按默认主路径推进，再考虑高级概念的团队

## 推荐阅读顺序

1. [README.zh-CN.md](../../../README.zh-CN.md)
2. 先用下面的最小工作流跑一个小聚合片段
3. 需要更清楚的概念边界时，再读 [框架定位](framework-positioning.zh-CN.md)

## 最小工作流

1. 先识别聚合根与实体边界
2. 分别定义命令意图与查询意图
3. 让状态变更收敛到命令处理路径
4. 需要流程继续时，由聚合根发出领域事件
5. 把 controller、job、事件处理器看作协同点，而不是业务真相所在
6. 先为 `domain` 和 `application` 主链路补行为测试，再决定是否需要更重的基础设施测试

## 先走保守路径

- 先走默认 happy path
- 不要一开始就使用高级读写建模
- 没有明确问题前，不要先上 Saga、Strong ID、Domain Service

## 下一步阅读

- [框架定位](framework-positioning.zh-CN.md)
- [编写指南总览](index.zh-CN.md)
- [Default Happy Path](default-happy-path.zh-CN.md)
- [测试合同](testing-contract.zh-CN.md)
