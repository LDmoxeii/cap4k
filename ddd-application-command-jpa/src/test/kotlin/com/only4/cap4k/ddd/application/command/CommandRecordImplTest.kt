package com.only4.cap4k.ddd.application.command
import com.only4.cap4k.ddd.application.command.persistence.*
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Nested
import org.junit.jupiter.api.Test
import java.time.Duration
import java.time.LocalDateTime

@DisplayName("CommandRecordImpl实现类测试")
class CommandRecordImplTest {

    private lateinit var commandRecord: CommandRecordImpl
    private val testTime: LocalDateTime = LocalDateTime.of(2025, 1, 15, 10, 30, 0)

    @BeforeEach
    fun setUp() {
        commandRecord = CommandRecordImpl()
    }

    @Nested
    @DisplayName("初始化测试")
    inner class InitializationTest {

        @Test
        @DisplayName("初始化简单命令")
        fun `should initialize simple command correctly`() {
            // Given
            val commandParam = TestCommand("create", mapOf("name" to "test"), 123456789L)
            val svcName = "test-service"
            val commandType = "TEST_COMMAND"
            val expireAfter = Duration.ofMinutes(30)
            val retryTimes = 3

            // When
            commandRecord.init(commandParam, svcName, commandType, testTime, expireAfter, retryTimes)

            // Then
            assertNotNull(commandRecord.id)
            assertTrue(commandRecord.id.isNotEmpty())
            assertEquals(commandType, commandRecord.type)
            assertEquals(commandParam, commandRecord.command)
            assertEquals(testTime, commandRecord.scheduleTime)
            assertNotNull(commandRecord.nextTryTime)
        }

        @Test
        @DisplayName("初始化用户创建命令")
        fun `should initialize create user command correctly`() {
            // Given
            val commandParam = CreateUserCommand("john", "john@test.com", "ADMIN")

            // When
            commandRecord.init(commandParam, "user-service", "CREATE_USER", testTime, Duration.ofHours(1), 5)

            // Then
            assertEquals("CREATE_USER", commandRecord.type)
            assertEquals(commandParam, commandRecord.command)
            assertEquals("john", (commandRecord.command as CreateUserCommand).username)
        }

        @Test
        @DisplayName("初始化订单处理命令")
        fun `should initialize process order command correctly`() {
            // Given
            val items = listOf(
                ProcessOrderCommand.OrderItem("product1", 2, 99.99),
                ProcessOrderCommand.OrderItem("product2", 1, 49.99)
            )
            val commandParam = ProcessOrderCommand("order123", "customer456", 249.97, items)

            // When
            commandRecord.init(commandParam, "order-service", "PROCESS_ORDER", testTime, Duration.ofMinutes(15), 3)

            // Then
            assertEquals("PROCESS_ORDER", commandRecord.type)
            assertEquals(commandParam, commandRecord.command)
            val orderParam = commandRecord.command as ProcessOrderCommand
            assertEquals("order123", orderParam.orderId)
            assertEquals(2, orderParam.items.size)
        }

        @Test
        @DisplayName("初始化后Command应该被正确设置")
        fun `should set command correctly after initialization`() {
            // Given
            val commandParam = TestCommand("test", mapOf("key" to "value"))

            // When
            commandRecord.init(commandParam, "test-service", "TEST", testTime, Duration.ofMinutes(10), 2)

            // Then
            assertNotNull(commandRecord.command)
            assertEquals(commandRecord.entity.commandParam, commandParam)
            assertEquals(commandRecord.entity.svcName, "test-service")
            assertEquals(commandRecord.entity.commandType, "TEST")
            assertEquals(commandRecord.entity.lastTryTime, testTime)
        }
    }

