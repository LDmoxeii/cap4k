# Generator Capability Audit

## 结论

Generator 已经具备一条可工作的核心闭环：显式输入进入 Canonical Model，planner 生成带 ownership 的 plan item，renderer 产出 checked-in skeleton、generated source 或 analysis artifact，并由 Gradle task 负责 materialization 与编译接线。

这条闭环覆盖当前上下文内最常用的战术建模载体：Aggregate、Strong ID、Repository、Factory、Behavior scaffold、Value Object、Enum、Command、Query、Capability、API Payload、Domain Event、Integration Event、Subscriber、Handler 与 Domain Service。现有证据不支持“Generator 缺少 DDD 核心战术建模骨架”这一结论。

Generator gate 当前仍不能通过，原因不是核心生成链不可用，而是存在两个当前契约漂移，以及一个会阻塞 HTTP adapter 下游验证的集成缺口：

1. Design JSON descriptor 与公共文档错误宣称 `Scheduled Reaction` 可由 generator 生成，实际没有对应 tag、canonical carrier、planner、template 或 runtime carrier。
2. 公共 `plan.json` 示例仍使用旧 generator id，并错误描述 `outputPath` shape。
3. Strong ID 本身可生成、编译、Jackson/JPA 可用，但 Spring MVC path/query 参数不能绑定为生成的 Strong ID。

其余开放事项主要属于诊断质量、可选 read-model provider、显式 analysis promotion 或陈旧 backlog，不应被误判为 DDD core 缺失。

## 审计边界

Generator 的责任是把已经确认的、显式的上下文内输入可靠投影为可审查的计划与工程结构。它不负责：

- 判断 Bounded Context 是否正确；
- 证明 Aggregate 边界或业务规则正确；
- 从任意代码自动恢复业务设计；
- 自动选择跨上下文 Integration Event 的 consumer 语义；
- 为所有现代 CQRS/read-model 技术提供内置 runtime；
- 模拟没有公开 runtime contract 的战术载体。

## 当前能力面

### Sources

| Source id | 输入责任 | 当前状态 |
| --- | --- | --- |
| `db` | DB/schema snapshot 与受支持 annotation | verified |
| `design-json` | application/domain design entries | verified，存在 descriptor drift |
| `enum-manifest` | shared/local enum authoring input | verified |
| `value-object-manifest` | value object authoring input | verified |
| `ir-analysis` | analysis lane 的结构观察输入 | verified；不属于 ordinary source generation |

### Authoring generators

当前注册的 authoring generator ids 为：

- `command`
- `query`
- `query-handler`
- `capability`
- `capability-handler`
- `api-payload`
- `domain-event`
- `domain-subscriber`
- `domain-service`
- `integration-event`
- `integration-subscriber`
- `types-value-object`
- `enum`
- `aggregate`
- `aggregate-projection`

Analysis generators 为 `flow` 与 `drawing-board`，由独立 analysis tasks 执行。

Design JSON 当前接受的 normal tags 只有：

- `command`
- `query`
- `capability`
- `api_payload`
- `domain_event`
- `integration_event`
- `domain_service`

`scheduled reaction/job`、`saga`、generic `validator`、`value_object`、`client` 与 generic `specification` 都不是当前 normal Design JSON carrier。

### Ownership contract

`ArtifactPlanItem` 暴露：

- `generatorId`
- `moduleRole`
- `templateId`
- `outputPath`
- `context`
- `conflictPolicy`
- `outputKind`
- `resolvedOutputRoot`

`DefaultPipelineRunner` 保持以下边界：

- 未配置的 source/generator id fail fast；
- `CHECKED_IN_SOURCE` 固定使用 `SKIP`，不允许 template override 覆盖手写内容；
- `GENERATED_SOURCE` 继续遵循明确的 conflict policy，并由 build-owned root 管理；
- 内置 flow/drawing-board observation output 固定覆盖；
- source generation 与 analysis generation 使用不同 task lane。

