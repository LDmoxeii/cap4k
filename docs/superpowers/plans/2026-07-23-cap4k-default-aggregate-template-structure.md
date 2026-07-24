# cap4k Default Aggregate Template Structure Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Implement the Phase 3.5 default aggregate entity structure baseline: `OwnedEntityList` runtime facade, private JPA owned relation backing collections, owned-one setter delegation, and compile verification without create-time ID assignment.

**Architecture:** Keep the canonical pipeline boundary intact: canonical/core inference continues to describe domain aggregate relations, generator planning derives render-only backing names and imports, Pebble templates render the new Kotlin/JPA shape, and ddd-core owns the small runtime facade. This plan deliberately excludes generated schema relation join recovery; that bug is deferred to Phase 3.625 while this phase preserves public-vs-persistence naming data needed by that later slice.

**Tech Stack:** Kotlin, Gradle 8.13, JUnit 5, MockK, Spring Data JPA, Hibernate/Jakarta Persistence annotations, Pebble templates, cap4k fixed-stage pipeline.

## Global Constraints

- Source spec copied into this worktree: `docs/superpowers/specs/2026-07-23-cap4k-default-aggregate-template-structure-design.md`.
- Keep the current JPA annotation-based persistence model.
- Keep generated entities as JPA-managed domain objects.
- Keep handwritten behavior in `behavior.kt` extension functions.
- Replace public raw `MutableList` owned-many relations with `OwnedEntityList`.
- Route owned-one transient setters through the same owned-entity collection helper.
- Reserve a future hook for create-time ID assignment without implementing it here.
- Do not remove JPA annotations from generated entities in this phase.
- Do not introduce `orm.xml` or annotation-free JPA mapping in this phase.
- Do not introduce a separate persistence entity model.
- Do not implement create-time ID assignment in this phase.
- Do not move handwritten behavior from extension functions into generated entity member methods.
- Do not try to fully enforce aggregate invariants at the language level.
- Do not make `OwnedEntityList` the JPA persistent collection field itself.
- Do not restore or add generated schema relation join methods in this phase.
- Do not redesign value object, enum, schema, repository, or UoW contracts beyond what the structure baseline requires.
- Avoid changing JPA UoW persistence classification, repository read/write APIs, Phase 2 Strong ID model semantics, Phase 3 mediator identifier API, value object templates, or enum templates.
- Current main worktree has uncommitted spec edits; implementation work must stay in this isolated worktree on branch `plan/cap4k-default-aggregate-template-structure`.
- No GitHub issue was provided for this slice; `gh issue list --search "default aggregate template structure"` returned no matching issue in this checkout, so this plan is based on the copied spec.

---

## File Structure

- Create `ddd-core/src/main/kotlin/com/only4/cap4k/ddd/core/domain/aggregate/OwnedEntityList.kt`.
  Owns the runtime owned collection facade. It delegates reads to a JPA-managed `MutableList`, exposes only `add`, `remove`, `singleOrNull`, and `replace`, and performs no ID generation or UoW registration.
- Create `ddd-core/src/test/kotlin/com/only4/cap4k/ddd/core/domain/aggregate/OwnedEntityListTest.kt`.
  Unit tests the facade semantics independently from JPA and templates.
- Modify `cap4k-plugin-pipeline-generator-aggregate/src/main/kotlin/com/only4/cap4k/plugin/pipeline/generator/aggregate/AggregateRelationPlanning.kt`.
  Derives private backing collection names for owned `ONE_TO_MANY` relations and exposes render-only `domainName` and `persistencePathName` metadata while leaving canonical relation facts unchanged.
- Modify `cap4k-plugin-pipeline-generator-aggregate/src/test/kotlin/com/only4/cap4k/plugin/pipeline/generator/aggregate/AggregateArtifactPlannerTest.kt`.
  Updates owned relation render model expectations and verifies `OwnedEntityList` import planning.
- Modify `cap4k-plugin-pipeline-renderer-pebble/src/main/resources/presets/ddd-default/aggregate/entity.kt.peb`.
  Renders internal constructors, private owned backing collections, transient `OwnedEntityList` facades, and owned-one delegation through `OwnedEntityList`.
- Modify `cap4k-plugin-pipeline-renderer-pebble/src/test/kotlin/com/only4/cap4k/plugin/pipeline/renderer/pebble/PebbleArtifactRendererTest.kt`.
  Updates renderer assertions for owned-one, owned-many, internal constructors, imports, and absence of public raw owned `MutableList`.
- Modify `cap4k-plugin-pipeline-gradle/src/test/kotlin/com/only4/cap4k/plugin/pipeline/gradle/PipelinePluginFunctionalTest.kt`.
  Updates generated-output assertions in functional tests.
- Modify `cap4k-plugin-pipeline-gradle/src/test/kotlin/com/only4/cap4k/plugin/pipeline/gradle/PipelinePluginCompileFunctionalTest.kt`.
  Updates compile functional assertions and strengthens handwritten behavior compile smoke for `OwnedEntityList.add/remove` and owned-one assignment.
- Modify `cap4k-plugin-pipeline-gradle/src/test/resources/functional/aggregate-relation-compile-sample/demo-domain/src/main/kotlin/com/acme/demo/domain/aggregates/video_post/AggregateRelationCompileSmoke.kt`.
  Adds compile smoke usage proving `OwnedEntityList` remains list-readable and exposes controlled mutation.
- Keep `cap4k-plugin-pipeline-renderer-pebble/src/main/resources/presets/ddd-default/aggregate/schema.kt.peb` unchanged except if import fallout appears. This phase must not add schema join methods.

---

### Task 1: Add `OwnedEntityList` Runtime Facade

**Files:**
- Create: `ddd-core/src/main/kotlin/com/only4/cap4k/ddd/core/domain/aggregate/OwnedEntityList.kt`
- Create: `ddd-core/src/test/kotlin/com/only4/cap4k/ddd/core/domain/aggregate/OwnedEntityListTest.kt`

**Interfaces:**
- Consumes: ordinary JPA-managed `MutableList<E>` backing collections rendered by later template tasks.
- Produces: `OwnedEntityList.of(delegate: MutableList<E>, entityType: KClass<E>, path: String): OwnedEntityList<E>`, `add(entity: E): Boolean`, `remove(entity: E): Boolean`, `singleOrNull(): E?`, `replace(value: E?)`.