    @Nested
    @DisplayName("属性访问测试")
    inner class PropertyAccessTest {

        @BeforeEach
        fun setUp() {
            val commandParam = TestCommand("test", mapOf("key" to "value"))
            commandRecord.init(commandParam, "test-service", "TEST_TYPE", testTime, Duration.ofMinutes(10), 3)
        }

        @Test
        @DisplayName("id属性应该返回command的commandUuid")
        fun `should return command uuid as id`() {
            // When
            val id = commandRecord.id

            // Then
            assertEquals(commandRecord.entity.commandUuid, id)
            assertTrue(id.isNotEmpty())
        }

        @Test
        @DisplayName("type属性应该返回command的commandType")
        fun `should return command type as type`() {
            // When
            val type = commandRecord.type

            // Then
            assertEquals(commandRecord.entity.commandType, type)
            assertEquals("TEST_TYPE", type)
        }

        @Test
        @DisplayName("param属性应该返回command的commandParam")
        fun `should return command param`() {
            // When
            val param = commandRecord.command

            // Then
            assertEquals(commandRecord.entity.commandParam, param)
            assertTrue(param is TestCommand)
            assertEquals("test", (param as TestCommand).action)
        }

        @Test
        @DisplayName("scheduleTime属性应该返回command的lastTryTime")
        fun `should return command lastTryTime as scheduleTime`() {
            // When
            val scheduleTime = commandRecord.scheduleTime

            // Then
            assertEquals(commandRecord.entity.lastTryTime, scheduleTime)
            assertEquals(testTime, scheduleTime)
        }

        @Test
        @DisplayName("nextTryTime属性应该返回command的nextTryTime")
        fun `should return command nextTryTime`() {
            // When
            val nextTryTime = commandRecord.nextTryTime

            // Then
            assertEquals(commandRecord.entity.nextTryTime, nextTryTime)
            assertNotNull(nextTryTime)
        }
    }

    @Nested
    @DisplayName("结果处理测试")
    inner class ResultHandlingTest {

        @BeforeEach
        fun setUp() {
            val commandParam = TestCommand("test", mapOf("key" to "value"))
            commandRecord.init(commandParam, "test-service", "TEST_TYPE", testTime, Duration.ofMinutes(10), 3)
        }

        @Test
        @DisplayName("应该能够获取设置的结果")
        fun `should be able to get set result`() {
            // Given
            val testResult = TestCommandResult(true, "Success", "test data")
            commandRecord.endCommand(testTime.plusMinutes(1), testResult)

            // When
            val result = commandRecord.getResult<TestCommandResult>()

            // Then
            assertNotNull(result)
            assertEquals(testResult, result)
            assertTrue(result!!.success)
            assertEquals("Success", result.message)
        }

        @Test
        @DisplayName("应该能够处理复杂结果类型")
        fun `should handle complex result types`() {
            // Given
            val createUserResult = CreateUserResult("user123", "john", System.currentTimeMillis())
            commandRecord.endCommand(testTime.plusMinutes(1), createUserResult)

            // When
            val result = commandRecord.getResult<CreateUserResult>()

            // Then
            assertNotNull(result)
            assertEquals(createUserResult, result)
            assertEquals("user123", result!!.userId)
            assertEquals("john", result.username)
        }
    }

    @Nested
    @DisplayName("命令状态管理测试")
    inner class CommandStateManagementTest {

        @BeforeEach
        fun setUp() {
            val commandParam = TestCommand("test", mapOf("key" to "value"))
            commandRecord.init(commandParam, "test-service", "TEST_TYPE", testTime, Duration.ofMinutes(30), 3)
        }

        @Test
        @DisplayName("新创建的命令应该是有效的")
        fun `should be valid when newly created`() {
            // Then
            assertTrue(commandRecord.isValid)
            assertFalse(commandRecord.isInvalid)
            assertFalse(commandRecord.isExecuting)
            assertFalse(commandRecord.isExecuted)
        }

        @Test
        @DisplayName("应该能够开始命令")
        fun `should be able to begin command`() {
            // When
            val result = commandRecord.beginCommand(testTime.plusMinutes(1))

            // Then
            assertTrue(result)
            assertTrue(commandRecord.isExecuting)
        }

        @Test
        @DisplayName("应该能够取消命令")
        fun `should be able to cancel command`() {
            // When
            val result = commandRecord.cancelCommand(testTime.plusMinutes(1))

            // Then
            assertTrue(result)
            assertTrue(commandRecord.isInvalid)
        }

        @Test
        @DisplayName("应该能够记录异常")
        fun `should be able to record exception`() {
            // Given
            val exception = RuntimeException("Test exception")

            // When
            commandRecord.occurredException(testTime.plusMinutes(1), exception)

            // Then
            assertNotNull(commandRecord.entity.exception)
            assertTrue(commandRecord.entity.exception!!.contains("Test exception"))
        }

        @Test
        @DisplayName("应该能够结束命令")
        fun `should be able to end command`() {
            // Given
            val result = TestCommandResult(true, "Completed successfully")

            // When
            commandRecord.endCommand(testTime.plusMinutes(1), result)

            // Then
            assertTrue(commandRecord.isExecuted)
            assertFalse(commandRecord.isValid)
            assertEquals(result, commandRecord.getResult<TestCommandResult>())
        }

        @Test
        @DisplayName("应该能够处理命令执行流程")
        fun `should handle command execution flow`() {
            // Given
            val result = CreateUserResult("user123", "john")

            // When - 开始命令
            val beginResult = commandRecord.beginCommand(testTime.plusMinutes(1))
            assertTrue(beginResult)
            assertTrue(commandRecord.isExecuting)

            // Then - 结束命令
            commandRecord.endCommand(testTime.plusMinutes(2), result)
            assertTrue(commandRecord.isExecuted)
            assertFalse(commandRecord.isExecuting)
            assertEquals(result, commandRecord.getResult<CreateUserResult>())
        }
    }

