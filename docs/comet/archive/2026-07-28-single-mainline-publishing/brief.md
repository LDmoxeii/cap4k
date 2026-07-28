# Outcome

将 cap4k 的发布、普通消费和本地联调从长期发布分支中解耦：仓库只保留 `master` 作为长期源码分支；正式版本从 `master` 中的 `v<major>.<minor>.<patch>` 标签发布到 Maven Central 并创建 GitHub Release；业务项目本地联调通过 Gradle Composite Build 显式替换本地 cap4k 源码；不再保留 Aliyun、Central Snapshot 或其他远程 Snapshot 通道，公开默认项目始终只消费 Maven Central 正式版本。

# Scope

- 用新的日期化发布治理 spec 取代当前自称 forward-looking 的长期 publish branch 合同，并为旧的 `2026-05-22`、`2026-07-21` 发布治理文档增加 superseded 指针，而不改写其历史内容。
- 将 Maven Central release workflow 的 containment gate 从 `origin/publish/maven-central` 改为 `origin/master`，保留精确 tag 校验、release version 推导、签名、Central Portal 自动上传和 GitHub Release。
- 删除 Aliyun workflow、Aliyun Gradle repository/version/task gating 及其专用测试；默认构建只保留本地 publication 和显式 Maven Central 正式发布能力。
- 删除 CI、PR 创建脚本、PR 模板、release issue form、AGENTS 和 current analysis 中对 `publish/aliyun-private`、`publish/maven-central` promotion lane 的现行依赖；required check context 继续叫 `check`。
- 增加一个不依赖 TestKit `withPluginClasspath()`、使用不存在的远程版本的最小 Composite Build consumer 证据，证明 pipeline plugin marker 和官方默认项目所用 runtime modules 都来自本地 included build。
- 将本地联调约束写入贡献者/集成文档：显式启用 Composite Build；公开 Template 不包含本地路径、Aliyun、Snapshot 或 `mavenLocal()`。
- 保留现有 `v2.0.1` tag、release 和旧 promotion merge 历史，不移动、不重建该 tag。
- 新流程合并并验证后，再执行远程 publish branch protection/rules、Aliyun repository secrets 和两个 publish branches 的清理；删除前记录旧 branch tip，确认没有未迁移的正式发布逻辑。

# Non-goals

- 不移动、重建或覆盖 `v2.0.1`，不让本次治理变更中断当前已启动的 `2.0.1` 发布。
- 不在本次增加 Central Portal Snapshot 通道；没有跨机器消费未发布版本的真实需求前，不增加该维护面。
- 不把 Snapshot repository、Composite Build 路径或 workspace-local Maven repository 加入独立 GitHub Template 的默认配置。
- 不把 `mavenLocal()` 作为官方本地联调方案。
- 不兼容历史 publish branch 的源码漂移、旧 bootstrap/codegen fixture 或分支专属 build logic；只迁移仍被目标合同需要的发布能力。
- 不修改 cap4k runtime、generator 或官方默认项目的业务能力合同。
- 本代码 PR 不直接删除远程分支或修改 GitHub branch protection；这些是新流程合并并完成发布 smoke 后的独立收尾操作。

# Acceptance examples

- 给定 tag `v2.0.2` 指向 `origin/master` 包含的提交，Maven Central workflow 推导版本 `2.0.2`、发布签名制品、触发 Central Portal 自动发布并创建同 tag 的 GitHub Release。
- 给定 `v2.0.2` 指向不被 `origin/master` 包含的提交，workflow 在使用发布凭据或上传制品前失败。
- 给定普通 clone 的 cap4k 或官方 Template，执行构建不需要 Central 或 GPG 发布凭据；Template 只使用 Maven Central 正式版本。
- 给定一个声明不存在版本 `999.0.0-local` 的最小 consumer，通过显式 included build 后，`io.github.ldmoxeii.cap4k.pipeline` plugin marker、`ddd-core`、`ddd-domain-repo-jpa` 和 `cap4k-ddd-jpa-starter` 均由本地 cap4k 工程提供，并能运行 `cap4kPlan`、编译和测试；测试不得依赖 `withPluginClasspath()` 或已发布的该版本。
- 给定未启用 included build 的普通 consumer，它继续按声明版本从 Maven Central 解析，不读取本地 sibling path。
- 给定当前主线 workflow、Gradle publication 和 current governance surface，全仓不再提供 Aliyun publish trigger、repository、version property、credentials contract 或 publish tasks；历史文档只能以已被取代的历史事实保留。
- 给定 PR 创建和 CI 校验，唯一长期 base 是 `master`；publish branch promotion 不再是受支持的 PR 类型，docs-only 与 full-check 行为保持不变。
- 给定新流程已合并且发布 smoke 完成，删除两个远程 publish branches 后，current AGENTS、workflow、script、template、issue form 和 release-map 不再把它们描述为现行通道；历史 spec/plan 可保留但必须清楚标记已被新治理取代。

