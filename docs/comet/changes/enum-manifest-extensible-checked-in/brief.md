# Outcome

让 Business Enum 真正成为可演进的领域类型：enum manifest 能声明除持久化 `value`、常量 `name` 和描述 `desc` 之外的类型化业务属性；首次生成的 enum class 落入 domain `src/main/kotlin` 并采用 checked-in ownership，项目作者可以继续增加领域逻辑，后续 generation 不覆盖。

# Scope

- 扩展 enum manifest definition，使用显式 property schema 描述额外业务字段，而不是从 item JSON 猜类型或把动态 `Map<String, Any?>` 泄漏进 canonical model。
- enum item 必须为 `fields` 中每个声明属性显式提供类型正确的值；即使属性 nullable，也必须显式写 `null`。未知字段、缺失字段、错误类型、重复/保留字段、重复持久化 value 和非整数 value 必须确定性失败。
- 扩展 SourceSnapshot、Canonical enum definition、catalog equality/ambiguity、planner context 与 Pebble enum template，使额外属性进入生成的 enum constructor 和常量参数。
- 将 manifest/local Business Enum Kotlin class 从 `GENERATED_SOURCE + OVERWRITE + build/generated` 改为 `CHECKED_IN_SOURCE + SKIP + src/main/kotlin`。
- 保持 enum FQN、稳定整数 `value`、`description` 与现有 nested JPA `Converter` API；本切片不为了 ownership 变化拆分 converter artifact。
- 更新 capability descriptor、plan/AgentFacts ownership、生成器/renderer/Gradle functional tests、Public Docs 与 authoring Skill ownership 说明。
- 使用 Payment reference 作为下游验证：至少一个真实业务 enum 声明并消费额外分组属性，且 enum 中包含 generation 后演进的领域逻辑；clean build 和重复 generation 都能证明 checked-in 语义。

# Non-goals

- 不提供任意 Kotlin expression、代码片段或模板内类型推断。
- 不把 enum 变成运行时可变配置、数据库字典或动态枚举。
- 不恢复 managed section、merge/patch 或 checked-in source 自动刷新。
- 不在本 change 删除 Pipeline `conflictPolicy`；ownership 与 effective conflict policy 的进一步简化由 GitHub Issue #213 跟踪。
- 不改变数据库已经持久化的 enum numeric value 或 enum FQN。

# Acceptance examples

- A1：带 `fields` 声明的 enum manifest 能生成额外 constructor property；每个 item 必须显式给出每个声明属性的类型正确值。Payment 的 `ChannelResultDisposition` 使用真实 `group` 属性，领域代码消费该属性而不是把它作为无用展示字段。
- A2：未声明的 item key、缺失必填值、错误 literal 类型、非法/重复/reserved property、重复 enum constant、重复 persisted `value`、小数或越界 value 均在 source/canonical 阶段给出包含 enum/item/property identity 的确定性诊断。
- A3：manifest enum 与 local enum 的 Kotlin artifact 均位于 domain `src/main/kotlin`，plan/AgentFacts 显示 `CHECKED_IN_SOURCE` 与 `SKIP`；clean 删除 build 目录后仍可编译。
- A4：第一次 materialization 后向 enum 添加实际领域方法；再次运行 plan/generate/generateSources 不覆盖该方法，enum 文件 hash 保持不变。
- A5：生成 enum 保持 `value: Int`、`description: String`、`valueOfOrNull` 和 nested `Converter`；现有 JPA `@Convert(converter = Enum.Converter::class)` 与整数 round-trip 不漂移。
- A6：没有 `fields` 的旧 manifest 保持源码兼容，生成的基础 enum API 不因新增扩展能力而改变。
- A7：canonical catalog 的 definition equality/ambiguity 同时比较 property schema 与各 item 的类型化值，不静默丢弃未知字段。
- A8：Generator descriptor、plan.json、AgentFacts、Public Docs、Skill 与真实 Payment Composite consumer 对 enum ownership 和能力描述一致。

# Constraints and invariants

- `name` 是 Kotlin enum constant identity，不自动成为 constructor property。
- `value` 是保留的唯一整数持久化 identity；numeric value 一经进入持久化合同必须保持稳定。
- `desc` 是 authoring key，canonical/生成 API 继续使用 `description`。
- property 顺序由 manifest `fields` 顺序决定，items 必须逐项显式提供所有属性值，不重新定义 schema；nullable 属性需要显式写 `null`，缺失值不隐式等于 `null`。
- Kotlin symbol identity/FQN resolution 和 literal compilation 在 Source/Canonical/Planner 完成，模板只消费已验证 context。
- checked-in source 首次 materialization 后由项目源码成为实际演进真相；manifest 不承诺对已有文件做同步更新。

# Decisions

- enum class 是领域业务类型，采用 checked-in ownership；用户可以增加方法、派生属性和其他领域逻辑。
- 保留 nested converter，避免无必要的 converter FQN 与 JPA projection 迁移。
- 额外字段必须有显式类型 schema；不采用未类型化 metadata map，也不从第一条 item 自动推断。
- `fields` 只定义属性名、类型与顺序；每个 item 必须显式提供所有属性值。清晰性优先于通过默认值减少重复，显式 `null` 只适用于 nullable 类型。
- 首版自定义属性支持可安全编译的 scalar literal（`String`、`Boolean`、`Byte`、`Short`、`Int`、`Long`、`Float`、`Double`、`BigInteger`、`BigDecimal`）以及对 canonical enum 常量的引用；暂不支持集合、`Map`、Value Object/任意对象构造和 raw Kotlin expression。
- Pipeline conflict policy 的整体简化不阻塞本 change，已记录为 #213。

# Open questions

- 无。用户于 2026-08-18 确认首版类型边界，并确认不提供字段默认值：每个 item 必须显式提供所有自定义属性值。

# Verification expectations

- Source provider、canonical catalog、planner、renderer、descriptor、AgentFacts 与 Gradle functional/compile tests 覆盖新字段和 checked-in ownership。
- 运行相关模块 focused tests、`./gradlew check`、capability facts export/validate、Skill/Runtime/PR/workflow guards。
- Payment reference 使用本机 Composite：clean build、12+ tests、plan/generation determinism、Analyzer、Mermaid parser smoke 和 Agent Snapshot 全部重建；旧候选 plan/hash/Verify pass 不复用。
