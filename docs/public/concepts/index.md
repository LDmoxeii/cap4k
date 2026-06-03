# Concepts

`concepts/` 是 cap4k 的概念入口，帮助读者给业务想法命名，判断代码应该放在哪一层，并理解 cap4k 如何把 generated structure 与 handwritten logic 分开。先读概念页，可以更容易看懂后续的 architecture、examples、authoring、generator 和 reference 章节。

概念分成两组。`modeling-building-blocks/` 说明业务模型由什么组成，包括聚合、实体、值对象、Strong ID、Factory、Domain Service、Domain Event、Integration Event 和 Saga。`execution-and-ownership/` 说明请求、读取、反应、持久化、生成骨架和手写逻辑如何被拥有，包括 Command / Query separation、Command、Query、Subscriber、Scheduled Reaction、Repository、Unit of Work、Mediator、External Capability Anti-Corruption Layer，以及 generated skeleton 与 handwritten logic 的边界。

建议阅读顺序是先理解 Aggregate，再读 Entity、Value Object 和 Strong ID；随后读 Factory 与 Domain Service，理解创建规则和跨对象决策；再读 Domain Event、Integration Event 和 Saga，区分领域事实、外部事实与持久化跨步骤协调。完成建模 building blocks 后，再进入 execution and ownership 页面，把模型放进 command、query、subscriber、repository 和 generation ownership 的协作关系里理解。

## Modeling Building Blocks

- [Aggregate](modeling-building-blocks/aggregate.md)
- [Entity](modeling-building-blocks/entity.md)
- [Value Object](modeling-building-blocks/value-object.md)
- [Strong ID](modeling-building-blocks/strong-id.md)
- [Factory](modeling-building-blocks/factory.md)
- [Domain Service](modeling-building-blocks/domain-service.md)
- [Domain Event](modeling-building-blocks/domain-event.md)
- [Integration Event](modeling-building-blocks/integration-event.md)
- [Saga](modeling-building-blocks/saga.md)

## Execution And Ownership

- [Command Query Separation](execution-and-ownership/command-query-separation.md)
- [Command](execution-and-ownership/command.md)
- [Query](execution-and-ownership/query.md)
- [Subscriber](execution-and-ownership/subscriber.md)
- [Scheduled Reaction](execution-and-ownership/scheduled-reaction.md)
- [Repository](execution-and-ownership/repository.md)
- [Unit Of Work](execution-and-ownership/unit-of-work.md)
- [Mediator](execution-and-ownership/mediator.md)
- [External Capability Anti-Corruption Layer](execution-and-ownership/external-capability-anti-corruption-layer.md)
- [Generated Skeleton And Handwritten Logic](execution-and-ownership/generated-skeleton-and-handwritten-logic.md)

<!-- IMAGE_PROMPT:
Purpose: 帮助读者把 concepts 章节看成从业务建模到执行所有权的阅读地图。
Type: concept map
Prompt: Draw a focused concept map for cap4k concepts. Show two groups: modeling-building-blocks for naming the business model, and execution-and-ownership for placing behavior and persistence. Highlight the suggested reading flow from Aggregate to value types, creation/decision concepts, events, Saga, then execution ownership. Use restrained colors and Chinese labels with English identifiers preserved.
Must show: modeling-building-blocks group, execution-and-ownership group, reading flow, aggregate as model boundary, events as facts, generated structure versus handwritten logic boundary
Must avoid: implying cap4k writes business decisions automatically, implying every callback is Saga, implying the generator replaces business modeling or ownership review, implying capabilities outside backend DDD authoring scope, arrows that violate Clean Architecture dependency rules
Alt text after insertion: cap4k concepts 概念地图，左侧是建模 building blocks，右侧是执行与所有权概念。
-->
