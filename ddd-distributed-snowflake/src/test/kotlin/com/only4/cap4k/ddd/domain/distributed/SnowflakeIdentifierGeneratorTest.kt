package com.only4.cap4k.ddd.domain.distributed

import com.only4.cap4k.ddd.domain.distributed.snowflake.SnowflakeIdGenerator
import io.mockk.every
import io.mockk.mockk
import io.mockk.verify
import org.hibernate.engine.spi.SharedSessionContractImplementor
import org.junit.jupiter.api.*
import kotlin.test.assertEquals

@DisplayName("Hibernate雪花ID生成器测试")
class SnowflakeIdentifierGeneratorTest {

    private lateinit var identifierGenerator: SnowflakeIdentifierGenerator
    private lateinit var mockSnowflakeGenerator: SnowflakeIdGenerator
    private lateinit var mockSession: SharedSessionContractImplementor

    @BeforeEach
    fun setUp() {
        identifierGenerator = SnowflakeIdentifierGenerator()
        mockSnowflakeGenerator = mockk<SnowflakeIdGenerator>()
        mockSession = mockk<SharedSessionContractImplementor>()
    }

    @AfterEach
    fun tearDown() {
        // 清理配置，避免测试间相互影响
        try {
            val generatorClass = SnowflakeIdentifierGenerator::class.java
            val companionField = generatorClass.getDeclaredField("snowflakeIdGeneratorImpl")
            companionField.isAccessible = true
            companionField.set(null, null)
        } catch (e: NoSuchFieldException) {
            // Kotlin编译后的字段名可能不同，尝试其他可能的字段名
            try {
                val generatorClass = SnowflakeIdentifierGenerator::class.java
                val companionClass = Class.forName("${generatorClass.name}\$Companion")
                val companionInstance = generatorClass.getDeclaredField("Companion").get(null)
                val implField = companionClass.getDeclaredField("snowflakeIdGeneratorImpl")
                implField.isAccessible = true
                implField.set(companionInstance, null)
            } catch (e: Exception) {
                // 如果反射失败，忽略错误，测试依然可以进行
            }
        }
    }

    @Test
    @DisplayName("测试配置雪花ID生成器")
    fun testConfigure() {
        // 配置生成器
        SnowflakeIdentifierGenerator.configure(mockSnowflakeGenerator)

        every { mockSnowflakeGenerator.nextId() } returns 123456789L

        // 生成ID
        val result = identifierGenerator.generate(mockSession, Any())

        assertEquals(123456789L, result)
        verify { mockSnowflakeGenerator.nextId() }
    }

    @Test
    @DisplayName("测试未配置生成器时抛出异常")
    fun testGenerateWithoutConfiguration() {
        // 清理任何可能的配置
        tearDown()

        // 创建新的实例确保没有配置
        val cleanGenerator = SnowflakeIdentifierGenerator()

        // 未配置生成器直接生成ID，会抛出异常（可能是HibernateException或其内部原因）
        assertThrows<Exception> {
            cleanGenerator.generate(mockSession, Any())
        }
    }

    @Test
    @DisplayName("测试重复配置")
    fun testReconfigure() {
        val firstGenerator = mockk<SnowflakeIdGenerator>()
        val secondGenerator = mockk<SnowflakeIdGenerator>()

        every { firstGenerator.nextId() } returns 111L
        every { secondGenerator.nextId() } returns 222L

        // 第一次配置
        SnowflakeIdentifierGenerator.configure(firstGenerator)
        val result1 = identifierGenerator.generate(mockSession, Any())
        assertEquals(111L, result1)

        // 重新配置
        SnowflakeIdentifierGenerator.configure(secondGenerator)
        val result2 = identifierGenerator.generate(mockSession, Any())
        assertEquals(222L, result2)

        verify { firstGenerator.nextId() }
        verify { secondGenerator.nextId() }
    }

