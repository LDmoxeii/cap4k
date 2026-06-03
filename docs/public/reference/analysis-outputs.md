# Analysis Outputs

本页的 analysis outputs 是 observation evidence，用来审查既有代码结构；它们不生成 ordinary source skeletons。

## 输入 Root

当前 compiler analysis output root：

```text
build/cap4k-code-analysis
```

必要 IR files：

| File | Purpose |
| --- | --- |
| `nodes.json` | code element nodes。 |
| `rels.json` | relationships between nodes。 |

`design-elements.json` 当前是 optional input，同时 compiler analysis 会输出它。

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

flow evidence 回答 controller、subscriber、job、Saga、Command dispatch、Query path 或 external capability wiring 如何连接。它们不证明 business behavior 正确。

## Drawing Board Files

常见 drawing-board output：

| Path | Purpose |
| --- | --- |
| `analysis/drawing-board/drawing_board_command.json` | Command anchors。 |
| `analysis/drawing-board/drawing_board_query.json` | Query anchors。 |
| `analysis/drawing-board/drawing_board_client.json` | client anchors。 |
| `analysis/drawing-board/drawing_board_domain_event.json` | domain event anchors。 |
| `analysis/drawing-board/drawing_board_integration_event.json` | integration event anchors。 |
| `analysis/drawing-board/drawing_board_saga.json` | Saga anchors。 |

drawing-board evidence 回答代码中有哪些 anchors。它不说明这些 anchors 已经完成。

## Boundary Checks

- `cap4kAnalysisGenerate` 不是 source generation。
- `flow` 和 `drawing-board` 是 analysis/observation outputs。
- 缺少 `nodes.json` 或 `rels.json` 表示 analysis input 不完整。
- `build/cap4k/analysis-plan.json` 是 `build/` 下的本地 generated evidence。
- 已提交的 `analysis/flows` 和 `analysis/drawing-board` 是 reference evidence，不是 runtime configuration。
