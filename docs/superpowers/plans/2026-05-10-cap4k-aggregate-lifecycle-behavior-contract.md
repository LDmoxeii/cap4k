# Aggregate Lifecycle Behavior Contract Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Make aggregate lifecycle hooks work from generated `*Behavior.kt` extension files while preserving existing entity member hook compatibility.

**Architecture:** `DefaultEntityInlinePersistListener` keeps the current member-method contract and adds a second reflection path for top-level Kotlin behavior extensions compiled as `*BehaviorKt` static methods. The aggregate behavior template exposes `onCreate`, `onUpdate`, and `onDelete` skeletons in the checked-in `SKIP` behavior file, and public authoring docs describe that behavior file as the default lifecycle completion surface.

**Tech Stack:** Kotlin JVM 17, JUnit Jupiter, Gradle, Pebble templates, cap4k pipeline renderer and aggregate generator.

---

## File Structure

- Modify `ddd-core/src/main/kotlin/com/only4/cap4k/ddd/core/domain/repo/impl/DefaultEntityInlinePersistListener.kt`
  - Owns lifecycle dispatch.
  - Adds member-vs-behavior lookup helpers and distinct cache keys.
- Modify `ddd-core/src/test/kotlin/com/only4/cap4k/ddd/core/domain/repo/impl/DefaultEntityInlinePersistListenerTest.kt`
  - Keeps existing member-method tests.
  - Adds behavior extension tests and updates cache key assertions.
- Create `ddd-core/src/test/kotlin/com/only4/cap4k/ddd/core/domain/repo/impl/lifecycle/TestEntityWithBehaviorHooks.kt`
  - Test entity for behavior extension create/update/delete.
- Create `ddd-core/src/test/kotlin/com/only4/cap4k/ddd/core/domain/repo/impl/lifecycle/TestEntityWithBehaviorHooksBehavior.kt`
  - File name intentionally compiles to `TestEntityWithBehaviorHooksBehaviorKt`.
- Create `ddd-core/src/test/kotlin/com/only4/cap4k/ddd/core/domain/repo/impl/lifecycle/TestEntityWithMemberAndBehaviorHooks.kt`
  - Test entity proving member hooks win over behavior hooks.
- Create `ddd-core/src/test/kotlin/com/only4/cap4k/ddd/core/domain/repo/impl/lifecycle/TestEntityWithMemberAndBehaviorHooksBehavior.kt`
  - Behavior extension for the member-priority test.
- Create `ddd-core/src/test/kotlin/com/only4/cap4k/ddd/core/domain/repo/impl/lifecycle/TestEntityWithBehaviorDeleteAndMemberRemove.kt`
  - Test entity proving behavior `onDelete` wins before member `onRemove`.
- Create `ddd-core/src/test/kotlin/com/only4/cap4k/ddd/core/domain/repo/impl/lifecycle/TestEntityWithBehaviorDeleteAndMemberRemoveBehavior.kt`
  - Behavior extension for delete precedence.
- Create `ddd-core/src/test/kotlin/com/only4/cap4k/ddd/core/domain/repo/impl/lifecycle/TestEntityWithBehaviorRemoveOnly.kt`
  - Test entity for behavior `onRemove` compatibility fallback.
- Create `ddd-core/src/test/kotlin/com/only4/cap4k/ddd/core/domain/repo/impl/lifecycle/TestEntityWithBehaviorRemoveOnlyBehavior.kt`
  - Behavior extension for `onRemove` fallback.
- Create `ddd-core/src/test/kotlin/com/only4/cap4k/ddd/core/domain/repo/impl/lifecycle/TestEntityWithThrowingBehaviorHook.kt`
  - Test entity for exception propagation.
- Create `ddd-core/src/test/kotlin/com/only4/cap4k/ddd/core/domain/repo/impl/lifecycle/TestEntityWithThrowingBehaviorHookBehavior.kt`
  - Throwing behavior extension.
- Modify `cap4k-plugin-pipeline-renderer-pebble/src/main/resources/presets/ddd-default/aggregate/behavior.kt.peb`
  - Emits lifecycle extension skeletons.
- Modify `cap4k-plugin-pipeline-renderer-pebble/src/test/kotlin/com/only4/cap4k/plugin/pipeline/renderer/pebble/PebbleArtifactRendererTest.kt`
  - Updates behavior scaffold assertions.
