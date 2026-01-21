# Unit Generation Export Pre-Apply Implementation Plan

> **For Claude:** REQUIRED SUB-SKILL: Use superpowers:executing-plans to implement this plan task-by-task.

**Goal:** Ensure unit exports are applied before dependent generator collects by adding dynamic planning and pre-apply export logic.

**Architecture:** Add a UnitGenerationCollector that accumulates units per generator, uses GenerationPlan.addAll to maintain a cumulative order, and pre-applies exports for newly ordered units. GenAggregateTask uses the collector for collection, then renders in final order as before.

**Tech Stack:** Kotlin, Gradle, JUnit Jupiter

### Task 1: Enable JUnit tests for cap4k-plugin-codegen

**Files:**
- Modify: `cap4k-plugin-codegen/build.gradle.kts`

**Step 1: Add JUnit dependencies**

```kotlin
    testImplementation(platform(libs.junit.bom))
    testImplementation("org.junit.jupiter:junit-jupiter-api")
    testRuntimeOnly("org.junit.jupiter:junit-jupiter-engine")
    testRuntimeOnly("org.junit.platform:junit-platform-launcher")
```

**Step 2: Enable test task**

```kotlin
tasks.test {
    enabled = true
}
```

**Step 3: Commit**

```bash
git add cap4k-plugin-codegen/build.gradle.kts
git commit -m "test: enable junit for codegen module"
```

### Task 2: Write failing test for pre-apply exports

**Files:**
- Create/Modify: `cap4k-plugin-codegen/src/test/kotlin/com/only4/cap4k/plugin/codegen/generators/unit/UnitGenerationCollectorTest.kt`

**Step 1: Write failing test**

```kotlin
@Test
fun preAppliesExportsBeforeLaterCollect() {
    val context = TestAggregateContext()

    val exportGenerator = object : AggregateUnitGenerator {
        override val tag: String = "enum"
        override val order: Int = 10

        context(ctx: AggregateContext)
        override fun collect(): List<GenerationUnit> {
            return listOf(
                GenerationUnit(
                    id = "enum:test",
                    tag = tag,
                    name = "TestEnum",
                    order = order,
                    templateNodes = emptyList(),
                    context = emptyMap(),
                    exportTypes = mapOf("TestEnum" to "com.example.TestEnum"),
                )
            )
        }
    }

    val dependentGenerator = object : AggregateUnitGenerator {
        override val tag: String = "entity"
        override val order: Int = 20

        context(ctx: AggregateContext)
        override fun collect(): List<GenerationUnit> {
            check(ctx.typeMapping.containsKey("TestEnum")) { "TestEnum should be available before collect" }
            return emptyList()
        }
    }

    val collector = newCollectorInstance()
    val collect = collector.javaClass.methods.firstOrNull {
        it.name == "collect" && it.parameterTypes.size == 2
    } ?: error("UnitGenerationCollector.collect missing")

    collect.invoke(collector, listOf(exportGenerator, dependentGenerator), context)

    assertEquals("com.example.TestEnum", context.typeMapping["TestEnum"])
}
```

**Step 2: Run test to verify it fails**

Run: `./gradlew.bat :cap4k-plugin-codegen:test --tests *UnitGenerationCollectorTest*`
Expected: FAIL (UnitGenerationCollector missing or behavior incorrect)

**Step 3: Commit**

```bash
git add cap4k-plugin-codegen/src/test/kotlin/com/only4/cap4k/plugin/codegen/generators/unit/UnitGenerationCollectorTest.kt
git commit -m "test: capture pre-apply export behavior"
```

### Task 3: Implement dynamic collection and apply exports

**Files:**
- Create: `cap4k-plugin-codegen/src/main/kotlin/com/only4/cap4k/plugin/codegen/generators/unit/UnitGenerationCollector.kt`
- Modify: `cap4k-plugin-codegen/src/main/kotlin/com/only4/cap4k/plugin/codegen/generators/unit/GenerationPlan.kt`
- Modify: `cap4k-plugin-codegen/src/main/kotlin/com/only4/cap4k/plugin/codegen/gradle/GenAggregateTask.kt`

**Step 1: Implement UnitGenerationCollector**

```kotlin
class UnitGenerationCollector @JvmOverloads constructor(
    private val log: LoggerAdapter? = null,
) {
    fun collect(
        generators: List<AggregateUnitGenerator>,
        context: AggregateContext,
    ): List<GenerationUnit> {
        if (generators.isEmpty()) return emptyList()

        val plan = GenerationPlan(log)
        val units = mutableListOf<GenerationUnit>()
        val applied = mutableSetOf<String>()

        generators.forEach { generator ->
            val collected = with(context) { generator.collect() }
            if (collected.isEmpty()) return@forEach

            units.addAll(collected)

            val ordered = plan.addAll(collected)
            ordered.forEach { unit ->
                if (applied.add(unit.id)) {
                    unit.exportTypes.forEach { (simple, full) ->
                        context.typeMapping[simple] = full
                    }
                }
            }
        }

        return units
    }
}
```

**Step 2: Add GenerationPlan.addAll**

```kotlin
private val allUnits = LinkedHashMap<String, GenerationUnit>()

fun addAll(newUnits: List<GenerationUnit>): List<GenerationUnit> {
    newUnits.forEach { unit ->
        if (allUnits.containsKey(unit.id)) {
            log?.warn("Duplicate unit id: ${unit.id}, keep first")
            return@forEach
        }
        allUnits[unit.id] = unit
    }
    return order(allUnits.values.toList())
}
```

**Step 3: Use UnitGenerationCollector in GenAggregateTask**

```kotlin
val collector = UnitGenerationCollector(logAdapter)
val units = collector.collect(generators, context)
```

**Step 4: Run tests to verify pass**

Run: `./gradlew.bat :cap4k-plugin-codegen:test --tests *UnitGenerationCollectorTest*`
Expected: PASS

**Step 5: Commit**

```bash
git add cap4k-plugin-codegen/src/main/kotlin/com/only4/cap4k/plugin/codegen/generators/unit/UnitGenerationCollector.kt \
  cap4k-plugin-codegen/src/main/kotlin/com/only4/cap4k/plugin/codegen/generators/unit/GenerationPlan.kt \
  cap4k-plugin-codegen/src/main/kotlin/com/only4/cap4k/plugin/codegen/gradle/GenAggregateTask.kt

git commit -m "feat: pre-apply unit exports during collection"
```
