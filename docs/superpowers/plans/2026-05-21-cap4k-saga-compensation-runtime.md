# Cap4k Saga Compensation Runtime Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Add first-class compensation-oriented Saga runtime support to cap4k's existing request/process replay model, including explicit compensable steps, explicit compensation requests, compensation-aware persistence, compensation resume, and synchronized docs/skills guidance.

**Architecture:** Keep the current whole-Saga replay model and extend it with explicit compensation contracts instead of replacing it with a general workflow engine. Forward steps continue to replay by `processCode`, while completed compensable steps persist reverse-compensation metadata and compensation execution state in the existing `Saga` / `SagaProcess` record family.

**Tech Stack:** Kotlin, Spring, JPA, Gradle, JUnit 5, MockK, cap4k `ddd-core`, `ddd-distributed-saga-jpa`, Markdown docs, source skills, GitHub CLI.

---

## Working Branch

Create and use an isolated worktree before implementing:

```powershell
git -C C:\Users\LD_moxeii\Documents\code\only-workspace\cap4k worktree add .worktrees\saga-compensation-runtime -b feature/saga-compensation-runtime master
Set-Location C:\Users\LD_moxeii\Documents\code\only-workspace\cap4k\.worktrees\saga-compensation-runtime
```

All commands below assume the active directory is:

```text
C:\Users\LD_moxeii\Documents\code\only-workspace\cap4k\.worktrees\saga-compensation-runtime
```

## Source Spec

Use the approved spec as the implementation contract:

- `docs/superpowers/specs/2026-05-21-cap4k-saga-compensation-runtime-design.md`

## File Structure

Create:

- `ddd-core/src/main/kotlin/com/only4/cap4k/ddd/core/application/saga/SagaCompensationRequestedBy.kt`
- `ddd-core/src/main/kotlin/com/only4/cap4k/ddd/core/application/saga/impl/SagaCompensationRequestedException.kt`
- `ddd-core/src/test/kotlin/com/only4/cap4k/ddd/core/application/saga/impl/DefaultSagaSupervisorCompensationTest.kt`
- `ddd-distributed-saga-jpa/src/test/kotlin/com/only4/cap4k/ddd/application/saga/SagaRecordCompensationTest.kt`

Modify:

- `ddd-core/src/main/kotlin/com/only4/cap4k/ddd/core/application/saga/SagaHandler.kt`
- `ddd-core/src/main/kotlin/com/only4/cap4k/ddd/core/application/saga/SagaProcessSupervisor.kt`
- `ddd-core/src/main/kotlin/com/only4/cap4k/ddd/core/application/saga/SagaManager.kt`
- `ddd-core/src/main/kotlin/com/only4/cap4k/ddd/core/application/saga/SagaRecord.kt`
- `ddd-core/src/main/kotlin/com/only4/cap4k/ddd/core/application/saga/impl/DefaultSagaSupervisor.kt`
- `ddd-core/src/test/kotlin/com/only4/cap4k/ddd/core/application/saga/impl/DefaultSagaSupervisorTest.kt`
- `ddd-distributed-saga-jpa/src/main/kotlin/com/only4/cap4k/ddd/application/saga/SagaRecordImpl.kt`
- `ddd-distributed-saga-jpa/src/main/kotlin/com/only4/cap4k/ddd/application/saga/JpaSagaRecordRepository.kt`
- `ddd-distributed-saga-jpa/src/main/kotlin/com/only4/cap4k/ddd/application/saga/JpaSagaScheduleService.kt`
- `ddd-distributed-saga-jpa/src/main/kotlin/com/only4/cap4k/ddd/application/saga/persistence/Saga.kt`
- `ddd-distributed-saga-jpa/src/main/kotlin/com/only4/cap4k/ddd/application/saga/persistence/SagaProcess.kt`
- `ddd-distributed-saga-jpa/src/main/kotlin/com/only4/cap4k/ddd/application/saga/persistence/ArchivedSaga.kt`
- `ddd-distributed-saga-jpa/src/main/kotlin/com/only4/cap4k/ddd/application/saga/persistence/ArchivedSagaProcess.kt`
- `ddd-distributed-saga-jpa/src/main/resources/saga.sql`
- `skills/cap4k-generation/references/sql/saga.sql`
- `ddd-distributed-saga-jpa/src/test/kotlin/com/only4/cap4k/ddd/application/saga/SagaRecordImplTest.kt`
- `ddd-distributed-saga-jpa/src/test/kotlin/com/only4/cap4k/ddd/application/saga/JpaSagaRecordRepositoryTest.kt`
- `ddd-distributed-saga-jpa/src/test/kotlin/com/only4/cap4k/ddd/application/saga/JpaSagaScheduleServiceTest.kt`
- `docs/public/authoring/advanced/saga.md`
- `docs/public/authoring/examples/content-publication-saga.md`
- `docs/public/authoring/advanced/index.md`
- `docs/public/authoring/framework-positioning.md`
- `docs/superpowers/analysis/2026-05-11-cap4k-runtime-support-and-integration-map.md`
- `skills/cap4k-modeling/rules/tactical-modeling.md`
- `skills/shared/rules/advanced-mode-gates.md`
- `skills/cap4k-implementation/workflows/implement-command-slice.md`
- `skills/cap4k-verification/workflows/run-analysis-and-flow-review.md`
- `skills/cap4k-verification/references/gotchas.md`

Do not modify:

- installed `.agents/skills/**`
- console/UI surfaces for operator compensation in this slice
- unrelated request/event runtime modules

## Implementation Order

Implement in this order:

1. lock compensation authoring and control-flow behavior in `ddd-core`
2. expand Saga and Saga-process persistence/state models in `ddd-distributed-saga-jpa`
3. wire repository/scheduler/resume semantics and schema comments
4. update public docs and internal analysis to match the new runtime truth
5. update skills so generated guidance stops recommending old Saga compensation patterns
6. run verification and update `#58` lifecycle state

