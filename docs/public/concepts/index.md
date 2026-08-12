# Concepts

`concepts/` 是 cap4k 的概念入口，帮助读者给业务想法命名，判断代码应该放在哪一层，并理解 cap4k 如何把 generated structure 与 handwritten logic 分开。先读概念页，可以更容易看懂后续的 architecture、examples、authoring、generator 和 reference 章节。

概念分成两组。`modeling-building-blocks/` 说明业务模型由什么组成，包括聚合、实体、值对象、Strong ID、Factory、Domain Service、Domain Event 和 Integration Event。`execution-and-ownership/` 说明写入、读取、外部能力、反应、持久化、生成骨架和手写逻辑如何被拥有，包括 Command / Query separation、Command、Query、Subscriber、Scheduled Reaction、Repository、Unit of Work、Mediator、External Capability Anti-Corruption Layer，以及 generated skeleton 与 handwritten logic 的边界。

建议阅读顺序是先理解 Aggregate，再读 Entity、Value Object 和 Strong ID；随后读 Factory 与 Domain Service，理解创建规则和跨对象决策；再读 Domain Event 与 Integration Event，区分本地事实和跨边界事实。完成建模 building blocks 后，再进入 execution and ownership 页面，把模型放进 Command、Query、Capability、Subscriber、Repository 和 generation ownership 的协作关系里理解。

## Modeling Building Blocks

- [Aggregate](modeling-building-blocks/aggregate.md)
- [Entity](modeling-building-blocks/entity.md)
- [Value Object](modeling-building-blocks/value-object.md)
- [Strong ID](modeling-building-blocks/strong-id.md)
- [Factory](modeling-building-blocks/factory.md)
- [Domain Service](modeling-building-blocks/domain-service.md)
- [Domain Event](modeling-building-blocks/domain-event.md)
- [Integration Event](modeling-building-blocks/integration-event.md)

## Execution And Ownership

- [Command Query Separation](execution-and-ownership/command-query-separation.md)
- [Command](execution-and-ownership/command.md)
- [Query](execution-and-ownership/query.md)
- [Subscriber](execution-and-ownership/subscriber.md)
- [Scheduled Reaction](execution-and-ownership/scheduled-reaction.md)
- [Repository](execution-and-ownership/repository.md)
- [Unit Of Work](execution-and-ownership/unit-of-work.md)
- [Mediator](execution-and-ownership/mediator.md)
- [Execution Context And Invocation Scope](execution-and-ownership/execution-context-and-invocation-scope.md)
- [External Capability Anti-Corruption Layer](execution-and-ownership/external-capability-anti-corruption-layer.md)
- [Generated Skeleton And Handwritten Logic](execution-and-ownership/generated-skeleton-and-handwritten-logic.md)
