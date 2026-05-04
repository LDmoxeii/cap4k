# Cap4k Aggregate Inverse-Navigation Owner and Fetch Policy Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Implement one parent-owned inverse-navigation contract for aggregate-owned parent-child bindings, keep owned parent-child fetch defaults `LAZY`, and restore audit acceptance coverage with a real generated-entity integration test in `only-danmuku-zero`.

**Architecture:** Keep the ownership rule inside cap4k canonical inference: `@P` plus the direct-parent FK `@Ref` resolve to one owned binding, the parent renders the only owner-side `@OneToMany`, and the child renders an optional read-only `@ManyToOne`. Then update planner/renderer/functional fixtures so generated output matches that contract, and finally re-enable a real generated-entity audit test against the previously broken `video_post_processing -> file -> variant` chain.

**Tech Stack:** Kotlin, JUnit 5, Gradle TestKit, Spring Boot, Hibernate/JPA, H2, cap4k pipeline modules (`core`, `generator-aggregate`, `renderer-pebble`, `gradle`), only-danmuku-zero `only-danmuku-start` integration tests.

---

### Task 1: Unify owned direct-parent binding inference in cap4k core

**Files:**
- Modify: `cap4k-plugin-pipeline-core/src/main/kotlin/com/only4/cap4k/plugin/pipeline/core/AggregateRelationInference.kt`
- Modify: `cap4k-plugin-pipeline-core/src/main/kotlin/com/only4/cap4k/plugin/pipeline/core/AggregateInverseRelationInference.kt`
- Test: `cap4k-plugin-pipeline-core/src/test/kotlin/com/only4/cap4k/plugin/pipeline/core/DefaultCanonicalAssemblerTest.kt`

- [ ] **Step 1: Add failing core tests for owned direct-parent binding and invalid local `@Lazy` override**

```kotlin
@Test
fun `assembler keeps direct parent ref inside parent owned relation contract`() {
    val result = DefaultCanonicalAssembler().assemble(
        aggregateProjectConfig(),
        listOf(
            DbSchemaSnapshot(
                tables = listOf(
                    table(
                        "video_post",
                        columns = listOf(
                            column("id", "BIGINT", "Long", false, isPrimaryKey = true),
                        ),
                    ),
                    table(
                        "video_post_item",
                        columns = listOf(
                            column("id", "BIGINT", "Long", false, isPrimaryKey = true),
                            column("video_post_id", "BIGINT", "Long", false, referenceTable = "video_post"),
                            column("label", "VARCHAR", "String", false),
                        ),
                        parentTable = "video_post",
                        aggregateRoot = false,
                        valueObject = true,
                    ),
                ),
            ),
        ),
    )

    assertEquals(
        listOf("VideoPost|items|VideoPostItem|ONE_TO_MANY"),
        result.model.aggregateRelations
            .filter { it.relationType == AggregateRelationType.ONE_TO_MANY }
            .map { "${it.ownerEntityName}|${it.fieldName}|${it.targetEntityName}|${it.relationType}" },
    )
    assertTrue(
        result.model.aggregateRelations.none {
            it.ownerEntityName == "VideoPostItem" &&
                it.targetEntityName == "VideoPost" &&
                it.relationType == AggregateRelationType.MANY_TO_ONE
        }
    )

    val inverse = result.model.aggregateInverseRelations.single()
    assertEquals("VideoPostItem", inverse.ownerEntityName)
    assertEquals("videoPost", inverse.fieldName)
    assertEquals("video_post_id", inverse.joinColumn)
    assertEquals(AggregateFetchType.LAZY, inverse.fetchType)
    assertEquals(false, inverse.insertable)
    assertEquals(false, inverse.updatable)
}

@Test
fun `assembler rejects local lazy override on owned direct parent binding`() {
    val ex = assertThrows<IllegalArgumentException> {
        DefaultCanonicalAssembler().assemble(
            aggregateProjectConfig(),
            listOf(
                DbSchemaSnapshot(
                    tables = listOf(
                        table(
                            "video_post",
                            columns = listOf(column("id", "BIGINT", "Long", false, isPrimaryKey = true)),
                        ),
                        table(
                            "video_post_item",
                            columns = listOf(
                                column("id", "BIGINT", "Long", false, isPrimaryKey = true),
                                column(
                                    "video_post_id",
                                    "BIGINT",
                                    "Long",
                                    false,
                                    referenceTable = "video_post",
                                    lazy = true,
                                ),
                            ),
                            parentTable = "video_post",
                            aggregateRoot = false,
                            valueObject = true,
                        ),
                    ),
                ),
            ),
        )
    }

    assertEquals(
        "owned parent-child direct parent binding does not allow local lazy override: video_post_item.video_post_id",
        ex.message,
    )
}
```