- [ ] **Step 1: Write the failing unit tests**

Create `ddd-core/src/test/kotlin/com/only4/cap4k/ddd/core/domain/aggregate/OwnedEntityListTest.kt`:

```kotlin
package com.only4.cap4k.ddd.core.domain.aggregate

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertSame
import org.junit.jupiter.api.Assertions.assertThrows
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class OwnedEntityListTest {

    @Test
    fun `add mutates delegate and read operations use same delegate`() {
        val delegate = mutableListOf<TestChild>()
        val children = OwnedEntityList.of(delegate, TestChild::class, "Parent.children")
        val child = TestChild("a")

        val added = children.add(child)

        assertTrue(added)
        assertEquals(listOf(child), delegate)
        assertEquals(1, children.size)
        assertSame(child, children[0])
    }

    @Test
    fun `remove mutates delegate without extra lifecycle side effects`() {
        val child = TestChild("a")
        val delegate = mutableListOf(child)
        val children = OwnedEntityList.of(delegate, TestChild::class, "Parent.children")

        val removed = children.remove(child)

        assertTrue(removed)
        assertTrue(delegate.isEmpty())
        assertTrue(children.isEmpty())
    }

    @Test
    fun `remove returns false when entity is absent`() {
        val delegate = mutableListOf(TestChild("a"))
        val children = OwnedEntityList.of(delegate, TestChild::class, "Parent.children")

        val removed = children.remove(TestChild("b"))

        assertFalse(removed)
        assertEquals(1, delegate.size)
    }

    @Test
    fun `singleOrNull returns null for empty delegate`() {
        val children = OwnedEntityList.of(mutableListOf<TestChild>(), TestChild::class, "Parent.child")

        assertEquals(null, children.singleOrNull())
    }

    @Test
    fun `singleOrNull returns the only child`() {
        val child = TestChild("a")
        val children = OwnedEntityList.of(mutableListOf(child), TestChild::class, "Parent.child")

        assertSame(child, children.singleOrNull())
    }

    @Test
    fun `singleOrNull fails when delegate has more than one child`() {
        val children = OwnedEntityList.of(
            mutableListOf(TestChild("a"), TestChild("b")),
            TestChild::class,
            "Parent.child",
        )

        val error = assertThrows(IllegalStateException::class.java) {
            children.singleOrNull()
        }

        assertEquals(
            "owned relation Parent.child expected at most one TestChild but found 2",
            error.message,
        )
    }

    @Test
    fun `replace clears delegate for null`() {
        val delegate = mutableListOf(TestChild("a"))
        val children = OwnedEntityList.of(delegate, TestChild::class, "Parent.child")

        children.replace(null)

        assertTrue(delegate.isEmpty())
    }

    @Test
    fun `replace non null clears old child and uses add path`() {
        val old = TestChild("old")
        val new = TestChild("new")
        val delegate = mutableListOf(old)
        val children = OwnedEntityList.of(delegate, TestChild::class, "Parent.child")

        children.replace(new)

        assertEquals(listOf(new), delegate)
        assertSame(new, children.singleOrNull())
    }

    @Test
    fun `replace fails before changing malformed multi child delegate`() {
        val first = TestChild("a")
        val second = TestChild("b")
        val replacement = TestChild("c")
        val delegate = mutableListOf(first, second)
        val children = OwnedEntityList.of(delegate, TestChild::class, "Parent.child")

        val error = assertThrows(IllegalStateException::class.java) {
            children.replace(replacement)
        }

        assertEquals(
            "owned relation Parent.child expected at most one TestChild but found 2",
            error.message,
        )
        assertEquals(listOf(first, second), delegate)
    }

    private data class TestChild(val value: String)
}
```

- [ ] **Step 2: Run the focused test and verify it fails**

Run:

```powershell
.\gradlew.bat :ddd-core:test --tests "com.only4.cap4k.ddd.core.domain.aggregate.OwnedEntityListTest" --console=plain
```

Expected: FAIL during `compileTestKotlin` because `OwnedEntityList` is unresolved.

- [ ] **Step 3: Add the minimal runtime facade**

Create `ddd-core/src/main/kotlin/com/only4/cap4k/ddd/core/domain/aggregate/OwnedEntityList.kt`:

```kotlin
package com.only4.cap4k.ddd.core.domain.aggregate

import kotlin.reflect.KClass

class OwnedEntityList<E : Any> internal constructor(
    private val delegate: MutableList<E>,
    private val entityType: KClass<E>,
    private val path: String,
) : List<E> by delegate {

    fun add(entity: E): Boolean = delegate.add(entity)

    fun remove(entity: E): Boolean = delegate.remove(entity)

    fun singleOrNull(): E? =
        when (delegate.size) {
            0 -> null
            1 -> delegate[0]
            else -> malformedSingleRelation()
        }

    fun replace(value: E?) {
        if (delegate.size > 1) {
            malformedSingleRelation()
        }
        delegate.clear()
        if (value != null) {
            add(value)
        }
    }

    private fun malformedSingleRelation(): Nothing =
        error("owned relation $path expected at most one ${entityType.simpleName} but found ${delegate.size}")

    companion object {
        fun <E : Any> of(
            delegate: MutableList<E>,
            entityType: KClass<E>,
            path: String,
        ): OwnedEntityList<E> =
            OwnedEntityList(delegate, entityType, path)
    }
}
```

- [ ] **Step 4: Run the focused test and verify it passes**

Run:

```powershell
.\gradlew.bat :ddd-core:test --tests "com.only4.cap4k.ddd.core.domain.aggregate.OwnedEntityListTest" --console=plain
```

Expected: PASS and `BUILD SUCCESSFUL`.

- [ ] **Step 5: Commit the runtime facade**

```powershell
git status --short
git add ddd-core/src/main/kotlin/com/only4/cap4k/ddd/core/domain/aggregate/OwnedEntityList.kt ddd-core/src/test/kotlin/com/only4/cap4k/ddd/core/domain/aggregate/OwnedEntityListTest.kt
git commit -m "feat: add owned entity list facade"
```

---

### Task 2: Derive Owned Relation Backing Names In The Planner

