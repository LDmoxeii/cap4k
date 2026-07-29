@file:OptIn(org.jetbrains.kotlin.ir.symbols.UnsafeDuringIrConstructionAPI::class)

package com.only4.cap4k.plugin.codeanalysis.compiler

import org.jetbrains.kotlin.ir.declarations.IrClass
import org.jetbrains.kotlin.ir.types.IrSimpleType
import org.jetbrains.kotlin.ir.types.IrType
import org.jetbrains.kotlin.ir.types.IrTypeProjection
import org.jetbrains.kotlin.ir.util.fqNameWhenAvailable
import org.jetbrains.kotlin.ir.util.isNullable

class IrTypeFormatter {
    private val unsupportedContainerTypes = setOf(
        "kotlin.Array",
        "kotlin.collections.Collection",
        "kotlin.collections.Iterable",
        "kotlin.collections.MutableCollection",
        "kotlin.collections.MutableIterable",
        "kotlin.collections.MutableList",
        "kotlin.collections.MutableMap",
        "kotlin.collections.MutableSet",
    )

    fun format(type: IrType): String {
        val simple = type as? IrSimpleType
            ?: unsupported(type, "only class-backed simple types are supported")
        val klass = simple.classifier?.owner as? IrClass
            ?: unsupported(type, "type parameters and unresolved classifiers are unsupported")
        val fq = klass.fqNameWhenAvailable?.asString()
            ?: unsupported(type, "anonymous and local classifiers are unsupported")
        require(fq !in unsupportedContainerTypes) {
            "unsupported IR design field type $fq: use canonical List, Set, or Map instead"
        }
        require(!fq.startsWith("kotlin.Function") && !fq.startsWith("kotlin.coroutines.SuspendFunction")) {
            "unsupported IR design field type $fq: function types are unsupported"
        }

        val rendered = when (fq) {
            LIST_FQN -> "List<${format(requireTypeArgument(simple, 0, fq, 1))}>"
            SET_FQN -> "Set<${format(requireTypeArgument(simple, 0, fq, 1))}>"
            MAP_FQN -> {
                requireTypeArgumentCount(simple, fq, 2)
                "Map<${format(requireTypeArgument(simple, 0, fq, 2))},${format(requireTypeArgument(simple, 1, fq, 2))}>"
            }
            PAGE_DATA_FQN -> "PageData<${format(requireTypeArgument(simple, 0, fq, 1))}>"
            else -> {
                require(simple.arguments.isEmpty()) {
                    "unsupported IR design field type $fq: arbitrary generic constructors are unsupported"
                }
                klass.name.asString()
            }
        }
        return if (type.isNullable()) "$rendered?" else rendered
    }

    fun collectionElementType(type: IrType): IrType? {
        val simple = type as? IrSimpleType ?: return null
        val klass = simple.classifier?.owner as? IrClass ?: return null
        val fq = klass.fqNameWhenAvailable?.asString()
        return if (fq == LIST_FQN) requireTypeArgument(simple, 0, fq, 1) else null
    }

    fun pageDataElementType(type: IrType): IrType? {
        val simple = type as? IrSimpleType ?: return null
        val klass = simple.classifier?.owner as? IrClass ?: return null
        val fq = klass.fqNameWhenAvailable?.asString()
        return if (fq == PAGE_DATA_FQN) requireTypeArgument(simple, 0, fq, 1) else null
    }

    private fun requireTypeArgument(
        type: IrSimpleType,
        index: Int,
        constructor: String,
        expectedCount: Int,
    ): IrType {
        requireTypeArgumentCount(type, constructor, expectedCount)
        val projection = type.arguments[index] as? IrTypeProjection
            ?: throw IllegalArgumentException(
                "unsupported IR design field type $constructor: variance and star projections are unsupported",
            )
        return projection.type
    }

    private fun requireTypeArgumentCount(type: IrSimpleType, constructor: String, expected: Int) {
        require(type.arguments.size == expected) {
            "unsupported IR design field type $constructor: expected $expected type argument${if (expected == 1) "" else "s"}"
        }
    }

    private fun unsupported(type: IrType, reason: String): Nothing =
        throw IllegalArgumentException("unsupported IR design field type $type: $reason")

    private companion object {
        const val LIST_FQN = "kotlin.collections.List"
        const val SET_FQN = "kotlin.collections.Set"
        const val MAP_FQN = "kotlin.collections.Map"
        const val PAGE_DATA_FQN = "com.only4.cap4k.ddd.core.share.PageData"
    }
}