## Task 1: Prepare Isolation And Baseline

**Files:**

- Read only unless the setup fails.

- [ ] **Step 1: Create the isolated worktree and move into it**

Run:

```powershell
git worktree add .worktrees\saga-compensation-runtime -b feature/saga-compensation-runtime master
Set-Location .worktrees\saga-compensation-runtime
```

Expected:

```text
Preparing worktree (new branch 'feature/saga-compensation-runtime')
HEAD is now at <current-master-sha> docs: add saga compensation runtime design spec
```

- [ ] **Step 2: Confirm the active checkout is the worktree, not `master`**

Run:

```powershell
git status --short --branch
git worktree list
```

Expected:

```text
## feature/saga-compensation-runtime
```

The `master` checkout at `C:\Users\LD_moxeii\Documents\code\only-workspace\cap4k` should remain clean.

- [ ] **Step 3: Confirm the baseline Saga tests pass before edits**

Run:

```powershell
.\gradlew.bat :ddd-core:test --tests "*DefaultSagaSupervisorTest" :ddd-distributed-saga-jpa:test --tests "*SagaRecordImplTest" --tests "*JpaSagaRecordRepositoryTest" --tests "*JpaSagaScheduleServiceTest"
```

Expected: existing tests pass on the untouched baseline.

## Task 2: Lock The Core Compensation API Contract

**Files:**

- Create: `ddd-core/src/test/kotlin/com/only4/cap4k/ddd/core/application/saga/impl/DefaultSagaSupervisorCompensationTest.kt`
- Modify: `ddd-core/src/test/kotlin/com/only4/cap4k/ddd/core/application/saga/impl/DefaultSagaSupervisorTest.kt`

- [ ] **Step 1: Write failing tests for compensable process registration and explicit compensation requests**

Create `DefaultSagaSupervisorCompensationTest.kt` with focused tests like:

```kotlin
package com.only4.cap4k.ddd.core.application.saga.impl

import com.only4.cap4k.ddd.core.application.RequestHandler
import com.only4.cap4k.ddd.core.application.RequestParam
import com.only4.cap4k.ddd.core.application.saga.SagaHandler
import com.only4.cap4k.ddd.core.application.saga.SagaParam
import com.only4.cap4k.ddd.core.application.saga.SagaProcessSupervisor
import com.only4.cap4k.ddd.core.application.saga.SagaRecord
import com.only4.cap4k.ddd.core.application.saga.SagaRecordRepository
import com.only4.cap4k.ddd.core.share.DomainException
import io.mockk.every
import io.mockk.just
import io.mockk.mockk
import io.mockk.runs
import io.mockk.verify
import jakarta.validation.Validator
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertThrows
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import java.time.Duration
import java.time.LocalDateTime

class DefaultSagaSupervisorCompensationTest {

    data class PublishSagaParam(val taskId: String) : SagaParam<String>
    data class CreatePlanRequest(val taskId: String) : RequestParam<CreatePlanResult>
    data class CancelPlanRequest(val planId: String) : RequestParam<Unit>
    data class CreatePlanResult(val planId: String)

    private lateinit var validator: Validator
    private lateinit var repository: SagaRecordRepository
    private lateinit var record: SagaRecord

    @BeforeEach
    fun setUp() {
        validator = mockk()
        repository = mockk()
        record = mockk(relaxed = true)
        every { validator.validate(any<PublishSagaParam>()) } returns emptySet()
        every { validator.validate(record) } returns emptySet()
        every { repository.create() } returns record
        every { repository.save(record) } just runs
        every { record.id } returns "saga-1"
        every { record.isExecuting } returns false
        every { record.init(any(), any(), any(), any(), any(), any()) } just runs
    }

    @Test
    fun `exec compensable process persists compensation request derived from forward result`() {
        val forwardHandler = object : RequestHandler<CreatePlanRequest, CreatePlanResult> {
            override fun exec(request: CreatePlanRequest): CreatePlanResult = CreatePlanResult("plan-9")
        }
        val sagaHandler = object : SagaHandler<PublishSagaParam, String> {
            override fun exec(request: PublishSagaParam): String {
                val result = execCompensableProcess(
                    processCode = "create-plan",
                    request = CreatePlanRequest(request.taskId),
                    compensationCode = "cancel-plan",
                    compensationRequest = { forward -> CancelPlanRequest(forward.planId) }
                )
                return result.planId
            }
        }
        val supervisor = DefaultSagaSupervisor(
            requestHandlers = listOf(forwardHandler, sagaHandler),
            requestInterceptors = emptyList(),
            validator = validator,
            sagaRecordRepository = repository,
            svcName = "svc"
        )

        val result = supervisor.send(PublishSagaParam("task-1"))

        assertEquals("plan-9", result)
        verify {
            record.registerSagaProcessCompensation(
                "create-plan",
                "cancel-plan",
                match<CancelPlanRequest> { it.planId == "plan-9" }
            )
        }
    }

    @Test
    fun `request compensation stops forward execution and records explicit compensation intent`() {
        val sagaHandler = object : SagaHandler<PublishSagaParam, String> {
            override fun exec(request: PublishSagaParam): String {
                requestCompensation("PAYMENT_REJECTED", "payment declined")
            }
        }
        val supervisor = DefaultSagaSupervisor(
            requestHandlers = listOf(sagaHandler),
            requestInterceptors = emptyList(),
            validator = validator,
            sagaRecordRepository = repository,
            svcName = "svc"
        )

        val ex = assertThrows(DomainException::class.java) {
            supervisor.send(PublishSagaParam("task-1"))
        }

        verify {
            record.requestCompensation(any(), "PAYMENT_REJECTED", "payment declined", any(), null)
        }
        verify(exactly = 0) { record.endSaga(any(), any()) }
        assertEquals(true, ex.message!!.contains("PAYMENT_REJECTED"))
    }
}
```

