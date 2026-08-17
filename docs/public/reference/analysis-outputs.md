# Analysis Outputs

本页的 analysis outputs 是 observation evidence，用来审查既有代码结构；它们不生成 ordinary source skeletons。

## 输入 Root

Compiler analysis output root：

```text
build/cap4k-code-analysis
```

必要 IR files：

| File | Purpose |
| --- | --- |
| `nodes.json` | code element nodes。 |
| `rels.json` | relationships between nodes。 |
| `aggregate-elements.json` | Aggregate element 结构证据；没有元素时也必须存在，内容为 `[]`。 |

`design-elements.json` 仍是 optional input。Compiler analysis 会输出 `nodes.json`、`rels.json`、`design-elements.json` 和 `aggregate-elements.json` 四个文件。

## DSL Selection

```kotlin
cap4k {
    sources {
        irAnalysis {
            inputDirs.from(
                "demo-domain/build/cap4k-code-analysis",
                "demo-application/build/cap4k-code-analysis",
                "demo-adapter/build/cap4k-code-analysis"
            )
        }
    }
}
```

使用 `sources.irAnalysis.inputDirs`。Analysis 使用 source id `ir-analysis` 和 generator ids `flow`、`drawing-board`。

## Plan Output

`cap4kAnalysisPlan` 写出：

```text
build/cap4k/analysis-plan.json
```

analysis plan 审查重点：

- input dirs。
- generator ids `flow` and `drawing-board`。
- output roots。
- 是否被误读成 ordinary source generation。

## Generated Evidence

`cap4kAnalysisGenerate` 导出 analysis artifacts。参考项目已提交的 evidence 使用：

```text
analysis/flows
analysis/drawing-board
```

这些 roots 可通过 layout 配置：

```kotlin
cap4k {
    layout {
        flow { outputRoot.set("analysis/flows") }
        drawingBoard { outputRoot.set("analysis/drawing-board") }
    }
}
```

## Flow Files

常见 flow output：

| Path | Purpose |
| --- | --- |
| `analysis/flows/index.json` | flow index and input metadata。 |
| `analysis/flows/*.json` | structured flow data。 |
| `analysis/flows/*.mmd` | Mermaid flow rendering source。 |

flow evidence 是从实际代码入口出发的默认业务因果投影：保留具体入口、Command、Domain Event 与 Integration Event，隐藏 Command/Event Handler 和 Entity Method，并把任意长度的已知隐藏路径收缩为可见节点之间的因果边。概念入口分为 Actor、Event、Time；当前生产 detector 包括 Spring HTTP Controller method、typed Spring MVC `EndpointMvcBinding` registration 与 generated `EndpointRpcProviderBinding` registration（Actor）、无上游 Inbound Integration Event（Event）和 Spring `@Scheduled` method（Time）。Endpoint HTTP/RPC Provider registration 通过 generated Request/operation identity 跨类、跨文件关联独立 Provider Handler；各自到 Command 的协议专属关系可形成独立 Flow root，到 Query 的关系只保留在 raw Graph。generated RPC Consumer handler/client artifact 不是 Actor evidence 或 Flow root。Query、Capability、Validator 等事实仍可存在于 raw graph，但不进入默认 Flow。它们不证明 business behavior 正确。

Spring `@Scheduled` method 以真实 method 节点 `temporaltriggermethod` 出现在 raw graph 中，且只有直接发送 Command 时才通过 `TemporalTriggerMethodToCommand` 形成默认 causal Flow。仅发送 Query、调用 Capability 或执行纯技术逻辑的 scheduled method 不形成 Flow；普通内部 method 直接发送 Command 也不会生成通用 sender 入口。未来 CLI 等真实 adapter detector 可以用与来源节点语义匹配的显式 `*ToCommand` relationship 扩展 Flow，但任意后缀关系不自动获得入口资格，旧 `commandsendermethod` / `CommandSenderMethodToCommand` fallback 已删除。

Temporal Trigger detection 只描述 Analyzer 观察到的代码入口与因果关系，不提供 scheduler runtime、Job generator、cron/misfire/retry 语义、`entryFamily` wire 或跨入口 process stitching。

阅读 Flow 数量时使用以下规则：

- 连续的 raw graph evidence 从同一个真实入口到达后续 Command/Event 时，保持在同一张 entry-centered Flow 中，不需要 process stitching。
- 两个各自具有入口证据、完成投影后均为零入度的真实入口分别生成两张 Flow，即使它们共享下游 Command 或 Event。
- 多张 Flow 通过 `index.json`、稳定 entry identity 和共享可见节点关联阅读；共享后缀本身不证明它们属于一个自动推断的业务过程。

## Drawing Board Files

常见 drawing-board output：