- [ ] **Step 2: Run the core test class to confirm failure**

Run: `.\gradlew.bat :cap4k-plugin-pipeline-core:test --tests "*DefaultCanonicalAssemblerTest*"`  
Expected: FAIL because the direct-parent `@Ref` path still creates an owner-side child `MANY_TO_ONE`, and the local `@Lazy` override is still accepted.

- [ ] **Step 3: Implement the bounded ownership fix in core inference**

```kotlin
// AggregateRelationInference.kt
private fun resolveOwnedParentAnchorColumn(
    child: DbTableSnapshot,
    parentTable: String,
): DbColumnSnapshot {
    val annotated = child.columns
        .filter { it.referenceTable?.equals(parentTable, ignoreCase = true) == true }
        .sortedBy { it.name }

    annotated.firstOrNull { it.lazy != null }?.let { column ->
        throw IllegalArgumentException(
            "owned parent-child direct parent binding does not allow local lazy override: ${child.tableName}.${column.name}"
        )
    }
    annotated.firstOrNull {
        it.explicitRelationType != null && it.explicitRelationType.uppercase(Locale.ROOT) != "MANY_TO_ONE"
    }?.let { column ->
        throw IllegalArgumentException(
            "parent reference relation type must be MANY_TO_ONE in owned parent-child binding: ${child.tableName}.${column.name} -> $parentTable = ${column.explicitRelationType}"
        )
    }

    val fallback = if (annotated.isEmpty()) {
        child.columns
            .filter { it.name.equals("${parentTable}_id", ignoreCase = true) }
            .sortedBy { it.name }
    } else {
        emptyList()
    }
    val candidates = if (annotated.isNotEmpty()) annotated else fallback

    return when (candidates.size) {
        0 -> throw IllegalArgumentException("missing parent reference column for table: ${child.tableName}")
        1 -> candidates.single()
        else -> throw IllegalArgumentException(
            "ambiguous parent reference columns for table ${child.tableName} -> $parentTable: ${candidates.joinToString(", ") { it.name }}"
        )
    }
}
```

```kotlin
// AggregateRelationInference.kt
val directParentColumn = resolveOwnedParentAnchorColumn(child, parentTable)

AggregateRelationModel(
    ownerEntityName = resolvedParent.entityName,
    ownerEntityPackageName = resolvedParent.packageName,
    fieldName = parentChildFieldName(parentTable, child.tableName),
    targetEntityName = target.entityName,
    targetEntityPackageName = target.packageName,
    relationType = AggregateRelationType.ONE_TO_MANY,
    joinColumn = directParentColumn.name,
    fetchType = AggregateFetchType.LAZY,
    nullable = false,
    cascadeTypes = listOf(
        AggregateCascadeType.PERSIST,
        AggregateCascadeType.MERGE,
        AggregateCascadeType.REMOVE,
    ),
    orphanRemoval = true,
    joinColumnNullable = false,
)
```

```kotlin
// AggregateRelationInference.kt
if (table.parentTable?.equals(referenceTable, ignoreCase = true) == true) {
    return@mapNotNull null
}
```

```kotlin
// AggregateInverseRelationInference.kt
if (ownerTable.parentTable?.equals(referenceTable, ignoreCase = true) == true) {
    return@mapNotNull null
}
```

- [ ] **Step 4: Re-run the core test class to confirm pass**

Run: `.\gradlew.bat :cap4k-plugin-pipeline-core:test --tests "*DefaultCanonicalAssemblerTest*"`  
Expected: PASS with one owner-side `ONE_TO_MANY`, one read-only inverse `MANY_TO_ONE`, and fail-fast on owned direct-parent `@Lazy`.

- [ ] **Step 5: Commit**

