# Integration Event

Integration Event 是跨系统或跨 bounded context 传播的外部事实。它属于 published language：字段、命名和语义要面向边界外的读者保持稳定，而不是暴露内部 Aggregate 结构。它可以是 outbound event，向外发布本系统已确认的事实；也可以是 inbound event，表示外部系统传入并被本系统理解的事实。

当事实需要跨服务、跨团队或跨上下文传播，并且接收方不应依赖本系统的内部 Domain Event 时，应建模 Integration Event。Domain Event 可以触发 outbound Integration Event 的发布，但二者不是同一个契约。Inbound Integration Event 进入系统后，通常由 adapter/application 层转换为 command、subscriber 处理或 anti-corruption translation。

在 cap4k 中，`design.json` 支持 `integration_event` tag 表达 Integration Event 骨架。generator 可以提供事件类型、字段结构和目录位置；published language 的语义、版本兼容、外部字段命名、幂等和失败处理策略需要手写设计。Integration Event 与 External Capability Anti-Corruption Layer 协作，避免外部协议直接污染 domain layer。

参考项目入口是 [reference-content-studio.md](../../examples/reference-content-studio.md)。在 `cap4k-reference-content-studio` 中，`design/design.json` 包含 `integration_event` 条目，可作为事件契约的输入锚点，并可继续查看完整设计文件和相关流程。

设计边界是跨边界事实。不要把内部 Entity 字段全量发布出去，不要用 Integration Event 表达内部方法调用，不要把 inbound payload 直接塞进 Aggregate，也不要把普通 callback 自动称为 Saga。常见误用包括混淆 Domain Event 与 Integration Event、把外部协议字段当作领域模型字段、或忽略 published language 的兼容性。

使用 Integration Event 时，保持 inbound/outbound 区分清楚，字段使用边界语言，并通过 anti-corruption translation 保护 domain layer。Domain Event 到 Integration Event 的映射应明确，生成骨架和手写契约语义应分离，不要把外部事实伪装成内部不变量。

<!-- IMAGE_PROMPT:
Purpose: 帮助读者区分 Domain Event、Integration Event、inbound/outbound 和 published language。
Type: concept map
Prompt: Draw a cap4k integration event boundary map. Show a bounded context with Domain Event inside, outbound Integration Event crossing to another system, inbound Integration Event entering through an anti-corruption layer and becoming a Command. Label published language at the boundary. Use Chinese labels and preserve English identifiers.
Must show: external fact, inbound event, outbound event, published language, anti-corruption translation, Domain Event to Integration Event mapping
Must avoid: exposing internal Aggregate structure as public contract, implying every callback is Saga, arrows that violate Clean Architecture dependency rules
Alt text after insertion: Integration Event 边界图，展示 outbound 和 inbound 外部事实如何通过 published language 与防腐转换协作。
-->