**Files:**
- Modify: `cap4k-plugin-pipeline-generator-aggregate/src/main/kotlin/com/only4/cap4k/plugin/pipeline/generator/aggregate/AggregateRelationPlanning.kt`
- Modify: `cap4k-plugin-pipeline-generator-aggregate/src/test/kotlin/com/only4/cap4k/plugin/pipeline/generator/aggregate/AggregateArtifactPlannerTest.kt`

**Interfaces:**
- Consumes: `AggregateRelationModel.fieldName`, `owned`, `relationType`, `ownedCardinality`, `backingCollectionName`, `singleAccessorName`.
- Produces per relation render map keys: `name` as the canonical relation field name, `domainName` as the public domain/member/API name, `persistencePathName` as the JPA persistent path name, `backingCollectionName` with a leading underscore for owned `ONE_TO_MANY`, and unchanged ordinary relation metadata.
- Produces entity import `com.only4.cap4k.ddd.core.domain.aggregate.OwnedEntityList` when the entity has at least one owned `ONE_TO_MANY` relation.
- Produces JPA import `jakarta.persistence.Transient` when the entity has any owned `ONE_TO_MANY` facade relation.

- [ ] **Step 1: Update planner tests first**

In `AggregateArtifactPlannerTest.kt`, update the existing owned-one relation planner assertion around the test that currently expects `backingCollectionName = "files"` so it asserts both public and backing names:

```kotlin
assertEquals("files", relation["name"])
assertEquals("file", relation["domainName"])
assertEquals("_files", relation["backingCollectionName"])
assertEquals("_files", relation["persistencePathName"])
assertEquals("file", relation["singleAccessorName"])
```

In the existing owned-many planner assertion that currently expects `backingCollectionName = "items"`, replace that assertion block with:

```kotlin
assertEquals("items", relation["name"])
assertEquals("items", relation["domainName"])
assertEquals("_items", relation["backingCollectionName"])
assertEquals("_items", relation["persistencePathName"])
assertEquals(null, relation["singleAccessorName"])
```

In the same owned-many planner test, assert that the entity plan imports `OwnedEntityList` and `Transient`. Use the existing `entityItem` and `jpaImports` variables or add them if the test does not already expose them:

```kotlin
@Suppress("UNCHECKED_CAST")
val imports = entityItem.context["imports"] as List<String>
@Suppress("UNCHECKED_CAST")
val jpaImports = entityItem.context["jpaImports"] as List<String>

assertTrue(imports.contains("com.only4.cap4k.ddd.core.domain.aggregate.OwnedEntityList"))
assertTrue(jpaImports.contains("jakarta.persistence.Transient"))
```

- [ ] **Step 2: Run planner tests and verify they fail**

Run:

```powershell
.\gradlew.bat :cap4k-plugin-pipeline-generator-aggregate:test --tests "com.only4.cap4k.plugin.pipeline.generator.aggregate.AggregateArtifactPlannerTest" --console=plain
```

Expected: FAIL because relation maps still expose `files/items` as backing names, do not expose `domainName` or `persistencePathName`, and do not import `OwnedEntityList`/`Transient` for owned-many.

- [ ] **Step 3: Update relation planning**

Modify `AggregateRelationPlanning.kt` with these concrete changes.

Add constants/helpers near the top of `AggregateRelationPlanning`:

```kotlin
private const val OWNED_ENTITY_LIST_FQN = "com.only4.cap4k.ddd.core.domain.aggregate.OwnedEntityList"

private fun privateBackingCollectionName(name: String): String =
    if (name.startsWith("_")) name else "_$name"
```

Inside `planFor`, after `targetPackagesByType` is computed, add:

```kotlin
val hasOwnedCollectionFacadeRelations = entityRelations.any {
    it.owned && it.relationType == AggregateRelationType.ONE_TO_MANY
}
```

Inside `ownerRelationFields = entityRelations.map { relation -> ... }`, compute the backing and persistence names before `mapOf(...)`:

```kotlin
val backingCollectionName = when {
    relation.owned && relation.relationType == AggregateRelationType.ONE_TO_MANY ->
        privateBackingCollectionName(relation.backingCollectionName ?: relation.fieldName)
    else -> relation.backingCollectionName
}
val persistencePathName = when {
    relation.owned && relation.relationType == AggregateRelationType.ONE_TO_MANY ->
        requireNotNull(backingCollectionName) {
            "owned relation ${relation.ownerEntityPackageName}.${relation.ownerEntityName}.${relation.fieldName} requires a backing collection name"
        }
    else -> relation.fieldName
}
val domainName = when {
    relation.owned &&
        relation.relationType == AggregateRelationType.ONE_TO_MANY &&
        relation.ownedCardinality == OwnedRelationCardinality.ONE ->
        requireNotNull(relation.singleAccessorName) {
            "owned one relation ${relation.ownerEntityPackageName}.${relation.ownerEntityName}.${relation.fieldName} requires a single accessor name"
        }
    else -> relation.fieldName
}
```

Then ensure the owner relation map contains these keys:

```kotlin
"name" to relation.fieldName,
"domainName" to domainName,
"persistencePathName" to persistencePathName,
"targetType" to relation.targetEntityName,
"targetTypeRef" to targetTypeRef,
"targetPackageName" to relation.targetEntityPackageName,
"relationType" to relation.relationType.name,
"fetchType" to relation.fetchType.name,
"joinColumn" to relation.joinColumn,
"nullable" to relation.nullable,
"cascadeTypes" to relation.cascadeTypes.map { it.name },
"orphanRemoval" to relation.orphanRemoval,
"joinColumnNullable" to relation.joinColumnNullable,
"owned" to relation.owned,
"parentRefColumn" to relation.parentRefColumn,
"ownedCardinality" to relation.ownedCardinality?.name,
"persistenceShape" to relation.persistenceShape?.name,
"backingCollectionName" to backingCollectionName,
"singleAccessorName" to relation.singleAccessorName,
```

Add `domainName` and `persistencePathName` to inverse relation maps as public path aliases, keeping their existing relation behavior:

```kotlin
"name" to relation.fieldName,
"domainName" to relation.fieldName,
"persistencePathName" to relation.fieldName,
```

Replace the current `imports` computation with an explicit target-import list plus the runtime facade import:

```kotlin
val relationTargetImports = (entityRelations.map {
    it.targetEntityName to it.targetEntityPackageName
} + entityInverseRelations.map {
    it.targetEntityName to it.targetEntityPackageName
})
    .mapNotNull { relation ->
        val targetEntityName = relation.first
        val targetEntityPackageName = relation.second
        val targetPackages = targetPackagesByType.getValue(targetEntityName)
        if (targetEntityPackageName != entity.packageName && targetPackages.size == 1) {
            "$targetEntityPackageName.$targetEntityName"
        } else {
            null
        }
    }
val imports = buildList {
    addAll(relationTargetImports)
    if (hasOwnedCollectionFacadeRelations) {
        add(OWNED_ENTITY_LIST_FQN)
    }
}.distinct()
```

Replace the current `hasOwnedOneRelations` import gate with the broader facade gate:

```kotlin
if (hasOwnedCollectionFacadeRelations) {
    add("jakarta.persistence.Transient")
}
```

- [ ] **Step 4: Run planner tests and verify they pass**

Run:

```powershell
.\gradlew.bat :cap4k-plugin-pipeline-generator-aggregate:test --tests "com.only4.cap4k.plugin.pipeline.generator.aggregate.AggregateArtifactPlannerTest" --console=plain
```

Expected: PASS and `BUILD SUCCESSFUL`.

- [ ] **Step 5: Commit planner render-model changes**

```powershell
git status --short
git add cap4k-plugin-pipeline-generator-aggregate/src/main/kotlin/com/only4/cap4k/plugin/pipeline/generator/aggregate/AggregateRelationPlanning.kt cap4k-plugin-pipeline-generator-aggregate/src/test/kotlin/com/only4/cap4k/plugin/pipeline/generator/aggregate/AggregateArtifactPlannerTest.kt
git commit -m "feat: expose owned relation backing names"
```

---

### Task 3: Render Owned Relations Through `OwnedEntityList`

**Files:**
- Modify: `cap4k-plugin-pipeline-renderer-pebble/src/main/resources/presets/ddd-default/aggregate/entity.kt.peb`
- Modify: `cap4k-plugin-pipeline-renderer-pebble/src/test/kotlin/com/only4/cap4k/plugin/pipeline/renderer/pebble/PebbleArtifactRendererTest.kt`

**Interfaces:**
- Consumes: planner keys `relation.name`, `relation.domainName`, `relation.backingCollectionName`, `relation.singleAccessorName`, `relation.targetTypeRef`, `relation.owned`, `relation.ownedCardinality`.
- Produces: generated classes with `internal constructor`, private `MutableList` backing fields for owned relations, transient `OwnedEntityList<T>` owned-many facades, and transient nullable owned-one properties backed by `OwnedEntityList.singleOrNull/replace`.

- [ ] **Step 1: Update renderer tests first**

In `PebbleArtifactRendererTest.kt`, update the owned-one test context so the relation map uses the new backing name and imports:

```kotlin
"jpaImports" to listOf(
    "jakarta.persistence.FetchType",
    "jakarta.persistence.JoinColumn",
    "jakarta.persistence.CascadeType",
    "jakarta.persistence.OneToMany",
    "jakarta.persistence.Transient",
),
"imports" to listOf(
    "com.acme.demo.domain.aggregates.video_post.VideoPostFile",
    "com.only4.cap4k.ddd.core.domain.aggregate.OwnedEntityList",
),
...
"name" to "files",
"domainName" to "file",
"persistencePathName" to "_files",
"backingCollectionName" to "_files",
```

Replace the owned-one assertions with:

```kotlin
assertReadableKotlin(content)
assertTrue(content.contains("import jakarta.persistence.Transient"))
assertTrue(content.contains("import com.only4.cap4k.ddd.core.domain.aggregate.OwnedEntityList"))
assertTrue(content.contains("class VideoPost internal constructor("))
assertTrue(content.contains("@OneToMany(fetch = FetchType.LAZY, cascade = [CascadeType.PERSIST, CascadeType.MERGE, CascadeType.REMOVE], orphanRemoval = true)"))
assertTrue(content.contains("@JoinColumn(name = \"video_post_id\", nullable = false)"))
assertTrue(content.contains("private var _files: MutableList<VideoPostFile> = mutableListOf()"))
assertFalse(content.normalizedLineEndings().contains("\n    val files: MutableList<VideoPostFile> = mutableListOf()"))
assertTrue(content.contains("@get:Transient"))
assertTrue(content.contains("var file: VideoPostFile?"))
assertTrue(content.contains("get() = OwnedEntityList.of(_files, VideoPostFile::class, \"VideoPost.file\")"))
assertTrue(content.contains(".singleOrNull()"))
assertTrue(content.contains("set(value)"))
assertTrue(content.contains("OwnedEntityList.of(_files, VideoPostFile::class, \"VideoPost.file\")"))
assertTrue(content.contains(".replace(value)"))
assertFalse(content.contains("_files.clear()"))
assertFalse(content.contains("_files.add(value)"))
```

Rename the test `aggregate entity template keeps owned many as public mutable list` to:

```kotlin
fun `aggregate entity template renders owned many as private backing collection plus facade`() {
```

Update its context imports and relation map:

```kotlin
"jpaImports" to listOf(
    "jakarta.persistence.FetchType",
    "jakarta.persistence.JoinColumn",
    "jakarta.persistence.CascadeType",
    "jakarta.persistence.OneToMany",
    "jakarta.persistence.Transient",
),
"imports" to listOf(
    "com.acme.demo.domain.aggregates.video_post.VideoPostItem",
    "com.only4.cap4k.ddd.core.domain.aggregate.OwnedEntityList",
),
...
"name" to "items",
"domainName" to "items",
"persistencePathName" to "_items",
"backingCollectionName" to "_items",
```

Replace its assertions with:

```kotlin
assertReadableKotlin(content)
assertTrue(content.contains("import jakarta.persistence.Transient"))
assertTrue(content.contains("import com.only4.cap4k.ddd.core.domain.aggregate.OwnedEntityList"))
assertTrue(content.contains("class VideoPost internal constructor("))
assertTrue(content.contains("private var _items: MutableList<VideoPostItem> = mutableListOf()"))
assertTrue(content.contains("val items: OwnedEntityList<VideoPostItem>"))
assertTrue(content.contains("get() = OwnedEntityList.of(_items, VideoPostItem::class, \"VideoPost.items\")"))
assertFalse(content.normalizedLineEndings().contains("\n    val items: MutableList<VideoPostItem> = mutableListOf()"))
assertFalse(content.contains("private val items: MutableList<VideoPostItem>"))
assertFalse(content.contains("var item: VideoPostItem?"))
```

