# Generator Capability Audit

## 结论

Generator 已经具备一条可工作的核心闭环：显式输入进入 Canonical Model，planner 生成带 ownership 的 plan item，renderer 产出 checked-in skeleton、generated source 或 analysis artifact，并由 Gradle task 负责 materialization 与编译接线。

这条闭环覆盖当前上下文内最常用的战术建模载体：Aggregate、Strong ID、Repository、Factory、Behavior scaffold、Value Object、Enum、Command、Query、Capability、API Payload、Domain Event、Integration Event、Subscriber、Handler 与 Domain Service。现有证据不支持“Generator 缺少 DDD 核心战术建模骨架”这一结论。

初次审计时 Generator gate 不能通过，原因不是核心生成链不可用，而是存在当前契约漂移、一个会阻塞 HTTP adapter 下游验证的集成缺口，以及一个新确认的 Generator/Analyzer 共同闭环缺口：

1. Design JSON descriptor 与公共文档错误宣称 `Scheduled Reaction` 可由 generator 生成，实际没有对应 tag、canonical carrier、planner、template 或 runtime carrier。
2. 公共 `plan.json` 示例仍使用旧 generator id，并错误描述 `outputPath` shape。
3. Strong ID 本身可生成、编译、Jackson/JPA 可用，但 Spring MVC path/query 参数不能绑定为生成的 Strong ID。
4. 当前尚不能保证 `Design JSON == generated skeleton == Drawing Board`。现有所谓 round-trip 测试绕过真实 Analyzer；多个合法输入会在生成或恢复时丢失、变形或无法再次编译。

2026-08-02 mainline refresh：PR #154 已消除第 1、2 项 contract drift、删除 standalone validator 与 dead Specification helper；PR #155 已修复第 3 项 Strong ID MVC binding；PR #156 已完成第 4 项所依赖的 compile-time analysis metadata contract 与缺失 metadata fail-fast。三个 PR 均按 `#154 -> #155 -> #156` 顺序合并，每个后续分支都先更新到最新 `master` 并重新通过 required `check`；当前 `master` merge commit 为 `540fef09`。

因此 Generator gate 只剩第 4 项的 semantic round-trip repair 与真实七 tag 二次 generate/compile gate，尚不能提前标记为 `READY`。

其余开放事项主要属于诊断质量、可选 read-model provider、测试证据质量或陈旧 backlog，不应被误判为 DDD core 缺失。Drawing Board 闭环不再属于可接受的“人工 promotion 成本”，而是已确认的框架完整性 gate。

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
| `design-json` | application/domain design entries | verified；descriptor drift 已由 PR #154 修复 |
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
- Gate 影响：accepted repair required

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

已确认决策：删除虚假的生成承诺，把 Scheduled Reaction 保持为手写 application reaction/Job surface；本轮不新增 tag、canonical carrier、planner、template 或 runtime execution contract。未来只有在出现真实统一建模需求后，才单独设计一等 carrier。

修复验收应包含：

- 从 `DesignJsonSourceProvider.descriptor.tacticalCarriers` 删除 `Scheduled Reaction`；
- 修正所有“generator 提供 Scheduled Reaction skeleton”的公共文档；
- 保留 application layer 对手写 Job/reaction 这一概念位置的说明；
- 修正当前锁定错误 descriptor 的测试；
- 增加 descriptor、supported tag、canonical artifact 与 planner registration 的一致性证据，避免再次出现机器事实漂移；
- 不增加兼容 alias、空 planner 或 no-op runtime carrier。

### G-02 — Strong ID Spring MVC binding gap

- 分类：`partial` / cross-surface integration gap
- 主责：Generator type shape
- 验收责任：MVC adapter/starter
- Gate 影响：accepted repair required；不否定 Strong ID core

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

已确认决策：由 generator 为所有 Strong ID 生成一个 Spring 默认 conversion 可识别、但不依赖 Spring API 的 JVM static String factory，例如 `@JvmStatic fun from(value: String) = parse(value)`。Runtime 继续只拥有 `StrongIds` 校验，不增加反射型通用 converter。

修复验收应包含：

