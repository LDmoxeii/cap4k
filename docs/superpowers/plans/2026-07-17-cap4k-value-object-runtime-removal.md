# Cap4k ValueObject Runtime Removal Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Remove the legacy runtime `ValueObject<ID>` protocol and its JPA/UoW/predicate/md5-generator compatibility paths while preserving generator-side value-object support.

**Architecture:** Remove references from leaf runtime entry points first, then remove constructor/configuration compatibility, then delete the md5 generator, and only then delete the core interface. This keeps each task reviewable and avoids a single broad compile break across `ddd-core`, `ddd-domain-repo-jpa`, and `cap4k-ddd-starter`.

**Tech Stack:** Kotlin/JVM, Spring Boot auto-configuration, Spring Data JPA, Hibernate `IdentifierGenerator`, Gradle Kotlin DSL, JUnit 5, MockK, ripgrep static verification.

## Global Constraints

- Work only in `C:\Users\LD_moxeii\Documents\code\only-workspace\cap4k\.worktrees\value-object-runtime-removal` on branch `feature/value-object-runtime-removal`.
- Do not implement directly on `master`.
- Do not unify fastjson, Jackson, or Gson in this slice.
- Do not introduce a replacement hash-based generator.
- Do not change generator-side value-object manifests, templates, or the current `data class` + converter output.
- Do not remove `ddd-domain-repo-jpa` as a module.
- Do not add historical narration to `docs/public`; if a public page needs a wording update, describe only the current contract.
- Do not run compile, run, test, install, or dependency-install commands unless the user explicitly allows them in the implementation turn.
- Static verification commands such as `rg`, `git diff`, and file reads are allowed.
- Do not commit unless the user explicitly asks for commits in the implementation turn.

---

## File Structure

- `ddd-domain-repo-jpa/src/main/kotlin/com/only4/cap4k/ddd/domain/repo/JpaPredicate.kt`
  - Owns the concrete JPA predicate data carrier and factory methods.
  - After this slice it exposes entity class, ids, and specification only.

- `ddd-domain-repo-jpa/src/main/kotlin/com/only4/cap4k/ddd/domain/repo/JpaPredicateSupport.kt`
  - Keeps the existing resume helpers for ids, specifications, and entity class.
  - No production edit is expected here, but its tests need old value-object cases removed.

- `ddd-domain-repo-jpa/src/main/kotlin/com/only4/cap4k/ddd/application/JpaUnitOfWork.kt`
  - Owns JPA persistence orchestration.
  - After this slice it no longer detects or persists value objects as standalone identity objects.

- `cap4k-ddd-starter/src/main/kotlin/com/only4/cap4k/ddd/domain/repo/configure/JpaUnitOfWorkProperties.kt`
  - Owns public starter properties for JPA UoW.
  - Remove only old value-object/md5-generator properties.

- `cap4k-ddd-starter/src/main/kotlin/com/only4/cap4k/ddd/domain/repo/JpaRepositoryAutoConfiguration.kt`
  - Owns wiring for `JpaUnitOfWork`.
  - Remove old constructor argument and md5-generator configuration hook.

- `ddd-domain-repo-jpa/src/main/kotlin/com/only4/cap4k/ddd/domain/repo/Md5HashIdentifierGenerator.kt`
  - Delete.

- `ddd-core/src/main/kotlin/com/only4/cap4k/ddd/core/domain/aggregate/ValueObject.kt`
  - Delete after all downstream references are gone.

- `ddd-domain-repo-jpa/build.gradle.kts`
  - Remove `implementation(libs.fastjson)` after deleting `Md5HashIdentifierGenerator`.

- Tests under `ddd-domain-repo-jpa/src/test/kotlin/com/only4/cap4k/ddd/...`
  - Remove old value-object interface cases.
  - Keep ordinary entity, predicate, UoW, and application-side-id coverage.

---

### Task 1: Shrink `JpaPredicate` To Entity Ids And Specifications