In the aggregate entity preset relation controls test around the `bodySection.contains("val items: MutableList<VideoPostItem> = mutableListOf()")` assertion, change the test relation context for `items` to include:

```kotlin
"owned" to true,
"parentRefColumn" to "video_post_id",
"ownedCardinality" to "MANY",
"persistenceShape" to "ONE_TO_MANY_JOIN_COLUMN",
"domainName" to "items",
"persistencePathName" to "_items",
"backingCollectionName" to "_items",
"singleAccessorName" to null,
```

Replace that assertion with:

```kotlin
assertTrue(bodySection.contains("private var _items: MutableList<VideoPostItem> = mutableListOf()"))
assertTrue(bodySection.contains("val items: OwnedEntityList<VideoPostItem>"))
assertTrue(bodySection.contains("OwnedEntityList.of(_items, VideoPostItem::class, \"VideoPost.items\")"))
assertFalse(bodySection.contains("val items: MutableList<VideoPostItem> = mutableListOf()"))
```

Update the existing constructor-shape assertions in the same test file so the global constructor template change is reflected everywhere:

```kotlin
assertTrue(content.contains("class Category internal constructor("))
assertTrue(content.normalizedLineEndings().contains("class Category internal constructor(\n    id: Long = 0L,"))
assertFalse(content.normalizedLineEndings().contains("class Category(\n    id: Long = 0L,"))
assertFalse(content.contains("data class Category("))
```

In these aggregate entity renderer tests, replace the constructor assertion shown below:

- `aggregate entity preset renders bounded relation-side jpa controls`
- `aggregate entity preset renders bounded Jakarta baseline annotations`

```kotlin
assertTrue(content.contains("class VideoPost("))
```

replace the assertion with:

```kotlin
assertTrue(content.contains("class VideoPost internal constructor("))
```

Keep the adjacent `assertFalse(content.contains("data class VideoPost("))` assertions.

- [ ] **Step 2: Run renderer tests and verify they fail**

Run:

```powershell
.\gradlew.bat :cap4k-plugin-pipeline-renderer-pebble:test --tests "com.only4.cap4k.plugin.pipeline.renderer.pebble.PebbleArtifactRendererTest" --console=plain
```

Expected: FAIL because the template still renders public raw `MutableList` for owned-many, direct backing-list owned-one setters, and a public constructor.

- [ ] **Step 3: Update the entity template**

In `entity.kt.peb`, change the class declaration from:

```pebble
class {{ typeName }}(
```

to:

```pebble
class {{ typeName }} internal constructor(
```

Replace the current `ONE_TO_MANY` rendering branch with this branch:

```pebble
{% elseif relation.relationType == "ONE_TO_MANY" %}    @OneToMany(fetch = FetchType.{{ relation.fetchType }}{% if relation.cascadeTypes|length > 0 %}, cascade = [{% for cascadeType in relation.cascadeTypes %}CascadeType.{{ cascadeType }}{% if not loop.last %}, {% endif %}{% endfor %}]{% endif %}, orphanRemoval = {{ relation.orphanRemoval }})
    @JoinColumn(name = "{{ relation.joinColumn }}", nullable = {{ relation.joinColumnNullable }})
{% if relation.owned %}    private var {{ relation.backingCollectionName }}: MutableList<{{ relation.targetTypeRef }}> = mutableListOf()

{% if relation.ownedCardinality == "ONE" %}    @get:Transient
    var {{ relation.singleAccessorName }}: {{ relation.targetTypeRef }}?
        get() = OwnedEntityList.of({{ relation.backingCollectionName }}, {{ relation.targetTypeRef }}::class, "{{ typeName }}.{{ relation.domainName }}")
            .singleOrNull()
        set(value) {
            OwnedEntityList.of({{ relation.backingCollectionName }}, {{ relation.targetTypeRef }}::class, "{{ typeName }}.{{ relation.domainName }}")
                .replace(value)
        }
{% else %}    @get:Transient
    val {{ relation.name }}: OwnedEntityList<{{ relation.targetTypeRef }}>
        get() = OwnedEntityList.of({{ relation.backingCollectionName }}, {{ relation.targetTypeRef }}::class, "{{ typeName }}.{{ relation.domainName }}")
{% endif %}
{% else %}    val {{ relation.name }}: MutableList<{{ relation.targetTypeRef }}> = mutableListOf()
{% endif %}
```

Do not add schema relation join methods in this task. Do not change `factory.kt.peb`, `behavior.kt.peb`, `strong_id.kt.peb`, `enum.kt.peb`, or `value_object.kt.peb`.

- [ ] **Step 4: Run renderer tests and verify they pass**

Run:

```powershell
.\gradlew.bat :cap4k-plugin-pipeline-renderer-pebble:test --tests "com.only4.cap4k.plugin.pipeline.renderer.pebble.PebbleArtifactRendererTest" --console=plain
```

Expected: PASS and `BUILD SUCCESSFUL`.

- [ ] **Step 5: Commit renderer template changes**

```powershell
git status --short
git add cap4k-plugin-pipeline-renderer-pebble/src/main/resources/presets/ddd-default/aggregate/entity.kt.peb cap4k-plugin-pipeline-renderer-pebble/src/test/kotlin/com/only4/cap4k/plugin/pipeline/renderer/pebble/PebbleArtifactRendererTest.kt
git commit -m "feat: render owned relations through facade"
```

---

### Task 4: Update Gradle Functional Generated-Output Assertions

**Files:**
- Modify: `cap4k-plugin-pipeline-gradle/src/test/kotlin/com/only4/cap4k/plugin/pipeline/gradle/PipelinePluginFunctionalTest.kt`
- Modify: `cap4k-plugin-pipeline-gradle/src/test/kotlin/com/only4/cap4k/plugin/pipeline/gradle/PipelinePluginCompileFunctionalTest.kt`