Agent Snapshot 的 ownership section 有意只投影审查所需的稳定 ownership 字段，不复制可能很大的 generator-specific `context`。完整 `plan.json` 仍保留 `context` 供深入排查。

## Findings

### G-01 — Scheduled Reaction capability overclaim

- 分类：`drift`
- 责任块：Generator contract；同时影响 Skill/Agent API 与公共文档
- Gate 影响：blocking

`DesignJsonSourceProvider.descriptor.tacticalCarriers` 声称支持 `Scheduled Reaction`：

- `cap4k-plugin-pipeline-source-design-json/src/main/kotlin/com/only4/cap4k/plugin/pipeline/source/designjson/DesignJsonSourceProvider.kt:31-40`

但同一 provider 的 `supportedTags` 只有七个 normal tags：

- `DesignJsonSourceProvider.kt:57-65`

Canonical assembler 的 supported tag/default artifact contract 也只有同样七类：

- `cap4k-plugin-pipeline-core/src/main/kotlin/com/only4/cap4k/plugin/pipeline/core/DefaultCanonicalAssembler.kt:889-898`
- `DefaultCanonicalAssembler.kt:1879-1887`

仓库没有 Scheduled Reaction/Job generator。Runtime 中的 `@Scheduled` 只用于 reliable provider 内部维护任务，不构成公开战术 carrier。

公共文档继续把它描述为 generated skeleton：

- `docs/public/architecture/application-layer.md:19`
- `docs/public/concepts/execution-and-ownership/generated-skeleton-and-handwritten-logic.md:7`
- `docs/public/examples/run-the-reference-project.md:10`

薄型 Skill 的表述反而更接近事实：Scheduled Reaction 是 application implementation surface；当 catalog 没有一等 carrier 时，durable progress 需要显式 provider：

- `skills/cap4k-authoring/references/tactical-carriers.md:11`

由于 Agent API 会投影 provider descriptor，Agent 当前会收到错误的机器可读能力事实。现有 descriptor test 还把错误声明锁成了预期值，因此单纯“测试通过”不能证明 descriptor 与 parser/planner 一致。

推荐方向：删除虚假的生成承诺，把 Scheduled Reaction 保持为手写 application reaction/Job surface；只有未来确认需要统一输入、代码形状和 runtime contract 时，才新增一等 carrier。修复时应加入跨 descriptor、tag、canonical artifact、planner registration 的一致性测试，而不是继续维护孤立字符串断言。

### G-02 — Strong ID Spring MVC binding gap

- 分类：`partial` / cross-surface integration gap
- 主责：Generator type shape
- 验收责任：MVC adapter/starter
- Gate 影响：不否定 Strong ID core；阻塞 HTTP adapter 下游验证

Strong ID 当前已支持：

- UUIDv7 String/UUID backing；
- Snowflake String/Long backing；
- runtime 统一语义校验；
- Jackson scalar string serialization/deserialization；
- JPA mapping；
- Aggregate Root、owned child 与 `@RefId` 共用同一 artifact planner；
- 四种生成形状编译通过。

但 template 中 backing constructor 为 private，`of(...)` 与 `parse(String)` 只是未加 `@JvmStatic` 的 companion function，唯一 JVM static factory 是 `fromJson(JsonNode)`：

- `cap4k-plugin-pipeline-renderer-pebble/src/main/resources/presets/ddd-default/aggregate/strong_id.kt.peb:42-77`

Spring 默认 String conversion 无法识别这些生成类型。仓库也没有 Strong ID `ConverterFactory`、`WebMvcConfigurer` 或 formatter registration。实际 `DefaultConversionService.canConvert(String, StrongId)` 对四种 backing 都为 false。

推荐方向：由 generator 为所有 Strong ID 生成一个 Spring 默认 conversion 可识别、但不依赖 Spring API 的 JVM static String factory，例如 `@JvmStatic fun from(value: String) = parse(value)`。Runtime 继续只拥有 `StrongIds` 校验，不增加反射型通用 converter。验收应包含真实 `@PathVariable`、`@RequestParam` 和异常输入测试。