- Modify `docs/public/authoring/domain.md`
  - Names lifecycle hooks as behavior-file completion points.
- Modify `docs/public/authoring/generation-boundaries.md`
  - Keeps generation/handwritten ownership guidance aligned with the new behavior skeleton.
- Modify `docs/public/authoring/generator/code-generation.md`
  - Keeps `plan.json` guidance aligned with lifecycle hook skeletons.

Do not modify `cap4k-reference-content-studio` in this issue. It can consume the released framework capability in its own follow-up.

### Task 1: Add Runtime Contract Tests

**Files:**
- Modify: `ddd-core/src/test/kotlin/com/only4/cap4k/ddd/core/domain/repo/impl/DefaultEntityInlinePersistListenerTest.kt`
- Create: `ddd-core/src/test/kotlin/com/only4/cap4k/ddd/core/domain/repo/impl/lifecycle/TestEntityWithBehaviorHooks.kt`
- Create: `ddd-core/src/test/kotlin/com/only4/cap4k/ddd/core/domain/repo/impl/lifecycle/TestEntityWithBehaviorHooksBehavior.kt`
- Create: `ddd-core/src/test/kotlin/com/only4/cap4k/ddd/core/domain/repo/impl/lifecycle/TestEntityWithMemberAndBehaviorHooks.kt`
- Create: `ddd-core/src/test/kotlin/com/only4/cap4k/ddd/core/domain/repo/impl/lifecycle/TestEntityWithMemberAndBehaviorHooksBehavior.kt`
- Create: `ddd-core/src/test/kotlin/com/only4/cap4k/ddd/core/domain/repo/impl/lifecycle/TestEntityWithBehaviorDeleteAndMemberRemove.kt`
- Create: `ddd-core/src/test/kotlin/com/only4/cap4k/ddd/core/domain/repo/impl/lifecycle/TestEntityWithBehaviorDeleteAndMemberRemoveBehavior.kt`
- Create: `ddd-core/src/test/kotlin/com/only4/cap4k/ddd/core/domain/repo/impl/lifecycle/TestEntityWithBehaviorRemoveOnly.kt`
- Create: `ddd-core/src/test/kotlin/com/only4/cap4k/ddd/core/domain/repo/impl/lifecycle/TestEntityWithBehaviorRemoveOnlyBehavior.kt`
- Create: `ddd-core/src/test/kotlin/com/only4/cap4k/ddd/core/domain/repo/impl/lifecycle/TestEntityWithThrowingBehaviorHook.kt`
- Create: `ddd-core/src/test/kotlin/com/only4/cap4k/ddd/core/domain/repo/impl/lifecycle/TestEntityWithThrowingBehaviorHookBehavior.kt`

- [ ] **Step 1: Add dedicated behavior-extension test entities**

Create `TestEntityWithBehaviorHooks.kt`:

```kotlin
package com.only4.cap4k.ddd.core.domain.repo.impl.lifecycle

class TestEntityWithBehaviorHooks {
    var onCreateCallCount = 0
    var onUpdateCallCount = 0
    var onDeleteCallCount = 0
}
```

Create `TestEntityWithBehaviorHooksBehavior.kt`:

```kotlin
package com.only4.cap4k.ddd.core.domain.repo.impl.lifecycle

fun TestEntityWithBehaviorHooks.onCreate() {
    onCreateCallCount++
}

fun TestEntityWithBehaviorHooks.onUpdate() {
    onUpdateCallCount++
}

fun TestEntityWithBehaviorHooks.onDelete() {
    onDeleteCallCount++
}
```

Create `TestEntityWithMemberAndBehaviorHooks.kt`:

```kotlin
package com.only4.cap4k.ddd.core.domain.repo.impl.lifecycle

class TestEntityWithMemberAndBehaviorHooks {
    var memberCreateCallCount = 0
    var behaviorCreateCallCount = 0

    fun onCreate() {
        memberCreateCallCount++
    }
}
```

Create `TestEntityWithMemberAndBehaviorHooksBehavior.kt`:

```kotlin
package com.only4.cap4k.ddd.core.domain.repo.impl.lifecycle

fun TestEntityWithMemberAndBehaviorHooks.onCreate() {
    behaviorCreateCallCount++
}
```

Create `TestEntityWithBehaviorDeleteAndMemberRemove.kt`:

```kotlin
package com.only4.cap4k.ddd.core.domain.repo.impl.lifecycle

class TestEntityWithBehaviorDeleteAndMemberRemove {
    var behaviorDeleteCallCount = 0
    var memberRemoveCallCount = 0

    fun onRemove() {
        memberRemoveCallCount++
    }
}
```

Create `TestEntityWithBehaviorDeleteAndMemberRemoveBehavior.kt`:

```kotlin
package com.only4.cap4k.ddd.core.domain.repo.impl.lifecycle

fun TestEntityWithBehaviorDeleteAndMemberRemove.onDelete() {
    behaviorDeleteCallCount++
}
```

Create `TestEntityWithBehaviorRemoveOnly.kt`:

```kotlin
package com.only4.cap4k.ddd.core.domain.repo.impl.lifecycle

class TestEntityWithBehaviorRemoveOnly {
    var behaviorRemoveCallCount = 0
}
```

Create `TestEntityWithBehaviorRemoveOnlyBehavior.kt`:

```kotlin
package com.only4.cap4k.ddd.core.domain.repo.impl.lifecycle

fun TestEntityWithBehaviorRemoveOnly.onRemove() {
    behaviorRemoveCallCount++
}
```

Create `TestEntityWithThrowingBehaviorHook.kt`:

```kotlin
package com.only4.cap4k.ddd.core.domain.repo.impl.lifecycle

class TestEntityWithThrowingBehaviorHook
```

Create `TestEntityWithThrowingBehaviorHookBehavior.kt`:

```kotlin
package com.only4.cap4k.ddd.core.domain.repo.impl.lifecycle

fun TestEntityWithThrowingBehaviorHook.onCreate() {
    throw IllegalStateException("behavior onCreate failed")
}
```

- [ ] **Step 2: Import the test entities**

Add these imports near the top of `DefaultEntityInlinePersistListenerTest.kt`:

```kotlin
import com.only4.cap4k.ddd.core.domain.repo.impl.lifecycle.TestEntityWithBehaviorDeleteAndMemberRemove
import com.only4.cap4k.ddd.core.domain.repo.impl.lifecycle.TestEntityWithBehaviorHooks
import com.only4.cap4k.ddd.core.domain.repo.impl.lifecycle.TestEntityWithBehaviorRemoveOnly
import com.only4.cap4k.ddd.core.domain.repo.impl.lifecycle.TestEntityWithMemberAndBehaviorHooks
import com.only4.cap4k.ddd.core.domain.repo.impl.lifecycle.TestEntityWithThrowingBehaviorHook
```

- [ ] **Step 3: Update existing member cache key assertions**

Replace the existing cache key in `should cache method lookup results`:

```kotlin
val cacheKey = "${TestEntityWithHandlers::class.java.name}.onCreate"
```

with:

```kotlin
val cacheKey = "member:${TestEntityWithHandlers::class.java.name}.onCreate"
```

Replace the cache filter in `different instances of same class should use cache`:

```kotlin
.filter { it.endsWith(".onCreate") }
```

with:

```kotlin
.filter { it == "member:${TestEntityWithHandlers::class.java.name}.onCreate" }
```

- [ ] **Step 4: Add behavior lifecycle tests**

Add these tests to the existing nested test groups in `DefaultEntityInlinePersistListenerTest.kt`.

Inside `OnCreateTests`:

```kotlin
@Test
@DisplayName("应该调用行为扩展的onCreate方法")
fun `should call behavior extension onCreate method`() {
    val entity = TestEntityWithBehaviorHooks()

    listener.onCreate(entity)

    assertEquals(1, entity.onCreateCallCount)
}

@Test
@DisplayName("实体onCreate方法应该优先于行为扩展onCreate方法")
fun `should prefer entity onCreate method over behavior extension onCreate method`() {
    val entity = TestEntityWithMemberAndBehaviorHooks()

    listener.onCreate(entity)

    assertEquals(1, entity.memberCreateCallCount)
    assertEquals(0, entity.behaviorCreateCallCount)
}

@Test
@DisplayName("行为扩展方法调用异常应该向上传播")
fun `should propagate behavior extension invocation exceptions`() {
    val entity = TestEntityWithThrowingBehaviorHook()

    assertThrows<InvocationTargetException> {
        listener.onCreate(entity)
    }
}
```

Inside `OnUpdateTests`:

```kotlin
@Test
@DisplayName("应该调用行为扩展的onUpdate方法")
fun `should call behavior extension onUpdate method`() {
    val entity = TestEntityWithBehaviorHooks()

    listener.onUpdate(entity)

    assertEquals(1, entity.onUpdateCallCount)
}
```

Inside `OnDeleteTests`:

```kotlin
@Test
@DisplayName("应该调用行为扩展的onDelete方法")
fun `should call behavior extension onDelete method`() {
    val entity = TestEntityWithBehaviorHooks()

    listener.onDelete(entity)

    assertEquals(1, entity.onDeleteCallCount)
}

@Test
@DisplayName("行为扩展onDelete应该优先于实体onRemove方法")
fun `should prefer behavior onDelete before entity onRemove fallback`() {
    val entity = TestEntityWithBehaviorDeleteAndMemberRemove()

    listener.onDelete(entity)

    assertEquals(1, entity.behaviorDeleteCallCount)
    assertEquals(0, entity.memberRemoveCallCount)
}

@Test
@DisplayName("当onDelete不存在时应该调用行为扩展onRemove方法")
fun `should call behavior onRemove when onDelete is absent`() {
    val entity = TestEntityWithBehaviorRemoveOnly()

    listener.onDelete(entity)

    assertEquals(1, entity.behaviorRemoveCallCount)
}
```

Inside `CacheTests`:

```kotlin
@Test
@DisplayName("行为扩展方法查找应该使用独立缓存键")
fun `behavior extension lookup should use distinct cache key`() {
    val entity = TestEntityWithBehaviorHooks()
    val entityClass = TestEntityWithBehaviorHooks::class.java
    val behaviorClassName = "${entityClass.`package`.name}.${entityClass.simpleName}BehaviorKt"

    listener.onCreate(entity)

    val cacheKey = "behavior:$behaviorClassName.onCreate(${entityClass.name})"
    assert(DefaultEntityInlinePersistListener.HANDLER_METHOD_CACHE.containsKey(cacheKey))
}
```

- [ ] **Step 5: Run runtime tests and confirm they fail for the current implementation**

Run:

```powershell
.\gradlew.bat :ddd-core:test --tests "com.only4.cap4k.ddd.core.domain.repo.impl.DefaultEntityInlinePersistListenerTest"
```

Expected: FAIL. The behavior extension tests fail because the listener does not yet resolve `*BehaviorKt` methods. The updated member cache assertions fail until member cache keys gain the `member:` prefix.

### Task 2: Implement Runtime Behavior Extension Lookup

**Files:**
- Modify: `ddd-core/src/main/kotlin/com/only4/cap4k/ddd/core/domain/repo/impl/DefaultEntityInlinePersistListener.kt`
- Test: `ddd-core/src/test/kotlin/com/only4/cap4k/ddd/core/domain/repo/impl/DefaultEntityInlinePersistListenerTest.kt`

- [ ] **Step 1: Replace the listener with member and behavior lookup paths**

Use this implementation shape in `DefaultEntityInlinePersistListener.kt`:

```kotlin
package com.only4.cap4k.ddd.core.domain.repo.impl

import java.lang.reflect.Method
import java.util.concurrent.ConcurrentHashMap

/**
 * 默认实体内联持久化监听器
 *
 * @author LD_moxeii
 * @date 2025/07/26
 */
class DefaultEntityInlinePersistListener : AbstractPersistListener<Any>() {

    companion object {
        val HANDLER_METHOD_CACHE: MutableMap<String, Method?> = ConcurrentHashMap()
    }

    override fun onCreate(entity: Any) {
        entity.tryInvokeHandlerMethod("onCreate")
    }

    override fun onUpdate(entity: Any) {
        entity.tryInvokeHandlerMethod("onUpdate")
    }

    override fun onDelete(entity: Any) {
        if (entity.tryInvokeMemberHandlerMethod("onDelete")) {
            return
        }
        if (entity.tryInvokeBehaviorHandlerMethod("onDelete")) {
            return
        }
        if (entity.tryInvokeMemberHandlerMethod("onRemove")) {
            return
        }
        entity.tryInvokeBehaviorHandlerMethod("onRemove")
    }

    private fun Any.tryInvokeHandlerMethod(methodName: String): Boolean =
        tryInvokeMemberHandlerMethod(methodName) || tryInvokeBehaviorHandlerMethod(methodName)

    private fun Any.tryInvokeMemberHandlerMethod(methodName: String): Boolean {
        val method = getMemberHandlerMethod(this.javaClass, methodName) ?: return false
        method.invoke(this)
        return true
    }

    private fun Any.tryInvokeBehaviorHandlerMethod(methodName: String): Boolean {
        val method = getBehaviorHandlerMethod(this.javaClass, methodName) ?: return false
        method.invoke(null, this)
        return true
    }

    private fun getMemberHandlerMethod(clazz: Class<*>, methodName: String): Method? {
        val key = "member:${clazz.name}.$methodName"
        return HANDLER_METHOD_CACHE.computeIfAbsent(key) {
            try {
                clazz.getMethod(methodName)
            } catch (ex: Exception) {
                null
            }
        }
    }

    private fun getBehaviorHandlerMethod(clazz: Class<*>, methodName: String): Method? {
        val behaviorClassName = behaviorClassName(clazz)
        val key = "behavior:$behaviorClassName.$methodName(${clazz.name})"
        return HANDLER_METHOD_CACHE.computeIfAbsent(key) {
            try {
                Class.forName(behaviorClassName).getMethod(methodName, clazz)
            } catch (ex: Exception) {
                null
            }
        }
    }

    private fun behaviorClassName(clazz: Class<*>): String {
        val packageName = clazz.`package`?.name.orEmpty()
        return if (packageName.isBlank()) {
            "${clazz.simpleName}BehaviorKt"
        } else {
            "$packageName.${clazz.simpleName}BehaviorKt"
        }
    }
}
```

- [ ] **Step 2: Run runtime tests and confirm they pass**

Run:

```powershell
.\gradlew.bat :ddd-core:test --tests "com.only4.cap4k.ddd.core.domain.repo.impl.DefaultEntityInlinePersistListenerTest"
```

Expected: BUILD SUCCESSFUL.

- [ ] **Step 3: Commit runtime changes**

Run:

```powershell
git add ddd-core/src/main/kotlin/com/only4/cap4k/ddd/core/domain/repo/impl/DefaultEntityInlinePersistListener.kt `
  ddd-core/src/test/kotlin/com/only4/cap4k/ddd/core/domain/repo/impl/DefaultEntityInlinePersistListenerTest.kt `
  ddd-core/src/test/kotlin/com/only4/cap4k/ddd/core/domain/repo/impl/lifecycle
git commit -m "fix: recognize aggregate behavior lifecycle hooks"
```

### Task 3: Update Behavior Template And Renderer Test

**Files:**
- Modify: `cap4k-plugin-pipeline-renderer-pebble/src/main/resources/presets/ddd-default/aggregate/behavior.kt.peb`
- Modify: `cap4k-plugin-pipeline-renderer-pebble/src/test/kotlin/com/only4/cap4k/plugin/pipeline/renderer/pebble/PebbleArtifactRendererTest.kt`

- [ ] **Step 1: Update the renderer test expectation**

In `PebbleArtifactRendererTest.kt`, replace the body assertions in `aggregate behavior template renders checked in scaffold without generated business body` with:

```kotlin
assertTrue(content.startsWith("package com.acme.demo.domain.aggregates.category"))
assertTrue(content.contains("Place behavior for Category"))
assertTrue(content.contains("fun Category.onCreate()"))
assertTrue(content.contains("fun Category.onUpdate()"))
assertTrue(content.contains("fun Category.onDelete()"))
assertFalse(content.contains("fun Category.onRemove()"))
assertFalse(content.contains("managed-begin"))
```

- [ ] **Step 2: Run renderer test and confirm it fails for the current template**

Run:

```powershell
.\gradlew.bat :cap4k-plugin-pipeline-renderer-pebble:test --tests "com.only4.cap4k.plugin.pipeline.renderer.pebble.PebbleArtifactRendererTest.aggregate behavior template renders checked in scaffold without generated business body"
```

Expected: FAIL. The current template does not render `fun Category.onCreate()`, `fun Category.onUpdate()`, or `fun Category.onDelete()`.

- [ ] **Step 3: Update the behavior template**

Replace `aggregate/behavior.kt.peb` with:

```text
package {{ packageName }}

{% for import in imports(imports) -%}
import {{ import }}
{% endfor %}
/**
 * Place behavior for {{ rootName }} and its owned entities here.
 */
fun {{ rootName }}.onCreate() {
}

fun {{ rootName }}.onUpdate() {
}

fun {{ rootName }}.onDelete() {
}
```

- [ ] **Step 4: Run renderer test and confirm it passes**

Run:

```powershell
.\gradlew.bat :cap4k-plugin-pipeline-renderer-pebble:test --tests "com.only4.cap4k.plugin.pipeline.renderer.pebble.PebbleArtifactRendererTest.aggregate behavior template renders checked in scaffold without generated business body"
```

Expected: BUILD SUCCESSFUL.

- [ ] **Step 5: Run aggregate planner test for behavior ownership regression**

Run:

```powershell
.\gradlew.bat :cap4k-plugin-pipeline-generator-aggregate:test --tests "com.only4.cap4k.plugin.pipeline.generator.aggregate.AggregateArtifactPlannerTest.aggregate planner emits checked in behavior scaffold per aggregate root only"
```

Expected: BUILD SUCCESSFUL. This confirms the behavior file is still aggregate-root only and remains `ConflictPolicy.SKIP`.

- [ ] **Step 6: Commit template and renderer changes**

Run:

```powershell
git add cap4k-plugin-pipeline-renderer-pebble/src/main/resources/presets/ddd-default/aggregate/behavior.kt.peb `
  cap4k-plugin-pipeline-renderer-pebble/src/test/kotlin/com/only4/cap4k/plugin/pipeline/renderer/pebble/PebbleArtifactRendererTest.kt
git commit -m "feat: generate aggregate lifecycle behavior skeletons"
```

### Task 4: Update Public Authoring Guidance

**Files:**
- Modify: `docs/public/authoring/domain.md`
- Modify: `docs/public/authoring/generation-boundaries.md`
- Modify: `docs/public/authoring/generator/code-generation.md`

- [ ] **Step 1: Update domain authoring guidance**

In `docs/public/authoring/domain.md`, under `## 这一层可以写什么`, replace this bullet:

```markdown
- `ContentBehavior.kt`、`MediaProcessingTaskBehavior.kt` 这类明确留给作者补聚合行为的 checked-in 文件。
```

with:

```markdown
- `ContentBehavior.kt`、`MediaProcessingTaskBehavior.kt` 这类明确留给作者补聚合行为的 checked-in 文件；生命周期入口优先写成 `fun Content.onCreate()`、`fun Content.onUpdate()`、`fun Content.onDelete()` 这类行为扩展，而不是改生成聚合主体。
```

Under `## 最低验证与审计检查点`, replace this bullet:

```markdown
- 如果当前文件是计划产物，作者逻辑是否已经回到 `*Behavior.kt` 或其他明确的手写领域文件，而不是继续堆在 plan-managed 文件里。
```

with:

```markdown
- 如果当前文件是计划产物，作者逻辑是否已经回到 `*Behavior.kt` 或其他明确的手写领域文件；生命周期逻辑是否优先落在 `onCreate`、`onUpdate`、`onDelete` 行为扩展里，而不是继续堆在 plan-managed 文件里。
```

- [ ] **Step 2: Update generation boundary guidance**

In `docs/public/authoring/generation-boundaries.md`, replace the matrix row:

```markdown
| aggregate `*Behavior.kt` | 手写补充点 | 这是当前明确留给作者补聚合行为的 checked-in scaffold，计划里固定使用 `ConflictPolicy.SKIP` |
```

with:

```markdown
| aggregate `*Behavior.kt` | 手写补充点 | 这是当前明确留给作者补聚合行为的 checked-in scaffold，计划里固定使用 `ConflictPolicy.SKIP`，默认带出 `onCreate` / `onUpdate` / `onDelete` 生命周期行为扩展骨架 |
```

In the `当前 checked-in aggregate 文件合同` table, replace:

```markdown
| `aggregate/behavior.kt.peb` | 明确的作者维护补充点；固定 `ConflictPolicy.SKIP`，生成后可以在文件内补聚合行为 |
```

with:

```markdown
| `aggregate/behavior.kt.peb` | 明确的作者维护补充点；固定 `ConflictPolicy.SKIP`，生成后可以在文件内补聚合行为，包括 `onCreate`、`onUpdate`、`onDelete` 生命周期扩展 |
```

- [ ] **Step 3: Update code generation guide**

