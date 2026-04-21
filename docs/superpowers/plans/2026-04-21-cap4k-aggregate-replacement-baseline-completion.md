# Aggregate Replacement Baseline Completion Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Complete the aggregate replacement baseline by restoring repository and schema output, adding capability-level aggregate artifact selection with minimal defaults, and hardening aggregate verification.

**Architecture:** Keep one public `aggregate` generator and add capability selection under `generators.aggregate.artifacts`. Translate DSL state into aggregate generator options, parse those options once into an internal selection model, gate family planners at planning time, and keep `cap4kPlan` artifact-accurate. Move shared schema runtime into `ddd-domain-repo-jpa`, restore repository and schema templates to bounded old-codegen baseline, and verify behavior through renderer, functional, and compile tests.

**Tech Stack:** Kotlin, Gradle, JUnit 5, Gradle TestKit, Pebble templates, H2 functional fixtures, Spring Data JPA, `ddd-domain-repo-jpa`.

---

## File Map

**Modify:**
- `cap4k-plugin-pipeline-gradle/src/main/kotlin/com/only4/cap4k/plugin/pipeline/gradle/Cap4kExtension.kt`
- `cap4k-plugin-pipeline-gradle/src/main/kotlin/com/only4/cap4k/plugin/pipeline/gradle/Cap4kProjectConfigFactory.kt`
- `cap4k-plugin-pipeline-gradle/src/test/kotlin/com/only4/cap4k/plugin/pipeline/gradle/Cap4kProjectConfigFactoryTest.kt`
- `cap4k-plugin-pipeline-generator-aggregate/src/main/kotlin/com/only4/cap4k/plugin/pipeline/generator/aggregate/AggregateArtifactPlanner.kt`
- `cap4k-plugin-pipeline-generator-aggregate/src/main/kotlin/com/only4/cap4k/plugin/pipeline/generator/aggregate/SchemaArtifactPlanner.kt`
- `cap4k-plugin-pipeline-generator-aggregate/src/main/kotlin/com/only4/cap4k/plugin/pipeline/generator/aggregate/RepositoryArtifactPlanner.kt`
- `cap4k-plugin-pipeline-generator-aggregate/src/main/kotlin/com/only4/cap4k/plugin/pipeline/generator/aggregate/AggregateDerivedTypeReferences.kt`
- `cap4k-plugin-pipeline-generator-aggregate/src/test/kotlin/com/only4/cap4k/plugin/pipeline/generator/aggregate/AggregateArtifactPlannerTest.kt`
- `cap4k-plugin-pipeline-renderer-pebble/src/main/resources/presets/ddd-default/aggregate/schema.kt.peb`
- `cap4k-plugin-pipeline-renderer-pebble/src/main/resources/presets/ddd-default/aggregate/repository.kt.peb`
- `cap4k-plugin-pipeline-renderer-pebble/src/main/resources/presets/ddd-default/aggregate/factory.kt.peb`
- `cap4k-plugin-pipeline-renderer-pebble/src/main/resources/presets/ddd-default/aggregate/specification.kt.peb`
- `cap4k-plugin-pipeline-renderer-pebble/src/main/resources/presets/ddd-default/aggregate/wrapper.kt.peb`
- `cap4k-plugin-pipeline-renderer-pebble/src/main/resources/presets/ddd-default/aggregate/unique_query.kt.peb`
- `cap4k-plugin-pipeline-renderer-pebble/src/main/resources/presets/ddd-default/aggregate/unique_query_handler.kt.peb`
- `cap4k-plugin-pipeline-renderer-pebble/src/main/resources/presets/ddd-default/aggregate/unique_validator.kt.peb`
- `cap4k-plugin-pipeline-renderer-pebble/src/test/kotlin/com/only4/cap4k/plugin/pipeline/renderer/pebble/PebbleArtifactRendererTest.kt`
- `cap4k-plugin-pipeline-gradle/src/test/kotlin/com/only4/cap4k/plugin/pipeline/gradle/PipelinePluginFunctionalTest.kt`
- `cap4k-plugin-pipeline-gradle/src/test/kotlin/com/only4/cap4k/plugin/pipeline/gradle/PipelinePluginCompileFunctionalTest.kt`
- `cap4k-plugin-pipeline-gradle/src/test/resources/functional/aggregate-sample/build.gradle.kts`
- `cap4k-plugin-pipeline-gradle/src/test/resources/functional/aggregate-compile-sample/build.gradle.kts`
- `cap4k-plugin-pipeline-gradle/src/test/resources/functional/aggregate-compile-sample/demo-domain/src/main/kotlin/com/acme/demo/domain/aggregates/video_post/AggregateCompileSmoke.kt`
- `cap4k-plugin-pipeline-gradle/README.md`

**Create:**
- `cap4k-plugin-pipeline-generator-aggregate/src/main/kotlin/com/only4/cap4k/plugin/pipeline/generator/aggregate/AggregateArtifactSelection.kt`
- `cap4k-plugin-pipeline-generator-aggregate/src/main/kotlin/com/only4/cap4k/plugin/pipeline/generator/aggregate/AggregateSchemaPlanning.kt`
- `ddd-domain-repo-jpa/src/main/kotlin/com/only4/cap4k/ddd/domain/repo/schema/SchemaContracts.kt`
- `ddd-domain-repo-jpa/src/main/kotlin/com/only4/cap4k/ddd/domain/repo/schema/JoinType.kt`
- `ddd-domain-repo-jpa/src/main/kotlin/com/only4/cap4k/ddd/domain/repo/schema/Field.kt`
- `ddd-domain-repo-jpa/src/main/kotlin/com/only4/cap4k/ddd/domain/repo/schema/Predicates.kt`
- `ddd-domain-repo-jpa/src/test/kotlin/com/only4/cap4k/ddd/domain/repo/schema/JoinTypeTest.kt`
- `ddd-domain-repo-jpa/src/test/kotlin/com/only4/cap4k/ddd/domain/repo/schema/FieldTest.kt`
- `cap4k-plugin-pipeline-gradle/src/test/resources/functional/aggregate-minimal-sample/build.gradle.kts`
- `cap4k-plugin-pipeline-gradle/src/test/resources/functional/aggregate-minimal-sample/schema.sql`
- `cap4k-plugin-pipeline-gradle/src/test/resources/functional/aggregate-minimal-compile-sample/build.gradle.kts`
- `cap4k-plugin-pipeline-gradle/src/test/resources/functional/aggregate-minimal-compile-sample/schema.sql`
- `cap4k-plugin-pipeline-gradle/src/test/resources/functional/aggregate-minimal-compile-sample/demo-domain/src/main/kotlin/com/acme/demo/domain/aggregates/video_post/AggregateMinimalDomainCompileSmoke.kt`
- `cap4k-plugin-pipeline-gradle/src/test/resources/functional/aggregate-minimal-compile-sample/demo-adapter/src/main/kotlin/com/acme/demo/adapter/domain/repositories/AggregateMinimalAdapterCompileSmoke.kt`

### Task 1: Add Aggregate Artifact Selection DSL and Config Validation