对应 backlog：Issue #76。

### G-03 — Public plan contract example is stale

- 分类：`drift`
- 责任块：Generator documentation
- Gate 影响：blocking documentation drift

`docs/public/reference/plan-json.md:32` 的最小示例使用：

```json
"generatorId": "design-command"
```

实际 generator id 是 `command`：

- `cap4k-plugin-pipeline-generator-design/src/main/kotlin/com/only4/cap4k/plugin/pipeline/generator/design/DesignCommandArtifactPlanner.kt:6`

该文档还把 `outputPath` 描述为 `resolvedOutputRoot` 下的相对路径。实际 planner 通过 `ArtifactLayoutResolver.kotlinSourcePath(...)` 直接生成 repo-relative 完整路径；generated-source rebasing 同样把 `outputPath` 重写为包含 resolved generated root 的完整 repo-relative 路径：

- `DesignCommandArtifactPlanner.kt:21-33`
- `cap4k-plugin-pipeline-gradle/src/main/kotlin/com/only4/cap4k/plugin/pipeline/gradle/PipelinePlugin.kt:752-777`
- `cap4k-plugin-pipeline-gradle/src/test/kotlin/com/only4/cap4k/plugin/pipeline/gradle/PipelinePluginFunctionalTest.kt:895-990`

修复应直接更新当前 contract example，不保留旧 id alias。

### G-04 — Offline input validator is useful but non-authoritative

- 分类：`partial`
- 责任块：authoring diagnostics
- Gate 影响：non-blocking

`scripts/validate-cap4k-generator-inputs.py` 能提前发现一部分明显输入错误，且文档已明确它不能替代 `cap4kPlan`。当前脚本执行通过。

它与 authoritative parser/assembler 仍存在覆盖差异：

- unknown design entry field 只产生 WARN，而 runtime parser 会忽略未移除字段；
- 没有检查 `domain_event` 必须恰好声明一个 aggregate；
- 没有完整复现 artifact family/variant 规则；
- integration event 与 domain event 的部分 canonical constraint 只在 assembler 中执行；
- 没有独立测试套件证明脚本与当前 source/canonical contract 同步。

权威校验仍是 source parse、canonical assembly、`cap4kPlan` diagnostics 与 Agent API evidence：

- `DefaultCanonicalAssembler.kt:799-884`
- `DefaultCanonicalAssembler.kt:1154-1159`
- `docs/public/reference/generator-input-validation.md:3-7`

这不是当前 core 缺口。后续应明确二选一：若保留离线 validator，则只承诺稳定、低成本的 preflight subset 并为该 subset 加测试；若实际 authoring 全部走 Gradle/Agent API，则可以删除重复 contract，避免形成第二套易漂移 parser。Issue #113 不能按其旧的“大型静态 Skill 事实库”方案继续执行。

### G-05 — Drawing-board recovery issue is substantially resolved

- 分类：`verified` explicit promotion path；旧诉求为 `stale`
- 责任块：Generator/Analyzer boundary
- Gate 影响：non-blocking

Issue #102 报告的 `command.resultFields` 不兼容已被当前 parser 修复：

- `DesignJsonSourceProvider.kt:57-68`
- `DesignJsonSourceProvider.kt:216-233`
- `cap4k-plugin-pipeline-source-design-json/src/test/kotlin/com/only4/cap4k/plugin/pipeline/source/designjson/DesignJsonSourceProviderTest.kt:93-138`

当前已有真实回环功能测试：analysis 生成 drawing-board fragment；测试将 query、inbound integration event、domain event fragment 显式注册为 `sources.designJson.files`；再次执行 `cap4kPlan` 后出现对应 ordinary generation items：

- `cap4k-plugin-pipeline-gradle/src/test/kotlin/com/only4/cap4k/plugin/pipeline/gradle/PipelinePluginFunctionalTest.kt:2481-2590`

正确责任边界是：

```text
raw analyzer observation
        -> human/Agent review and normalization
promoted Design JSON
        -> explicit sources.designJson.files registration
cap4kPlan / cap4kGenerate
```

