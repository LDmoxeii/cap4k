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

## Source Generation 边界

`cap4kAnalysisGenerate` 不是 source generation。flow 和 drawing-board output 默认是 observation evidence，用来观察已有代码结构。

drawing-board 文件只有在内容满足当前 [Design JSON](design-json.md) 规则时，才可以手动复制或注册为 design JSON input。`command.resultFields` 现在是受支持的 design JSON 字段。

但 copied fragment 仍包含旧字段 `responseFields`、unsupported tag 或 invalid artifact selection 时，它就不是合法的 design JSON，应先修正，再通过 `sources.designJson.files` 使用。

任意 analysis output 都不能自动当作 ordinary source-generation input skeleton。

## 边界检查

- `cap4kAnalysisGenerate` 不是 source generation。
- `flow` 和 `drawing-board` 是 analysis/observation outputs。
- 缺少 `nodes.json` 或 `rels.json` 表示 analysis input 不完整。
- `build/cap4k/analysis-plan.json` 是 `build/` 下的本地 generated evidence。
- 已提交的 `analysis/flows` 和 `analysis/drawing-board` 是 reference evidence，不是 runtime configuration。