```bash
git add cap4k-plugin-pipeline-core/src/main/kotlin/com/only4/cap4k/plugin/pipeline/core/AggregateRelationInference.kt cap4k-plugin-pipeline-core/src/main/kotlin/com/only4/cap4k/plugin/pipeline/core/AggregateInverseRelationInference.kt cap4k-plugin-pipeline-core/src/test/kotlin/com/only4/cap4k/plugin/pipeline/core/DefaultCanonicalAssemblerTest.kt
git commit -m "fix: unify owned inverse-navigation binding"
```

### Task 2: Align planner, renderer, functional fixtures, and docs with the new owned binding contract

**Files:**
- Test: `cap4k-plugin-pipeline-generator-aggregate/src/test/kotlin/com/only4/cap4k/plugin/pipeline/generator/aggregate/AggregateArtifactPlannerTest.kt`
- Test: `cap4k-plugin-pipeline-renderer-pebble/src/test/kotlin/com/only4/cap4k/plugin/pipeline/renderer/pebble/PebbleArtifactRendererTest.kt`
- Modify: `cap4k-plugin-pipeline-gradle/src/test/resources/functional/aggregate-relation-sample/schema.sql`
- Modify: `cap4k-plugin-pipeline-gradle/src/test/resources/functional/aggregate-relation-compile-sample/schema.sql`
- Test: `cap4k-plugin-pipeline-gradle/src/test/kotlin/com/only4/cap4k/plugin/pipeline/gradle/PipelinePluginFunctionalTest.kt`
- Test: `cap4k-plugin-pipeline-gradle/src/test/kotlin/com/only4/cap4k/plugin/pipeline/gradle/PipelinePluginCompileFunctionalTest.kt`
- Modify: `cap4k-plugin-pipeline-gradle/README.md`

- [ ] **Step 1: Add failing planner, renderer, and Gradle functional assertions**

```kotlin
@Test
fun `planner keeps child inverse relation read only for owned child child binding`() {
    val plan = AggregateRelationPlanning.planFor(
        entity = entity("VideoPostProcessingFile", "edu.only4.demo.domain.aggregates.video_post_processing"),
        relations = listOf(
            AggregateRelationModel(
                ownerEntityName = "VideoPostProcessingFile",
                ownerEntityPackageName = "edu.only4.demo.domain.aggregates.video_post_processing",
                fieldName = "videoPostProcessingVariants",
                targetEntityName = "VideoPostProcessingVariant",
                targetEntityPackageName = "edu.only4.demo.domain.aggregates.video_post_processing",
                relationType = AggregateRelationType.ONE_TO_MANY,
                joinColumn = "parent_id",
                fetchType = AggregateFetchType.LAZY,
                nullable = false,
                cascadeTypes = listOf(
                    AggregateCascadeType.PERSIST,
                    AggregateCascadeType.MERGE,
                    AggregateCascadeType.REMOVE,
                ),
                orphanRemoval = true,
                joinColumnNullable = false,
            ),
        ),
        inverseRelations = listOf(
            AggregateInverseRelationModel(
                ownerEntityName = "VideoPostProcessingFile",
                ownerEntityPackageName = "edu.only4.demo.domain.aggregates.video_post_processing",
                fieldName = "parent",
                targetEntityName = "VideoPostProcessing",
                targetEntityPackageName = "edu.only4.demo.domain.aggregates.video_post_processing",
                relationType = AggregateRelationType.MANY_TO_ONE,
                joinColumn = "parent_id",
                fetchType = AggregateFetchType.LAZY,
                nullable = false,
                insertable = false,
                updatable = false,
            ),
        ),
    )

    val parent = plan.relationFields.single { it["name"] == "parent" }
    assertEquals("LAZY", parent["fetchType"])
    assertEquals(true, parent["readOnly"])
    assertEquals(false, parent["insertable"])
    assertEquals(false, parent["updatable"])
}
```

```kotlin
@Test
fun `cap4kGenerate rejects local lazy override on owned direct parent binding`() {
    val projectDir = Files.createTempDirectory("pipeline-functional-owned-parent-lazy")
    copyFixture(projectDir, "aggregate-relation-sample")
    val schemaFile = projectDir.resolve("schema.sql")
    schemaFile.writeText(
        schemaFile.readText().replace(
            "@Reference=video_post;",
            "@Reference=video_post;@Lazy=true;",
        ),
    )

    val result = FunctionalFixtureSupport.runner(projectDir, "cap4kGenerate").buildAndFail()

    assertTrue(
        result.output.contains(
            "owned parent-child direct parent binding does not allow local lazy override: video_post_item.video_post_id"
        )
    )
}
```

