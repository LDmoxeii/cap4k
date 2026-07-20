# Generator Input Validation

`scripts/validate-cap4k-generator-inputs.py` 是 generation 前的离线预检查工具。它面向作者已经写好的 `design/design.json`、schema comment、enum manifest 和 value-object manifest，帮助尽早发现明显的输入问题。

这个脚本只给出静态、保守、可解释的反馈，不改写文件，也不把输入规范化。它不能替代 `cap4kPlan`、`cap4kGenerate` 或人工 plan review；它的价值是让输入错误在进入 generation planning 前先暴露出来。

## 校验范围

脚本可以检查：

- 通过 `sources.designJson.files` 注册的 design JSON 文件。
- SQL 文件中的 DB/schema DDL comments。
- 通过 `types.enumManifest.files` 配置的 enum manifests。
- 通过 `types.valueObjectManifest.files` 配置的 value-object manifests。
- 仓库内 live public docs 和 live skill references 是否仍引用已移除的 DB annotation 名称。

DB/schema comments 使用严格、大小写敏感的现行 allow-list。table comments 只接受 `@Parent=<table>` 和 `@Ignore`；column comments 只接受 `@ParentRef`、`@Type=<TypeName>`、`@RefAggregate=<AggregateName>`、`@RefId=<TypeName>`、`@IdStrategy=db_identity`、`@Managed=system|scope|deleted|version` 和 `@Inherited`。

脚本不会连接数据库、运行 Gradle、生成代码、编译、运行测试，也不会改写或整理输入文件。

## 使用方式

```powershell
python scripts/validate-cap4k-generator-inputs.py `
  --design design/design.json `
  --schema schema.sql `
  --enum design/enums.json `
  --value-object design/value-objects.json
```

需要机器可读输出时使用 `--json`。

## 问题级别

| Level | 含义 | 退出行为 |
| --- | --- | --- |
| `ERROR` | 输入规则不接受这个文件或字段。 | 只要出现 `ERROR`，脚本以非零状态退出。 |
| `WARN` | 静态证据可疑或不完整，需要作者复核。 | 单独出现时不让脚本失败。 |
| `RECOVERY_HINT` | 给相关错误补充修复方向，尤其是误把 analysis 或 drawing-board 片段作为普通输入时。 | 单独出现时不让脚本失败。 |