**Files:**
- Modify: `ddd-domain-repo-jpa/src/main/kotlin/com/only4/cap4k/ddd/domain/repo/JpaPredicate.kt`
- Modify: `ddd-domain-repo-jpa/src/test/kotlin/com/only4/cap4k/ddd/domain/repo/JpaPredicateTest.kt`
- Modify: `ddd-domain-repo-jpa/src/test/kotlin/com/only4/cap4k/ddd/domain/repo/JpaPredicateSupportTest.kt`

**Interfaces:**
- Consumes: existing `JpaPredicate.byId(entityClass: Class<ENTITY>, id: Any)`, `JpaPredicate.byIds(entityClass: Class<ENTITY>, ids: Iterable<Any>)`, and `JpaPredicate.bySpecification(entityClass: Class<ENTITY>, specification: Specification<ENTITY>)`.
- Produces: `JpaPredicate<ENTITY>(entityClass: Class<ENTITY>, spec: Specification<ENTITY>? = null, ids: Iterable<Any>? = null)` with no `valueObject` field and no `byValueObject(...)` factory.

- [ ] **Step 1: Run the static pre-check**

Run:

```powershell
rg -n "ValueObject<|byValueObject|valueObject" ddd-domain-repo-jpa/src/main/kotlin/com/only4/cap4k/ddd/domain/repo ddd-domain-repo-jpa/src/test/kotlin/com/only4/cap4k/ddd/domain/repo
```

Expected before this task: matches in `JpaPredicate.kt`, `JpaPredicateTest.kt`, and `JpaPredicateSupportTest.kt`.

- [ ] **Step 2: Update `JpaPredicate.kt`**

Change `JpaPredicate.kt` to this shape:

```kotlin
package com.only4.cap4k.ddd.domain.repo

import com.only4.cap4k.ddd.core.domain.repo.Predicate
import org.springframework.data.jpa.domain.Specification

/**
 * Jpa仓储检索断言
 *
 * @author LD_moxeii
 * @date 2025/07/28
 */
class JpaPredicate<ENTITY : Any>(
    val entityClass: Class<ENTITY>,
    val spec: Specification<ENTITY>? = null,
    val ids: Iterable<Any>? = null
) : Predicate<ENTITY> {

    companion object {
        @JvmStatic
        fun <ENTITY : Any> byId(entityClass: Class<ENTITY>, id: Any): JpaPredicate<ENTITY> =
            JpaPredicate(entityClass, ids = listOf(id))

        @JvmStatic
        fun <ENTITY : Any> byIds(entityClass: Class<ENTITY>, ids: Iterable<Any>): JpaPredicate<ENTITY> =
            JpaPredicate(entityClass, ids = ids)

        @JvmStatic
        fun <ENTITY : Any> bySpecification(
            entityClass: Class<ENTITY>,
            specification: Specification<ENTITY>
        ): JpaPredicate<ENTITY> = JpaPredicate(entityClass, specification)
    }
}
```

- [ ] **Step 3: Update `JpaPredicateTest.kt`**

Remove:

```kotlin
import com.only4.cap4k.ddd.core.domain.aggregate.ValueObject
```

Remove the `TestValueObject` helper:

```kotlin
private class TestValueObject(private val value: String) : ValueObject<String> {
    override fun hash(): String = value.hashCode().toString()
}
```

Remove these tests completely:

```kotlin
fun `byValueObject should create predicate with value object`()
fun `byValueObject should handle different value object implementations`()
```

In the remaining tests, delete every assertion against `predicate.valueObject`.

Change `multiple factory methods should create distinct predicates` so it only compares id and specification predicates:

```kotlin
@Test
@DisplayName("多个工厂方法应该创建不同的谓词")
fun `multiple factory methods should create distinct predicates`() {
    val id = 1L
    val specification = mockk<Specification<TestEntity>>()

    val predicateById = JpaPredicate.byId(TestEntity::class.java, id)
    val predicateBySpec = JpaPredicate.bySpecification(TestEntity::class.java, specification)

    assertNotNull(predicateById.ids)
    assertNull(predicateById.spec)

    assertNull(predicateBySpec.ids)
    assertNotNull(predicateBySpec.spec)
}
```

