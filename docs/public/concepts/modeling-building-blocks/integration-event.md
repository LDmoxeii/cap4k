# Integration Event

Integration Event 是跨系统或跨 bounded context 传播的外部事实。它属于 published language：字段、命名和语义要面向边界外的读者保持稳定，而不是暴露内部 Aggregate 结构。它可以是 outbound event，向外发布本系统已确认的事实；也可以是 inbound event，表示外部系统传入并被本系统理解的事实。

当事实需要跨服务、跨团队或跨上下文传播，并且接收方不应依赖本系统的内部 Domain Event 时，应建模 Integration Event。Domain Event 可以触发 outbound Integration Event 的发布，但二者不是同一个契约。Inbound Integration Event 进入系统后，要区分两段责任：cap4k integration-event transport adapter/runtime 消费 HTTP/message 等外部协议，解析、注册并分发 typed integration event；application layer 再通过接收一个具体事件类型的方法级 Spring `@EventListener` Handler 解释这个外部事实，做幂等和语义转换，并在需要改变状态时委托 Command 或 application behavior。生成代码中名为 `*Subscriber` 的 class 只是这些 Handler methods 的容器。

在 cap4k 中，`design.json` 支持 `integration_event` tag 表达 Integration Event 与 Handler 容器骨架。事件 payload 生成到 `project.contractModulePath` 指向的 dependency-leaf contract module，并使用轻量 `cap4k-contract-api` 中的共享 annotation；inbound `*Subscriber` 容器继续生成到 application module。generator 可以提供事件类型、字段结构和目录位置；published language 的语义、版本兼容、外部字段命名、幂等和失败处理策略需要手写设计。Integration Event 与 External Capability Anti-Corruption Layer 协作，避免外部协议直接污染 domain layer。

参考项目入口是 [reference-content-studio.md](../../examples/reference-content-studio.md)。在 `cap4k-reference-content-studio` 中，`design/design.json` 包含 `integration_event` 条目，可作为事件契约的输入锚点，并可继续查看完整设计文件和相关流程。

设计边界是跨边界事实。不要把内部 Entity 字段全量发布出去，不要用 Integration Event 表达内部方法调用，也不要把 inbound payload 直接塞进 Aggregate。outbox 记录必须与本地 Command 事务一起提交，外部 transport 只能在提交成功后发布；事务回滚时 outbox 一起回滚。after-commit 唤醒只是优化，唤醒失败不能丢失已提交记录；失败交给 transport/provider polling、retry 与恢复。

入站 Integration Event 与本地 Domain Event 共用同步、串行、fail-fast 的 Handler 模型。不同方法级 `@Order` 值按数值从小到大执行，相同值不承诺次序。Handler 返回后，Runtime 等待当前 scope 中由 `askAsync()` / `callAsync()` / Endpoint `sendAsync()` 启动的全部受管任务；Handler 或受管任务失败都会使本服务的本次 delivery 失败，因此 transport 不会把它当作已完成。Handler 必须返回 `Unit/void`，并且不能使用 `@Async`、`suspend`、`@TransactionalEventListener`、`defaultExecution=false`、多事件声明或多态订阅。跨服务的“至少一次”仍由各自 transport/provider 的 delivery 与 retry 边界负责；一个服务的完成不代表其他接收方已经完成。

使用 Integration Event 时，保持 inbound/outbound 区分清楚，字段使用边界语言，并通过 anti-corruption translation 保护 domain layer。`inbound` / `outbound` 只决定本地 artifact binding，不自动绑定消息中间件；HTTP、RabbitMQ、RocketMQ 等 provider topology 仍通过 Spring YAML/runtime configuration 按稳定 `eventName` 装配。移动 payload 到 contract module 不改变既有 inbox/outbox、ack、retry 或 delivery 语义。Domain Event 到 Integration Event 的映射应明确，生成骨架和手写契约语义应分离，不要把外部事实伪装成内部不变量。
