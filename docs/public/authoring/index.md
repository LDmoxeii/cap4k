# Cap4k 编写指南总览

> 这套文档定义 cap4k 项目的默认编写方式、审计方式，以及选择合适建模概念时的决策入口。

默认硬边界是：能生成的骨架先回到 DDL / design / registry 合同与 `cap4kPlan`；如果 generation 依赖的 KSP metadata 还没产出，就先修 generation / compile / setup 链路；implementation 不手写 generator-capable 替代物。

## 这套文档解决什么问题

- 让项目作者知道如何直接开始编写 cap4k 项目
- 让审阅者知道如何按 Default Happy Path 审核项目
- 让人类作者在 AI 协作中保留领域决策、路径取舍和最终审计权

## 人类作者与 AI 协作边界

这套 authoring 文档是给人类作者使用的决策和审计入口。它回答的是：

- 当前业务应该放在哪个 DDD 战术对象上
- 什么时候遵循 Default Happy Path 约定，什么时候引入更具体的建模概念
- 哪些代码应该生成，哪些代码应该手写
- AI 交付后，人类应该按什么规则做最终审计

AI 可以辅助梳理方案、实现主要代码，并在最终审计前完成测试、编译、生成和分析验证。但 AI 的结论不能替代人类对领域流程、架构取舍和最终代码形态的判断。

AI 作者规则以独立 skill 维护。authoring 文档不作为 AI skill 的运行时依赖；两者共享项目纪律，但服务对象不同。

## 阅读路径

### 项目作者

1. [框架定位](framework-positioning.md)
2. [项目编写工作流](project-authoring-workflow.md)
3. [快速开始](getting-started.md)
4. [Default Happy Path](default-happy-path.md)
5. [示例总览](examples/index.md)
6. [公开战术模型](tactical-model.md)
7. [生成器指南](generator/index.md)
8. [领域层指南](domain.md)
9. [应用层指南](application.md)
10. [测试合同](testing-contract.md)
11. [适配器层指南](adapter.md)
12. [概念选择指南](advanced/index.md)

### 深度用户 / 框架贡献者

- 先完整阅读本页和 Default Happy Path
- 再按需阅读横切规范与 generator reference

## 主题入口

- [框架定位](framework-positioning.md)
- [快速开始](getting-started.md)
- [项目编写工作流](project-authoring-workflow.md)
- [Default Happy Path](default-happy-path.md)
- [示例总览](examples/index.md)
- [Generator Guide](generator/index.md)
- [生成输入源](generator/input-sources.md)
- [Addon 与 SPI 使用](generator/addons-and-spi.md)
- [公开战术模型](tactical-model.md)
- [Domain Authoring Guide](domain.md)
- [Application Authoring Guide](application.md)
- [Adapter Authoring Guide](adapter.md)
- [Concept Selection Guide](advanced/index.md)

## 统一示例

所有概念讲解默认回到 [示例总览](examples/index.md) 中的同一套内容发布与媒体处理参考项目。`examples/` 是示例总入口；各层指南、概念选择页和生成边界页只在这套参考项目上补充视角，不另起样例宇宙。

## 横切规范

- [命名与目录规范](naming-and-layout.md)
- [生成 / 手写边界](generation-boundaries.md)
- [示例合同](example-contract.md)
- [测试合同](testing-contract.md)

## 审计重点

人类最终审计时，至少确认这些问题：

- 业务流程是否仍然围绕聚合根、命令、查询、领域事件和编排面表达
- 写入行为是否收敛在命令处理路径，而不是散落在开放服务入口、外部事实入口或内部触发入口中
- 生成物、手写物、模板覆盖和生成快照是否清楚分界
- `domain` / `application` 主链路是否有符合 [测试合同](testing-contract.md) 的行为验证
- AI 是否给出了可复核的测试、编译、生成、分析或链接检查证据
- 缺口是否被明确记录，而不是被局部约定伪装成框架能力

## 当前缺口与扩展位

这些主题可以被讨论和示范，但不能在当前文档中被当成完整默认能力：

- 值对象、Saga、Domain Service 的作者定性仍会继续细化
- 值对象、Saga、Domain Service 的生成器支持仍需要后续切片
- 分层模型和公开战术模型还会继续收敛
- design 对 command、query、client、domain_event、integration_event、domain_service、saga 等已支持契约会继续打磨；`value_object` 由 `types.valueObjectManifest` 表达，不是 design tag
- `drawing_board.json` 面向跨服务集成事件沟通的用法留作后续扩展
- addon / SPI 面向更深度使用场景的作者规则会在更多真实使用后补强