- [ ] **Step 2: Run planner, renderer, and Gradle relation tests to confirm failure**

Run: `.\gradlew.bat :cap4k-plugin-pipeline-generator-aggregate:test --tests "*AggregateArtifactPlannerTest*" :cap4k-plugin-pipeline-renderer-pebble:test --tests "*PebbleArtifactRendererTest*" :cap4k-plugin-pipeline-gradle:test --tests "*PipelinePluginFunctionalTest*" --tests "*PipelinePluginCompileFunctionalTest*"`  
Expected: FAIL because the functional fixtures still expect direct-parent `@Reference` to create an owner-side child `MANY_TO_ONE`, and there is no fail-fast test for owned direct-parent `@Lazy`.

- [ ] **Step 3: Update fixtures, functional expectations, and README wording**

```sql
-- aggregate-relation-sample/schema.sql
create table video_post_item (
    id bigint not null,
    video_post_id bigint not null comment '@Reference=video_post;',
    label varchar(64) not null,
    primary key (id)
) comment='视频帖子项 @P=video_post;@VO;';
```

```kotlin
// PipelinePluginFunctionalTest.kt
assertTrue(childEntityContent.contains("@Column(name = \"video_post_id\")"))
assertTrue(childEntityContent.contains("var videoPostId: Long = videoPostId"))
assertTrue(childEntityContent.contains("@ManyToOne(fetch = FetchType.LAZY)"))
assertTrue(
    childEntityContent.contains(
        "@JoinColumn(name = \"video_post_id\", nullable = false, insertable = false, updatable = false)"
    )
)
assertFalse(
    childEntityContent.contains(
        "@JoinColumn(name = \"video_post_id\", nullable = false)"
    ) && !childEntityContent.contains("insertable = false")
)
```

```markdown
<!-- README.md -->
> Owned parent-child direct-parent FK bindings (`@P` table + direct-parent `@Ref`) are fixed to `LAZY`.
> Local `@Lazy` override is invalid for that owned relation family; whole-aggregate loading stays under `AggregateLoadPlan`.
```

- [ ] **Step 4: Re-run planner, renderer, and Gradle relation tests to confirm pass**

Run: `.\gradlew.bat :cap4k-plugin-pipeline-generator-aggregate:test --tests "*AggregateArtifactPlannerTest*" :cap4k-plugin-pipeline-renderer-pebble:test --tests "*PebbleArtifactRendererTest*" :cap4k-plugin-pipeline-gradle:test --tests "*PipelinePluginFunctionalTest*" --tests "*PipelinePluginCompileFunctionalTest*"`  
Expected: PASS with parent-owned `ONE_TO_MANY`, child read-only inverse `MANY_TO_ONE`, `LAZY` on both sides, and fail-fast for owned direct-parent `@Lazy`.

- [ ] **Step 5: Commit**

```bash
git add cap4k-plugin-pipeline-generator-aggregate/src/test/kotlin/com/only4/cap4k/plugin/pipeline/generator/aggregate/AggregateArtifactPlannerTest.kt cap4k-plugin-pipeline-renderer-pebble/src/test/kotlin/com/only4/cap4k/plugin/pipeline/renderer/pebble/PebbleArtifactRendererTest.kt cap4k-plugin-pipeline-gradle/src/test/resources/functional/aggregate-relation-sample/schema.sql cap4k-plugin-pipeline-gradle/src/test/resources/functional/aggregate-relation-compile-sample/schema.sql cap4k-plugin-pipeline-gradle/src/test/kotlin/com/only4/cap4k/plugin/pipeline/gradle/PipelinePluginFunctionalTest.kt cap4k-plugin-pipeline-gradle/src/test/kotlin/com/only4/cap4k/plugin/pipeline/gradle/PipelinePluginCompileFunctionalTest.kt cap4k-plugin-pipeline-gradle/README.md
git commit -m "test: align owned inverse-navigation fixtures"
```

### Task 3: Restore real generated-entity audit verification in only-danmuku-zero