**Files:**
- Modify: `cap4k-plugin-pipeline-gradle/src/main/kotlin/com/only4/cap4k/plugin/pipeline/gradle/Cap4kExtension.kt`
- Modify: `cap4k-plugin-pipeline-gradle/src/main/kotlin/com/only4/cap4k/plugin/pipeline/gradle/Cap4kProjectConfigFactory.kt`
- Test: `cap4k-plugin-pipeline-gradle/src/test/kotlin/com/only4/cap4k/plugin/pipeline/gradle/Cap4kProjectConfigFactoryTest.kt`

- [ ] **Step 1: Write failing config-factory tests for aggregate artifact defaults and dependency validation**

```kotlin
@Test
fun `aggregate artifacts default optional capabilities to false`() {
    val project = ProjectBuilder.builder().build()
    val extension = project.extensions.create("cap4k", Cap4kExtension::class.java)

    assertFalse(extension.generators.aggregate.artifacts.factory.get())
    assertFalse(extension.generators.aggregate.artifacts.specification.get())
    assertFalse(extension.generators.aggregate.artifacts.wrapper.get())
    assertFalse(extension.generators.aggregate.artifacts.unique.get())
    assertFalse(extension.generators.aggregate.artifacts.enumTranslation.get())
}

@Test
fun `aggregate wrapper artifact requires factory artifact`() {
    val project = ProjectBuilder.builder().build()
    val extension = project.extensions.create("cap4k", Cap4kExtension::class.java)

    extension.project {
        basePackage.set("com.acme.demo")
        domainModulePath.set("demo-domain")
        applicationModulePath.set("demo-application")
        adapterModulePath.set("demo-adapter")
    }
    extension.sources {
        db {
            enabled.set(true)
            url.set("jdbc:h2:mem:test")
            username.set("sa")
            password.set("secret")
        }
    }
    extension.generators {
        aggregate {
            enabled.set(true)
            artifacts {
                factory.set(false)
                wrapper.set(true)
            }
        }
    }

    val error = assertThrows(IllegalArgumentException::class.java) {
        Cap4kProjectConfigFactory().build(project, extension)
    }

    assertEquals("aggregate wrapper artifact requires enabled aggregate factory artifact.", error.message)
}
```

- [ ] **Step 2: Run the targeted Gradle test task and verify it fails**

Run:

```bash
./gradlew :cap4k-plugin-pipeline-gradle:test --tests "com.only4.cap4k.plugin.pipeline.gradle.Cap4kProjectConfigFactoryTest"
```

Expected: FAIL because `AggregateGeneratorExtension` has no `artifacts` block and config validation does not check the wrapper/factory dependency.

- [ ] **Step 3: Add the aggregate artifacts DSL block and map it into generator options**

```kotlin
open class AggregateGeneratorArtifactsExtension @Inject constructor(objects: ObjectFactory) {
    val factory: Property<Boolean> = objects.property(Boolean::class.java).convention(false)
    val specification: Property<Boolean> = objects.property(Boolean::class.java).convention(false)
    val wrapper: Property<Boolean> = objects.property(Boolean::class.java).convention(false)
    val unique: Property<Boolean> = objects.property(Boolean::class.java).convention(false)
    val enumTranslation: Property<Boolean> = objects.property(Boolean::class.java).convention(false)
}

open class AggregateGeneratorExtension @Inject constructor(objects: ObjectFactory) {
    val enabled: Property<Boolean> = objects.property(Boolean::class.java).convention(false)
    val unsupportedTablePolicy: Property<String> = objects.property(String::class.java).convention("FAIL")
    val artifacts: AggregateGeneratorArtifactsExtension =
        objects.newInstance(AggregateGeneratorArtifactsExtension::class.java)

    fun artifacts(block: AggregateGeneratorArtifactsExtension.() -> Unit) {
        artifacts.block()
    }
}
```

```kotlin
if (states.aggregateEnabled) {
    val aggregate = extension.generators.aggregate
    val artifactOptions = mapOf(
        "unsupportedTablePolicy" to aggregate.unsupportedTablePolicy.normalized().uppercase(Locale.ROOT).ifEmpty { "FAIL" },
        "artifact.factory" to aggregate.artifacts.factory.get(),
        "artifact.specification" to aggregate.artifacts.specification.get(),
        "artifact.wrapper" to aggregate.artifacts.wrapper.get(),
        "artifact.unique" to aggregate.artifacts.unique.get(),
        "artifact.enumTranslation" to aggregate.artifacts.enumTranslation.get(),
    )
    if (aggregate.artifacts.wrapper.get() && !aggregate.artifacts.factory.get()) {
        throw IllegalArgumentException("aggregate wrapper artifact requires enabled aggregate factory artifact.")
    }
    put("aggregate", GeneratorConfig(enabled = true, options = artifactOptions))
}
```

- [ ] **Step 4: Re-run the config-factory test task and verify it passes**

Run:

```bash
./gradlew :cap4k-plugin-pipeline-gradle:test --tests "com.only4.cap4k.plugin.pipeline.gradle.Cap4kProjectConfigFactoryTest"
```

Expected: PASS, including new default-value assertions and wrapper/factory dependency validation.

- [ ] **Step 5: Commit**

```bash
git add cap4k-plugin-pipeline-gradle/src/main/kotlin/com/only4/cap4k/plugin/pipeline/gradle/Cap4kExtension.kt cap4k-plugin-pipeline-gradle/src/main/kotlin/com/only4/cap4k/plugin/pipeline/gradle/Cap4kProjectConfigFactory.kt cap4k-plugin-pipeline-gradle/src/test/kotlin/com/only4/cap4k/plugin/pipeline/gradle/Cap4kProjectConfigFactoryTest.kt
git commit -m "feat: add aggregate artifact selection config"
```

### Task 2: Gate Aggregate Family Planning with a Typed Selection Model

**Files:**
- Create: `cap4k-plugin-pipeline-generator-aggregate/src/main/kotlin/com/only4/cap4k/plugin/pipeline/generator/aggregate/AggregateArtifactSelection.kt`
- Modify: `cap4k-plugin-pipeline-generator-aggregate/src/main/kotlin/com/only4/cap4k/plugin/pipeline/generator/aggregate/AggregateArtifactPlanner.kt`
- Test: `cap4k-plugin-pipeline-generator-aggregate/src/test/kotlin/com/only4/cap4k/plugin/pipeline/generator/aggregate/AggregateArtifactPlannerTest.kt`

- [ ] **Step 1: Write failing planner tests for fixed baseline output and grouped unique output**

