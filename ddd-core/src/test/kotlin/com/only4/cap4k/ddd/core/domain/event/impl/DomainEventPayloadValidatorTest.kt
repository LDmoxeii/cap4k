package com.only4.cap4k.ddd.core.domain.event.impl

import com.only4.cap4k.ddd.core.share.DomainException
import jakarta.persistence.Entity
import jakarta.persistence.Id
import org.junit.jupiter.api.Assertions.assertDoesNotThrow
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows
import java.math.BigDecimal

class DomainEventPayloadValidatorTest {
    @Test
    fun `immutable scalar and value object snapshots are accepted`() {
        assertDoesNotThrow {
            DomainEventPayloadValidator.validate(
                PriceChanged(
                    orderId = 42,
                    oldPrice = Price(BigDecimal("10.00"), "CNY"),
                    newPrice = Price(BigDecimal("12.00"), "CNY"),
                    tags = listOf("promotion", "manual"),
                )
            )
        }
    }

    @Test
    fun `persistent entity references are rejected with their payload path`() {
        val error = assertThrows<DomainException> {
            DomainEventPayloadValidator.validate(InvalidEvent(OrderEntity(42)))
        }

        assertTrue(error.message.orEmpty().contains("payload.order"))
        assertTrue(error.message.orEmpty().contains(OrderEntity::class.java.name))
    }

    @Test
    fun `nested persistent entity references remain rejected`() {
        val error = assertThrows<DomainException> {
            DomainEventPayloadValidator.validate(NestedInvalidEvent(OrderSnapshot(OrderEntity(42))))
        }

        assertTrue(error.message.orEmpty().contains("payload.snapshot.order"))
        assertTrue(error.message.orEmpty().contains(OrderEntity::class.java.name))
    }

    @Test
    fun `collection map and array persistent entity references remain rejected`() {
        val payloads = listOf(
            CollectionInvalidEvent(listOf(OrderEntity(42))) to "payload.orders[0]",
            MapInvalidEvent(mapOf("order" to OrderEntity(42))) to "payload.orders.values[0]",
            ArrayInvalidEvent(arrayOf(OrderEntity(42))) to "payload.orders[0]",
        )

        payloads.forEach { (payload, expectedPath) ->
            val error = assertThrows<DomainException> {
                DomainEventPayloadValidator.validate(payload)
            }
            assertTrue(error.message.orEmpty().contains(expectedPath))
            assertTrue(error.message.orEmpty().contains(OrderEntity::class.java.name))
        }
    }

    data class Price(val amount: BigDecimal, val currency: String)

    data class PriceChanged(
        val orderId: Long,
        val oldPrice: Price,
        val newPrice: Price,
        val tags: List<String>,
    )

    data class InvalidEvent(val order: OrderEntity)

    data class NestedInvalidEvent(val snapshot: OrderSnapshot)

    data class OrderSnapshot(val order: OrderEntity)

    data class CollectionInvalidEvent(val orders: List<OrderEntity>)

    data class MapInvalidEvent(val orders: Map<String, OrderEntity>)

    data class ArrayInvalidEvent(val orders: Array<OrderEntity>)

    @Entity
    class OrderEntity(
        @Id
        val id: Long,
    )
}
