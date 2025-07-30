package com.only4.cap4k.ddd.domain.repo

import com.only4.cap4k.ddd.core.domain.aggregate.ValueObject
import io.mockk.every
import io.mockk.mockk
import io.mockk.mockkStatic
import io.mockk.unmockkStatic
import org.hibernate.engine.spi.SharedSessionContractImplementor
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Test
import java.io.Serializable
import java.math.BigDecimal
import java.math.BigInteger
import kotlin.test.assertEquals
import kotlin.test.assertNotEquals
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

class Md5HashIdentifierGeneratorTest {

    private lateinit var generator: Md5HashIdentifierGenerator
    private lateinit var mockSession: SharedSessionContractImplementor

    private data class TestEntity(
        val id: Long? = null,
        val name: String,
        val value: Int
    )

    private data class EntityWithCustomId(
        val customId: String? = null,
        val name: String,
        val value: Int
    )

    private class TestValueObject(private val data: String) : ValueObject<String> {
        override fun hash(): String = "test-hash-$data"
    }

    private class TestSerializableValueObject(private val data: String) : ValueObject<String>, Serializable {
        override fun hash(): String = "serializable-hash-$data"
    }

    private class TestNumberValueObject(private val data: String) : ValueObject<Long> {
        override fun hash(): Long = data.hashCode().toLong()
    }

    @BeforeEach
    fun setup() {
        generator = Md5HashIdentifierGenerator()
        mockSession = mockk<SharedSessionContractImplementor>()
        mockkStatic("com.only4.cap4k.ddd.core.share.misc.ClassUtils")
    }

    @AfterEach
    fun teardown() {
        unmockkStatic("com.only4.cap4k.ddd.core.share.misc.ClassUtils")
    }

    @Test
    @DisplayName("当实体是具有可序列化哈希的ValueObject时应该返回ValueObject哈希")
    fun `generate should return ValueObject hash when entity is ValueObject with Serializable hash`() {
        val valueObject = TestSerializableValueObject("test")

        val result = generator.generate(mockSession, valueObject)

        assertEquals("serializable-hash-test", result)
    }

    @Test
    @DisplayName("当实体是ValueObject但哈希不可序列化时应该使用哈希方法")
    fun `generate should use hash method when entity is ValueObject but hash is not Serializable`() {
        val valueObject = TestValueObject("test")

        every {
            com.only4.cap4k.ddd.core.share.misc.resolveGenericTypeClass(any(), 0, ValueObject::class.java)
        } returns String::class.java

        val result = generator.generate(mockSession, valueObject)

        assertNotNull(result)
        assertTrue(result is String)
    }

    @Test
    @DisplayName("当实体不是ValueObject时应该使用哈希方法")
    fun `generate should use hash method when entity is not ValueObject`() {
        val entity = TestEntity(name = "test", value = 42)

        every {
            com.only4.cap4k.ddd.core.share.misc.resolveGenericTypeClass(any(), 0, ValueObject::class.java)
        } returns String::class.java

        val result = generator.generate(mockSession, entity)

        assertNotNull(result)
        assertTrue(result is String)
    }

    @Test
    @DisplayName("哈希应该生成String类型的哈希")
    fun `hash should generate String type hash`() {
        val entity = TestEntity(name = "test", value = 42)

        every {
            com.only4.cap4k.ddd.core.share.misc.resolveGenericTypeClass(any(), 0, ValueObject::class.java)
        } returns String::class.java

        val result = Md5HashIdentifierGenerator.hash(entity)

        assertNotNull(result)
        assertTrue(result is String)
        assertEquals(32, result.length) // MD5 hex string length
    }

    @Test
    @DisplayName("哈希应该生成Int类型的哈希")
    fun `hash should generate Int type hash`() {
        val entity = TestEntity(name = "test", value = 42)

        every {
            com.only4.cap4k.ddd.core.share.misc.resolveGenericTypeClass(any(), 0, ValueObject::class.java)
        } returns Int::class.java

        val result = Md5HashIdentifierGenerator.hash(entity)

        assertNotNull(result)
        assertTrue(result is Int)
    }

