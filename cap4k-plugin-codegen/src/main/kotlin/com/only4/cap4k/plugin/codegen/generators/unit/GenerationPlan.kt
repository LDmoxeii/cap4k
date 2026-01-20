package com.only4.cap4k.plugin.codegen.generators.unit

import com.only4.cap4k.plugin.codegen.core.LoggerAdapter
import java.util.PriorityQueue

class GenerationPlan(
    private val log: LoggerAdapter? = null,
) {
    fun order(units: List<GenerationUnit>): List<GenerationUnit> {
        if (units.isEmpty()) return emptyList()

        val unique = LinkedHashMap<String, GenerationUnit>()
        units.forEach { unit ->
            if (unique.containsKey(unit.id)) {
                log?.warn("Duplicate unit id: ${unit.id}, keep first")
                return@forEach
            }
            unique[unit.id] = unit
        }

        val index = unique.keys.withIndex().associate { it.value to it.index }
        val inDegree = unique.keys.associateWith { 0 }.toMutableMap()
        val edges = mutableMapOf<String, MutableList<String>>()

        unique.values.forEach { unit ->
            unit.deps.forEach { dep ->
                if (!unique.containsKey(dep)) return@forEach
                edges.computeIfAbsent(dep) { mutableListOf() }.add(unit.id)
                inDegree[unit.id] = (inDegree[unit.id] ?: 0) + 1
            }
        }

        val comparator = Comparator<String> { a, b ->
            val ua = unique[a]!!
            val ub = unique[b]!!
            val orderCmp = ua.order.compareTo(ub.order)
            if (orderCmp != 0) {
                orderCmp
            } else {
                index[a]!!.compareTo(index[b]!!)
            }
        }
        val ready = PriorityQueue(comparator)
        inDegree.filterValues { it == 0 }.keys.forEach { ready.add(it) }

        val ordered = mutableListOf<GenerationUnit>()
        while (ready.isNotEmpty()) {
            val id = ready.poll()
            val unit = unique[id] ?: continue
            ordered.add(unit)
            edges[id]?.forEach { next ->
                val nextDegree = (inDegree[next] ?: 0) - 1
                inDegree[next] = nextDegree
                if (nextDegree == 0) {
                    ready.add(next)
                }
            }
        }

        if (ordered.size == unique.size) return ordered

        log?.warn("GenerationPlan contains cycles, fallback to order sorting")
        val remaining = unique.values.filterNot { unit ->
            ordered.any { it.id == unit.id }
        }
        val fallback = remaining.sortedWith { a, b ->
            val orderCmp = a.order.compareTo(b.order)
            if (orderCmp != 0) orderCmp else index[a.id]!!.compareTo(index[b.id]!!)
        }
        return ordered + fallback
    }
}