- UUIDv7 String/UUID 与 Snowflake String/Long 四种 backing 都暴露同一个 JVM static String conversion surface；
- factory 委托现有 `parse`/`StrongIds` 校验，不复制或放宽合法性规则；
- `DefaultConversionService.canConvert(String, StrongId)` 对四种形状均为 true；
- 真实 `@PathVariable` 与 `@RequestParam` 能绑定 Aggregate Root ID；
- 至少一个 `@RefId`/owned ID 生成类型共享相同 conversion contract；
- 非法输入仍被拒绝，并有可诊断的 MVC failure；
- Runtime/starter 不增加 classpath scanning、reflection converter 或 Strong ID type registry 的第二条转换路径。

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

- 分类：`partial` / accepted retirement
- 责任块：authoring diagnostics
- Gate 影响：accepted cleanup required

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

已确认决策：删除这套独立 Python validator，不再把它强化成第二个 authoring product。权威校验收敛到实际 source provider、canonical assembler、`cap4kPlan` diagnostics 与 Agent API evidence。Issue #113 不能按其旧的“大型静态 Skill 事实库 + 离线规则复制”方案继续执行。

清理验收应包含：

- 删除 `scripts/validate-cap4k-generator-inputs.py`；
- 删除 `docs/public/reference/generator-input-validation.md` 及所有导航、README、示例引用；
- 删除只服务该脚本的 fixture、命令说明或 CI wiring；
- 不保留 alias、deprecated wrapper 或 no-op command；
- 确认被脚本部分覆盖的真实规则在 source/canonical tests 中有当前 owner；
- Agent authoring route 继续以 snapshot、inputs、diagnostics、ownership 和显式 plan task 为入口。

### G-05 — Design JSON / generated skeleton / Drawing Board semantic round-trip is incomplete

- 分类：`missing-core` / cross-block drift
- 责任块：Generator faithful projection + Analyzer faithful recovery
- Gate 影响：blocking repair required

已确认的目标不是自动 Analyzer-to-Generator 回灌，而是三个表面表达同一个战术设计：

```text
Get(Put(design)) = Normalize(design)

Put(Get(generated skeleton))
    = same framework-owned structural/runtime contract
    + preserved handwritten behavior boundary
```

其中：

- `Put` 是 Design JSON 经 canonical model 与 Generator 产生可编译、可被 runtime 发现的骨架；
- `Get` 是 Analyzer 从该骨架恢复 Drawing Board；
- `Normalize` 只能处理无语义差异，例如文件分组、JSON formatting、默认 artifact 的省略与明确 type-expression canonicalization；
- Drawing Board 不会自动注册到 `sources.designJson.files`，采用它仍是显式的人/Agent 动作；
- 一旦某项设计已进入 Design JSON，框架不能再要求人补字段、类型、artifact、事件方向、runtime annotation 或其他框架所有的结构。

已确认采用 normalized tactical semantics，而不是 JSON 字面相等。允许归一化文件名称/数量/拆分、JSON formatting、file/entry order、artifact order、可选空数组、相同有效默认值的省略/显式写法，以及解析到相同 canonical FQN 的 type-expression spelling。不得归一化掉 field/resultField 与 nested DTO 顺序、resolved type identity、nullability、default semantics、artifact set/variant、event direction、persist/eventName 或 runtime annotation semantics。

当前方向正确的部分包括：

- Generator 通过 `@BuildingBlock` 携带 `tag/package/name/description/aggregates/eventName/family/variant`；
- Analyzer 以 `tag + package + name` 合并多 artifact，并从主 contract/event carrier 恢复 fields/resultFields；
- Drawing Board 输出使用正式 Design JSON 字段集；
- handler/subscriber 的依赖和方法体不被当作 design fields，业务行为仍可手写。

但当前不是语义闭环，已确认以下阻塞项：