- [ ] **Step 2: Run the new core compensation tests and confirm they fail**

Run:

```powershell
.\gradlew.bat :ddd-core:test --tests "*DefaultSagaSupervisorCompensationTest"
```

Expected: compile failures for missing Saga APIs and compensation-record methods.

- [ ] **Step 3: Add the new core compensation API surface and control-flow implementation**

Create `SagaCompensationRequestedBy.kt`:

```kotlin
package com.only4.cap4k.ddd.core.application.saga

enum class SagaCompensationRequestedBy {
    INTERNAL,
    OPERATOR
}
```

Create `SagaCompensationRequestedException.kt`:

```kotlin
package com.only4.cap4k.ddd.core.application.saga.impl

internal class SagaCompensationRequestedException(
    val code: String,
    override val message: String
) : RuntimeException(message)
```

Update `SagaHandler.kt`:

```kotlin
fun <SUB_REQUEST : RequestParam<SUB_RESPONSE>, SUB_RESPONSE : Any> execCompensableProcess(
    processCode: String,
    request: SUB_REQUEST,
    compensationCode: String,
    compensationRequest: (SUB_RESPONSE) -> RequestParam<*>
): SUB_RESPONSE = SagaProcessSupervisor.instance.sendCompensableProcess(
    processCode = processCode,
    request = request,
    compensationCode = compensationCode,
    compensationRequest = compensationRequest
)

fun requestCompensation(code: String, reason: String): Nothing =
    SagaProcessSupervisor.instance.requestCompensation(code, reason)
```

Update `SagaProcessSupervisor.kt`:

```kotlin
fun <REQUEST : RequestParam<RESPONSE>, RESPONSE : Any> sendCompensableProcess(
    processCode: String,
    request: REQUEST,
    compensationCode: String,
    compensationRequest: (RESPONSE) -> RequestParam<*>
): RESPONSE

fun requestCompensation(code: String, reason: String): Nothing
```

Update `SagaManager.kt`:

```kotlin
fun requestCompensation(
    sagaId: String,
    code: String,
    reason: String
)
```

Update `SagaRecord.kt` with the new contract:

```kotlin
fun registerSagaProcessCompensation(
    processCode: String,
    compensationCode: String,
    param: RequestParam<*>
)

fun requestCompensation(
    now: LocalDateTime,
    code: String,
    reason: String,
    requestedBy: SagaCompensationRequestedBy,
    sourceProcessCode: String? = null
)

fun beginCompensation(now: LocalDateTime): Boolean

fun endCompensation(now: LocalDateTime)

fun markManualRepairRequired(now: LocalDateTime)

fun compensationProcessCodesToRun(): List<String>

fun getSagaProcessCompensationRequest(processCode: String): RequestParam<*>?

fun beginSagaCompensationProcess(now: LocalDateTime, processCode: String)

fun endSagaCompensationProcess(now: LocalDateTime, processCode: String, result: Any = Unit)

fun sagaCompensationProcessOccurredException(now: LocalDateTime, processCode: String, throwable: Throwable)
```

Update `DefaultSagaSupervisor.kt` so that:

```kotlin
override fun <REQUEST : RequestParam<RESPONSE>, RESPONSE : Any> sendCompensableProcess(
    processCode: String,
    request: REQUEST,
    compensationCode: String,
    compensationRequest: (RESPONSE) -> RequestParam<*>
): RESPONSE {
    val sagaRecord = requireNotNull(SAGA_RECORD_THREAD_LOCAL.get()) { "No SagaRecord found in thread local" }
    val response = sendProcess(processCode, request)
    sagaRecord.registerSagaProcessCompensation(processCode, compensationCode, compensationRequest(response))
    sagaRecordRepository.save(sagaRecord)
    return response
}

override fun requestCompensation(code: String, reason: String): Nothing {
    val sagaRecord = requireNotNull(SAGA_RECORD_THREAD_LOCAL.get()) { "No SagaRecord found in thread local" }
    sagaRecord.requestCompensation(LocalDateTime.now(), code, reason, SagaCompensationRequestedBy.INTERNAL, null)
    sagaRecordRepository.save(sagaRecord)
    throw SagaCompensationRequestedException(code, "Saga compensation requested: $code - $reason")
}
```

Then update `internalSend(request, sagaRecord)` to catch `SagaCompensationRequestedException`, run compensation immediately, and throw `DomainException` outward after compensation finishes or fails:

```kotlin
        } catch (requested: SagaCompensationRequestedException) {
            compensateNow(sagaRecord)
            throw DomainException(requested.message)
        } catch (throwable: Throwable) {
            sagaRecord.occurredException(LocalDateTime.now(), throwable)
            sagaRecordRepository.save(sagaRecord)
            throw throwable
        }
```

Add a focused private helper:

```kotlin
private fun compensateNow(sagaRecord: SagaRecord) {
    if (!sagaRecord.beginCompensation(LocalDateTime.now())) {
        return
    }

    var manualRepair = false
    for (processCode in sagaRecord.compensationProcessCodesToRun()) {
        val compensationRequest = sagaRecord.getSagaProcessCompensationRequest(processCode)
        if (compensationRequest == null) {
            manualRepair = true
            continue
        }
        try {
            sagaRecord.beginSagaCompensationProcess(LocalDateTime.now(), processCode)
            RequestSupervisor.instance.send(compensationRequest)
            sagaRecord.endSagaCompensationProcess(LocalDateTime.now(), processCode, Unit)
            sagaRecordRepository.save(sagaRecord)
        } catch (ex: Throwable) {
            sagaRecord.sagaCompensationProcessOccurredException(LocalDateTime.now(), processCode, ex)
            sagaRecordRepository.save(sagaRecord)
            throw ex
        }
    }

    if (manualRepair) sagaRecord.markManualRepairRequired(LocalDateTime.now())
    else sagaRecord.endCompensation(LocalDateTime.now())
    sagaRecordRepository.save(sagaRecord)
}
```