| Path | Purpose |
| --- | --- |
| `analysis/drawing-board/drawing_board_command.json` | Command anchors。 |
| `analysis/drawing-board/drawing_board_query.json` | Query anchors。 |
| `analysis/drawing-board/drawing_board_capability.json` | Capability anchors。 |
| `analysis/drawing-board/drawing_board_domain_event.json` | domain event anchors。 |
| `analysis/drawing-board/drawing_board_integration_event.json` | integration event anchors。 |
| `analysis/drawing-board/drawing_board_aggregate_elements.json` | Aggregate element 结构证据。 |

普通 `drawing_board_<tag>.json` 文件按 Design JSON tag 分类。`drawing_board_aggregate_elements.json` 则由独立 Aggregate Structure canonical 分区驱动，记录 `carrierQualifiedName`、`aggregate`、`name`、`packageName`、`description`、`type` 与 `root`；它不属于 raw graph 或 Drawing Board design projection，不带 `tag`，也不是 Design JSON。

drawing-board evidence 回答代码中有哪些 anchors。它不说明这些 anchors 已经完成。

## Analysis Metadata Completeness

Drawing Board 与 metadata-dependent Flow Analysis 依赖专用的 BINARY-retained compile-time contract：

- `DesignBlockMetadata`：设计载体 identity、package、description、aggregate ownership、artifact family/variant 等；
- `AggregateElementMetadata`：Aggregate element 的 carrier identity、aggregate、name、package、description、type 与 root 等结构信息。

它们来自 `io.github.ldmoxeii:cap4k-analysis-metadata`，业务模块只在 `compileOnly` classpath 使用。默认 templates 会生成这些 annotations；自定义 templates 删除 annotation 时，项目明确 opt out 对应能力。

当已显式请求的 analysis capability 发现必要 metadata 缺失时，`cap4kAnalysisPlan` 和 `cap4kAnalysisGenerate` 会在 planning/rendering 前失败。Diagnostic 会列出 metadata owner symbol、缺失 annotation、受影响的 `Drawing Board` / `Flow Analysis`，并提示恢复默认 template，或补回 annotation 与 compile-only dependency。cap4k 不会通过命名、路径或 sidecar skeleton index 猜测 authoring metadata，也不会把残缺结果伪装为完整 evidence。

事件 metadata 的 `eventName` 必须与 runtime annotation 的未修改字面值一致。Analyzer 不会 trim runtime `eventName` 或用 runtime 值补齐缺失 metadata。Integration Event 的 inbound/outbound direction 只来自显式设计 metadata；runtime `@IntegrationEvent` 只携带 eventName，Analyzer 不从 runtime annotation 推断 direction。只有 metadata 与 runtime 都没有名称的 transient Domain Event 可以保持空名称。

## Source Generation 边界

`cap4kAnalysisGenerate` 不是 source generation。flow 和 drawing-board output 默认是 observation evidence，用来观察已有代码结构。

Drawing Board 的普通 tag 文件由当前 generator 输出为直接兼容 [Design JSON](design-json.md) 的普通 JSON array。需要恢复或传递当前 tactical contract 时，由人或 Agent 选择这些普通 tag 文件并显式注册到 `sources.designJson.files`；不需要额外 converter。

`drawing_board_aggregate_elements.json` 是这一规则的明确例外：它是 Aggregate element 结构证据，不是 Design JSON，不能注册到 `sources.designJson.files`。`repository` 也不是受支持的普通 Design JSON tag。

显式注册保留原有 artifact direction。跨上下文把 outbound Integration Event 改成 inbound 是新的设计决策，必须在输入中明确修改；cap4k 不会自动转换。

任意 analysis output 都不能自动当作 ordinary source-generation input skeleton。只有 Drawing Board 的普通 tag 输出支持上述显式 Design JSON 输入路径；flow、nodes、rels、aggregate element 结构证据和其他 observation output 不支持。

## 边界检查

- `cap4kAnalysisGenerate` 不是 source generation。
- `flow` 和 `drawing-board` 是显式配置的 analysis/observation outputs；Pipeline `flow` 是唯一公开 Flow 产品入口，已退役的独立 flow-export plugin 不再存在。
- 缺失必要 analysis metadata 时必须 fail fast；恢复默认 template/annotation 与 `compileOnly` dependency 后才能重新启用能力。
- 缺少 `nodes.json`、`rels.json` 或 `aggregate-elements.json` 表示 analysis input 不完整；无 Aggregate element 时后者也必须为 `[]`。
- `build/cap4k/analysis-plan.json` 是 `build/` 下的本地 generated evidence。
- 已提交的 `analysis/flows` 和 `analysis/drawing-board` 是 reference evidence，不是 runtime configuration。
