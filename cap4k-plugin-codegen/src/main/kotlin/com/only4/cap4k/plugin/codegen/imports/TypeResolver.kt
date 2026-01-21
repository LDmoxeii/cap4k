package com.only4.cap4k.plugin.codegen.imports

import com.only4.cap4k.plugin.codegen.misc.getPackageFromClassName

class TypeResolver(
    private val typeMapping: Map<String, String>,
    private val currentPackage: String,
) {
    private val builtinSimpleNames = setOf(
        "String",
        "Int",
        "Long",
        "Short",
        "Byte",
        "Boolean",
        "Float",
        "Double",
        "Char",
        "Any",
        "Unit",
        "Nothing",
        "List",
        "MutableList",
        "Set",
        "MutableSet",
        "Map",
        "MutableMap",
        "Collection",
        "Iterable",
        "Sequence",
        "Array",
        "Pair",
        "Triple",
    )

    private val skipPackagePrefixes = listOf(
        "kotlin",
        "kotlin.collections",
        "kotlin.ranges",
        "kotlin.text",
        "java.lang",
    )

    fun resolve(raw: String): TypeRef {
        val trimmed = raw.trim()
        if (trimmed.isEmpty()) {
            return TypeRef(simpleName = "", fqn = null, nullable = false)
        }
        return parseType(trimmed)
    }

    private fun parseType(raw: String): TypeRef {
        var text = raw.trim()
        var nullable = false
        if (text.endsWith("?")) {
            nullable = true
            text = text.dropLast(1).trim()
        }

        val (basePart, args) = splitTopLevelArgs(text)
        val (variance, baseName) = parseVariance(basePart.trim())

        if (baseName == "*") {
            return TypeRef(
                simpleName = "*",
                fqn = null,
                nullable = false,
                typeArguments = emptyList(),
                variance = variance,
                isStar = true,
                importable = false,
            )
        }

        val resolved = resolveBase(baseName)
        val typeArgs = args.map { parseType(it) }

        return TypeRef(
            simpleName = resolved.simpleName,
            fqn = resolved.fqn,
            nullable = nullable,
            typeArguments = typeArgs,
            variance = variance,
            isStar = false,
            importable = resolved.importable,
        )
    }

    private fun splitTopLevelArgs(text: String): Pair<String, List<String>> {
        val lt = text.indexOf('<')
        if (lt < 0) return text to emptyList()

        val base = text.substring(0, lt).trim()
        val args = mutableListOf<String>()
        val current = StringBuilder()
        var depth = 0

        var i = lt + 1
        while (i < text.length) {
            val c = text[i]
            when (c) {
                '<' -> {
                    depth++
                    current.append(c)
                }
                '>' -> {
                    if (depth == 0) {
                        val arg = current.toString().trim()
                        if (arg.isNotEmpty()) args.add(arg)
                        break
                    }
                    depth--
                    current.append(c)
                }
                ',' -> {
                    if (depth == 0) {
                        val arg = current.toString().trim()
                        if (arg.isNotEmpty()) args.add(arg)
                        current.setLength(0)
                    } else {
                        current.append(c)
                    }
                }
                else -> current.append(c)
            }
            i++
        }

        return base to args
    }

    private fun parseVariance(text: String): Pair<String?, String> {
        val trimmed = text.trim()
        return when {
            trimmed.startsWith("out ") -> "out" to trimmed.removePrefix("out ").trim()
            trimmed.startsWith("in ") -> "in" to trimmed.removePrefix("in ").trim()
            else -> null to trimmed
        }
    }

    private data class ResolvedBase(
        val simpleName: String,
        val fqn: String?,
        val importable: Boolean,
    )

    private fun resolveBase(baseName: String): ResolvedBase {
        val simple = baseName.substringAfterLast('.')

        if (simple in builtinSimpleNames) {
            return ResolvedBase(simpleName = simple, fqn = null, importable = false)
        }

        val fqn = when {
            baseName.contains('.') -> baseName
            typeMapping.containsKey(simple) -> typeMapping[simple]
            else -> null
        }

        if (fqn.isNullOrBlank()) {
            return ResolvedBase(simpleName = simple, fqn = null, importable = false)
        }

        val pkg = getPackageFromClassName(fqn)
        val skip = skipPackagePrefixes.any { pkg == it || pkg.startsWith("$it.") }
        val importable = !skip && pkg.isNotBlank() && pkg != currentPackage

        return ResolvedBase(simpleName = simple, fqn = fqn, importable = importable)
    }
}