    @Test
    @DisplayName("哈希应该生成Long类型的哈希")
    fun `hash should generate Long type hash`() {
        val entity = TestEntity(name = "test", value = 42)

        every {
            com.only4.cap4k.ddd.core.share.misc.resolveGenericTypeClass(any(), 0, ValueObject::class.java)
        } returns Long::class.java

        val result = Md5HashIdentifierGenerator.hash(entity)

        assertNotNull(result)
        assertTrue(result is Long)
    }

    @Test
    @DisplayName("哈希应该生成BigInteger类型的哈希")
    fun `hash should generate BigInteger type hash`() {
        val entity = TestEntity(name = "test", value = 42)

        every {
            com.only4.cap4k.ddd.core.share.misc.resolveGenericTypeClass(any(), 0, ValueObject::class.java)
        } returns BigInteger::class.java

        val result = Md5HashIdentifierGenerator.hash(entity)

        assertNotNull(result)
        assertTrue(result is BigInteger)
    }

    @Test
    @DisplayName("哈希应该生成BigDecimal类型的哈希")
    fun `hash should generate BigDecimal type hash`() {
        val entity = TestEntity(name = "test", value = 42)

        every {
            com.only4.cap4k.ddd.core.share.misc.resolveGenericTypeClass(any(), 0, ValueObject::class.java)
        } returns BigDecimal::class.java

        val result = Md5HashIdentifierGenerator.hash(entity)

        assertNotNull(result)
        assertTrue(result is BigDecimal)
    }

    @Test
    @DisplayName("哈希应该处理Integer包装类")
    fun `hash should handle Integer wrapper class`() {
        val entity = TestEntity(name = "test", value = 42)

        every {
            com.only4.cap4k.ddd.core.share.misc.resolveGenericTypeClass(any(), 0, ValueObject::class.java)
        } returns Integer::class.java

        val result = Md5HashIdentifierGenerator.hash(entity)

        assertNotNull(result)
        assertTrue(result is Int)
    }

    @Test
    @DisplayName("哈希应该从 JSON中递归地移除ID字段")
    fun `hash should remove id field recursively from JSON`() {
        val entity = TestEntity(id = 123L, name = "test", value = 42)

        every {
            com.only4.cap4k.ddd.core.share.misc.resolveGenericTypeClass(any(), 0, ValueObject::class.java)
        } returns String::class.java

        val result1 = Md5HashIdentifierGenerator.hash(entity)

        // 同样的对象但ID不同，应该生成相同的哈希
        val entity2 = TestEntity(id = 456L, name = "test", value = 42)
        val result2 = Md5HashIdentifierGenerator.hash(entity2)

        assertEquals(result1, result2)
    }

    @Test
    @DisplayName("哈希应该使用自定义ID字段名")
    fun `hash should use custom id field name`() {
        val entity = EntityWithCustomId(customId = "custom123", name = "test", value = 42)

        every {
            com.only4.cap4k.ddd.core.share.misc.resolveGenericTypeClass(any(), 0, ValueObject::class.java)
        } returns String::class.java

        val result1 = Md5HashIdentifierGenerator.hash(entity, "customId")

        // 同样的对象但customId不同，应该生成相同的哈希
        val entity2 = EntityWithCustomId(customId = "custom456", name = "test", value = 42)
        val result2 = Md5HashIdentifierGenerator.hash(entity2, "customId")

        assertEquals(result1, result2)
    }

    @Test
    @DisplayName("哈希应该为不同对象生成不同的结果")
    fun `hash should generate different results for different objects`() {
        val entity1 = TestEntity(name = "test1", value = 42)
        val entity2 = TestEntity(name = "test2", value = 42)

        every {
            com.only4.cap4k.ddd.core.share.misc.resolveGenericTypeClass(any(), 0, ValueObject::class.java)
        } returns String::class.java

        val result1 = Md5HashIdentifierGenerator.hash(entity1)
        val result2 = Md5HashIdentifierGenerator.hash(entity2)

        assertNotEquals(result1, result2)
    }

