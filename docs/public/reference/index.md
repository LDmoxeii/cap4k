# Reference

本章是查表入口，不重复 [Generator](../generator/index.md) 和 [Authoring](../authoring/index.md) 的流程说明。需要确认字段名、task output、JSON key、manifest shape、output ownership 或常见误用时，从这里进入。

## 查表入口

| 要查什么 | 入口 |
| --- | --- |
| Gradle plugin id、公开 task 名称、task output | [Gradle Plugin](gradle-plugin.md) |
| `cap4k {}` extension block、字段、最小 Gradle 片段 | [Generator DSL](generator-dsl.md) |
| `design/design.json` normal tags、common keys、`resultFields`、`eventName`、artifact metadata | [Design JSON](design-json.md) |
| DB/schema comment annotations、relation metadata、type markers | [DB Schema Annotations](db-schema-annotations.md) |
| `types.valueObjectManifest`、shared 与 aggregate-owned Value Object、JSON-backed value | [Value Object Manifest](value-object-manifest.md) |
| `types.enumManifest`、enum `items` shape、enum generation notes | [Enum Manifest](enum-manifest.md) |
| design JSON、schema comments、enum manifests 和 value-object manifests 的离线预检查 | [Generator Input Validation](generator-input-validation.md) |
| `plan.json`、`bootstrap-plan.json`、ownership review fields | [Plan JSON](plan-json.md) |
| `CHECKED_IN_SOURCE`、`GENERATED_SOURCE`、`OUTPUT_ARTIFACT` 和输出根 | [Outputs](outputs.md) |
| `build/cap4k-code-analysis`、`nodes.json`、`rels.json`、flows、drawing-board | [Analysis Outputs](analysis-outputs.md) |
| runtime SQL resource 与表用途 | [Runtime Database Schema](runtime-database-schema.md) |
| 生成器、设计输入、analysis、bootstrap、Saga、adapter/domain 边界误用 | [Common Mistakes](common-mistakes.md) |

## 按任务阅读

| 读者任务 | 最小阅读组合 |
| --- | --- |
| 接入 pipeline plugin | [Gradle Plugin](gradle-plugin.md), [Generator DSL](generator-dsl.md) |
| 写 source generation 输入 | [Generator DSL](generator-dsl.md), [Design JSON](design-json.md), [DB Schema Annotations](db-schema-annotations.md), [Value Object Manifest](value-object-manifest.md), [Enum Manifest](enum-manifest.md), [Generator Input Validation](generator-input-validation.md) |
| 审查 generation plan | [Plan JSON](plan-json.md), [Outputs](outputs.md), [Common Mistakes](common-mistakes.md) |
| 区分 checked-in skeleton 与 build-owned source | [Outputs](outputs.md), [Plan JSON](plan-json.md) |
| 生成或提交 analysis evidence | [Analysis Outputs](analysis-outputs.md), [Generator DSL](generator-dsl.md) |
| 查 framework runtime table | [Runtime Database Schema](runtime-database-schema.md) |
| 排查文档或项目输入漂移 | [Common Mistakes](common-mistakes.md) |

## 回到叙事页

参考页主要给精确规则和查表信息。需要上下文时，回到这些叙事页：

- [Generation Tasks](../generator/generation-tasks.md)
- [Planning And Ownership Review](../generator/planning-and-ownership-review.md)
- [Analysis Evidence](../generator/analysis-evidence.md)
- [Generator Input Projection](../authoring/generator-input-projection.md)
- [Plan Review And Generation](../authoring/plan-review-and-generation.md)
- [Reference Content Studio](../examples/reference-content-studio.md)
- [Value Object And Type Inputs](../examples/value-object-and-type-inputs.md)
- [Generation And Analysis Evidence](../examples/generation-and-analysis-evidence.md)