1. **Page 基础设施字段泄漏。** Query/API Payload 的 `page` template 自动加入 `pageNum/pageSize`，Analyzer 却把它们当作用户 fields。回灌后 template 再次加入同名参数，可能直接生成不可编译代码：
   - `cap4k-plugin-pipeline-renderer-pebble/src/main/resources/presets/ddd-default/design/query.kt.peb:27-55`
   - `cap4k-plugin-pipeline-renderer-pebble/src/main/resources/presets/ddd-default/design/api_payload.kt.peb:26-54`
   - `cap4k-plugin-code-analysis-compiler/src/main/kotlin/com/only4/cap4k/plugin/codeanalysis/compiler/DesignElementCollector.kt:239-299`

   已确认修复边界：`pageNum/pageSize` 是由 `page` variant 隐含的 framework-owned PageRequest structure，不是 Design JSON 的普通 `fields`。Generator 继续自动投影，Analyzer 只有在确认当前 carrier 是 `page` variant 且属性满足 PageRequest contract 后才排除它们，不能全局按字段名过滤。`page` variant 显式声明同名 fields 必须 fail fast；非 `page` variant 可正常使用这些名称。当前 `1/10` 默认值属于 variant contract，未来若需要可配置分页策略，应增加明确配置而不是借用普通 fields。
2. **Type identity 丢失。** `IrTypeFormatter` 对普通 class-backed type 只输出 simple name，使 Strong ID、Value Object、enum、外部 FQN 变成 unknown 或 ambiguous short type。Analyzer 应恢复 resolved canonical FQN；只有 builtin 和当前 block nested type 可安全使用短名：
   - `cap4k-plugin-code-analysis-compiler/src/main/kotlin/com/only4/cap4k/plugin/codeanalysis/compiler/IrTypeFormatter.kt:24-53`
   - `cap4k-plugin-pipeline-core/src/main/kotlin/com/only4/cap4k/plugin/pipeline/core/SemanticValueCompiler.kt:287-319`

   已确认修复边界：Drawing Board 对 Design JSON builtin、标准 container 名称和当前 building block 自己声明的 nested DTO 使用规范短名；对 Strong ID、Value Object、enum、其他 project/context type 与 external type 输出 resolved canonical FQN。Container element/key/value type 递归应用同一规则。不得根据当前 type registry 或 classpath 恰好不存在歧义而输出 context-dependent short name。
3. **受支持 type algebra 不对称。** Design JSON/canonical 支持 `Array<T>`，Analyzer 明确拒绝 `kotlin.Array`；合法输入无法完成分析回环：
   - `docs/public/reference/design-json.md:45-53`
   - `cap4k-plugin-code-analysis-compiler/src/main/kotlin/com/only4/cap4k/plugin/codeanalysis/compiler/IrTypeFormatter.kt:13-22`

   已确认修复边界：`Array<T>` 保留为正式 Design JSON type algebra，Analyzer/Drawing Board 必须无损恢复并覆盖 recursive container nesting、element nullability、container nullability 与 `emptyArray()` normalized default。可靠事件 payload validator 继续递归检查 Array element type。本轮不顺带支持 `IntArray`、`ByteArray` 等 primitive array；它们需要未来独立证据和明确 contract。
4. **Event field default 丢失。** Source/canonical 接受 field `defaultValue`，但 Domain/Integration Event templates 不渲染默认值，第一轮生成已经丢失语义：
   - `cap4k-plugin-pipeline-source-design-json/src/main/kotlin/com/only4/cap4k/plugin/pipeline/source/designjson/DesignJsonSourceProvider.kt:289-315`
   - `cap4k-plugin-pipeline-renderer-pebble/src/main/resources/presets/ddd-default/design/domain_event.kt.peb:27-36`
   - `cap4k-plugin-pipeline-renderer-pebble/src/main/resources/presets/ddd-default/design/integration_event.kt.peb:29-42`

   已确认修复边界：Domain/Integration Event fields 继续共享 Design JSON 的 `defaultValue` contract。Generator 必须在 event payload 与 nested DTO constructor 渲染现有 stable default-expression subset，Analyzer 必须恢复同一 normalized default semantics。默认值是否符合领域事实由作者决定，framework 不得静默丢弃已接受的 default。
