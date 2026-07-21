package com.only4.cap4k.plugin.pipeline.generator.common.types

class TypeSymbolRegistry(symbols: Iterable<TypeSymbolIdentity> = emptyList()) {
    private val symbolsBySimpleName = linkedMapOf<String, LinkedHashSet<TypeSymbolIdentity>>()

    init {
        symbols.forEach(::register)
    }

    fun register(symbol: TypeSymbolIdentity) {
        symbolsBySimpleName.getOrPut(symbol.simpleName) { linkedSetOf() }.add(symbol)
    }

    fun findBySimpleName(simpleName: String): List<TypeSymbolIdentity> =
        symbolsBySimpleName[simpleName].orEmpty().toList()

    fun allSymbols(): List<TypeSymbolIdentity> = symbolsBySimpleName.values.flatten()
}
