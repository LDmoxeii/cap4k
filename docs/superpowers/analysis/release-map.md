# Release Map

## Purpose

本页是 cap4k Maven Central 正式发布的当前代码地图和事实索引，服务维护 agent 判断 release 行为是否来自当前 repo source。它不是面向最终用户的发布教程，也不替代 repo 外部的 credential 或 Central Portal runbook。

当前治理设计见 [2026-07-28-cap4k-single-mainline-release-governance-design.md](../specs/2026-07-28-cap4k-single-mainline-release-governance-design.md)。

## Current Facts

- `master` 是唯一长期源码分支；PR、workflow、build logic 和治理变更均通过短期工作分支进入 `master`。
- Maven Central release workflow 定义在 `.github/workflows/maven-central-release.yml`，由 `push.tags` 触发，粗粒度 tag glob 是 `"v*"`。
- Workflow 内部要求 `GITHUB_REF_NAME` 精确匹配 `^v[0-9]+\.[0-9]+\.[0-9]+$`。
- `RELEASE_VERSION` 由 tag 去掉 `v` 得到，并通过 `./gradlew publish -Prelease.version="${RELEASE_VERSION}"` 传给 Gradle。
- Tagged commit 必须被 `origin/master` 包含。Workflow fetch `master:refs/remotes/origin/master`，再用 `git merge-base --is-ancestor "${GITHUB_SHA}" "origin/master"` 做 containment gate。
- Workflow 通过签名材料和 Central credentials 发布，再调用 Central Portal automatic upload endpoint，并为同一 tag 创建 GitHub Release。
- Release workflow 不重复运行 `./gradlew check`；可发布提交先通过受保护 `master` 的 required `check`，tag containment 再限制实际发布来源。
- `CentralReleaseVersion.kt` 定义 Maven group 为 `io.github.ldmoxeii`，baseline version 为 `0.6.0-dev`，release property 为 `release.version`，environment fallback 为 `RELEASE_VERSION`。
- `CentralReleaseVersion.resolve` 对缺失或空白输入返回 baseline；非空输入 trim 后必须是 plain `major.minor.patch`；Snapshot 和带 `v` 的输入均被拒绝。
- `kotlin-jvm.gradle.kts` 只配置 Maven Central 正式发布目标；没有 Aliyun、远程 Snapshot、`com.only4` group switching 或 private repository credentials contract。
- Central Portal repository name 是 `CentralPortal`，目标 URL 是 `https://ossrh-staging-api.central.sonatype.com/service/local/staging/deploy/maven2/`。
- `CentralPublishTaskPolicy.kt` 识别 Central Portal publish task，并只允许 Pipeline plugin marker publication；已退役的 flow-export marker 不在 allowlist 中。
- `PublishToMavenRepository` gating 只在显式 release build 中启用 Central Portal tasks；签名 required gate 也绑定到允许的 Central task graph。
- 本地 cap4k/consumer 联调使用显式 Gradle Composite Build，不使用 Aliyun、远程 Snapshot 或默认 `mavenLocal()`。
- 已存在的 `v2.0.1` tag 和历史 promotion merge 保持原样；未来 release containment 以 `origin/master` 为准。

## Source Anchors

- `.github/workflows/maven-central-release.yml`: tag trigger、精确 tag regex、`origin/master` containment、`release.version` invocation、Central Portal upload、GitHub Release。
- `buildSrc/src/main/kotlin/buildsrc/convention/CentralReleaseVersion.kt`: group、baseline、release input 名称、semver 校验和 Snapshot 拒绝。
- `buildSrc/src/test/kotlin/buildsrc/convention/CentralReleaseVersionTest.kt`: release version 行为测试。
- `buildSrc/src/main/kotlin/buildsrc/convention/CentralPublishTaskPolicy.kt`: Central Portal task detection 和 plugin marker allowlist。
- `buildSrc/src/test/kotlin/buildsrc/convention/CentralPublishTaskPolicyTest.kt`: task policy 测试。
- `buildSrc/src/main/kotlin/kotlin-jvm.gradle.kts`: publication coordinates、Central repository、task/signing gating。
- `.github/workflows/ci.yml`: 唯一 PR base、docs-only/full-check classification 和 required `check`。
- `scripts/create-pr.ps1` 与 `scripts/test-pr-workflow.ps1`: 本地 PR base/head policy 和可执行治理证据。