不应新增自动 analyzer-to-generator 回灌。尤其 producer outbound Integration Event 转成 consumer inbound event/subscriber 是新的上下文决策，不能由 framework 自动推断。

剩余 partial 只有：outbound-to-inbound promotion 示例不足，recovery fixture 只证明到 plan 而非重新 generate/compile，以及 Skill 的绝对措辞需要区分 raw observation 与 reviewed/promoted input。

### G-06 — Read-model weak-reference projection is an optional extension

- 分类：`provider/extension`，当前基础 projection 为 `partial`
- 责任块：optional Generator provider
- Gate 影响：non-blocking

现有 `aggregate-projection` 是 opt-in、adapter-owned、generated-source 的基础 projection generator：

- `cap4k-plugin-pipeline-generator-aggregate/src/main/kotlin/com/only4/cap4k/plugin/pipeline/generator/aggregate/AggregateProjectionArtifactPlanner.kt:20-43`
- `cap4k-plugin-pipeline-gradle/src/test/kotlin/com/only4/cap4k/plugin/pipeline/gradle/PipelinePluginFunctionalTest.kt:1225-1262`

默认 template 只生成 scalar fields，不生成关系对象图：

- `cap4k-plugin-pipeline-renderer-pebble/src/main/resources/presets/ddd-default/aggregate_projection/entity.kt.peb:47-72`

真正未实现的是 `@RefAggregate` 到 read-side weak-reference metadata 的投影。Canonical Model 没有独立 `weakReferences`；`@RefAggregate` 当前只把字段解析为目标 Strong ID；projection planner 的 relation context 只来自 owned aggregate relation：

- `cap4k-plugin-pipeline-api/src/main/kotlin/com/only4/cap4k/plugin/pipeline/api/PipelineModels.kt:505-527`
- `DefaultCanonicalAssembler.kt:516-543`
- `AggregateProjectionArtifactPlanner.kt:58-62`
- `AggregateProjectionArtifactPlanner.kt:107-139`

这不影响 Aggregate、Query、Strong ID 或 DDD core 的充分性。若保留 Issue #118，应把它重写为可选 projection provider enhancement，而不是 core blocker。公共文档应集中披露 scalar-only、无 read-model runtime、`@RefAggregate` 只保留标量 Strong ID 的当前边界。

### G-07 — Strong ID conflict diagnostics lack column context

- 分类：`partial`
- 责任块：Generator diagnostics
- Gate 影响：non-blocking

Issue #75 记录的 annotation conflict 语义本身已正确 fail fast，但错误消息没有稳定携带 table/column context。它影响排错成本，不影响 canonical correctness 或生成结果。

### G-08 — Generic Specification layout helper is dead residue

- 分类：`drift` / cleanup residue
- 责任块：Pipeline API cleanup
- Gate 影响：non-blocking

当前 aggregate generator 不生成 generic Specification，薄型 Skill 也明确不应假定该 surface 存在。生产代码仍保留未被 planner 使用的：

- `cap4k-plugin-pipeline-api/src/main/kotlin/com/only4/cap4k/plugin/pipeline/api/ArtifactLayoutResolver.kt:34-35`
- `cap4k-plugin-pipeline-api/src/test/kotlin/com/only4/cap4k/plugin/pipeline/api/ArtifactLayoutResolverTest.kt:27-28`

在允许 breaking iteration 的前提下，应删除这种已无当前 owner 的 helper/test，而不是为它恢复 generator。

### G-09 — File-backed H2 TestKit fixtures are concurrency-fragile

- 分类：`partial` / test-evidence quality
- 责任块：Generator functional verification fixtures
- Gate 影响：不表示生产 generator 失败；会降低并行审计和 CI 证据稳定性

多个 Gradle TestKit fixture 使用 file-backed H2，并配置 `DB_CLOSE_DELAY=-1`。该设置会让 H2 在 Gradle daemon JVM 存活期间继续持有数据库文件锁。当多个 TestKit/Gradle invocation 并行或使用不同 daemon 访问同一临时 fixture 时，后续 `cap4kGenerate` 可能得到：

