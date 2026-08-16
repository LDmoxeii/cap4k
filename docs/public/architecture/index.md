# Architecture

`architecture/` 说明一个 cap4k 项目如何按 Clean Architecture 组织代码。这里比 [concepts](../concepts/index.md) 更偏规则：concepts 帮读者给业务对象和执行入口命名，architecture 帮读者判断代码应该落在哪一层、依赖应该指向哪里、生成骨架和手写逻辑如何共同维护边界。

cap4k public docs 使用同一个参考项目作为具体锚点：[reference-content-studio.md](../examples/reference-content-studio.md)。该历史示例由四个实现/装配模块组成；当前项目在选择 Endpoint 或 Integration Event payload generation 时，还应增加独立的 dependency-leaf contract module：

- `cap4k-reference-content-studio-domain`
- `cap4k-reference-content-studio-application`
- `cap4k-reference-content-studio-adapter`
- `cap4k-reference-content-studio-start`

## Published Contract Role

Contract 不是第五个业务层，而是跨服务 published language 的依赖叶子。它承载 [Actor Endpoint](../concepts/execution-and-ownership/actor-endpoint.md) Request/Response、Integration Event payload 以及轻量 `cap4k-contract-api` 依赖；Provider/Consumer 可以单向依赖它，但 contract 不依赖 domain、application、adapter、start、Spring 或 transport implementation。

## Four Layers

[Domain Layer](domain-layer.md) 是业务事实和业务不变量所在的位置。它负责 Aggregate、Entity、Value Object、Factory、Domain Service 和 Domain Event，参考概念页可以从 [Aggregate](../concepts/modeling-building-blocks/aggregate.md)、[Entity](../concepts/modeling-building-blocks/entity.md)、[Value Object](../concepts/modeling-building-blocks/value-object.md)、[Factory](../concepts/modeling-building-blocks/factory.md)、[Domain Service](../concepts/modeling-building-blocks/domain-service.md) 和 [Domain Event](../concepts/modeling-building-blocks/domain-event.md) 开始阅读。

[Application Layer](application-layer.md) 是用例编排位置。它组织 Command、Query、Capability、Endpoint Handler、Subscriber 和 Scheduled Reaction，并在写入提交和入口路由中使用 Unit of Work、Mediator 等框架能力。相关概念页包括 [Command Query Separation](../concepts/execution-and-ownership/command-query-separation.md)、[Command](../concepts/execution-and-ownership/command.md)、[Query](../concepts/execution-and-ownership/query.md)、[Subscriber](../concepts/execution-and-ownership/subscriber.md)、[Scheduled Reaction](../concepts/execution-and-ownership/scheduled-reaction.md)、[Unit Of Work](../concepts/execution-and-ownership/unit-of-work.md)、[Mediator](../concepts/execution-and-ownership/mediator.md) 和 [External Capability Anti-Corruption Layer](../concepts/execution-and-ownership/external-capability-anti-corruption-layer.md)。

[Adapter Layer](adapter-layer.md) 是协议转换位置。它负责 Controller、adapter-private DTO、query adapter、capability-handler 和 persistence adapter，把 HTTP、external service 或 persistence 细节转换成 application layer 能理解的请求和结果。对 inbound Integration Event，cap4k integration-event transport adapter/runtime 消费 HTTP/message protocol，解析、注册并分发 typed integration event；业务项目的 application-layer inbound integration subscriber 接收 typed external fact，处理幂等和语义翻译，并在需要改变状态时委托 Command/application behavior。

[Start Layer](start-layer.md) 是 Spring Boot runtime assembly 位置。它负责 local startup、runtime config、bean assembly 和 smoke path，把 domain、application、adapter 模块装配成可启动应用，但不承载业务真相。

## Cross-Cutting Pages

[Clean Architecture](clean-architecture.md) 给出完整 mental model：内层表达业务，外层处理技术入口和运行时装配，依赖方向向内。它也说明 cap4k generation 如何通过稳定骨架支持层模型，而不是替团队写业务决策。

[Dependency Rules](dependency-rules.md) 把允许和禁止的依赖方向写成审查规则。它强调 contract leaf ownership、domain independence、application orchestration、adapter protocol conversion 和 start assembly。

[Testing By Layer](testing-by-layer.md) 说明每层应该测试什么。测试重点放在手写业务行为、用例编排、协议转换和 runtime wiring，同时保护 generated skeleton 与 handwritten logic 的边界。

阅读 architecture 时，可以先读 [Clean Architecture](clean-architecture.md) 和 [Dependency Rules](dependency-rules.md)，再按 domain、application、adapter、start 顺序进入 layer 页面，最后用 [Testing By Layer](testing-by-layer.md) 检查测试责任是否和代码边界一致。
