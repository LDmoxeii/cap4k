package com.only4.cap4k.plugin.pipeline.core

import com.only4.cap4k.plugin.pipeline.api.CanonicalTypeIdentity
import com.only4.cap4k.plugin.pipeline.api.CanonicalTypeKind
import com.only4.cap4k.plugin.pipeline.api.JsonValuePersistenceProjection
import com.only4.cap4k.plugin.pipeline.api.SemanticValueDefinition
import com.only4.cap4k.plugin.pipeline.api.SemanticValueRole
import com.only4.cap4k.plugin.pipeline.api.ValueObjectModel
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertThrows
import org.junit.jupiter.api.Test

class ValueObjectPersistenceProjectionIdentityValidatorTest {
    @Test
    fun `derived converter identity cannot collide with creation value identity`() {
        val converterFqn = "com.acme.demo.domain.orders.AddressJsonAttributeConverter"
        val error = assertThrows(IllegalArgumentException::class.java) {
            ValueObjectPersistenceProjectionIdentityValidator.validate(
                valueObjects = listOf(
                    ValueObjectModel(
                        definition = SemanticValueDefinition(
                            identity = CanonicalTypeIdentity(
                                packageName = "com.acme.demo.domain.orders",
                                typePath = listOf("Address"),
                                kind = CanonicalTypeKind.VALUE_OBJECT,
                            ),
                            role = SemanticValueRole.VALUE_OBJECT,
                        ),
                        persistence = JsonValuePersistenceProjection(converterFqn),
                    )
                ),
                canonicalDeclarations = listOf(
                    CanonicalTypeIdentity(
                        packageName = "com.acme.demo.domain.orders",
                        typePath = listOf("AddressJsonAttributeConverter"),
                        kind = CanonicalTypeKind.CREATION_VALUE,
                        ownerAggregateName = "Order",
                    )
                ),
            )
        }

        assertEquals(
            "value object com.acme.demo.domain.orders.Address JSON converter identity conflicts with " +
                "canonical/artifact declaration: $converterFqn",
            error.message,
        )
    }
}
