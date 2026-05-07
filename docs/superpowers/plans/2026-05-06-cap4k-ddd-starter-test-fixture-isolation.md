# Cap4k DDD Starter Test Fixture Isolation Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Stabilize `:cap4k-ddd-starter:test` by isolating starter smoke-test fixtures, removing root-package scan contamination, and repairing the two confirmed test-fixture self-consistency failures.

**Architecture:** Replace root-package nested test applications with two shared test-only fixture applications under narrow packages: one minimal non-JPA application and one scoped JPA application. Repoint the failing starter smoke tests at those fixture applications, replace broad scan boundaries with explicit `basePackageClasses`, set a non-blank event scan package for the JPA fixture path, and turn off Snowflake worker-table startup for smoke tests that do not need it.

**Tech Stack:** Kotlin, Spring Boot test, JPA, H2, JUnit 5, Gradle.

---

## File Structure

Create shared fixture applications:

- Create `cap4k-ddd-starter/src/test/kotlin/com/only4/cap4k/ddd/fixture/minimal/StarterMinimalTestApplication.kt`
- Create `cap4k-ddd-starter/src/test/kotlin/com/only4/cap4k/ddd/fixture/jpa/StarterJpaTestApplication.kt`
- Create `cap4k-ddd-starter/src/test/kotlin/com/only4/cap4k/ddd/fixture/event/StarterEventScanMarker.kt`

Modify starter smoke tests to use the shared fixture applications:

- Modify `cap4k-ddd-starter/src/test/kotlin/com/only4/cap4k/ddd/AutoConfigurationLoadTest.kt`
- Modify `cap4k-ddd-starter/src/test/kotlin/com/only4/cap4k/ddd/BasicAutoConfigurationTest.kt`
- Modify `cap4k-ddd-starter/src/test/kotlin/com/only4/cap4k/ddd/BeanDependencyTest.kt`
- Modify `cap4k-ddd-starter/src/test/kotlin/com/only4/cap4k/ddd/BeanInitializationTest.kt`
- Modify `cap4k-ddd-starter/src/test/kotlin/com/only4/cap4k/ddd/BeanLifecycleTest.kt`
- Modify `cap4k-ddd-starter/src/test/kotlin/com/only4/cap4k/ddd/CircularDependencyTest.kt`
- Modify `cap4k-ddd-starter/src/test/kotlin/com/only4/cap4k/ddd/CoreInitializationTest.kt`
- Modify `cap4k-ddd-starter/src/test/kotlin/com/only4/cap4k/ddd/JavaVersionTest.kt`
- Modify `cap4k-ddd-starter/src/test/kotlin/com/only4/cap4k/ddd/SimpleAutoConfigurationTest.kt`
- Modify `cap4k-ddd-starter/src/test/kotlin/com/only4/cap4k/ddd/SimpleBeanLoadTest.kt`
- Modify `cap4k-ddd-starter/src/test/kotlin/com/only4/cap4k/ddd/AutoConfigurationContextTest.kt`

No production file changes are planned.

---

### Task 1: Add Shared Test Fixture Applications

**Files:**
- Create: `cap4k-ddd-starter/src/test/kotlin/com/only4/cap4k/ddd/fixture/minimal/StarterMinimalTestApplication.kt`
- Create: `cap4k-ddd-starter/src/test/kotlin/com/only4/cap4k/ddd/fixture/jpa/StarterJpaTestApplication.kt`
- Create: `cap4k-ddd-starter/src/test/kotlin/com/only4/cap4k/ddd/fixture/event/StarterEventScanMarker.kt`

- [ ] **Step 1: Add the event-scan marker package**

Create `cap4k-ddd-starter/src/test/kotlin/com/only4/cap4k/ddd/fixture/event/StarterEventScanMarker.kt`:

```kotlin
package com.only4.cap4k.ddd.fixture.event

class StarterEventScanMarker
```

- [ ] **Step 2: Add the minimal non-JPA test application**

Create `cap4k-ddd-starter/src/test/kotlin/com/only4/cap4k/ddd/fixture/minimal/StarterMinimalTestApplication.kt`:

```kotlin
package com.only4.cap4k.ddd.fixture.minimal

import org.springframework.boot.autoconfigure.SpringBootApplication

@SpringBootApplication(scanBasePackageClasses = [StarterMinimalTestApplication::class])
class StarterMinimalTestApplication
```

- [ ] **Step 3: Add the scoped JPA test application**