# Constraints and invariants

- 正常实现仍通过短期工作分支 PR 合并到受保护的 `master`；不得直接在 `master` 修改或提交。
- required status check context 保持 `check`，`master` 继续 required PR、strict check 和 admin enforcement。
- Central 发布凭据是 release workflow concern，不得成为普通 clone、`check`、Composite Build 或 Template 构建的前置条件。
- Central 正式版本只接受 plain semantic version 对应的精确 `v<major>.<minor>.<patch>` tag，Gradle 侧仍拒绝 Snapshot 和带 `v` 的 release version input。
- 主线不提供任何远程 Snapshot repository 或 private publish mode；普通 `./gradlew publish` 不得意外激活远程发布。
- Composite Build 的 runtime 自动替换以 `group = io.github.ldmoxeii`、`artifactId = project.name` 为契约；若将来 publication coordinates 漂移，必须重新验证或显式 substitution。
- plugin marker 的本地解析必须由 included build 的 `java-gradle-plugin` declaration 证明，不能由 TestKit 注入 classpath 掩盖。
- 如果 Composite Build 因自定义 publication metadata 不能忠实替换，允许设计显式 workspace-local Maven repository 回退，但仍不得使用或默认启用全局 `mavenLocal()`。
- 独立 GitHub Template 与 cap4k 源码仓库保持运行时解耦，只消费正式 Maven Central 制品。

# Decisions

- 长期源码分支只保留 `master`；artifact channel 不再映射为长期源码分支。
- 正式发布由 `master` 中的 `v*` tag 驱动；tag containment 以 `origin/master` 为准。
- 当前 `v2.0.1` 按既有历史完成且保持不可变，新治理只作用于未来版本。
- Aliyun 发布能力完整移除，不保留 `com.only4` legacy compatibility；当前也不建设 Central Snapshot，本地同机联调只以 Gradle Composite Build 为正式方向。
- 官方本地联调证据必须同时覆盖 pipeline plugin marker 与官方 Template 的三个 runtime modules；当前已有 runtime substitution 证据，但现有 `withPluginClasspath()` 测试不足以证明 marker 解析。
- `--include-build` 可作为便捷入口；最终文档采用实际验证通过、能稳定解析 plugin marker 的 `settings.gradle.kts` 显式 included-build 形式。
- `mavenLocal()` 不作为正式解决方案；必要时的回退是项目显式启用的 workspace-local Maven repository。
- 旧 publish branches 在新 workflow 合并并完成必要验证后删除，删除操作不与代码 PR 混在一起；Aliyun secrets 同期清理。
- 用户于 2026-07-28 确认 Aliyun 私有仓库只为本地联调而存在；既然 Composite Build 能解决该问题，就不再保留 Aliyun 或历史 `com.only4` 坐标。
- 用户于 2026-07-28 确认完整共享理解：唯一长期分支 `master`、唯一远程制品通道 Maven Central 正式版、Composite Build 本地联调、完整移除 Aliyun/Snapshot、现有 `v2.0.1` 不变，并批准进入 Build。

# Open questions

- 无。

# Verification expectations

- 运行 `buildSrc` 单测，覆盖 Central release version 解析、Snapshot 拒绝和 remote publish task gating，并确认 Aliyun version/repository policy 已删除。
- 运行 `scripts/test-pr-workflow.ps1`，证明 PR base 只允许 `master`、模板校验仍有效、docs-only/full-check contract 未回归。
- 解析所有修改的 GitHub Actions 和 issue-form YAML，并执行 `git diff --check`。
- 运行 `./gradlew check`；若受外部制品或环境阻塞，必须记录准确失败点，不能把未运行检查写成通过。
- 运行独立 Composite Build fixture，使用不存在的 plugin/runtime 版本且不调用 `withPluginClasspath()`；执行 `cap4kPlan`、相关 Kotlin compile/test 和 `dependencyInsight`，证明依赖来自 included build project。
- 静态搜索 current governance surface，确认现行 AGENTS、CI、PR scripts/templates、issue form、release-map 和新 spec 不再依赖 publish branch promotion。
- 检查历史 governance specs/plans 带有明确 superseded 指针，但不伪造历史内容。
- 远程 branch protection、Aliyun secrets 和 branch deletion 在合并后单独核查并记录旧 tip SHA；本轮 Verify 只验证代码仓库内准备工作。
