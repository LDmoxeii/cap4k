package com.only4.cap4k.plugin.pipeline.core

import com.only4.cap4k.plugin.pipeline.api.AggregateFetchType
import com.only4.cap4k.plugin.pipeline.api.AggregateRelationModel
import com.only4.cap4k.plugin.pipeline.api.AggregateRelationType
import com.only4.cap4k.plugin.pipeline.api.EntityModel
import com.only4.cap4k.plugin.pipeline.api.FieldModel
import com.only4.cap4k.plugin.pipeline.api.OwnedRelationCardinality
import com.only4.cap4k.plugin.pipeline.api.OwnedRelationPersistenceShape
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows

class AggregateCreationGraphValidatorTest {
    @Test
    fun `rejects an owned child reached from multiple owners`() {
        val entities = listOf(entity("Order"), entity("Shipment"), entity("Address"))

        val error = assertThrows<IllegalArgumentException> {
            AggregateCreationGraphValidator.validate(
                entities,
                listOf(
                    relation("Order", "Address", "addresses"),
                    relation("Shipment", "Address", "addresses"),
                ),
            )
        }

        assertTrue(error.message.orEmpty().contains("Address has conflicting owners"))
        assertTrue(error.message.orEmpty().contains("Order.addresses"))
        assertTrue(error.message.orEmpty().contains("Shipment.addresses"))
    }

    @Test
    fun `rejects owned relation cycles before graph compilation`() {
        val entities = listOf(entity("Order"), entity("OrderLine"))

        val error = assertThrows<IllegalArgumentException> {
            AggregateCreationGraphValidator.validate(
                entities,
                listOf(
                    relation("Order", "OrderLine", "lines"),
                    relation("OrderLine", "Order", "order"),
                ),
            )
        }

        assertTrue(error.message.orEmpty().contains("owned relation cycle detected"))
        assertTrue(error.message.orEmpty().contains("Order.lines"))
        assertTrue(error.message.orEmpty().contains("OrderLine.order"))
    }

    private fun entity(name: String): EntityModel {
        val id = FieldModel("id", "Long")
        return EntityModel(
            name = name,
            packageName = "com.acme.order",
            tableName = name.lowercase(),
            comment = "",
            fields = listOf(id),
            idField = id,
        )
    }

    private fun relation(
        owner: String,
        target: String,
        fieldName: String,
    ): AggregateRelationModel = AggregateRelationModel(
        ownerEntityName = owner,
        ownerEntityPackageName = "com.acme.order",
        fieldName = fieldName,
        targetEntityName = target,
        targetEntityPackageName = "com.acme.order",
        relationType = AggregateRelationType.ONE_TO_MANY,
        joinColumn = "owner_id",
        fetchType = AggregateFetchType.LAZY,
        nullable = false,
        owned = true,
        parentRefColumn = "owner_id",
        ownedCardinality = OwnedRelationCardinality.MANY,
        persistenceShape = OwnedRelationPersistenceShape.ONE_TO_MANY_JOIN_COLUMN,
        backingCollectionName = fieldName,
    )
}
