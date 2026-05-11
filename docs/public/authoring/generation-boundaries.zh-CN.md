# 生成 / 手写边界

## 何时阅读本页

- 当你已经知道默认流程，但需要判断“这段代码该交给生成器，还是该留给作者手写”时，读本页。
- 如果还没有先确认默认规则，请先回到 [Default Happy Path](default-happy-path.zh-CN.md)。

本页说明 cap4k 项目里哪些产物默认交给生成器，哪些责任必须回到手写主面。教学主场景仍然是 `Content`、`MediaProcessingTask` 与 `MediaProcessingCli` 组成的内容发布与处理项目。

完整端到端顺序见 [项目编写工作流](project-authoring-workflow.zh-CN.md)，DB / design / enum manifest / KSP / IR 输入细节见 [生成输入源](generator/input-sources.zh-CN.md)。

## 全局边界矩阵

| 产物类型 | 默认归属 | 说明 |
| --- | --- | --- |
| 生成的聚合骨架 | 生成主面 | 例如 `Content`、`MediaProcessingTask` 的结构骨架由生成器落出；作者在手写文件中补行为，不直接改写由生成器负责的文件 |
| aggregate `*Behavior.kt` | 手写补充点 | 这是当前明确留给作者补聚合行为的 checked-in scaffold，计划里固定使用 `ConflictPolicy.SKIP`，默认带出 `onCreate` / `onUpdate` / `onDelete` 生命周期行为扩展骨架 |
| aggregate `factory` / `specification` scaffold | 条件性手写补充点 | 这两类文件虽然是 `CHECKED_IN_SOURCE`，但是否可当作者维护骨架取决于 `templates.conflictPolicy`；`SKIP` 时可作为作者维护 scaffold，`OVERWRITE` / `FAIL` 时仍按计划产物对待 |
| 生成的命令 / 查询契约骨架 | 生成主面 | `CreateContentDraftCmd`、`GetContentDetailQry` 这类契约骨架可生成；手写逻辑放在 handler 或 adapter 侧 |
| query / client handler family | 条件性计划产物 | `*QryHandler.kt`、`*CliHandler.kt` 在当前默认 layout 下常落在 `adapter.application.*`；它们承担 application 级调度责任，但 ownership 仍要先看 `plan.json` |
| domain / integration subscriber family | 条件性计划产物 | `*DomainEventSubscriber.kt`、`*IntegrationEventSubscriber.kt` 既可能是作者维护推进点，也可能是 generator 写出的壳；需要先核对 recurring plan item |
| payload family | 条件性计划产物 | `adapter.portal.api.payload/**` 下的 request / response payload 常由 generator 产出；项目特有协议转换不要直接抢它们当长期手写家 |
| repository-side generated artifacts | 条件性计划产物 | `adapter.domain.repositories/*Repository.kt`、`*JpaRepositoryAdapter.kt` 一类当前可能由 generator 写出或持续接管；是否可直接编辑要以 `plan.json` 为准 |
| 应用层流程编排 | 手写主面 | 项目特有的送审、媒体处理推进、发布编排不能期待生成器自动替你完成 |
| 外部系统协议转换 | 手写主面 | `MediaProcessingCli`、回调 payload、轮询结果转换都属于适配器责任 |
| 模板覆盖 | `Advanced` | 允许，但会引入升级漂移与额外审计成本，只在默认模板确实不够时进入 |

## 如何识别生成主面与手写主面

- 先看 `build/cap4k/plan.json`：这里会列出本次 `cap4kPlan` 规划出的目标产物与落点路径。凡是被 `cap4kGenerate` 计划写入、重复生成后仍会被覆盖的目标文件，都应先视为生成主面。
- 再看模块里的实际目录：生成后的契约骨架通常会落在业务模块的 `src/main/kotlin/.../application/commands/...`、`src/main/kotlin/.../application/queries/...`，或聚合对应的目标目录中。路径本身不自动说明“能不能手改”，是否属于生成主面要以 `plan.json` 和重复生成行为为准。
- 再看 `conflictPolicy`：对 checked-in aggregate artifact，`behavior` 当前固定 `SKIP`，而 `factory` / `specification` 默认跟随 `templates.conflictPolicy`。这一步决定“虽然 checked in，但当前到底能不能按作者维护文件来用”。
- 再看分析输入目录：像 `domain/build/generated/ksp/main/resources/metadata/...` 这类路径是生成或分析出来的输入资料，不是作者手写主面，也不该被当成业务逻辑落点。
- 手写主面通常是作者自己维护、需要长期保留人工修改的文件，例如 `application` 下的处理器实现、`adapter/application/...`、`adapter/portal/...`、`adapter/integration/...` 中的边界转换文件。它们不依赖 `plan.json` 直接落产物，重新生成也不应该默默覆盖作者改动。
- 对当前默认 layout 再加一条现实判断：
  - `application/commands/**`、`application/queries/**` 更常出现 request-contract family；
  - `adapter.application.queries/**`、`adapter.application.distributed.clients/**` 更常出现 `*QryHandler.kt`、`*CliHandler.kt` 这类 handler family；
  - `adapter.domain.repositories/**` 更常出现 repository-side generated artifacts；
  - `adapter.portal.api.payload/**` 更常出现 payload family。
  这些路径都不能单靠目录推断为“稳定手写家”，仍要回到 `plan.json` 判断 ownership。