Implementation note: keep `execProcess(...)` behavior untouched for non-compensable steps.

- [ ] **Step 4: Run the core Saga tests and make them pass**

Run:

```powershell
.\gradlew.bat :ddd-core:test --tests "*DefaultSagaSupervisorCompensationTest" --tests "*DefaultSagaSupervisorTest"
```

Expected: both the new focused compensation tests and the existing supervisor tests pass.

- [ ] **Step 5: Commit the core runtime API slice**

Run:

```powershell
git add ddd-core/src/main/kotlin/com/only4/cap4k/ddd/core/application/saga ddd-core/src/test/kotlin/com/only4/cap4k/ddd/core/application/saga/impl
git commit -m "feat: add saga compensation core api"
```

## Task 3: Expand Saga And Saga-Process Persistence Model

**Files:**

- Create: `ddd-distributed-saga-jpa/src/test/kotlin/com/only4/cap4k/ddd/application/saga/SagaRecordCompensationTest.kt`
- Modify: `ddd-distributed-saga-jpa/src/main/kotlin/com/only4/cap4k/ddd/application/saga/SagaRecordImpl.kt`
- Modify: `ddd-distributed-saga-jpa/src/main/kotlin/com/only4/cap4k/ddd/application/saga/persistence/Saga.kt`
- Modify: `ddd-distributed-saga-jpa/src/main/kotlin/com/only4/cap4k/ddd/application/saga/persistence/SagaProcess.kt`
- Modify: `ddd-distributed-saga-jpa/src/main/kotlin/com/only4/cap4k/ddd/application/saga/persistence/ArchivedSaga.kt`
- Modify: `ddd-distributed-saga-jpa/src/main/kotlin/com/only4/cap4k/ddd/application/saga/persistence/ArchivedSagaProcess.kt`
- Modify: `ddd-distributed-saga-jpa/src/test/kotlin/com/only4/cap4k/ddd/application/saga/SagaRecordImplTest.kt`

- [ ] **Step 1: Write failing persistence tests for compensation metadata, ordering, and manual repair**

Create `SagaRecordCompensationTest.kt` with tests like:

```kotlin
package com.only4.cap4k.ddd.application.saga

import com.only4.cap4k.ddd.application.saga.persistence.Saga
import com.only4.cap4k.ddd.application.saga.persistence.SagaProcess
import com.only4.cap4k.ddd.core.application.saga.SagaCompensationRequestedBy
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import java.time.Duration
import java.time.LocalDateTime

class SagaRecordCompensationTest {

    private lateinit var sagaRecord: SagaRecordImpl
    private val now = LocalDateTime.of(2025, 1, 15, 10, 30, 0)

    @BeforeEach
    fun setUp() {
        sagaRecord = SagaRecordImpl()
        sagaRecord.init(TestSagaParam("publish"), "svc", "PUBLISH_SAGA", now, Duration.ofMinutes(10), 3)
        sagaRecord.beginSaga(now.plusMinutes(1))
    }

    @Test
    fun `register compensation stores final reverse request and marks process ready`() {
        sagaRecord.beginSagaProcess(now.plusMinutes(2), "create-plan", TestRequestParam("plan"))
        sagaRecord.endSagaProcess(now.plusMinutes(3), "create-plan", mapOf("planId" to "plan-9"))

        sagaRecord.registerSagaProcessCompensation(
            "create-plan",
            "cancel-plan",
            TestRequestParam("plan-9")
        )

        val process = sagaRecord.saga.getSagaProcess("create-plan")!!
        assertEquals("cancel-plan", process.compensationCode)
        assertEquals(SagaProcess.SagaCompensationState.READY, process.compensationState)
        assertTrue(process.compensationParam.contains("plan-9"))
    }

    @Test
    fun `request compensation stores root metadata and changes saga state`() {
        sagaRecord.requestCompensation(
            now.plusMinutes(4),
            "PAYMENT_REJECTED",
            "payment declined",
            SagaCompensationRequestedBy.INTERNAL,
            "publish-content"
        )

        assertEquals(Saga.SagaState.COMPENSATION_REQUESTED, sagaRecord.saga.sagaState)
        assertEquals("PAYMENT_REJECTED", sagaRecord.saga.compensationRequestCode)
        assertEquals("publish-content", sagaRecord.saga.compensationSourceProcessCode)
    }

    @Test
    fun `compensation ordering resumes from failed reverse step`() {
        val hold = SagaProcess().apply {
            processCode = "reserve-hold"
            processState = SagaProcess.SagaProcessState.EXECUTED
            executedAt = now.plusMinutes(2)
            compensationCode = "release-hold"
            compensationState = SagaProcess.SagaCompensationState.COMPENSATED
        }
        val plan = SagaProcess().apply {
            processCode = "create-plan"
            processState = SagaProcess.SagaProcessState.EXECUTED
            executedAt = now.plusMinutes(3)
            compensationCode = "cancel-plan"
            compensationState = SagaProcess.SagaCompensationState.FAILED
        }
        sagaRecord.saga.sagaProcesses = mutableListOf(hold, plan)

        assertEquals(listOf("create-plan"), sagaRecord.compensationProcessCodesToRun())
    }
}
```

Also extend `SagaRecordImplTest.kt` with archive/state assertions for the new fields.