Create `cap4k-ddd-starter/src/test/kotlin/com/only4/cap4k/ddd/fixture/jpa/StarterJpaTestApplication.kt`:

```kotlin
package com.only4.cap4k.ddd.fixture.jpa

import com.only4.cap4k.ddd.application.event.persistence.EventHttpSubscriber
import com.only4.cap4k.ddd.application.event.persistence.EventHttpSubscriberJpaRepository
import com.only4.cap4k.ddd.application.persistence.ArchivedRequest
import com.only4.cap4k.ddd.application.persistence.ArchivedRequestJpaRepository
import com.only4.cap4k.ddd.application.persistence.Request
import com.only4.cap4k.ddd.application.persistence.RequestJpaRepository
import com.only4.cap4k.ddd.application.saga.persistence.ArchivedSaga
import com.only4.cap4k.ddd.application.saga.persistence.ArchivedSagaJpaRepository
import com.only4.cap4k.ddd.application.saga.persistence.ArchivedSagaProcess
import com.only4.cap4k.ddd.application.saga.persistence.Saga
import com.only4.cap4k.ddd.application.saga.persistence.SagaJpaRepository
import com.only4.cap4k.ddd.application.saga.persistence.SagaProcess
import com.only4.cap4k.ddd.domain.event.persistence.ArchivedEvent
import com.only4.cap4k.ddd.domain.event.persistence.ArchivedEventJpaRepository
import com.only4.cap4k.ddd.domain.event.persistence.Event
import com.only4.cap4k.ddd.domain.event.persistence.EventJpaRepository
import com.only4.cap4k.ddd.fixture.event.StarterEventScanMarker
import org.springframework.boot.autoconfigure.SpringBootApplication
import org.springframework.boot.autoconfigure.domain.EntityScan
import org.springframework.data.jpa.repository.config.EnableJpaRepositories

@SpringBootApplication(scanBasePackageClasses = [StarterEventScanMarker::class])
@EntityScan(
    basePackageClasses = [
        Event::class,
        ArchivedEvent::class,
        Request::class,
        ArchivedRequest::class,
        Saga::class,
        SagaProcess::class,
        ArchivedSaga::class,
        ArchivedSagaProcess::class,
        EventHttpSubscriber::class,
    ]
)
@EnableJpaRepositories(
    basePackageClasses = [
        EventJpaRepository::class,
        ArchivedEventJpaRepository::class,
        RequestJpaRepository::class,
        ArchivedRequestJpaRepository::class,
        SagaJpaRepository::class,
        ArchivedSagaJpaRepository::class,
        EventHttpSubscriberJpaRepository::class,
    ]
)
class StarterJpaTestApplication
```

- [ ] **Step 4: Verify the new fixture files compile cleanly**

Run:

```powershell
.\gradlew.bat :cap4k-ddd-starter:compileTestKotlin --no-daemon
```

Expected:

- Kotlin test sources compile successfully before repointing the smoke tests.

### Task 2: Repoint Minimal and No-JPA Smoke Tests

**Files:**
- Modify: `cap4k-ddd-starter/src/test/kotlin/com/only4/cap4k/ddd/BasicAutoConfigurationTest.kt`
- Modify: `cap4k-ddd-starter/src/test/kotlin/com/only4/cap4k/ddd/BeanInitializationTest.kt`
- Modify: `cap4k-ddd-starter/src/test/kotlin/com/only4/cap4k/ddd/CircularDependencyTest.kt`
- Modify: `cap4k-ddd-starter/src/test/kotlin/com/only4/cap4k/ddd/SimpleAutoConfigurationTest.kt`

- [ ] **Step 1: Point `BasicAutoConfigurationTest` at the minimal fixture app**

Replace the class-level boot wiring with:

```kotlin
import com.only4.cap4k.ddd.fixture.minimal.StarterMinimalTestApplication

@SpringBootTest(classes = [StarterMinimalTestApplication::class])
@TestPropertySource(
    properties = [
        "cap4k.application.name=test-app",
        "spring.main.allow-bean-definition-overriding=true",
        "spring.datasource.url=jdbc:h2:mem:testdb",
        "spring.datasource.driver-class-name=org.h2.Driver",
        "spring.jpa.hibernate.ddl-auto=none",
        "spring.jpa.show-sql=false",
        "cap4k.ddd.domain.event.enable=false",
        "cap4k.ddd.application.request.enable=false",
        "cap4k.ddd.application.saga.enable=false",
        "cap4k.ddd.application.distributed.locker.enable=false",
        "cap4k.ddd.distributed.id-generator.snowflake.enable=false",
        "logging.level.org.springframework.beans=WARN",
        "logging.level.com.only4.cap4k.ddd=WARN"
    ]
)
```