- [ ] **Step 4: Update `JpaPredicateSupportTest.kt`**

Remove:

```kotlin
import com.only4.cap4k.ddd.core.domain.aggregate.ValueObject
```

Remove the `TestValueObject` helper:

```kotlin
class TestValueObject(private val value: String) : ValueObject<String> {
    override fun hash(): String = value
}
```

Remove these tests completely:

```kotlin
fun `test resumeId with ValueObject-based JpaPredicate`()
fun `test resumeIds with ValueObject-based JpaPredicate`()
fun `test reflectEntityClass with ValueObject-based JpaPredicate`()
```

- [ ] **Step 5: Static verification for Task 1**

Run:

```powershell
rg -n "byValueObject|valueObject" ddd-domain-repo-jpa/src/main/kotlin/com/only4/cap4k/ddd/domain/repo ddd-domain-repo-jpa/src/test/kotlin/com/only4/cap4k/ddd/domain/repo
```

Expected: no matches.

Run:

```powershell
rg -n "ValueObject<" ddd-domain-repo-jpa/src/main/kotlin/com/only4/cap4k/ddd/domain/repo ddd-domain-repo-jpa/src/test/kotlin/com/only4/cap4k/ddd/domain/repo
```

Expected: no matches.

- [ ] **Step 6: Optional focused tests if user permits tests**

Run only after explicit user permission:

```powershell
.\gradlew.bat :ddd-domain-repo-jpa:test --tests "com.only4.cap4k.ddd.domain.repo.JpaPredicateTest" --tests "com.only4.cap4k.ddd.domain.repo.JpaPredicateSupportTest"
```

Expected if tests are permitted: `BUILD SUCCESSFUL`.

- [ ] **Step 7: Commit gate**

Run only if the user asks for commits:

```powershell
git add ddd-domain-repo-jpa/src/main/kotlin/com/only4/cap4k/ddd/domain/repo/JpaPredicate.kt `
        ddd-domain-repo-jpa/src/test/kotlin/com/only4/cap4k/ddd/domain/repo/JpaPredicateTest.kt `
        ddd-domain-repo-jpa/src/test/kotlin/com/only4/cap4k/ddd/domain/repo/JpaPredicateSupportTest.kt
git commit -m "refactor: remove value object predicate support"
```

---

### Task 2: Remove ValueObject Handling From `JpaUnitOfWork`

**Files:**
- Modify: `ddd-domain-repo-jpa/src/main/kotlin/com/only4/cap4k/ddd/application/JpaUnitOfWork.kt`
- Modify: `cap4k-ddd-starter/src/main/kotlin/com/only4/cap4k/ddd/domain/repo/configure/JpaUnitOfWorkProperties.kt`
- Modify: `cap4k-ddd-starter/src/main/kotlin/com/only4/cap4k/ddd/domain/repo/JpaRepositoryAutoConfiguration.kt`
- Modify: `ddd-domain-repo-jpa/src/test/kotlin/com/only4/cap4k/ddd/application/JpaUnitOfWorkTest.kt`

**Interfaces:**
- Consumes: `JpaUnitOfWork(uowInterceptors, persistListenerManager, supportEntityInlinePersistListener, idStrategyRegistry = MapBackedIdStrategyRegistry(emptyList()))`.
- Produces: `JpaUnitOfWork` constructors without `supportValueObjectExistsCheckOnSave`.

- [ ] **Step 1: Run the static pre-check**

Run:

```powershell
rg -n "ValueObject<|supportValueObjectExistsCheckOnSave|isValueObjectAndExists|entity is ValueObject" ddd-domain-repo-jpa/src/main/kotlin/com/only4/cap4k/ddd/application cap4k-ddd-starter/src/main/kotlin/com/only4/cap4k/ddd/domain/repo ddd-domain-repo-jpa/src/test/kotlin/com/only4/cap4k/ddd/application
```

Expected before this task: matches in `JpaUnitOfWork.kt`, `JpaUnitOfWorkProperties.kt`, `JpaRepositoryAutoConfiguration.kt`, and `JpaUnitOfWorkTest.kt`.

- [ ] **Step 2: Update `JpaUnitOfWork` constructor shape**

Remove:

```kotlin
import com.only4.cap4k.ddd.core.domain.aggregate.ValueObject
```

Change the class header and secondary constructor to:

```kotlin
open class JpaUnitOfWork(
    private val uowInterceptors: List<UnitOfWorkInterceptor>,
    private val persistListenerManager: PersistListenerManager,
    private val supportEntityInlinePersistListener: Boolean,
    idStrategyRegistry: IdStrategyRegistry = MapBackedIdStrategyRegistry(emptyList()),
) : UnitOfWork {

    constructor(
        uowInterceptors: List<UnitOfWorkInterceptor>,
        persistListenerManager: PersistListenerManager,
        supportEntityInlinePersistListener: Boolean,
    ) : this(
        uowInterceptors,
        persistListenerManager,
        supportEntityInlinePersistListener,
        MapBackedIdStrategyRegistry(emptyList()),
    )
```

- [ ] **Step 3: Replace `isExists(...)` and delete `isValueObjectAndExists(...)`**

Delete this function:

```kotlin
private fun isValueObjectAndExists(entity: Any): Boolean {
    val valueObject = entity as? ValueObject<*> ?: return false
    val id = valueObject.hash()
    return entityManager.find(entity.javaClass, id) != null
}
```

Replace `isExists(...)` with:

```kotlin
private fun isExists(entity: Any): Boolean {
    val entityInformation = getEntityInformation(entity.javaClass)
    if (entityInformation.isNew(entity)) return false
    val id = entityInformation.getId(entity)
    return entityManager.find(entity.javaClass, id) != null
}
```

- [ ] **Step 4: Remove the value-object save branch**

In the `persistEntities` loop, remove this `when` branch:

```kotlin
// ValueObject 存在性检查
supportValueObjectExistsCheckOnSave && entity is ValueObject<*> -> {
    if (!isExists(entity)) {
        entityManager.persist(entity)
        results.created.add(entity)
    }
}
```

The next branch should be the application-side-id branch:

```kotlin
// 应用侧ID实体处理
applicationSideIdMember != null -> {
```

- [ ] **Step 5: Remove the starter value-object property**

In `JpaUnitOfWorkProperties.kt`, remove:

```kotlin
/**
 * 是否在保存时检查值对象是否存在
 */
var supportValueObjectExistsCheckOnSave: Boolean = true,
```

Keep `generalIdFieldName` for Task 3.

- [ ] **Step 6: Remove the constructor argument from auto-configuration**

In `JpaRepositoryAutoConfiguration.kt`, change the `JpaUnitOfWork` bean construction from:

```kotlin
JpaUnitOfWork(
    unitOfWorkInterceptors,
    persistListenerManager,
    jpaUnitOfWorkProperties.supportEntityInlinePersistListener,
    jpaUnitOfWorkProperties.supportValueObjectExistsCheckOnSave,
    idStrategyRegistry,
)
```

to:

```kotlin
JpaUnitOfWork(
    unitOfWorkInterceptors,
    persistListenerManager,
    jpaUnitOfWorkProperties.supportEntityInlinePersistListener,
    idStrategyRegistry,
)
```

- [ ] **Step 7: Update `JpaUnitOfWorkTest` helper and setup**

Remove:

```kotlin
import com.only4.cap4k.ddd.core.domain.aggregate.ValueObject
```

Change `TestableJpaUnitOfWork` constructor and super call to:

```kotlin
class TestableJpaUnitOfWork(
    uowInterceptors: List<UnitOfWorkInterceptor>,
    persistListenerManager: PersistListenerManager,
    supportEntityInlinePersistListener: Boolean,
    private val overridePersistenceContextEntities: Boolean = true,
    idStrategyRegistry: IdStrategyRegistry = MapBackedIdStrategyRegistry(emptyList()),
) : JpaUnitOfWork(
    uowInterceptors,
    persistListenerManager,
    supportEntityInlinePersistListener,
    idStrategyRegistry
) {
```

Change setup construction to:

```kotlin
jpaUnitOfWork = TestableJpaUnitOfWork(
    uowInterceptors = uowInterceptors,
    persistListenerManager = persistListenerManager,
    supportEntityInlinePersistListener = true,
    idStrategyRegistry = MapBackedIdStrategyRegistry(listOf(FixedLongStrategy())),
)
```

Remove `supportValueObjectExistsCheckOnSave = ...` from every `TestableJpaUnitOfWork(...)` call.

- [ ] **Step 8: Remove value-object UoW tests and helper**

Delete these tests:

```kotlin
fun testPersistValueObjectAlreadyExists()
fun testPersistValueObjectNotExists()
fun testValueObjectPersistenceWithExistsCheck()
```

Delete the helper entity:

```kotlin
@jakarta.persistence.Entity
data class TestValueObject(
    @jakarta.persistence.Id
    private val value: String
) : ValueObject<String> {
    override fun hash(): String = value.hashCode().toString()
}
```

- [ ] **Step 9: Update the constructor compatibility test**

Replace `fourArgumentJpaUnitOfWorkConstructorShouldRemainCallable` with:

```kotlin
@Test
@DisplayName("three argument JpaUnitOfWork constructor should remain callable")
fun threeArgumentJpaUnitOfWorkConstructorShouldRemainCallable() {
    val unitOfWork = JpaUnitOfWork(
        uowInterceptors,
        persistListenerManager,
        supportEntityInlinePersistListener = true,
    )
    val constructor = JpaUnitOfWork::class.java.getConstructor(
        List::class.java,
        PersistListenerManager::class.java,
        Boolean::class.javaPrimitiveType,
    )

    assertEquals(JpaUnitOfWork::class.java, unitOfWork.javaClass)
    assertEquals(JpaUnitOfWork::class.java, constructor.declaringClass)
}
```

- [ ] **Step 10: Static verification for Task 2**

Run:

```powershell
rg -n "supportValueObjectExistsCheckOnSave|isValueObjectAndExists|entity is ValueObject|ValueObject<" ddd-domain-repo-jpa/src/main/kotlin/com/only4/cap4k/ddd/application cap4k-ddd-starter/src/main/kotlin/com/only4/cap4k/ddd/domain/repo ddd-domain-repo-jpa/src/test/kotlin/com/only4/cap4k/ddd/application
```

Expected: no matches.

- [ ] **Step 11: Optional focused tests if user permits tests**

Run only after explicit user permission:

```powershell
.\gradlew.bat :ddd-domain-repo-jpa:test --tests "com.only4.cap4k.ddd.application.JpaUnitOfWorkTest"
```

Expected if tests are permitted: `BUILD SUCCESSFUL`.

- [ ] **Step 12: Commit gate**

Run only if the user asks for commits:

```powershell
git add ddd-domain-repo-jpa/src/main/kotlin/com/only4/cap4k/ddd/application/JpaUnitOfWork.kt `
        cap4k-ddd-starter/src/main/kotlin/com/only4/cap4k/ddd/domain/repo/configure/JpaUnitOfWorkProperties.kt `
        cap4k-ddd-starter/src/main/kotlin/com/only4/cap4k/ddd/domain/repo/JpaRepositoryAutoConfiguration.kt `
        ddd-domain-repo-jpa/src/test/kotlin/com/only4/cap4k/ddd/application/JpaUnitOfWorkTest.kt