```kotlin
@Test
fun `planner keeps fixed baseline families when optional artifacts are disabled`() {
    val plan = AggregateArtifactPlanner().plan(
        aggregateConfig(
            options = mapOf(
                "artifact.factory" to false,
                "artifact.specification" to false,
                "artifact.wrapper" to false,
                "artifact.unique" to false,
                "artifact.enumTranslation" to false,
            )
        ),
        sampleAggregateModel(),
    )

    assertTrue(plan.any { it.templateId == "aggregate/entity.kt.peb" })
    assertTrue(plan.any { it.templateId == "aggregate/schema.kt.peb" })
    assertTrue(plan.any { it.templateId == "aggregate/repository.kt.peb" })
    assertFalse(plan.any { it.templateId == "aggregate/factory.kt.peb" })
    assertFalse(plan.any { it.templateId == "aggregate/specification.kt.peb" })
    assertFalse(plan.any { it.templateId == "aggregate/wrapper.kt.peb" })
    assertFalse(plan.any { it.templateId == "aggregate/unique_query.kt.peb" })
    assertFalse(plan.any { it.templateId == "aggregate/unique_query_handler.kt.peb" })
    assertFalse(plan.any { it.templateId == "aggregate/unique_validator.kt.peb" })
}

@Test
fun `planner expands unique capability into three concrete artifact items`() {
    val plan = AggregateArtifactPlanner().plan(
        aggregateConfig(
            options = mapOf(
                "artifact.factory" to false,
                "artifact.specification" to false,
                "artifact.wrapper" to false,
                "artifact.unique" to true,
                "artifact.enumTranslation" to false,
            )
        ),
        sampleAggregateModelWithUniqueConstraint(),
    )

    assertEquals(1, plan.count { it.templateId == "aggregate/unique_query.kt.peb" })
    assertEquals(1, plan.count { it.templateId == "aggregate/unique_query_handler.kt.peb" })
    assertEquals(1, plan.count { it.templateId == "aggregate/unique_validator.kt.peb" })
}
```

- [ ] **Step 2: Run the aggregate planner tests and verify they fail**

Run:

```bash
./gradlew :cap4k-plugin-pipeline-generator-aggregate:test --tests "com.only4.cap4k.plugin.pipeline.generator.aggregate.AggregateArtifactPlannerTest"
```

Expected: FAIL because `AggregateArtifactPlanner` currently always executes all delegates and has no typed selection object.

- [ ] **Step 3: Implement a typed aggregate selection helper and apply it before delegate planning**

```kotlin
internal data class AggregateArtifactSelection(
    val factoryEnabled: Boolean,
    val specificationEnabled: Boolean,
    val wrapperEnabled: Boolean,
    val uniqueEnabled: Boolean,
    val enumTranslationEnabled: Boolean,
) {
    companion object {
        fun from(config: ProjectConfig): AggregateArtifactSelection {
            val options = config.generators.getValue("aggregate").options
            return AggregateArtifactSelection(
                factoryEnabled = options["artifact.factory"] as? Boolean ?: false,
                specificationEnabled = options["artifact.specification"] as? Boolean ?: false,
                wrapperEnabled = options["artifact.wrapper"] as? Boolean ?: false,
                uniqueEnabled = options["artifact.unique"] as? Boolean ?: false,
                enumTranslationEnabled = options["artifact.enumTranslation"] as? Boolean ?: false,
            )
        }
    }
}
```

```kotlin
override fun plan(config: ProjectConfig, model: CanonicalModel): List<ArtifactPlanItem> {
    val selection = AggregateArtifactSelection.from(config)
    return delegates.flatMap { delegate ->
        when (delegate) {
            is FactoryArtifactPlanner -> if (selection.factoryEnabled) delegate.plan(config, model) else emptyList()
            is SpecificationArtifactPlanner -> if (selection.specificationEnabled) delegate.plan(config, model) else emptyList()
            is AggregateWrapperArtifactPlanner -> if (selection.wrapperEnabled) delegate.plan(config, model) else emptyList()
            is UniqueQueryArtifactPlanner,
            is UniqueQueryHandlerArtifactPlanner,
            is UniqueValidatorArtifactPlanner -> if (selection.uniqueEnabled) delegate.plan(config, model) else emptyList()
            is EnumTranslationArtifactPlanner -> if (selection.enumTranslationEnabled) delegate.plan(config, model) else emptyList()
            else -> delegate.plan(config, model)
        }
    }
}
```

- [ ] **Step 4: Re-run the aggregate planner tests and verify they pass**

Run:

```bash
./gradlew :cap4k-plugin-pipeline-generator-aggregate:test --tests "com.only4.cap4k.plugin.pipeline.generator.aggregate.AggregateArtifactPlannerTest"
```

Expected: PASS, with fixed baseline families retained and optional families gated by selection.

- [ ] **Step 5: Commit**

```bash
git add cap4k-plugin-pipeline-generator-aggregate/src/main/kotlin/com/only4/cap4k/plugin/pipeline/generator/aggregate/AggregateArtifactSelection.kt cap4k-plugin-pipeline-generator-aggregate/src/main/kotlin/com/only4/cap4k/plugin/pipeline/generator/aggregate/AggregateArtifactPlanner.kt cap4k-plugin-pipeline-generator-aggregate/src/test/kotlin/com/only4/cap4k/plugin/pipeline/generator/aggregate/AggregateArtifactPlannerTest.kt
git commit -m "feat: gate aggregate families by artifact selection"
```

### Task 3: Restore the Repository Baseline

**Files:**
- Modify: `cap4k-plugin-pipeline-generator-aggregate/src/main/kotlin/com/only4/cap4k/plugin/pipeline/generator/aggregate/RepositoryArtifactPlanner.kt`
- Modify: `cap4k-plugin-pipeline-renderer-pebble/src/main/resources/presets/ddd-default/aggregate/repository.kt.peb`
- Test: `cap4k-plugin-pipeline-renderer-pebble/src/test/kotlin/com/only4/cap4k/plugin/pipeline/renderer/pebble/PebbleArtifactRendererTest.kt`
- Test: `cap4k-plugin-pipeline-generator-aggregate/src/test/kotlin/com/only4/cap4k/plugin/pipeline/generator/aggregate/AggregateArtifactPlannerTest.kt`

- [ ] **Step 1: Write failing tests for full repository rendering**

```kotlin
@Test
fun `aggregate repository template renders spring data baseline`() {
    val content = renderSingle(
        templateId = "aggregate/repository.kt.peb",
        context = mapOf(
            "packageName" to "com.acme.demo.adapter.domain.repositories",
            "typeName" to "VideoPostRepository",
            "entityName" to "VideoPost",
            "entityTypeFqn" to "com.acme.demo.domain.aggregates.video_post.VideoPost",
            "idType" to "Long",
            "supportQuerydsl" to false,
        ),
    )

    assertTrue(content.contains("interface VideoPostRepository : JpaRepository<VideoPost, Long>, JpaSpecificationExecutor<VideoPost>"))
    assertTrue(content.contains("class VideoPostJpaRepositoryAdapter("))
    assertTrue(content.contains("AbstractJpaRepository<VideoPost, Long>"))
    assertTrue(content.contains("@Aggregate(aggregate = \"VideoPost\", name = \"VideoPostRepo\", type = Aggregate.TYPE_REPOSITORY"))
}
```

```kotlin
@Test
fun `repository planner exposes entity type import context`() {
    val item = AggregateArtifactPlanner().plan(
        aggregateConfig(options = mapOf("artifact.factory" to false, "artifact.specification" to false, "artifact.wrapper" to false, "artifact.unique" to false, "artifact.enumTranslation" to false)),
        sampleAggregateModel(),
    ).single { it.templateId == "aggregate/repository.kt.peb" }

    assertEquals("com.acme.demo.domain.aggregates.video_post.VideoPost", item.context["entityTypeFqn"])
    assertEquals(false, item.context["supportQuerydsl"])
}
```

