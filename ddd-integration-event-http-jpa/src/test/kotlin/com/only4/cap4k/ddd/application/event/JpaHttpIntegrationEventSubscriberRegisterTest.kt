package com.only4.cap4k.ddd.application.event

import com.only4.cap4k.ddd.application.event.persistence.EventHttpSubscriber
import com.only4.cap4k.ddd.application.event.persistence.EventHttpSubscriberJpaRepository
import io.mockk.every
import io.mockk.just
import io.mockk.mockk
import io.mockk.runs
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Test
import java.util.*
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

@DisplayName("JPA HTTP集成事件订阅注册器测试")
class JpaHttpIntegrationEventSubscriberRegisterTest {

    @Test
    @DisplayName("新订阅应该成功创建")
    fun `should create new subscription successfully`() {
        // Arrange
        val repository = mockk<EventHttpSubscriberJpaRepository>()
        val register = JpaHttpIntegrationEventSubscriberRegister(repository)

        val event = "user.created"
        val subscriber = "email-service"
        val callbackUrl = "http://localhost:8080/webhook"

        every { repository.findOne(any()) } returns Optional.empty()
        every { repository.saveAndFlush(any()) } returns mockk()

        // Act
        val result = register.subscribe(event, subscriber, callbackUrl)

        // Assert
        assertTrue(result)
    }

    @Test
    @DisplayName("重复订阅应该返回失败")
    fun `should return false for duplicate subscription`() {
        // Arrange
        val repository = mockk<EventHttpSubscriberJpaRepository>()
        val register = JpaHttpIntegrationEventSubscriberRegister(repository)

        val event = "user.created"
        val subscriber = "email-service"
        val callbackUrl = "http://localhost:8080/webhook"

        val existingSubscriber = EventHttpSubscriber(
            id = 1L,
            event = event,
            subscriber = subscriber,
            callbackUrl = callbackUrl,
            version = 0
        )

        every { repository.findOne(any()) } returns Optional.of(existingSubscriber)

        // Act
        val result = register.subscribe(event, subscriber, callbackUrl)

        // Assert
        assertFalse(result)
    }

    @Test
    @DisplayName("存在的订阅应该成功取消")
    fun `should unsubscribe existing subscription successfully`() {
        // Arrange
        val repository = mockk<EventHttpSubscriberJpaRepository>()
        val register = JpaHttpIntegrationEventSubscriberRegister(repository)

        val event = "user.deleted"
        val subscriber = "audit-service"

        val existingSubscriber = EventHttpSubscriber(
            id = 1L,
            event = event,
            subscriber = subscriber,
            callbackUrl = "http://audit:8080/webhook",
            version = 0
        )

        every { repository.findOne(any()) } returns Optional.of(existingSubscriber)
        every { repository.delete(existingSubscriber) } just runs
        every { repository.flush() } just runs

        // Act
        val result = register.unsubscribe(event, subscriber)

        // Assert
        assertTrue(result)
    }

    @Test
    @DisplayName("不存在的订阅应该返回失败")
    fun `should return false for non-existing subscription`() {
        // Arrange
        val repository = mockk<EventHttpSubscriberJpaRepository>()
        val register = JpaHttpIntegrationEventSubscriberRegister(repository)

        val event = "user.updated"
        val subscriber = "notification-service"

        every { repository.findOne(any()) } returns Optional.empty()

        // Act
        val result = register.unsubscribe(event, subscriber)

        // Assert
        assertFalse(result)
    }

    @Test
    @DisplayName("应该返回所有不同的事件类型")
    fun `should return all distinct event types`() {
        // Arrange
        val repository = mockk<EventHttpSubscriberJpaRepository>()
        val register = JpaHttpIntegrationEventSubscriberRegister(repository)

        val subscribers = listOf(
            EventHttpSubscriber(1L, "user.created", "service1", "url1", 0),
            EventHttpSubscriber(2L, "user.updated", "service2", "url2", 0),
            EventHttpSubscriber(3L, "user.created", "service3", "url3", 0),
            EventHttpSubscriber(4L, "order.placed", "service4", "url4", 0)
        )

        every { repository.findAll() } returns subscribers

        // Act
        val result = register.events()

        // Assert
        assertEquals(3, result.size)
        assertTrue(result.contains("user.created"))
        assertTrue(result.contains("user.updated"))
        assertTrue(result.contains("order.placed"))
    }

    @Test
    @DisplayName("应该返回指定事件的所有订阅者")
    fun `should return all subscribers for specific event`() {
        // Arrange
        val repository = mockk<EventHttpSubscriberJpaRepository>()
        val register = JpaHttpIntegrationEventSubscriberRegister(repository)

        val event = "user.created"
        val subscribers = listOf(
            EventHttpSubscriber(1L, event, "email-service", "http://email:8080/webhook", 0),
            EventHttpSubscriber(2L, event, "audit-service", "http://audit:9090/events", 0)
        )

        every { repository.findAll(ofType<org.springframework.data.jpa.domain.Specification<EventHttpSubscriber>>()) } returns subscribers

        // Act
        val result = register.subscribers(event)

        // Assert
        assertEquals(2, result.size)
        assertEquals("email-service", result[0].subscriber)
        assertEquals("audit-service", result[1].subscriber)
    }
}