5. **Domain Service fields 被接受后静默丢弃。** Parser/canonical 接受并编译 `domain_service.fields`，Domain Service render model/template 和 Analyzer 却没有 field carrier。当前 schema 也没有 operation name、参数归属和返回值，不能诚实生成领域服务方法 contract：
   - `cap4k-plugin-pipeline-core/src/main/kotlin/com/only4/cap4k/plugin/pipeline/core/DefaultCanonicalAssembler.kt:727-784`
   - `cap4k-plugin-pipeline-generator-design/src/main/kotlin/com/only4/cap4k/plugin/pipeline/generator/design/DesignDomainServiceRenderModels.kt:5-26`
   - `cap4k-plugin-pipeline-renderer-pebble/src/main/resources/presets/ddd-default/design/domain_service.kt.peb:11-24`
   - `cap4k-plugin-code-analysis-compiler/src/main/kotlin/com/only4/cap4k/plugin/codeanalysis/compiler/DesignElementCollector.kt:116-125`

   已确认修复边界：`domain_service` 保持 metadata-only anchor，只允许 identity、package、description、aggregate ownership 与 `domain-service` artifact；非空 `fields/resultFields` 必须 fail fast。Generator 生成带 `@DomainService`/`@BuildingBlock` 的 class anchor，领域服务方法与算法继续手写，Analyzer 不从方法体推断 operation contract。未来若需要一等 operation model，必须另行设计包含方法 identity、参数、返回值和 multiplicity 的明确 schema，不能复用含义不清晰的 `fields`。
6. **Artifact contract 允许不可逆输入。** 当前只有全局 family/variant 校验，没有 tag-family 矩阵；`artifacts: []`、secondary-only handler/subscriber、错误 tag-family 组合都可能被接受。没有 primary field carrier 的 design block 无法从代码真相恢复，有些组合本身也无法独立编译：
   - `cap4k-plugin-pipeline-core/src/main/kotlin/com/only4/cap4k/plugin/pipeline/core/DefaultCanonicalAssembler.kt:820-887`
   - `cap4k-plugin-pipeline-generator-design/src/main/kotlin/com/only4/cap4k/plugin/pipeline/generator/design/DesignBlockSelection.kt:11-15`

   已确认修复边界：每个 accepted entry 必须选择非空、与 tag 匹配、包含 primary structural carrier 的 artifact set；query/capability handler、domain/integration subscriber 只能与对应 primary contract/event 同时存在，`integration-subscriber` 还必须配合 `integration-event:inbound`。显式 empty、cross-tag family 与 secondary-only selection 必须 fail fast；省略 `artifacts` 仍展开为当前默认集合，不新增 metadata-only generated carrier。
7. **Field order 被改变。** Drawing Board 对 fields/resultFields 排序，重新生成会改变 Kotlin constructor 参数顺序。Artifact set 可以排序，field/resultField 及 nested DTO 内部顺序必须保留：
   - `cap4k-plugin-pipeline-generator-drawing-board/src/main/kotlin/com/only4/cap4k/plugin/pipeline/generator/drawingboard/DrawingBoardArtifactPlanner.kt:93-105`
   - `DrawingBoardArtifactPlanner.kt:174-181`

   已确认修复边界：Drawing Board 可以规范化 artifact/file/entry order，但必须保留 `fields`、`resultFields` 和每个 nested DTO 内部的声明顺序。Constructor position 属于 normalized tactical semantics，不能为了输出稳定排序而改变。
8. **Domain Event runtime event name 未投影。** 非空 `eventName` 只进入 recovery metadata，template 仍只生成 `@DomainEvent(persist = ...)`，没有写入真正 runtime 使用的 `DomainEvent.value`：
   - `cap4k-plugin-pipeline-renderer-pebble/src/main/resources/presets/ddd-default/design/domain_event.kt.peb:11-25`
   - `ddd-core/src/main/kotlin/com/only4/cap4k/ddd/core/domain/event/annotation/DomainEvent.kt:11-21`
   - `ddd-domain-event-jpa/src/main/kotlin/com/only4/cap4k/ddd/domain/event/persistence/Event.kt:199-211`

   已确认修复边界：`persist: true` 的 Domain Event 必须声明 non-blank `eventName`；transient Domain Event 可省略。Generator 对 present name 生成 `@DomainEvent(value = ..., persist = ...)`，Analyzer 同时读取 authoring metadata 与 runtime annotation 并在冲突时 fail fast。Persisted event 不得产生空 runtime `eventType`。