- [ ] **Step 2: Run the renderer and planner tests and verify they fail**

Run:

```bash
./gradlew :cap4k-plugin-pipeline-renderer-pebble:test --tests "com.only4.cap4k.plugin.pipeline.renderer.pebble.PebbleArtifactRendererTest" :cap4k-plugin-pipeline-generator-aggregate:test --tests "com.only4.cap4k.plugin.pipeline.generator.aggregate.AggregateArtifactPlannerTest"
```

Expected: FAIL because the repository template is still `interface {{ typeName }}` and the planner does not expose entity FQN or support flag.

- [ ] **Step 3: Upgrade repository planning and template rendering to the old baseline shape**

```kotlin
context = mapOf(
    "packageName" to repository.packageName,
    "typeName" to repository.name,
    "entityName" to repository.entityName,
    "entityTypeFqn" to derivedTypeReferences.entityFqn(repository.entityName),
    "idType" to repository.idType,
    "supportQuerydsl" to false,
)
```

```pebble
package {{ packageName }}

{{ use("org.springframework.data.jpa.repository.JpaRepository") -}}
{{ use("org.springframework.data.jpa.repository.JpaSpecificationExecutor") -}}
{{ use("org.springframework.stereotype.Repository") -}}
{{ use("org.springframework.stereotype.Component") -}}
{{ use("com.only4.cap4k.ddd.core.domain.aggregate.annotation.Aggregate") -}}
{{ use("com.only4.cap4k.ddd.domain.repo.AbstractJpaRepository") -}}
{{ use(entityTypeFqn) -}}
{{ imports() }}

@Repository
interface {{ typeName }} : JpaRepository<{{ entityName }}, {{ idType }}>, JpaSpecificationExecutor<{{ entityName }}> {

    @Component
    @Aggregate(aggregate = "{{ entityName }}", name = "{{ entityName }}Repo", type = Aggregate.TYPE_REPOSITORY, description = "")
    class {{ entityName }}JpaRepositoryAdapter(
        jpaSpecificationExecutor: JpaSpecificationExecutor<{{ entityName }}>,
        jpaRepository: JpaRepository<{{ entityName }}, {{ idType }}>
    ) : AbstractJpaRepository<{{ entityName }}, {{ idType }}>(
        jpaSpecificationExecutor,
        jpaRepository
    )
}
```

- [ ] **Step 4: Re-run the repository tests and verify they pass**

Run:

```bash
./gradlew :cap4k-plugin-pipeline-renderer-pebble:test --tests "com.only4.cap4k.plugin.pipeline.renderer.pebble.PebbleArtifactRendererTest" :cap4k-plugin-pipeline-generator-aggregate:test --tests "com.only4.cap4k.plugin.pipeline.generator.aggregate.AggregateArtifactPlannerTest"
```

Expected: PASS, with repository output matching the bounded Spring Data + adapter baseline.

- [ ] **Step 5: Commit**

```bash
git add cap4k-plugin-pipeline-generator-aggregate/src/main/kotlin/com/only4/cap4k/plugin/pipeline/generator/aggregate/RepositoryArtifactPlanner.kt cap4k-plugin-pipeline-renderer-pebble/src/main/resources/presets/ddd-default/aggregate/repository.kt.peb cap4k-plugin-pipeline-renderer-pebble/src/test/kotlin/com/only4/cap4k/plugin/pipeline/renderer/pebble/PebbleArtifactRendererTest.kt cap4k-plugin-pipeline-generator-aggregate/src/test/kotlin/com/only4/cap4k/plugin/pipeline/generator/aggregate/AggregateArtifactPlannerTest.kt
git commit -m "feat: restore aggregate repository baseline"
```

### Task 4: Move Shared Schema Runtime into `ddd-domain-repo-jpa`

**Files:**
- Create: `ddd-domain-repo-jpa/src/main/kotlin/com/only4/cap4k/ddd/domain/repo/schema/SchemaContracts.kt`
- Create: `ddd-domain-repo-jpa/src/main/kotlin/com/only4/cap4k/ddd/domain/repo/schema/JoinType.kt`
- Create: `ddd-domain-repo-jpa/src/main/kotlin/com/only4/cap4k/ddd/domain/repo/schema/Field.kt`
- Create: `ddd-domain-repo-jpa/src/main/kotlin/com/only4/cap4k/ddd/domain/repo/schema/Predicates.kt`
- Test: `ddd-domain-repo-jpa/src/test/kotlin/com/only4/cap4k/ddd/domain/repo/schema/JoinTypeTest.kt`
- Test: `ddd-domain-repo-jpa/src/test/kotlin/com/only4/cap4k/ddd/domain/repo/schema/FieldTest.kt`

- [ ] **Step 1: Write failing runtime tests for the new schema support package**

```kotlin
class JoinTypeTest {

    @Test
    fun `join type maps to jakarta join type`() {
        assertEquals(jakarta.persistence.criteria.JoinType.INNER, JoinType.INNER.toJpaJoinType())
        assertEquals(jakarta.persistence.criteria.JoinType.LEFT, JoinType.LEFT.toJpaJoinType())
        assertEquals(jakarta.persistence.criteria.JoinType.RIGHT, JoinType.RIGHT.toJpaJoinType())
    }
}
```

```kotlin
class FieldTest {

    @Test
    fun `field created from name keeps printable name`() {
        val field = Field<Long>("id")

        assertEquals("id", field.toString())
        assertEquals(null, field.path())
    }
}
```

- [ ] **Step 2: Run the `ddd-domain-repo-jpa` tests and verify they fail**

Run:

```bash
./gradlew :ddd-domain-repo-jpa:test --tests "com.only4.cap4k.ddd.domain.repo.schema.JoinTypeTest" --tests "com.only4.cap4k.ddd.domain.repo.schema.FieldTest"
```

Expected: FAIL because the `com.only4.cap4k.ddd.domain.repo.schema` package does not exist yet.

- [ ] **Step 3: Add the schema runtime package by splitting old `schema_base.kt.peb` into focused runtime files**

```kotlin
// SchemaContracts.kt
package com.only4.cap4k.ddd.domain.repo.schema

import jakarta.persistence.criteria.CriteriaBuilder
import jakarta.persistence.criteria.CriteriaQuery
import jakarta.persistence.criteria.Expression
import jakarta.persistence.criteria.Order
import jakarta.persistence.criteria.Predicate
import jakarta.persistence.criteria.Subquery

fun interface SchemaSpecification<E, S> {
    fun toPredicate(schema: S, criteriaQuery: CriteriaQuery<*>, criteriaBuilder: CriteriaBuilder): Predicate?
}

fun interface SubqueryConfigure<E, S> {
    fun configure(subquery: Subquery<E>, schema: S)
}

fun interface ExpressionBuilder<S, T> {
    fun build(schema: S): Expression<T>
}

fun interface PredicateBuilder<S> {
    fun build(schema: S): Predicate
}

fun interface OrderBuilder<S> {
    fun build(schema: S): Order
}
```