- [ ] **Step 2: Run the new persistence tests and confirm they fail**

Run:

```powershell
.\gradlew.bat :ddd-distributed-saga-jpa:test --tests "*SagaRecordCompensationTest" --tests "*SagaRecordImplTest"
```

Expected: compile failures for missing compensation fields, states, and record methods.

- [ ] **Step 3: Implement the expanded Saga and Saga-process state models**

In `Saga.kt`, add compensation root fields and new states. Preserve old numeric values where possible and add new distinct values:

```kotlin
@Column(name = "`compensation_request_code`", nullable = false)
var compensationRequestCode: String = ""

@Column(name = "`compensation_request_reason`")
var compensationRequestReason: String = ""

@Column(name = "`compensation_requested_at`")
var compensationRequestedAt: LocalDateTime? = null

@Column(name = "`compensation_requested_by`", nullable = false)
var compensationRequestedBy: String = ""

@Column(name = "`compensation_source_process_code`", nullable = false)
var compensationSourceProcessCode: String = ""

enum class SagaState(val value: Int, val stateName: String) {
    INIT(0, "init"),
    EXECUTING_FORWARD(-1, "executing-forward"),
    CANCELLED(-2, "cancelled"),
    EXPIRED(-3, "expired"),
    EXHAUSTED(-4, "exhausted"),
    COMPENSATION_REQUESTED(-5, "compensation-requested"),
    COMPENSATING(-6, "compensating"),
    MANUAL_REPAIR_REQUIRED(-7, "manual-repair-required"),
    EXCEPTION(-9, "exception"),
    EXECUTED(1, "executed"),
    COMPENSATED(2, "compensated");
}
```

Add state transition helpers:

```kotlin
fun requestCompensation(
    now: LocalDateTime,
    code: String,
    reason: String,
    requestedBy: SagaCompensationRequestedBy,
    sourceProcessCode: String?
) {
    compensationRequestCode = code
    compensationRequestReason = reason
    compensationRequestedAt = now
    compensationRequestedBy = requestedBy.name
    compensationSourceProcessCode = sourceProcessCode ?: ""
    sagaState = SagaState.COMPENSATION_REQUESTED
}

fun beginCompensation(now: LocalDateTime): Boolean {
    if (sagaState !in setOf(SagaState.COMPENSATION_REQUESTED, SagaState.COMPENSATING)) return false
    sagaState = SagaState.COMPENSATING
    lastTryTime = now
    return true
}

fun endCompensation(now: LocalDateTime) {
    sagaState = SagaState.COMPENSATED
    lastTryTime = now
}

fun markManualRepairRequired(now: LocalDateTime) {
    sagaState = SagaState.MANUAL_REPAIR_REQUIRED
    lastTryTime = now
}
```

In `SagaProcess.kt`, add forward completion and compensation fields:

```kotlin
@Column(name = "`executed_at`")
var executedAt: LocalDateTime? = null

@Column(name = "`compensation_code`", nullable = false)
var compensationCode: String = ""

@Column(name = "`compensation_param`")
var compensationParam: String = ""

@Column(name = "`compensation_param_type`", nullable = false)
var compensationParamType: String = ""

@Column(name = "`compensation_result`")
var compensationResult: String = ""

@Column(name = "`compensation_result_type`", nullable = false)
var compensationResultType: String = ""

@Column(name = "`compensation_exception`")
var compensationException: String? = null

@Column(name = "`compensation_state`", nullable = false)
@Convert(converter = SagaCompensationState.Converter::class)
var compensationState: SagaCompensationState = SagaCompensationState.NONE

@Column(name = "`compensation_last_try_time`")
var compensationLastTryTime: LocalDateTime? = null

@Column(name = "`compensation_tried_times`", nullable = false)
var compensationTriedTimes: Int = 0

@Column(name = "`compensated_at`")
var compensatedAt: LocalDateTime? = null

enum class SagaCompensationState(val value: Int, val stateName: String) {
    NONE(0, "none"),
    READY(1, "ready"),
    COMPENSATING(-1, "compensating"),
    MANUAL_REPAIR_REQUIRED(-7, "manual-repair-required"),
    FAILED(-9, "failed"),
    COMPENSATED(2, "compensated");
}
```

Update `endProcess(...)` so it sets `executedAt = now`.

Add process helpers:

```kotlin
fun registerCompensation(compensationCode: String, param: RequestParam<*>) { ... }
fun beginCompensation(now: LocalDateTime) { ... }
fun endCompensation(now: LocalDateTime, result: Any = Unit) { ... }
fun occurredCompensationException(now: LocalDateTime, ex: Throwable) { ... }
```

Update `SagaRecordImpl.kt` to implement the new `SagaRecord` methods by delegating to `Saga` / `SagaProcess`, including:

```kotlin
override fun compensationProcessCodesToRun(): List<String> =
    saga.sagaProcesses
        .filter { it.processState == SagaProcess.SagaProcessState.EXECUTED }
        .sortedByDescending { it.executedAt }
        .dropWhile { it.compensationState == SagaProcess.SagaCompensationState.COMPENSATED }
        .map { it.processCode }
```

Refine the exact filter if a failed compensation step should anchor resumption more directly, but keep the persisted reverse-order rule intact.

Update `ArchivedSaga.kt` and `ArchivedSagaProcess.kt` so `archiveFrom(...)` copies every new compensation field.

- [ ] **Step 4: Run the JPA entity/record tests and make them pass**

Run:

```powershell
.\gradlew.bat :ddd-distributed-saga-jpa:test --tests "*SagaRecordCompensationTest" --tests "*SagaRecordImplTest"
```

Expected: new compensation tests and existing record tests pass.

- [ ] **Step 5: Commit the expanded persistence model**

Run:

```powershell
git add ddd-distributed-saga-jpa/src/main/kotlin/com/only4/cap4k/ddd/application/saga ddd-distributed-saga-jpa/src/test/kotlin/com/only4/cap4k/ddd/application/saga
git commit -m "feat: persist saga compensation state"
```

## Task 4: Wire Repository, Scheduler, And Schema Semantics

**Files:**

- Modify: `ddd-distributed-saga-jpa/src/main/kotlin/com/only4/cap4k/ddd/application/saga/JpaSagaRecordRepository.kt`
- Modify: `ddd-distributed-saga-jpa/src/main/kotlin/com/only4/cap4k/ddd/application/saga/JpaSagaScheduleService.kt`
- Modify: `ddd-distributed-saga-jpa/src/main/resources/saga.sql`
- Modify: `skills/cap4k-generation/references/sql/saga.sql`
- Modify: `ddd-distributed-saga-jpa/src/test/kotlin/com/only4/cap4k/ddd/application/saga/JpaSagaRecordRepositoryTest.kt`
- Modify: `ddd-distributed-saga-jpa/src/test/kotlin/com/only4/cap4k/ddd/application/saga/JpaSagaScheduleServiceTest.kt`

- [ ] **Step 1: Extend repository tests for runnable compensation states and archive terminal states**

Add or extend tests with expectations like:

```kotlin
@Test
fun `get by next try time includes compensation requested and compensating sagas`() {
    val sagas = listOf(
        createMockSaga("forward-ex", Saga.SagaState.EXCEPTION),
        createMockSaga("comp-requested", Saga.SagaState.COMPENSATION_REQUESTED),
        createMockSaga("compensating", Saga.SagaState.COMPENSATING)
    )
    every { sagaJpaRepository.findAll(any<Specification<Saga>>(), any<PageRequest>()) } returns PageImpl(sagas)

    val records = repository.getByNextTryTime("test-service", testTime.plusMinutes(30), 10)

    assertEquals(3, records.size)
}

@Test
fun `archive includes compensated and manual repair required sagas`() {
    val sagas = listOf(
        createMockSaga("compensated", Saga.SagaState.COMPENSATED),
        createMockSaga("manual", Saga.SagaState.MANUAL_REPAIR_REQUIRED)
    )
    every { sagaJpaRepository.findAll(any<Specification<Saga>>(), any<PageRequest>()) } returns PageImpl(sagas)

    assertEquals(2, repository.archiveByExpireAt("test-service", testTime.plusDays(1), 10))
}
```

- [ ] **Step 2: Extend schedule-service tests for operator/compensation resume semantics**

Add assertions that `compense(...)` still drives `resume(...)` for compensation states and that exceptions in compensation resume do not break lock cleanup:

```kotlin
@Test
fun `compense releases lock when compensation resume throws`() {
    val sagaRecord = createMockSagaRecord("saga-comp")
    every { locker.acquire(compensationLockerKey, any(), any()) } returns true
    every { sagaManager.getByNextTryTime(any(), any()) } returnsMany listOf(listOf(sagaRecord), emptyList())
    every { sagaManager.resume(sagaRecord, any()) } throws RuntimeException("compensation failed")
    every { locker.release(compensationLockerKey, any()) } returns true

    assertDoesNotThrow {
        scheduleService.compense(10, 5, Duration.ofMinutes(5), Duration.ofMinutes(10))
    }

    verify { locker.release(compensationLockerKey, any()) }
}
```

- [ ] **Step 3: Run the repository and scheduler tests and confirm they fail**

Run:

```powershell
.\gradlew.bat :ddd-distributed-saga-jpa:test --tests "*JpaSagaRecordRepositoryTest" --tests "*JpaSagaScheduleServiceTest"
```

Expected: failures because current repository queries and schema comments still only model forward states.

- [ ] **Step 4: Implement the runnable/archive state query updates**

Update `JpaSagaRecordRepository.getByNextTryTime(...)` so runnable states include:

```kotlin
cb.or(
    cb.equal(root.get<Saga.SagaState>(Saga.F_SAGA_STATE), Saga.SagaState.INIT),
    cb.equal(root.get<Saga.SagaState>(Saga.F_SAGA_STATE), Saga.SagaState.EXECUTING_FORWARD),
    cb.equal(root.get<Saga.SagaState>(Saga.F_SAGA_STATE), Saga.SagaState.EXCEPTION),
    cb.equal(root.get<Saga.SagaState>(Saga.F_SAGA_STATE), Saga.SagaState.COMPENSATION_REQUESTED),
    cb.equal(root.get<Saga.SagaState>(Saga.F_SAGA_STATE), Saga.SagaState.COMPENSATING)
)
```

Update `archiveByExpireAt(...)` so terminal archive states include:

```kotlin
cb.or(
    cb.equal(root.get<Saga.SagaState>(Saga.F_SAGA_STATE), Saga.SagaState.CANCELLED),
    cb.equal(root.get<Saga.SagaState>(Saga.F_SAGA_STATE), Saga.SagaState.EXPIRED),
    cb.equal(root.get<Saga.SagaState>(Saga.F_SAGA_STATE), Saga.SagaState.EXHAUSTED),
    cb.equal(root.get<Saga.SagaState>(Saga.F_SAGA_STATE), Saga.SagaState.EXECUTED),
    cb.equal(root.get<Saga.SagaState>(Saga.F_SAGA_STATE), Saga.SagaState.COMPENSATED),
    cb.equal(root.get<Saga.SagaState>(Saga.F_SAGA_STATE), Saga.SagaState.MANUAL_REPAIR_REQUIRED)
)
```

Add `SagaManager.requestCompensation(...)` support in `DefaultSagaSupervisor` by loading the persisted Saga, recording operator intent, saving it, and scheduling the next compensation attempt.

