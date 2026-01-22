@file:OptIn(org.jetbrains.kotlin.ir.symbols.UnsafeDuringIrConstructionAPI::class)

package com.only4.cap4k.plugin.codeanalysis.compiler

import org.jetbrains.kotlin.ir.declarations.IrClass
import org.jetbrains.kotlin.ir.types.IrSimpleType
import org.jetbrains.kotlin.ir.types.IrType
import org.jetbrains.kotlin.ir.types.IrTypeProjection
import org.jetbrains.kotlin.ir.util.fqNameWhenAvailable

class IrTypeFormatter {
    private val listTypes = setOf(
        "kotlin.collections.List",
        "kotlin.collections.MutableList",
        "kotlin.collections.Collection",
        "kotlin.collections.MutableCollection",
        "kotlin.collections.Iterable",
        "kotlin.collections.MutableIterable"
    )
    private val setTypes = setOf(
        "kotlin.collections.Set",
        "kotlin.collections.MutableSet"
    )
    private val mapTypes = setOf(
        "kotlin.collections.Map",
        "kotlin.collections.MutableMap"
    )
    private val arrayTypes = setOf("kotlin.Array")

    fun format(type: IrType): String {
        val simple = type as? IrSimpleType ?: return "Any"
        val klass = simple.classifier?.owner as? IrClass ?: return "Any"
        val fq = klass.fqNameWhenAvailable?.asString()
        if (fq in listTypes) {
            val arg = typeArgumentOrNull(simple, 0)
            return if (arg == null) "List<Any>" else "List<${format(arg)}>"
        }
        if (fq in setTypes) {
            val arg = typeArgumentOrNull(simple, 0)
            return if (arg == null) "Set<Any>" else "Set<${format(arg)}>"
        }
        if (fq in mapTypes) {
            val keyArg = typeArgumentOrNull(simple, 0)
            val valueArg = typeArgumentOrNull(simple, 1)
            val keyType = if (keyArg == null) "Any" else format(keyArg)
            val valueType = if (valueArg == null) "Any" else format(valueArg)
            return "Map<$keyType,$valueType>"
        }
        if (fq in arrayTypes) {
            val arg = typeArgumentOrNull(simple, 0)
            return if (arg == null) "Array<Any>" else "Array<${format(arg)}>"
        }
        return klass.name.asString()
    }

    fun collectionElementType(type: IrType): IrType? {
        val simple = type as? IrSimpleType ?: return null
        val klass = simple.classifier?.owner as? IrClass ?: return null
        val fq = klass.fqNameWhenAvailable?.asString()
        return when {
            fq in listTypes || fq in setTypes || fq in arrayTypes -> typeArgumentOrNull(simple, 0)
            else -> null
        }
    }

    private fun typeArgumentOrNull(type: IrSimpleType, index: Int): IrType? {
        val arg = type.arguments.getOrNull(index) as? IrTypeProjection
        return arg?.type
    }
}
