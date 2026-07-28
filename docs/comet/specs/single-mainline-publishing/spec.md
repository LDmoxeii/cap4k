# 单主线发布与本地联调契约

## 目标模型

cap4k 以 `master` 作为唯一长期源码分支。源码协作、制品发布目的地和未发布源码联调是三个独立边界：

- 工作分支通过 PR 进入 `master`；
- Maven Central 正式制品由 `master` 中的稳定版本 tag 发布；
- 本机同时开发 cap4k 与 consumer 时使用 Gradle Composite Build；
- 公开 consumer 和官方 Template 默认只消费 Maven Central 正式制品。

不再提供 Aliyun、Central Snapshot 或其他远程 Snapshot 通道。长期发布分支不得再保存与 `master` 不同的 group、version、fixture、workflow 或 build logic。

## 源码与 PR 治理

- 唯一长期源码分支是 `master`。
- 正常代码、文档、workflow、发布约定和治理规则都从短期 `feature/*`、`fix/*`、`docs/*` 分支通过 PR 进入 `master`。
- PR 创建脚本只接受 `master` 作为 base，并拒绝以受保护长期分支作为工作分支 head。
- required CI job context 保持 `check`。
- `master` PR 的 docs-only/full Gradle 分类保持现有合同；不再存在 publish-promotion PR 分类或“只接受同仓库 master 作为 publish branch head”的分支特例。
- release issue 记录制品动作、source commit/tag/version 和检查，不再以发布分支名作为 lane。

## Maven Central 正式发布

- GitHub Actions 监听粗粒度 `v*` tag trigger，但只接受精确 `v<major>.<minor>.<patch>`。
- workflow 从 tag 去掉 `v` 得到 plain release version，并通过 `release.version` 传给 Gradle。
- tag commit 必须被 `origin/master` 包含；不满足 containment 时必须在远程发布前失败。
- Central release 保留签名、Maven Central repository publish、Central Portal automatic publication 和 GitHub Release。
- Central 凭据和签名材料只在 release workflow 中读取；普通 clone、CI check、Composite Build 和 consumer 构建不要求这些凭据。
- 普通 branch push 和普通 `./gradlew publish` 不得激活 Central remote publish tasks。
- 已存在的 `v2.0.1` tag 及其 promotion merge 历史保持不可变；新 containment 只约束未来 tag。

## 无私有或 Snapshot 发布通道

- 删除 `.github/workflows/aliyun-snapshot.yml`，不再提供 Aliyun branch-push 或 manual-dispatch publish path。
- 删除 Gradle 中的 Aliyun repository、credentials、version property、group switching 和 remote task gating。
- 不保留历史 `com.only4:*` compatibility contract；公开、正式和本地 Composite Build 坐标统一以 `io.github.ldmoxeii:*` 为准。
- 当前不增加 Central Portal Snapshot 或其他远程 Snapshot repository。
- Aliyun repository secrets 在代码变更合并后作为远程治理收尾删除。
- 将来若出现跨机器消费未发布版本的真实需求，必须以独立 change 重新选择公开 Snapshot、CI-local repository 或其他方案，不能恢复长期发布分支。

## 普通消费合同

- 公开 consumer 和独立 GitHub Template 使用 `mavenCentral()` 与确定的 cap4k 正式版本。
- 默认配置不包含 Snapshot repository、本地 filesystem repository、sibling checkout path 或 `mavenLocal()`。
- clone 后的普通构建不需要任何发布凭据。
- 未显式启用本地联调时，consumer 按声明的正式版本从 Maven Central 解析。

## Gradle Composite Build 本地联调

- consumer 必须显式选择本地 cap4k checkout；默认行为不得因 sibling directory 恰好存在而改变。
- 正式支持形式以验证通过的 `settings.gradle.kts` included-build 配置为准；`--include-build` 可以作为便捷命令，但不能在缺少 plugin marker 证据时成为唯一承诺。
- 本地 included build 必须同时提供：
  - Gradle plugin id `io.github.ldmoxeii.cap4k.pipeline`；
  - `io.github.ldmoxeii:ddd-core`；
  - `io.github.ldmoxeii:ddd-domain-repo-jpa`；
  - `io.github.ldmoxeii:cap4k-ddd-jpa-starter`。
- runtime module substitution 依赖 `group = io.github.ldmoxeii` 与 `artifactId = project.name`，版本不同不阻止 Composite Build 替换。
- pipeline plugin marker 必须由 included build 的 `java-gradle-plugin` declaration 解析，不得依赖 TestKit `withPluginClasspath()`。
- 最小验收 consumer 使用仓库中不存在的 `999.0.0-local` plugin/runtime 版本，以排除 Maven Central 意外命中；它必须能运行 `cap4kPlan`、Kotlin compile、smoke test 和 dependency insight。
- 当前坐标不需要显式 dependency substitution。只有 artifactId/project name 漂移、多 publication、variant metadata 不一致或明确兼容历史坐标时，才允许增加最小显式 mapping。

## 本地仓库回退

如果真实 Composite Build 验证证明自定义 publication metadata 无法忠实替换，允许增加显式 workspace-local Maven repository 回退：

- repository 位于 workspace 受控目录，而不是全局 Maven cache；
- consumer 在 `pluginManagement` 和 `dependencyResolutionManagement` 中通过显式 local property 同时启用；
- repository 包含 pipeline marker、plugin implementation、三个默认 runtime modules 及其本地传递依赖；
- 公开 Template 和普通 consumer 默认不启用该 repository；
- `mavenLocal()` 仍不是官方方案。

## 旧发布分支迁移

- 新 workflow、Gradle publication、CI/PR 治理、current analysis 和贡献者规则先通过普通 PR 合并到 `master`。
- 合并前不得移动或重建 `v2.0.1`。
- 删除旧 publish branches 前记录两个 branch tip，确认 Maven Central 正式发布所需 behavior 已迁移，且没有未关闭 promotion PR；Aliyun branch 中的私有 repository、`com.only4` 坐标和旧 fixture 不迁移。
- 新 workflow 合并并完成必要验证后，删除两个 publish branches 的 protection/rules 和远程 refs，再 prune 本地 refs/worktrees，并删除 Aliyun repository secrets。
- 当前治理文档不得继续把已删除分支描述为现行通道；历史 spec/plan 保留原始事实，但必须清楚标记已被新日期化治理 spec 取代。

## 非目标

- 当前不提供 Central Portal Snapshot。
- 不保留 Aliyun 私有发布能力或 `com.only4` 坐标兼容。
- 不恢复旧 publish branch 中已经退出主线的 bootstrap、codegen 或 fixture 内容。
- 不改变 runtime、generator、starter 或官方默认项目的业务能力。
- 不将本地联调设置写入独立 GitHub Template 默认配置。
- 不把远程 branch deletion 混入落地代码 PR；它是合并和 smoke 后的操作性收尾。