Then remove the unused nested `BasicTestApplication`.

- [ ] **Step 2: Point `BeanInitializationTest` at the minimal fixture app**

Replace the class-level boot wiring with:

```kotlin
import com.only4.cap4k.ddd.fixture.minimal.StarterMinimalTestApplication

@SpringBootTest(classes = [StarterMinimalTestApplication::class])
@TestPropertySource(
    properties = [
        "cap4k.application.name=test-app",
        "spring.datasource.url=jdbc:h2:mem:testdb",
        "spring.datasource.driver-class-name=org.h2.Driver",
        "spring.jpa.hibernate.ddl-auto=none",
        "spring.jpa.show-sql=false",
        "spring.main.allow-bean-definition-overriding=true",
        "spring.autoconfigure.exclude=org.springframework.boot.autoconfigure.orm.jpa.HibernateJpaAutoConfiguration",
        "cap4k.ddd.domain.event.enable=false",
        "cap4k.ddd.application.request.enable=false",
        "cap4k.ddd.application.saga.enable=false",
        "cap4k.ddd.application.distributed.locker.enable=false",
        "cap4k.ddd.distributed.id-generator.snowflake.enable=false",
        "logging.level.org.springframework.beans=WARN",
        "logging.level.com.only4.cap4k.ddd=INFO"
    ]
)
```

Then delete the nested `BeanInitializationTestApp`. The test should no longer declare `@ComponentScan`, `@EntityScan`, or `@EnableJpaRepositories`.

- [ ] **Step 3: Keep the circular-dependency smoke test on a minimal context**

Update `CircularDependencyTest` to use the minimal fixture app and add the disabling properties:

```kotlin
import com.only4.cap4k.ddd.fixture.minimal.StarterMinimalTestApplication

@SpringBootTest(classes = [StarterMinimalTestApplication::class])
@TestPropertySource(
    properties = [
        "spring.main.allow-bean-definition-overriding=true",
        "cap4k.ddd.domain.event.enable=false",
        "cap4k.ddd.application.request.enable=false",
        "cap4k.ddd.application.saga.enable=false",
        "cap4k.ddd.application.distributed.locker.enable=false",
        "cap4k.ddd.distributed.id-generator.snowflake.enable=false",
        "logging.level.org.springframework.beans=ERROR"
    ]
)
```

Then remove the nested `MinimalTestConfiguration`.

- [ ] **Step 4: Repoint `SimpleAutoConfigurationTest` to the minimal fixture app**

Replace the test boot application reference with:

```kotlin
import com.only4.cap4k.ddd.fixture.minimal.StarterMinimalTestApplication

@SpringBootTest(
    classes = [StarterMinimalTestApplication::class],
    properties = [
        "spring.main.allow-bean-definition-overriding=true",
        "spring.autoconfigure.exclude=com.only4.cap4k.ddd.domain.repo.JpaRepositoryAutoConfiguration,com.only4.cap4k.ddd.domain.event.DomainEventAutoConfiguration,com.only4.cap4k.ddd.application.request.RequestAutoConfiguration,com.only4.cap4k.ddd.application.saga.SagaAutoConfiguration,com.only4.cap4k.ddd.application.event.IntegrationEventAutoConfiguration,com.only4.cap4k.ddd.application.distributed.JdbcLockerAutoConfiguration,com.only4.cap4k.ddd.domain.distributed.SnowflakeAutoConfiguration",
        "logging.level.org.springframework.beans=WARN"
    ]
)
```

Then remove the nested `MinimalTestApp`.

- [ ] **Step 5: Re-run the minimal-context tests**

Run:

```powershell
.\gradlew.bat :cap4k-ddd-starter:test --tests "com.only4.cap4k.ddd.BasicAutoConfigurationTest" --no-daemon
.\gradlew.bat :cap4k-ddd-starter:test --tests "com.only4.cap4k.ddd.BeanInitializationTest" --no-daemon
.\gradlew.bat :cap4k-ddd-starter:test --tests "com.only4.cap4k.ddd.CircularDependencyTest" --no-daemon
.\gradlew.bat :cap4k-ddd-starter:test --tests "com.only4.cap4k.ddd.SimpleAutoConfigurationTest" --no-daemon
```

Expected:

- no context startup failure caused by blank event-scan package
- no `entityManagerFactory` failure in `BeanInitializationTest`
- no accidental Snowflake startup against ``__worker_id``

### Task 3: Repoint JPA Smoke Tests to the Scoped JPA Fixture

**Files:**
- Modify: `cap4k-ddd-starter/src/test/kotlin/com/only4/cap4k/ddd/AutoConfigurationLoadTest.kt`
- Modify: `cap4k-ddd-starter/src/test/kotlin/com/only4/cap4k/ddd/BeanDependencyTest.kt`
- Modify: `cap4k-ddd-starter/src/test/kotlin/com/only4/cap4k/ddd/BeanLifecycleTest.kt`
- Modify: `cap4k-ddd-starter/src/test/kotlin/com/only4/cap4k/ddd/CoreInitializationTest.kt`
- Modify: `cap4k-ddd-starter/src/test/kotlin/com/only4/cap4k/ddd/JavaVersionTest.kt`
- Modify: `cap4k-ddd-starter/src/test/kotlin/com/only4/cap4k/ddd/SimpleBeanLoadTest.kt`
- Modify: `cap4k-ddd-starter/src/test/kotlin/com/only4/cap4k/ddd/AutoConfigurationContextTest.kt`

- [ ] **Step 1: Repair `AutoConfigurationLoadTest` with per-test scoped properties first**

Replace:

```kotlin
@TestPropertySource(locations = ["classpath:application-test.properties"])
```

with inline scoped properties on `AutoConfigurationLoadTest`, for example:

```kotlin
@TestPropertySource(
    properties = [
        "cap4k.application.name=cap4k-ddd-starter-test",
        "spring.application.name=cap4k-ddd-starter-test",
        "spring.main.allow-bean-definition-overriding=true",
        "spring.datasource.url=jdbc:h2:mem:testdb;DB_CLOSE_DELAY=-1;DB_CLOSE_ON_EXIT=FALSE",
        "spring.datasource.driver-class-name=org.h2.Driver",
        "spring.datasource.username=sa",
        "spring.datasource.password=",
        "spring.jpa.hibernate.ddl-auto=create-drop",
        "spring.jpa.show-sql=false",
        "spring.jpa.properties.hibernate.dialect=org.hibernate.dialect.H2Dialect",
        "spring.jpa.properties.hibernate.format_sql=false",
        "logging.level.com.only4.cap4k=INFO",
        "logging.level.org.springframework.beans=WARN",
        "logging.level.org.hibernate=WARN",
        "cap4k.ddd.domain.event.enable=true",
        "cap4k.ddd.domain.event.event-scan-package=com.only4.cap4k.ddd.fixture.event",
        "cap4k.ddd.application.request.enable=true",
        "cap4k.ddd.application.request.schedule.compense-cron=0 */30 * * * ?",
        "cap4k.ddd.application.request.schedule.archive-cron=0 0 3 * * ?",
        "cap4k.ddd.application.saga.enable=true",
        "cap4k.ddd.application.saga.schedule.compense-cron=0 */30 * * * ?",
        "cap4k.ddd.application.saga.schedule.archive-cron=0 0 3 * * ?",
        "cap4k.ddd.application.distributed.locker.enable=true",
        "cap4k.ddd.application.distributed.locker.timeout-seconds=30",
        "cap4k.ddd.application.event.http.enable=false",
        "cap4k.ddd.application.event.rabbitmq.enable=false",
        "cap4k.ddd.application.event.rocketmq.enable=false",
        "cap4k.ddd.distributed.id-generator.snowflake.enable=false"
    ]
)
```

This keeps the property repair fixture-local. Only touch `src/test/resources/application-test.properties` if implementation evidence shows more than one starter smoke fixture truly depends on the shared file.

- [ ] **Step 2: Point the JPA smoke tests at `StarterJpaTestApplication`**

Update these test classes to use:

```kotlin
import com.only4.cap4k.ddd.fixture.jpa.StarterJpaTestApplication

@SpringBootTest(classes = [StarterJpaTestApplication::class])
```

Apply this to:

- `AutoConfigurationLoadTest`
- `BeanDependencyTest`
- `JavaVersionTest`
- `SimpleBeanLoadTest`
- `CoreInitializationTest`

Then remove the old nested root-package `@SpringBootApplication` declarations from those files.

- [ ] **Step 3: Add the scoped event-scan and Snowflake-off properties to inline JPA smoke tests**

Update the inline test properties in these files so they include:

```properties
cap4k.ddd.domain.event.event-scan-package=com.only4.cap4k.ddd.fixture.event
cap4k.ddd.distributed.id-generator.snowflake.enable=false
```

Apply this to:

- `BeanDependencyTest`
- `BeanLifecycleTest`
- `JavaVersionTest`
- `SimpleBeanLoadTest`

Keep the existing H2 and logging properties unchanged unless they conflict with the new fixture applications.

- [ ] **Step 4: Preserve `BeanLifecycleTest`'s listener while narrowing the application**

Update `BeanLifecycleTest` to use:

```kotlin
import com.only4.cap4k.ddd.fixture.jpa.StarterJpaTestApplication

@SpringBootTest(
    classes = [
        StarterJpaTestApplication::class,
        BeanLifecycleTest.TestLifecycleListener::class
    ]
)
```

Add the two fixture properties if they are not already present:

```properties
cap4k.ddd.domain.event.event-scan-package=com.only4.cap4k.ddd.fixture.event
cap4k.ddd.distributed.id-generator.snowflake.enable=false
```

Then remove the nested `BeanLifecycleTestApp`.

- [ ] **Step 5: Remove broad scan fallbacks from `AutoConfigurationContextTest`**

Leave the existing `ApplicationContextRunner` test in place, but remove the broad nested helper application from `AutoConfigurationContextTest` or convert it to use the shared scoped JPA fixture if the disabled helper test still needs a boot application reference.

The file must not retain:

```kotlin
@ComponentScan(basePackages = ["com.only4.cap4k.ddd"])
@EnableJpaRepositories(basePackages = ["com.only4.cap4k.ddd"])
@EntityScan(basePackages = ["com.only4.cap4k.ddd"])
```

- [ ] **Step 6: Re-run the JPA smoke tests with short commands**

Run:

```powershell
.\gradlew.bat :cap4k-ddd-starter:test --tests "com.only4.cap4k.ddd.AutoConfigurationLoadTest" --no-daemon
.\gradlew.bat :cap4k-ddd-starter:test --tests "com.only4.cap4k.ddd.BeanDependencyTest" --no-daemon
.\gradlew.bat :cap4k-ddd-starter:test --tests "com.only4.cap4k.ddd.BeanLifecycleTest" --no-daemon
.\gradlew.bat :cap4k-ddd-starter:test --tests "com.only4.cap4k.ddd.CoreInitializationTest" --no-daemon
.\gradlew.bat :cap4k-ddd-starter:test --tests "com.only4.cap4k.ddd.JavaVersionTest" --no-daemon
.\gradlew.bat :cap4k-ddd-starter:test --tests "com.only4.cap4k.ddd.SimpleBeanLoadTest" --no-daemon
```

Expected:

- no `BeanDefinitionOverrideException` caused by sibling test applications or repositories
- no Snowflake access to ``__worker_id``
- no blank event-scan package failure

### Task 4: Final Verification and Evidence Capture

**Files:**
- No new files required

- [ ] **Step 1: Run the full module test task**

Run:

```powershell
.\gradlew.bat :cap4k-ddd-starter:test --no-daemon
```

Expected:

- the old starter smoke/context cluster is stable
- the already-green runtime and ID-policy tests remain green

- [ ] **Step 2: Record the stable passing classes for the issue handoff**

Capture at minimum whether these classes now pass:

- `AutoConfigurationLoadTest`
- `BasicAutoConfigurationTest`
- `BeanDependencyTest`
- `BeanInitializationTest`
- `BeanLifecycleTest`
- `CircularDependencyTest`
- `CoreInitializationTest`
- `JavaVersionTest`
- `SimpleAutoConfigurationTest`
- `SimpleBeanLoadTest`
- `IdPolicyAutoConfigurationTest`
- `ApplicationSideIdJpaRuntimeTest`
- `AggregateJpaRuntimeDefectReproductionTest`

- [ ] **Step 3: Summarize residual risk only if the full rerun still shows non-isolation failures**

Allowed residual-risk examples:

- an old smoke test assertion is still semantically weak but the context now boots correctly
- a passing runtime fixture remains broad internally but no longer participates in the failing starter smoke contexts

Not allowed as residual risk for this slice:

- root-package cross-fixture scanning
- broad `com.only4.cap4k.ddd` JPA scans in the repaired smoke tests
- stale Snowflake property drift causing ``__worker_id`` access
- no-Hibernate plus JPA-scan `entityManagerFactory` failure
