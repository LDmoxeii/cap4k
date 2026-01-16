package com.only4.cap4k.codegen.imports

/**
 * Import 管理器的通用实现
 */
abstract class BaseImportManager : ImportManager {
    override val requiredImports = mutableSetOf<String>()

    override fun toImportLines(): List<String> {
        // 1) Apply wildcard collapsing: if a package.* exists, drop specific imports from the same package
        val wildcardPkgs = requiredImports
            .filter { it.endsWith(".*") }
            .map { it.removeSuffix(".*") }
            .toSet()

        val filtered = requiredImports.filter { imp ->
            if (imp.endsWith(".*")) return@filter true
            val lastDot = imp.lastIndexOf('.')
            if (lastDot <= 0) return@filter true
            val pkg = imp.substring(0, lastDot)
            // Do not collapse explicit imports under jakarta.persistence to avoid
            // ambiguity with org.hibernate.annotations (e.g., Table)
            !wildcardPkgs.contains(pkg) || pkg == "jakarta.persistence"
        }.sorted()

        // 2) Render grouped import lines
        val result = mutableListOf<String>()
        result.add("")

        var lastPackageGroup: String? = null
        filtered.forEach { importStr ->
            val currentGroup = when {
                importStr.startsWith("com.only4.cap4k") -> "cap4k"
                importStr.startsWith("org.springframework") -> "spring"
                importStr.startsWith("jakarta") -> "jakarta"
                importStr.startsWith("org.hibernate") -> "hibernate"
                importStr.startsWith("org.slf4j") -> "slf4j"
                else -> "other"
            }

            if (lastPackageGroup != null && lastPackageGroup != currentGroup) {
                result.add("")
            }

            result.add("import $importStr")
            lastPackageGroup = currentGroup
        }

        return result
    }
}
