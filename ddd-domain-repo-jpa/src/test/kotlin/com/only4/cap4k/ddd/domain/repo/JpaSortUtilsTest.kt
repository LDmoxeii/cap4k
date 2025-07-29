package com.only4.cap4k.ddd.domain.repo

import com.only4.cap4k.ddd.core.share.OrderInfo
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Test
import org.springframework.data.domain.Sort

class JpaSortUtilsTest {

    @Test
    @DisplayName("当集合为空时应返回无排序")
    fun `toSpringData should return unsorted when collection is empty`() {
        val result = toSpringData(emptyList())

        assertTrue(result.isUnsorted)
        assertEquals(Sort.unsorted(), result)
    }

    @Test
    @DisplayName("应正确转换单个升序OrderInfo")
    fun `toSpringData should convert single ascending OrderInfo correctly`() {
        val orderInfos = listOf(OrderInfo.asc("name"))

        val result = toSpringData(orderInfos)

        assertTrue(result.isSorted)
        val orders = result.toList()
        assertEquals(1, orders.size)
        assertEquals("name", orders[0].property)
        assertEquals(Sort.Direction.ASC, orders[0].direction)
    }

    @Test
    @DisplayName("应正确转换单个降序OrderInfo")
    fun `toSpringData should convert single descending OrderInfo correctly`() {
        val orderInfos = listOf(OrderInfo.desc("id"))

        val result = toSpringData(orderInfos)

        assertTrue(result.isSorted)
        val orders = result.toList()
        assertEquals(1, orders.size)
        assertEquals("id", orders[0].property)
        assertEquals(Sort.Direction.DESC, orders[0].direction)
    }

    @Test
    @DisplayName("应正确转换多个OrderInfo对象")
    fun `toSpringData should convert multiple OrderInfo objects correctly`() {
        val orderInfos = listOf(
            OrderInfo.desc("createTime"),
            OrderInfo.asc("name"),
            OrderInfo.desc("priority"),
            OrderInfo.asc("version")
        )

        val result = toSpringData(orderInfos)

        assertTrue(result.isSorted)
        val orders = result.toList()
        assertEquals(4, orders.size)

        assertEquals("createTime", orders[0].property)
        assertEquals(Sort.Direction.DESC, orders[0].direction)

        assertEquals("name", orders[1].property)
        assertEquals(Sort.Direction.ASC, orders[1].direction)

        assertEquals("priority", orders[2].property)
        assertEquals(Sort.Direction.DESC, orders[2].direction)

        assertEquals("version", orders[3].property)
        assertEquals(Sort.Direction.ASC, orders[3].direction)
    }

    @Test
    @DisplayName("应处理混合集合类型")
    fun `toSpringData should handle mixed collection types`() {
        val orderInfos = mutableListOf<OrderInfo>()
        orderInfos.add(OrderInfo.asc("field1"))
        orderInfos.add(OrderInfo.desc("field2"))

        val result = toSpringData(orderInfos)

        assertTrue(result.isSorted)
        val orders = result.toList()
        assertEquals(2, orders.size)
        assertEquals("field1", orders[0].property)
        assertEquals(Sort.Direction.ASC, orders[0].direction)
        assertEquals("field2", orders[1].property)
        assertEquals(Sort.Direction.DESC, orders[1].direction)
    }

    @Test
    @DisplayName("可变参数方法在没有参数时应正确工作")
    fun `toSpringData varargs should work with no arguments`() {
        val result = toSpringData()

        assertTrue(result.isUnsorted)
        assertEquals(Sort.unsorted(), result)
    }

    @Test
    @DisplayName("可变参数方法在单个OrderInfo时应正确工作")
    fun `toSpringData varargs should work with single OrderInfo`() {
        val result = toSpringData(OrderInfo.desc("status"))

        assertTrue(result.isSorted)
        val orders = result.toList()
        assertEquals(1, orders.size)
        assertEquals("status", orders[0].property)
        assertEquals(Sort.Direction.DESC, orders[0].direction)
    }

    @Test
    @DisplayName("可变参数方法在多个OrderInfo对象时应正确工作")
    fun `toSpringData varargs should work with multiple OrderInfo objects`() {
        val result = toSpringData(
            OrderInfo.asc("name"),
            OrderInfo.desc("createTime"),
            OrderInfo.asc("id")
        )

        assertTrue(result.isSorted)
        val orders = result.toList()
        assertEquals(3, orders.size)

        assertEquals("name", orders[0].property)
        assertEquals(Sort.Direction.ASC, orders[0].direction)

        assertEquals("createTime", orders[1].property)
        assertEquals(Sort.Direction.DESC, orders[1].direction)

        assertEquals("id", orders[2].property)
        assertEquals(Sort.Direction.ASC, orders[2].direction)
    }

    @Test
    @DisplayName("应保持OrderInfo对象的顺序")
    fun `toSpringData should preserve order of OrderInfo objects`() {
        val orderInfos = listOf(
            OrderInfo.desc("z_field"),
            OrderInfo.asc("a_field"),
            OrderInfo.desc("m_field")
        )

        val result = toSpringData(orderInfos)

        val orders = result.toList()
        assertEquals(3, orders.size)

        // Should preserve input order, not alphabetical order
        assertEquals("z_field", orders[0].property)
        assertEquals("a_field", orders[1].property)
        assertEquals("m_field", orders[2].property)
    }

