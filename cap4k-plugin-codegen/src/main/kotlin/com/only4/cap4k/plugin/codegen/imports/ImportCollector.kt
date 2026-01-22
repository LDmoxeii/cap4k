package com.only4.cap4k.plugin.codegen.imports

class ImportCollector {
    private val imports = linkedSetOf<String>()

    fun add(importFqn: String) {
        val trimmed = importFqn.trim()
        if (trimmed.isNotEmpty()) {
            imports.add(trimmed)
        }
    }

    fun addImportLine(line: String) {
        val trimmed = line.trim()
        if (trimmed.startsWith("import ")) {
            add(trimmed.removePrefix("import").trim())
        }
    }

    fun addImportLines(lines: Iterable<String>) {
        lines.forEach { addImportLine(it) }
    }

    fun toImportLines(): List<String> {
        val wildcardPkgs = imports
            .filter { it.endsWith(".*") }
            .map { it.removeSuffix(".*") }
            .toSet()

        val filtered = imports.filter { imp ->
            if (imp.endsWith(".*")) return@filter true
            val lastDot = imp.lastIndexOf('.')
            if (lastDot <= 0) return@filter true
            val pkg = imp.substring(0, lastDot)
            !wildcardPkgs.contains(pkg) || pkg == "jakarta.persistence"
        }.sorted()

        val result = mutableListOf<String>()
        filtered.forEach { importStr ->
            result.add("import $importStr")
        }

        return result
    }

    fun render(): String = toImportLines().joinToString("\n")
}
