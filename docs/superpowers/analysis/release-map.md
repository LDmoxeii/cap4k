# Release Map

## Purpose

本页是 cap4k Maven Central release 的当前代码地图和事实索引，服务维护 agent 判断 release 行为是否来自当前 repo source。它不是面向最终用户的发布教程，也不替代 repo 外部的 release runbook。

## Current Facts

- Maven Central release workflow 定义在 `.github/workflows/maven-central-release.yml`，由 `push.tags` 触发，当前 tag glob 是 `"v*"`。
- Workflow 内部二次校验 release tag 格式：`GITHUB_REF_NAME` 必须匹配 `^v[0-9]+\.[0-9]+\.[0-9]+$`，否则 `Derive release version` step 失败。
- `RELEASE_VERSION` 由 tag 去掉前缀 `v` 得到，并通过 `./gradlew publish -Prelease.version="${RELEASE_VERSION}"` 传入 Gradle。
- Tagged commit 必须被 `origin/publish/maven-central` 包含。Workflow 会执行 `git fetch --no-tags origin publish/maven-central:refs/remotes/origin/publish/maven-central`，再用 `git merge-base --is-ancestor "${GITHUB_SHA}" "origin/publish/maven-central"` 做 containment gate。
- Workflow release path 先运行 `buildSrc` tests，再运行 root `./gradlew check`，然后执行 publish，最后调用 Central Portal manual upload endpoint，并创建 GitHub Release。
- `CentralReleaseVersion.kt` 定义 Maven group 为 `io.github.ldmoxeii`，baseline version 为 `0.6.0-dev`，release property 为 `release.version`，environment fallback 为 `RELEASE_VERSION`。
- `CentralReleaseVersion.resolve` 对缺失或空白输入返回 `0.6.0-dev`；对非空输入会 trim 后校验；`-SNAPSHOT` 被拒绝；合法 release version 必须是 plain `major.minor.patch`，不是带 `v` 的 tag 字符串。
- `CentralReleaseVersionTest.kt` 覆盖缺失输入使用 baseline、plain release version 被接受、snapshot 被拒绝、`v0.5.0` 被拒绝。
- `kotlin-jvm.gradle.kts` 从 Gradle property `release.version` 或 env `RELEASE_VERSION` 读取 release input，并用 `CentralReleaseVersion.isReleaseBuild` 决定是否是 Central release。
- `kotlin-jvm.gradle.kts` 的 Central Portal repository name 是 `CentralPortal`，目标 URL 是 `https://ossrh-staging-api.central.sonatype.com/service/local/staging/deploy/maven2/`。
- `CentralPublishTaskPolicy.kt` 将所有以 `ToCentralPortalRepository` 结尾的 task 识别为 Central Portal publish task。
- `CentralPublishTaskPolicy.kt` 将所有以 `PluginMarkerMavenPublicationToCentralPortalRepository` 结尾的 task 识别为 plugin marker Central Portal publish task。
- 当前允许发布到 Central Portal 的 plugin marker task 只有：`publishCap4kPipelinePluginMarkerMavenPublicationToCentralPortalRepository` 和 `publishCap4kFlowExportPluginMarkerMavenPublicationToCentralPortalRepository`。
- `kotlin-jvm.gradle.kts` 对 `PublishToMavenRepository` task 做 gating：plugin marker Central Portal task 只有在 release build 且位于 allowlist 时启用；其他 Central Portal publish task 只有在 release build 时启用。
- Signing required gate 与 Central Portal publish task graph 绑定：release build 中，只要 task graph 有允许的 Central Portal publish task，就要求 signing。
- `CentralPublishTaskPolicyTest.kt` 覆盖 Central Portal task name detection、plugin marker detection，以及 allowlist 只允许 pipeline 和 flow export plugin marker，不允许 `publishCap4kPluginPluginMarkerMavenPublicationToCentralPortalRepository` 或普通 maven publication。

## Source Anchors

