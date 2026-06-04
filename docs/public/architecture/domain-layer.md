# Domain Layer

Domain layer 是 cap4k 项目中最靠近业务真相的层。它负责表达业务事实、生命周期、不变量和领域语言；其他层可以调用它，但不能把 adapter protocol、runtime config 或外部 payload 细节带进来。

## 负责

Domain layer 负责 [Aggregate](../concepts/modeling-building-blocks/aggregate.md)、[Entity](../concepts/modeling-building-blocks/entity.md)、[Value Object](../concepts/modeling-building-blocks/value-object.md)、[Factory](../concepts/modeling-building-blocks/factory.md)、[Domain Service](../concepts/modeling-building-blocks/domain-service.md) 和 [Domain Event](../concepts/modeling-building-blocks/domain-event.md)。Aggregate Root 是事务一致性边界，Entity 和 Value Object 帮助表达内部结构，Factory 管理创建规则，Domain Service 表达不适合放进单个 Aggregate 的领域决策，Domain Event 记录已经发生的业务事实。

在参考项目中，domain layer 负责 `Content` 的发布准备、review approval、媒体处理状态、paid publication eligibility 输入和 `ContentPublicationReadyDomainEvent` 这类领域事实。domain tests 应优先覆盖状态推进、拒绝路径、默认状态和事件触发条件。

## 不负责

Domain layer 不负责 Controller、API Payload、HTTP status、request header、callback payload、client-handler、persistence adapter、Spring Boot runtime config 或 local startup。它也不负责把外部 service 的字段名直接变成领域对象字段。外部协议进入系统时，framework/runtime transport 负责 HTTP/message consume、parse、register 和 dispatch；adapter mapping 收敛协议形状；application code 解释 typed external fact、做幂等和语义翻译，并委托 Command 或 application use case。Domain layer 只接收领域语言中的意图、值对象或事实。

如果一个 Aggregate 需要知道 URL、JSON 字段、SQL column、Spring profile 或 external service error code，说明协议细节已经越界。domain layer 可以表达“媒体处理已经完成”这样的业务事实，但不应该表达“某个 HTTP callback body 的字段名是什么”。

## 生成骨架

cap4k generation 可以在 domain module 中生成稳定骨架，例如 aggregate 目录、Entity、Value Object、Repository contract、Domain Event、Domain Service 或 Factory 入口。生成骨架负责保持命名、目录和基础 shape 一致，方便 application layer 找到写入边界和事件类型。

生成骨架不是业务结论。`ContentPublicationReadyDomainEvent` 这样的事件类型可以由设计输入形成稳定入口，但什么时候产生事件、事件携带哪些业务含义、哪些状态不允许推进，仍属于手写逻辑。

## 手写逻辑

手写逻辑应该落在 Aggregate behavior、Factory、Domain Service 和 domain tests 中。参考项目锚点包括 `ContentBehavior.kt`、`ContentFactory.kt`、`ContentPublicationReadyDomainEvent`、`ContentBehaviorTest.kt` 和 `ContentFactoryTest.kt`。

`ContentBehavior.kt` 应表达内容状态如何改变，`ContentFactory.kt` 应表达创建规则和默认值，domain tests 应直接验证业务语言，而不是通过 HTTP smoke path 间接猜测领域行为。测试名称和断言应该让读者看出业务规则，而不是只看出生成骨架存在。

## 依赖方向

Domain layer 的依赖方向是向内自足：它不依赖 application、adapter 或 start。application layer 可以依赖 domain 来加载 Aggregate、调用行为并保存；adapter 和 start 可以通过更外层协作间接使用 domain，但 domain 不反向引用它们。

Repository contract、Domain Event 类型和值对象可以被外层使用，但它们不能因为外层协议改变而失去领域命名。若协议字段和领域语言不一致，protocol shape 应由 framework/runtime transport 与 adapter mapping 收敛；application 只解释 typed fact 或 use-case input，并委托 Command 或 application behavior。Domain 不承担协议转换。

## 参考项目

参考项目入口是 [reference-content-studio.md](../examples/reference-content-studio.md)。阅读 `cap4k-reference-content-studio-domain` 时，优先定位这些锚点：

- `ContentBehavior.kt`
- `ContentFactory.kt`
- `ContentPublicationReadyDomainEvent`
- `ContentBehaviorTest.kt`
- `ContentFactoryTest.kt`

这些文件能展示 domain layer 如何把生成骨架、手写状态转移和领域测试放在同一业务语言下。

## 审核

审核 domain layer 时，先看业务不变量是否集中在 Aggregate 或 Domain Service 中，再看 Factory 是否表达创建规则，Value Object 是否承载真正的值语义，Domain Event 是否描述已经发生的业务事实。随后检查是否出现 adapter/protocol concerns，例如 HTTP DTO、Controller、client-handler、Spring config 或 external payload 字段。最后确认 domain tests 是否覆盖手写业务行为，而不是只验证骨架文件存在。