**Interfaces:**
- Consumes: generated source shape from Task 3.
- Produces: functional-test assertions that verify generated aggregate entities use `internal constructor`, `OwnedEntityList`, private backing collections, and owned-one `replace(value)` delegation.

- [ ] **Step 1: Update `PipelinePluginFunctionalTest` expected root entity shape**

In `PipelinePluginFunctionalTest.kt`, inside `cap4kGenerate aligns owned direct parent bindings with scalar fk and read only inverse relation`, replace these old assertions:

```kotlin
assertTrue(rootEntityContent.contains("class VideoPost("))
assertTrue(rootEntityContent.contains("val items: MutableList<VideoPostItem> = mutableListOf()"))
assertTrue(rootEntityContent.contains("private val files: MutableList<VideoPostFile> = mutableListOf()"))
assertTrue(rootEntityContent.contains("get() = when (files.size)"))
assertTrue(rootEntityContent.contains("else -> error(\"owned relation VideoPost.file expected at most one VideoPostFile but found \" + files.size)"))
assertTrue(rootEntityContent.contains("files.clear()"))
assertTrue(rootEntityContent.contains("files.add(value)"))
```

with:

```kotlin
assertTrue(rootEntityContent.contains("class VideoPost internal constructor("))
assertTrue(rootEntityContent.contains("import com.only4.cap4k.ddd.core.domain.aggregate.OwnedEntityList"))
assertTrue(rootEntityContent.contains("private var _items: MutableList<VideoPostItem> = mutableListOf()"))
assertTrue(rootEntityContent.contains("val items: OwnedEntityList<VideoPostItem>"))
assertTrue(rootEntityContent.contains("get() = OwnedEntityList.of(_items, VideoPostItem::class, \"VideoPost.items\")"))
assertFalse(rootEntityContent.replace("\r\n", "\n").contains("\n    val items: MutableList<VideoPostItem> = mutableListOf()"))
assertTrue(rootEntityContent.contains("private var _files: MutableList<VideoPostFile> = mutableListOf()"))
assertFalse(rootEntityContent.replace("\r\n", "\n").contains("\n    val files: MutableList<VideoPostFile> = mutableListOf()"))
assertTrue(rootEntityContent.contains("get() = OwnedEntityList.of(_files, VideoPostFile::class, \"VideoPost.file\")"))
assertTrue(rootEntityContent.contains(".singleOrNull()"))
assertTrue(rootEntityContent.contains("OwnedEntityList.of(_files, VideoPostFile::class, \"VideoPost.file\")"))
assertTrue(rootEntityContent.contains(".replace(value)"))
assertFalse(rootEntityContent.contains("_files.clear()"))
assertFalse(rootEntityContent.contains("_files.add(value)"))
```

Keep the existing assertions for scalar reference IDs, read-only inverse relation fields, and absence of unsupported relation shapes.

In `cap4kGenerateSources writes only generated source and cap4kGenerate preserves behavior scaffold`, update the regenerated entity assertion from:

```kotlin
assertTrue(generatedEntityFile.readText().contains("class VideoPost("))
```

to:

```kotlin
assertTrue(generatedEntityFile.readText().contains("class VideoPost internal constructor("))
```

- [ ] **Step 2: Run the functional generated-output test and verify it fails before implementation**

Run:

```powershell
.\gradlew.bat :cap4k-plugin-pipeline-gradle:test --tests "com.only4.cap4k.plugin.pipeline.gradle.PipelinePluginFunctionalTest.cap4kGenerate aligns owned direct parent bindings with scalar fk and read only inverse relation" --tests "com.only4.cap4k.plugin.pipeline.gradle.PipelinePluginFunctionalTest.cap4kGenerateSources writes only generated source and cap4kGenerate preserves behavior scaffold" --console=plain
```

Expected: FAIL before Tasks 1-3 are implemented because generated `VideoPost` still exposes public raw owned-many `MutableList`, direct owned-one backing-list setter logic, and a public constructor.

- [ ] **Step 3: Update `PipelinePluginCompileFunctionalTest` generated source assertions**

In `PipelinePluginCompileFunctionalTest.kt`, inside `aggregate relation generation keeps owned direct parent bindings scalar plus read only inverse relation`, replace these old assertions:

```kotlin
assertTrue(generatedRootEntity.contains("private val files: MutableList<VideoPostFile> = mutableListOf()"))
assertTrue(generatedRootEntity.contains("var file: VideoPostFile?"))
assertTrue(generatedRootEntity.contains("@get:Transient"))
assertFalse(generatedRootEntity.replace("\r\n", "\n").contains("\n    val files: MutableList<VideoPostFile> = mutableListOf()"))
```

with:

```kotlin
assertTrue(generatedRootEntity.contains("class VideoPost internal constructor("))
assertTrue(generatedRootEntity.contains("import com.only4.cap4k.ddd.core.domain.aggregate.OwnedEntityList"))
assertTrue(generatedRootEntity.contains("private var _items: MutableList<VideoPostItem> = mutableListOf()"))
assertTrue(generatedRootEntity.contains("val items: OwnedEntityList<VideoPostItem>"))
assertTrue(generatedRootEntity.contains("get() = OwnedEntityList.of(_items, VideoPostItem::class, \"VideoPost.items\")"))
assertFalse(generatedRootEntity.replace("\r\n", "\n").contains("\n    val items: MutableList<VideoPostItem> = mutableListOf()"))
assertTrue(generatedRootEntity.contains("private var _files: MutableList<VideoPostFile> = mutableListOf()"))
assertTrue(generatedRootEntity.contains("var file: VideoPostFile?"))
assertTrue(generatedRootEntity.contains("@get:Transient"))
assertFalse(generatedRootEntity.replace("\r\n", "\n").contains("\n    val files: MutableList<VideoPostFile> = mutableListOf()"))
assertTrue(generatedRootEntity.contains("get() = OwnedEntityList.of(_files, VideoPostFile::class, \"VideoPost.file\")"))
assertTrue(generatedRootEntity.contains(".singleOrNull()"))
assertTrue(generatedRootEntity.contains("OwnedEntityList.of(_files, VideoPostFile::class, \"VideoPost.file\")"))
assertTrue(generatedRootEntity.contains(".replace(value)"))
assertFalse(generatedRootEntity.contains("_files.clear()"))
assertFalse(generatedRootEntity.contains("_files.add(value)"))
```

