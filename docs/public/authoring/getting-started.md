# 快速开始

[English](getting-started.en.md)

## 适用对象

- 想落地 DDD，但不想先搭一套过重框架叙事的团队
- 希望先按默认主路径推进，再考虑高级概念的团队

## 推荐阅读顺序

1. [README.md](../../../README.md)
2. 先用下面的最小工作流跑一个小聚合片段
3. 需要端到端 authoring 顺序时，读 [项目编写工作流](project-authoring-workflow.md)
4. 需要建立项目骨架时，读 [Bootstrap](generator/bootstrap.md)
5. 需要更清楚的概念边界时，再读 [框架定位](framework-positioning.md)

## 最小工作流

1. 先识别聚合根与实体边界
2. 分别定义命令意图与查询意图
3. 让状态变更收敛到命令处理路径
4. 需要流程继续时，由聚合根发出领域事件
5. 把开放服务入口、外部事实入口、内部触发入口看作边界协同点，而不是业务真相所在
6. 先为 `domain` 和 `application` 主链路补行为测试，再决定是否需要更重的基础设施测试

## AI 协作时的最小审计

AI 可以负责草拟设计、实现代码和运行验证，但人类作者需要在接受结果前确认：

- 领域流程是否仍然符合上面的最小工作流
- 生成 / 手写边界是否清楚
- AI 是否给出可复核的测试、编译、生成或分析证据
- 不支持或未完成的能力是否被明确标成缺口

## 先走保守路径

- 先走默认 happy path
- 不要一开始就使用高级读写建模
- 没有明确问题前，不要先上 Saga、Strong ID、Domain Service

## 下一步阅读

- [项目编写工作流](project-authoring-workflow.md)
- [Bootstrap](generator/bootstrap.md)
- [框架定位](framework-positioning.md)
- [编写指南总览](index.md)
- [Default Happy Path](default-happy-path.md)
- [测试合同](testing-contract.md)
