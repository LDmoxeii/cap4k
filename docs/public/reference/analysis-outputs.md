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

`design-elements.json` 是 optional input，同时 compiler analysis 会输出它。

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

flow evidence 回答 controller、subscriber、job、Command dispatch、Query path 或 external Capability wiring 如何连接。它们不证明 business behavior 正确。

## Drawing Board Files

常见 drawing-board output：

| Path | Purpose |
| --- | --- |
| `analysis/drawing-board/drawing_board_command.json` | Command anchors。 |
| `analysis/drawing-board/drawing_board_query.json` | Query anchors。 |
| `analysis/drawing-board/drawing_board_capability.json` | Capability anchors。 |
| `analysis/drawing-board/drawing_board_domain_event.json` | domain event anchors。 |
| `analysis/drawing-board/drawing_board_integration_event.json` | integration event anchors。 |

drawing-board evidence 回答代码中有哪些 anchors。它不说明这些 anchors 已经完成。

## Analysis Metadata Completeness

Drawing Board 与 metadata-dependent Flow Analysis 依赖专用的 BINARY-retained compile-time contract：

- `DesignBlockMetadata`：设计载体 identity、package、description、aggregate ownership、artifact family/variant 等；
- `AggregateElementMetadata`：Aggregate element 的 aggregate/type/root identity 等。

它们来自 `io.github.ldmoxeii:cap4k-analysis-metadata`，业务模块只在 `compileOnly` classpath 使用。默认 templates 会生成这些 annotations；自定义 templates 删除 annotation 时，项目明确 opt out 对应能力。

当已显式请求的 analysis capability 发现必要 metadata 缺失时，`cap4kAnalysisPlan` 和 `cap4kAnalysisGenerate` 会在 planning/rendering 前失败。Diagnostic 会列出 metadata owner symbol、缺失 annotation、受影响的 `Drawing Board` / `Flow Analysis`，并提示恢复默认 template，或补回 annotation 与 compile-only dependency。cap4k 不会通过命名、路径或 sidecar skeleton index 猜测 authoring metadata，也不会把残缺结果伪装为完整 evidence。

## Source Generation 边界

`cap4kAnalysisGenerate` 不是 source generation。flow 和 drawing-board output 默认是 observation evidence，用来观察已有代码结构。

drawing-board 文件由当前 generator 输出为直接兼容 [Design JSON](design-json.md) 的普通 JSON array。需要恢复或传递当前 tactical contract 时，由人或 Agent 选择文件并显式注册到 `sources.designJson.files`；不需要额外 converter。

显式注册保留原有 artifact direction。跨上下文把 outbound Integration Event 改成 inbound 是新的设计决策，必须在输入中明确修改；cap4k 不会自动转换。

任意 analysis output 都不能自动当作 ordinary source-generation input skeleton。只有 Drawing Board 的当前 Design JSON contract 支持上述显式输入路径，flow、nodes、rels 和其他 observation output 不支持。

## 边界检查

- `cap4kAnalysisGenerate` 不是 source generation。
- `flow` 和 `drawing-board` 是显式配置的 analysis/observation outputs。
- 缺失必要 analysis metadata 时必须 fail fast；恢复默认 template/annotation 与 `compileOnly` dependency 后才能重新启用能力。
- 缺少 `nodes.json` 或 `rels.json` 表示 analysis input 不完整。
- `build/cap4k/analysis-plan.json` 是 `build/` 下的本地 generated evidence。
- 已提交的 `analysis/flows` 和 `analysis/drawing-board` 是 reference evidence，不是 runtime configuration。
