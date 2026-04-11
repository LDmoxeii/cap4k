package com.only4.cap4k.plugin.pipeline.generator.design.types

internal class DesignSymbolRegistry {

    private val symbolsBySimpleName = linkedMapOf<String, MutableList<SymbolIdentity>>()

    fun register(symbol: SymbolIdentity) {
        symbolsBySimpleName.getOrPut(symbol.simpleName) { mutableListOf() }.add(symbol)
    }

    fun findBySimpleName(simpleName: String): List<SymbolIdentity> {
        return symbolsBySimpleName[simpleName].orEmpty().toList()
    }
}