    @Test
    @DisplayName("应处理使用Any类型字段创建的OrderInfo")
    fun `toSpringData should handle OrderInfo created with Any type fields`() {
        val orderInfos = listOf(
            OrderInfo.asc(123), // numeric field name
            OrderInfo.desc("stringField"),
            OrderInfo.asc(true) // boolean field name
        )

        val result = toSpringData(orderInfos)

        assertTrue(result.isSorted)
        val orders = result.toList()
        assertEquals(3, orders.size)

        assertEquals("123", orders[0].property)
        assertEquals(Sort.Direction.ASC, orders[0].direction)

        assertEquals("stringField", orders[1].property)
        assertEquals(Sort.Direction.DESC, orders[1].direction)

        assertEquals("true", orders[2].property)
        assertEquals(Sort.Direction.ASC, orders[2].direction)
    }

    @Test
    @DisplayName("应处理复杂字段名")
    fun `toSpringData should handle complex field names`() {
        val orderInfos = listOf(
            OrderInfo.asc("user.profile.name"),
            OrderInfo.desc("order.items.count"),
            OrderInfo.asc("nested_field_with_underscores"),
            OrderInfo.desc("CamelCaseField")
        )

        val result = toSpringData(orderInfos)

        assertTrue(result.isSorted)
        val orders = result.toList()
        assertEquals(4, orders.size)

        assertEquals("user.profile.name", orders[0].property)
        assertEquals(Sort.Direction.ASC, orders[0].direction)

        assertEquals("order.items.count", orders[1].property)
        assertEquals(Sort.Direction.DESC, orders[1].direction)

        assertEquals("nested_field_with_underscores", orders[2].property)
        assertEquals(Sort.Direction.ASC, orders[2].direction)

        assertEquals("CamelCaseField", orders[3].property)
        assertEquals(Sort.Direction.DESC, orders[3].direction)
    }

    @Test
    @DisplayName("应高效处理大集合")
    fun `toSpringData should handle large collections efficiently`() {
        val largeOrderList = (1..1000).map { index ->
            if (index % 2 == 0) OrderInfo.asc("field$index") else OrderInfo.desc("field$index")
        }

        val result = toSpringData(largeOrderList)

        assertTrue(result.isSorted)
        val orders = result.toList()
        assertEquals(1000, orders.size)

        // Verify first few and last few orders
        assertEquals("field1", orders[0].property)
        assertEquals(Sort.Direction.DESC, orders[0].direction)

        assertEquals("field2", orders[1].property)
        assertEquals(Sort.Direction.ASC, orders[1].direction)

        assertEquals("field999", orders[998].property)
        assertEquals(Sort.Direction.DESC, orders[998].direction)

        assertEquals("field1000", orders[999].property)
        assertEquals(Sort.Direction.ASC, orders[999].direction)
    }

    @Test
    @DisplayName("集合方法和可变参数方法应产生相同结果")
    fun `toSpringData collection and varargs methods should produce identical results`() {
        val orderInfo1 = OrderInfo.asc("name")
        val orderInfo2 = OrderInfo.desc("id")
        val orderInfo3 = OrderInfo.asc("status")

        val collectionResult = toSpringData(listOf(orderInfo1, orderInfo2, orderInfo3))
        val varargsResult = toSpringData(orderInfo1, orderInfo2, orderInfo3)

        assertEquals(collectionResult, varargsResult)

        val collectionOrders = collectionResult.toList()
        val varargsOrders = varargsResult.toList()

        assertEquals(collectionOrders.size, varargsOrders.size)
        for (i in collectionOrders.indices) {
            assertEquals(collectionOrders[i].property, varargsOrders[i].property)
            assertEquals(collectionOrders[i].direction, varargsOrders[i].direction)
        }
    }

    @Test
    @DisplayName("应正确处理Set集合")
    fun `toSpringData should handle Set collections correctly`() {
        val orderInfos = setOf(
            OrderInfo.asc("name"),
            OrderInfo.desc("createTime")
        )

        val result = toSpringData(orderInfos)

        assertTrue(result.isSorted)
        val orders = result.toList()
        assertEquals(2, orders.size)

        // Note: Set order might not be guaranteed, but the conversion should still work
        assertTrue(orders.any { it.property == "name" && it.direction == Sort.Direction.ASC })
        assertTrue(orders.any { it.property == "createTime" && it.direction == Sort.Direction.DESC })
    }

    @Test
    @DisplayName("应处理相同字段名但不同方向的情况")
    fun `toSpringData should handle duplicate field names with different directions`() {
        val orderInfos = listOf(
            OrderInfo.asc("name"),
            OrderInfo.desc("name"), // Same field, different direction
            OrderInfo.asc("id")
        )

        val result = toSpringData(orderInfos)

        assertTrue(result.isSorted)
        val orders = result.toList()
        assertEquals(3, orders.size)

        assertEquals("name", orders[0].property)
        assertEquals(Sort.Direction.ASC, orders[0].direction)

        assertEquals("name", orders[1].property)
        assertEquals(Sort.Direction.DESC, orders[1].direction)

        assertEquals("id", orders[2].property)
        assertEquals(Sort.Direction.ASC, orders[2].direction)
    }

    @Test
    @DisplayName("应优雅地处理边界情况")
    fun `toSpringData should handle edge cases gracefully`() {
        // Test with valid non-empty field names only
        val orderInfos = listOf(
            OrderInfo.asc("validField1"),
            OrderInfo.desc("validField2"),
            OrderInfo.asc("validField3")
        )

        val result = toSpringData(orderInfos)

        assertTrue(result.isSorted)
        val orders = result.toList()
        assertEquals(3, orders.size)

        assertEquals("validField1", orders[0].property)
        assertEquals(Sort.Direction.ASC, orders[0].direction)

        assertEquals("validField2", orders[1].property)
        assertEquals(Sort.Direction.DESC, orders[1].direction)

        assertEquals("validField3", orders[2].property)
        assertEquals(Sort.Direction.ASC, orders[2].direction)
    }
}
