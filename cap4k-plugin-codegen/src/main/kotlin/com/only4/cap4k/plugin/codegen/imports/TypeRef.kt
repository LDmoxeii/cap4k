package com.only4.cap4k.plugin.codegen.imports

data class TypeRef(
    val simpleName: String,
    val fqn: String?,
    val nullable: Boolean,
    val typeArguments: List<TypeRef> = emptyList(),
    val variance: String? = null,
    val isStar: Boolean = false,
    val importable: Boolean = false,
) {
    fun render(): String {
        if (isStar) return "*"
        val prefix = variance?.let { "$it " } ?: ""
        val args = if (typeArguments.isNotEmpty()) {
            typeArguments.joinToString(", ") { it.render() }.let { "<$it>" }
        } else {
            ""
        }
        val suffix = if (nullable) "?" else ""
        return "$prefix$simpleName$args$suffix"
    }

    fun collectImports(collector: ImportCollector) {
        if (importable && !fqn.isNullOrBlank()) {
            collector.add(fqn)
        }
        typeArguments.forEach { it.collectImports(collector) }
    }
}