**Files:**
- Create: `only-danmuku-zero/only-danmuku-start/src/test/kotlin/edu/only4/danmuku/start/audit/GeneratedEntityAuditIntegrationTest.kt`
- Modify: `only-danmuku-zero/only-danmuku-start/src/test/kotlin/edu/only4/danmuku/start/audit/EngineAuditSmokeTest.kt`

- [ ] **Step 1: Add a failing real generated-entity audit integration test on the previously broken owned chain**

```kotlin
package edu.only4.danmuku.start.audit

import com.only.engine.spi.audit.AuditOperatorProvider
import edu.only4.danmuku.domain.aggregates.video_post_processing.VideoPostProcessing
import edu.only4.danmuku.domain.aggregates.video_post_processing.VideoPostProcessingFile
import edu.only4.danmuku.domain.aggregates.video_post_processing.VideoPostProcessingVariant
import jakarta.persistence.EntityManager
import java.util.UUID
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.autoconfigure.SpringBootApplication
import org.springframework.boot.autoconfigure.domain.EntityScan
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.boot.test.context.TestConfiguration
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Import
import org.springframework.scheduling.annotation.EnableScheduling
import org.springframework.transaction.support.TransactionTemplate

@SpringBootTest(
    classes = [GeneratedEntityAuditIntegrationTest.TestApplication::class],
    webEnvironment = SpringBootTest.WebEnvironment.MOCK,
    properties = [
        "spring.datasource.url=jdbc:h2:mem:generated_entity_audit;MODE=MySQL;DB_CLOSE_DELAY=-1;DATABASE_TO_UPPER=false",
        "spring.datasource.driver-class-name=org.h2.Driver",
        "spring.datasource.username=sa",
        "spring.datasource.password=",
        "spring.jpa.hibernate.ddl-auto=create-drop",
        "only.engine.redis.enable=false",
        "only.engine.oss.enable=false",
        "only.engine.captcha.enable=false",
        "only.engine.sa-token.enable=false",
        "only.engine.security.enable=false",
    ],
)
class GeneratedEntityAuditIntegrationTest(
    @param:Autowired private val entityManager: EntityManager,
    @param:Autowired private val transactionTemplate: TransactionTemplate,
) {

    @Test
    fun `engine audit fills real generated owned chain on insert and update`() {
        val aggregateId = transactionTemplate.execute {
            val root = VideoPostProcessing(
                videoPostId = UUID.randomUUID(),
                totalFiles = 1,
            )
            val file = VideoPostProcessingFile(
                fileIndex = 0,
                uploadId = UUID.randomUUID(),
            )
            val variant = VideoPostProcessingVariant(
                quality = "360p",
                width = 640,
                height = 360,
                videoBitrateKbps = 800,
                audioBitrateKbps = 96,
                bandwidthBps = 896000,
                playlistPath = "video/360p/index.m3u8",
            )
            file.videoPostProcessingVariants += variant
            root.files += file
            entityManager.persist(root)
            entityManager.flush()
            root.id
        }!!

        transactionTemplate.executeWithoutResult {
            val root = entityManager.find(VideoPostProcessing::class.java, aggregateId)
            assertThat(root.createTime).isNotNull
            assertThat(root.updateTime).isNotNull
            assertThat(root.createUserId).isEqualTo(TEST_OPERATOR_ID)
            assertThat(root.updateUserId).isEqualTo(TEST_OPERATOR_ID)

            val file = root.files.single()
            assertThat(file.parent.id).isEqualTo(root.id)
            assertThat(file.createBy).isEqualTo(TEST_OPERATOR_NAME)

            val variant = file.videoPostProcessingVariants.single()
            assertThat(variant.parent.id).isEqualTo(file.id)
            assertThat(variant.createUserId).isEqualTo(TEST_OPERATOR_ID)

            val updateField = VideoPostProcessingFile::class.java.getDeclaredField("transcodeOutputPath")
            updateField.isAccessible = true
            val beforeUpdateTime = file.updateTime
            updateField.set(file, "transcode/output/master.m3u8")
            entityManager.flush()
            entityManager.clear()

            val reloaded = entityManager.createQuery(
                "select f from VideoPostProcessingFile f where f.id = :id",
                VideoPostProcessingFile::class.java,
            ).setParameter("id", file.id).singleResult
            assertThat(reloaded.updateTime).isGreaterThanOrEqualTo(beforeUpdateTime)
            assertThat(reloaded.updateUserId).isEqualTo(TEST_OPERATOR_ID)
            assertThat(reloaded.updateBy).isEqualTo(TEST_OPERATOR_NAME)
        }
    }

    @SpringBootApplication(
        proxyBeanMethods = false,
        exclude = [
            com.baomidou.lock.spring.boot.autoconfigure.LockAutoConfiguration::class,
            com.only4.cap4k.ddd.application.distributed.JdbcLockerAutoConfiguration::class,
            com.only4.cap4k.ddd.application.event.IntegrationEventAutoConfiguration::class,
            com.only4.cap4k.ddd.application.request.RequestAutoConfiguration::class,
            com.only4.cap4k.ddd.application.saga.SagaAutoConfiguration::class,
            com.only4.cap4k.ddd.console.DDDConsoleAutoConfiguration::class,
            com.only4.cap4k.ddd.domain.distributed.SnowflakeAutoConfiguration::class,
            com.only4.cap4k.ddd.domain.event.DomainEventAutoConfiguration::class,
            com.only4.cap4k.ddd.domain.repo.JpaRepositoryAutoConfiguration::class,
            org.redisson.spring.starter.RedissonAutoConfigurationV2::class,
            org.springframework.boot.autoconfigure.data.redis.RedisAutoConfiguration::class,
            org.springframework.boot.autoconfigure.data.redis.RedisReactiveAutoConfiguration::class,
            org.springframework.boot.autoconfigure.data.redis.RedisRepositoriesAutoConfiguration::class,
        ],
    )
    @EnableScheduling
    @EntityScan(
        basePackageClasses = [
            VideoPostProcessing::class,
            VideoPostProcessingFile::class,
            VideoPostProcessingVariant::class,
        ],
    )
    @Import(FixedAuditOperatorConfiguration::class)
    class TestApplication

    @TestConfiguration(proxyBeanMethods = false)
    class FixedAuditOperatorConfiguration {
        @Bean
        fun auditOperatorProvider(): AuditOperatorProvider = object : AuditOperatorProvider {
            override fun currentOperatorId(): Any? = TEST_OPERATOR_ID

            override fun currentOperatorName(): String? = TEST_OPERATOR_NAME
        }
    }

    companion object {
        private val TEST_OPERATOR_ID: UUID = UUID.fromString("00000000-0000-0000-0000-000000000321")
        private const val TEST_OPERATOR_NAME = "generated-entity-audit"
    }
}
```