In `docs/public/authoring/generator/code-generation.md`, replace this sentence:

```markdown
一个关键例子是 aggregate 默认行为骨架：`aggregate/behavior.kt.peb` 会以 checked-in source 形式进入 `src/main/kotlin/.../<AggregateRootName>Behavior.kt`。这类文件是明确留给作者补业务行为的文件；而大量 aggregate 主体骨架则会通过 `GENERATED_SOURCE` 进入模块本地 `build/generated/cap4k/main/kotlin`。如果你不读 `outputKind`、`resolvedOutputRoot` 和 `conflictPolicy`，很容易把“输出根位置”误判成“作者是否可以直接改”。
```

with:

```markdown
一个关键例子是 aggregate 默认行为骨架：`aggregate/behavior.kt.peb` 会以 checked-in source 形式进入 `src/main/kotlin/.../<AggregateRootName>Behavior.kt`，并默认带出 `onCreate`、`onUpdate`、`onDelete` 生命周期行为扩展骨架。这类文件是明确留给作者补业务行为的文件；而大量 aggregate 主体骨架则会通过 `GENERATED_SOURCE` 进入模块本地 `build/generated/cap4k/main/kotlin`。如果你不读 `outputKind`、`resolvedOutputRoot` 和 `conflictPolicy`，很容易把“输出根位置”误判成“作者是否可以直接改”。
```

Replace the `behavior` row in the checked-in family table:

```markdown
| `behavior` | 聚合根行为补充点 | 固定 `SKIP` | 这是明确的作者维护文件；补聚合行为就在这里 |
```

with:

```markdown
| `behavior` | 聚合根行为补充点 | 固定 `SKIP` | 这是明确的作者维护文件；聚合行为和生命周期扩展就在这里 |
```

- [ ] **Step 4: Verify docs mention lifecycle behavior without broad feature claims**

Run:

```powershell
rg -n "onCreate|onUpdate|onDelete|生命周期行为扩展|Behavior.kt" docs/public/authoring/domain.md docs/public/authoring/generation-boundaries.md docs/public/authoring/generator/code-generation.md
```

Expected: output includes only the narrow authoring guidance above. It must not claim new DSL options, annotations, runtime services, or entity template overrides.

- [ ] **Step 5: Commit docs changes**

Run:

```powershell
git add docs/public/authoring/domain.md `
  docs/public/authoring/generation-boundaries.md `
  docs/public/authoring/generator/code-generation.md
git commit -m "docs: document aggregate lifecycle behavior hooks"
```

### Task 5: Final Verification

**Files:**
- Verify: runtime, renderer, aggregate planner, docs

- [ ] **Step 1: Run focused runtime tests**

Run:

```powershell
.\gradlew.bat :ddd-core:test --tests "com.only4.cap4k.ddd.core.domain.repo.impl.DefaultEntityInlinePersistListenerTest"
```

Expected: BUILD SUCCESSFUL.

- [ ] **Step 2: Run focused renderer tests**

Run:

```powershell
.\gradlew.bat :cap4k-plugin-pipeline-renderer-pebble:test --tests "com.only4.cap4k.plugin.pipeline.renderer.pebble.PebbleArtifactRendererTest.aggregate behavior template renders checked in scaffold without generated business body"
```

Expected: BUILD SUCCESSFUL.

- [ ] **Step 3: Run aggregate planner ownership regression**

Run:

```powershell
.\gradlew.bat :cap4k-plugin-pipeline-generator-aggregate:test --tests "com.only4.cap4k.plugin.pipeline.generator.aggregate.AggregateArtifactPlannerTest.aggregate planner emits checked in behavior scaffold per aggregate root only"
```

Expected: BUILD SUCCESSFUL.

- [ ] **Step 4: Run diff hygiene**

Run:

```powershell
git diff --check
```

Expected: no output.

- [ ] **Step 5: Inspect changed files**

Run:

```powershell
git status --short
git diff --stat
```

Expected: only files listed in this plan are changed.

- [ ] **Step 6: Update issue lifecycle after merge**

After implementation is merged to `master`, update `https://github.com/LDmoxeii/cap4k/issues/38`:

```markdown
- [x] spec written
- [x] plan written
- [x] implementation merged
```

Add a comment with the merge commit, verification commands, and a note that downstream reference-project adoption remains outside this issue.