- [ ] **Step 4: Run the compile functional generated-source test and verify it fails before implementation**

Run:

```powershell
.\gradlew.bat :cap4k-plugin-pipeline-gradle:test --tests "com.only4.cap4k.plugin.pipeline.gradle.PipelinePluginCompileFunctionalTest.aggregate relation generation keeps owned direct parent bindings scalar plus read only inverse relation" --console=plain
```

Expected: FAIL before Tasks 1-3 are implemented because generated code still uses the old public collection shape.

- [ ] **Step 5: Run the Task 4 tests after Tasks 1-3 and verify they pass**

Run:

```powershell
.\gradlew.bat :cap4k-plugin-pipeline-gradle:test --tests "com.only4.cap4k.plugin.pipeline.gradle.PipelinePluginFunctionalTest.cap4kGenerate aligns owned direct parent bindings with scalar fk and read only inverse relation" --tests "com.only4.cap4k.plugin.pipeline.gradle.PipelinePluginFunctionalTest.cap4kGenerateSources writes only generated source and cap4kGenerate preserves behavior scaffold" --tests "com.only4.cap4k.plugin.pipeline.gradle.PipelinePluginCompileFunctionalTest.aggregate relation generation keeps owned direct parent bindings scalar plus read only inverse relation" --console=plain
```

Expected: PASS and `BUILD SUCCESSFUL`.

- [ ] **Step 6: Commit Gradle functional assertion updates**

```powershell
git status --short
git add cap4k-plugin-pipeline-gradle/src/test/kotlin/com/only4/cap4k/plugin/pipeline/gradle/PipelinePluginFunctionalTest.kt cap4k-plugin-pipeline-gradle/src/test/kotlin/com/only4/cap4k/plugin/pipeline/gradle/PipelinePluginCompileFunctionalTest.kt
git commit -m "test: verify generated owned relation facade shape"
```

---

### Task 5: Compile Smoke And Final Verification

**Files:**
- Modify: `cap4k-plugin-pipeline-gradle/src/test/resources/functional/aggregate-relation-compile-sample/demo-domain/src/main/kotlin/com/acme/demo/domain/aggregates/video_post/AggregateRelationCompileSmoke.kt`
- Read: `ddd-core/src/main/kotlin/com/only4/cap4k/ddd/core/domain/aggregate/OwnedEntityList.kt`
- Read: `cap4k-plugin-pipeline-generator-aggregate/src/main/kotlin/com/only4/cap4k/plugin/pipeline/generator/aggregate/AggregateRelationPlanning.kt`
- Read: `cap4k-plugin-pipeline-renderer-pebble/src/main/resources/presets/ddd-default/aggregate/entity.kt.peb`
- Read: `cap4k-plugin-pipeline-renderer-pebble/src/main/resources/presets/ddd-default/aggregate/schema.kt.peb`

**Interfaces:**
- Consumes: `VideoPost.items: OwnedEntityList<VideoPostItem>` and `VideoPost.file: VideoPostFile?` generated by Tasks 1-3.
- Produces: compile smoke proving handwritten same-module behavior can call `items.add(...)`, `items.remove(...)`, list read operations, and owned-one nullable assignment.

- [ ] **Step 1: Update the compile smoke source**

Replace `AggregateRelationCompileSmoke.kt` with:

```kotlin
package com.acme.demo.domain.aggregates.video_post

import com.acme.demo.domain.aggregates.user_profile.UserProfile

class AggregateRelationCompileSmoke {
    fun touch(entity: VideoPost, child: VideoPostItem, file: VideoPostFile, profile: UserProfile) {
        entity.items.add(child)
        entity.items.remove(child)
        entity.items.forEach { it.label }
        entity.items.firstOrNull()?.label
        entity.file = file
        entity.file?.storageKey
        entity.authorId
        entity.coverProfileId
        child.videoPost.id
        profile.id
    }
}
```

- [ ] **Step 2: Run the compile functional test after Tasks 1-4**

Run:

```powershell
.\gradlew.bat :cap4k-plugin-pipeline-gradle:test --tests "com.only4.cap4k.plugin.pipeline.gradle.PipelinePluginCompileFunctionalTest.aggregate relation generation keeps owned direct parent bindings scalar plus read only inverse relation" --console=plain
```

Expected: PASS and `BUILD SUCCESSFUL`; generated aggregate entities compile with handwritten usage of `OwnedEntityList.add`, `OwnedEntityList.remove`, `List` reads, and owned-one assignment.

- [ ] **Step 3: Verify schema template scope did not expand**

Run:

```powershell
rg -n "relationJoins|root\\.join|fun .*joinType|schemaRelations|JoinType" cap4k-plugin-pipeline-renderer-pebble/src/main/resources/presets/ddd-default/aggregate/schema.kt.peb
```

Expected: no matches. This confirms Phase 3.5 did not restore generated schema join methods.

- [ ] **Step 4: Run focused module tests**

Run:

```powershell
.\gradlew.bat :ddd-core:test --tests "com.only4.cap4k.ddd.core.domain.aggregate.OwnedEntityListTest" :cap4k-plugin-pipeline-generator-aggregate:test --tests "com.only4.cap4k.plugin.pipeline.generator.aggregate.AggregateArtifactPlannerTest" :cap4k-plugin-pipeline-renderer-pebble:test --tests "com.only4.cap4k.plugin.pipeline.renderer.pebble.PebbleArtifactRendererTest" --console=plain
```

Expected: PASS and `BUILD SUCCESSFUL`.

- [ ] **Step 5: Run focused Gradle plugin functional tests**

Run:

```powershell
.\gradlew.bat :cap4k-plugin-pipeline-gradle:test --tests "com.only4.cap4k.plugin.pipeline.gradle.PipelinePluginFunctionalTest.cap4kGenerate aligns owned direct parent bindings with scalar fk and read only inverse relation" --tests "com.only4.cap4k.plugin.pipeline.gradle.PipelinePluginFunctionalTest.cap4kGenerateSources writes only generated source and cap4kGenerate preserves behavior scaffold" --tests "com.only4.cap4k.plugin.pipeline.gradle.PipelinePluginCompileFunctionalTest.aggregate relation generation keeps owned direct parent bindings scalar plus read only inverse relation" --console=plain
```