Keep `JpaSagaScheduleService.compense(...)` as the scheduler entrypoint; it should not gain special-case branching beyond the new runnable states.

- [ ] **Step 5: Update the active and reference SQL scripts**

Patch both SQL files with the new root and process columns. The active table should gain:

```sql
`compensation_request_code` varchar(255) NOT NULL DEFAULT '' COMMENT '补偿请求代码',
`compensation_request_reason` text COMMENT '补偿请求原因',
`compensation_requested_at` datetime DEFAULT NULL COMMENT '补偿请求时间',
`compensation_requested_by` varchar(32) NOT NULL DEFAULT '' COMMENT '补偿请求来源',
`compensation_source_process_code` varchar(255) NOT NULL DEFAULT '' COMMENT '补偿触发流程代码',
```

The process table should gain:

```sql
`executed_at` datetime DEFAULT NULL COMMENT '前向流程成功时间',
`compensation_code` varchar(255) NOT NULL DEFAULT '' COMMENT '补偿流程代码',
`compensation_param` text COMMENT '补偿参数',
`compensation_param_type` varchar(255) NOT NULL DEFAULT '' COMMENT '补偿参数类型',
`compensation_result` text COMMENT '补偿结果',
`compensation_result_type` varchar(255) NOT NULL DEFAULT '' COMMENT '补偿结果类型',
`compensation_exception` text COMMENT '补偿异常',
`compensation_state` int NOT NULL DEFAULT '0' COMMENT '补偿状态@E=0:NONE:none|1:READY:ready|-1:COMPENSATING:compensating|-7:MANUAL_REPAIR_REQUIRED:manual-repair-required|-9:FAILED:failed|2:COMPENSATED:compensated;@T=SagaCompensationState;',
`compensation_last_try_time` datetime DEFAULT NULL COMMENT '补偿上次尝试时间',
`compensation_tried_times` int NOT NULL DEFAULT '0' COMMENT '补偿尝试次数',
`compensated_at` datetime DEFAULT NULL COMMENT '补偿完成时间',
```

Mirror the same column additions into `skills/cap4k-generation/references/sql/saga.sql` so analysis/generation references do not drift from runtime DDL.

- [ ] **Step 6: Run the JPA Saga module tests and make them pass**

Run:

```powershell
.\gradlew.bat :ddd-distributed-saga-jpa:test --tests "*JpaSagaRecordRepositoryTest" --tests "*JpaSagaScheduleServiceTest" --tests "*SagaRecordCompensationTest" --tests "*SagaRecordImplTest"
```

Expected: repository, scheduler, and compensation persistence tests pass together.

- [ ] **Step 7: Commit the repository/scheduler/schema slice**

Run:

```powershell
git add ddd-distributed-saga-jpa/src/main/kotlin/com/only4/cap4k/ddd/application/saga ddd-distributed-saga-jpa/src/main/resources/saga.sql skills/cap4k-generation/references/sql/saga.sql
git commit -m "feat: add saga compensation persistence and resume"
```

## Task 5: Update Public Authoring Docs And Runtime Analysis

**Files:**

- Modify: `docs/public/authoring/advanced/saga.md`
- Modify: `docs/public/authoring/examples/content-publication-saga.md`
- Modify: `docs/public/authoring/advanced/index.md`
- Modify: `docs/public/authoring/framework-positioning.md`
- Modify: `docs/superpowers/analysis/2026-05-11-cap4k-runtime-support-and-integration-map.md`

- [ ] **Step 1: Rewrite the public Saga concept page around the supported compensation slice**

Update `advanced/saga.md` so the recommended shape names the new runtime contract:

```md
- 当前 cap4k Saga 在运行时上优先支持的是“补偿导向的 persisted coordination”，不是泛化的 callback-step resume workflow。
- 需要自动补偿的前向步骤，用 `execCompensableProcess(...)` 显式声明补偿流程与最终补偿请求。
- 业务已经明确知道主流程不能继续时，用 `requestCompensation(code, reason)` 显式进入补偿，而不是依赖异常分类或 retry 耗尽。
- waiting-style Saga、外部 callback resume 仍然不在这次能力切片里。
```

Keep the existing "callback main path / polling fallback" teaching intact.

- [ ] **Step 2: Rewrite the content publication Saga example to match the paid-publication compensation flow**

Update `content-publication-saga.md` to show the new authoring shape:

````md
```kotlin
val hold = execCompensableProcess(
    processCode = "reserve-payout-hold",
    request = ReserveCreatorPayoutHoldCmd.Request(taskId),
    compensationCode = "release-payout-hold",
    compensationRequest = { ReleaseCreatorPayoutHoldCmd.Request(taskId) }
)

val plan = execCompensableProcess(
    processCode = "create-entitlement-plan",
    request = CreateAccessEntitlementPlanCmd.Request(taskId),
    compensationCode = "cancel-entitlement-plan",
    compensationRequest = { result -> CancelEntitlementPlanCmd.Request(result.planId) }
)

if (!publish.accepted) {
    requestCompensation("PUBLISH_REJECTED", publish.reason)
}
```
````

Also add the manual-repair rule: non-compensable completed steps still allow other completed compensable steps to roll back, but the Saga ends in `MANUAL_REPAIR_REQUIRED`.

- [ ] **Step 3: Narrow cross-page positioning text that would otherwise over-promise Saga**

Review `advanced/index.md` and `framework-positioning.md`, then patch the Saga lines to stay truthful to this runtime slice:

```md
- Saga 目前优先用于 persisted long-running coordination, retry, recovery, and compensation.
- 这次能力不把 Saga 扩展成完整 waiting / callback resume workflow.
```

Only change the Saga-related wording. Do not broaden this task into a general positioning rewrite.

