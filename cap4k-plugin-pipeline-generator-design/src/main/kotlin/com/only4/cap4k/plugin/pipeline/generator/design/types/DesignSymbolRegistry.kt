package com.only4.cap4k.plugin.pipeline.generator.design.types

internal class DesignSymbolRegistry {
    constructor(symbols: Iterable<SymbolIdentity> = emptyList()) {
        symbols.forEach(::register)
    }

    private val symbolsBySimpleName = linkedMapOf<String, LinkedHashSet<SymbolIdentity>>()

    fun register(symbol: SymbolIdentity) {
        symbolsBySimpleName.getOrPut(symbol.simpleName) { linkedSetOf() }.add(symbol)
    }

    fun findBySimpleName(simpleName: String): List<SymbolIdentity> {
        return symbolsBySimpleName[simpleName].orEmpty().toList()
    }

    fun allSymbols(): List<SymbolIdentity> = symbolsBySimpleName.values.flatten()
}