9. **Domain Event field name 被错误当作类型边界。** Canonical 与 Analyzer 会按字段名过滤 `entity`，可能删除合法的 immutable payload field。可靠事件拒绝 Entity/Aggregate payload 的 runtime 历史事实边界仍应保留，但应由现有递归 semantic type validator 执行，不能用字段名替代类型检查：
   - `cap4k-plugin-pipeline-core/src/main/kotlin/com/only4/cap4k/plugin/pipeline/core/DefaultCanonicalAssembler.kt:733-738`
   - `cap4k-plugin-code-analysis-compiler/src/main/kotlin/com/only4/cap4k/plugin/codeanalysis/compiler/DesignElementCollector.kt:130-134`
   - `cap4k-plugin-pipeline-generator-design/src/main/kotlin/com/only4/cap4k/plugin/pipeline/generator/design/DomainEventPayloadModelValidator.kt:20-119`

   已确认修复边界：`entity` 不是保留字段名。Canonical 与 Analyzer 必须保留名为 `entity` 的合法 payload field，并只依据 resolved semantic type graph 判断载荷是否合法。真正的 Entity/Aggregate 类型及其递归容器位置继续由 payload validator 拒绝；不得削弱 PR #152 的 runtime reliable-event persistence boundary。
10. **Analyzer metadata annotations 污染了 DDD core 与生成源码表面。** `@BuildingBlock` 与 `@AggregateElement` 都是 BINARY-retained class annotations，当前没有 runtime consumer；前者携带无法从接口、class name 或 package 安全推断的 authoring identity/artifact metadata，后者为跨 module flow analysis 携带 aggregate/type/root identity：
    - `ddd-core/src/main/kotlin/com/only4/cap4k/ddd/core/annotation/BuildingBlock.kt:1-15`
    - `ddd-core/src/main/kotlin/com/only4/cap4k/ddd/core/annotation/AggregateElement.kt:1-12`
    - `cap4k-plugin-code-analysis-compiler/src/main/kotlin/com/only4/cap4k/plugin/codeanalysis/compiler/DesignElementCollector.kt:42-113`
    - `cap4k-plugin-code-analysis-compiler/src/main/kotlin/com/only4/cap4k/plugin/codeanalysis/compiler/Cap4kIrGenerationExtension.kt:190-243`

    仅靠 runtime interfaces/annotations、命名规则或 physical path 无法无损恢复 description、authoring-relative package、aggregate ownership、artifact family/variant，以及非 Entity aggregate artifact 的 type/root。完全移除显式 metadata carrier 会迫使 Analyzer 猜测，违反本次 round-trip contract。

    已确认修复边界：保留显式、BINARY-retained 的 lossless annotation metadata carrier，但将其从 `ddd-core` 迁出并重命名到专用 compile-time analysis-metadata contract/module；它没有 runtime 语义，业务项目只通过 compile-only dependency 引入。默认 generator templates 继续生成这些 metadata annotations，以支持 Drawing Board 与 flow recovery；不需要 Drawing Board 的项目可在自定义 templates 中删除它们，明确放弃相应分析能力。本轮不采用 sidecar skeleton index，不把 Analyzer 真相边界扩展为 code + sidecar。

    如果项目删除了当前任务所需的 analysis metadata，却仍调用 Drawing Board 或依赖该 metadata 的 flow analysis，请求必须 fail fast：diagnostic 应列出缺失 metadata 的 symbol、受影响的 analysis capability，以及通过恢复默认 template/annotation 重新启用能力的方法。不得静默忽略 unannotated element，也不得输出没有显式 completeness 标记、看起来可用于 round trip 的残缺 Drawing Board。

现有测试名为 `issue 92 metadata contract supports generation analysis and drawing board round trip`，但它不是真实回环：

- 生成骨架后调用 `writeIssue92AnalysisFixture(...)` 手工写 `design-elements.json`；
- 只覆盖 query、integration_event、domain_event；
- 回灌后只运行 `cap4kPlan`，没有再次 generate/compile；
- 不比较原始 Design JSON 与 Drawing Board 语义。

证据：

- `cap4k-plugin-pipeline-gradle/src/test/kotlin/com/only4/cap4k/plugin/pipeline/gradle/PipelinePluginFunctionalTest.kt:2481-2590`
- `PipelinePluginFunctionalTest.kt:3374-3428`

当前 fixture 的手写 analysis data 本身已相对原 Design JSON 丢失 query `status` 和 Domain Event `snapshot` subtree；测试仍通过，因为断言只检查几个文件名。因此它只能证明“手写 analysis fixture 能被 Drawing Board 与 parser 消费”，不能证明 Generator/Analyzer 等价。