- [ ] **Step 4: Update the runtime analysis document to match the new compensation semantics**

Patch `2026-05-11-cap4k-runtime-support-and-integration-map.md` so the Saga section includes:

```md
- `SagaHandler.execCompensableProcess(processCode, request, compensationCode, compensationRequest)` executes a forward child request and persists the final compensation request on success.
- `SagaHandler.requestCompensation(code, reason)` records explicit compensation intent and enters compensation immediately in the current Saga execution.
- forward retry exhaustion does not automatically trigger compensation.
- scheduled resume may continue forward replay or compensation replay depending on Saga state.
- operator-driven compensation uses `SagaManager.requestCompensation(...)` and the same persisted state machine.
```

Update the mermaid diagram labels only where they would otherwise be false after the runtime change.

- [ ] **Step 5: Run markdown hygiene checks**

Run:

```powershell
git diff --check
rg -n "requestCompensation|execCompensableProcess|waiting-style|manual repair" docs/public/authoring docs/superpowers/analysis
```

Expected: no whitespace errors and the new terminology appears in the intended doc surfaces.

- [ ] **Step 6: Commit the docs/analysis sync**

Run:

```powershell
git add docs/public/authoring/advanced/saga.md docs/public/authoring/examples/content-publication-saga.md docs/public/authoring/advanced/index.md docs/public/authoring/framework-positioning.md docs/superpowers/analysis/2026-05-11-cap4k-runtime-support-and-integration-map.md
git commit -m "docs: narrow saga guidance to compensation runtime"
```

## Task 6: Update Skills To Match The New Runtime Contract

**Files:**

- Modify: `skills/cap4k-modeling/rules/tactical-modeling.md`
- Modify: `skills/shared/rules/advanced-mode-gates.md`
- Modify: `skills/cap4k-implementation/workflows/implement-command-slice.md`
- Modify: `skills/cap4k-verification/workflows/run-analysis-and-flow-review.md`
- Modify: `skills/cap4k-verification/references/gotchas.md`

- [ ] **Step 1: Narrow the high-level Saga modeling gates**

Patch `tactical-modeling.md` and `advanced-mode-gates.md` from the generic wording:

```md
- Use Saga only for persisted long-running coordination, retry, recovery, compensation, or cross-time waiting.
```

to wording that reflects the current supported slice:

```md
- Use Saga only for persisted long-running coordination, retry, recovery, or compensation when the current runtime contract is sufficient.
- The current cap4k runtime first-class slice is compensation-oriented Saga, not a general callback-resume workflow engine.
```

- [ ] **Step 2: Update implementation guidance so it recommends the new authoring APIs**

Add to `implement-command-slice.md`:

```md
- If a process needs persisted reverse compensation, model that orchestration in Saga and prefer `execCompensableProcess(...)` plus explicit `requestCompensation(...)` over handler-level `try/catch` compensation.
- Do not treat forward retry exhaustion as the trigger for compensation; compensation should come from explicit business or operator intent.
```

Keep the existing zero-trust command rules intact.

- [ ] **Step 3: Update verification guidance and gotchas**

Patch `run-analysis-and-flow-review.md` and `gotchas.md` with checks like:

```md
- [ ] Compensation-oriented Saga uses explicit `requestCompensation(...)` instead of relying on retry exhaustion or framework business exception classes.
- [ ] Compensable forward steps persist reverse-compensation metadata instead of rebuilding compensation requests ad hoc during failure handling.
```

```md
## Saga Compensation Drift

If Saga code still uses handler-level `try/catch` to manually replay reverse commands, review whether the flow should migrate to `execCompensableProcess(...)` and explicit `requestCompensation(...)`.
```

- [ ] **Step 4: Run skills drift checks**

Run:

```powershell
git diff --check
rg -n 'execCompensableProcess|requestCompensation|retry exhaustion|handler-level `try/catch`' skills
```

Expected: new runtime terms appear in the intended skill files and no markdown whitespace errors remain.

- [ ] **Step 5: Commit the skill sync**

Run:

```powershell
git add skills/cap4k-modeling/rules/tactical-modeling.md skills/shared/rules/advanced-mode-gates.md skills/cap4k-implementation/workflows/implement-command-slice.md skills/cap4k-verification/workflows/run-analysis-and-flow-review.md skills/cap4k-verification/references/gotchas.md
git commit -m "docs: sync skills with saga compensation runtime"
```

## Task 7: Full Verification And Issue Lifecycle Update

**Files:**

- Modify externally: GitHub issue `#58`

- [ ] **Step 1: Run the full affected module tests**

Run:

```powershell
.\gradlew.bat :ddd-core:test :ddd-distributed-saga-jpa:test
```

Expected: both modules pass. If only one module fails, fix the failure before closing the branch work.

- [ ] **Step 2: Run final repository hygiene checks**

Run:

```powershell
git diff --check
git status --short
```

Expected: no whitespace errors and only intended tracked files remain changed before the final commit window closes.

- [ ] **Step 3: Final commit for any post-verification cleanup**

If verification or doc wording required additional tracked edits after the previous task commits, finish with:

```powershell
git add -A
git commit -m "chore: finish saga compensation runtime verification"
```

If no tracked edits remain, do not create an empty commit.

## Self-Review Checklist

Before executing this plan, re-check:

- every spec decision has a task:
  - explicit `execCompensableProcess(...)`
  - explicit `requestCompensation(...)`
  - operator-triggered compensation
  - persisted compensation request built from forward result
  - compensation resume from failed reverse step
  - non-compensable completed step -> `MANUAL_REPAIR_REQUIRED`
  - docs/public/authoring sync
  - docs/superpowers/analysis sync
  - `skills/**` sync
- no task relies on "decide later" placeholder behavior
- the method names used in tests and implementation snippets stay consistent across tasks