```kotlin
// JoinType.kt
package com.only4.cap4k.ddd.domain.repo.schema

enum class JoinType {
    INNER,
    LEFT,
    RIGHT,
    ;

    fun toJpaJoinType(): jakarta.persistence.criteria.JoinType = when (this) {
        INNER -> jakarta.persistence.criteria.JoinType.INNER
        LEFT -> jakarta.persistence.criteria.JoinType.LEFT
        RIGHT -> jakarta.persistence.criteria.JoinType.RIGHT
    }
}
```

```kotlin
// Field.kt and Predicates.kt
// Port the existing old-codegen runtime methods verbatim where possible:
// - constructor(path, criteriaBuilder)
// - constructor(name)
// - asc / desc
// - equal / notEqual / isNull / isNotNull
// - comparison helpers
// - in / notIn
// - Kotlin DSL aliases such as eq, neq, gt, ge, lt, le, eq?, in?
// - top-level and/or/not helpers with Hibernate-backed CriteriaBuilder recovery
```

- [ ] **Step 4: Re-run the `ddd-domain-repo-jpa` tests and verify they pass**

Run:

```bash
./gradlew :ddd-domain-repo-jpa:test --tests "com.only4.cap4k.ddd.domain.repo.schema.JoinTypeTest" --tests "com.only4.cap4k.ddd.domain.repo.schema.FieldTest"
```

Expected: PASS, proving the new runtime package exists and basic behavior works.

- [ ] **Step 5: Commit**

```bash
git add ddd-domain-repo-jpa/src/main/kotlin/com/only4/cap4k/ddd/domain/repo/schema ddd-domain-repo-jpa/src/test/kotlin/com/only4/cap4k/ddd/domain/repo/schema
git commit -m "feat: add shared schema runtime to jpa repo module"
```

### Task 5: Restore the Schema Baseline with Wrapper-Aware Surface Gating

**Files:**
- Create: `cap4k-plugin-pipeline-generator-aggregate/src/main/kotlin/com/only4/cap4k/plugin/pipeline/generator/aggregate/AggregateSchemaPlanning.kt`
- Modify: `cap4k-plugin-pipeline-generator-aggregate/src/main/kotlin/com/only4/cap4k/plugin/pipeline/generator/aggregate/SchemaArtifactPlanner.kt`
- Modify: `cap4k-plugin-pipeline-generator-aggregate/src/main/kotlin/com/only4/cap4k/plugin/pipeline/generator/aggregate/AggregateDerivedTypeReferences.kt`
- Modify: `cap4k-plugin-pipeline-renderer-pebble/src/main/resources/presets/ddd-default/aggregate/schema.kt.peb`
- Test: `cap4k-plugin-pipeline-generator-aggregate/src/test/kotlin/com/only4/cap4k/plugin/pipeline/generator/aggregate/AggregateArtifactPlannerTest.kt`
- Test: `cap4k-plugin-pipeline-renderer-pebble/src/test/kotlin/com/only4/cap4k/plugin/pipeline/renderer/pebble/PebbleArtifactRendererTest.kt`

- [ ] **Step 1: Write failing schema planner and renderer tests**

```kotlin
@Test
fun `schema planner omits wrapper dependent surface when wrapper artifact is disabled`() {
    val item = AggregateArtifactPlanner().plan(
        aggregateConfig(
            options = mapOf(
                "artifact.factory" to false,
                "artifact.specification" to false,
                "artifact.wrapper" to false,
                "artifact.unique" to false,
                "artifact.enumTranslation" to false,
            )
        ),
        sampleAggregateModel(),
    ).single { it.templateId == "aggregate/schema.kt.peb" }

    assertEquals(false, item.context["wrapperEnabled"])
    assertEquals("", item.context["aggregateTypeFqn"])
}
```

```kotlin
@Test
fun `aggregate schema template renders baseline query dsl`() {
    val content = renderSingle(
        templateId = "aggregate/schema.kt.peb",
        context = mapOf(
            "packageName" to "com.acme.demo.domain._share.meta.video_post",
            "typeName" to "SVideoPost",
            "entityName" to "VideoPost",
            "entityTypeFqn" to "com.acme.demo.domain.aggregates.video_post.VideoPost",
            "schemaRuntimePackage" to "com.only4.cap4k.ddd.domain.repo.schema",
            "idType" to "Long",
            "comment" to "video post",
            "wrapperEnabled" to false,
            "aggregateTypeFqn" to "",
            "qEntityTypeFqn" to "",
            "fields" to listOf(
                mapOf("name" to "id", "type" to "Long", "nullable" to false),
                mapOf("name" to "title", "type" to "String", "nullable" to false)
            ),
            "relationFields" to emptyList<Map<String, Any?>>(),
        ),
    )

    assertTrue(content.contains("class PROPERTY_NAMES"))
    assertTrue(content.contains("val props = PROPERTY_NAMES()"))
    assertTrue(content.contains("fun specify("))
    assertTrue(content.contains("fun predicateById(id: Any): JpaPredicate<VideoPost>"))
    assertFalse(content.contains("AggregatePredicate<AggVideoPost, VideoPost>"))
}
```

- [ ] **Step 2: Run the schema planner and renderer tests and verify they fail**

Run:

```bash
./gradlew :cap4k-plugin-pipeline-generator-aggregate:test --tests "com.only4.cap4k.plugin.pipeline.generator.aggregate.AggregateArtifactPlannerTest" :cap4k-plugin-pipeline-renderer-pebble:test --tests "com.only4.cap4k.plugin.pipeline.renderer.pebble.PebbleArtifactRendererTest"
```

Expected: FAIL because the planner still emits minimal schema context and the template still renders only a constant object.

- [ ] **Step 3: Add schema planning helpers and replace the schema template with the bounded old baseline**

```kotlin
// AggregateSchemaPlanning.kt
internal data class AggregateSchemaPlan(
    val schemaRuntimePackage: String,
    val entityTypeFqn: String,
    val aggregateTypeFqn: String,
    val qEntityTypeFqn: String,
    val idType: String,
    val wrapperEnabled: Boolean,
    val fields: List<Map<String, Any?>>,
    val relationFields: List<Map<String, Any?>>,
)
```

```kotlin
// SchemaArtifactPlanner.kt
val selection = AggregateArtifactSelection.from(config)
val entity = model.entities.singleOrNull { it.name == schema.entityName && it.packageName.endsWith(schema.entityName.toSnakeCase()) }
val schemaPlan = AggregateSchemaPlanning.from(schema, entity, model, selection)
context = mapOf(
    "packageName" to schema.packageName,
    "typeName" to schema.name,
    "entityName" to schema.entityName,
    "schemaRuntimePackage" to schemaPlan.schemaRuntimePackage,
    "entityTypeFqn" to schemaPlan.entityTypeFqn,
    "aggregateTypeFqn" to schemaPlan.aggregateTypeFqn,
    "qEntityTypeFqn" to schemaPlan.qEntityTypeFqn,
    "idType" to schemaPlan.idType,
    "wrapperEnabled" to schemaPlan.wrapperEnabled,
    "fields" to schemaPlan.fields,
    "relationFields" to schemaPlan.relationFields,
    "comment" to schema.comment,
)
```