修复验收必须使用真实链路：

1. 对七个 supported tags 和全部 artifact variants 读取原始 Design JSON，得到 canonical `C0`；
2. 运行真实 Generator 并编译所有生成 module，不允许人工补结构；
3. 在这些生成骨架上运行真实 compiler Analyzer，不能手写 `design-elements.json`；
4. 生成 Drawing Board，不编辑其内容，只显式注册为 ordinary Design JSON；
5. 得到 canonical `C1`，断言 `RoundTripProjection(C0) == RoundTripProjection(C1)`；
6. 从 `C1` 再次 plan/generate/compile，并比较两轮 artifact family、variant、结构和 runtime annotation 语义；
7. 覆盖 Strong ID、enum、Value Object、external FQN、List/Set/Map/Array、recursive nullability、default、nested DTO、page、event direction、persist/eventName、marker event 与跨 module artifact merge；
8. 增加负向证据：tag-family mismatch、empty artifacts、secondary-only artifact、metadata/runtime annotation 冲突和 incomplete analysis input 必须 fail fast；
9. 只修改 handler/subscriber/Domain Service 方法体、Repository call 或 injected dependency 时，Drawing Board 必须不变。

Generator 必须完整生成的是 framework-owned 结构与 runtime contract；Command/Query/Capability handler body、subscriber translation、Domain Service 算法、Repository strategy、事务/补偿等业务行为仍由人和 Agent 实现。`TODO` 或空 hook 不是缺陷，要求人补字段、类型、annotation、接口或 wiring 才能编译/运行才是缺陷。

不自动回灌仍是硬边界。尤其 producer outbound Integration Event 转成 consumer inbound event/subscriber 是新的上下文决策，不能由 framework 自动推断；但已经确认并生成的 direction 必须被 Analyzer 机械恢复，不能要求人再次判断。

### G-06 — Read-model weak-reference projection is an optional extension

- 分类：`provider/extension`，accepted boundary
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

已确认决策：这不影响 Aggregate、Query、Strong ID 或 DDD core 的充分性，不进入本轮 Generator gate。`aggregate-projection` 保持 opt-in、adapter-owned、scalar-only、无 read-model runtime；`@RefAggregate` 继续只表达目标 Aggregate 的 Strong ID，不自动创建 projection object relation。

边界验收应包含：

- 公共文档集中披露 opt-in、adapter-owned、generated-source 与 scalar-only；
- 明确 cap4k 不提供内置 read-model runtime；
- 明确 `@RefAggregate` 不产生 write-side relation、cascade、Repository navigation 或 projection object graph；
- template override 当前没有专用 weak-reference target metadata，不应靠短名猜测；
- Issue #118 若保留，只能作为未来可选 provider enhancement，不得阻塞四块下游验证。

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

## Implementation handoff

已确认将 Generator repair 拆为四个独立、短生命周期分支/PR；实现分支均从当时最新的 `origin/master` 开始，审计分支不承载生产代码修复：

| 顺序 | Branch | Scope | Merge/verification boundary |
| --- | --- | --- | --- |
| 1 | `fix/generator-contract-surface` | G-01 Scheduled Reaction overclaim、G-03 stale `plan.json` example、G-04 standalone Python validator retirement、G-08 dead Specification layout helper，以及 descriptor/Agent API/docs/tests 同步 | 可与 2、3 并行；不得恢复旧 generator surface 或 validator compatibility entry |
| 2 | `fix/strong-id-mvc-binding` | G-02：四种 Strong ID backing 的 JVM-static String factory 与真实 Spring MVC path/query binding evidence | 可与 1、3 并行；Runtime 不增加 reflection converter |
| 3 | `feature/analysis-metadata-contract` | 将 analyzer-only annotations 迁出 `ddd-core` 到专用 compile-time module/package，更新默认 templates、Analyzer/flow consumers、compile-only wiring，并实现缺失 metadata fail-fast | 可与 1、2 并行；是 4 的前置，默认模板保留 metadata，自定义模板可明确 opt out |
| 4 | `fix/design-roundtrip-contract` | G-05 全部 semantic repair、metadata/runtime conflict validation、七 tag 真实二次 generate/compile gate，以及为该 gate 提供稳定证据所需的 G-09 H2 TestKit isolation | 必须从 3 已合并后的最新 `master` 开始；不得手写 `design-elements.json` 代替真实 Analyzer |

