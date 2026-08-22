# Outcome

从最新 `origin/master` 重新交付已完成并验证过的 Gradle 9.6.1 Wrapper 升级：将旧归档 change 的实现差异安全重放到新的独立 worktree，在已经支持 zero-spec Native PR finish 的主线基础上重新 Build、Verify、Archive，并通过仓库自有 provider 创建面向 `master` 的 PR。

# Scope

- 更新 `gradle/wrapper/gradle-wrapper.properties` 到 `gradle-9.6.1-bin.zip`，完整刷新 `gradle-wrapper.jar`、`gradlew` 与 `gradlew.bat`。
- 保留 Wrapper URL 校验，并接受 Gradle 9.6.1 官方生成的 `retries=0` 与 `retryBackOffMs=500`。
- 重放旧实现提交 `94dc2ccd1febee3ef64902b7c8c9e8272f976b52` 中与 Gradle 9.6.1 兼容相关的生产代码、测试和 fixture 差异；不复制旧 change 的 `comet-state.yaml`、`verification.md`、Archive 提交或本机 Runtime overlay。
- 为六个 pipeline Gradle `DefaultTask` 类型保留明确的 `@DisableCachingByDefault` 合同；`Cap4kGenerateSourcesTask` 不声明 `@CacheableTask`，因为 stale-output 清理依赖本机 managed-root 历史。
- 保留 Gradle 9.6.1 对 dependencyInsight 输出、TestKit fixture 目录存在性等变化所需的最小测试兼容修复。
- 保持 Kotlin 2.2.20、Spring Boot 3.5.6、JDK 17、Foojay 0.8.0、Maven 坐标和发布流程不变。
- 重新运行 buildSrc、全仓 `check`、capability contract、Skill、Runtime、PR/workflow 治理验证、Wrapper 稳定性检查和下游 payment Composite Build smoke。
- Verify 通过后使用 `finish=pull-request`，让当前 `master` 上修复后的 repository-owned provider 处理 exact zero-spec PR authoring 与 create/reuse。

# Non-goals

- 不发布 cap4k 新版本，不创建 tag，不改变 Maven coordinates。
- 不主动升级 Kotlin、Spring Boot、JDK、Foojay 或业务依赖。
- 不改变 Runtime、Generator、Analyzer、AgentFacts、Public Docs 或 authoring Skill 的产品能力合同。
- 不修改 canonical capability Spec；不为交付修复伪造占位 Spec。
- 不修改或提交用户级 `gradle.properties`，不向仓库写入本机绝对路径、`mavenLocal()`、Snapshot 仓库或 sibling filesystem repository。
- 不修改、重写或复活旧归档 `upgrade-gradle-wrapper-9-6-1` 的 state、candidate、verification 或 Archive evidence。
- 不绕过 Native provider，不直接调用 `gh pr create`。

# Acceptance examples

- A1: `./gradlew.bat --version` 报告 Gradle 9.6.1，仓库不要求安装或调用全局 Gradle。
- A2: Wrapper 四个受跟踪文件由 Gradle 9.6.1 完整刷新，distribution 为 `bin`，URL 校验开启，且没有本机绝对路径。
- A3: `./gradlew.bat help --no-daemon --console=plain` 和 `:cap4k-plugin-pipeline-gradle:validatePlugins` 在 JDK 17 下成功。
- A4: buildSrc 测试与全仓 `./gradlew.bat check` 在 Gradle 9.6.1 下通过，兼容修改保持在六个 task cacheability 合同、两个 stale fixture include 和 dependencyInsight 断言的最小范围。
- A5: capability facts 导出/校验、Skill 校验、Runtime facts、capability contract tests、PR workflow guards 全部通过；Runtime/Generator/Analyzer/AgentFacts/Public Docs/Skill 的传播结论为 `verified-no-change` 或 `not-applicable`。
- A6: `git diff --check` 通过；连续两次官方 Wrapper 任务不改变四个 Wrapper 文件的哈希，也不产生其他未解释的 tracked drift。
- A7: `cap4k-reference-payment/.worktrees/build-payment-merchant-settlement` 使用自身 Gradle 9.6.1 Wrapper和显式 `-Pcap4k.local.path=<本 change worktree>` 完成 Composite Build smoke，日志确认本地 cap4k included build 被加载。
- A8: 已知 Gradle 10 弃用与现有非阻塞警告被准确记录；没有伪装为已修复，也没有借机扩大依赖升级范围。

# Constraints and invariants

- 所有修改只发生在 `fix/deliver-gradle-wrapper-9-6-1` 隔离 worktree，`master` 仅用于读取和 fast-forward 同步。
- 新 change 以 `523aa5cf803e12d4a53591a2094a0ad358e3a428` 或创建时更新的最新 `origin/master` 为基线，必须保留其中 PR #216 的 zero-spec provider 修复。
- 旧归档 brief/verification 只作为历史目标与命令清单；新 candidate 必须重新执行检查并由新的只读 Verifier 独立验收。
- Wrapper 版本升级是 Gradle-impacting 变更，必须运行完整 CI 等价验证。
- 验证命令统一使用仓库 Wrapper；不使用全局 `gradle` 生成或验证。
- 下游 Composite Build 只通过显式 Gradle property 指向本 change worktree，不写死仓库默认绝对路径。
- accepted pre-Archive snapshot 与 Archive finish 之间不得加入额外源码、brief、state 或 Git tree 漂移。

# Decisions

- 采用新的 delivery change，而不复活已经 `archive/done` 的旧 change。
- 目标版本仍固定为 Gradle 9.6.1。
- 采用 selective path replay：只迁移旧实现提交中的 15 个非 Comet 文件，不 cherry-pick 旧 state 或 Archive commit。
- `spec_changes` 保持 exact `[]`；本 change 不修改 capability canonical Spec。
- 使用当前主线的 repository-owned Native provider 完成 PR authoring 与 create/reuse。

# Open questions

- 无。

# Verification expectations

- `./gradlew.bat --version`
- `./gradlew.bat help :cap4k-plugin-pipeline-gradle:validatePlugins --no-daemon --console=plain --warning-mode=all`
- `Push-Location buildSrc; ../gradlew.bat test --no-daemon --console=plain --warning-mode=all; Pop-Location`
- `./gradlew.bat check --no-daemon --console=plain --warning-mode=all`
- `./scripts/export-capability-contract-facts.ps1 -OutputFile build/cap4k/capability-contract-facts.json`
- `./scripts/validate-capability-contract.ps1 -FactsFile build/cap4k/capability-contract-facts.json`
- `./skills/scripts/validate-cap4k-skills.ps1 -FactsFile build/cap4k/capability-contract-facts.json`
- `./scripts/validate-current-runtime-facts.ps1 -FactsFile build/cap4k/capability-contract-facts.json`
- `./scripts/test-capability-contract.ps1 -FactsFile build/cap4k/capability-contract-facts.json`
- `./scripts/test-pr-workflow.ps1`
- 连续两次 `./gradlew.bat wrapper --gradle-version 9.6.1 --distribution-type bin` 后核对 Wrapper 哈希和 `git diff --check`
- payment worktree：清空 `CAP4K_LOCAL_PATH`，以 `-Pcap4k.local.path=C:/Users/LD_moxeii/Documents/code/only-workspace/cap4k/.worktrees/deliver-gradle-wrapper-9-6-1` 运行 `cap4kPlan :application:compileKotlin :adapter:compileKotlin :start:test`