```text
JdbcSQLNonTransientConnectionException: Database may be already in use
```

代表性 fixture：

- `cap4k-plugin-pipeline-gradle/src/test/resources/functional/design-integrated-compile-sample/build.gradle.kts:5-20`
- 同类 file-backed URL 还存在于 aggregate、relation、enum、persistence、provider 与 domain-event fixtures。

`DbSchemaSourceProvider` 本身通过 `use` 正确关闭 JDBC connection：

- `cap4k-plugin-pipeline-source-db/src/main/kotlin/com/only4/cap4k/plugin/pipeline/source/db/DbSchemaSourceProvider.kt:58-96`

本轮集成编译测试在并行 TestKit 活动期间因文件锁失败；停止 Gradle daemons 并单独重跑同一测试后在 1 分钟内通过。因此当前证据指向 fixture URL/lifecycle 隔离问题，而不是 generator 行为回归。

建议将 file-backed functional fixture 改为不会跨 build invocation 长期持锁的 URL/lifecycle，并新增可重复执行证据。不要把该环境性失败列为已知可忽略债务；它应有明确 owner，因为它会掩盖真实 generator regression。

## Backlog reconciliation

| Issue | 当前判断 |
| --- | --- |
| #75 | 有效的 P2 diagnostics partial，不阻塞核心 gate |
| #76 | 有效的 P1 HTTP adapter integration gap；应修 |
| #102 | substantially resolved/stale；不应建立自动 recovery subsystem |
| #113 | 方案已被 PR #153 的 thin Skill + Agent API 责任重置取代；仅少量底层事实需要重新归属 |
| #118 | optional projection provider investigation；不属于 missing-core |

## Verification evidence

本轮已执行：

```text
python scripts/validate-cap4k-generator-inputs.py
```

结果：`OK: no issues found.`

本轮已执行 Generator 核心模块测试：

```text
:cap4k-plugin-pipeline-api:test
:cap4k-plugin-pipeline-source-design-json:test
:cap4k-plugin-pipeline-source-enum-manifest:test
:cap4k-plugin-pipeline-source-value-object-manifest:test
:cap4k-plugin-pipeline-core:test
:cap4k-plugin-pipeline-generator-design:test
:cap4k-plugin-pipeline-generator-aggregate:test
:cap4k-plugin-pipeline-generator-types:test
:cap4k-plugin-pipeline-renderer-pebble:test
```

结果：`BUILD SUCCESSFUL`。

另行执行的聚焦 Gradle TestKit 验证覆盖：

- pretty-printed source plan；
- aggregate projection generated source；
- Domain Event domain/application compile；
- integrated design-family compile；
- manifest-first Agent Snapshot dry run。

结果：四项在组合运行中通过；integrated design-family compile 首次因并行 TestKit 的 file-backed H2 lock 失败。执行 `gradlew --stop` 后单独重跑同一测试，结果为 `BUILD SUCCESSFUL in 1m`。因此五项功能证据最终均通过，同时记录 G-09 的 fixture 并发脆弱性，不把首次失败隐藏为普通噪音。

## Generator gate

当前状态：`NOT READY`

解除条件：

1. 决定并消除 Scheduled Reaction descriptor/docs 与实际 generator/runtime contract 的漂移。
2. 修正 `plan.json` 当前公开 contract example。
3. 决定是否在 Generator gate 内修复 Strong ID MVC binding；若暂缓，必须明确 downstream validation 不覆盖 HTTP typed-id binding，否则下游 gate 仍会失败。
4. 将 G-04、G-05、G-06、G-07、G-08、G-09 记录为已接受的 partial/provider/cleanup 边界，或分别创建后续实现决策；它们本身不要求恢复旧 generator surface。

Generator 修复不得弱化 PR #152 已确认的 runtime reliable-event payload boundary。生成的 persisted Domain Event 必须继续满足 runtime 对历史事实 payload 的拒绝实体规则。