## Contracts

- 代码事实优先于本地图。修改 release 文档、发布说明或自动化前，重新读取 workflow、Gradle convention source 和 tests。
- GitHub trigger 的 `"v*"` 只是候选范围；真正 tag 合同是 `v<major>.<minor>.<patch>`。
- Branch containment 合同来自 workflow source：tagged commit 必须包含在 `origin/master`。
- Gradle release version 输入是 workflow 去掉 `v` 后得到的 plain `major.minor.patch`。
- Central Portal plugin marker policy 以 `CentralPublishTaskPolicy` allowlist 为准，不能从 plugin 名称或旧发布记录推断。
- Central/signing credentials 只属于 release workflow，不得成为 clone、required `check`、Composite Build 或 consumer build 的前置条件。
- 当前没有 Aliyun、Central Snapshot 或其他远程 Snapshot 发布通道；历史文档中的相关内容不是现行合同。
- 公开 consumer 和独立 GitHub Template 只使用 Maven Central 正式版本，不包含本地路径或本地 repository 默认值。

## Change Impact

- 修改 tag pattern 会影响 workflow trigger、bash regex、version 推导、release issue 和维护检查。
- 修改唯一长期分支会影响 tag containment、CI base guard、PR script/template、AGENTS 和本页；不能只改 workflow。
- 修改 `CentralReleaseVersion` group、baseline 或校验规则会影响模块坐标、Composite substitution、Maven Central publish 和 tests。
- 新增 Gradle plugin 或 marker publication 时，必须同步评估 marker allowlist、tests、signing gate 和本页。
- 修改 Central Portal repository name 或 URL 会影响 publish task name matching、credentials、upload endpoint 和 release verification。
- 修改 project group 或 artifactId/project-name 对齐关系时，必须重新运行 Composite Build substitution 证据。
- 新增任何远程 pre-release channel 必须先有真实跨机器消费需求和独立 design；不得重新引入长期 publish branch。

## Verification

从 cap4k worktree 根目录运行：

```powershell
rg -ni 'v\*|origin/master|release.version|CentralPortal|ossrh-staging-api|plugin marker|PluginMarker' .github/workflows/maven-central-release.yml buildSrc/src/main/kotlin buildSrc/src/test/kotlin
```

确认 current governance surface 不再包含旧发布通道：

```powershell
rg -ni 'publish/(aliyun-private|maven-central)|publish_promotion|ALIYUN_|com\.only4|SNAPSHOT' AGENTS.md .github/workflows .github/PULL_REQUEST_TEMPLATE.md .github/ISSUE_TEMPLATE/release.yml scripts docs/superpowers/analysis/release-map.md docs/superpowers/specs/2026-07-28-cap4k-single-mainline-release-governance-design.md
```

上面的搜索允许出现明确的拒绝、删除或 non-goal 描述；不得出现可执行 trigger、repository、credential、promotion 或 current containment 合同。

## Drift Watch

- Workflow `push.tags` 的 `"v*"` 与内部 exact regex 可能不一致；检查时必须同时读取。
- `release.version` 必须保持与 workflow 去 `v` 的行为一致。
- `origin/master` containment、PR base guard、PR template 和 AGENTS 必须作为同一个治理合同更新。
- Central Portal task matching 依赖 Gradle 生成的 task names；升级 Gradle publish/plugin 行为后重新运行 `buildSrc` tests。
- Plugin marker allowlist 当前只覆盖 Pipeline marker；新增 plugin publication 时不要默认放行，也不要恢复已退役的 flow-export marker。
- Composite Build runtime substitution 依赖 `io.github.ldmoxeii` 和 project name；publication coordinates 漂移后不能沿用旧证据。

## Not Covered

- Repo 外 credential 管理、Sonatype 账号权限、Central Portal UI 操作和应急回滚 runbook。
- GitHub Release 文案策略或 changelog 编写规范。
- Public docs 的安装教程、版本选择教程或用户迁移指南。
- Composite fixture 的具体路径和命令；以当前 integration test source 为准。
- 合并后的远程 branch/rules/secrets 删除记录。
