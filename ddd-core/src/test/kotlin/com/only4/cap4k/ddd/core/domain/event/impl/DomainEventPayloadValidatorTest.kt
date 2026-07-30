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

    data class Price(val amount: BigDecimal, val currency: String)

    data class PriceChanged(
        val orderId: Long,
        val oldPrice: Price,
        val newPrice: Price,
        val tags: List<String>,
    )

    data class InvalidEvent(val order: OrderEntity)

    @Entity
    class OrderEntity(
        @Id
        val id: Long,
    )
}