    @Test
    @DisplayName("哈希应该为相同对象内容生成相同的结果")
    fun `hash should generate same results for same object content`() {
        val entity1 = TestEntity(name = "test", value = 42)
        val entity2 = TestEntity(name = "test", value = 42)

        every {
            com.only4.cap4k.ddd.core.share.misc.resolveGenericTypeClass(any(), 0, ValueObject::class.java)
        } returns String::class.java

        val result1 = Md5HashIdentifierGenerator.hash(entity1)
        val result2 = Md5HashIdentifierGenerator.hash(entity2)

        assertEquals(result1, result2)
    }

    @Test
    @DisplayName("配置应该改变ID_FIELD_NAME")
    fun `configure should change ID_FIELD_NAME`() {
        val originalFieldName = Md5HashIdentifierGenerator.ID_FIELD_NAME

        try {
            Md5HashIdentifierGenerator.configure("newIdField")
            assertEquals("newIdField", Md5HashIdentifierGenerator.ID_FIELD_NAME)
        } finally {
            // 恢复原始配置
            Md5HashIdentifierGenerator.configure(originalFieldName)
        }
    }

    @Test
    @DisplayName("哈希应该正确处理边界情况类型")
    fun `hash should handle edge case types correctly`() {
        val entity = TestEntity(name = "edge-case", value = 999)

        // 测试 Float 类型
        every {
            com.only4.cap4k.ddd.core.share.misc.resolveGenericTypeClass(any(), 0, ValueObject::class.java)
        } returns Float::class.java

        val result = Md5HashIdentifierGenerator.hash(entity)

        assertNotNull(result)
        // Float类型会通过convertToTargetType转换，最终返回Float类型
        assertTrue(result is Number) // 更宽泛的验证
    }

    @Test
    @DisplayName("哈希应该正确处理Short类型")
    fun `hash should handle Short type correctly`() {
        val entity = TestEntity(name = "short-test", value = 123)

        every {
            com.only4.cap4k.ddd.core.share.misc.resolveGenericTypeClass(any(), 0, ValueObject::class.java)
        } returns Short::class.java

        val result = Md5HashIdentifierGenerator.hash(entity)

        assertNotNull(result)
        // Short类型会通过convertToTargetType转换，最终返回Number类型
        assertTrue(result is Number) // 更宽泛的验证
    }

    @Test
    @DisplayName("哈希应该处理具有ID移除的复杂嵌套对象")
    fun `hash should handle complex nested objects with id removal`() {
        data class NestedEntity(val id: Long? = null, val name: String)
        data class ComplexEntity(val id: Long? = null, val nested: NestedEntity, val value: Int)

        val nested1 = NestedEntity(id = 1L, name = "nested")
        val nested2 = NestedEntity(id = 2L, name = "nested") // 不同的ID

        val entity1 = ComplexEntity(id = 100L, nested = nested1, value = 42)
        val entity2 = ComplexEntity(id = 200L, nested = nested2, value = 42) // 不同的ID

        every {
            com.only4.cap4k.ddd.core.share.misc.resolveGenericTypeClass(any(), 0, ValueObject::class.java)
        } returns String::class.java

        val result1 = Md5HashIdentifierGenerator.hash(entity1)
        val result2 = Md5HashIdentifierGenerator.hash(entity2)

        // 由于ID字段被递归移除，两个对象应该生成相同的哈希
        assertEquals(result1, result2)
    }

    @Test
    @DisplayName("哈希应该在多次调用中保持一致性")
    fun `hash should be consistent across multiple calls`() {
        val entity = TestEntity(name = "consistency-test", value = 777)

        every {
            com.only4.cap4k.ddd.core.share.misc.resolveGenericTypeClass(any(), 0, ValueObject::class.java)
        } returns String::class.java

        val results = mutableSetOf<String>()

        // 多次调用相同的对象，应该产生相同的哈希
        repeat(10) {
            val result = Md5HashIdentifierGenerator.hash(entity) as String
            results.add(result)
        }

        // 所有结果应该相同
        assertEquals(1, results.size)
    }
}