- [ ] **Step 2: Run generation plus the new integration test to capture the current failure**

Run: `.\gradlew.bat --no-daemon --include-build C:\Users\LD_moxeii\Documents\code\only-workspace\cap4k --include-build C:\Users\LD_moxeii\Documents\code\only-workspace\only-engine cap4kGenerate :only-danmuku-start:test --tests "*GeneratedEntityAuditIntegrationTest*"`  
Expected: FAIL before the cap4k ownership fix is complete, typically with a Hibernate duplicated-column mapping error on `parent_id` or a startup failure while scanning the generated `video_post_processing` chain.

- [ ] **Step 3: Keep the probe smoke as fast coverage and make the generated-entity test the completion gate**

```kotlin
// EngineAuditSmokeTest.kt
@Test
fun `context loads with engine audit and without old jpa auditing bridge`() {
    assertThat(applicationContext.getBean(AuditAutoConfiguration::class.java)).isNotNull
    assertThat(applicationContext.getBean(AuditEntityLifecycleListener::class.java)).isNotNull
    assertThat(applicationContext.containsBean("auditHibernatePropertiesCustomizer")).isTrue
    assertThrows(ClassNotFoundException::class.java) {
        Class.forName("edu.only4.danmuku.adapter.domain._share.configure.JpaAuditingConfig")
    }

    // Keep the probe path as a fast smoke check.
    val created = repository.saveAndFlush(AuditProbeEntity().apply { name = "created" })
    entityManager.clear()
    val persisted = repository.findById(created.id!!).orElseThrow()
    assertThat(persisted.createTime).isNotNull
    assertThat(persisted.updateTime).isNotNull
}
```

The important execution change is not deleting the probe smoke; it is adding the real generated-entity test and treating that new test as the acceptance gate for this slice.

