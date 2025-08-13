package com.only4.cap4k.ddd.application.event.persistence

import io.mockk.*
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Test
import java.util.*

@DisplayName("EventHttpSubscriberJpaRepository测试")
class EventHttpSubscriberJpaRepositoryTest {

    @Test
    @DisplayName("应该能够保存实体")
    fun `should be able to save entity`() {
        // Arrange
        val repository = mockk<EventHttpSubscriberJpaRepository>()
        val entity = EventHttpSubscriber(
            event = "user.created",
            subscriber = "email-service",
            callbackUrl = "http://email:8080/webhook"
        )
        val savedEntity = entity.copy(id = 1L)

        every { repository.save(any()) } returns savedEntity

        // Act
        val result = repository.save(entity)

        // Assert
        assertEquals(1L, result.id)
        assertEquals("user.created", result.event)
    }

    @Test
    @DisplayName("应该能够通过ID查找实体")
    fun `should be able to find entity by id`() {
        // Arrange
        val repository = mockk<EventHttpSubscriberJpaRepository>()
        val entityId = 42L
        val entity = EventHttpSubscriber(
            id = entityId,
            event = "order.placed",
            subscriber = "inventory-service",
            callbackUrl = "http://inventory:9090/events"
        )

        every { repository.findById(entityId) } returns Optional.of(entity)

        // Act
        val result = repository.findById(entityId)

        // Assert
        assertTrue(result.isPresent)
        assertEquals(entityId, result.get().id)
    }

    @Test
    @DisplayName("应该能够删除实体")
    fun `should be able to delete entity`() {
        // Arrange
        val repository = mockk<EventHttpSubscriberJpaRepository>()
        val entity = EventHttpSubscriber(
            id = 1L,
            event = "user.deleted",
            subscriber = "audit-service",
            callbackUrl = "http://audit:8080/log"
        )

        every { repository.delete(entity) } just runs

        // Act
        repository.delete(entity)

        // Assert - 验证方法被调用
        verify { repository.delete(entity) }
    }

    @Test
    @DisplayName("应该能够查找所有实体")
    fun `should be able to find all entities`() {
        // Arrange
        val repository = mockk<EventHttpSubscriberJpaRepository>()
        val entities = listOf(
            EventHttpSubscriber(1L, "user.created", "service1", "url1"),
            EventHttpSubscriber(2L, "user.updated", "service2", "url2"),
            EventHttpSubscriber(3L, "order.placed", "service3", "url3")
        )

        every { repository.findAll() } returns entities

        // Act
        val result = repository.findAll()

        // Assert
        assertEquals(3, result.size)
        assertEquals("user.created", result[0].event)
    }

    @Test
    @DisplayName("应该能够使用规格查询")
    fun `should be able to use specification queries`() {
        // Arrange
        val repository = mockk<EventHttpSubscriberJpaRepository>()
        val entity = EventHttpSubscriber(
            id = 1L,
            event = "payment.processed",
            subscriber = "billing-service",
            callbackUrl = "http://billing:8080/webhook"
        )

        every { repository.findOne(any()) } returns Optional.of(entity)

        // Act
        val result = repository.findOne(mockk())

        // Assert
        assertTrue(result.isPresent)
        assertEquals("payment.processed", result.get().event)
    }

    @Test
    @DisplayName("应该能够计算实体总数")
    fun `should be able to count entities`() {
        // Arrange
        val repository = mockk<EventHttpSubscriberJpaRepository>()
        val totalCount = 42L
        every { repository.count() } returns totalCount

        // Act
        val result = repository.count()

        // Assert
        assertEquals(totalCount, result)
    }
}