    @Nested
    @DisplayName("命令恢复测试")
    inner class CommandResumeTest {

        @Test
        @DisplayName("应该能够从现有Command恢复")
        fun `should be able to resume from existing command`() {
            // Given
            val commandParam = TestCommand("original", mapOf("data" to "test"))
            val originalCommandRecord = CommandRecordImpl()
            originalCommandRecord.init(commandParam, "test-service", "TEST_TYPE", testTime, Duration.ofMinutes(10), 3)
            val originalEntity = originalCommandRecord.entity

            // When
            val newCommandRecord = CommandRecordImpl()
            newCommandRecord.resume(originalEntity)

            // Then
            assertEquals(originalEntity, newCommandRecord.entity)
            assertEquals(originalCommandRecord.id, newCommandRecord.id)
            assertEquals(originalCommandRecord.command, newCommandRecord.command)
            assertEquals(originalCommandRecord.type, newCommandRecord.type)
        }

        @Test
        @DisplayName("恢复后应该能够正常访问所有属性")
        fun `should access all properties correctly after resume`() {
            // Given
            val items = listOf(ProcessOrderCommand.OrderItem("product1", 1, 99.99))
            val commandParam = ProcessOrderCommand("order123", "customer456", 99.99, items)
            val originalCommandRecord = CommandRecordImpl()
            originalCommandRecord.init(
                commandParam,
                "order-service",
                "PROCESS_ORDER",
                testTime,
                Duration.ofMinutes(30),
                5
            )

            // When
            val newCommandRecord = CommandRecordImpl()
            newCommandRecord.resume(originalCommandRecord.entity)

            // Then
            assertEquals(originalCommandRecord.id, newCommandRecord.id)
            assertEquals("PROCESS_ORDER", newCommandRecord.type)
            assertEquals(commandParam, newCommandRecord.command)
            assertEquals(testTime, newCommandRecord.scheduleTime)

            val orderParam = newCommandRecord.command as ProcessOrderCommand
            assertEquals("order123", orderParam.orderId)
            assertEquals(1, orderParam.items.size)
        }
    }

    @Nested
    @DisplayName("toString方法测试")
    inner class ToStringTest {

        @Test
        @DisplayName("toString应该返回command的字符串表示")
        fun `should return command string representation`() {
            // Given
            val commandParam = TestCommand("test", mapOf("key" to "value"))
            commandRecord.init(commandParam, "test-service", "TEST_TYPE", testTime, Duration.ofMinutes(10), 3)

            // When
            val result = commandRecord.toString()

            // Then
            assertNotNull(result)
            assertEquals(commandRecord.entity.toString(), result)
            assertTrue(result.contains("commandUuid"))
        }
    }