```pebble
package {{ packageName }}

{{ use(schemaRuntimePackage ~ ".SchemaSpecification") -}}
{{ use(schemaRuntimePackage ~ ".SubqueryConfigure") -}}
{{ use(schemaRuntimePackage ~ ".ExpressionBuilder") -}}
{{ use(schemaRuntimePackage ~ ".PredicateBuilder") -}}
{{ use(schemaRuntimePackage ~ ".OrderBuilder") -}}
{{ use(schemaRuntimePackage ~ ".Field") -}}
{{ use("com.only4.cap4k.ddd.domain.repo.JpaPredicate") -}}
{{ use(entityTypeFqn) -}}
{%- if wrapperEnabled and aggregateTypeFqn != "" -%}
{{ use("com.only4.cap4k.ddd.core.domain.aggregate.AggregatePredicate") -}}
{{ use(aggregateTypeFqn) -}}
{%- endif -%}
{{ imports() }}

class {{ typeName }}(
    private val root: Root<{{ entityName }}>? = null,
    private val criteriaBuilder: CriteriaBuilder? = null,
) {
    class PROPERTY_NAMES {
{% for field in fields %}
        val {{ field.name }} = "{{ field.name }}"
{% endfor %}
    }

    companion object {
        val props = PROPERTY_NAMES()

        fun specify(builder: PredicateBuilder<{{ typeName }}>): Specification<{{ entityName }}> =
            specify(builder, false, emptyList())

        fun predicateById(id: Any): JpaPredicate<{{ entityName }}> = JpaPredicate.byId({{ entityName }}::class.java, id)
{%- if wrapperEnabled and aggregateTypeFqn != "" %}
        fun predicateByIdAggregate(id: Any): AggregatePredicate<Agg{{ entityName }}, {{ entityName }}> =
            JpaPredicate.byId({{ entityName }}::class.java, id).toAggregatePredicate(Agg{{ entityName }}::class.java)
{%- endif %}
    }
}
```

- [ ] **Step 4: Re-run the schema tests and verify they pass**

Run:

```bash
./gradlew :cap4k-plugin-pipeline-generator-aggregate:test --tests "com.only4.cap4k.plugin.pipeline.generator.aggregate.AggregateArtifactPlannerTest" :cap4k-plugin-pipeline-renderer-pebble:test --tests "com.only4.cap4k.plugin.pipeline.renderer.pebble.PebbleArtifactRendererTest"
```

Expected: PASS, with schema output restored to the bounded query-DSL baseline and wrapper-dependent surface removed when wrappers are disabled.

- [ ] **Step 5: Commit**

```bash
git add cap4k-plugin-pipeline-generator-aggregate/src/main/kotlin/com/only4/cap4k/plugin/pipeline/generator/aggregate/AggregateSchemaPlanning.kt cap4k-plugin-pipeline-generator-aggregate/src/main/kotlin/com/only4/cap4k/plugin/pipeline/generator/aggregate/SchemaArtifactPlanner.kt cap4k-plugin-pipeline-generator-aggregate/src/main/kotlin/com/only4/cap4k/plugin/pipeline/generator/aggregate/AggregateDerivedTypeReferences.kt cap4k-plugin-pipeline-renderer-pebble/src/main/resources/presets/ddd-default/aggregate/schema.kt.peb cap4k-plugin-pipeline-generator-aggregate/src/test/kotlin/com/only4/cap4k/plugin/pipeline/generator/aggregate/AggregateArtifactPlannerTest.kt cap4k-plugin-pipeline-renderer-pebble/src/test/kotlin/com/only4/cap4k/plugin/pipeline/renderer/pebble/PebbleArtifactRendererTest.kt
git commit -m "feat: restore aggregate schema baseline"
```

### Task 6: Harden Aggregate Template Formatting Tests

**Files:**
- Modify: `cap4k-plugin-pipeline-renderer-pebble/src/test/kotlin/com/only4/cap4k/plugin/pipeline/renderer/pebble/PebbleArtifactRendererTest.kt`
- Modify: `cap4k-plugin-pipeline-renderer-pebble/src/main/resources/presets/ddd-default/aggregate/factory.kt.peb`
- Modify: `cap4k-plugin-pipeline-renderer-pebble/src/main/resources/presets/ddd-default/aggregate/specification.kt.peb`
- Modify: `cap4k-plugin-pipeline-renderer-pebble/src/main/resources/presets/ddd-default/aggregate/wrapper.kt.peb`
- Modify: `cap4k-plugin-pipeline-renderer-pebble/src/main/resources/presets/ddd-default/aggregate/unique_query.kt.peb`
- Modify: `cap4k-plugin-pipeline-renderer-pebble/src/main/resources/presets/ddd-default/aggregate/unique_query_handler.kt.peb`
- Modify: `cap4k-plugin-pipeline-renderer-pebble/src/main/resources/presets/ddd-default/aggregate/unique_validator.kt.peb`

- [ ] **Step 1: Write failing full-file formatting assertions for representative aggregate templates**

```kotlin
@Test
fun `aggregate wrapper template keeps stable import and class spacing`() {
    val content = renderSingle(
        templateId = "aggregate/wrapper.kt.peb",
        context = mapOf(
            "packageName" to "com.acme.demo.domain.aggregates.video_post",
            "typeName" to "AggVideoPost",
            "entityName" to "VideoPost",
            "entityTypeFqn" to "com.acme.demo.domain.aggregates.video_post.VideoPost",
            "factoryTypeName" to "VideoPostFactory",
            "factoryTypeFqn" to "com.acme.demo.domain.aggregates.video_post.factory.VideoPostFactory",
            "idType" to "Long",
            "comment" to "video post",
        ),
    )

    assertFalse(content.contains("\n\n\n"))
    assertFalse(content.contains("\n        \n"))
    assertTrue(content.contains("class AggVideoPost("))
}
```

```kotlin
@Test
fun `aggregate unique query template does not collapse constructor fields onto one line`() {
    val content = renderSingle(
        templateId = "aggregate/unique_query.kt.peb",
        context = uniqueQueryContext(),
    )

    assertFalse(content.contains("val slug: String,        val excludeVideoPostId: Long?"))
    assertTrue(content.contains("val slug: String"))
    assertTrue(content.contains("val excludeVideoPostId: Long?"))
}
```

- [ ] **Step 2: Run the renderer tests and verify they fail**

Run:

```bash
./gradlew :cap4k-plugin-pipeline-renderer-pebble:test --tests "com.only4.cap4k.plugin.pipeline.renderer.pebble.PebbleArtifactRendererTest"
```

Expected: FAIL on at least one aggregate template due to extra blank lines, unstable indentation, or collapsed whitespace.

- [ ] **Step 3: Normalize the aggregate templates without adding a global formatter**

```pebble
package {{ packageName }}

{{ imports() }}

class {{ typeName }}(
    ...
) {
    ...
}
```

Apply the same bounded cleanup pattern to:

- `factory.kt.peb`
- `specification.kt.peb`
- `wrapper.kt.peb`
- `unique_query.kt.peb`
- `unique_query_handler.kt.peb`
- `unique_validator.kt.peb`

Do not add a renderer-wide post-processor. Fix the templates themselves and keep the checks local to aggregate coverage.

- [ ] **Step 4: Re-run the renderer tests and verify they pass**

Run:

```bash
./gradlew :cap4k-plugin-pipeline-renderer-pebble:test --tests "com.only4.cap4k.plugin.pipeline.renderer.pebble.PebbleArtifactRendererTest"
```

Expected: PASS, with aggregate templates now protected by formatting-floor assertions.

- [ ] **Step 5: Commit**

```bash
git add cap4k-plugin-pipeline-renderer-pebble/src/test/kotlin/com/only4/cap4k/plugin/pipeline/renderer/pebble/PebbleArtifactRendererTest.kt cap4k-plugin-pipeline-renderer-pebble/src/main/resources/presets/ddd-default/aggregate/factory.kt.peb cap4k-plugin-pipeline-renderer-pebble/src/main/resources/presets/ddd-default/aggregate/specification.kt.peb cap4k-plugin-pipeline-renderer-pebble/src/main/resources/presets/ddd-default/aggregate/wrapper.kt.peb cap4k-plugin-pipeline-renderer-pebble/src/main/resources/presets/ddd-default/aggregate/unique_query.kt.peb cap4k-plugin-pipeline-renderer-pebble/src/main/resources/presets/ddd-default/aggregate/unique_query_handler.kt.peb cap4k-plugin-pipeline-renderer-pebble/src/main/resources/presets/ddd-default/aggregate/unique_validator.kt.peb
git commit -m "test: harden aggregate template formatting coverage"
```

### Task 7: Add Functional Fixtures for Minimal and Opt-In Aggregate Output

**Files:**
- Create: `cap4k-plugin-pipeline-gradle/src/test/resources/functional/aggregate-minimal-sample/build.gradle.kts`
- Create: `cap4k-plugin-pipeline-gradle/src/test/resources/functional/aggregate-minimal-sample/schema.sql`
- Modify: `cap4k-plugin-pipeline-gradle/src/test/resources/functional/aggregate-sample/build.gradle.kts`
- Modify: `cap4k-plugin-pipeline-gradle/src/test/kotlin/com/only4/cap4k/plugin/pipeline/gradle/PipelinePluginFunctionalTest.kt`

- [ ] **Step 1: Write failing functional tests for minimal defaults and explicit opt-in output**

```kotlin
@Test
fun `cap4kGenerate aggregate minimal sample writes only fixed baseline output`() {
    val projectDir = Files.createTempDirectory("pipeline-functional-aggregate-minimal")
    copyFixture(projectDir, "aggregate-minimal-sample")

    val result = GradleRunner.create()
        .withProjectDir(projectDir.toFile())
        .withPluginClasspath()
        .withArguments("cap4kGenerate")
        .build()

    assertTrue(result.output.contains("BUILD SUCCESSFUL"))
    assertTrue(projectDir.resolve("demo-domain/src/main/kotlin/com/acme/demo/domain/aggregates/video_post/VideoPost.kt").toFile().exists())
    assertTrue(projectDir.resolve("demo-domain/src/main/kotlin/com/acme/demo/domain/_share/meta/video_post/SVideoPost.kt").toFile().exists())
    assertTrue(projectDir.resolve("demo-adapter/src/main/kotlin/com/acme/demo/adapter/domain/repositories/VideoPostRepository.kt").toFile().exists())
    assertFalse(projectDir.resolve("demo-domain/src/main/kotlin/com/acme/demo/domain/aggregates/video_post/factory/VideoPostFactory.kt").toFile().exists())
    assertFalse(projectDir.resolve("demo-domain/src/main/kotlin/com/acme/demo/domain/aggregates/video_post/specification/VideoPostSpecification.kt").toFile().exists())
    assertFalse(projectDir.resolve("demo-domain/src/main/kotlin/com/acme/demo/domain/aggregates/video_post/AggVideoPost.kt").toFile().exists())
}

@Test
fun `cap4kGenerate aggregate opt in sample writes selected optional outputs`() {
    val projectDir = Files.createTempDirectory("pipeline-functional-aggregate-opt-in")
    copyFixture(projectDir, "aggregate-sample")

    val result = GradleRunner.create()
        .withProjectDir(projectDir.toFile())
        .withPluginClasspath()
        .withArguments("cap4kGenerate")
        .build()

    assertTrue(result.output.contains("BUILD SUCCESSFUL"))
    assertTrue(projectDir.resolve("demo-domain/src/main/kotlin/com/acme/demo/domain/aggregates/video_post/factory/VideoPostFactory.kt").toFile().exists())
    assertTrue(projectDir.resolve("demo-domain/src/main/kotlin/com/acme/demo/domain/aggregates/video_post/specification/VideoPostSpecification.kt").toFile().exists())
    assertTrue(projectDir.resolve("demo-domain/src/main/kotlin/com/acme/demo/domain/aggregates/video_post/AggVideoPost.kt").toFile().exists())
    assertTrue(projectDir.resolve("demo-application/src/main/kotlin/com/acme/demo/application/queries/video_post/unique/UniqueVideoPostSlugQry.kt").toFile().exists())
}
```

- [ ] **Step 2: Run the functional tests and verify they fail**

Run:

```bash
./gradlew :cap4k-plugin-pipeline-gradle:test --tests "com.only4.cap4k.plugin.pipeline.gradle.PipelinePluginFunctionalTest"
```

Expected: FAIL because there is no minimal aggregate fixture yet and current aggregate defaults still implicitly assume richer output.

- [ ] **Step 3: Create a minimal fixture and pin the existing aggregate fixture to explicit opt-ins**

```kotlin
// aggregate-minimal-sample/build.gradle.kts
cap4k {
    project {
        basePackage.set("com.acme.demo")
        domainModulePath.set("demo-domain")
        applicationModulePath.set("demo-application")
        adapterModulePath.set("demo-adapter")
    }
    sources {
        db {
            enabled.set(true)
            url.set("jdbc:h2:file:$dbFilePath;MODE=MySQL;DB_CLOSE_DELAY=-1;DATABASE_TO_UPPER=false;INIT=RUNSCRIPT FROM '$schemaScriptPath'")
            username.set("sa")
            password.set("secret")
            schema.set("PUBLIC")
            includeTables.set(listOf("video_post"))
        }
    }
    generators {
        aggregate { enabled.set(true) }
    }
}
```

```kotlin
// aggregate-sample/build.gradle.kts
generators {
    aggregate {
        enabled.set(true)
        artifacts {
            factory.set(true)
            specification.set(true)
            wrapper.set(true)
            unique.set(true)
            enumTranslation.set(true)
        }
    }
}
```

- [ ] **Step 4: Re-run the functional tests and verify they pass**

Run:

```bash
./gradlew :cap4k-plugin-pipeline-gradle:test --tests "com.only4.cap4k.plugin.pipeline.gradle.PipelinePluginFunctionalTest"
```

Expected: PASS, with one fixture proving minimal defaults and the other proving opt-in expansion.

- [ ] **Step 5: Commit**

```bash
git add cap4k-plugin-pipeline-gradle/src/test/resources/functional/aggregate-minimal-sample cap4k-plugin-pipeline-gradle/src/test/resources/functional/aggregate-sample/build.gradle.kts cap4k-plugin-pipeline-gradle/src/test/kotlin/com/only4/cap4k/plugin/pipeline/gradle/PipelinePluginFunctionalTest.kt
git commit -m "test: cover aggregate minimal and opt-in generation"
```