G-06 保持 optional provider boundary；G-07 继续作为独立 P2 diagnostics backlog，不进入以上 blocking repair；G-09 仅在它影响真实 round-trip gate 的 fixture 范围内由第 4 个分支处理。

每个 PR 合并后，审计线应刷新 `origin/master`、复核对应 finding 和验收证据，再改变 Generator gate 结论。只有第 4 个 PR 合并并通过共同 gate 后，Generator 才能从 `NOT READY` 改为 `READY`，随后进入 Runtime capability audit。

### 第四分支可执行合同

`fix/design-roundtrip-contract` 是一个语义合同修复，不再拆成互相漂移的 Generator 与 Analyzer PR。建议按依赖顺序实现，但最终必须作为同一个当前合同验收：

1. **输入与 canonical。** 补齐 tag-artifact matrix、primary carrier、Domain Service、page collision、persisted Domain Event name 与 `entity` semantic-type validation；accepted Design JSON 必须先形成唯一、可逆的 canonical 语义。
2. **Generator projection。** 补齐 event defaults、Domain Event runtime name、recursive type/default projection 与所有 framework-owned compile/runtime structure；不得要求人工补字段、annotation、interface 或 wiring 才能编译。
3. **Analyzer recovery。** 恢复 canonical FQN、`Array<T>`、recursive nullability/default、field/nested order、page-derived structure、event direction/name/persist，并校验 authoring metadata 与 runtime annotations 冲突。
4. **Drawing Board。** 输出可直接作为普通 Design JSON 显式注册的兼容结构；保持 semantic order，允许 artifact/file/entry 物理排序。Completeness 必须逐 configured analysis input directory 判断，不能让一个完整 module 掩盖另一个缺失 metadata 的 module 后输出残缺 board。
5. **真实 gate。** 新增独立 `DesignRoundTripFunctionalTest` 与一套丰富 fixture，使用两个干净临时工程。Project A 执行 generate -> compile -> real compiler Analyzer -> Drawing Board；Project B 禁用原始 Design JSON，只显式输入 Project A 的 Drawing Board，再执行 canonicalize -> generate -> compile。断言 `RoundTripProjection(C0) == RoundTripProjection(C1)`，并比较两代 framework-owned skeleton 与 runtime annotation semantics。

当前 Issue #92 测试中的 `writeIssue92AnalysisFixture(...)` 不能改造成验收捷径，应删除这条手写 analysis JSON 的假 round-trip 路径。普通 fixture 的 `compileKotlin` 也不等于 Analyzer 已安装；真实 gate 必须显式运行 `Cap4kCodeAnalysisCompilerRegistrar`，并按 domain -> application -> adapter 的实际 classpath 顺序收集各 module analysis output 后再合并。

快速契约测试分别归属 source/core、design generator、compiler Analyzer、IR analysis source 与 Drawing Board generator；昂贵的跨模块链路只保留一个真实 gate。正向 fixture 覆盖七个 tag、page 与 primary/secondary variants、Strong ID/enum/Value Object/external FQN、List/Set/Map/Array、recursive nullability/default、nested DTO、合法 `entity` 字段、persisted/transient/marker events。负向测试覆盖 empty/cross-tag/secondary-only artifacts、page 显式保留字段、Domain Service payload、persisted event 缺 name、metadata/runtime conflict、真实 Entity payload与 incomplete analysis input。

G-09 只为该 gate 做 TestKit isolation：使用临时工程名唯一的 H2 in-memory URL，移除 `DB_CLOSE_DELAY=-1`，必要时同步修正已被相同 gate 复用且已观察到锁问题的 integrated fixture；不修改 `DbSchemaSourceProvider` 的生产 connection lifecycle，也不把所有历史 H2 fixture 的机械清理扩进本分支。

Issue #102 在本分支中的完成含义是：Drawing Board 结构与 Design JSON input 直接兼容，并有显式 import 的真实证据。把 producer 的 outbound contract 复制到 consumer 并改成 inbound 仍是人/Agent 的上下文决策；不新增自动回灌、自动注册、自动 event direction 翻转或专用 recovery subsystem。