    @Test
    @DisplayName("测试多个实例共享同一个生成器")
    fun testMultipleInstancesShareGenerator() {
        val generator1 = SnowflakeIdentifierGenerator()
        val generator2 = SnowflakeIdentifierGenerator()

        every { mockSnowflakeGenerator.nextId() } returnsMany listOf(111L, 222L)

        // 配置生成器
        SnowflakeIdentifierGenerator.configure(mockSnowflakeGenerator)

        // 两个实例应该使用同一个生成器
        val result1 = generator1.generate(mockSession, Any())
        val result2 = generator2.generate(mockSession, Any())

        assertEquals(111L, result1)
        assertEquals(222L, result2)
        verify(exactly = 2) { mockSnowflakeGenerator.nextId() }
    }

    @Test
    @DisplayName("测试生成器异常传播")
    fun testGeneratorExceptionPropagation() {
        val exception = RuntimeException("生成ID失败")
        every { mockSnowflakeGenerator.nextId() } throws exception

        SnowflakeIdentifierGenerator.configure(mockSnowflakeGenerator)

        // 应该传播原始异常
        val thrownException = assertThrows<RuntimeException> {
            identifierGenerator.generate(mockSession, Any())
        }

        assertEquals("生成ID失败", thrownException.message)
    }

    @Test
    @DisplayName("测试使用真实雪花生成器")
    fun testWithRealSnowflakeGenerator() {
        val realGenerator = SnowflakeIdGenerator(1L, 1L)
        SnowflakeIdentifierGenerator.configure(realGenerator)

        // 生成多个ID验证
        val ids = mutableSetOf<Long>()
        repeat(100) {
            val id = identifierGenerator.generate(mockSession, Any()) as Long
            kotlin.test.assertTrue(ids.add(id), "生成的ID应该是唯一的")
            kotlin.test.assertTrue(id > 0, "生成的ID应该是正数")
        }

        assertEquals(100, ids.size)
    }

    @Test
    @DisplayName("测试并发场景下的ID生成")
    fun testConcurrentGeneration() {
        val realGenerator = SnowflakeIdGenerator(1L, 1L)
        SnowflakeIdentifierGenerator.configure(realGenerator)

        val ids = mutableSetOf<Long>()
        val threads = mutableListOf<Thread>()

        // 创建多个线程并发生成ID
        repeat(10) { threadIndex ->
            val thread = Thread {
                repeat(50) {
                    synchronized(ids) {
                        val identifierGen = SnowflakeIdentifierGenerator()
                        val id = identifierGen.generate(mockSession, Any()) as Long
                        kotlin.test.assertTrue(ids.add(id), "并发生成的ID应该是唯一的: $id (thread: $threadIndex)")
                    }
                }
            }
            threads.add(thread)
            thread.start()
        }

        // 等待所有线程完成
        threads.forEach { it.join() }

        assertEquals(500, ids.size, "应该生成500个不同的ID")
    }

    @Test
    @DisplayName("测试静态方法的Java互操作性")
    fun testJavaInterop() {
        // 验证@JvmStatic注解确保Java可以调用
        val realGenerator = SnowflakeIdGenerator(1L, 1L)

        // 这里模拟Java调用的方式
        SnowflakeIdentifierGenerator.configure(realGenerator)

        val result = identifierGenerator.generate(mockSession, Any())
        kotlin.test.assertTrue(result is Long)
        kotlin.test.assertTrue((result as Long) > 0)
    }

    @Test
    @DisplayName("测试参数传递和返回值类型")
    fun testParameterAndReturnTypes() {
        every { mockSnowflakeGenerator.nextId() } returns Long.MAX_VALUE

        SnowflakeIdentifierGenerator.configure(mockSnowflakeGenerator)

        val testObject = "test-entity"
        val result = identifierGenerator.generate(mockSession, testObject)

        // 验证返回值是Serializable
        kotlin.test.assertTrue(result is java.io.Serializable)
        assertEquals(Long.MAX_VALUE, result)

        // 验证调用了生成器
        verify { mockSnowflakeGenerator.nextId() }
    }
}