### Task 8: Refresh Compile Smoke and Public Aggregate DSL Documentation

**Files:**
- Create: `cap4k-plugin-pipeline-gradle/src/test/resources/functional/aggregate-minimal-compile-sample/build.gradle.kts`
- Create: `cap4k-plugin-pipeline-gradle/src/test/resources/functional/aggregate-minimal-compile-sample/schema.sql`
- Create: `cap4k-plugin-pipeline-gradle/src/test/resources/functional/aggregate-minimal-compile-sample/demo-domain/src/main/kotlin/com/acme/demo/domain/aggregates/video_post/AggregateMinimalDomainCompileSmoke.kt`
- Create: `cap4k-plugin-pipeline-gradle/src/test/resources/functional/aggregate-minimal-compile-sample/demo-adapter/src/main/kotlin/com/acme/demo/adapter/domain/repositories/AggregateMinimalAdapterCompileSmoke.kt`
- Modify: `cap4k-plugin-pipeline-gradle/src/test/resources/functional/aggregate-compile-sample/build.gradle.kts`
- Modify: `cap4k-plugin-pipeline-gradle/src/test/resources/functional/aggregate-compile-sample/demo-domain/src/main/kotlin/com/acme/demo/domain/aggregates/video_post/AggregateCompileSmoke.kt`
- Modify: `cap4k-plugin-pipeline-gradle/src/test/kotlin/com/only4/cap4k/plugin/pipeline/gradle/PipelinePluginCompileFunctionalTest.kt`
- Modify: `cap4k-plugin-pipeline-gradle/README.md`

- [ ] **Step 1: Write failing compile-fixture tests and README assertions**

```kotlin
@Test
fun `aggregate minimal schema generation participates in domain compileKotlin`() {
    val projectDir = Files.createTempDirectory("pipeline-functional-aggregate-minimal-domain-compile")
    FunctionalFixtureSupport.copyCompileFixture(projectDir, "aggregate-minimal-compile-sample")

    val beforeGenerateCompileResult = FunctionalFixtureSupport
        .runner(projectDir, ":demo-domain:compileKotlin")
        .buildAndFail()
    assertEquals(
        TaskOutcome.FAILED,
        beforeGenerateCompileResult.task(":demo-domain:compileKotlin")?.outcome
    )
    assertTrue(beforeGenerateCompileResult.output.contains("SVideoPost"))
}

@Test
fun `aggregate minimal repository generation participates in adapter compileKotlin`() {
    val projectDir = Files.createTempDirectory("pipeline-functional-aggregate-minimal-adapter-compile")
    FunctionalFixtureSupport.copyCompileFixture(projectDir, "aggregate-minimal-compile-sample")

    val beforeGenerateCompileResult = FunctionalFixtureSupport
        .runner(projectDir, ":demo-adapter:compileKotlin")
        .buildAndFail()
    assertEquals(
        TaskOutcome.FAILED,
        beforeGenerateCompileResult.task(":demo-adapter:compileKotlin")?.outcome
    )
    assertTrue(beforeGenerateCompileResult.output.contains("VideoPostRepository"))
}
```

README assertions to add after implementation:

```text
- aggregate fixed baseline output: entity, schema, repository, enum
- aggregate optional outputs: factory, specification, wrapper, unique, enumTranslation
- all optional outputs default to false
- wrapper requires factory
```

- [ ] **Step 2: Run the functional Gradle test task and verify it fails**

Run:

```bash
./gradlew :cap4k-plugin-pipeline-gradle:test --tests "com.only4.cap4k.plugin.pipeline.gradle.PipelinePluginFunctionalTest"
```

Expected: FAIL because the existing compile smoke fixture still assumes factory/specification/wrapper by default and no minimal compile fixture exists yet.

- [ ] **Step 3: Add the minimal compile fixture, opt in the rich compile fixture, and document the new DSL**

```kotlin
// aggregate-minimal-compile-sample/demo-domain/.../AggregateMinimalDomainCompileSmoke.kt
package com.acme.demo.domain.aggregates.video_post

import com.acme.demo.domain._share.meta.video_post.SVideoPost

class AggregateMinimalDomainCompileSmoke {
    fun wire(): Pair<String, Any> =
        Pair(SVideoPost.props.id, SVideoPost.predicateById(1L))
}
```

```kotlin
// aggregate-minimal-compile-sample/demo-adapter/.../AggregateMinimalAdapterCompileSmoke.kt
package com.acme.demo.adapter.domain.repositories

class AggregateMinimalAdapterCompileSmoke(
    private val repository: VideoPostRepository,
) {
    fun wire(): VideoPostRepository = repository
}
```

```kotlin
// aggregate-compile-sample/build.gradle.kts
generators {
    aggregate {
        enabled.set(true)
        artifacts {
            factory.set(true)
            specification.set(true)
            wrapper.set(true)
            unique.set(true)
            enumTranslation.set(true)
        }
    }
}
```

```markdown
## Aggregate Artifact Selection

- fixed baseline output: `entity`, `schema`, `repository`, `enum`
- optional outputs: `factory`, `specification`, `wrapper`, `unique`, `enumTranslation`
- defaults: all optional outputs are `false`
- dependency: `wrapper = true` requires `factory = true`
```

- [ ] **Step 4: Run the focused verification stack and verify it passes**

Run:

```bash
./gradlew :ddd-domain-repo-jpa:test --tests "com.only4.cap4k.ddd.domain.repo.schema.JoinTypeTest" --tests "com.only4.cap4k.ddd.domain.repo.schema.FieldTest" :cap4k-plugin-pipeline-generator-aggregate:test --tests "com.only4.cap4k.plugin.pipeline.generator.aggregate.AggregateArtifactPlannerTest" :cap4k-plugin-pipeline-renderer-pebble:test --tests "com.only4.cap4k.plugin.pipeline.renderer.pebble.PebbleArtifactRendererTest" :cap4k-plugin-pipeline-gradle:test --tests "com.only4.cap4k.plugin.pipeline.gradle.Cap4kProjectConfigFactoryTest" --tests "com.only4.cap4k.plugin.pipeline.gradle.PipelinePluginFunctionalTest" --tests "com.only4.cap4k.plugin.pipeline.gradle.PipelinePluginCompileFunctionalTest"
```

Expected: PASS across runtime, planner, renderer, config, and functional verification.

- [ ] **Step 5: Commit**

```bash
git add cap4k-plugin-pipeline-gradle/src/test/resources/functional/aggregate-minimal-compile-sample cap4k-plugin-pipeline-gradle/src/test/resources/functional/aggregate-compile-sample/build.gradle.kts cap4k-plugin-pipeline-gradle/src/test/resources/functional/aggregate-compile-sample/demo-domain/src/main/kotlin/com/acme/demo/domain/aggregates/video_post/AggregateCompileSmoke.kt cap4k-plugin-pipeline-gradle/src/test/kotlin/com/only4/cap4k/plugin/pipeline/gradle/PipelinePluginCompileFunctionalTest.kt cap4k-plugin-pipeline-gradle/README.md
git commit -m "docs: document aggregate replacement baseline dsl"
```