- `.github/workflows/maven-central-release.yml`: tag trigger、tag regex、`publish/maven-central` branch containment、`release.version` publish invocation、Central Portal upload、GitHub Release creation。
- `buildSrc/src/main/kotlin/buildsrc/convention/CentralReleaseVersion.kt`: release version property/env 名称、baseline、semver 校验、snapshot 拒绝。
- `buildSrc/src/test/kotlin/buildsrc/convention/CentralReleaseVersionTest.kt`: release version behavior 的可执行测试事实。
- `buildSrc/src/main/kotlin/buildsrc/convention/CentralPublishTaskPolicy.kt`: Central Portal task detection 和 plugin marker allowlist。
- `buildSrc/src/test/kotlin/buildsrc/convention/CentralPublishTaskPolicyTest.kt`: task policy 的可执行测试事实。
- `buildSrc/src/main/kotlin/kotlin-jvm.gradle.kts`: publish/signing task gating、CentralPortal repository、`release.version`/`RELEASE_VERSION` 读取。

## Contracts

- Code wins over this map。改 release 文档、发布说明或自动化前，必须重新读取 workflow、`buildSrc` convention source 和 tests。
- Release tag 对外可用的粗触发是 workflow `"v*"`，实际 release tag 合同是 workflow bash regex `v<major>.<minor>.<patch>`。
- Branch containment 合同来自 workflow source：tagged commit 必须包含在 `origin/publish/maven-central`。
- Gradle 侧 release version 输入合同是 plain `major.minor.patch`，由 workflow 从 tag 中去掉 `v` 后通过 `release.version` 传入。
- Central Portal plugin marker policy 以 `CentralPublishTaskPolicy` allowlist 为准，不应从 plugin 名称或旧发布记录推断。
- 本页不替代 repo 外 release runbook、credential 操作手册、Sonatype 账号流程或 GitHub release notes 流程。

## Change Impact

- 修改 tag pattern 会影响 workflow trigger、bash regex、`release.version` 推导、发布说明和维护检查命令。
- 修改 release branch 会影响 containment gate、发布权限边界、issue #98 下游文档和任何外部 runbook。
- 修改 `CentralReleaseVersion` baseline 或校验规则会影响所有模块 version、Maven Central publish 坐标和测试。
- 新增 Gradle plugin 或 plugin marker publication 时，必须同步评估 `CentralPublishTaskPolicy` allowlist、tests、signing gate 和 release map。
- 修改 Central Portal repository name 或 URL 会影响 publish task name matching、credential usage、upload endpoint 和 release verification。

## Verification

Run this command from the cap4k worktree root:

```powershell
rg -n "v\*|publish/maven-central|release.version|centralPortal|plugin marker|PluginMarker|publishPlugins" .github/workflows/maven-central-release.yml buildSrc/src/main/kotlin buildSrc/src/test/kotlin
```

Useful source reads when changing this map:

```powershell
Get-Content -Path .github/workflows/maven-central-release.yml -Raw
Get-Content -Path buildSrc/src/main/kotlin/buildsrc/convention/CentralReleaseVersion.kt -Raw
Get-Content -Path buildSrc/src/main/kotlin/buildsrc/convention/CentralPublishTaskPolicy.kt -Raw
Get-Content -Path buildSrc/src/test/kotlin/buildsrc/convention/CentralReleaseVersionTest.kt -Raw
Get-Content -Path buildSrc/src/test/kotlin/buildsrc/convention/CentralPublishTaskPolicyTest.kt -Raw
Get-Content -Path buildSrc/src/main/kotlin/kotlin-jvm.gradle.kts -Raw
```

## Drift Watch

- Workflow `push.tags` 的 `"v*"` 和内部 regex 可能分离；不要只读其中一个。
- `release.version` 输入必须保持与 workflow 去 `v` 行为一致；如果 Gradle 侧改为接受带 `v` 的 tag，本页和测试都要更新。
- `publish/maven-central` 是当前 containment branch；如果 release branch 策略改名，workflow、public badge/runbook 和本页都要同步。
- Central Portal task name matching 依赖 Gradle generated task names；升级 Gradle publish/plugin behavior 后要重新跑 `buildSrc` tests。
- Plugin marker allowlist 目前只覆盖 `cap4k-pipeline` 和 `cap4k-flow-export` marker；新增 plugin publication 时不要默认放行。

## Not Covered

- Repo 外 credential 管理、Sonatype 账号权限、Central Portal UI 操作和应急回滚 runbook。
- GitHub Release 文案策略或 changelog 编写规范。
- Public docs 的安装教程、版本选择教程或用户迁移指南。
- CI workflow `ci.yml`、snapshot publish、local publish 或 Maven local verification。