git commit -m "refactor: remove value object unit of work support"
```

---

### Task 3: Delete `Md5HashIdentifierGenerator`

**Files:**
- Delete: `ddd-domain-repo-jpa/src/main/kotlin/com/only4/cap4k/ddd/domain/repo/Md5HashIdentifierGenerator.kt`
- Delete: `ddd-domain-repo-jpa/src/test/kotlin/com/only4/cap4k/ddd/domain/repo/Md5HashIdentifierGeneratorTest.kt`
- Modify: `cap4k-ddd-starter/src/main/kotlin/com/only4/cap4k/ddd/domain/repo/configure/JpaUnitOfWorkProperties.kt`
- Modify: `cap4k-ddd-starter/src/main/kotlin/com/only4/cap4k/ddd/domain/repo/JpaRepositoryAutoConfiguration.kt`
- Modify: `ddd-domain-repo-jpa/build.gradle.kts`

**Interfaces:**
- Consumes: Task 2 output where `JpaUnitOfWorkProperties` still has `generalIdFieldName` only for md5-generator wiring.
- Produces: no `Md5HashIdentifierGenerator`, no `generalIdFieldName`, no `ddd-domain-repo-jpa` dependency on `libs.fastjson`.

- [ ] **Step 1: Run the static pre-check**

Run:

```powershell
rg -n "Md5HashIdentifierGenerator|generalIdFieldName|libs\\.fastjson|com\\.alibaba\\.fastjson" ddd-domain-repo-jpa cap4k-ddd-starter/src/main/kotlin/com/only4/cap4k/ddd/domain/repo --glob "!**/build/**"
```

Expected before this task: matches in `Md5HashIdentifierGenerator.kt`, its test, `JpaUnitOfWorkProperties.kt`, `JpaRepositoryAutoConfiguration.kt`, and `ddd-domain-repo-jpa/build.gradle.kts`.

- [ ] **Step 2: Delete the generator and its tests**

Delete:

```text
ddd-domain-repo-jpa/src/main/kotlin/com/only4/cap4k/ddd/domain/repo/Md5HashIdentifierGenerator.kt
ddd-domain-repo-jpa/src/test/kotlin/com/only4/cap4k/ddd/domain/repo/Md5HashIdentifierGeneratorTest.kt
```

- [ ] **Step 3: Remove `generalIdFieldName` from `JpaUnitOfWorkProperties.kt`**

Remove:

```kotlin
/**
 * 通用主键字段名
 */