明确非目标：不恢复 sidecar，不推断手写业务方法体，不恢复缺失 metadata 的静默降级，不支持 primitive arrays，不弱化 PR #152 的 reliable-event Entity payload 边界，不修改生产 JDBC lifecycle，也不提前安装或自动接线 Analyzer 产品能力。

Mainline merge evidence：

- PR #154 `fix/generator-contract-surface` -> merge commit `d310f3fa`；required `check` passed。
- PR #155 `fix/strong-id-mvc-binding` -> 先更新到 `d310f3fa`，required `check` passed，merge commit `9e0e0bcd`。
- PR #156 `feature/analysis-metadata-contract` -> 先更新到 `9e0e0bcd`，组合 required `check` passed，merge commit `540fef09`。
- 当前审计线已合入 `origin/master@540fef09`；本地主工作区的用户未提交改动未被触碰。
- `fix/design-roundtrip-contract` 现在可以从 `origin/master@540fef09` 创建，不再依赖未合并前置。


## Backlog reconciliation

| Issue | 当前判断 |
| --- | --- |
| #75 | 有效的 P2 diagnostics partial，不阻塞核心 gate |
| #76 | 有效的 P1 HTTP adapter integration gap；应修 |
| #102 | 纳入第四分支：以 Drawing Board 直接 Design-JSON-compatible + 显式 import 完成；不建立自动 recovery subsystem，不自动翻转 inbound/outbound |
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

针对 G-05 另行执行：

```text
:cap4k-plugin-pipeline-gradle:test
  --tests "com.only4.cap4k.plugin.pipeline.gradle.PipelinePluginFunctionalTest.issue 92 metadata contract supports generation analysis and drawing board round trip"

:cap4k-plugin-code-analysis-compiler:test
  --tests "com.only4.cap4k.plugin.codeanalysis.compiler.DesignElementExtractionTest"
  --tests "com.only4.cap4k.plugin.codeanalysis.compiler.AnalysisOutputCorrectnessTest"

:cap4k-plugin-pipeline-gradle:test
  --tests "com.only4.cap4k.plugin.pipeline.gradle.PipelinePluginCompileFunctionalTest.integrated compile sample keeps migrated design families compile-safe together"

:cap4k-plugin-pipeline-core:test
  --tests "com.only4.cap4k.plugin.pipeline.core.DefaultCanonicalAssemblerTest"
:cap4k-plugin-pipeline-generator-drawing-board:test
```

结果均为 `BUILD SUCCESSFUL`。这些结果分别证明当前七类 integrated skeleton 可编译、Analyzer component extraction 可工作、Drawing Board component tests 可工作，以及现有名义 round-trip 测试按其当前断言通过；它们不构成真实端到端语义等价证据。对该测试使用的原 Design JSON 与手写 `design-elements.json` 做 normalized comparison 后，query 的 `status` 和 Domain Event 的 `snapshot`/`snapshot.traceId` 已在手写中间数据丢失，而测试仍通过，进一步证明当前断言存在假阳性。

## Generator gate

当前状态：`NOT READY`（仅剩 semantic round-trip blocking repair）

解除条件：

1. [x] 按已确认决策消除 Scheduled Reaction descriptor/docs 与实际 generator/runtime contract 的漂移：PR #154。
2. [x] 修正 `plan.json` 当前公开 contract example：PR #154。
3. [x] 按已确认决策修复 Strong ID MVC binding，并通过真实 HTTP adapter binding evidence：PR #155。
4. [x] 按已确认决策删除独立 Python input validator 及其公共 contract surface：PR #154。
5. [ ] 修复 G-05 的真实语义 round-trip 缺口，并以七 tag、真实 Analyzer、二次 generate/compile 的端到端证据通过共同 gate。
6. [x] G-06/G-07 已记录为 accepted optional/P2 boundaries，G-08 已由 PR #154 清理；G-09 只在阻塞真实 gate 的 fixture 范围内归入第 5 项。

Generator 修复不得弱化 PR #152 已确认的 runtime reliable-event payload boundary。生成的 persisted Domain Event 必须继续满足 runtime 对历史事实 payload 的拒绝实体规则。