Expected: PASS and `BUILD SUCCESSFUL`.

- [ ] **Step 6: Run the touched-module verification sweep**

Run:

```powershell
.\gradlew.bat :ddd-core:test :cap4k-plugin-pipeline-generator-aggregate:test :cap4k-plugin-pipeline-renderer-pebble:test :cap4k-plugin-pipeline-gradle:test --console=plain
```

Expected: PASS and `BUILD SUCCESSFUL`. If this command is too slow for the local machine, run Steps 4-5 and record that the broader sweep was skipped because of runtime cost.

If the broader sweep is skipped, also run this focused fallback so the spec-required factory, behavior extension, Strong ID, enum, and value-object compile surfaces are still verified:

```powershell
.\gradlew.bat :cap4k-plugin-pipeline-gradle:test --tests "com.only4.cap4k.plugin.pipeline.gradle.PipelinePluginCompileFunctionalTest.aggregate factory and specification generation participates in domain compileKotlin" --tests "com.only4.cap4k.plugin.pipeline.gradle.PipelinePluginCompileFunctionalTest.aggregate behavior source compiles against generated entities when module build dir is customized" --tests "com.only4.cap4k.plugin.pipeline.gradle.PipelinePluginCompileFunctionalTest.aggregate enum generation participates in domain compileKotlin" --console=plain
```

Expected: PASS and `BUILD SUCCESSFUL`; this fallback is not optional when Step 6 is skipped.

- [ ] **Step 7: Check final diff scope**

Run:

```powershell
git diff --name-only HEAD
```

Expected implementation and test files:

```text
ddd-core/src/main/kotlin/com/only4/cap4k/ddd/core/domain/aggregate/OwnedEntityList.kt
ddd-core/src/test/kotlin/com/only4/cap4k/ddd/core/domain/aggregate/OwnedEntityListTest.kt
cap4k-plugin-pipeline-generator-aggregate/src/main/kotlin/com/only4/cap4k/plugin/pipeline/generator/aggregate/AggregateRelationPlanning.kt
cap4k-plugin-pipeline-generator-aggregate/src/test/kotlin/com/only4/cap4k/plugin/pipeline/generator/aggregate/AggregateArtifactPlannerTest.kt
cap4k-plugin-pipeline-renderer-pebble/src/main/resources/presets/ddd-default/aggregate/entity.kt.peb
cap4k-plugin-pipeline-renderer-pebble/src/test/kotlin/com/only4/cap4k/plugin/pipeline/renderer/pebble/PebbleArtifactRendererTest.kt
cap4k-plugin-pipeline-gradle/src/test/kotlin/com/only4/cap4k/plugin/pipeline/gradle/PipelinePluginFunctionalTest.kt
cap4k-plugin-pipeline-gradle/src/test/kotlin/com/only4/cap4k/plugin/pipeline/gradle/PipelinePluginCompileFunctionalTest.kt
cap4k-plugin-pipeline-gradle/src/test/resources/functional/aggregate-relation-compile-sample/demo-domain/src/main/kotlin/com/acme/demo/domain/aggregates/video_post/AggregateRelationCompileSmoke.kt
```

The copied spec and this plan may also appear in a planning-only branch:

```text
docs/superpowers/specs/2026-07-23-cap4k-default-aggregate-template-structure-design.md
docs/superpowers/plans/2026-07-23-cap4k-default-aggregate-template-structure.md
```

No files under repository implementations, UoW persistence classification, mediator identifier generation, Strong ID templates, enum templates, value object templates, or `aggregate/schema.kt.peb` should appear unless a compile-only import fallout fix is recorded with evidence.

- [ ] **Step 8: Commit compile smoke and verification updates**

```powershell
git status --short
git add cap4k-plugin-pipeline-gradle/src/test/resources/functional/aggregate-relation-compile-sample/demo-domain/src/main/kotlin/com/acme/demo/domain/aggregates/video_post/AggregateRelationCompileSmoke.kt
git commit -m "test: compile owned relation facade usage"
```

## Self-Review

Spec coverage:

- Covered `OwnedEntityList` runtime facade in Task 1.
- Covered public owned-many facade and private JPA backing collection in Tasks 2-4.
- Covered owned-one nullable property shape and setter delegation through `OwnedEntityList.replace` in Tasks 3-4.
- Covered `internal constructor(...)` in Tasks 3-4.
- Covered global constructor assertion fallout by updating the additional renderer and Gradle functional assertions that still expected `class VideoPost(`/`class Category(`.
- Covered behavior extension compatibility with same-module `internal set` through the Task 5 fallback compile tests and covered owned relation mutation in Task 5 compile smoke.
- Covered representative Strong ID, enum, and value-object compile surfaces through the full Gradle plugin sweep or the mandatory focused fallback when that sweep is skipped.
- Covered schema/query naming boundary by preserving `domainName` and `persistencePathName` render metadata, using the owned-one single accessor as `domainName`, and forbidding schema join restoration in Task 5.
- Covered non-goals by final diff scope checks: no UoW classification changes, no repository API changes, no create-time ID assignment, no schema join methods, no value object or enum redesign.

Placeholder scan:

- The plan contains no placeholder markers, no unspecified file paths, and no test steps without commands.

Type consistency:

- `OwnedEntityList.of(delegate: MutableList<E>, entityType: KClass<E>, path: String)` is defined in Task 1 and used by the entity template in Task 3.
- `AggregateRelationPlanning` produces `domainName`, `persistencePathName`, `backingCollectionName`, and `singleAccessorName` in Task 2; Task 3 consumes those exact keys. Owned-one uses `name = "files"`, `domainName = "file"`, `backingCollectionName = "_files"`, and `singleAccessorName = "file"`.
- Generated `VideoPost.items` has type `OwnedEntityList<VideoPostItem>` in renderer tests, Gradle functional assertions, and compile smoke.
- Generated `VideoPost.file` remains `VideoPostFile?` in renderer tests, Gradle functional assertions, and compile smoke.

Plan complete and saved to `docs/superpowers/plans/2026-07-23-cap4k-default-aggregate-template-structure.md`. Two execution options:

**1. Subagent-Driven (recommended)** - dispatch a fresh subagent per task, review between tasks, fast iteration

**2. Inline Execution** - execute tasks in this session using executing-plans, batch execution with checkpoints