- [ ] **Step 4: Re-run generation and both audit tests to confirm pass**

Run: `.\gradlew.bat --no-daemon --include-build C:\Users\LD_moxeii\Documents\code\only-workspace\cap4k --include-build C:\Users\LD_moxeii\Documents\code\only-workspace\only-engine cap4kGenerate :only-danmuku-start:test --tests "*EngineAuditSmokeTest*" --tests "*GeneratedEntityAuditIntegrationTest*"`  
Expected: PASS, proving the `video_post_processing -> file -> variant` owned chain starts cleanly, persists through parent-side cascades, exposes child back-references as navigation-only, and receives runtime audit values on insert/update.

- [ ] **Step 5: Commit**

```bash
git -C C:\Users\LD_moxeii\Documents\code\only-workspace\only-danmuku-zero add only-danmuku-start/src/test/kotlin/edu/only4/danmuku/start/audit/EngineAuditSmokeTest.kt only-danmuku-start/src/test/kotlin/edu/only4/danmuku/start/audit/GeneratedEntityAuditIntegrationTest.kt
git -C C:\Users\LD_moxeii\Documents\code\only-workspace\only-danmuku-zero commit -m "test: verify audit on generated owned chain"
```

### Task 4: Run the final cross-repository verification matrix

**Files:**
- No new files; verify the repos touched by Tasks 1-3.

- [ ] **Step 1: Run the focused cap4k matrix**

Run: `.\gradlew.bat :cap4k-plugin-pipeline-core:test --tests "*DefaultCanonicalAssemblerTest*" :cap4k-plugin-pipeline-generator-aggregate:test --tests "*AggregateArtifactPlannerTest*" :cap4k-plugin-pipeline-renderer-pebble:test --tests "*PebbleArtifactRendererTest*" :cap4k-plugin-pipeline-gradle:test --tests "*PipelinePluginFunctionalTest*" --tests "*PipelinePluginCompileFunctionalTest*"`  
Expected: PASS across canonical, planner, renderer, functional, and compile-functional coverage.

- [ ] **Step 2: Rebuild only-danmuku-zero against local cap4k and only-engine**

Run: `.\gradlew.bat --no-daemon --include-build C:\Users\LD_moxeii\Documents\code\only-workspace\cap4k --include-build C:\Users\LD_moxeii\Documents\code\only-workspace\only-engine cap4kPlan cap4kGenerate :only-danmuku-start:test --tests "*EngineAuditSmokeTest*" --tests "*GeneratedEntityAuditIntegrationTest*"`  
Expected: PASS with local composite builds, and no fallback to stale published artifacts.

- [ ] **Step 3: Inspect the regenerated owned chain for the expected annotation shape**

Run: `rg -n "@OneToMany|@ManyToOne|insertable = false|updatable = false|FetchType.LAZY|FetchType.EAGER" C:\Users\LD_moxeii\Documents\code\only-workspace\only-danmuku-zero\only-danmuku-domain\build\generated\cap4k\main\kotlin\edu\only4\danmuku\domain\aggregates\video_post_processing\VideoPostProcessing.kt C:\Users\LD_moxeii\Documents\code\only-workspace\only-danmuku-zero\only-danmuku-domain\build\generated\cap4k\main\kotlin\edu\only4\danmuku\domain\aggregates\video_post_processing\VideoPostProcessingFile.kt C:\Users\LD_moxeii\Documents\code\only-workspace\only-danmuku-zero\only-danmuku-domain\build\generated\cap4k\main\kotlin\edu\only4\danmuku\domain\aggregates\video_post_processing\VideoPostProcessingVariant.kt`  
Expected:
- parent entities keep `@OneToMany(fetch = FetchType.LAZY, ...)`
- child back-references are `@ManyToOne(fetch = FetchType.LAZY)`
- child back-references include `insertable = false, updatable = false`
- no owner-side `@ManyToOne(fetch = FetchType.EAGER)` remains on the direct-parent binding

- [ ] **Step 4: Confirm worktrees are clean after the task commits**

Run: `git -C C:\Users\LD_moxeii\Documents\code\only-workspace\cap4k status --short`  
Run: `git -C C:\Users\LD_moxeii\Documents\code\only-workspace\only-danmuku-zero status --short`  
Expected: no uncommitted changes beyond any intentionally staged follow-up outside this plan.