var generalIdFieldName: String = "id"
```

The class should end with `supportEntityInlinePersistListener` as the last constructor property:

```kotlin
class JpaUnitOfWorkProperties(
    /**
     * 单次获取记录数
     */
    var retrieveCountWarnThreshold: Int = 3000,

    /**
     * 是否支持实体内联持久化监听器
     * 创建 onCreate
     * 更新 onUpdate
     * 删除 onDelete | onRemove
     */
    var supportEntityInlinePersistListener: Boolean = true,
)
```

- [ ] **Step 4: Remove the md5 configuration hook**

In `JpaRepositoryAutoConfiguration.kt`, remove:

```kotlin
Md5HashIdentifierGenerator.configure(jpaUnitOfWorkProperties.generalIdFieldName)
```

Keep:

```kotlin
UnitOfWorkSupport.configure(it)
JpaQueryUtils.configure(it, jpaUnitOfWorkProperties.retrieveCountWarnThreshold)
```

- [ ] **Step 5: Remove fastjson from `ddd-domain-repo-jpa`**

In `ddd-domain-repo-jpa/build.gradle.kts`, remove:

```kotlin
implementation(libs.fastjson)
```

Do not remove `fastjson` from `gradle/libs.versions.toml`, because other runtime modules still use it.

- [ ] **Step 6: Static verification for Task 3**

Run:

```powershell
rg -n "Md5HashIdentifierGenerator|generalIdFieldName|com\\.alibaba\\.fastjson|libs\\.fastjson" ddd-domain-repo-jpa cap4k-ddd-starter/src/main/kotlin/com/only4/cap4k/ddd/domain/repo --glob "!**/build/**"
```

Expected after this task: no matches in `ddd-domain-repo-jpa` and no matches in `cap4k-ddd-starter/src/main/kotlin/com/only4/cap4k/ddd/domain/repo`.

- [ ] **Step 7: Optional focused tests if user permits tests**

Run only after explicit user permission:

```powershell
.\gradlew.bat :ddd-domain-repo-jpa:test
```

Expected if tests are permitted: `BUILD SUCCESSFUL`.

- [ ] **Step 8: Commit gate**

Run only if the user asks for commits:

```powershell
git add ddd-domain-repo-jpa/src/main/kotlin/com/only4/cap4k/ddd/domain/repo/Md5HashIdentifierGenerator.kt `
        ddd-domain-repo-jpa/src/test/kotlin/com/only4/cap4k/ddd/domain/repo/Md5HashIdentifierGeneratorTest.kt `
        cap4k-ddd-starter/src/main/kotlin/com/only4/cap4k/ddd/domain/repo/configure/JpaUnitOfWorkProperties.kt `
        cap4k-ddd-starter/src/main/kotlin/com/only4/cap4k/ddd/domain/repo/JpaRepositoryAutoConfiguration.kt `
        ddd-domain-repo-jpa/build.gradle.kts