- 复制到 `src-generated/main/kotlin` 的内容只是审计快照，不是活跃生成输出；活跃生成源码仍在模块本地 `build/generated/cap4k/main/kotlin`。详见 [项目编写工作流](project-authoring-workflow.zh-CN.md#4-区分生成物骨架快照手写代码)。

## 当前 checked-in aggregate 文件合同

| 文件族 | 当前合同 |
| --- | --- |
| `aggregate/behavior.kt.peb` | 明确的作者维护补充点；固定 `ConflictPolicy.SKIP`，生成后可以在文件内补聚合行为，包括 `onCreate`、`onUpdate`、`onDelete` 生命周期扩展 |
| `aggregate/factory.kt.peb` | 可选构造骨架；只有在 `templates.conflictPolicy=SKIP` 时，才把它当作者维护 scaffold |
| `aggregate/specification.kt.peb` | 可选规格骨架；只有在 `templates.conflictPolicy=SKIP` 时，才把它当作者维护 scaffold |
- 如果 checked-in aggregate 文件是 `behavior`，可以把它当作者维护文件。
- 如果 checked-in aggregate 文件是 `factory` / `specification`，只有 `SKIP` 时才把它当作者维护 scaffold。
- 如果 checked-in aggregate 文件当前使用 `OVERWRITE` 或 `FAIL`，不要因为它已经 checked in 就把它当成普通手写家。

## 当前常见 family 的 ownership 判断

| 文件族 | 默认判断 |
| --- | --- |
| `application/commands/**/*.kt` 下的 `*Cmd.kt` | 默认先当 request-contract 计划产物，除非 `plan.json` 明确没有持续接管 |
| `application/queries/**/*.kt` 下的 `*Qry.kt` | 默认先当 request-contract 计划产物 |
| `adapter.application.queries/**/*.kt` 下的 `*QryHandler.kt` | 当前常见为 handler family 产物；逻辑属于 application 调度，但 ownership 仍先看 `plan.json` |
| `adapter.application.distributed/clients/**/*.kt` 下的 `*CliHandler.kt` | 当前常见为 client-handler family 产物；不要因为它在 adapter module 就自动当普通手写家 |
| `application/subscribers/**/*.kt` 下的 `*DomainEventSubscriber.kt` / `*IntegrationEventSubscriber.kt` | 可能是作者推进点，也可能是 generator 壳；先核对是否 recurring |
| `adapter.portal.api.payload/**/*.kt` | 常见为 payload family 产物；项目特有逻辑优先放在 controller / mapper / collaborator，而不是直接改 payload 骨架 |
| `adapter.domain.repositories/**/*.kt` 下的 `*Repository.kt` / `*JpaRepositoryAdapter.kt` | 先视为 repository family；是否可改必须看 `plan.json`，不能只因是 Spring Data 落点就当纯手写文件 |

如果某个 family 同时满足“位于 `src/main/kotlin`”和“出现在 `plan.json` recurring items”这两个条件，先把它当计划产物，而不是因为 checked in 就默认长期手写。

## 当前现实（Current Reality）

- 当前模板覆盖采用项目级 `overrideDirs`。
- 当前覆盖方式是 preset 路径替换，不是 artifact / family 粒度 override。
- 一旦覆盖，团队就要承担升级漂移、差异复核、审计补偿成本。
- 这代表“当前真实能力”，不是建议把 override 当成默认起点。

上面这些规则就是今天的判断合同。下面只记录可能的后续强化方向，不改变当前默认边界。

## 后续强化方向（Future Strengthening Directions）

- 更细粒度模板 override。
- artifact / family 级覆盖控制。
- 更强的生成 / 手写归属标记。
- override 漂移诊断。
- 升级期的覆盖差异审计支持。

这些方向只记录未来强化可能性，不表示当前版本已经具备这些能力。