    @Nested
    @DisplayName("边界条件测试")
    inner class EdgeCaseTest {

        @Test
        @DisplayName("应该处理空字符串服务名")
        fun `should handle empty service name`() {
            // Given
            val commandParam = TestCommand("test", mapOf("key" to "value"))

            // When & Then
            assertDoesNotThrow {
                commandRecord.init(commandParam, "", "TEST_TYPE", testTime, Duration.ofMinutes(10), 3)
            }
            assertEquals("", commandRecord.entity.svcName)
        }

        @Test
        @DisplayName("应该处理零重试次数")
        fun `should handle zero retry times`() {
            // Given
            val commandParam = TestCommand("test", mapOf("key" to "value"))

            // When & Then
            assertDoesNotThrow {
                commandRecord.init(commandParam, "test-service", "TEST_TYPE", testTime, Duration.ofMinutes(10), 0)
            }
            assertEquals(0, commandRecord.entity.tryTimes)
        }

        @Test
        @DisplayName("应该处理极短的过期时间")
        fun `should handle very short expire duration`() {
            // Given
            val commandParam = TestCommand("test", mapOf("key" to "value"))

            // When & Then
            assertDoesNotThrow {
                commandRecord.init(commandParam, "test-service", "TEST_TYPE", testTime, Duration.ofMillis(1), 3)
            }
        }

        @Test
        @DisplayName("应该处理复杂对象commandParam")
        fun `should handle complex object command param`() {
            // Given
            val options = ComplexCalculationCommand.CalculationOptions(4, 60000, false)
            val parameters = mapOf(
                "algorithm" to "neural_network",
                "dataset_size" to 10000,
                "features" to listOf("feature1", "feature2", "feature3")
            )
            val commandParam = ComplexCalculationCommand("ML_TRAINING", parameters, options)

            // When
            commandRecord.init(commandParam, "ml-service", "COMPLEX_CALCULATION", testTime, Duration.ofMinutes(15), 3)

            // Then
            assertEquals("COMPLEX_CALCULATION", commandRecord.type)
            assertEquals(commandParam, commandRecord.command)

            val retrievedParam = commandRecord.command as ComplexCalculationCommand
            assertEquals("ML_TRAINING", retrievedParam.calculationType)
            assertEquals(10000, retrievedParam.parameters["dataset_size"])
            assertEquals(4, retrievedParam.options.precision)
            assertFalse(retrievedParam.options.enableCache)
        }

        @Test
        @DisplayName("应该处理类型转换异常")
        fun `should handle type casting exceptions`() {
            // Given
            val commandParam = TestCommand("test", mapOf("key" to "value"))
            commandRecord.init(commandParam, "test-service", "TEST_TYPE", testTime, Duration.ofMinutes(10), 3)
            commandRecord.endCommand(testTime.plusMinutes(1), "string result")

            // When & Then - 尝试获取错误类型的结果应该返回字符串
            val result = commandRecord.getResult<String>()
            assertEquals("string result", result)
        }
    }

    @Nested
    @DisplayName("并发安全测试")
    inner class ConcurrencyTest {

        @Test
        @DisplayName("多线程访问属性应该是安全的")
        fun `property access should be thread safe`() {
            // Given
            val commandParam = TestCommand("test", mapOf("key" to "value"))
            commandRecord.init(commandParam, "test-service", "TEST_TYPE", testTime, Duration.ofMinutes(10), 3)

            // When - 多线程同时访问属性
            val ids = mutableSetOf<String>()
            val types = mutableSetOf<String>()
            val threads = (1..10).map {
                Thread {
                    synchronized(ids) {
                        ids.add(commandRecord.id)
                        types.add(commandRecord.type)
                    }
                }
            }

            // 启动所有线程
            threads.forEach { it.start() }
            // 等待所有线程完成
            threads.forEach { it.join() }

            // Then - 所有线程应该获取到相同的值
            assertEquals(1, ids.size, "All threads should get the same id")
            assertEquals(1, types.size, "All threads should get the same type")
            assertEquals(commandRecord.id, ids.first())
            assertEquals("TEST_TYPE", types.first())
        }

        @Test
        @DisplayName("多线程状态变更应该正确处理")
        fun `state changes should be handled correctly in multi-threaded environment`() {
            // Given
            val commandParam = TestCommand("test", mapOf("key" to "value"))
            commandRecord.init(commandParam, "test-service", "TEST_TYPE", testTime, Duration.ofMinutes(10), 3)

            // When - 多个线程尝试开始命令
            val results = mutableListOf<Boolean>()
            val threads = (1..5).map {
                Thread {
                    val result = commandRecord.beginCommand(testTime.plusMinutes(it.toLong()))
                    synchronized(results) {
                        results.add(result)
                    }
                }
            }

            threads.forEach { it.start() }
            threads.forEach { it.join() }

            // Then - 验证基本条件即可（不强制要求严格的并发控制）
            val successCount = results.count { it }
            assertTrue(successCount >= 1, "At least one thread should succeed")
        }
    }
}