git commit -m "refactor: remove md5 hash identifier generator"
```

---

### Task 4: Delete The Core `ValueObject` Interface And Validate Public Contract

**Files:**
- Delete: `ddd-core/src/main/kotlin/com/only4/cap4k/ddd/core/domain/aggregate/ValueObject.kt`
- Inspect: `docs/public/**`
- Inspect: `cap4k-plugin-pipeline-*/**`

**Interfaces:**
- Consumes: Tasks 1 through 3 outputs where no production runtime code imports `ValueObject`.
- Produces: no `ValueObject<ID>` runtime interface and no runtime references to it.

- [ ] **Step 1: Run the static pre-check**

Run:

```powershell
rg -n "ValueObject<|interface ValueObject|com\\.only4\\.cap4k\\.ddd\\.core\\.domain\\.aggregate\\.ValueObject|byValueObject|supportValueObjectExistsCheckOnSave|Md5HashIdentifierGenerator|generalIdFieldName" ddd-core ddd-domain-repo-jpa cap4k-ddd-starter --glob "!**/build/**"
```

Expected before deleting `ValueObject.kt`: only `ddd-core/src/main/kotlin/com/only4/cap4k/ddd/core/domain/aggregate/ValueObject.kt` may match.

- [ ] **Step 2: Delete the interface**

Delete:

```text
ddd-core/src/main/kotlin/com/only4/cap4k/ddd/core/domain/aggregate/ValueObject.kt
```

- [ ] **Step 3: Check public docs for old runtime-interface terms**

Run:

```powershell
rg -n "ValueObject<|interface ValueObject|ValueObject 接口|byValueObject|supportValueObjectExistsCheckOnSave|Md5HashIdentifierGenerator|generalIdFieldName|hash\\(\\)" docs/public --glob "!**/build/**"
```

Expected: no matches.

If this command finds a public doc match, rewrite the line to describe the current model only. Use present-tense wording such as:

```markdown
Value objects are ordinary Kotlin data classes. JSON-backed value objects persist through their nested JPA converter.
```

Do not write a sentence such as:

```markdown
The old ValueObject interface was removed.
```

- [ ] **Step 4: Check generator-side value-object support was not touched**

Run:

```powershell
git diff -- cap4k-plugin-pipeline-api cap4k-plugin-pipeline-generator-types cap4k-plugin-pipeline-source-value-object-manifest cap4k-plugin-pipeline-renderer-pebble/src/main/resources/presets/ddd-default/types/value_object.kt.peb docs/public/reference/value-object-manifest.md docs/public/concepts/modeling-building-blocks/value-object.md
```

Expected: no diff unless Step 3 found a public doc line that describes the old runtime interface.

- [ ] **Step 5: Full static acceptance scan**

Run:

```powershell
rg -n "ValueObject<|interface ValueObject|com\\.only4\\.cap4k\\.ddd\\.core\\.domain\\.aggregate\\.ValueObject|JpaPredicate\\.byValueObject|byValueObject|supportValueObjectExistsCheckOnSave|Md5HashIdentifierGenerator|generalIdFieldName" ddd-core ddd-domain-repo-jpa cap4k-ddd-starter docs/public --glob "!**/build/**"
```

Expected: no matches.

Run:

```powershell
rg -n "types.valueObjectManifest|ValueObjectModel|ValueObjectArtifactPlanner|value_object.kt.peb|family = \"value-object\"" cap4k-plugin-pipeline-api cap4k-plugin-pipeline-generator-types cap4k-plugin-pipeline-source-value-object-manifest cap4k-plugin-pipeline-renderer-pebble docs/public --glob "!**/build/**"
```

Expected: matches remain. These matches prove generator-side value-object support still exists.

- [ ] **Step 6: Optional broader tests if user permits tests**

Run only after explicit user permission:

```powershell
.\gradlew.bat :ddd-core:test :ddd-domain-repo-jpa:test :cap4k-ddd-starter:test
```

Expected if tests are permitted: `BUILD SUCCESSFUL`, except for any known unrelated starter fixture isolation failures already documented in `AGENTS.md`. If such a known fixture failure appears, record the exact failing test names and run the narrower impacted tests from Tasks 1 through 3.

- [ ] **Step 7: Final diff review**

Run:

```powershell
git status --short
git diff --stat
git diff -- ddd-core ddd-domain-repo-jpa cap4k-ddd-starter docs/public
```

Expected:

- deleted `ValueObject.kt`
- deleted `Md5HashIdentifierGenerator.kt`
- deleted `Md5HashIdentifierGeneratorTest.kt`
- focused edits in `JpaPredicate`, `JpaUnitOfWork`, starter UoW properties, starter auto-configuration, and related tests
- no generator-side value-object template/model rewrites
- no public docs historical-removal wording

- [ ] **Step 8: Commit gate**

Run only if the user asks for commits:

```powershell
git add ddd-core/src/main/kotlin/com/only4/cap4k/ddd/core/domain/aggregate/ValueObject.kt docs/public
git commit -m "refactor: remove legacy value object interface"
```

---

## Final Review Checklist

- [ ] `rg -n "ValueObject<|interface ValueObject|com\\.only4\\.cap4k\\.ddd\\.core\\.domain\\.aggregate\\.ValueObject|JpaPredicate\\.byValueObject|byValueObject|supportValueObjectExistsCheckOnSave|Md5HashIdentifierGenerator|generalIdFieldName" ddd-core ddd-domain-repo-jpa cap4k-ddd-starter docs/public --glob "!**/build/**"` returns no matches.
- [ ] `rg -n "types.valueObjectManifest|ValueObjectModel|ValueObjectArtifactPlanner|value_object.kt.peb|family = \"value-object\"" cap4k-plugin-pipeline-api cap4k-plugin-pipeline-generator-types cap4k-plugin-pipeline-source-value-object-manifest cap4k-plugin-pipeline-renderer-pebble docs/public --glob "!**/build/**"` still returns matches.
- [ ] `ddd-domain-repo-jpa/build.gradle.kts` does not contain `implementation(libs.fastjson)`.
- [ ] `gradle/libs.versions.toml` still contains `fastjson`, because other modules still use it.
- [ ] No compile/run/test/install command was run unless the user explicitly allowed it.
- [ ] No commit was created unless the user explicitly asked for commits.
